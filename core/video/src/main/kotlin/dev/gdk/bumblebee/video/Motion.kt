package dev.gdk.bumblebee.video

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What colours the ridden trail: the accent, how fast the rider went, or how steep the road was. */
enum class TrailColor(val label: String) { ACCENT("Accent"), SPEED("Speed"), CLIMB("Climb") }

/**
 * The trail's colour scales, as sRGB stops from low to high. The renderer's ribbon shader, the
 * legend and the elevation strip all read these, so the three always agree.
 */
object TrailRamp {
    /** Slow (blue) through green and yellow to fast (hot pink-red). */
    val SPEED = intArrayOf(0x2B59FF, 0x19C3FF, 0x3CE07A, 0xFFD23A, 0xFF7A1A, 0xFF2D55)

    /** Downhill blues, a pale green on the flat, orange then red up the steep bits. */
    val CLIMB = intArrayOf(0x2E7BFF, 0x7ED6FF, 0xC8F5B0, 0xFFC23A, 0xFF6A1A, 0xFF2D3B)

    fun stops(c: TrailColor): IntArray? = when (c) {
        TrailColor.ACCENT -> null
        TrailColor.SPEED -> SPEED
        TrailColor.CLIMB -> CLIMB
    }

    /** The colour at [v] (0..1) along [stops], as 0xRRGGBB, blended in sRGB like the shader. */
    fun at(stops: IntArray, v: Double): Int {
        val f = v.coerceIn(0.0, 1.0) * (stops.size - 1)
        val i = min(floor(f).toInt(), stops.size - 2)
        val t = f - i
        fun ch(c: Int, s: Int) = (c shr s) and 255
        fun mix(s: Int) = (ch(stops[i], s) + (ch(stops[i + 1], s) - ch(stops[i], s)) * t).roundToInt()
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** A climb of this grade or steeper (and the same downhill) takes the end colours. */
    const val FULL_GRADE = 0.08
}

/** A round number passed on the ride: [label] big, [detail] under it. */
data class Milestone(val distanceM: Double, val label: String, val detail: String)

object Milestones {
    private val STEPS_KM = listOf(5, 10, 25, 50, 100, 200, 250, 500)

    /**
     * Round distances on the odometer (the step chosen so there are one to four of them) and
     * thousand-metre heights first reached, inside the trimmed route, away from its ends, and
     * not within [clearS] seconds of a moment's pause or of each other.
     */
    fun plan(
        routeLengthM: Double,
        trimStartM: Double,
        altitudeAt: ((Double) -> Double)?,
        timeAt: (Double) -> Double,
        holds: List<Double>,
        clearS: Double = 2.5,
    ): List<Milestone> {
        val fromKm = trimStartM / 1000
        val toKm = (trimStartM + routeLengthM) / 1000
        val rideKm = toKm + trimStartM / 1000
        val step = STEPS_KM.firstOrNull { rideKm / it <= 4.5 } ?: STEPS_KM.last()
        val candidates = mutableListOf<Milestone>()
        var k = (floor(fromKm / step).toInt() + 1) * step
        while (k < toKm) {
            candidates += Milestone(k * 1000.0 - trimStartM, "$k KM", "RIDDEN")
            k += step
        }
        if (altitudeAt != null) {
            val n = max(2, (routeLengthM / 50).toInt())
            var best = altitudeAt(0.0)
            for (i in 1..n) {
                val d = routeLengthM * i / n
                val a = altitudeAt(d)
                val mark = (floor(a / 1000) * 1000).toInt()
                if (mark >= 1000 && mark > best) {
                    candidates += Milestone(d, String.format(Locale.ENGLISH, "%,d M", mark), "ABOVE SEA LEVEL")
                }
                best = max(best, a)
            }
        }
        val out = mutableListOf<Milestone>()
        for (m in candidates.sortedBy { it.distanceM }) {
            if (m.distanceM < routeLengthM * 0.06 || m.distanceM > routeLengthM * 0.94) continue
            val t = timeAt(m.distanceM)
            if (holds.any { abs(it - t) < clearS }) continue
            if (out.any { abs(timeAt(it.distanceM) - t) < clearS }) continue
            out += m
        }
        return out
    }
}

/**
 * A music track the rider chose, as analysed once on import: where its beats fall (seconds into
 * the track), the tempo, which beat starts each bar, and how loud it is every [LOUD_HOP_S].
 */
class MusicTrack(
    val name: String,
    val durationS: Double,
    val bpm: Double,
    val beats: DoubleArray,
    /** Index into [beats] of the first downbeat; every fourth beat after it is one too. */
    val downbeat: Int,
    val loudness: FloatArray,
) {
    val beatS: Double get() = 60.0 / bpm

    fun isDownbeat(i: Int) = ((i - downbeat) % 4 + 4) % 4 == 0

    fun loudAt(s: Double): Double {
        if (loudness.isEmpty()) return 0.0
        val i = (s / LOUD_HOP_S).toInt().coerceIn(0, loudness.lastIndex)
        return loudness[i].toDouble()
    }

    /** A compact text form for storing next to the audio file. */
    fun encode(): String = buildString {
        append(name.replace('\n', ' ')).append('\n')
        append(durationS).append('\n')
        append(bpm).append('\n')
        append(downbeat).append('\n')
        append(beats.joinToString(",") { String.format(Locale.ENGLISH, "%.3f", it) }).append('\n')
        append(loudness.joinToString(",") { String.format(Locale.ENGLISH, "%.3f", it) })
    }

    companion object {
        const val LOUD_HOP_S = 0.1

        fun decode(s: String): MusicTrack? = runCatching {
            val l = s.split('\n')
            MusicTrack(
                name = l[0],
                durationS = l[1].toDouble(),
                bpm = l[2].toDouble(),
                downbeat = l[3].toInt(),
                beats = l[4].split(',').filter { it.isNotBlank() }.map { it.toDouble() }.toDoubleArray(),
                loudness = l.getOrElse(5) { "" }.split(',').filter { it.isNotBlank() }.map { it.toFloat() }.toFloatArray(),
            )
        }.getOrNull()
    }
}

/**
 * Finds the beat in a track from two envelopes made while decoding it, one value per [hopS]: the
 * whole signal's energy, and its low end's (where the kick drum is). Onsets are rises in log
 * energy; the tempo is the strongest repeat between 70 and 180 BPM (leaning toward 120); the beats
 * are then followed through the track, each allowed to drift a little from the last.
 */
object BeatTracker {
    fun analyze(name: String, energy: FloatArray, low: FloatArray, hopS: Double): MusicTrack {
        val n = min(energy.size, low.size)
        val durationS = n * hopS
        val onset = onsets(energy, low, n, hopS)
        val period = tempo(onset, hopS)
        val beats = track(onset, period)
        // Downbeats: the one of four phases where the kick is strongest.
        val lowOnset = DoubleArray(n) { i -> if (i == 0) 0.0 else max(0.0, ln(low[i] + 1e-6) - ln(low[i - 1] + 1e-6)) }
        var bestPhase = 0
        var bestScore = -1.0
        for (ph in 0 until 4) {
            var s = 0.0
            var i = ph
            while (i < beats.size) {
                s += peak(lowOnset, beats[i], 2)
                i += 4
            }
            if (s > bestScore) {
                bestScore = s
                bestPhase = ph
            }
        }
        // Loudness every LOUD_HOP_S, 0..1 against the loudest stretch.
        val per = MusicTrack.LOUD_HOP_S / hopS
        val raw = FloatArray(max(1, (n / per).toInt())) { k ->
            var sum = 0.0
            var c = 0
            for (i in (k * per).toInt() until min(n, ((k + 1) * per).toInt())) {
                sum += energy[i]
                c++
            }
            kotlin.math.sqrt(sum / max(1, c)).toFloat()
        }
        val top = raw.sortedDescending().getOrElse(raw.size / 50) { 1f }.coerceAtLeast(1e-6f)
        val loud = FloatArray(raw.size) { (raw[it] / top).coerceIn(0f, 1f) }
        return MusicTrack(name, durationS, 60.0 / ((if (beats.size > 1) (beats.last() - beats.first()) / (beats.size - 1) else period) * hopS), DoubleArray(beats.size) { beats[it] * hopS }, bestPhase, loud)
    }

    internal fun onsets(energy: FloatArray, low: FloatArray, n: Int, hopS: Double): DoubleArray {
        val raw = DoubleArray(n)
        for (i in 1 until n) {
            val full = ln(energy[i] + 1e-6) - ln(energy[i - 1] + 1e-6)
            val bass = ln(low[i] + 1e-6) - ln(low[i - 1] + 1e-6)
            raw[i] = max(0.0, full) + 1.5 * max(0.0, bass)
        }
        // Take away the local average so steady sections don't count as beats.
        val w = max(1, (0.25 / hopS).toInt())
        val out = DoubleArray(n)
        var sum = 0.0
        for (i in 0 until n) {
            sum += raw[i]
            if (i - 2 * w - 1 >= 0) sum -= raw[i - 2 * w - 1]
            val centre = i - w
            if (centre >= 0) out[centre] = max(0.0, raw[centre] - sum / (2 * w + 1))
        }
        return out
    }

    /** Beat period in hops. */
    private fun tempo(onset: DoubleArray, hopS: Double): Double {
        val minLag = max(2, (60.0 / 180 / hopS).toInt())
        val maxLag = (60.0 / 70 / hopS).toInt() + 1
        val scores = DoubleArray(maxLag + 2)
        for (lag in minLag..maxLag + 1) {
            var s = 0.0
            for (i in lag until onset.size) s += onset[i] * onset[i - lag]
            val bpm = 60.0 / (lag * hopS)
            val prior = exp(-0.5 * (ln(bpm / 120.0) / 0.35).let { it * it })
            scores[lag] = s * prior
        }
        var best = minLag
        for (lag in minLag..maxLag) if (scores[lag] > scores[best]) best = lag
        // Parabolic refinement between neighbouring lags.
        val a = scores.getOrElse(best - 1) { 0.0 }
        val b = scores[best]
        val c = scores.getOrElse(best + 1) { 0.0 }
        val den = a - 2 * b + c
        val shift = if (den < 0) (0.5 * (a - c) / den).coerceIn(-0.5, 0.5) else 0.0
        return best + shift
    }

    /** Beat positions in hops (fractional), following the onsets with [period] as the guide. */
    private fun track(onset: DoubleArray, period: Double): List<Double> {
        if (onset.isEmpty()) return emptyList()
        // The period and phase where onsets line up best across the whole track: the tempo from
        // autocorrelation is close, and this pins it down so the grid doesn't drift.
        var phase = 0.0
        var best = -1.0
        var bestPeriod = period
        var p = period - 0.6
        while (p <= period + 0.6) {
            var ph = 0.0
            while (ph < p) {
                var s = 0.0
                var t = ph
                while (t < onset.size) {
                    s += peak(onset, t, 1)
                    t += p
                }
                if (s > best) {
                    best = s
                    phase = ph
                    bestPeriod = p
                }
                ph += 0.5
            }
            p += 0.02
        }
        @Suppress("NAME_SHADOWING") val period = bestPeriod
        val mean = onset.average().coerceAtLeast(1e-9)
        val out = mutableListOf<Double>()
        var t = phase
        while (t < onset.size) {
            // Look a little either side of where the beat should be for a clear onset.
            val reach = period * 0.1
            var bestAt = t
            var bestV = 0.0
            var x = t - reach
            while (x <= t + reach) {
                val v = peak(onset, x, 0) * exp(-0.5 * ((x - t) / (reach * 0.6)).let { it * it })
                if (v > bestV) {
                    bestV = v
                    bestAt = x
                }
                x += 0.5
            }
            val at = if (bestV > mean * 2) bestAt else t
            out += at
            t = at + period
        }
        return out
    }

    private fun peak(a: DoubleArray, at: Double, spread: Int): Double {
        val c = at.roundToInt()
        var m = 0.0
        for (i in c - spread..c + spread) if (i in a.indices) m = max(m, a[i])
        return m
    }
}

/**
 * How the chosen track plays under a video: where in the track the video starts ([offsetS]), its
 * volume at each moment with fades at both ends, and the beats in video time for the pulses.
 */
class MusicCue(val track: MusicTrack, val offsetS: Double, val totalS: Double, private val volume: Double, private val fadeS: Double, val synced: Boolean) {
    /** Video time where the music runs out (or the video ends). */
    val endS: Double = min(totalS, track.durationS - offsetS)

    private val beatTimes: DoubleArray = track.beats.map { it - offsetS }.toDoubleArray()

    /** The volume at video time [t], 0..1. */
    fun gain(t: Double): Double {
        if (t < 0 || t > endS) return 0.0
        val fin = if (fadeS <= 0 || offsetS < 0.5) 1.0 else (t / fadeS).coerceIn(0.0, 1.0)
        val fout = if (fadeS <= 0) 1.0 else ((endS - t) / max(fadeS, 0.8)).coerceIn(0.0, 1.0)
        return volume * fin * fout
    }

    /** A flash on each beat that dies away within about a fifth of a second; bars hit hardest. */
    fun pulse(t: Double): Double {
        if (!synced || t < 0 || t > endS) return 0.0
        var lo = 0
        var hi = beatTimes.lastIndex
        if (hi < 0 || beatTimes[0] > t) return 0.0
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (beatTimes[mid] <= t) lo = mid else hi = mid - 1
        }
        val since = t - beatTimes[lo]
        return exp(-since / 0.16) * (if (track.isDownbeat(lo)) 1.0 else 0.55)
    }

    /** Video times of the beats, for tests and the Studio's timeline. */
    fun beatsInVideo(): List<Double> = beatTimes.filter { it in 0.0..totalS }

    companion object {
        /**
         * Places the track under the video. Automatically, the track's biggest lift in energy (the
         * drop) lands as the camera dives in at [rideStartS]; by hand, it starts at [startS]. With
         * [sync], the start is nudged so a beat (a bar's first, when there's one near) lands
         * exactly on that cut.
         */
        fun place(track: MusicTrack, totalS: Double, rideStartS: Double, startS: Double?, volume: Double, fadeS: Double, sync: Boolean): MusicCue {
            val latest = max(0.0, track.durationS - totalS)
            var offset = startS?.coerceIn(0.0, max(0.0, track.durationS - 1)) ?: run {
                val drop = dropAt(track, rideStartS, totalS)
                (drop - rideStartS).coerceIn(0.0, latest)
            }
            if (sync && track.beats.isNotEmpty()) {
                val cut = offset + rideStartS
                val near = track.beats.indices.filter { abs(track.beats[it] - cut) <= track.beatS * 2.1 }
                val pick = near.filter { track.isDownbeat(it) }.minByOrNull { abs(track.beats[it] - cut) }
                    ?: track.beats.indices.minBy { abs(track.beats[it] - cut) }
                val at = if (track.beats[pick] >= rideStartS) pick else {
                    // Too early to fit the intro: the first bar (or beat) that leaves room.
                    val first = track.beats.indexOfFirst { it >= rideStartS }.coerceAtLeast(0)
                    (first until min(track.beats.size, first + 4)).firstOrNull { track.isDownbeat(it) } ?: first
                }
                offset = max(0.0, track.beats[at] - rideStartS)
            }
            return MusicCue(track, offset, totalS, volume, fadeS, sync)
        }

        /**
         * The track time with the biggest rise in loudness (four seconds after against four
         * before, plus how loud it then stays), with room for the intro before it and, if the
         * track allows, the rest of the video after.
         */
        fun dropAt(track: MusicTrack, rideStartS: Double, totalS: Double): Double {
            val candidates = if (track.beats.isNotEmpty()) track.beats.toList() else (0 until track.durationS.toInt()).map { it.toDouble() }
            val after = totalS - rideStartS
            var best = rideStartS
            var bestScore = Double.NEGATIVE_INFINITY
            for (b in candidates) {
                if (b < rideStartS) continue
                val fits = b + after <= track.durationS
                if (!fits && track.durationS >= totalS) continue
                val before = mean(track, b - 4, b)
                val then = mean(track, b, b + 4)
                val score = (then - before) + 0.5 * then
                if (score > bestScore) {
                    bestScore = score
                    best = b
                }
            }
            return best
        }

        private fun mean(track: MusicTrack, from: Double, to: Double): Double {
            var s = 0.0
            var n = 0
            var t = max(0.0, from)
            while (t < min(to, track.durationS)) {
                s += track.loudAt(t)
                n++
                t += MusicTrack.LOUD_HOP_S
            }
            return if (n == 0) 0.0 else s / n
        }
    }
}

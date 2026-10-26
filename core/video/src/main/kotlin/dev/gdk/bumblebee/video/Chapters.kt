package dev.gdk.bumblebee.video

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One act of the ride, from [startM] along the trimmed route. */
data class Chapter(val number: Int, val startM: Double, val title: String, val detail: String? = null)

/**
 * Splits a ride into a few named acts from what the road did: sustained climbs and descents,
 * twisty sections, open road, and breaks. Short bits merge into their neighbours so a ride gets
 * two to five chapters, or none when it was all one thing.
 */
object Chapters {
    private enum class Kind(val title: String) {
        CLIMB("The climb"), DESCENT("The descent"), TWISTY("The twisties"), OPEN("Open road")
    }

    private const val BINS = 20
    private const val MIN_SHARE = 0.15
    private const val MAX_CHAPTERS = 5

    fun plan(
        routeLengthM: Double,
        altitudeAt: (Double) -> Double?,
        twistAt: (Double) -> Double,
        breaks: List<StopMark> = emptyList(),
    ): List<Chapter> {
        if (routeLengthM < 3_000) return emptyList()
        val bin = routeLengthM / BINS
        // Classify each 5% of the route.
        val kinds = (0 until BINS).map { i ->
            val a = altitudeAt(i * bin)
            val b = altitudeAt((i + 1) * bin)
            val rise = if (a != null && b != null) b - a else 0.0
            val steep = maxOf(40.0, bin * 0.025)
            val twist = (0..4).map { twistAt(i * bin + bin * it / 4) }.average()
            when {
                rise > steep -> Kind.CLIMB
                rise < -steep -> Kind.DESCENT
                twist > 0.22 -> Kind.TWISTY
                else -> Kind.OPEN
            }
        }
        // Runs of the same kind.
        data class Run(var from: Int, var to: Int, val kind: Kind)
        val runs = mutableListOf<Run>()
        for ((i, k) in kinds.withIndex()) {
            val last = runs.lastOrNull()
            if (last != null && last.kind == k) last.to = i else runs += Run(i, i, k)
        }
        // Merge runs shorter than the minimum into the longer neighbour.
        fun share(r: Run) = (r.to - r.from + 1).toDouble() / BINS
        var merged = true
        while (merged && runs.size > 1) {
            merged = false
            val i = runs.indices.minBy { share(runs[it]) }
            if (share(runs[i]) < MIN_SHARE) {
                val left = runs.getOrNull(i - 1)
                val right = runs.getOrNull(i + 1)
                val into = when {
                    left == null -> right!!
                    right == null -> left
                    share(left) >= share(right) -> left
                    else -> right
                }
                into.from = minOf(into.from, runs[i].from)
                into.to = maxOf(into.to, runs[i].to)
                runs.removeAt(i)
                // Neighbours of the same kind become one.
                var j = 0
                while (j < runs.size - 1) {
                    if (runs[j].kind == runs[j + 1].kind) {
                        runs[j].to = runs[j + 1].to
                        runs.removeAt(j + 1)
                    } else {
                        j++
                    }
                }
                merged = true
            }
        }
        val starts = runs.map { r ->
            val from = r.from * bin
            val to = (r.to + 1) * bin
            val gain = altitudeAt(to)?.let { b -> altitudeAt(from)?.let { a -> b - a } }
            val detail = when (r.kind) {
                Kind.CLIMB, Kind.DESCENT -> gain?.let { String.format(Locale.ENGLISH, "%+,d m over %.0f km", it.roundToInt(), (to - from) / 1000) }
                else -> String.format(Locale.ENGLISH, "%.0f km", (to - from) / 1000)
            }
            Triple(from, r.kind.title, detail)
        }.toMutableList()
        // A long break starts its own chapter, unless one already starts close by.
        for (b in breaks.filter { it.durationS >= Moments.MIN_STOP_S }) {
            if (b.distanceM < routeLengthM * 0.1 || b.distanceM > routeLengthM * 0.9) continue
            if (starts.any { abs(it.first - b.distanceM) < routeLengthM * 0.1 }) continue
            starts += Triple(b.distanceM, "After the break", "${b.durationS / 60} min stop")
        }
        starts.sortBy { it.first }
        if (starts.size < 2) return emptyList()
        // Too many: keep the first and the longest others.
        val kept = if (starts.size <= MAX_CHAPTERS) starts else {
            val lengths = starts.mapIndexed { i, s -> i to ((starts.getOrNull(i + 1)?.first ?: routeLengthM) - s.first) }
            val keep = (listOf(0) + lengths.drop(1).sortedByDescending { it.second }.take(MAX_CHAPTERS - 1).map { it.first }).sorted()
            keep.map { starts[it] }
        }
        // The last act reads better as the run home.
        return kept.mapIndexed { i, (from, title, detail) ->
            val name = if (i == kept.lastIndex && title == Kind.OPEN.title) "The run home" else if (i == 0 && title == Kind.OPEN.title) "Setting off" else title
            Chapter(i + 1, from, name, detail)
        }
    }
}

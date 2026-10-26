package dev.gdk.bumblebee.video

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.min

/**
 * The rider's soundtrack for ride videos: one track at a time, copied into the app (so renders
 * don't depend on the picker's short-lived access) and analysed once for its beat and loudness.
 * Blocking; use off the main thread.
 */
class MusicStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "video-music")
    private val audio = File(dir, "track.audio")
    private val analysis = File(dir, "track.txt")

    /** The current track with its analysis, or null when none has been chosen. */
    fun track(): MusicTrack? = if (!audio.exists()) null else runCatching { MusicTrack.decode(analysis.readText()) }.getOrNull()

    /** The audio file itself, for the render and the Studio's listen-through. */
    fun file(): File? = audio.takeIf { it.exists() }

    /**
     * Copies [uri] in and finds its beat, replacing any earlier track. Throws with a reason the
     * rider can read when the file can't be used.
     */
    fun import(uri: Uri): MusicTrack {
        dir.mkdirs()
        val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.substringBeforeLast('.')?.take(60) ?: "Your track"
        val tmp = File(dir, "incoming.audio")
        app.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out ->
                val copied = input.copyTo(out)
                if (copied > MAX_BYTES) error("That file is too big (over ${MAX_BYTES / 1_048_576} MB).")
            }
        } ?: error("Couldn't open that file.")
        try {
            val track = analyze(tmp, name)
            if (track.durationS < 10) error("That track is too short (under 10 s).")
            tmp.renameTo(audio.also { it.delete() })
            analysis.writeText(track.encode())
            return track
        } finally {
            tmp.delete()
        }
    }

    fun remove() {
        audio.delete()
        analysis.delete()
    }

    /** Decodes the whole track, keeping only an energy envelope (and its bass) per 512 samples. */
    private fun analyze(file: File, name: String): MusicTrack {
        val energy = FloatList()
        val low = FloatList()
        var hopS = 512 / 44_100.0
        var lp = 0.0
        var alpha = 0.0
        var sumE = 0.0
        var sumL = 0.0
        var count = 0
        AudioDecoder.decode(file, 0.0, MAX_SECONDS) { pcm, n, channels, rate ->
            if (alpha == 0.0) {
                alpha = 1 - exp(-2 * Math.PI * 150 / rate)
                hopS = 512.0 / rate
            }
            var i = 0
            while (i + channels <= n) {
                var m = 0.0
                for (c in 0 until channels) m += pcm[i + c]
                m /= channels * 32768.0
                lp += alpha * (m - lp)
                sumE += m * m
                sumL += lp * lp
                if (++count == 512) {
                    energy.add((sumE / 512).toFloat())
                    low.add((sumL / 512).toFloat())
                    sumE = 0.0
                    sumL = 0.0
                    count = 0
                }
                i += channels
            }
        }
        if (energy.size < 100) error("Couldn't read any sound from that file.")
        return BeatTracker.analyze(name, energy.toArray(), low.toArray(), hopS)
    }

    private class FloatList {
        private var a = FloatArray(4096)
        var size = 0
            private set

        fun add(v: Float) {
            if (size == a.size) a = a.copyOf(a.size * 2)
            a[size++] = v
        }

        fun toArray() = a.copyOf(size)
    }

    companion object {
        const val MAX_BYTES = 80L * 1_048_576
        const val MAX_SECONDS = 20 * 60.0
    }
}

/** Decodes an audio file to 16-bit PCM with the phone's own decoders. */
object AudioDecoder {
    /**
     * Calls [onPcm] with interleaved samples (count, channels, sample rate) from [fromS] to
     * [toS] seconds. Handles decoders that put out float samples.
     */
    fun decode(file: File, fromS: Double, toS: Double, onPcm: (ShortArray, Int, Int, Int) -> Unit) {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(file.path)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("That file has no sound we can read.")
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            val fromUs = (fromS * 1e6).toLong()
            val toUs = (toS * 1e6).toLong()
            if (fromUs > 0) ex.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var float = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var buf = ShortArray(0)
            while (true) {
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val input = c.getInputBuffer(i)!!
                        val size = ex.readSampleData(input, 0)
                        if (size < 0 || ex.sampleTime > toUs) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = c.outputFormat
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    o >= 0 -> {
                        val out = c.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val n = if (float) info.size / 4 else info.size / 2
                        if (buf.size < n) buf = ShortArray(n)
                        if (float) {
                            val fb = out.asFloatBuffer()
                            for (k in 0 until n) buf[k] = (fb.get(k).coerceIn(-1f, 1f) * 32767).toInt().toShort()
                        } else {
                            out.asShortBuffer().get(buf, 0, n)
                        }
                        // Drop what came before the start (decoding begins at a sync point).
                        val startUs = info.presentationTimeUs
                        var skip = 0
                        if (startUs < fromUs) {
                            val frames = ((fromUs - startUs) * rate / 1_000_000L).toInt()
                            skip = min(n, frames * channels)
                        }
                        if (n - skip > 0 && startUs <= toUs) {
                            if (skip > 0) System.arraycopy(buf, skip, buf, 0, n - skip)
                            onPcm(buf, n - skip, channels, rate)
                        }
                        c.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }
}

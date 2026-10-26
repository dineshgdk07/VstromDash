package dev.gdk.bumblebee.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Puts the soundtrack under a finished silent video: the chosen stretch of the track is decoded,
 * shaped by the cue's volume and fades, encoded to AAC and written into a new MP4 beside the
 * video's own frames, which are copied as they are.
 */
object MusicMixer {
    private const val OUT_RATE_FALLBACK = 44_100
    private const val BIT_RATE = 192_000

    private class Sample(val data: ByteArray, val timeUs: Long, val flags: Int)

    fun mux(silent: File, audio: File, cue: MusicCue, out: File) {
        val (samples, format) = encode(audio, cue)
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val ex = MediaExtractor()
        try {
            ex.setDataSource(silent.path)
            val vt = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            ex.selectTrack(vt)
            val videoTrack = muxer.addTrack(ex.getTrackFormat(vt))
            val audioTrack = muxer.addTrack(format)
            muxer.start()
            val buf = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                info.set(0, size, ex.sampleTime, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(videoTrack, buf, info)
                ex.advance()
            }
            for (s in samples) {
                val b = ByteBuffer.wrap(s.data)
                info.set(0, s.data.size, s.timeUs, s.flags)
                muxer.writeSampleData(audioTrack, b, info)
            }
            muxer.stop()
        } finally {
            ex.release()
            runCatching { muxer.release() }
        }
    }

    /** The cue's stretch of the track as AAC samples, and their format for the muxer. */
    private fun encode(audio: File, cue: MusicCue): Pair<List<Sample>, MediaFormat> {
        // Decode the stretch to stereo 16-bit, with the volume and fades applied.
        var rate = 0
        var pcm = ShortArray(0)
        var frames = 0
        val endS = cue.endS
        AudioDecoder.decode(audio, cue.offsetS, cue.offsetS + endS + 0.5) { src, n, channels, r ->
            if (rate == 0) rate = r
            val need = (frames + n / channels) * 2
            if (pcm.size < need) pcm = pcm.copyOf(maxOf(need, pcm.size * 2))
            var i = 0
            while (i + channels <= n) {
                val t = frames.toDouble() / rate
                if (t >= endS) break
                val g = cue.gain(t)
                val l = src[i]
                val rr = if (channels > 1) src[i + 1] else l
                pcm[frames * 2] = (l * g).toInt().toShort()
                pcm[frames * 2 + 1] = (rr * g).toInt().toShort()
                frames++
                i += channels
            }
        }
        if (rate == 0) rate = OUT_RATE_FALLBACK
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val out = ArrayList<Sample>()
        var outFormat: MediaFormat? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var fed = 0
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val input = codec.getInputBuffer(i)!!
                        input.clear()
                        val chunk = min(frames - fed, input.remaining() / 4)
                        if (chunk <= 0) {
                            codec.queueInputBuffer(i, 0, 0, fed * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val sb = input.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                            sb.put(pcm, fed * 2, chunk * 2)
                            codec.queueInputBuffer(i, 0, chunk * 4, fed * 1_000_000L / rate, 0)
                            fed += chunk
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                    o >= 0 -> {
                        val b = codec.getOutputBuffer(o)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            b.position(info.offset)
                            b.limit(info.offset + info.size)
                            val data = ByteArray(info.size)
                            b.get(data)
                            out += Sample(data, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return out to (outFormat ?: error("The audio encoder gave no format."))
    }
}

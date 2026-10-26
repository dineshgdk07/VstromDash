package dev.gdk.bumblebee.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/** Where an export got to. */
sealed interface VideoProgress {
    data object Preparing : VideoProgress
    data class Rendering(val frame: Int, val of: Int) : VideoProgress {
        val fraction: Float get() = if (of == 0) 0f else frame.toFloat() / of
    }
    data object Saving : VideoProgress
}

/**
 * Renders a [RideVideoPlan] to an MP4: each frame is drawn by [TerrainRenderer] with the
 * [VideoOverlay] on top and handed to [VideoEncoder]. GL needs one thread for its whole life, so
 * the work runs on a private single thread. Cancelling the calling coroutine stops between frames
 * and deletes the partial file.
 */
class RideVideoExporter(private val context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)

    suspend fun render(
        plan: RideVideoPlan,
        look: VideoLook,
        fonts: VideoFonts,
        accent: Int,
        out: File,
        onProgress: (VideoProgress) -> Unit,
    ) {
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ride-video-gl") }
        val gl = executor.asCoroutineDispatcher()
        try {
            withContext(gl) { renderOnGlThread(plan, look, fonts, accent, out, onProgress) }
        } catch (e: Throwable) {
            out.delete()
            throw e
        } finally {
            gl.close()
        }
    }

    private suspend fun renderOnGlThread(
        plan: RideVideoPlan,
        look: VideoLook,
        fonts: VideoFonts,
        accent: Int,
        out: File,
        onProgress: (VideoProgress) -> Unit,
    ) {
        val shape = plan.shape
        var encoder: VideoEncoder? = null
        var renderer: TerrainRenderer? = null
        var overlay: VideoOverlay? = null
        try {
            encoder = try {
                VideoEncoder(out, shape.width, shape.height, plan.fps, BIT_RATE)
            } catch (e: Exception) {
                // Some encoders top out below 1080p: fall back to 720p on the short side.
                VideoEncoder(out, shape.width * 2 / 3 / 2 * 2, shape.height * 2 / 3 / 2 * 2, plan.fps, BIT_RATE / 2)
            }
            encoder.makeCurrent()
            renderer = TerrainRenderer(plan, look, encoder.width, encoder.height)
            overlay = VideoOverlay(plan, fonts, accent)
            val scaledOverlay = encoder.width != overlay.width
            val frames = plan.frameCount
            for (i in 0 until frames) {
                coroutineContext.ensureActive()
                val t = plan.timeOf(i)
                val layer = overlay.draw(t)
                if (scaledOverlay) {
                    val scaled = android.graphics.Bitmap.createScaledBitmap(layer, encoder.width, encoder.height, true)
                    renderer.draw(t, scaled)
                    scaled.recycle()
                } else {
                    renderer.draw(t, layer)
                }
                encoder.frame(i)
                if (i % 5 == 0) onProgress(VideoProgress.Rendering(i, frames))
                coolDown()
            }
            encoder.finish()
        } finally {
            renderer?.release()
            overlay?.release()
            encoder?.release()
        }
    }

    /** Eases off when the phone reports it's getting hot, rather than throttling hard later. */
    private fun coolDown() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        when (power?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) {
            PowerManager.THERMAL_STATUS_MODERATE -> Thread.sleep(15)
            PowerManager.THERMAL_STATUS_SEVERE -> Thread.sleep(60)
            in PowerManager.THERMAL_STATUS_CRITICAL..PowerManager.THERMAL_STATUS_SHUTDOWN -> Thread.sleep(200)
        }
    }

    /**
     * Copies a finished video into the gallery under Movies/Bumblebee and returns its content
     * Uri, or null below Android 10, where the caller shares the file itself.
     */
    fun saveToGallery(file: File, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Bumblebee")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } } ?: error("Couldn't open the gallery file")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private companion object {
        const val BIT_RATE = 12_000_000
    }
}

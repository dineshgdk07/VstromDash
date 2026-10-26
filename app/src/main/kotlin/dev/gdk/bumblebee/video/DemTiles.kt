package dev.gdk.bumblebee.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Elevation tiles for ride videos: AWS Terrain Tiles (Terrarium PNGs), kept on disk so a second
 * export of the same area needs no network. Blocking; call from a background thread.
 */
class DemTiles(context: Context, private val maxBytes: Long = 150L * 1024 * 1024) {
    private val root = File(context.applicationContext.cacheDir, "dem")

    /** Tiles that couldn't be had this session, so a dead connection isn't retried per tile. */
    var failures = 0
        private set

    fun tile(z: Int, x: Int, y: Int): IntArray? {
        val file = File(root, "$z/$x/$y.png")
        if (!file.exists()) {
            if (failures >= MAX_FAILURES) return null
            val bytes = download(z, x, y)
            if (bytes == null) {
                failures++
                return null
            }
            file.parentFile?.mkdirs()
            runCatching { file.writeBytes(bytes) }
        } else {
            file.setLastModified(System.currentTimeMillis())
        }
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            // Heights are encoded in the colour channels; any colour management would corrupt them.
            inPremultiplied = false
            inScaled = false
        }
        val bmp = BitmapFactory.decodeFile(file.path, opts) ?: run {
            file.delete()
            return null
        }
        return try {
            if (bmp.width != Terrarium.TILE || bmp.height != Terrarium.TILE) return null
            IntArray(Terrarium.TILE * Terrarium.TILE).also { bmp.getPixels(it, 0, Terrarium.TILE, 0, 0, Terrarium.TILE, Terrarium.TILE) }
        } finally {
            bmp.recycle()
        }
    }

    private fun download(z: Int, x: Int, y: Int): ByteArray? = runCatching {
        val conn = URL(Terrarium.url(z, x, y)).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "BumblebeeDash (ride video)")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null else conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Size of the cache on disk, in bytes. */
    fun bytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Drops the least recently used tiles until the cache is under three quarters of its limit. */
    fun trim() {
        val files = root.walkTopDown().filter { it.isFile }.toList()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes * 3 / 4) break
            total -= f.length()
            f.delete()
        }
    }

    fun clear() {
        root.deleteRecursively()
    }

    private companion object {
        const val MAX_FAILURES = 4
    }
}

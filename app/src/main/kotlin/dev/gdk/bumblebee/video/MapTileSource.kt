package dev.gdk.bumblebee.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenStreetMap map tiles as pixels, for finding lakes and rivers in ride videos. Shares the
 * on-disk cache of the app's maps (cacheDir/osm), follows the same tile policy (identifying user
 * agent, cached tiles reused) and gives up after a few failures when offline. Blocking.
 */
class MapTileSource(context: Context) {
    private val root = File(context.applicationContext.cacheDir, "osm")
    private var failures = 0

    fun tile(z: Int, x: Int, y: Int): IntArray? {
        val file = File(root, "$z/$x/$y.png")
        if (!file.exists()) {
            if (failures >= 4) return null
            val bytes = download(z, x, y) ?: run {
                failures++
                return null
            }
            file.parentFile?.mkdirs()
            runCatching { file.writeBytes(bytes) }
        }
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: return null
        return try {
            if (bmp.width != 256 || bmp.height != 256) null else IntArray(256 * 256).also { bmp.getPixels(it, 0, 256, 0, 0, 256, 256) }
        } finally {
            bmp.recycle()
        }
    }

    private fun download(z: Int, x: Int, y: Int): ByteArray? = runCatching {
        val conn = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "BumblebeeDash/0.2 (dev.gdk.bumblebee; ride maps)")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null else conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

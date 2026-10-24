package dev.gdk.bumblebee.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.gdk.bumblebee.ride.TileCover
import dev.gdk.bumblebee.ride.TileId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenStreetMap raster tiles. Cached on disk. OSM requires a identifying User-Agent.
 */
class OsmTileStore(context: Context) {
    private val root = File(context.cacheDir, "osm")

    suspend fun load(cover: TileCover): Map<TileId, Bitmap> = withContext(Dispatchers.IO) {
        cover.tiles().mapNotNull { id ->
            decode(id)?.let { id to it }
        }.toMap()
    }

    private fun decode(id: TileId): Bitmap? {
        val file = File(root, "${id.z}/${id.x}/${id.y}.png")
        if (file.exists()) return BitmapFactory.decodeFile(file.absolutePath)
        val bytes = fetch(id) ?: return null
        file.parentFile?.mkdirs()
        runCatching { file.writeBytes(bytes) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun fetch(id: TileId): ByteArray? {
        val url = URL("https://tile.openstreetmap.org/${id.z}/${id.x}/${id.y}.png")
        return runCatching {
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", USER_AGENT)
                try {
                    if (responseCode != HttpURLConnection.HTTP_OK) return@run null
                    inputStream.use { it.readBytes() }
                } finally {
                    disconnect()
                }
            }
        }.getOrNull()
    }

    private companion object {
        const val USER_AGENT = "BumblebeeDash/0.1 (dev.gdk.bumblebee; ride track preview)"
    }
}

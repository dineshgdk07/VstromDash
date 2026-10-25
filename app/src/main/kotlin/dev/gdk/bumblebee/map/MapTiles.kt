package dev.gdk.bumblebee.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.gdk.bumblebee.ride.TileId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenStreetMap tiles for the interactive maps: a memory cache over a disk cache over the network.
 *
 * Follows the OSM tile policy: an identifying User-Agent, at most two downloads at once, tiles kept
 * on disk and only re-fetched after a month. Tiles that scroll out of view before their turn are
 * dropped rather than downloaded.
 */
object MapTiles {
    private const val USER_AGENT = "BumblebeeDash/0.2 (dev.gdk.bumblebee; ride maps)"
    private const val STALE_MS = 30L * 24 * 3600 * 1000
    private const val DISK_MAX = 200L * 1024 * 1024
    private const val DISK_KEEP = 150L * 1024 * 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val network = Semaphore(2)
    private val loading = ConcurrentHashMap.newKeySet<TileId>()
    private val failed = ConcurrentHashMap<TileId, Long>()

    /** Recent tiles, about 48 MB of them. */
    private val memory = object : LruCache<TileId, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: TileId, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Bumps whenever a tile arrives, so maps reading it redraw. */
    val version = mutableIntStateOf(0)

    @Volatile private var wanted: Set<TileId> = emptySet()
    @Volatile private var trimmed = false

    fun cached(id: TileId): ImageBitmap? = memory.get(id)

    /** Asks for the tiles on screen now; anything queued that is no longer wanted is skipped. */
    fun request(context: Context, ids: List<TileId>) {
        wanted = ids.toSet()
        val root = File(context.applicationContext.cacheDir, "osm")
        if (!trimmed) {
            trimmed = true
            scope.launch { trim(root) }
        }
        val now = System.currentTimeMillis()
        for (id in ids) {
            if (memory.get(id) != null) continue
            if ((failed[id] ?: 0L) > now - 20_000) continue
            if (!loading.add(id)) continue
            scope.launch {
                try {
                    val bmp = load(root, id)
                    if (bmp != null) {
                        memory.put(id, bmp)
                        failed.remove(id)
                        version.intValue++
                    } else {
                        failed[id] = System.currentTimeMillis()
                    }
                } finally {
                    loading.remove(id)
                }
            }
        }
    }

    private suspend fun load(root: File, id: TileId): ImageBitmap? {
        val file = File(root, "${id.z}/${id.x}/${id.y}.png")
        val onDisk = file.takeIf { it.exists() }
        if (onDisk != null && System.currentTimeMillis() - onDisk.lastModified() < STALE_MS) {
            decode(onDisk.readBytes())?.let { return it }
        }
        if (id !in wanted) return onDisk?.let { decode(it.readBytes()) }
        val bytes = network.withPermit { if (id in wanted || onDisk == null) fetch(id) else null }
        if (bytes != null) {
            file.parentFile?.mkdirs()
            runCatching { file.writeBytes(bytes) }
            return decode(bytes)
        }
        // Offline: an old tile beats no tile.
        return onDisk?.let { decode(it.readBytes()) }
    }

    private fun decode(bytes: ByteArray): ImageBitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

    private fun fetch(id: TileId): ByteArray? = runCatching {
        val url = URL("https://tile.openstreetmap.org/${id.z}/${id.x}/${id.y}.png")
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", USER_AGENT)
            try {
                if (responseCode != HttpURLConnection.HTTP_OK) null else inputStream.use { it.readBytes() }
            } finally {
                disconnect()
            }
        }
    }.getOrNull()

    /** Keeps the disk cache bounded: oldest tiles go first. */
    private fun trim(root: File) {
        val files = root.walkTopDown().filter { it.isFile }.toList()
        var total = files.sumOf { it.length() }
        if (total <= DISK_MAX) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= DISK_KEEP) break
            total -= f.length()
            f.delete()
        }
    }
}

package dev.gdk.bumblebee.ui.video

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ui.theme.Accent
import dev.gdk.bumblebee.video.DemTiles
import dev.gdk.bumblebee.video.RideVideoExporter
import dev.gdk.bumblebee.video.RideVideoInput
import dev.gdk.bumblebee.video.RideVideoPlan
import dev.gdk.bumblebee.video.RideVideoSource
import dev.gdk.bumblebee.video.VideoConfig
import dev.gdk.bumblebee.video.VideoExportService
import dev.gdk.bumblebee.video.VideoExportState
import dev.gdk.bumblebee.video.VideoLook
import dev.gdk.bumblebee.video.VideoPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class StudioUi(
    val config: VideoConfig = VideoConfig(),
    val plan: RideVideoPlan? = null,
    val look: VideoLook? = null,
    /** Building the plan: fetching terrain the first time, quick after. */
    val preparing: Boolean = true,
    /** Why there can't be a video for this ride, if so. */
    val blocked: String? = null,
    val message: String? = null,
    val savedTo: String? = null,
    /** The bike and photo pictures for the preview's overlay. */
    val assets: dev.gdk.bumblebee.video.VideoAssets = dev.gdk.bumblebee.video.VideoAssets(),
    /** The ride's full length, for placing photos by hand. */
    val rideLengthM: Double = 0.0,
    val importing: Boolean = false,
)

/**
 * The Ride Video Studio: holds the rider's choices, rebuilds the plan behind the live preview as
 * they change (after a short pause, so dragging a slider doesn't rebuild per pixel), and starts,
 * saves and shares renders. The last choices are remembered for next time, minus the per-ride
 * title and length.
 */
class VideoStudioViewModel(app: Application, private val rideId: Long) : AndroidViewModel(app) {
    private val dash = app as DashApp
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val tiles = DemTiles(app)
    private val tileMemory = ConcurrentHashMap<Long, IntArray>()
    private val maps = dev.gdk.bumblebee.video.MapTileSource(app)
    private val mapMemory = ConcurrentHashMap<Long, IntArray>()

    private fun mapTile(z: Int, x: Int, y: Int): IntArray? {
        val key = (z.toLong() shl 48) or (x.toLong() shl 24) or y.toLong()
        mapMemory[key]?.let { return it }
        return maps.tile(z, x, y)?.also { mapMemory[key] = it }
    }
    private var input: RideVideoInput? = null
    private val photoStore = dev.gdk.bumblebee.video.VideoPhotoStore(app)
    private val pictureCache = ConcurrentHashMap<String, android.graphics.Bitmap>()
    private var bikePicture: Pair<Int, android.graphics.Bitmap>? = null
    private var rebuild: Job? = null

    private val _ui = MutableStateFlow(StudioUi(config = VideoConfig.decode(prefs.getString(KEY_CONFIG, null))))
    val ui: StateFlow<StudioUi> = _ui.asStateFlow()

    val export: StateFlow<VideoExportState> get() = VideoExportService.state

    init {
        viewModelScope.launch {
            input = withContext(Dispatchers.IO) { RideVideoSource.load(dash, rideId) }
            input?.let { inp ->
                var total = 0.0
                for (i in 1 until inp.track.size) {
                    val a = inp.track[i - 1]
                    val b = inp.track[i]
                    total += dev.gdk.bumblebee.ride.Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng)
                }
                _ui.update { it.copy(rideLengthM = total) }
            }
            if (input == null) {
                _ui.update { it.copy(preparing = false, blocked = "This ride has no GPS track to fly over.") }
            } else {
                rebuildNow()
            }
        }
    }

    /** Tiles decoded once per Studio visit; the disk cache keeps them between visits. */
    private fun tile(z: Int, x: Int, y: Int): IntArray? {
        val key = (z.toLong() shl 48) or (x.toLong() shl 24) or y.toLong()
        tileMemory[key]?.let { return it }
        return tiles.tile(z, x, y)?.also { tileMemory[key] = it }
    }

    /** Applies a change; anything but picking a preset turns the preset to Custom. */
    fun update(change: (VideoConfig) -> VideoConfig) {
        val next = change(_ui.value.config).clamped().let { c -> if (c.preset == _ui.value.config.preset) c.copy(preset = VideoPreset.CUSTOM) else c }
        set(next)
    }

    /** Size, length and title don't make the look custom. */
    fun updateKeepingPreset(change: (VideoConfig) -> VideoConfig) = set(change(_ui.value.config).clamped())

    fun preset(p: VideoPreset) = set(_ui.value.config.withPreset(p))

    private fun set(c: VideoConfig) {
        _ui.update { it.copy(config = c) }
        prefs.edit().putString(KEY_CONFIG, c.copy(title = null, lengthS = null).encode()).apply()
        rebuild?.cancel()
        rebuild = viewModelScope.launch {
            delay(350)
            rebuildNow()
        }
    }

    private suspend fun rebuildNow() {
        val inp = input ?: return
        _ui.update { it.copy(preparing = true) }
        val config = _ui.value.config
        val plan = withContext(Dispatchers.Default) { runCatching { RideVideoPlan.build(inp, config, ::tile, ::mapTile) }.getOrNull() }
        if (plan == null) {
            _ui.update { it.copy(preparing = false, plan = null, blocked = "The ride is too short for a video once the first and last 500 m are hidden.") }
            return
        }
        val assets = withContext(Dispatchers.IO) { assetsFor(plan) }
        _ui.update {
            it.copy(
                preparing = false,
                plan = plan,
                assets = assets,
                look = VideoLook(plan.sun, Accent.color.toArgb()),
                blocked = null,
                message = if (plan.flat) "Couldn't get terrain (offline?). The preview is flat; connect and reopen for hills." else null,
            )
        }
    }

    /** The bike and photos the preview needs, decoded once per visit. */
    private fun assetsFor(plan: RideVideoPlan): dev.gdk.bumblebee.video.VideoAssets {
        val app = getApplication<Application>()
        val variant = dev.gdk.bumblebee.bike.BikeProfile(app).variant
        val bike = if (plan.config.marker == dev.gdk.bumblebee.video.MarkerStyle.BIKE_PHOTO) {
            val res = variant.image
            bikePicture?.takeIf { it.first == res }?.second
                ?: android.graphics.BitmapFactory.decodeResource(app.resources, res)?.also { bikePicture = res to it }
        } else {
            null
        }
        val photos = plan.timeline.moments.mapNotNull { m ->
            val id = m.photoId ?: return@mapNotNull null
            (pictureCache[id] ?: photoStore.bitmap(rideId, id, 900)?.also { pictureCache[id] = it })?.let { id to it }
        }.toMap()
        return dev.gdk.bumblebee.video.VideoAssets(bike, photos, variant.paint.toArgb() and 0xFFFFFF)
    }

    /** Photo thumbnails for the list, small. */
    fun thumbnail(id: String): android.graphics.Bitmap? = photoStore.bitmap(rideId, id, 240)

    fun addPhotos(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(importing = true) }
            val failed = withContext(Dispatchers.IO) { photoStore.add(rideId, uris) }
            _ui.update { it.copy(importing = false, message = if (failed > 0) "$failed photo${if (failed == 1) "" else "s"} couldn't be read." else null) }
            reloadPhotos()
        }
    }

    fun removePhoto(id: String) = photoChange { photoStore.remove(rideId, id); pictureCache.remove(id) }

    /** Puts a photo at [rideM] metres from the start, or back to automatic with null. */
    fun placePhoto(id: String, rideM: Double?) = photoChange { photoStore.setManual(rideId, id, rideM) }

    private var captionJob: Job? = null

    /** Saves a caption once typing pauses, so each keystroke doesn't rebuild the preview. */
    fun captionPhoto(id: String, caption: String) {
        captionJob?.cancel()
        captionJob = viewModelScope.launch {
            delay(700)
            withContext(Dispatchers.IO) { photoStore.setCaption(rideId, id, caption) }
            reloadPhotos()
        }
    }

    private fun photoChange(change: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { change() }
            reloadPhotos()
        }
    }

    private suspend fun reloadPhotos() {
        val inp = input ?: return
        input = inp.copy(photos = withContext(Dispatchers.IO) { photoStore.list(rideId) })
        rebuild?.cancel()
        rebuildNow()
    }

    fun render() {
        if (_ui.value.plan == null) return
        VideoExportService.start(getApplication(), rideId, _ui.value.config)
    }

    fun cancel() = VideoExportService.cancel(getApplication())

    /** Back to the setup, optionally in another shape, keeping everything else. */
    fun again(shape: dev.gdk.bumblebee.video.VideoShape? = null) {
        VideoExportService.consume()
        _ui.update { it.copy(savedTo = null) }
        if (shape != null && shape != _ui.value.config.shape) updateKeepingPreset { it.copy(shape = shape) }
    }

    fun save(done: VideoExportState.Done) {
        viewModelScope.launch {
            val name = "Bumblebee-ride-$rideId-${done.shape.name.lowercase()}-${System.currentTimeMillis() / 1000}.mp4"
            val result = withContext(Dispatchers.IO) { runCatching { RideVideoExporter(getApplication()).saveToGallery(done.file, name) } }
            _ui.update {
                it.copy(
                    savedTo = result.getOrNull()?.let { "Saved to Movies/Bumblebee" },
                    message = when {
                        result.isFailure -> "Couldn't save: ${result.exceptionOrNull()?.message}"
                        result.getOrNull() == null -> "Saving to the gallery needs Android 10 or newer. Use Share to keep it."
                        else -> null
                    },
                )
            }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    class Factory(private val app: Application, private val rideId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return VideoStudioViewModel(app, rideId) as T
        }
    }

    private companion object {
        const val PREFS = "video_studio"
        const val KEY_CONFIG = "config"
    }
}

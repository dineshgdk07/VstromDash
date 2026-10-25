package dev.gdk.bumblebee.dev

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One line in the developer log. */
data class DevLine(val ts: Long, val tag: String, val text: String) {
    fun format(): String = "${TIME.get()!!.format(Date(ts))} $tag  $text"

    private companion object {
        val TIME = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.ENGLISH) }
    }
}

/**
 * A small in-memory log of what the app did: link state, nav steps, crash watch, widgets. It never
 * leaves the phone unless the rider shares a debug report, and holds no keys, numbers or places.
 */
object DevLog {
    private const val MAX = 400
    private val lines = ArrayDeque<DevLine>()
    private val _flow = MutableStateFlow<List<DevLine>>(emptyList())
    val flow: StateFlow<List<DevLine>> = _flow.asStateFlow()

    /** Raw BLE frames are kept only while this is on, set from [AdvancedPrefs.logFrames]. */
    @Volatile var frames: Boolean = false

    /** The last raw notify from the bike, for the live view, whatever [frames] says. */
    private val _lastFrame = MutableStateFlow<Pair<Long, ByteArray>?>(null)
    val lastFrame: StateFlow<Pair<Long, ByteArray>?> = _lastFrame.asStateFlow()

    @Synchronized
    fun d(tag: String, text: String) {
        lines.addLast(DevLine(System.currentTimeMillis(), tag, text))
        while (lines.size > MAX) lines.removeFirst()
        _flow.value = lines.toList()
    }

    fun frame(bytes: ByteArray) {
        val now = System.currentTimeMillis()
        _lastFrame.value = now to bytes
        if (frames) d("ble", "rx ${hex(bytes)}")
    }

    @Synchronized
    fun clear() {
        lines.clear()
        _flow.value = emptyList()
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
}

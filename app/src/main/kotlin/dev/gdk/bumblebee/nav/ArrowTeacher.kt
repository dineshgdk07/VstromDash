package dev.gdk.bumblebee.nav

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** One Maps notification as it arrived and what the app made of it, for the Cluster check sheet. */
data class NavLogLine(
    val ts: Long,
    val title: String?,
    val text: String?,
    val subText: String?,
    val arrow: ArrowKind?,
    val source: ArrowSource,
    val iconHash: Long?,
)

/**
 * The rider's arrow corrections, saved in a small file, plus a short log of recent Maps
 * notifications. Teaching an arrow re-sends the current step straight away.
 */
object ArrowTeacher {
    private const val FILE = "arrow_memory.tsv"
    @Volatile private var loaded: ArrowMemory? = null
    private val _log = MutableStateFlow<List<NavLogLine>>(emptyList())
    val recent: StateFlow<List<NavLogLine>> = _log.asStateFlow()

    @Synchronized
    fun memory(context: Context): ArrowMemory = loaded ?: run {
        val f = File(context.applicationContext.filesDir, FILE)
        val m = runCatching { ArrowMemory.decode(f.readText()) }.getOrElse { ArrowMemory() }
        loaded = m
        m
    }

    @Synchronized
    fun teach(context: Context, arrow: ArrowKind) {
        val current = MapsNavSource.parsed.value ?: return
        val m = memory(context)
        m.teach(current.iconHash, current.instruction, arrow)
        save(context, m)
        MapsNavSource.update(current.copy(arrow = arrow, arrowSource = ArrowSource.TAUGHT, maneuverId = arrow.clusterByte), MapsNavSource.activeKey)
    }

    @Synchronized
    fun forgetAll(context: Context) {
        val m = memory(context)
        m.forget()
        save(context, m)
    }

    fun log(title: String?, text: String?, subText: String?, parsed: ParsedNavData) {
        val line = NavLogLine(System.currentTimeMillis(), title, text, subText, parsed.arrow, parsed.arrowSource, parsed.iconHash)
        val last = _log.value.firstOrNull()
        // Maps re-posts the same step every second; keep only changes.
        if (last != null && last.title == title && last.text == text && last.arrow == parsed.arrow) return
        _log.value = (listOf(line) + _log.value).take(30)
    }

    private fun save(context: Context, m: ArrowMemory) {
        runCatching { File(context.applicationContext.filesDir, FILE).writeText(m.encode()) }
    }
}

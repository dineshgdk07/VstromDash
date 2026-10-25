package dev.gdk.bumblebee.nav

import dev.gdk.bumblebee.protocol.NavFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object MapsNavSource {
    const val STALE_AFTER_MS: Long = 60_000

    private val _frame = MutableStateFlow<NavFrame?>(null)
    val frame: StateFlow<NavFrame?> = _frame.asStateFlow()

    private val _parsed = MutableStateFlow<ParsedNavData?>(null)

    /** The current step with everything Maps said about it, for the trip card and arrow fixer. */
    val parsed: StateFlow<ParsedNavData?> = _parsed.asStateFlow()

    private val _street = MutableStateFlow<String?>(null)
    val street: StateFlow<String?> = _street.asStateFlow()

    @Volatile var activeKey: String? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var staleJob: Job? = null

    /** [staleAfterMs] null keeps the step until replaced: the app's own navigation updates as it likes. */
    fun update(parsed: ParsedNavData?, key: String? = null, staleAfterMs: Long? = STALE_AFTER_MS) {
        if (parsed == null) {
            if (key != null && activeKey != null && key != activeKey) return
            clear()
            return
        }
        activeKey = key
        _parsed.value = parsed
        _frame.value = parsed.toNavFrame()
        _street.value = parsed.streetName
        staleJob?.cancel()
        if (staleAfterMs == null) return
        staleJob = scope.launch {
            delay(staleAfterMs)
            if (_frame.value != null) {
                _frame.value = null
                _parsed.value = null
                _street.value = null
                activeKey = null
            }
        }
    }

    fun clearIf(key: String) {
        if (activeKey == null || activeKey == key) clear()
    }

    fun clear() {
        _frame.value = null
        _parsed.value = null
        _street.value = null
        activeKey = null
        staleJob?.cancel()
    }
}

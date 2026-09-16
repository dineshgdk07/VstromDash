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

    private val _street = MutableStateFlow<String?>(null)
    val street: StateFlow<String?> = _street.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var staleJob: Job? = null

    fun update(parsed: ParsedNavData?) {
        _frame.value = parsed?.toNavFrame()
        _street.value = parsed?.streetName
        staleJob?.cancel()
        if (_frame.value != null) {
            staleJob = scope.launch {
                delay(STALE_AFTER_MS)
                if (_frame.value != null) {
                    _frame.value = null
                    _street.value = null
                }
            }
        }
    }

    fun clear() {
        _frame.value = null
        _street.value = null
        staleJob?.cancel()
    }
}

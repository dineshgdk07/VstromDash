package dev.gdk.bumblebee.ble

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select

/**
 * Queue of pending BLE writes with priority + drop policy.
 * URGENT (identity/call/sms) > HEARTBEAT (1 Hz) > NAV (drop identical / oldest).
 */
class FrameWriter {

    enum class Priority { URGENT, HEARTBEAT, NAV }

    data class Entry(val priority: Priority, val frame: ByteArray, val note: String? = null)

    private val urgent = Channel<Entry>(capacity = 32)
    private val heartbeat = Channel<Entry>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val nav = Channel<Entry>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _lastNav = MutableStateFlow<ByteArray?>(null)
    val lastNav: StateFlow<ByteArray?> = _lastNav.asStateFlow()

    fun enqueue(entry: Entry) {
        if (entry.priority == Priority.NAV) {
            val last = _lastNav.value
            if (last != null && last.contentEquals(entry.frame)) return
            _lastNav.value = entry.frame.copyOf()
        }
        val target = when (entry.priority) {
            Priority.URGENT -> urgent
            Priority.HEARTBEAT -> heartbeat
            Priority.NAV -> nav
        }
        target.trySend(entry)
    }

    suspend fun take(): Entry {
        val u = urgent.tryReceive(); if (u.isSuccess) return u.getOrThrow()
        val h = heartbeat.tryReceive(); if (h.isSuccess) return h.getOrThrow()
        val n = nav.tryReceive(); if (n.isSuccess) return n.getOrThrow()
        return select {
            urgent.onReceive { it }
            heartbeat.onReceive { it }
            nav.onReceive { it }
        }
    }
}

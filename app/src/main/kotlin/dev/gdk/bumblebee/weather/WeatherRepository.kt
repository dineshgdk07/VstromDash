package dev.gdk.bumblebee.weather

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.cos

/**
 * Keeps one forecast in memory and refreshes it sparingly: the HUD polls several
 * times a second, so every call funnels through the staleness and distance checks
 * below rather than hitting the network.
 */
class WeatherRepository(private val client: OpenMeteoClient = OpenMeteoClient()) {

    val snapshot = MutableStateFlow<WeatherSnapshot?>(null)

    private val gate = Mutex()
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastAttemptAt = 0L

    suspend fun maybeRefresh(lat: Double, lon: Double) {
        if (!gate.tryLock()) return
        try {
            val now = System.currentTimeMillis()
            if (now - lastAttemptAt < RETRY_FLOOR_MS) return
            val current = snapshot.value
            val stale = current == null || now - current.fetchedAt >= MAX_AGE_MS
            if (!stale && !hasMoved(lat, lon)) return

            lastAttemptAt = now
            val fresh = client.fetch(lat, lon)
            if (fresh != null) {
                snapshot.value = fresh
                lastLat = lat
                lastLon = lon
            }
        } finally {
            gate.unlock()
        }
    }

    private fun hasMoved(lat: Double, lon: Double): Boolean {
        val pLat = lastLat ?: return true
        val pLon = lastLon ?: return true
        val dLat = abs(lat - pLat) * KM_PER_DEGREE
        val dLon = abs(lon - pLon) * KM_PER_DEGREE * cos(Math.toRadians(lat))
        return dLat + dLon >= MOVE_THRESHOLD_KM
    }

    private companion object {
        const val MAX_AGE_MS = 15 * 60 * 1000L
        const val RETRY_FLOOR_MS = 60 * 1000L
        const val MOVE_THRESHOLD_KM = 25.0
        const val KM_PER_DEGREE = 111.0
    }
}

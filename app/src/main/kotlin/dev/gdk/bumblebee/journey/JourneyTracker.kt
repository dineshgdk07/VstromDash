package dev.gdk.bumblebee.journey

import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ble.BikeBridgeService
import dev.gdk.bumblebee.nav.ArrowKind
import dev.gdk.bumblebee.nav.MapsNavSource
import dev.gdk.bumblebee.ride.JourneyEntry
import dev.gdk.bumblebee.ride.JourneyInsights
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns Maps guidance into the journey log: a trip starts when guidance appears and ends when it
 * goes away. It counts as arrived if the last step was the arrival or under 300 m remained.
 * With auto-record on, guidance starting also starts a GPS ride if nothing is recording.
 */
object JourneyTracker {
    private var current: JourneyEntry? = null
    private var lastRemainingKm: Double? = null
    private var lastArrow: ArrowKind? = null

    fun start(app: DashApp, scope: CoroutineScope) {
        val prefs = JourneyPrefs(app)
        scope.launch {
            MapsNavSource.parsed.collect { step ->
                val now = System.currentTimeMillis()
                // Arrows sent from Cluster check aren't trips.
                if (step?.summary == "test") return@collect
                if (step != null) {
                    if (current == null) {
                        val zone = ZoneId.systemDefault()
                        val eta = JourneyInsights.parseEta(step.eta)?.let {
                            JourneyInsights.arrivalAt(LocalDateTime.now(zone), it).atZone(zone).toInstant().toEpochMilli()
                        }
                        current = JourneyEntry(now, null, prefs.pendingDestination, eta, JourneyInsights.km(step.distTotal, step.distTotalUnit), false)
                        prefs.pendingDestination = null
                        prefs.sharedEtaMinute = null
                        // Android can refuse a background service start; recording then waits for the rider.
                        if (prefs.autoRecord && app.capture.activeRideId == null) runCatching { BikeBridgeService.startGpsRide(app) }
                    }
                    lastRemainingKm = JourneyInsights.km(step.distTotal, step.distTotalUnit) ?: lastRemainingKm
                    lastArrow = step.arrow
                } else {
                    val trip = current ?: return@collect
                    current = null
                    // A blip under a minute (Maps re-routing) isn't a trip.
                    if (now - trip.startTs < 60_000) return@collect
                    val arrived = lastArrow == ArrowKind.ARRIVE || (lastRemainingKm ?: 99.0) < 0.3
                    prefs.log = prefs.log + trip.copy(endTs = now, arrived = arrived)
                    lastRemainingKm = null
                    lastArrow = null
                }
            }
        }
    }
}

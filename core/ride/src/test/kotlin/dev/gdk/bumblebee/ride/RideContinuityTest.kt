package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Test

class RideContinuityTest {

    private val window = 45
    private val pausedAt = 1_000_000L

    @Test
    fun `reconnect inside the window with a continuous odometer resumes`() {
        val now = pausedAt + 12 * 60_000L
        assertEquals(
            ContinuityDecision.RESUME,
            RideContinuity.onReconnect(window, pausedAt, now, lastOdoKm = 1500, nextOdoKm = 1501),
        )
    }

    @Test
    fun `reconnect inside the window after an odometer jump starts a new ride`() {
        val now = pausedAt + 10 * 60_000L
        assertEquals(
            ContinuityDecision.START_NEW,
            RideContinuity.onReconnect(window, pausedAt, now, lastOdoKm = 1500, nextOdoKm = 1503),
        )
    }

    @Test
    fun `one kilometre of odometer slack still resumes`() {
        val now = pausedAt + 60_000L
        assertEquals(
            ContinuityDecision.RESUME,
            RideContinuity.onReconnect(window, pausedAt, now, lastOdoKm = 1500, nextOdoKm = 1501),
        )
    }

    @Test
    fun `missing odometer does not invent a jump`() {
        val now = pausedAt + 60_000L
        assertEquals(
            ContinuityDecision.RESUME,
            RideContinuity.onReconnect(window, pausedAt, now, lastOdoKm = null, nextOdoKm = 1500),
        )
    }

    @Test
    fun `no reconnect before the window elapses keeps the ride paused`() {
        val now = pausedAt + 44 * 60_000L
        assertEquals(ContinuityDecision.STAY_PAUSED, RideContinuity.onRestart(window, pausedAt, now))
    }

    @Test
    fun `window elapsed finishes and the end time is the last sample`() {
        val now = pausedAt + 46 * 60_000L
        assertEquals(ContinuityDecision.FINISH, RideContinuity.onRestart(window, pausedAt, now))
        assertEquals(ContinuityDecision.FINISH, RideContinuity.onWindowElapsed())
        val lastSample = pausedAt - 5_000L
        assertEquals(lastSample, RideContinuity.finishTimestamp(lastSample, rideStartTs = 0L))
    }

    @Test
    fun `end ride now finishes immediately`() {
        assertEquals(ContinuityDecision.FINISH, RideContinuity.onEndNow())
    }

    @Test
    fun `pause window off never resumes`() {
        val now = pausedAt + 1_000L
        assertEquals(
            ContinuityDecision.START_NEW,
            RideContinuity.onReconnect(0, pausedAt, now, lastOdoKm = 10, nextOdoKm = 10),
        )
        assertEquals(ContinuityDecision.FINISH, RideContinuity.onRestart(0, pausedAt, now))
    }

    @Test
    fun `process death inside the window restores the pause`() {
        val now = pausedAt + 20 * 60_000L
        assertEquals(ContinuityDecision.STAY_PAUSED, RideContinuity.onRestart(window, pausedAt, now))
    }

    @Test
    fun `gps stationary timeout uses the same finish rule`() {
        assertEquals(ContinuityDecision.FINISH, RideContinuity.onStationaryTimeout())
    }
}

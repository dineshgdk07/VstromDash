package dev.gdk.bumblebee.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotPlanTest {

    @Test
    fun `same room id is not a clash`() {
        val incoming = bundle(rides = listOf(ride(id = 1, startTs = 10)))
        val here = bundle(rides = listOf(ride(id = 1, startTs = 20)))
        val plan = planApply(incoming, here, overwriteClashes = false)
        assertTrue(plan.clashes.isEmpty())
        assertEquals(RowAction.ADD, plan.rides.single().action)
    }

    @Test
    fun `same start time clashes`() {
        val incoming = bundle(
            rides = listOf(ride(id = 1, startTs = 10), ride(id = 2, startTs = 30)),
        )
        val here = bundle(rides = listOf(ride(id = 99, startTs = 10)))
        val kept = planApply(incoming, here, overwriteClashes = false)
        assertEquals(1, kept.clashes.size)
        assertEquals(SnapshotSections.RIDES, kept.clashes.single().section)
        assertEquals(RowAction.KEEP, kept.rides.first { it.sourceId == 1L }.action)
        assertEquals(RowAction.ADD, kept.rides.first { it.sourceId == 2L }.action)
        val replaced = planApply(incoming, here, overwriteClashes = true)
        assertEquals(RowAction.REPLACE, replaced.rides.first { it.sourceId == 1L }.action)
        assertEquals(RowAction.ADD, replaced.rides.first { it.sourceId == 2L }.action)
    }

    @Test
    fun `remote id wins over start time`() {
        val incoming = bundle(rides = listOf(ride(id = 1, startTs = 10, remoteId = "r1")))
        val here = bundle(rides = listOf(ride(id = 2, startTs = 99, remoteId = "r1")))
        val plan = planApply(incoming, here, overwriteClashes = false)
        assertEquals(1, plan.clashes.size)
        assertEquals(SnapshotSections.RIDES, plan.clashes.single().section)
    }

    @Test
    fun `fuel clashes on its own key`() {
        val incoming = bundle(
            fuel = listOf(
                fuel(id = 1, ts = 5, odometerKm = 100, type = "REFUEL"),
                fuel(id = 2, ts = 5, odometerKm = 100, type = "BAR_DROP"),
            ),
        )
        val here = bundle(fuel = listOf(fuel(id = 9, ts = 5, odometerKm = 100, type = "REFUEL")))
        val plan = planApply(incoming, here, overwriteClashes = false)
        assertEquals(SnapshotSections.FUEL, plan.clashes.single().section)
        assertEquals(RowAction.KEEP, plan.fuels.first { it.sourceId == 1L }.action)
        assertEquals(RowAction.ADD, plan.fuels.first { it.sourceId == 2L }.action)
    }

    @Test
    fun `blank document numbers are the same key`() {
        val incoming = bundle(documents = listOf(doc(id = 1, number = " ")))
        val here = bundle(documents = listOf(doc(id = 2, number = null)))
        val plan = planApply(incoming, here, overwriteClashes = false)
        assertEquals(1, plan.clashes.size)
        assertEquals(SnapshotSections.DOCUMENTS, plan.clashes.single().section)
    }

    @Test
    fun `rollup date and calibration key clash`() {
        val incoming = bundle(
            rollups = listOf(rollup("2026-01-01"), rollup("2026-01-02")),
            calibration = listOf(SnapshotCalibration("km-per-bar", "1"), SnapshotCalibration("other", "2")),
        )
        val here = bundle(
            rollups = listOf(rollup("2026-01-01")),
            calibration = listOf(SnapshotCalibration("km-per-bar", "9")),
        )
        val plan = planApply(incoming, here, overwriteClashes = false)
        assertEquals(RowAction.KEEP, plan.rollups["2026-01-01"])
        assertEquals(RowAction.ADD, plan.rollups["2026-01-02"])
        assertEquals(RowAction.KEEP, plan.calibration["km-per-bar"])
        assertEquals(RowAction.ADD, plan.calibration["other"])
    }

    @Test
    fun `default prefs are not a clash`() {
        val here = bundle()
        val incoming = bundle(prefs = defaultPrefs(bikeMac = "AA:BB"))
        assertTrue(here.isEmptyPhone())
        val plan = planApply(incoming, here, overwriteClashes = false)
        assertEquals(RowAction.ADD, plan.prefs)
        assertTrue(plan.clashes.isEmpty())
    }

    @Test
    fun `a saved bike mac can clash`() {
        val here = bundle(prefs = defaultPrefs(bikeMac = "AA"))
        val incoming = bundle(prefs = defaultPrefs(bikeMac = "BB"))
        assertEquals(RowAction.KEEP, planApply(incoming, here, overwriteClashes = false).prefs)
        assertEquals(RowAction.REPLACE, planApply(incoming, here, overwriteClashes = true).prefs)
    }

    @Test
    fun `open ride blocks packing`() {
        val active = packIssues(bundle(rides = listOf(ride(1, 10, state = "ACTIVE"))), emptyList())
        val paused = packIssues(bundle(rides = listOf(ride(1, 10, state = "PAUSED"))), emptyList())
        assertEquals("Finish or discard the open ride before packing.", active.single().message)
        assertEquals("Finish or discard the open ride before packing.", paused.single().message)
    }

    @Test
    fun `missing scan blocks packing`() {
        val issues = packIssues(bundle(), listOf("files/rc.pdf"))
        assertEquals("A paper is missing its scan.", issues.single().message)
        assertEquals("files/rc.pdf", issues.single().log)
    }
}

private fun ride(id: Long, startTs: Long, remoteId: String? = null, state: String = "FINISHED") =
    SnapshotRide(id = id, startTs = startTs, remoteId = remoteId, rideState = state)

private fun fuel(id: Long, ts: Long, odometerKm: Int, type: String) = SnapshotFuel(
    id = id,
    ts = ts,
    rideId = null,
    odometerKm = odometerKm,
    barsBefore = 1,
    barsAfter = 5,
    type = type,
    remoteId = null,
)

private fun doc(id: Long, number: String?) = SnapshotDocument(
    id = id,
    title = "RC",
    kind = "RC",
    number = number,
    createdTs = 0L,
)

private fun rollup(date: String) = SnapshotRollup(
    date = date,
    km = 1.0,
    rides = 1,
    movingSec = 1,
    maxSpeedKmh = 1,
    fuelBarsUsed = 0,
)

private fun defaultPrefs(bikeMac: String? = null) = SnapshotPrefs(
    bikeMac = bikeMac,
    riderName = "RIDER",
    serviceIntervalKm = 0,
    lastServiceOdoKm = null,
    rcExpiry = "",
    insuranceExpiry = "",
    pucExpiry = "",
    mapsNavToCluster = true,
    tankCapacityL = 12f,
    remindersEnabled = true,
    remindDays = 14,
    remindKm = 200,
    pauseWindowMin = 45,
    autoConnect = true,
    skipAutoConnectDate = "",
    crashSensitivity = "NORMAL",
    emergencyContacts = "",
    bloodGroup = "",
    medicalNote = "",
    lastReminderStamp = "",
    widget = SnapshotWidget(
        fuelBars = null,
        rangeKm = null,
        serviceDueKm = null,
        alert = "",
        todayKm = 0.0,
        lastRideKm = 0.0,
        lastRideLabel = "",
        linked = false,
        open = "garage",
        atMs = 0L,
    ),
)

private fun bundle(
    rides: List<SnapshotRide> = emptyList(),
    fuel: List<SnapshotFuel> = emptyList(),
    documents: List<SnapshotDocument> = emptyList(),
    rollups: List<SnapshotRollup> = emptyList(),
    calibration: List<SnapshotCalibration> = emptyList(),
    prefs: SnapshotPrefs = defaultPrefs(),
) = SnapshotBundle(
    rides = rides,
    fuel = fuel,
    documents = documents,
    rollups = rollups,
    calibration = calibration,
    prefs = prefs,
)

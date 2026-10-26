package dev.gdk.bumblebee.ui.transfer

import dev.gdk.bumblebee.snapshot.Clash
import dev.gdk.bumblebee.snapshot.PackIssue
import dev.gdk.bumblebee.snapshot.SnapshotBundle
import dev.gdk.bumblebee.snapshot.SnapshotPrefs
import dev.gdk.bumblebee.snapshot.SnapshotRide
import dev.gdk.bumblebee.snapshot.SnapshotWidget
import dev.gdk.bumblebee.transfer.BringIn
import dev.gdk.bumblebee.transfer.PackPreview
import dev.gdk.bumblebee.transfer.TransferEvents
import dev.gdk.bumblebee.transfer.TransferGateway
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream

class TransferViewModelTest {

    private val fake = object : TransferGateway {
        override suspend fun preview() = error("unused")
        override suspend fun writeTo(out: OutputStream, events: TransferEvents) = 0L
        override suspend fun inspect(input: InputStream, events: TransferEvents) = error("unused")
        override suspend fun commit(bundle: SnapshotBundle, overwriteClashes: Boolean, events: TransferEvents) = emptyList<String>()
    }

    @Test
    fun `pack lists the sections and a clean preview can save`() {
        val model = TransferViewModel(fake)
        model.onPackLoaded(preview(issues = emptyList(), mac = "AA"))
        val ui = model.ui.value
        assertEquals("PACK THE BIKE", ui.shout)
        assertTrue(ui.canSave)
        assertEquals(
            listOf("Rides and tracks", "Fuel log", "Daily rollups", "Service and plans", "Garage papers", "Settings", "Paired bike"),
            ui.rows.map { it.title },
        )
        assertEquals("yes", ui.rows.last().detail)
    }

    @Test
    fun `a note about what's left out doesn't stop the pack`() {
        val model = TransferViewModel(fake)
        model.onPackLoaded(preview(issues = listOf(PackIssue("The ride being recorded now isn't included.", "ACTIVE"))))
        val ui = model.ui.value
        assertTrue(ui.canSave)
        assertEquals("The ride being recorded now isn't included.", ui.message)
    }

    @Test
    fun `packing shows each step in the log and ends with the size`() {
        val model = TransferViewModel(fake)
        model.onStarted("Packing")
        model.onStep("Packing rides", 0.1f)
        model.onStep("Packing rides", 0.2f)
        model.onStep("Packing GPS tracks", 0.4f)
        val working = model.ui.value
        assertEquals(TransferPhase.WORKING, working.phase)
        assertEquals(0.4f, working.progress)
        assertEquals(listOf("Packing", "Packing rides", "Packing GPS tracks"), working.lines.map { it.text })
        model.onExported(2_500_000, "bumblebee-backup.zip")
        assertEquals(TransferPhase.DONE, model.ui.value.phase)
        assertEquals("2.4 MB", model.ui.value.headline)
    }

    @Test
    fun `a failed pack says so and can be tried again`() {
        val model = TransferViewModel(fake)
        model.onStarted("Packing")
        model.onExportFailed("disk full")
        val ui = model.ui.value
        assertEquals("NOT SAVED", ui.shout)
        assertTrue(ui.canSave)
        assertEquals(CheckState.FAILED, ui.lines.last().tone)
    }

    @Test
    fun `a damaged file is held with the log closed`() {
        val model = TransferViewModel(fake)
        model.onInspected(BringIn.Failed("The file is damaged. The checksum does not match.", listOf("sha256")))
        val ui = model.ui.value
        assertEquals("NOT APPLIED", ui.shout)
        assertFalse(ui.canOverwrite)
        assertFalse(ui.logOpen)
        model.toggleLog()
        assertTrue(model.ui.value.logOpen)
    }

    @Test
    fun `a clash offers overwrite and keep`() = runTest {
        val model = TransferViewModel(fake)
        model.onInspected(BringIn.Held(bundle(), listOf(Clash("rides", "12", "clash ride"))))
        val ui = model.ui.value
        assertEquals("Overwrite those 1", ui.overwriteLabel)
        assertEquals("1 already live here. Nothing was replaced yet.", ui.message)
        assertTrue(ui.canKeep)
        model.chooseKeep()
        assertEquals(false, model.commits.first())
    }

    @Test
    fun `a held import keeps the bundle for the choice`() {
        val model = TransferViewModel(fake)
        val held = bundle()
        model.onInspected(BringIn.Held(held, listOf(Clash("rides", "12", "clash ride"))))
        assertEquals(held.rides.single().startTs, model.pending?.rides?.single()?.startTs)
    }

    @Test
    fun `a ready file is brought in and the summary is listed`() {
        val model = TransferViewModel(fake)
        model.onStarted("Bringing in")
        model.onInspected(BringIn.Ready(bundle()))
        assertEquals(TransferPhase.WORKING, model.ui.value.phase)
        model.onCommitted(listOf("Rides: 1 brought in"))
        assertEquals(TransferPhase.DONE, model.ui.value.phase)
        assertEquals("Rides: 1 brought in", model.ui.value.rows.single().title)
        assertEquals("All done", model.ui.value.lines.last().text)
    }

    private fun preview(issues: List<PackIssue>, mac: String? = null) = PackPreview(
        bundle = bundle(mac),
        issues = issues,
        rideCount = 1,
        distanceKm = 2,
        fillCount = 1,
    )

    private fun bundle(mac: String? = null) = SnapshotBundle(
        rides = listOf(SnapshotRide(id = 1, startTs = 1L)),
        prefs = SnapshotPrefs(
            bikeMac = mac,
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
            widget = SnapshotWidget(null, null, null, "", 0.0, 0.0, "", false, "garage", 0L),
        ),
    )
}

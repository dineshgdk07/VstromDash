package dev.gdk.bumblebee.ui.transfer

import dev.gdk.bumblebee.snapshot.Clash
import dev.gdk.bumblebee.snapshot.PackIssue
import dev.gdk.bumblebee.snapshot.SnapshotBundle
import dev.gdk.bumblebee.snapshot.SnapshotPrefs
import dev.gdk.bumblebee.snapshot.SnapshotRide
import dev.gdk.bumblebee.snapshot.SnapshotWidget
import dev.gdk.bumblebee.transfer.BringIn
import dev.gdk.bumblebee.transfer.PackPreview
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
        override suspend fun writeTo(out: OutputStream) = Unit
        override suspend fun inspect(input: InputStream) = error("unused")
        override suspend fun commit(bundle: SnapshotBundle, overwriteClashes: Boolean) = Unit
    }

    @Test
    fun `pack lists the sections and a clean preview can save`() {
        val model = TransferViewModel(fake)
        model.onPackLoaded(preview(issues = emptyList(), mac = "AA"))
        val ui = model.ui.value
        assertEquals("PACK THE BIKE", ui.shout)
        assertTrue(ui.canSave)
        assertEquals(
            listOf("Rides and tracks", "Fuel log", "Daily rollups", "Service and plans", "Garage papers", "Paired bike"),
            ui.rows.map { it.title },
        )
        assertEquals("yes", ui.rows.last().detail)
    }

    @Test
    fun `an open ride disables save`() {
        val model = TransferViewModel(fake)
        model.onPackLoaded(preview(issues = listOf(PackIssue("Finish or discard the open ride before packing.", "ACTIVE"))))
        val ui = model.ui.value
        assertFalse(ui.canSave)
        assertEquals("Finish or discard the open ride before packing.", ui.message)
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
        assertEquals("1 already live here. Nothing was replaced.", ui.message)
        assertTrue(ui.canKeep)
        model.chooseKeep()
        assertEquals(false, model.commits.first())
    }

    @Test
    fun `a ready file waits on the data rows until commit finishes`() {
        val model = TransferViewModel(fake)
        model.onInspected(BringIn.Ready(bundle()))
        assertEquals(CheckState.WAITING, model.ui.value.rows[4].state)
        model.onCommitted()
        assertEquals(CheckState.DONE, model.ui.value.rows[4].state)
        assertEquals(CheckState.DONE, model.ui.value.rows.last().state)
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

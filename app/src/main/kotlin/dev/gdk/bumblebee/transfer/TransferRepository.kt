package dev.gdk.bumblebee.transfer

import dev.gdk.bumblebee.ble.android.BikePrefs
import dev.gdk.bumblebee.data.local.SnapshotArchive
import dev.gdk.bumblebee.snapshot.Clash
import dev.gdk.bumblebee.snapshot.PackIssue
import dev.gdk.bumblebee.snapshot.RowAction
import dev.gdk.bumblebee.snapshot.SnapshotBundle
import dev.gdk.bumblebee.snapshot.SnapshotRead
import dev.gdk.bumblebee.snapshot.SnapshotWidget
import dev.gdk.bumblebee.snapshot.packIssues
import dev.gdk.bumblebee.snapshot.planApply
import dev.gdk.bumblebee.snapshot.readSnapshot
import dev.gdk.bumblebee.snapshot.writeSnapshot
import java.io.InputStream
import java.io.OutputStream

interface WidgetStore {
    fun read(): SnapshotWidget
    fun write(widget: SnapshotWidget)
}

data class PackPreview(
    val bundle: SnapshotBundle,
    val issues: List<PackIssue>,
    val rideCount: Int,
    val distanceKm: Int,
    val fillCount: Int,
)

sealed class BringIn {
    data class Ready(val bundle: SnapshotBundle) : BringIn()
    data class Held(val bundle: SnapshotBundle, val clashes: List<Clash>) : BringIn()
    data class Failed(val message: String, val log: List<String>) : BringIn()
}

interface TransferGateway {
    suspend fun preview(): PackPreview
    suspend fun writeTo(out: OutputStream)
    suspend fun inspect(input: InputStream): BringIn
    suspend fun commit(bundle: SnapshotBundle, overwriteClashes: Boolean)
}

class TransferRepository(
    private val archive: SnapshotArchive,
    private val prefs: BikePrefs,
    private val widget: WidgetStore,
    private val buildType: String,
) : TransferGateway {
    override suspend fun preview(): PackPreview {
        val captured = archive.capture()
        val bundle = captured.bundle.copy(prefs = prefs.toSnapshotPrefs(widget.read()))
        val issues = packIssues(bundle, captured.missingFiles)
        val distanceKm = (bundle.rides.sumOf { it.gpsDistanceM } / 1000.0).toInt()
        val fillCount = bundle.fuel.count { it.type == "REFUEL" }
        return PackPreview(bundle, issues, bundle.rides.size, distanceKm, fillCount)
    }

    override suspend fun writeTo(out: OutputStream) {
        val packed = preview()
        val issue = packed.issues.firstOrNull()
        if (issue != null) throw IllegalStateException(issue.message)
        writeSnapshot(out, packed.bundle, buildType, System.currentTimeMillis())
    }

    override suspend fun inspect(input: InputStream): BringIn {
        return when (val read = readSnapshot(input)) {
            is SnapshotRead.Rejected -> BringIn.Failed(read.message, read.log)
            is SnapshotRead.Ready -> {
                val here = archive.capture().bundle.copy(prefs = prefs.toSnapshotPrefs(widget.read()))
                val plan = planApply(read.bundle, here, overwriteClashes = false)
                if (plan.clashes.isEmpty()) BringIn.Ready(read.bundle) else BringIn.Held(read.bundle, plan.clashes)
            }
        }
    }

    override suspend fun commit(bundle: SnapshotBundle, overwriteClashes: Boolean) {
        val here = archive.capture().bundle.copy(prefs = prefs.toSnapshotPrefs(widget.read()))
        val plan = planApply(bundle, here, overwriteClashes)
        archive.apply(bundle, overwriteClashes)
        if (plan.prefs == RowAction.ADD || plan.prefs == RowAction.REPLACE) {
            prefs.writeSnapshotPrefs(bundle.prefs)
            widget.write(bundle.prefs.widget)
        }
    }
}

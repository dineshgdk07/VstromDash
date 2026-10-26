package dev.gdk.bumblebee.transfer

import dev.gdk.bumblebee.ble.android.BikePrefs
import dev.gdk.bumblebee.data.local.SnapshotArchive
import dev.gdk.bumblebee.snapshot.Clash
import dev.gdk.bumblebee.snapshot.PackIssue
import dev.gdk.bumblebee.snapshot.RowAction
import dev.gdk.bumblebee.snapshot.SnapshotBundle
import dev.gdk.bumblebee.snapshot.SnapshotProgress
import dev.gdk.bumblebee.snapshot.SnapshotRead
import dev.gdk.bumblebee.snapshot.SnapshotWidget
import dev.gdk.bumblebee.snapshot.packIssues
import dev.gdk.bumblebee.snapshot.planApply
import dev.gdk.bumblebee.snapshot.readSnapshot
import dev.gdk.bumblebee.snapshot.writeSnapshot
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

interface WidgetStore {
    fun read(): SnapshotWidget
    fun write(widget: SnapshotWidget)
}

data class PackPreview(
    val bundle: SnapshotBundle,
    /** Things left out of the pack, said plainly; none of them stop it. */
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

/** How far a pack or bring-in has got: a step in words and, when known, a fraction done. */
fun interface TransferEvents {
    fun step(text: String, fraction: Float?)

    companion object {
        val NONE = TransferEvents { _, _ -> }
    }
}

interface TransferGateway {
    suspend fun preview(): PackPreview

    /** Writes the pack; returns how many bytes went out. */
    suspend fun writeTo(out: OutputStream, events: TransferEvents = TransferEvents.NONE): Long
    suspend fun inspect(input: InputStream, events: TransferEvents = TransferEvents.NONE): BringIn

    /** Brings a pack in; returns what it did, line by line. */
    suspend fun commit(bundle: SnapshotBundle, overwriteClashes: Boolean, events: TransferEvents = TransferEvents.NONE): List<String>
}

class TransferRepository(
    private val archive: SnapshotArchive,
    private val prefs: BikePrefs,
    private val widget: WidgetStore,
    private val buildType: String,
    /** Where an incoming pack's scans wait until it's applied; null keeps them in memory (tests). */
    private val spoolDir: java.io.File? = null,
    private val extras: AppExtras = AppExtras.NONE,
) : TransferGateway {
    /**
     * Everything that can go in a pack. A ride still being recorded is left out (it isn't
     * finished, so its numbers aren't final) and a paper whose scan is gone goes without it; both
     * are said in the issues, but neither stops the pack.
     */
    override suspend fun preview(): PackPreview {
        val captured = archive.capture()
        val all = captured.bundle.copy(
            prefs = prefs.toSnapshotPrefs(widget.read()),
            settings = extras.settings(),
        )
        val issues = packIssues(all, captured.missingFiles).map { issue ->
            when (issue.message) {
                OPEN_RIDE_BLOCK -> PackIssue("The ride being recorded now isn't included. It'll be in your next pack once it's finished.", issue.log)
                MISSING_SCAN_BLOCK -> PackIssue("A garage paper's scan is missing from this phone, so it's packed without its scan.", issue.log)
                else -> issue
            }
        }
        val bundle = withoutOpenRides(all)
        val distanceKm = (bundle.rides.sumOf { it.gpsDistanceM.takeIf { d -> d.isFinite() } ?: 0.0 } / 1000.0).toInt()
        val fillCount = bundle.fuel.count { it.type == "REFUEL" }
        return PackPreview(bundle, issues, bundle.rides.size, distanceKm, fillCount)
    }

    override suspend fun writeTo(out: OutputStream, events: TransferEvents): Long {
        events.step("Reading your rides, garage and settings", null)
        val packed = preview()
        val counted = CountingStream(out)
        writeSnapshot(counted, packed.bundle, buildType, System.currentTimeMillis(), progress(events))
        counted.flush()
        return counted.count
    }

    override suspend fun inspect(input: InputStream, events: TransferEvents): BringIn {
        // A new pack replaces whatever an earlier, abandoned one left behind.
        spoolDir?.deleteRecursively()
        events.step("Opening the file", null)
        return when (val read = readSnapshot(input, spoolDir = spoolDir, progress = progress(events))) {
            is SnapshotRead.Rejected -> BringIn.Failed(read.message, read.log)
            is SnapshotRead.Ready -> {
                events.step("Comparing with what's on this phone", null)
                val here = archive.capture().bundle.copy(prefs = prefs.toSnapshotPrefs(widget.read()))
                val plan = planApply(read.bundle, here, overwriteClashes = false)
                if (plan.clashes.isEmpty()) BringIn.Ready(read.bundle) else BringIn.Held(read.bundle, plan.clashes)
            }
        }
    }

    override suspend fun commit(bundle: SnapshotBundle, overwriteClashes: Boolean, events: TransferEvents): List<String> {
        val here = archive.capture().bundle.copy(prefs = prefs.toSnapshotPrefs(widget.read()))
        val plan = planApply(bundle, here, overwriteClashes)
        val steps = 9f
        var n = 0
        archive.apply(bundle, overwriteClashes) { step -> events.step(step, (++n / steps).coerceAtMost(0.9f)) }
        val done = mutableListOf<String>()
        val added = plan.rides.count { it.action != RowAction.KEEP }
        done += "Rides: $added brought in" + if (added < bundle.rides.size) ", ${bundle.rides.size - added} already here" else ""
        done += "Fuel log: ${plan.fuels.count { it.action != RowAction.KEEP }} entries"
        done += "Garage papers: ${plan.documents.count { it.action != RowAction.KEEP }}, service log: ${plan.services.count { it.action != RowAction.KEEP }}"
        val withSettings = plan.prefs == RowAction.ADD || plan.prefs == RowAction.REPLACE
        if (withSettings) {
            events.step("Restoring the bike link and safety details", 0.92f)
            prefs.writeSnapshotPrefs(bundle.prefs)
            widget.write(bundle.prefs.widget)
            done += "Bike link, safety contacts and reminders"
        } else {
            done += "Kept this phone's settings (choose overwrite to take the pack's)"
        }
        if (withSettings) done += extras.restore(bundle.settings) { events.step(it, 0.96f) }
        // Kept until it's in, so a failed apply can be tried again.
        spoolDir?.deleteRecursively()
        events.step("Done", 1f)
        return done
    }

    private fun progress(events: TransferEvents) = SnapshotProgress { step, done, total ->
        events.step(step, if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null)
    }

    private class CountingStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    private companion object {
        const val OPEN_RIDE_BLOCK = "Finish or discard the open ride before packing."
        const val MISSING_SCAN_BLOCK = "A paper is missing its scan."

        /** The pack without a ride still being recorded, and nothing that points at it. */
        fun withoutOpenRides(b: SnapshotBundle): SnapshotBundle {
            val open = b.rides.filter { it.rideState == "ACTIVE" || it.rideState == "PAUSED" }.map { it.id }.toSet()
            if (open.isEmpty()) return b
            return b.copy(
                rides = b.rides.filter { it.id !in open },
                segments = b.segments.filter { it.rideId !in open },
                telemetry = b.telemetry.filter { it.rideId !in open },
                tracks = b.tracks.filter { it.rideId !in open },
                fuel = b.fuel.map { f -> if (f.rideId?.let { it in open } == true) f.copy(rideId = null) else f },
            )
        }
    }
}

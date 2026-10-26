package dev.gdk.bumblebee.snapshot

object SnapshotFormat {
    const val MAGIC = "bumblebee-snapshot"
    const val FORMAT = 1
    const val APP_SCHEMA = 10
    const val EXTENSION = "bbpack"
}

object SnapshotSections {
    const val RIDES = "rides"
    const val SEGMENTS = "ride_segments"
    const val TELEMETRY = "telemetry_samples"
    const val TRACKS = "track_points"
    const val FUEL = "fuel_events"
    const val ROLLUPS = "daily_rollups"
    const val CALIBRATION = "calibration"
    const val SERVICE = "service_logs"
    const val DOCUMENTS = "bike_documents"
    const val PLANS = "maintenance_plans"
    const val PREFS = "prefs"

    val ORDER = listOf(
        RIDES,
        SEGMENTS,
        TELEMETRY,
        TRACKS,
        FUEL,
        ROLLUPS,
        CALIBRATION,
        SERVICE,
        DOCUMENTS,
        PLANS,
    )
}

data class SnapshotRide(
    val id: Long = 0,
    val startTs: Long,
    val endTs: Long? = null,
    val odoStartKm: Int? = null,
    val odoEndKm: Int? = null,
    val gpsDistanceM: Double = 0.0,
    val movingSec: Int = 0,
    val idleSec: Int = 0,
    val maxSpeedKmh: Int = 0,
    val avgMovingSpeedKmh: Double = 0.0,
    val fuelBarsStart: Int? = null,
    val fuelBarsEnd: Int? = null,
    val speedHistogramJson: String? = null,
    val name: String? = null,
    val tags: String? = null,
    val mergedIntoId: Long? = null,
    val remoteId: String? = null,
    val syncedAt: Long? = null,
    val isDemo: Boolean = false,
    val gpsOnly: Boolean = false,
    val rideState: String = "FINISHED",
    val pausedAt: Long? = null,
    val startPlace: String? = null,
    val endPlace: String? = null,
    val weatherTempC: Double? = null,
    val weatherCode: Int? = null,
    val climbM: Double? = null,
)

data class SnapshotSegment(
    val id: Long = 0,
    val rideId: Long,
    val startTs: Long,
    val endTs: Long? = null,
    val odoStartKm: Int? = null,
    val odoEndKm: Int? = null,
)

data class SnapshotTelemetry(
    val id: Long = 0,
    val rideId: Long,
    val ts: Long,
    val speedKmh: Int,
    val odometerKm: Int,
    val tripAKm: Double,
    val tripBKm: Double,
    val fuelBars: Int?,
    val fuelEconKml: Double?,
)

data class SnapshotTrack(
    val id: Long = 0,
    val rideId: Long,
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val altitudeM: Double?,
    val bearingDeg: Float?,
    val speedMps: Float?,
    val accuracyM: Float?,
)

data class SnapshotFuel(
    val id: Long = 0,
    val ts: Long,
    val rideId: Long?,
    val odometerKm: Int,
    val barsBefore: Int,
    val barsAfter: Int,
    val type: String,
    val source: String = "AUTO",
    val litres: Double? = null,
    val cost: Double? = null,
    val note: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val remoteId: String? = null,
    val syncedAt: Long? = null,
    val isFullTank: Boolean = true,
    val pricePerLitre: Double? = null,
    val outlierConfirmed: Boolean = false,
)

data class SnapshotRollup(
    val date: String,
    val km: Double,
    val rides: Int,
    val movingSec: Int,
    val maxSpeedKmh: Int,
    val fuelBarsUsed: Int,
)

data class SnapshotCalibration(
    val key: String,
    val value: String,
)

data class SnapshotService(
    val id: Long = 0,
    val ts: Long,
    val odometerKm: Int,
    val item: String,
    val rupees: Double? = null,
    val note: String? = null,
    val shop: String? = null,
    val planId: Long? = null,
    val filePath: String? = null,
)

data class SnapshotDocument(
    val id: Long = 0,
    val title: String,
    val kind: String,
    val expiryIso: String? = null,
    val number: String? = null,
    val filePath: String? = null,
    val mime: String? = null,
    val note: String? = null,
    val createdTs: Long,
)

data class SnapshotPlan(
    val id: Long = 0,
    val title: String,
    val kind: String,
    val intervalKm: Int? = null,
    val lastDoneOdoKm: Int? = null,
    val intervalDays: Int? = null,
    val lastDoneOnIso: String? = null,
    val dueOnIso: String? = null,
    val active: Boolean = true,
    val createdTs: Long,
)

data class SnapshotWidget(
    val fuelBars: Int?,
    val rangeKm: Int?,
    val serviceDueKm: Int?,
    val alert: String,
    val todayKm: Double,
    val lastRideKm: Double,
    val lastRideLabel: String,
    val linked: Boolean,
    val open: String,
    val atMs: Long,
)

data class SnapshotPrefs(
    val bikeMac: String?,
    val riderName: String,
    val serviceIntervalKm: Int,
    val lastServiceOdoKm: Int?,
    val rcExpiry: String,
    val insuranceExpiry: String,
    val pucExpiry: String,
    val mapsNavToCluster: Boolean,
    val tankCapacityL: Float,
    val remindersEnabled: Boolean,
    val remindDays: Int,
    val remindKm: Int,
    val pauseWindowMin: Int,
    val autoConnect: Boolean,
    val skipAutoConnectDate: String,
    val crashSensitivity: String,
    val emergencyContacts: String,
    val bloodGroup: String,
    val medicalNote: String,
    val lastReminderStamp: String,
    val widget: SnapshotWidget,
)

/**
 * A document scan in a snapshot. Its bytes are read only when used, from wherever they live (the
 * garage folder on export, a spooled temp file on import), so a pack with many scans never
 * holds them all in memory at once.
 */
class SnapshotFile private constructor(
    val relativePath: String,
    private val load: () -> ByteArray,
) {
    constructor(relativePath: String, bytes: ByteArray) : this(relativePath, { bytes })

    val bytes: ByteArray get() = load()

    companion object {
        /** A scan read from storage each time it's needed. */
        fun lazy(relativePath: String, load: () -> ByteArray) = SnapshotFile(relativePath, load)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnapshotFile) return false
        return relativePath == other.relativePath && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * relativePath.hashCode() + bytes.contentHashCode()
}

data class SnapshotBundle(
    val rides: List<SnapshotRide> = emptyList(),
    val segments: List<SnapshotSegment> = emptyList(),
    val telemetry: List<SnapshotTelemetry> = emptyList(),
    val tracks: List<SnapshotTrack> = emptyList(),
    val fuel: List<SnapshotFuel> = emptyList(),
    val rollups: List<SnapshotRollup> = emptyList(),
    val calibration: List<SnapshotCalibration> = emptyList(),
    val services: List<SnapshotService> = emptyList(),
    val documents: List<SnapshotDocument> = emptyList(),
    val plans: List<SnapshotPlan> = emptyList(),
    val prefs: SnapshotPrefs,
    val files: List<SnapshotFile> = emptyList(),
)

data class SnapshotManifest(
    val magic: String,
    val format: Int,
    val schema: Int,
    val exportedAt: Long,
    val buildType: String,
    val counts: Map<String, Int>,
    val sha256: String,
)

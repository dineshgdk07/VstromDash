package dev.gdk.bumblebee.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One ride = one BLE/engine session (spec §4). Sync-ready fields per D-016. */
@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val rideState: String = RIDE_FINISHED,
    val pausedAt: Long? = null,
    val startPlace: String? = null,
    val endPlace: String? = null,
    val weatherTempC: Double? = null,
    val weatherCode: Int? = null,
    val climbM: Double? = null,
)

const val RIDE_ACTIVE = "ACTIVE"
const val RIDE_PAUSED = "PAUSED"
const val RIDE_FINISHED = "FINISHED"

/** One bike session inside a ride. A pause closes a segment; a resume opens the next. */
@Entity(tableName = "ride_segments", indices = [Index("rideId")])
data class RideSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val startTs: Long,
    val endTs: Long? = null,
    val odoStartKm: Int? = null,
    val odoEndKm: Int? = null,
)

@Entity(tableName = "service_logs", indices = [Index("ts")])
data class ServiceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val odometerKm: Int,
    val item: String,
    val rupees: Double? = null,
    val note: String? = null,
    val shop: String? = null,
    val planId: Long? = null,
    val filePath: String? = null,
)

@Entity(tableName = "bike_documents")
data class BikeDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String,
    val expiryIso: String? = null,
    val number: String? = null,
    val filePath: String? = null,
    val mime: String? = null,
    val note: String? = null,
    val createdTs: Long,
)

@Entity(tableName = "maintenance_plans")
data class MaintenancePlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

/** Raw a537 archive — every frame, verbatim (insights spec B1). */
@Entity(tableName = "telemetry_samples", indices = [Index("rideId")])
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val ts: Long,
    val speedKmh: Int,
    val odometerKm: Int,
    val tripAKm: Double,
    val tripBKm: Double,
    val fuelBars: Int?,
    val fuelEconKml: Double?,
)

/** 1 Hz GPS archive (insights spec B1). */
@Entity(tableName = "track_points", indices = [Index(value = ["rideId", "ts"]), Index("ts")])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val altitudeM: Double?,
    val bearingDeg: Float?,
    val speedMps: Float?,
    val accuracyM: Float?,
)

/** Fuel-bar transitions + refuels; litres/cost are optional rider input (insights spec B2). */
@Entity(tableName = "fuel_events", indices = [Index("ts")])
data class FuelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val rideId: Long?,
    val odometerKm: Int,
    val barsBefore: Int,
    val barsAfter: Int,
    val type: String, // "BAR_DROP" | "REFUEL" — mirrors ride.FuelEventType
    val source: String = "AUTO", // "AUTO" | "RIDER" — mirrors ride.FuelEventSource
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

/** Per-day aggregates so trends stay O(days) (insights spec B4). date = ISO yyyy-MM-dd. */
@Entity(tableName = "daily_rollups")
data class DailyRollupEntity(
    @PrimaryKey val date: String,
    val km: Double,
    val rides: Int,
    val movingSec: Int,
    val maxSpeedKmh: Int,
    val fuelBarsUsed: Int,
)

/** Learned constants (km-per-bar curve, odo-vs-GPS factor, ...) as a KV store (spec B4). */
@Entity(tableName = "calibration")
data class CalibrationEntity(
    @PrimaryKey val key: String,
    val value: String,
)

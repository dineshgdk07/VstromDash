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
    val remoteId: String? = null,
    val syncedAt: Long? = null,
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
@Entity(tableName = "track_points", indices = [Index("rideId")])
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
    val litres: Double? = null,
    val cost: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val remoteId: String? = null,
    val syncedAt: Long? = null,
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

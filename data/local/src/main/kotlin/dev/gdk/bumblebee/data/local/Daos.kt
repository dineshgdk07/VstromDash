package dev.gdk.bumblebee.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RideDao {
    @Insert suspend fun insert(ride: RideEntity): Long
    @Update suspend fun update(ride: RideEntity)
    @Query("SELECT * FROM rides WHERE id = :id") suspend fun byId(id: Long): RideEntity?
    @Query("SELECT * FROM rides ORDER BY startTs DESC") suspend fun allNewestFirst(): List<RideEntity>
}

@Dao
interface TelemetryDao {
    @Insert suspend fun insertAll(samples: List<TelemetrySampleEntity>)
    @Insert suspend fun insert(sample: TelemetrySampleEntity)
    @Query("SELECT COUNT(*) FROM telemetry_samples WHERE rideId = :rideId")
    suspend fun countForRide(rideId: Long): Int
    @Query("SELECT * FROM telemetry_samples WHERE rideId = :rideId ORDER BY ts")
    suspend fun forRide(rideId: Long): List<TelemetrySampleEntity>
}

@Dao
interface TrackDao {
    @Insert suspend fun insertAll(points: List<TrackPointEntity>)
    @Insert suspend fun insert(point: TrackPointEntity)
    @Query("SELECT COUNT(*) FROM track_points WHERE rideId = :rideId")
    suspend fun countForRide(rideId: Long): Int
    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY ts")
    suspend fun forRide(rideId: Long): List<TrackPointEntity>
}

@Dao
interface FuelDao {
    @Insert suspend fun insert(event: FuelEventEntity): Long
    @Query("SELECT * FROM fuel_events ORDER BY ts") suspend fun allChronological(): List<FuelEventEntity>
}

@Dao
interface RollupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(rollup: DailyRollupEntity)
    @Query("SELECT * FROM daily_rollups WHERE date = :date") suspend fun byDate(date: String): DailyRollupEntity?
}

@Dao
interface CalibrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entry: CalibrationEntity)
    @Query("SELECT * FROM calibration WHERE `key` = :key") suspend fun get(key: String): CalibrationEntity?
}

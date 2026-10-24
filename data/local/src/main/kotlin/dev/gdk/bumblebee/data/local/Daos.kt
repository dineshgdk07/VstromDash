package dev.gdk.bumblebee.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert suspend fun insert(ride: RideEntity): Long
    @Update suspend fun update(ride: RideEntity)
    @Query("SELECT * FROM rides WHERE id = :id") suspend fun byId(id: Long): RideEntity?
    @Query("SELECT * FROM rides ORDER BY startTs DESC") suspend fun allNewestFirst(): List<RideEntity>
    @Query("SELECT * FROM rides WHERE endTs IS NOT NULL AND mergedIntoId IS NULL AND isDemo = 0 ORDER BY startTs DESC")
    suspend fun finishedNewestFirst(): List<RideEntity>
    @Query("SELECT * FROM rides WHERE endTs IS NOT NULL AND mergedIntoId IS NULL AND isDemo = 0 ORDER BY startTs DESC")
    fun observeFinishedNewestFirst(): Flow<List<RideEntity>>
    @Query("SELECT * FROM rides WHERE endTs IS NOT NULL AND mergedIntoId IS NULL AND isDemo = 0 ORDER BY endTs DESC LIMIT 1")
    suspend fun lastFinished(): RideEntity?
    @Query("SELECT * FROM rides WHERE endTs IS NULL")
    suspend fun unfinished(): List<RideEntity>
    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT * FROM rides WHERE mergedIntoId = :parentId ORDER BY startTs")
    suspend fun childrenOf(parentId: Long): List<RideEntity>
    @Query("SELECT MAX(maxSpeedKmh) FROM rides WHERE endTs IS NOT NULL AND mergedIntoId IS NULL AND isDemo = 0")
    suspend fun allTimeMaxSpeed(): Int?
}

@Dao
interface SegmentDao {
    @Insert suspend fun insert(segment: RideSegmentEntity): Long
    @Update suspend fun update(segment: RideSegmentEntity)
    @Query("SELECT * FROM ride_segments WHERE rideId = :rideId ORDER BY startTs")
    suspend fun forRide(rideId: Long): List<RideSegmentEntity>
    @Query("SELECT * FROM ride_segments WHERE rideId = :rideId AND endTs IS NULL ORDER BY startTs DESC LIMIT 1")
    suspend fun openForRide(rideId: Long): RideSegmentEntity?
    @Query("DELETE FROM ride_segments WHERE rideId = :rideId")
    suspend fun deleteForRide(rideId: Long)
}

@Dao
interface TelemetryDao {
    @Insert suspend fun insertAll(samples: List<TelemetrySampleEntity>)
    @Insert suspend fun insert(sample: TelemetrySampleEntity)
    @Query("SELECT COUNT(*) FROM telemetry_samples WHERE rideId = :rideId")
    suspend fun countForRide(rideId: Long): Int
    @Query("SELECT * FROM telemetry_samples WHERE rideId = :rideId ORDER BY ts")
    suspend fun forRide(rideId: Long): List<TelemetrySampleEntity>
    @Query("DELETE FROM telemetry_samples WHERE rideId = :rideId")
    suspend fun deleteForRide(rideId: Long)
    @Query("SELECT * FROM telemetry_samples WHERE rideId = :rideId ORDER BY ts DESC LIMIT 1")
    suspend fun lastForRide(rideId: Long): TelemetrySampleEntity?

    @Query("SELECT odometerKm FROM telemetry_samples ORDER BY ts DESC LIMIT 1")
    suspend fun latestOdometerKm(): Int?
}

@Dao
interface TrackDao {
    @Insert suspend fun insertAll(points: List<TrackPointEntity>)
    @Insert suspend fun insert(point: TrackPointEntity)
    @Query("SELECT COUNT(*) FROM track_points WHERE rideId = :rideId")
    suspend fun countForRide(rideId: Long): Int
    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY ts")
    suspend fun forRide(rideId: Long): List<TrackPointEntity>
    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY ts DESC LIMIT 1")
    suspend fun lastForRide(rideId: Long): TrackPointEntity?
    @Query("SELECT t.* FROM track_points t INNER JOIN rides r ON r.id = t.rideId WHERE r.isDemo = 0 ORDER BY t.ts DESC LIMIT 1")
    suspend fun latest(): TrackPointEntity?
    @Query("DELETE FROM track_points WHERE rideId = :rideId")
    suspend fun deleteForRide(rideId: Long)
}

@Dao
interface FuelDao {
    @Insert suspend fun insert(event: FuelEventEntity): Long
    @Update suspend fun update(event: FuelEventEntity)
    @Query("DELETE FROM fuel_events WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM fuel_events WHERE id = :id") suspend fun byId(id: Long): FuelEventEntity?
    @Query("SELECT * FROM fuel_events ORDER BY ts") suspend fun allChronological(): List<FuelEventEntity>
    @Query("SELECT * FROM fuel_events ORDER BY ts") fun observeAll(): Flow<List<FuelEventEntity>>
    @Query("SELECT * FROM fuel_events WHERE rideId = :rideId ORDER BY ts")
    suspend fun forRide(rideId: Long): List<FuelEventEntity>
}

@Dao
interface RollupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(rollup: DailyRollupEntity)
    @Query("SELECT * FROM daily_rollups WHERE date = :date") suspend fun byDate(date: String): DailyRollupEntity?
    @Query("SELECT * FROM daily_rollups WHERE date = :date") fun observeByDate(date: String): Flow<DailyRollupEntity?>
    @Query("SELECT * FROM daily_rollups") suspend fun all(): List<DailyRollupEntity>
}

@Dao
interface CalibrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entry: CalibrationEntity)
    @Query("SELECT * FROM calibration WHERE `key` = :key") suspend fun get(key: String): CalibrationEntity?
    @Query("SELECT * FROM calibration") suspend fun all(): List<CalibrationEntity>
}

@Dao
interface ServiceDao {
    @Insert suspend fun insert(log: ServiceLogEntity): Long
    @Update suspend fun update(log: ServiceLogEntity)
    @Query("DELETE FROM service_logs WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM service_logs WHERE id = :id") suspend fun byId(id: Long): ServiceLogEntity?
    @Query("SELECT * FROM service_logs ORDER BY ts DESC") suspend fun allNewestFirst(): List<ServiceLogEntity>
}

@Dao
interface DocumentDao {
    @Insert suspend fun insert(doc: BikeDocumentEntity): Long
    @Update suspend fun update(doc: BikeDocumentEntity)
    @Query("DELETE FROM bike_documents WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM bike_documents WHERE id = :id") suspend fun byId(id: Long): BikeDocumentEntity?
    @Query("SELECT * FROM bike_documents ORDER BY kind, title") suspend fun all(): List<BikeDocumentEntity>
    @Query("SELECT COUNT(*) FROM bike_documents") suspend fun count(): Int
}

@Dao
interface PlanDao {
    @Insert suspend fun insert(plan: MaintenancePlanEntity): Long
    @Update suspend fun update(plan: MaintenancePlanEntity)
    @Query("DELETE FROM maintenance_plans WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM maintenance_plans WHERE id = :id") suspend fun byId(id: Long): MaintenancePlanEntity?
    @Query("SELECT * FROM maintenance_plans ORDER BY title") suspend fun all(): List<MaintenancePlanEntity>
    @Query("SELECT COUNT(*) FROM maintenance_plans") suspend fun count(): Int
}

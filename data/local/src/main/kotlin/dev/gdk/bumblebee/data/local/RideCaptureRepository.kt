package dev.gdk.bumblebee.data.local

import dev.gdk.bumblebee.protocol.TelemetryFrame
import dev.gdk.bumblebee.ride.BarCalibrator
import dev.gdk.bumblebee.ride.FuelEvent
import dev.gdk.bumblebee.ride.FuelEventDetector
import dev.gdk.bumblebee.ride.FuelEventType
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.RideRecorder
import dev.gdk.bumblebee.ride.TelemetrySample
import java.time.Instant
import java.time.ZoneId

/**
 * Single entry point for ride capture. The BLE foreground service (later plan) calls
 * [startRide] on session start, [onTelemetry]/[onGps] as data streams in, and
 * [finishRide] on disconnect. Raw data is archived immediately (golden rule: capture
 * everything from ride one); aggregates land on the ride row at finish.
 */
class RideCaptureRepository(private val db: BumblebeeDb) {

    private val recorder = RideRecorder()
    private val fuelDetector = FuelEventDetector()
    private val calibrator = BarCalibrator()

    var activeRideId: Long? = null
        private set

    suspend fun startRide(ts: Long): Long {
        check(activeRideId == null) { "a ride is already active" }
        val id = db.rideDao().insert(RideEntity(startTs = ts))
        recorder.start(ts)
        fuelDetector.reset() // keep last stable bars; catches refuels between rides
        activeRideId = id
        return id
    }

    suspend fun onTelemetry(frame: TelemetryFrame, ts: Long) {
        val rideId = checkNotNull(activeRideId) { "no active ride" }
        val sample = TelemetrySample.fromFrame(frame, ts)

        db.telemetryDao().insert(
            TelemetrySampleEntity(
                rideId = rideId, ts = sample.ts, speedKmh = sample.speedKmh,
                odometerKm = sample.odometerKm, tripAKm = sample.tripAKm,
                tripBKm = sample.tripBKm, fuelBars = sample.fuelBars,
                fuelEconKml = sample.fuelEconKml,
            )
        )
        recorder.onTelemetry(sample)
        fuelDetector.onSample(sample)?.let { persistFuelEvent(it, rideId) }
    }

    suspend fun onGps(fix: GpsFix) {
        val rideId = checkNotNull(activeRideId) { "no active ride" }
        db.trackDao().insert(
            TrackPointEntity(
                rideId = rideId, ts = fix.ts, lat = fix.lat, lng = fix.lng,
                altitudeM = fix.altitudeM, bearingDeg = fix.bearingDeg,
                speedMps = fix.speedMps, accuracyM = fix.accuracyM,
            )
        )
        recorder.onGps(fix)
    }

    suspend fun finishRide(ts: Long): RideEntity {
        val rideId = checkNotNull(activeRideId) { "no active ride" }
        val agg = recorder.stop(ts)
        val ride = checkNotNull(db.rideDao().byId(rideId)).copy(
            endTs = agg.endTs,
            odoStartKm = agg.odoStartKm,
            odoEndKm = agg.odoEndKm,
            gpsDistanceM = agg.gpsDistanceM,
            movingSec = agg.movingSec,
            idleSec = agg.idleSec,
            maxSpeedKmh = agg.maxSpeedKmh,
            avgMovingSpeedKmh = agg.avgMovingSpeedKmh,
            fuelBarsStart = agg.fuelBarsStart,
            fuelBarsEnd = agg.fuelBarsEnd,
            speedHistogramJson = agg.histogram.toJson(),
        )
        db.rideDao().update(ride)
        updateRollup(ride)
        updateCalibration()
        activeRideId = null
        return ride
    }

    private suspend fun persistFuelEvent(event: FuelEvent, rideId: Long) {
        db.fuelDao().insert(
            FuelEventEntity(
                ts = event.ts, rideId = rideId, odometerKm = event.odometerKm,
                barsBefore = event.barsBefore, barsAfter = event.barsAfter,
                type = event.type.name,
            )
        )
    }

    private suspend fun updateRollup(ride: RideEntity) {
        val date = Instant.ofEpochMilli(ride.startTs)
            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val km = ride.odoDeltaKm() ?: (ride.gpsDistanceM / 1000.0)
        val barsUsed = maxOf(0, (ride.fuelBarsStart ?: 0) - (ride.fuelBarsEnd ?: 0))
        val prev = db.rollupDao().byDate(date)
        db.rollupDao().upsert(
            DailyRollupEntity(
                date = date,
                km = (prev?.km ?: 0.0) + km,
                rides = (prev?.rides ?: 0) + 1,
                movingSec = (prev?.movingSec ?: 0) + ride.movingSec,
                maxSpeedKmh = maxOf(prev?.maxSpeedKmh ?: 0, ride.maxSpeedKmh),
                fuelBarsUsed = (prev?.fuelBarsUsed ?: 0) + barsUsed,
            )
        )
    }

    /** Re-learn the km-per-bar curve from full fuel history and persist it (spec B4). */
    private suspend fun updateCalibration() {
        val events = db.fuelDao().allChronological().map {
            FuelEvent(
                ts = it.ts, odometerKm = it.odometerKm, barsBefore = it.barsBefore,
                barsAfter = it.barsAfter, type = FuelEventType.valueOf(it.type),
            )
        }
        calibrator.update(events)
        val json = (1..6).mapNotNull { bar ->
            calibrator.kmPerBar(bar)?.let { "\"$bar\":$it" }
        }.joinToString(",", "{", "}")
        db.calibrationDao().put(CalibrationEntity(key = "km_per_bar", value = json))
    }

    private fun RideEntity.odoDeltaKm(): Double? {
        val s = odoStartKm ?: return null
        val e = odoEndKm ?: return null
        return (e - s).toDouble()
    }
}

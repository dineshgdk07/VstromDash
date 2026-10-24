package dev.gdk.bumblebee.ride

/** Final aggregates for one ride (one BLE/engine session). */
data class RideAggregates(
    val startTs: Long,
    val endTs: Long,
    val odoStartKm: Int?,
    val odoEndKm: Int?,
    val gpsDistanceM: Double,
    val movingSec: Int,
    val idleSec: Int,
    val maxSpeedKmh: Int,
    val avgMovingSpeedKmh: Double,
    val fuelBarsStart: Int?,
    val fuelBarsEnd: Int?,
    val histogram: SpeedHistogram,
)

/**
 * Streaming per-ride aggregator. Pure state machine: the caller owns persistence and
 * session boundaries (a Ride = one BLE session per the design spec).
 */
class RideRecorder {
    companion object {
        const val MOVING_THRESHOLD_KMH = 3
        const val MAX_DT_SEC = 10
        const val MIN_GPS_SEGMENT_M = 3.0
        const val MAX_GPS_ACCURACY_M = 25f
    }

    var isActive: Boolean = false
        private set

    private var startTs = 0L
    private var lastTelemetryTs: Long? = null
    private var odoStartKm: Int? = null
    private var odoEndKm: Int? = null
    private var fuelBarsStart: Int? = null
    private var fuelBarsEnd: Int? = null
    private var maxSpeedKmh = 0
    private var movingSec = 0
    private var idleSec = 0
    private var movingSpeedSecSum = 0.0
    private var gpsDistanceM = 0.0
    private var lastFix: GpsFix? = null
    private var histogram = SpeedHistogram()

    fun start(startTs: Long) {
        check(!isActive) { "recorder already active" }
        this.startTs = startTs
        lastTelemetryTs = null
        odoStartKm = null; odoEndKm = null
        fuelBarsStart = null; fuelBarsEnd = null
        maxSpeedKmh = 0; movingSec = 0; idleSec = 0; movingSpeedSecSum = 0.0
        gpsDistanceM = 0.0; lastFix = null
        histogram = SpeedHistogram()
        isActive = true
    }

    fun onTelemetry(sample: TelemetrySample) {
        check(isActive) { "recorder not active" }
        if (odoStartKm == null) odoStartKm = sample.odometerKm
        odoEndKm = sample.odometerKm
        if (fuelBarsStart == null && sample.fuelBars != null) fuelBarsStart = sample.fuelBars
        if (sample.fuelBars != null) fuelBarsEnd = sample.fuelBars
        if (sample.speedKmh > maxSpeedKmh) maxSpeedKmh = sample.speedKmh

        val prevTs = lastTelemetryTs
        lastTelemetryTs = sample.ts
        // The first frame has no interval; time buckets start from the second frame.
        if (prevTs != null) {
            val dt = (((sample.ts - prevTs) / 1000).toInt()).coerceIn(0, MAX_DT_SEC)
            if (sample.speedKmh >= MOVING_THRESHOLD_KMH) {
                movingSec += dt
                movingSpeedSecSum += sample.speedKmh.toDouble() * dt
            } else {
                idleSec += dt
            }
            histogram.add(sample.speedKmh, dt)
        }
    }

    /**
     * Phone-GPS speed sample. Buckets moving/idle time the same way telemetry
     * does, without touching odometer or fuel (those only come from the bike).
     */
    fun onSpeedTick(ts: Long, speedKmh: Int) {
        check(isActive) { "recorder not active" }
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        val prevTs = lastTelemetryTs
        lastTelemetryTs = ts
        if (prevTs != null) {
            val dt = (((ts - prevTs) / 1000).toInt()).coerceIn(0, MAX_DT_SEC)
            if (speedKmh >= MOVING_THRESHOLD_KMH) {
                movingSec += dt
                movingSpeedSecSum += speedKmh.toDouble() * dt
            } else {
                idleSec += dt
            }
            histogram.add(speedKmh, dt)
        }
    }

    fun onGps(fix: GpsFix) {
        check(isActive) { "recorder not active" }
        if (fix.accuracyM != null && fix.accuracyM > MAX_GPS_ACCURACY_M) return
        val prev = lastFix
        if (prev == null) {
            lastFix = fix
            return
        }
        val d = Geo.haversineMeters(prev.lat, prev.lng, fix.lat, fix.lng)
        if (d < MIN_GPS_SEGMENT_M) return
        gpsDistanceM += d
        lastFix = fix
    }

    fun snapshot(now: Long = lastTelemetryTs ?: startTs): RideAggregates {
        check(isActive) { "recorder not active" }
        return aggregates(now)
    }

    fun stop(endTs: Long): RideAggregates {
        check(isActive) { "recorder not active" }
        isActive = false
        return aggregates(endTs)
    }

    private fun aggregates(endTs: Long): RideAggregates {
        return RideAggregates(
            startTs = startTs,
            endTs = endTs,
            odoStartKm = odoStartKm,
            odoEndKm = odoEndKm,
            gpsDistanceM = gpsDistanceM,
            movingSec = movingSec,
            idleSec = idleSec,
            maxSpeedKmh = maxSpeedKmh,
            avgMovingSpeedKmh = if (movingSec > 0) movingSpeedSecSum / movingSec else 0.0,
            fuelBarsStart = fuelBarsStart,
            fuelBarsEnd = fuelBarsEnd,
            histogram = histogram,
        )
    }
}

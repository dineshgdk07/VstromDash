package dev.gdk.bumblebee.ride

object ShareTraces {
    const val SAMPLES = 64
    private const val CLIMB_MIN_M = 40.0
    private const val SMOOTH = 5

    /** Empty when the climb is under 40 m or fewer than 4 altitudes are known. */
    fun elevation(route: List<GpsFix>, climbM: Double?): List<Float> {
        if (climbM == null || climbM < CLIMB_MIN_M) return emptyList()
        val indexed = route.withIndex().filter { it.value.altitudeM != null }
        if (indexed.size < 4) return emptyList()
        val cum = cumulative(route)
        val dist = indexed.map { cum[it.index] }
        val alt = indexed.map { it.value.altitudeM!!.toFloat() }
        val sampled = resample(dist, alt, SAMPLES)
        return normalise(smooth(sampled))
    }

    /** Empty when fewer than 2 fixes. Values are speed ÷ peak, sampled by time. */
    fun speed(route: List<GpsFix>): List<Float> {
        if (route.size < 2) return emptyList()
        val time = route.map { it.ts.toDouble() }
        val speeds = route.map { (it.speedMps ?: 0f).coerceAtLeast(0f) }
        val sampled = resample(time, speeds, SAMPLES)
        val peak = sampled.maxOrNull() ?: return emptyList()
        if (peak <= 0f) return List(SAMPLES) { 0f }
        return sampled.map { (it / peak).coerceIn(0f, 1f) }
    }

    private fun cumulative(route: List<GpsFix>): DoubleArray {
        val out = DoubleArray(route.size)
        for (i in 1 until route.size) {
            val a = route[i - 1]
            val b = route[i]
            out[i] = out[i - 1] + Geo.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        }
        return out
    }

    private fun resample(x: List<Double>, y: List<Float>, n: Int): List<Float> {
        val start = x.first()
        val span = x.last() - start
        return List(n) { i ->
            val target = if (span <= 0.0 || n == 1) start else start + span * i / (n - 1)
            interpolate(x, y, target)
        }
    }

    private fun interpolate(x: List<Double>, y: List<Float>, target: Double): Float {
        if (target <= x.first()) return y.first()
        if (target >= x.last()) return y.last()
        var hi = 1
        while (hi < x.size && x[hi] < target) hi++
        val lo = hi - 1
        val gap = x[hi] - x[lo]
        if (gap <= 0.0) return y[hi]
        val t = ((target - x[lo]) / gap).toFloat()
        return y[lo] + (y[hi] - y[lo]) * t
    }

    private fun smooth(values: List<Float>): List<Float> {
        val radius = SMOOTH / 2
        return values.indices.map { i ->
            var sum = 0f
            var count = 0
            for (j in (i - radius)..(i + radius)) {
                if (j in values.indices) {
                    sum += values[j]
                    count++
                }
            }
            sum / count
        }
    }

    private fun normalise(values: List<Float>): List<Float> {
        val min = values.min()
        val max = values.max()
        val span = max - min
        if (span <= 0f) return List(values.size) { 0f }
        return values.map { ((it - min) / span).coerceIn(0f, 1f) }
    }
}

package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteClusteringTest {

    @Test
    fun `same corridor is one cluster`() {
        val a = linear(12.971, 77.594, 8)
        val b = linear(12.9712, 77.5941, 8)
        val clusters = RouteClustering.cluster(
            listOf(1L, 2L),
            mapOf(1L to a, 2L to b),
        )
        val real = clusters.filter { it.clusterId != RouteClustering.UNTRACKED }
        assertEquals(1, real.size)
        assertEquals(setOf(1L, 2L), real[0].rideIds.toSet())
    }

    @Test
    fun `sparse gps is untracked`() {
        val clusters = RouteClustering.cluster(
            listOf(9L),
            mapOf(9L to listOf(GpsFix(0, 12.97, 77.59))),
        )
        assertEquals(1, clusters.size)
        assertEquals(RouteClustering.UNTRACKED, clusters[0].clusterId)
    }
}

class JourneyDetectorRulesTest {

    private val utc = java.time.ZoneOffset.UTC

    @Test
    fun `chained short sessions become one suggestion`() {
        val rides = listOf(
            JourneyRide(1, 0, 10_000, 1000, 1030),
            JourneyRide(2, 20_000, 30_000, 1030, 1060),
            JourneyRide(3, 40_000, 50_000, 1060, 1100),
        )
        val found = JourneyDetector.detect(rides, JourneyConfig(gapMaxMin = 5, minSegments = 3, minTotalKm = 50), utc)
        assertEquals(1, found.size)
        assertEquals(listOf(1L, 2L, 3L), found[0].rideIds)
        assertEquals(100, found[0].totalKm)
    }

    @Test
    fun `same-day two-stop commute is mergeable`() {
        val day = 1_746_000_000_000L // well inside one UTC day
        val rides = listOf(
            JourneyRide(1, day, day + 40 * 60_000, 2000, 2025),
            JourneyRide(2, day + 70 * 60_000, day + 110 * 60_000, 2025, 2048),
        )
        val found = JourneyDetector.detect(rides, zone = utc)
        assertEquals(1, found.size)
        assertEquals(listOf(1L, 2L), found[0].rideIds)
        assertEquals(48, found[0].totalKm)
        assertTrue(JourneyDetector.caption(found[0], utc).contains("1 engine-off"))
    }

    @Test
    fun `odo may slip one km between BLE sessions`() {
        val rides = listOf(
            JourneyRide(1, 0, 10 * 60_000, 1000, 1020),
            JourneyRide(2, 20 * 60_000, 40 * 60_000, 1021, 1040),
        )
        assertEquals(1, JourneyDetector.detect(rides, zone = utc).size)
    }

    @Test
    fun `a trip that crosses midnight is still one journey`() {
        val start = 86_400_000L - 30 * 60_000L
        val rides = listOf(
            JourneyRide(1, start, start + 10 * 60_000, 1000, 1020),
            JourneyRide(2, start + 40 * 60_000, start + 70 * 60_000, 1020, 1050),
        )
        val found = JourneyDetector.detect(rides, zone = utc)
        assertEquals(1, found.size)
        assertEquals(listOf(1L, 2L), found[0].rideIds)
    }

    @Test
    fun `same calendar day can still be required`() {
        val start = 86_400_000L - 30 * 60_000L
        val rides = listOf(
            JourneyRide(1, start, start + 10 * 60_000, 1000, 1020),
            JourneyRide(2, start + 40 * 60_000, start + 70 * 60_000, 1020, 1050),
        )
        val cfg = JourneyConfig(sameCalendarDay = true)
        assertTrue(JourneyDetector.detect(rides, cfg, utc).isEmpty())
    }

    @Test
    fun `merged children are ignored`() {
        val rides = listOf(
            JourneyRide(1, 0, 10_000, 1000, 1030, mergedIntoId = 9),
            JourneyRide(2, 20_000, 30_000, 1030, 1060, mergedIntoId = 9),
            JourneyRide(3, 40_000, 50_000, 1060, 1100, mergedIntoId = 9),
        )
        assertTrue(JourneyDetector.detect(rides).isEmpty())
    }
}

class FuelTankEstimatorTest {

    @Test
    fun `litres left from last fill is labelled not rough`() {
        val fills = listOf(FuelFill(1, 1_000, 1000, 8.0, 400.0))
        val est = FuelTankEstimator.estimate(
            fills = fills,
            currentOdometerKm = 1120,
            avgKmPerL = 40.0,
            bikeLiveKmPerL = null,
            bikeFuelBars = 4,
            capacityL = 12.0,
        )!!
        assertFalse(est.isRough)
        assertEquals(9.0, est.litresLeft, 0.001)
        assertEquals(360.0, est.rangeKm, 0.001)
    }

    @Test
    fun `no fill uses bars and is rough`() {
        val est = FuelTankEstimator.estimate(
            fills = emptyList(),
            currentOdometerKm = 1000,
            avgKmPerL = null,
            bikeLiveKmPerL = null,
            bikeFuelBars = 3,
            capacityL = 12.0,
            fallbackKmPerL = 40.0,
        )!!
        assertTrue(est.isRough)
        assertEquals(6.0, est.litresLeft, 0.001)
    }
}

class RefuelPredictorTest {

    @Test
    fun `two to three days of riding left`() {
        val p = RefuelPredictor.predict(rangeKm = 90.0, dailyKmPace = 30.0, serviceKmRemaining = 800)
        assertEquals(RefuelBucket.TWO_THREE_DAYS, p.bucket)
        assertFalse(p.fillBeforeService)
    }

    @Test
    fun `unknown without pace`() {
        val p = RefuelPredictor.predict(rangeKm = 90.0, dailyKmPace = null, serviceKmRemaining = null)
        assertEquals(RefuelBucket.UNKNOWN, p.bucket)
    }
}

class RunningCostAnalyticsTest {

    @Test
    fun `fuel plus service rupees over fill odo span`() {
        val cost = RunningCostAnalytics.cost(
            fills = listOf(
                FuelFill(1, 1, 1000, 8.0, 400.0),
                FuelFill(2, 2, 1400, 8.0, 400.0),
            ),
            services = listOf(ServiceSpend(1, 1, 1200, "oil", 800.0)),
        )!!
        assertEquals(400, cost.distanceKm)
        assertEquals(1600.0, cost.totalRupees, 0.001)
        assertEquals(4.0, cost.rupeesPerKm, 0.001)
        assertTrue(cost.serviceFraction > 0)
    }
}

class CsvExportTest {

    @Test
    fun `csv has a header and one sample row`() {
        val csv = CsvExport.rideSamples(
            rideId = 7,
            samples = listOf(
                CsvSample(1_000L, 40, 15000, 1.0, 2.0, 5, 42.0),
            ),
        )
        assertTrue(csv.contains("timestamp_ms,speed_kmh,odometer_km"))
        assertTrue(csv.contains("1000,40,15000,1.0,2.0,5,42.0"))
        assertTrue(csv.contains("# Bumblebee Dash ride #7"))
    }
}

private fun linear(lat: Double, lng: Double, n: Int): List<GpsFix> =
    (0 until n).map { i -> GpsFix(i * 1_000L, lat, lng + i * 0.001) }

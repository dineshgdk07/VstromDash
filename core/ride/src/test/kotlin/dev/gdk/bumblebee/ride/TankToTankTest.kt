package dev.gdk.bumblebee.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TankToTankTest {

    private fun fill(
        id: Long,
        odo: Int,
        litres: Double?,
        full: Boolean = true,
        rupees: Double? = null,
        confirmed: Boolean = false,
    ) = FuelFill(
        id = id,
        ts = id * 1_000,
        odometerKm = odo,
        litres = litres,
        rupees = rupees,
        isFullTank = full,
        outlierConfirmed = confirmed,
    )

    @Test
    fun `full partial partial full uses every litre between the full tanks`() {
        val spans = FuelEconomics.tankSpans(
            listOf(
                fill(1, 1000, 10.0),
                fill(2, 1100, 2.0, full = false),
                fill(3, 1200, 3.0, full = false),
                fill(4, 1400, 8.0),
            ),
        )
        assertEquals(1, spans.size)
        assertEquals(400.0, spans[0].km, 0.001)
        assertEquals(13.0, spans[0].litres, 0.001)
        assertEquals(400.0 / 13.0, spans[0].kmPerL, 0.001)
    }

    @Test
    fun `a partial fill never produces a km per litre on its own`() {
        assertTrue(
            FuelEconomics.tankSpans(
                listOf(fill(1, 1000, 10.0), fill(2, 1100, 2.0, full = false)),
            ).isEmpty(),
        )
        assertTrue(FuelEconomics.perTankKmPerL(listOf(fill(1, 1000, 4.0, full = false))).isEmpty())
    }

    @Test
    fun `cluster accuracy waits for three full-to-full tanks`() {
        val two = FuelEconomics.tankSpans(
            listOf(
                fill(1, 0, 10.0),
                fill(2, 400, 10.0),
                fill(3, 800, 10.0),
            ),
        )
        assertNull(FuelEconomics.clusterAccuracy(38.2, two))
        val three = FuelEconomics.tankSpans(
            listOf(
                fill(1, 0, 10.0),
                fill(2, 349, 10.0),
                fill(3, 698, 10.0),
                fill(4, 1047, 10.0),
            ),
        )
        val accuracy = FuelEconomics.clusterAccuracy(38.2, three)!!
        assertEquals(34.9, accuracy.tankKmPerL, 0.05)
        assertTrue(accuracy.summary.contains("38.2"))
        assertTrue(accuracy.summary.contains("+9%"))
    }

    @Test
    fun `a tank more than 30 percent off the median is left out until confirmed`() {
        val fills = listOf(
            fill(1, 0, 10.0),
            fill(2, 400, 10.0),
            fill(3, 800, 10.0),
            fill(4, 900, 10.0),
        )
        val spans = FuelEconomics.tankSpans(fills)
        val odd = spans.last()
        assertTrue(odd.outlier)
        assertEquals(2, FuelEconomics.perTankKmPerL(fills).size)
        val confirmed = fills.dropLast(1) + fills.last().copy(outlierConfirmed = true)
        assertEquals(3, FuelEconomics.perTankKmPerL(confirmed).size)
    }

    @Test
    fun `odometer jump past a tank of range hints at a missed fill`() {
        assertTrue(FuelEconomics.missedFill(prevOdoKm = 1000, nextOdoKm = 1600, tankRangeKm = 400.0))
        assertTrue(!FuelEconomics.missedFill(1000, 1300, 400.0))
    }

    @Test
    fun `merged average speed is distance over moving time`() {
        assertEquals(70.0, RideMergeMath.averageSpeedKmh(280.0, 4 * 3600), 0.001)
        assertEquals(0.0, RideMergeMath.averageSpeedKmh(10.0, 0), 0.0)
    }

    @Test
    fun `merge preview sums km and moving time and counts stops`() {
        val preview = RideMergeMath.preview(
            km = listOf(40.0, 60.0, 50.0),
            movingSec = listOf(3600, 5400, 3600),
            startTs = listOf(0L, 4_000_000L, 8_000_000L),
            endTs = listOf(3_600_000L, 7_000_000L, 12_000_000L),
        )!!
        assertEquals(150.0, preview.km, 0.001)
        assertEquals(12_600, preview.movingSec)
        assertEquals(2, preview.stops)
        assertEquals(3, preview.rideCount)
        assertEquals(43.0, preview.avgKmh, 0.5)
    }
}

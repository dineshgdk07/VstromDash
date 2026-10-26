package dev.gdk.bumblebee.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration9to10Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BumblebeeDb::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `telemetry gets time indexes and keeps its samples`() {
        helper.createDatabase("m9.db", 9).apply {
            execSQL(
                """
                INSERT INTO telemetry_samples (rideId, ts, speedKmh, odometerKm, tripAKm, tripBKm, fuelBars, fuelEconKml)
                VALUES (1, 100, 40, 24800, 1.0, 2.0, 5, 30.0), (2, 200, 50, 24816, 3.0, 4.0, 4, 31.0)
                """.trimIndent(),
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("m9.db", 10, true, *BumblebeeDb.ALL_MIGRATIONS)
        val indexes = db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'telemetry_samples'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertTrue("index_telemetry_samples_rideId_ts" in indexes)
        assertTrue("index_telemetry_samples_ts" in indexes)
        assertTrue("index_telemetry_samples_rideId" !in indexes)
        db.query("SELECT odometerKm FROM telemetry_samples ORDER BY ts DESC LIMIT 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(24816, c.getInt(0))
        }
        db.close()
    }
}

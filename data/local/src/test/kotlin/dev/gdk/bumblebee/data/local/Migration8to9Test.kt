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
class Migration8to9Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BumblebeeDb::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `new ride columns are null and old fields survive`() {
        helper.createDatabase("m8.db", 8).apply {
            execSQL(
                """
                INSERT INTO rides (
                    startTs, gpsDistanceM, movingSec, idleSec, maxSpeedKmh, avgMovingSpeedKmh,
                    isDemo, gpsOnly, rideState, name, odoEndKm
                ) VALUES (42, 1500.0, 90, 0, 80, 40.0, 0, 0, 'FINISHED', 'Dawn', 24816)
                """.trimIndent(),
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("m8.db", 9, true, *BumblebeeDb.ALL_MIGRATIONS)
        db.query(
            "SELECT name, odoEndKm, startPlace, endPlace, weatherTempC, weatherCode, climbM FROM rides",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Dawn", cursor.getString(0))
            assertEquals(24816, cursor.getInt(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
            assertTrue(cursor.isNull(5))
            assertTrue(cursor.isNull(6))
        }
        db.close()
    }
}

package dev.gdk.bumblebee.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration1to6Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BumblebeeDb::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1to6() {
        helper.createDatabase("mig.db", 1).apply {
            execSQL("INSERT INTO rides (startTs, gpsDistanceM, movingSec, idleSec, maxSpeedKmh, avgMovingSpeedKmh) VALUES (1, 0, 0, 0, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate("mig.db", 8, true, *BumblebeeDb.ALL_MIGRATIONS)
    }
}

package dev.gdk.bumblebee.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RideEntity::class,
        TelemetrySampleEntity::class,
        TrackPointEntity::class,
        FuelEventEntity::class,
        DailyRollupEntity::class,
        CalibrationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BumblebeeDb : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun trackDao(): TrackDao
    abstract fun fuelDao(): FuelDao
    abstract fun rollupDao(): RollupDao
    abstract fun calibrationDao(): CalibrationDao

    companion object {
        private const val DB_NAME = "bumblebee.db"

        fun create(context: Context): BumblebeeDb =
            Room.databaseBuilder(context, BumblebeeDb::class.java, DB_NAME).build()

        /** In-memory instance for tests. */
        fun inMemory(context: Context): BumblebeeDb =
            Room.inMemoryDatabaseBuilder(context, BumblebeeDb::class.java)
                .allowMainThreadQueries()
                .build()
    }
}

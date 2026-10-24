package dev.gdk.bumblebee.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RideEntity::class,
        TelemetrySampleEntity::class,
        TrackPointEntity::class,
        FuelEventEntity::class,
        DailyRollupEntity::class,
        CalibrationEntity::class,
        ServiceLogEntity::class,
        BikeDocumentEntity::class,
        MaintenancePlanEntity::class,
        RideSegmentEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class BumblebeeDb : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun trackDao(): TrackDao
    abstract fun fuelDao(): FuelDao
    abstract fun rollupDao(): RollupDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun serviceDao(): ServiceDao
    abstract fun documentDao(): DocumentDao
    abstract fun planDao(): PlanDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        private const val DB_NAME = "bumblebee.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fuel_events ADD COLUMN note TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fuel_events ADD COLUMN source TEXT NOT NULL DEFAULT 'AUTO'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN name TEXT")
                db.execSQL("ALTER TABLE rides ADD COLUMN tags TEXT")
                db.execSQL("ALTER TABLE rides ADD COLUMN mergedIntoId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS service_logs (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ts` INTEGER NOT NULL, `odometerKm` INTEGER NOT NULL, `item` TEXT NOT NULL, `rupees` REAL, `note` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_service_logs_ts ON service_logs (`ts`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE service_logs ADD COLUMN shop TEXT")
                db.execSQL("ALTER TABLE service_logs ADD COLUMN planId INTEGER")
                db.execSQL("ALTER TABLE service_logs ADD COLUMN filePath TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bike_documents (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `expiryIso` TEXT, `number` TEXT, `filePath` TEXT, `mime` TEXT, `note` TEXT, `createdTs` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS maintenance_plans (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `intervalKm` INTEGER, `lastDoneOdoKm` INTEGER, `intervalDays` INTEGER, `lastDoneOnIso` TEXT, `dueOnIso` TEXT, `active` INTEGER NOT NULL, `createdTs` INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN rideState TEXT NOT NULL DEFAULT 'FINISHED'")
                db.execSQL("UPDATE rides SET rideState = 'ACTIVE' WHERE endTs IS NULL")
                db.execSQL("ALTER TABLE rides ADD COLUMN pausedAt INTEGER")
                db.execSQL("ALTER TABLE fuel_events ADD COLUMN isFullTank INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE fuel_events ADD COLUMN pricePerLitre REAL")
                db.execSQL("ALTER TABLE fuel_events ADD COLUMN outlierConfirmed INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ride_segments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rideId` INTEGER NOT NULL, `startTs` INTEGER NOT NULL, `endTs` INTEGER, `odoStartKm` INTEGER, `odoEndKm` INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ride_segments_rideId` ON `ride_segments` (`rideId`)")
                db.execSQL(
                    """
                    INSERT INTO ride_segments (rideId, startTs, endTs, odoStartKm, odoEndKm)
                    SELECT id, startTs, endTs, odoStartKm, odoEndKm FROM rides
                    WHERE mergedIntoId IS NULL
                      AND id NOT IN (SELECT mergedIntoId FROM rides WHERE mergedIntoId IS NOT NULL)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO ride_segments (rideId, startTs, endTs, odoStartKm, odoEndKm)
                    SELECT p.id, p.startTs,
                      (SELECT MIN(c.startTs) FROM rides c WHERE c.mergedIntoId = p.id),
                      p.odoStartKm,
                      (SELECT c.odoStartKm FROM rides c WHERE c.mergedIntoId = p.id ORDER BY c.startTs LIMIT 1)
                    FROM rides p
                    WHERE p.id IN (SELECT mergedIntoId FROM rides WHERE mergedIntoId IS NOT NULL)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO ride_segments (rideId, startTs, endTs, odoStartKm, odoEndKm)
                    SELECT mergedIntoId, startTs, endTs, odoStartKm, odoEndKm
                    FROM rides WHERE mergedIntoId IS NOT NULL
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DROP INDEX IF EXISTS `index_track_points_rideId`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_points_rideId_ts` ON track_points (`rideId`, `ts`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_points_ts` ON track_points (`ts`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN gpsOnly INTEGER NOT NULL DEFAULT 0")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        )

        fun create(context: Context): BumblebeeDb =
            Room.databaseBuilder(context, BumblebeeDb::class.java, DB_NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()

        /** In-memory instance for tests. */
        fun inMemory(context: Context): BumblebeeDb =
            Room.inMemoryDatabaseBuilder(context, BumblebeeDb::class.java)
                .allowMainThreadQueries()
                .build()
    }
}

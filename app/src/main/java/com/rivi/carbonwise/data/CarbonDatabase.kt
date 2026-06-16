package com.rivi.carbonwise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EntryEntity::class, DetectedSegmentEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class CarbonDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun detectedSegmentDao(): DetectedSegmentDao

    companion object {
        @Volatile
        private var instance: CarbonDatabase? = null

        /** v1 → v2 adds the Activity-Recognition detected_segments table. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `detected_segments` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`kind` TEXT NOT NULL, " +
                        "`startMillis` INTEGER NOT NULL, " +
                        "`endMillis` INTEGER, " +
                        "`status` TEXT NOT NULL)",
                )
            }
        }

        /** v2 → v3 adds GPS metric columns to detected_segments. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE detected_segments ADD COLUMN distanceMeters REAL")
                db.execSQL("ALTER TABLE detected_segments ADD COLUMN avgSpeedKmh REAL")
                db.execSQL("ALTER TABLE detected_segments ADD COLUMN maxSpeedKmh REAL")
                db.execSQL("ALTER TABLE detected_segments ADD COLUMN stopCount INTEGER")
                db.execSQL("ALTER TABLE detected_segments ADD COLUMN gpsGaps INTEGER")
                db.execSQL("ALTER TABLE detected_segments ADD COLUMN suggestedType TEXT")
            }
        }

        fun get(context: Context): CarbonDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CarbonDatabase::class.java,
                    "carbonwise.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}

package com.rivi.carbonwise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EntryEntity::class, DetectedSegmentEntity::class],
    version = 2,
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

        fun get(context: Context): CarbonDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CarbonDatabase::class.java,
                    "carbonwise.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

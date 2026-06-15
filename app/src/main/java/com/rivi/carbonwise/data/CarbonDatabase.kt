package com.rivi.carbonwise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EntryEntity::class], version = 1, exportSchema = false)
abstract class CarbonDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        @Volatile
        private var instance: CarbonDatabase? = null

        fun get(context: Context): CarbonDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CarbonDatabase::class.java,
                    "carbonwise.db",
                ).build().also { instance = it }
            }
    }
}

package com.punegork.ioniqtelemetry.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, TelemetrySampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun sampleDao(): TelemetrySampleDao
}

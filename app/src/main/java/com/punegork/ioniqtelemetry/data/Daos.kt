package com.punegork.ioniqtelemetry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Query("SELECT * FROM trips ORDER BY startTimeMs DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): TripEntity?
}

@Dao
interface TelemetrySampleDao {
    @Insert
    suspend fun insert(sample: TelemetrySampleEntity)

    @Query("SELECT * FROM telemetry_samples WHERE tripId = :tripId ORDER BY timestampMs")
    fun observeForTrip(tripId: Long): Flow<List<TelemetrySampleEntity>>
}

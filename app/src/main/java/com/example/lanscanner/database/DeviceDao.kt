package com.example.lanscanner.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    // Insert or update a device. If MAC already exists, it is replaced
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    // Get all devices as Flow
    // UI will subscribe to this Flow to receive updates
    @Query("SELECT * FROM devices ORDER BY ip")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    // Get all devices (non Flow version)
    @Query("SELECT * FROM devices")
    suspend fun getAllDevicesAsList(): List<DeviceEntity>

    // Put all devices offline
    // Will be called before each scan
    @Query("UPDATE devices SET isOnline = 0")
    suspend fun setAllDevicesOffline()

    // Clear all devices
    @Query("DELETE FROM devices")
    suspend fun clearAllDevices()




}
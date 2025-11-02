package com.example.lanscanner.database

import kotlinx.coroutines.flow.Flow

/**
 * This repository is a link between ViewModel and database.
 */
class DeviceRepository(private val deviceDao: DeviceDao) {
    /**
     * A Flow of all devices. Viewmodel will observe.
     */
    val allDevices: Flow<List<DeviceEntity>> = deviceDao.getAllDevices()

    /**
     * Get list of devices.
     */
    suspend fun getAllDevicesAsList(): List<DeviceEntity> {
        return deviceDao.getAllDevicesAsList()
    }

    /**
     * Update or insert a device.
     */
    suspend fun upsertDevice(device: DeviceEntity) {
        deviceDao.upsert(device)
    }

    /**
     * Mark all devices as offline
     */
    suspend fun setAllDevicesOffline() {
        deviceDao.setAllDevicesOffline()
    }

    /**
     * Empty database
     */
    suspend fun clearAllDevices() {
        deviceDao.clearAllDevices()
    }
}
package com.example.lanscanner.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represent a device in database
 * MAC address is the primary key because it is unique and stable
 */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val mac: String,
    val ip: String,
    val hostname: String,
    val lastSeen: Long,
    val isOnline: Boolean
)
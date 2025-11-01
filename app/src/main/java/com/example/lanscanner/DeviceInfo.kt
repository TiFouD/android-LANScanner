package com.example.lanscanner

/**
 * Data class representing information about a discovered network device.
 *
 * @param ip The IP address of the device.
 * @param hostname The hostname of the device.
 * @param mac The MAC address of the device, if available.
 */
data class DeviceInfo(
    val ip: String,
    val hostname: String,
    val mac: String?
)
package com.example.lanscanner

/**
 * Represent a device as shown in the UI.
 * Contains connection status.
 */
data class UiDevice(
    val ip: String,
    val hostname: String,
    val mac: String?,
    val isOnline: Boolean
)
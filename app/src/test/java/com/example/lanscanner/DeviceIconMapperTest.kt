package com.example.lanscanner

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIconMapperTest {

    @Test
    fun `getVendorName returns NA when mac address is null`() {
        val result = DeviceIconMapper.getVendorName(null)
        assertEquals("N/A", result)
    }

    @Test
    fun `getIconForDevice returns UNKNOWN_ICON when mac is null`() {
        val result = DeviceIconMapper.getIconForDevice(null)
        assertEquals(DeviceIconMapper.UNKNOWN_ICON, result)
    }
}
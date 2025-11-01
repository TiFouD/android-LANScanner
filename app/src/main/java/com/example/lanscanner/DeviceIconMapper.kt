package com.example.lanscanner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Object to map MAC addresses (specifically the OUI) to appropriate [ImageVector] icons.
 */
object DeviceIconMapper {

    val ROUTER_ICON = Icons.Default.Router
    val PC_ICON = Icons.Default.Computer
    val PHONE_ICON = Icons.Default.PhoneAndroid
    val APPLE_ICON = Icons.Default.PhoneIphone
    val GOOGLE_ICON = Icons.Default.SmartDisplay
    val SAMSUNG_ICON = Icons.Default.Tv
    val TV_ICON = Icons.Default.Tv
    val SMART_DEVICE_ICON = Icons.Default.SettingsRemote
    val CONSOLE_ICON = Icons.Default.VideogameAsset
    val PRINTER_ICON = Icons.Default.Print
    val NETWORK_ICON = Icons.Default.SettingsEthernet
    val UNKNOWN_ICON = Icons.Default.QuestionMark

    private val ouiToIconMap = mapOf(
        // == Cases where MAC address is not retrieved ==
        null to UNKNOWN_ICON,

        // == Networking Devices (Routers, Switches, NAS) ==
        "B8:27:EB" to SMART_DEVICE_ICON, // Raspberry Pi
        "DC:A6:32" to SMART_DEVICE_ICON, // Raspberry Pi (New)
        "00:11:32" to ROUTER_ICON,     // Synology (NAS)
        "00:14:BF" to ROUTER_ICON,     // Linksys
        "00:25:9C" to ROUTER_ICON,     // Linksys
        "00:0F:B5" to ROUTER_ICON,     // Netgear
        "08:02:8E" to ROUTER_ICON,     // Netgear
        "E0:91:F5" to ROUTER_ICON,     // Netgear
        "00:14:78" to ROUTER_ICON,     // TP-Link
        "B0:4E:26" to ROUTER_ICON,     // TP-Link
        "00:0C:42" to ROUTER_ICON,     // Cisco
        "00:40:96" to ROUTER_ICON,     // Cisco
        "08:60:6E" to ROUTER_ICON,     // Asus (AsusTek)
        "1C:87:2C" to ROUTER_ICON,     // Asus (AsusTek)
        "04:18:D6" to ROUTER_ICON,     // Ubiquiti Networks
        "F0:9F:C2" to ROUTER_ICON,     // Ubiquiti Networks
        "38:07:16" to ROUTER_ICON,     // Ubiquiti Networks

        // == Apple Devices ==
        "BC:54:2F" to APPLE_ICON, // Apple
        "FC:D8:48" to APPLE_ICON, // Apple
        "00:03:93" to APPLE_ICON, // Apple (Old)
        "00:0A:95" to APPLE_ICON, // Apple (AirPort)
        "00:16:CB" to APPLE_ICON, // Apple (iPhone/Mac)
        "00:25:00" to APPLE_ICON, // Apple
        "40:A3:CC" to APPLE_ICON, // Apple
        "88:6B:6E" to APPLE_ICON, // Apple
        "6A:45:55" to APPLE_ICON, // Apple

        // == Google / Nest Devices ==
        "A0:B3:95" to GOOGLE_ICON,     // Google (Pixel)
        "00:1A:11" to GOOGLE_ICON,     // Google (Nest)
        "18:B4:30" to GOOGLE_ICON,     // Google (Nest)
        "94:EB:2C" to GOOGLE_ICON,     // Google (Nest)
        "F8:8F:CA" to GOOGLE_ICON,     // Google (Chromecast)
        "E4:F0:42" to GOOGLE_ICON,     // Google (Pixel)
        "CE:19:EF" to GOOGLE_ICON,     // Google (Pixel)
        "5A:14:94" to GOOGLE_ICON,     // Google (Pixel)

        // == Samsung Devices ==
        "E8:B5:A0" to SAMSUNG_ICON, // Samsung (TV)
        "00:16:DB" to SAMSUNG_ICON, // Samsung (General)
        "00:07:AB" to SAMSUNG_ICON, // Samsung (SmartThings)
        "00:12:FB" to SAMSUNG_ICON, // Samsung (Phones)
        "BC:A9:93" to SAMSUNG_ICON, // Samsung (Phones)

        // == Smart Home / IoT ==
        "00:FC:8B" to SMART_DEVICE_ICON, // Amazon (Echo / Kindle)
        "18:74:2E" to SMART_DEVICE_ICON, // Amazon (Echo / Kindle)
        "74:C6:3B" to SMART_DEVICE_ICON, // Amazon (Echo / Kindle)
        "00:17:88" to SMART_DEVICE_ICON, // Philips Hue (Signify)
        "54:2A:1B" to SMART_DEVICE_ICON, // Sonos
        "B4:75:0E" to SMART_DEVICE_ICON, // Belkin / Wemo
        "00:15:BC" to SMART_DEVICE_ICON, // Tado (Thermostats)
        "B4:5D:50" to SMART_DEVICE_ICON, // Ring (Doorbells)
        "28:D0:EA" to PHONE_ICON,        // Xiaomi
        "F4:F5:DB" to PHONE_ICON,        // Xiaomi

        // == PC / Components ==
        "00:0D:3A" to PC_ICON,         // Microsoft (Surface / Xbox)
        "00:50:F2" to PC_ICON,         // Microsoft
        "58:CD:C9" to PC_ICON,         // Microsoft
        "00:08:C7" to PRINTER_ICON,    // HP (Printers)
        "00:17:A4" to PC_ICON,         // HP (PC)
        "3C:D9:2B" to PC_ICON,         // HP (PC)
        "00:14:22" to PC_ICON,         // Dell
        "00:1D:09" to PC_ICON,         // Dell
        "00:50:8D" to PC_ICON,         // Lenovo
        "00:02:B3" to NETWORK_ICON,    // Intel (Network Cards)
        "00:1C:C0" to NETWORK_ICON,    // Intel (Network Cards)
        "00:E0:4C" to NETWORK_ICON,    // Realtek (Very common)

        // == TV / Consoles ==
        "00:04:27" to TV_ICON,         // Sony (TV)
        "00:13:A9" to CONSOLE_ICON,    // Sony (PlayStation 3/PSP)
        "00:DD:08" to CONSOLE_ICON,    // Sony (PlayStation 4)
        "70:EC:86" to CONSOLE_ICON,    // Sony (PlayStation 5)
        "00:19:7D" to TV_ICON,         // LG (TV)
        "00:30:57" to TV_ICON,         // LG
        "00:1F:C6" to CONSOLE_ICON,    // Nintendo (Wii)
        "00:24:1E" to CONSOLE_ICON,    // Nintendo (DS/3DS)
        "B8:8A:E3" to CONSOLE_ICON,    // Nintendo (Switch)

        // == Mobile (Others) ==
        "00:1E:10" to PHONE_ICON, // Huawei
        "FC:E9:98" to PHONE_ICON, // Huawei
    )

    /**
     * Returns an [ImageVector] icon based on the provided MAC address's OUI (Organizationally Unique Identifier).
     *
     * @param macAddress The MAC address of the device. Can be null.
     * @return An [ImageVector] representing the device type, or [UNKNOWN_ICON] if the MAC address is null or not recognized.
     */
    fun getIconForDevice(macAddress: String?): ImageVector {
        if (macAddress == null || macAddress.length < 8) {
            return UNKNOWN_ICON
        }

        val oui = macAddress.substring(0, 8).uppercase()

        return ouiToIconMap[oui] ?: UNKNOWN_ICON
    }
}
package com.example.lanscanner

import android.content.Context
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data class to represent an OUI entry from the JSON file.
 *
 * @property oui The Organizationally Unique Identifier.
 * @property manufacturer The name of the manufacturer associated with the OUI.
 */
@Serializable
private data class OuiEntry(val oui: String, val manufacturer: String)

/**
 * Provides functionality to map MAC addresses to device icons and manufacturer names.
 * This object initializes an OUI database from a JSON asset and uses it for lookups.
 */
object DeviceIconMapper {

    // The icons remain the same
    /** Icon for a router device. */
    val ROUTER_ICON = Icons.Default.Router

    /** Icon for a personal computer. */
    val PC_ICON = Icons.Default.Computer

    /** Icon for an Android phone. */
    val PHONE_ICON = Icons.Default.PhoneAndroid

    /** Icon for an Apple iPhone. */
    val APPLE_ICON = Icons.Default.PhoneIphone

    /** Icon for a Google smart display or similar device. */
    val GOOGLE_ICON = Icons.Default.SmartDisplay

    /** Icon for a Samsung TV or similar device. */
    val SAMSUNG_ICON = Icons.Default.Tv

    /** Icon for a generic TV. */
    val TV_ICON = Icons.Default.Tv

    /** Icon for a smart home device or remote. */
    val SMART_DEVICE_ICON = Icons.Default.SettingsRemote

    /** Icon for a gaming console. */
    val CONSOLE_ICON = Icons.Default.VideogameAsset

    /** Icon for a printer. */
    val PRINTER_ICON = Icons.Default.Print

    /** Icon for a generic network device. */
    val NETWORK_ICON = Icons.Default.SettingsEthernet

    /** Icon for an unknown device. */
    val UNKNOWN_ICON = Icons.Default.QuestionMark

    /**
     * Map to store JSON data (OUI -> Manufacturer Name).
     * e.g.: "b827eb" -> "Raspberry Pi Foundation"
     */
    private var ouiToVendorMap: Map<String, String> = emptyMap()

    /** Indicates if the OUI database has been initialized. */
    private var isInitialized = false

    /**
     * Map to associate a manufacturer KEYWORD with an ICON.
     * Feel free to enrich this list!
     */
    private val manufacturerKeywordsToIconMap = mapOf(
        "APPLE" to APPLE_ICON,
        "GOOGLE" to GOOGLE_ICON,
        "SAMSUNG" to SAMSUNG_ICON,
        "SONY" to TV_ICON,
        "LG ELECTRONICS" to TV_ICON,
        "NINTENDO" to CONSOLE_ICON,
        "MICROSOFT" to PC_ICON,
        "RASPBERRY PI" to SMART_DEVICE_ICON,
        "SYNOLOGY" to ROUTER_ICON,
        "NETGEAR" to ROUTER_ICON,
        "TP-LINK" to ROUTER_ICON,
        "LINKSYS" to ROUTER_ICON,
        "ASUSTEK" to ROUTER_ICON,
        "UBIQUITI" to ROUTER_ICON,
        "AMAZON" to SMART_DEVICE_ICON,
        "PHILIPS" to SMART_DEVICE_ICON,
        "SIGNIFY" to SMART_DEVICE_ICON, // For Hue
        "SONOS" to SMART_DEVICE_ICON,
        "BELKIN" to SMART_DEVICE_ICON,
        "XIAOMI" to PHONE_ICON,
        "HUAWEI" to PHONE_ICON,
        "ONEPLUS" to PHONE_ICON,
        "OPPO" to PHONE_ICON,
        "HP" to PRINTER_ICON,
        "HEWLETT PACKARD" to PRINTER_ICON,
        "DELL" to PC_ICON,
        "LENOVO" to PC_ICON,
        "INTEL" to NETWORK_ICON,
        "REALTEK" to NETWORK_ICON
    )

    /**
     * Initializes the OUI database.
     * Should be called only once at app startup.
     *
     * @param context The application context, used to access assets.
     */
    fun init(context: Context) {
        if (isInitialized) return
        try {
            // Reads the file from the "assets" folder
            val jsonString = context.assets.open("lightweight_oui.json")
                .bufferedReader()
                .use { it.readText() }

            val json = Json { ignoreUnknownKeys = true }

            // Parses the JSON into a list of OuiEntry
            val entries = json.decodeFromString<List<OuiEntry>>(jsonString)

            // Converts the list into a Map for quick lookup
            ouiToVendorMap = entries.associate { it.oui to it.manufacturer }
            isInitialized = true
            Log.d("DeviceIconMapper", "OUI database loaded: ${ouiToVendorMap.size} manufacturers.")
        } catch (e: Exception) {
            Log.e("DeviceIconMapper", "Failed to load OUI database", e)
        }
    }

    /**
     * Retrieves an icon for a given device based on its MAC address.
     * This function handles format conversion and keyword-based icon lookup.
     *
     * @param macAddress The MAC address of the device. Can be null.
     * @return An [ImageVector] representing the device's icon. Returns [UNKNOWN_ICON] if MAC is null or invalid,
     *         or [NETWORK_ICON] if no specific icon is found.
     */
    fun getIconForDevice(macAddress: String?): ImageVector {
        if (macAddress == null) {
            return UNKNOWN_ICON
        }

        // 1. Format the MAC to match the database
        //    e.g.: "B8:27:EB:..." -> "b827eb"
        val ouiKey = macAddress.take(8)
            .replace(":", "")
            .lowercase()

        if (ouiKey.length != 6) {
            return UNKNOWN_ICON
        }

        // 2. Find the manufacturer name (e.g., "Raspberry Pi Foundation")
        val manufacturer = ouiToVendorMap[ouiKey]?.uppercase() ?: return NETWORK_ICON

        // 3. Look for a keyword (e.g., "RASPBERRY PI") in our icon map
        manufacturerKeywordsToIconMap.forEach { (keyword, icon) ->
            if (manufacturer.contains(keyword)) {
                return icon
            }
        }

        // 4. If no keyword matches, return a default network icon
        return NETWORK_ICON
    }

    /**
     * Retrieves the vendor name for a given device based on its MAC address.
     * This function is used to display the manufacturer's name in the UI.
     *
     * @param macAddress The MAC address of the device. Can be null.
     * @return The manufacturer's name as a [String]. Returns "N/A" if MAC is null or invalid,
     *         or "Unknown manufacturer" if the OUI is not found in the database.
     */
    fun getVendorName(macAddress: String?): String {
        if (macAddress == null) return "N/A"

        val ouiKey = macAddress.take(8)
            .replace(":", "")
            .lowercase()

        if (ouiKey.length != 6) return "N/A"

        // Returns the manufacturer name or "Unknown manufacturer"
        return ouiToVendorMap[ouiKey] ?: "Unknown manufacturer"
    }
}
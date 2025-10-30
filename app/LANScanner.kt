package com.example.lanscanner

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Simple class to hold the results.
 */
data class DeviceInfo(val ip: String, val hostname: String)

// --- NETWORK LOGIC CLASS ---
class NetworkScanner(private val context: Context) {

    private val TAG = "NetworkScanner"

    /**
     * 'suspend' means that this function must be called
     * from a Coroutine. It can be "paused"
     * without blocking the thread.
     */
    suspend fun startScan(): List<DeviceInfo> {
        val devices = mutableListOf<DeviceInfo>()

        // Executes on the "IO" (Input/Output) thread pool,
        // optimized for network/disk operations.
        return withContext(Dispatchers.IO) {
            val subnet = getSubnet()
            if (subnet == null) {
                Log.e(TAG, "Error: Could not find subnet. Is Wi-Fi enabled?")
                return@withContext emptyList<DeviceInfo>()
            }

            Log.i(TAG, "Scanning subnet: $subnet.xxx")

            // 'coroutineScope' is used to launch multiple jobs
            // in parallel.
            val jobs = (1..254).map { i ->
                launch { // 'launch' starts a new coroutine
                    val ip = "$subnet.$i"
                    val reachableDevice = scanIp(ip)
                    if (reachableDevice != null) {
                        // 'synchronized' to avoid
                        // concurrent access issues with the list.
                        synchronized(devices) {
                            devices.add(reachableDevice)
                        }
                    }
                }
            }

            // Waits for all jobs (1-254) to complete.
            jobs.forEach { it.join() }

            Log.i(TAG, "Scan finished. ${devices.size} devices found.")

            // Sort by IP before returning.
            return@withContext devices.sortedBy { device ->
                // Numerical sort of the IP.
                device.ip.split(".").map { it.toInt() }
                    .let { it[0] * 1000000 + it[1] * 1000 + it[2] * 100 + it[3] }
            }
        }
    }

    /**
     * Attempts to connect to an IP.
     */
    private suspend fun scanIp(ip: String): DeviceInfo? {
        return try {
            val socket = Socket()
            // Attempts a connection on port 135 (TCP) with a 50ms timeout.
            // Port 135 = Microsoft EPMAP. Often open/denied on Windows.
            // Other ports to test: 22 (SSH), 80 (HTTP), 443 (HTTPS).
            socket.connect(InetSocketAddress(ip, 135), 50)
            socket.close()

            // Host found! Attempting to resolve hostname.
            val hostname = try {
                val name = InetAddress.getByName(ip).hostName
                if (name == ip) "Hostname not found" else name
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving Hostname for $ip: ${e.message}")
                "Hostname not found" // If hostname is not found, use the IP.
            }
            Log.i(TAG, "Host found: $ip ($hostname)")
            DeviceInfo(ip, hostname)

        } catch (e: Exception) {
            // e.g., SocketTimeoutException, ConnectException.
            // This is normal, it means the host is not there or the port is filtered.
            null // Timeout or connection refused.
        }
    }

    /**
     * Gets the subnet (e.g., "192.168.1").
     */
    private fun getSubnet(): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Requests the active WiFi network.
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            Log.w(TAG, "Active network is null.")
            return null
        }

        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
        if (linkProperties == null) {
            Log.w(TAG, "LinkProperties are null.")
            return null
        }

        // Iterates over the IP addresses of this link (can be IPv4 and IPv6).
        linkProperties.linkAddresses.forEach { linkAddress ->
            val address = linkAddress.address
            // We only want the IPv4 address.
            if (address is java.net.Inet4Address) {
                val ip = address.hostAddress
                if (ip != null && !ip.startsWith("127.")) {
                    // Returns the prefix (e.g., "192.168.1").
                    return ip.substring(0, ip.lastIndexOf('.'))
                }
            }
        }
        Log.e(TAG, "No non-loopback IPv4 address found.")
        return null // WiFi not connected or IP not found.
    }
}

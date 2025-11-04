package com.example.lanscanner

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Scans the local network to discover connected devices.
 *
 * @param context The application context.
 */
class NetworkScanner(private val context: Context) {

    private val TAG = "NetworkScanner"

    /**
     * Starts a network scan to discover devices on the local subnet.
     *
     * @return A list of [DeviceInfo] objects representing the discovered devices, sorted by IP address.
     *         Returns an empty list if the subnet cannot be determined or no devices are found.
     */
    suspend fun startScan(): List<DeviceInfo> {
        val subnet = getSubnet()
        if (subnet == null) {
            Log.e(TAG, "Subnet not found, is Wifi on?")
            return emptyList()
        }

        Log.i(TAG, "Scanning subnet : $subnet.xxx")
        val devices = mutableListOf<DeviceInfo>()
        return withContext(Dispatchers.IO) {
            coroutineScope {
                val jobs = (1..254).map { i ->
                    launch {
                        val ip = "$subnet.$i"

                        val reachableDevice = scanIp(ip)

                        if (reachableDevice != null) {
                            synchronized(devices) {
                                devices.add(reachableDevice)
                            }
                        }
                    }
                }

                jobs.joinAll()
            }

            Log.i(TAG, "Scan done. ${devices.size} found.")

            return@withContext devices.sortedBy { device ->
                device.ip.split(".").map { it.toInt() }
                    .let { it[0] * 1000000 + it[1] * 1000 + it[2] * 100 + it[3] }
            }
        }
    }

    /**
     * Retrieves the local subnet from the active network connection.
     *
     * @return The subnet string (e.g., "192.168.1"), or null if no active network or subnet is found.
     */
    private fun getSubnet(): String? {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null

        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address
            if (address is InetAddress && !address.isLoopbackAddress) {
                val ip = address.hostAddress
                if (ip != null) {
                    val lastDotIndex = ip.lastIndexOf('.')
                    if (lastDotIndex > 0) {
                        return ip.substring(0, lastDotIndex)
                    }
                }
            }
        }

        Log.e(TAG, "No subnet found after checking all link addresses.")
        return null
    }

    /**
     * Scans a single IP address to determine if a device is reachable and attempts to resolve its hostname.
     *
     * @param ip The IP address to scan.
     * @return A [DeviceInfo] object if the device is reachable, otherwise null.
     */
    private suspend fun scanIp(ip: String): DeviceInfo? {
        return withContext(Dispatchers.IO) {
            var socket: Socket? = null
            var hostIsAlive = false

            try {
                socket = Socket()
                val socketAddress = InetSocketAddress(ip, 135)

                socket.connect(socketAddress, 50)

                hostIsAlive = true

            } catch (e: java.net.ConnectException) {
                if (e.message?.contains("ECONNREFUSED") == true) {
                    hostIsAlive = true
                } else {
                    // Do nothing
                }
            } catch (_: java.net.SocketTimeoutException) {
            } catch (e: Exception) {
                Log.d(TAG, "Unknown error while scanning $ip: ${e.message}")
            } finally {
                socket?.close()
            }

            if (hostIsAlive) {
                val hostname = try {
                    val name = InetAddress.getByName(ip).canonicalHostName
                    if (name == ip) "N/A" else name
                } catch (e: Exception) {
                    Log.w(TAG, "Hostname not found for $ip: ${e.message}")
                    "N/A"
                }

                Log.i(TAG, "Host found : $ip ($hostname)")

                return@withContext DeviceInfo(
                    ip = ip,
                    hostname = hostname,
                    mac = null
                )
            } else {
                return@withContext null
            }
        }
    }

}
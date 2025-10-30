package com.example.lanscanner

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.Frame
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.io.Closeable
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


private const val TAG = "FreeboxManager"

//<editor-fold desc="Data Classes">
@Serializable
data class AuthorizeRequest(
    val app_id: String,
    val app_name: String,
    val app_version: String,
    val device_name: String
)

@Serializable
data class AuthorizeResponse(
    val success: Boolean,
    val result: AuthorizeResult? = null
)

@Serializable
data class AuthorizeResult(
    val app_token: String,
    val track_id: Int
)

@Serializable
data class TrackAuthorizationProgressResponse(
    val success: Boolean,
    val result: TrackAuthorizationProgressResult? = null
)

@Serializable
data class TrackAuthorizationProgressResult(
    val status: String,
    val challenge: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val result: LoginResult? = null
)

@Serializable
data class LoginResult(
    val logged_in: Boolean? = null,
    val challenge: String,
    val session_token: String? = null
)

@Serializable
data class LanDeviceResponse(
    val success: Boolean,
    val result: List<LanDevice>? = null
)

@Serializable
data class LanDevice(
    val primary_name: String,
    val l3connectivities: List<L3Connectivity>? = null
)

@Serializable
data class L3Connectivity(
    val addr: String,
    val active: Boolean
)
//</editor-fold>

class FreeboxManager(private val context: Context) : Closeable {

    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    private val sharedPreferences by lazy {
        context.getSharedPreferences("freebox_prefs", Context.MODE_PRIVATE)
    }

    var appToken: String?
        get() = sharedPreferences.getString("app_token", null)
        set(value) {
            sharedPreferences.edit().putString("app_token", value).apply()
        }

    private var sessionToken: String? = null


    suspend fun discoverFreebox(): NsdServiceInfo? {
        var serviceInfo: NsdServiceInfo? = null
        val latch = CountDownLatch(1)

        // On déclare le listener ici pour qu'il soit accessible dans le 'finally'
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success: $service")
                if (service.serviceType.contains("_fbx-api._tcp")) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed: $errorCode")
                            latch.countDown()
                        }

                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                            Log.e(TAG, "Resolve Succeeded: $resolvedServiceInfo")
                            serviceInfo = resolvedServiceInfo
                            latch.countDown()
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(TAG, "service lost: $service")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                latch.countDown()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                latch.countDown()
            }
        }

        try {
            nsdManager.discoverServices("_fbx-api._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            latch.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error during service discovery", e)
        } finally {
            Log.d(TAG, "Stopping service discovery (in finally block)")
            nsdManager.stopServiceDiscovery(discoveryListener)
        }

        return serviceInfo
    }

    suspend fun requestAuthorization(freeboxApiUrl: String): AuthorizeResponse {
        val request = AuthorizeRequest(
            app_id = "com.example.lanscanner",
            app_name = "LAN Scanner",
            app_version = "1.0.0",
            device_name = getDeviceName()
        )

        val response: HttpResponse = httpClient.post("$freeboxApiUrl/api/v4/login/authorize/") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        val authResponse: AuthorizeResponse = response.body()
        if (authResponse.success) {
            appToken = authResponse.result?.app_token
        }
        return authResponse
    }

    suspend fun trackAuthorizationProgress(freeboxApiUrl: String, trackId: Int): TrackAuthorizationProgressResponse {
        val response: HttpResponse = httpClient.get("$freeboxApiUrl/api/v4/login/authorize/$trackId")
        return response.body()
    }

    suspend fun openSession(freeboxApiUrl: String): Boolean {
        // 1. Get challenge
        val loginResponse: LoginResponse = httpClient.get("$freeboxApiUrl/api/v4/login/").body()
        if (!loginResponse.success) return false

        val challenge = loginResponse.result!!.challenge
        val password = hmacSha1(challenge, appToken!!)

        // 2. Send challenge response
        val sessionResponse: LoginResponse = httpClient.post("$freeboxApiUrl/api/v4/login/session/") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("app_id" to "com.example.lanscanner", "password" to password))
        }.body()

        if (sessionResponse.success) {
            sessionToken = sessionResponse.result?.session_token
        }
        return sessionResponse.success
    }

    suspend fun getLanDevices(freeboxApiUrl: String): List<DeviceInfo> {
        if (sessionToken == null) return emptyList()

        val response: LanDeviceResponse = httpClient.get("$freeboxApiUrl/api/v4/lan/browser/pub/") {
            header("X-Fbx-App-Auth", sessionToken)
        }.body()

        return response.result?.mapNotNull { device ->
            val activeIp = device.l3connectivities?.find { it.active }?.addr
            if (activeIp != null) {
                DeviceInfo(activeIp, device.primary_name)
            } else {
                null
            }
        } ?: emptyList()
    }

    private fun getDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL

        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model
        } else {
            "$manufacturer $model"
        }
    }
    private fun hmacSha1(value: String, key: String): String {
        val keySpec = SecretKeySpec(key.toByteArray(), "HmacSHA1")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(keySpec)
        val bytes = mac.doFinal(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun forgetAuthorization() {
        Log.d(TAG, "Forgetting stored authorization data")
        appToken = null
        sessionToken = null
    }

    override fun close() {
        Log.d(TAG, "Closing HttpClient")
        httpClient.close()
    }
}

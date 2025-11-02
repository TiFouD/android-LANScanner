package com.example.lanscanner

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lanscanner.ui.theme.LANScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sealed class representing the different authentication states with the Freebox.
 */
sealed class FreeboxAuthState {
    /**
     * Initial state, no action has been taken.
     */
    object Idle : FreeboxAuthState()
    /**
     * Freebox discovery is in progress.
     */
    object Discovering : FreeboxAuthState()
    /**
     * Authorization is pending on the Freebox.
     *
     * @param trackId The tracking ID for the authorization process.
     */
    data class Authorizing(val trackId: Int) : FreeboxAuthState()
    /**
     * Freebox is authorized and connected.
     */
    object Authorized : FreeboxAuthState()
    /**
     * An error occurred during the Freebox authentication process.
     *
     * @param message A description of the error.
     */
    data class Error(val message: String) : FreeboxAuthState()
}


/**
 * ViewModel for the main activity, handling network scanning and Freebox authentication logic.
 */
@Suppress("DEPRECATION")
class MainViewModel : ViewModel() {
    /**
     * The current authentication state with the Freebox.
     */
    var freeboxAuthState by mutableStateOf<FreeboxAuthState>(FreeboxAuthState.Idle)
    private var freeboxManager: FreeboxManager? = null
    private var networkScanner: NetworkScanner? = null
    private var freeboxApiUrl: String? = null
    /**
     * List of discovered network devices.
     */
    var discoveredDevices by mutableStateOf<List<DeviceInfo>>(emptyList())

    /**
     * Returns an instance of [FreeboxManager], creating it if it doesn't exist.
     *
     * @param context The application context.
     * @return The [FreeboxManager] instance.
     */
    private fun getManager(context: Context): FreeboxManager {
        if (freeboxManager == null) {
            freeboxManager = FreeboxManager(context.applicationContext)
        }
        return freeboxManager!!
    }

    /**
     * Returns an instance of [NetworkScanner], creating it if it doesn't exist.
     *
     * @param context The application context.
     * @return The [NetworkScanner] instance.
     */
    private fun getScanner(context : Context): NetworkScanner {
        if (networkScanner == null) {
            networkScanner = NetworkScanner(context.applicationContext)
        }
        return networkScanner!!
    }

    /**
     * Initiates the Freebox authentication process. If a Freebox is not found,
     * it falls back to a general network scan.
     *
     * @param context The application context.
     */
    fun startFreeboxAuth(context: Context) {
        val manager = getManager(context)
        val scanner = getScanner(context)

        viewModelScope.launch {
            freeboxAuthState = FreeboxAuthState.Discovering

            // Perform discovery on IO dispatcher
            /*val serviceInfo = withContext(Dispatchers.IO) {
                // Replace with actual discovery logic if available, currently null
                //null
            }*/
            val serviceInfo = manager.discoverFreebox()

            if (serviceInfo != null) {

                // Logs show that serviceInfo.port is 80 (HTTP)
                // and https_port=57461 (for REMOTE access, which causes "Connection refused" locally).
                // If HTTPS is available (https_available=1), the local port is
                // almost certainly the standard port 443.

                val host = serviceInfo.host.hostAddress

                if (host != null) {
                    // Use the standard HTTPS port
                    val httpsPort = 443
                    freeboxApiUrl = "https://$host:$httpsPort"
                    Log.i("MainViewModel", "Freebox found. Attempting HTTPS connection on standard port 443: $freeboxApiUrl")
                } else {
                    Log.e("MainViewModel", "Freebox found but host IP address is null.")
                    freeboxAuthState = FreeboxAuthState.Error("Hôte Freebox introuvable")
                    return@launch
                }

                if (manager.appToken != null) {
                    if (manager.openSession(freeboxApiUrl!!)) {
                        withContext(Dispatchers.Main) {
                            freeboxAuthState = FreeboxAuthState.Authorized
                            fetchDevices()
                        }
                    } else {
                        requestAuthorization(manager)
                    }
                } else {
                    requestAuthorization(manager)
                }
            } else {
                Log.w("MainViewModel", "Freebox not found. Starting general scan...")

                val genericDevices = withContext(Dispatchers.IO) {
                    scanner.startScan()

                }

                withContext(Dispatchers.Main) {
                    discoveredDevices = genericDevices
                    freeboxAuthState = FreeboxAuthState.Authorized
                }
            }
        }
    }

    /**
     * Requests authorization from the Freebox.
     *
     * @param manager The [FreeboxManager] instance.
     */
    private suspend fun requestAuthorization(manager: FreeboxManager) {
        val authResponse = manager.requestAuthorization(freeboxApiUrl!!)
        if (authResponse.success) {
            freeboxAuthState = FreeboxAuthState.Authorizing(authResponse.result!!.track_id)
            trackAuthorization(manager,authResponse.result.track_id)
        } else {
            freeboxAuthState = FreeboxAuthState.Error("Authentication failed")
        }
    }

    /**
     * Tracks the authorization progress with the Freebox until it's granted, denied, or times out.
     *
     * @param manager The [FreeboxManager] instance.
     * @param trackId The tracking ID for the authorization process.
     */
    private fun trackAuthorization(manager: FreeboxManager, trackId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            while (freeboxAuthState is FreeboxAuthState.Authorizing) {
                val progress = manager.trackAuthorizationProgress(freeboxApiUrl!!, trackId)
                if (progress.success && progress.result != null) {
                    when (progress.result.status) {
                        "granted" -> {
                            if (manager.openSession(freeboxApiUrl!!)) {
                                freeboxAuthState = FreeboxAuthState.Authorized
                                fetchDevices()
                            } else {
                                freeboxAuthState = FreeboxAuthState.Error("Failed to open session")
                            }
                        }
                        "timeout" -> {
                            freeboxAuthState = FreeboxAuthState.Error("Authorization timed out")
                        }
                        "denied" -> {
                            freeboxAuthState = FreeboxAuthState.Error("Authorization denied")
                        }
                        "pending" -> {

                        }
                    }
                } else {
                    freeboxAuthState = FreeboxAuthState.Error("Failed to track authentication process")
                }
                if (freeboxAuthState is FreeboxAuthState.Authorizing) {
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    /**
     * Fetches the list of LAN devices from the Freebox.
     * Requires an active session.
     */
    fun fetchDevices() {
        freeboxManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val devices = freeboxManager!!.getLanDevices(freeboxApiUrl!!)
            withContext(Dispatchers.Main) {
                discoveredDevices = devices
            }
        }
    }

    /**
     * Clears the stored Freebox authorization and resets the UI state.
     *
     * @param context The application context.
     */
    fun forgetFreebox(context: Context) {
        val manager = getManager(context)
        viewModelScope.launch {
            manager.forgetAuthorization()
            freeboxAuthState = FreeboxAuthState.Idle
            discoveredDevices = emptyList()
        }
    }

    /**
     * Closes the FreeboxManager when the ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        freeboxManager?.close()
    }
}


/**
 * Main activity of the application. It sets up the user interface content
 * using Jetpack Compose and displays the [NetworkScannerScreen].
 */
class MainActivity : ComponentActivity() {
    /**
     * Called when the activity is first created.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data it most recently
     *                           supplied in [onSaveInstanceState].  **Note: Otherwise it is null.**
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceIconMapper.init(applicationContext)
        setContent {
            LANScannerTheme {
                NetworkScannerScreen()
            }
        }
    }
}

/**
 * Main network scanner screen, displaying the Freebox connection status
 * and the list of discovered devices.
 *
 * @param viewModel The [MainViewModel] which handles the state and interaction logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScannerScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Wifi") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options"
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Oublier la Freebox") }, // "Forget Freebox"
                                onClick = {
                                    viewModel.forgetFreebox(context)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val freeboxState = viewModel.freeboxAuthState
            when (freeboxState) {
                is FreeboxAuthState.Idle -> {
                    Button(onClick = { viewModel.startFreeboxAuth(context) }) {
                        Text("Connexion au réseau") // "Connect to network"
                    }
                }
                is FreeboxAuthState.Discovering -> {
                    CircularProgressIndicator()
                    Text("Scan du réseau en cours...") // "Network scan in progress..."
                }
                is FreeboxAuthState.Authorizing -> {
                    CircularProgressIndicator()
                    Text("Veuillez autoriser la demande sur votre Freebox") // "Please authorize the request on your Freebox"
                }
                is FreeboxAuthState.Authorized -> {
                    Text("Scan terminé. ${viewModel.discoveredDevices.size} appareils trouvés.") // "Scan complete. ${viewModel.discoveredDevices.size} devices found."
                    Button(onClick = { viewModel.startFreeboxAuth(context) }) {
                        Text("Relancer un scan") // "Relaunch scan"
                    }
                }
                is FreeboxAuthState.Error -> {
                    Text(freeboxState.message)
                    Button(onClick = { viewModel.startFreeboxAuth(context) }) {
                        Text("Rééssayer") // "Retry"
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(viewModel.discoveredDevices) { device ->
                    DeviceItem(device)
                }
            }
        }
    }
}

/**
 * A composable that displays a single discovered device item.
 *
 * @param device The device information to display.
 */
@Composable
fun DeviceItem(device: DeviceInfo) {
    // Retrieves the icon AND the manufacturer name
    val icon = DeviceIconMapper.getIconForDevice(device.mac)
    val vendorName = DeviceIconMapper.getVendorName(device.mac)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp) // Adjusted padding
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Icone de l'appareil", // "Device icon"
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 16.dp)
                )

                // Column for title + manufacturer
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.hostname.ifBlank { "Appareil inconnu" }, // "Unknown device"
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = vendorName, // Displays the manufacturer name
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "IP: ${device.ip}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "MAC: ${device.mac ?: "N/A"}", // Handles null MAC
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
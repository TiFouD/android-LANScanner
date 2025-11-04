package com.example.lanscanner

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lanscanner.database.AppDatabase
import com.example.lanscanner.database.DeviceEntity
import com.example.lanscanner.database.DeviceRepository
import com.example.lanscanner.ui.theme.LANScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
class MainViewModel(application: Application) : AndroidViewModel(application) {

    var freeboxAuthState by mutableStateOf<FreeboxAuthState>(FreeboxAuthState.Idle)
    private var freeboxManager: FreeboxManager? = null
    private var networkScanner: NetworkScanner? = null
    private var freeboxApiUrl: String? = null

    private val repository: DeviceRepository =
        DeviceRepository(AppDatabase.getInstance(application).deviceDao())

    // Source 1: Database (for Freebox / History)
    private val historicalDevicesFlow = repository.allDevices
        .map { dbList ->
            // Converts the list of DB entities (DeviceEntity) to a list of UI objects (UiDevice)
            dbList
                .filter { it.ip.contains(".") } // Filter out IPv6 addresses
                .map { dbDevice ->
                    UiDevice(
                        ip = dbDevice.ip,
                        hostname = dbDevice.hostname,
                        mac = dbDevice.mac,
                        isOnline = dbDevice.isOnline
                    )
                }
                .sortedBy { device -> // Sort the list by IP address.
                    device.ip.split(".").map { it.toInt() }
                        .let { it[0] * 1_000_000 + it[1] * 1_000 + it[2] * 100 + it[3] }
                }
        }

    // Source 2: Generic Scan (Fallback)
    private val _genericScanDevices = MutableStateFlow<List<UiDevice>>(emptyList())

    // The "Selector" between historical and generic scan data
    private val _isGenericScanActive = MutableStateFlow(false)

    /**
     * The final list of devices to be displayed on the UI.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val displayDevices: StateFlow<List<UiDevice>> =
        _isGenericScanActive.flatMapLatest { isGeneric ->
            if (isGeneric) {
                _genericScanDevices
            } else {
                historicalDevicesFlow
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    /**
     * Gets a singleton instance of [FreeboxManager].
     *
     * @param context The application context.
     * @return The singleton [FreeboxManager] instance.
     */
    private fun getManager(context: Context): FreeboxManager {
        if (freeboxManager == null) {
            freeboxManager = FreeboxManager(context.applicationContext)
        }
        return freeboxManager!!
    }

    /**
     * Gets a singleton instance of [NetworkScanner].
     *
     * @param context The application context.
     * @return The singleton [NetworkScanner] instance.
     */
    private fun getScanner(context: Context): NetworkScanner {
        if (networkScanner == null) {
            networkScanner = NetworkScanner(context.applicationContext)
        }
        return networkScanner!!
    }

    /**
     * Initiates the Freebox authentication process.
     */
    fun startFreeboxAuth(context: Context) {
        val manager = getManager(context)
        val scanner = getScanner(context)

        viewModelScope.launch {
            freeboxAuthState = FreeboxAuthState.Discovering

            val serviceInfo = manager.discoverFreebox()

            if (serviceInfo != null) {
                // Case 1: Freebox found on the network
                _isGenericScanActive.value = false

                val host = serviceInfo.host.hostAddress
                if (host != null) {
                    val httpsPort = 443
                    freeboxApiUrl = "https://$host:$httpsPort"
                    Log.i(
                        "MainViewModel",
                        "Freebox found. Attempting HTTPS connection on standard port 443: $freeboxApiUrl"
                    )
                } else {
                    Log.e("MainViewModel", "Freebox found but host IP address is null.")
                    freeboxAuthState = FreeboxAuthState.Error("Hôte Freebox introuvable")
                    return@launch
                }

                if (manager.appToken != null) {
                    if (manager.openSession(freeboxApiUrl!!)) {
                        withContext(Dispatchers.Main) {
                            freeboxAuthState = FreeboxAuthState.Authorized
                            fetchDevices() // Update the database
                        }
                    } else {
                        requestAuthorization(manager)
                    }
                } else {
                    requestAuthorization(manager)
                }
            } else {
                // Case 2: Freebox not found, start a generic scan
                Log.w("MainViewModel", "Freebox not found. Starting general scan...")

                // 1. Activate generic scan mode
                _isGenericScanActive.value = true

                // 2. Start the scan
                val genericDevices = withContext(Dispatchers.IO) {
                    scanner.startScan()
                }

                // 3. Update the generic scan flow
                _genericScanDevices.value = genericDevices
                    .filter { it.ip.contains(".") } // Filter out IPv6 addresses
                    .map {
                        // All devices are "online" because this is a live scan
                        UiDevice(it.ip, it.hostname, it.mac, true)
                    }
                    .sortedBy { device -> // Sort for consistency
                        device.ip.split(".").map { it.toInt() }
                            .let { it[0] * 1_000_000 + it[1] * 1_000 + it[2] * 100 + it[3] }
                    }

                // 4. Update the UI state
                withContext(Dispatchers.Main) {
                    freeboxAuthState = FreeboxAuthState.Authorized
                }
            }
        }
    }

    /**
     * Requests authorization from the Freebox.
     *
     * @param manager The [FreeboxManager] instance to use.
     */
    private suspend fun requestAuthorization(manager: FreeboxManager) {
        val authResponse = manager.requestAuthorization(freeboxApiUrl!!)
        if (authResponse.success) {
            freeboxAuthState = FreeboxAuthState.Authorizing(authResponse.result!!.track_id)
            trackAuthorization(manager, authResponse.result.track_id)
        } else {
            freeboxAuthState = FreeboxAuthState.Error("Authentication failed")
        }
    }

    /**
     * Tracks the authorization progress with the Freebox.
     *
     * @param manager The [FreeboxManager] instance to use.
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
                            // Do nothing, just wait
                        }
                    }
                } else {
                    freeboxAuthState =
                        FreeboxAuthState.Error("Failed to track authentication process")
                }
                if (freeboxAuthState is FreeboxAuthState.Authorizing) {
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    /**
     * Fetches the list of LAN devices from the Freebox. (Freebox/History Mode)
     */
    fun fetchDevices() {
        _isGenericScanActive.value = false

        freeboxManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.setAllDevicesOffline()
            val liveDevices = freeboxManager!!.getLanDevices(freeboxApiUrl!!)
            val timestamp = System.currentTimeMillis()

            for (device in liveDevices) {
                if (device.mac != null) {
                    repository.upsertDevice(
                        DeviceEntity(
                            mac = device.mac,
                            ip = device.ip,
                            hostname = device.hostname.ifBlank { "Appareil inconnu" },
                            lastSeen = timestamp,
                            isOnline = true
                        )
                    )
                }
            }
        }
    }

    /**
     * Clears the stored Freebox authorization and resets the UI state.
     */
    fun forgetFreebox(context: Context) {
        val manager = getManager(context)
        viewModelScope.launch {
            manager.forgetAuthorization()
            repository.clearAllDevices() // Clear the database
            _isGenericScanActive.value = false // Revert to history mode (which will be empty)
            freeboxAuthState = FreeboxAuthState.Idle
        }
    }

    /**
     * Forces a new network scan.
     */
    fun rescanNetwork(context: Context) {
        freeboxManager?.close()
        freeboxManager = null
        _genericScanDevices.value = emptyList()

        startFreeboxAuth(context)
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

    val devices by viewModel.displayDevices.collectAsState()

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
                                text = { Text("Oublier la Freebox") },
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
                        Text("Connexion au réseau")
                    }
                }

                is FreeboxAuthState.Discovering -> {
                    CircularProgressIndicator()
                    Text("Scan du réseau en cours...")
                }

                is FreeboxAuthState.Authorizing -> {
                    CircularProgressIndicator()
                    Text("Veuillez autoriser la demande sur votre Freebox")
                }

                is FreeboxAuthState.Authorized -> {
                    Text("Scan terminé. ${devices.size} appareils trouvés.")
                    Button(onClick = { viewModel.rescanNetwork(context) }) {
                        Text("Relancer un scan")
                    }
                }

                is FreeboxAuthState.Error -> {
                    Text(freeboxState.message)
                    Button(onClick = { viewModel.startFreeboxAuth(context) }) {
                        Text("Rééssayer")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices, key = { it.mac ?: it.ip }) { device ->
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
fun DeviceItem(device: UiDevice) {
    val icon = DeviceIconMapper.getIconForDevice(device.mac)
    val vendorName = DeviceIconMapper.getVendorName(device.mac)

    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(300))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (device.isOnline) Color.Green else Color.Red,
                            shape = CircleShape
                        )
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = icon,
                    contentDescription = "Icone de l'appareil",
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 16.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.hostname.ifBlank { "Appareil inconnu" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = vendorName,
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
                    text = "MAC: ${device.mac ?: "N/A"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(0))

            ) {
                PowerUserActions(device = device)
            }
        }
    }
}

@Composable
fun PowerUserActions(device: UiDevice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )

        Text(
            text = "Actions avancées",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // TODO: Implement port scanning
            TextButton(onClick = { /* port scanning logic */ }) {
                Text("Scanner les ports")
            }

            // TODO: Implement Wake-on-LAN
            TextButton(onClick = { /* wake on lan logic */ }, enabled = device.mac != null) {
                Text("Wake-on-LAN")
            }

        }
    }
}

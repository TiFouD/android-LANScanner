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
import androidx.compose.material3.HorizontalDivider
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
                freeboxApiUrl = "http://${serviceInfo.host.hostAddress}:${serviceInfo.port}"
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
            freeboxAuthState = FreeboxAuthState.Error("Échec de l'authentification")
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
                                freeboxAuthState = FreeboxAuthState.Error("Impossible d'ouvrir la session")
                            }
                        }
                        "timeout" -> {
                            freeboxAuthState = FreeboxAuthState.Error("Délai d'autorisation expiré")
                        }
                        "denied" -> {
                            freeboxAuthState = FreeboxAuthState.Error("Autorisation refusée")
                        }
                        "pending" -> {

                        }
                    }
                } else {
                    freeboxAuthState = FreeboxAuthState.Error("Impossible de suivre le processus d'authentification")
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
            discoveredDevices = freeboxManager!!.getLanDevices(freeboxApiUrl!!)
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
 * Activité principale de l'application. Elle configure le contenu de l'interface utilisateur
 * en utilisant Jetpack Compose et affiche le [NetworkScannerScreen].
 */
class MainActivity : ComponentActivity() {
    /**
     * Appelé lorsque l'activité est créée pour la première fois.
     *
     * @param savedInstanceState Si l'activité est recréée après avoir été précédemment détruite,
     *                           il s'agit du Bundle que l'activité a fourni pour l'enregistrer son état.
     *                           Sinon, c'est null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LANScannerTheme {
                NetworkScannerScreen()
            }
        }
    }
}

/**
 * Écran principal du scanner de réseau, affichant l'état de la connexion Freebox
 * et la liste des appareils découverts.
 *
 * @param viewModel Le [MainViewModel] qui gère la logique d'état et d'interaction.
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
                    Text("Connecté à la Freebox")
                    Button(onClick = { viewModel.fetchDevices() }) {
                        Text("Rafraichir les appareils")
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
                items(viewModel.discoveredDevices) { device ->
                    DeviceItem(device)
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Un composable qui affiche un seul élément d'appareil découvert.
 *
 * @param device Les informations sur l'appareil à afficher.
 */
@Composable
fun DeviceItem(device: DeviceInfo) {
    val icon = DeviceIconMapper.getIconForDevice(device.mac)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
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
                    contentDescription = "Icone de l'appareil",
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 16.dp)
                )
                Text(
                    text = device.hostname.ifBlank { "Appareil inconnu" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
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
        }
    }
}
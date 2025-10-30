package com.example.lanscanner

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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

sealed class FreeboxAuthState {
    object Idle : FreeboxAuthState()
    object Discovering : FreeboxAuthState()
    data class Authorizing(val trackId: Int) : FreeboxAuthState()
    object Authorized : FreeboxAuthState()
    data class Error(val message: String) : FreeboxAuthState()
}


class MainViewModel : ViewModel() {
    var freeboxAuthState by mutableStateOf<FreeboxAuthState>(FreeboxAuthState.Idle)
    private var freeboxManager: FreeboxManager? = null
    private var freeboxApiUrl: String? = null
    var discoveredDevices by mutableStateOf<List<DeviceInfo>>(emptyList())

    private fun getManager(context: Context): FreeboxManager {
        if (freeboxManager == null) {
            freeboxManager = FreeboxManager(context.applicationContext)
        }
        return freeboxManager!!
    }

    fun startFreeboxAuth(context: Context) {
        val manager = getManager(context)

        viewModelScope.launch {
            freeboxAuthState = FreeboxAuthState.Discovering
            // Switch to the IO dispatcher for network operations
            withContext(Dispatchers.IO) {
                val serviceInfo = manager.discoverFreebox()
                if (serviceInfo != null) {
                    freeboxApiUrl = "http://${serviceInfo.host.hostAddress}:${serviceInfo.port}"
                    if (manager.appToken != null) {
                        // We already have a token, try to open a session directly
                        if (manager.openSession(freeboxApiUrl!!)) {
                            freeboxAuthState = FreeboxAuthState.Authorized
                            fetchDevices()
                        } else {
                            // Token might be invalid, re-authorize
                            requestAuthorization(manager)
                        }
                    } else {
                        requestAuthorization(manager)
                    }
                } else {
                    freeboxAuthState = FreeboxAuthState.Error("Impossible de trouver la Freebox")
                }
            }
        }
    }

    private suspend fun requestAuthorization(manager: FreeboxManager) {
        val authResponse = manager.requestAuthorization(freeboxApiUrl!!)
        if (authResponse.success) {
            freeboxAuthState = FreeboxAuthState.Authorizing(authResponse.result!!.track_id)
            trackAuthorization(manager,authResponse.result.track_id)
        } else {
            freeboxAuthState = FreeboxAuthState.Error("Échec de l'authentification")
        }
    }

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

    fun fetchDevices() {
        freeboxManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            discoveredDevices = freeboxManager!!.getLanDevices(freeboxApiUrl!!)
        }
    }

    fun forgetFreebox(context: Context) {
        val manager = getManager(context)
        viewModelScope.launch {
            manager.forgetAuthorization()
            freeboxAuthState = FreeboxAuthState.Idle
            discoveredDevices = emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        freeboxManager?.close()
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LANScannerTheme {
                NetworkScannerScreen()
            }
        }
    }
}

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
                                imageVector = Icons.Default.MoreVert, // <- Ceci vient de l'import
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

@Composable
fun DeviceItem(device: DeviceInfo) {
    ListItem(
        headlineContent = { Text(device.hostname) },
        supportingContent = { Text(device.ip) }
    )
}

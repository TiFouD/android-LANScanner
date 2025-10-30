package com.example.lanscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lanscanner.ui.theme.LANScannerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Material 3 theme (defined in ui.theme/Theme.kt)
            LANScannerTheme {
                NetworkScannerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScannerScreen() {
    // State for the list of devices. When this list changes,
    // the UI (LazyColumn) will update automatically.
    var discoveredDevices by remember { mutableStateOf(listOf<DeviceInfo>()) }

    // State to know if a scan is in progress.
    var isScanning by remember { mutableStateOf(false) }

    // 'coroutineScope' is necessary to launch Coroutines
    // from a click event.
    val coroutineScope = rememberCoroutineScope()

    // Application context (necessary to access system services).
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Network Scanner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
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

            // The scan button.
            Button(
                onClick = {
                    // On click: launches a coroutine.
                    coroutineScope.launch {
                        isScanning = true
                        discoveredDevices = listOf() // Clear the list.

                        // Calls the scan function (which must run
                        // in the background).
                        val scanner = NetworkScanner(context)
                        val devices = scanner.startScan()

                        discoveredDevices = devices
                        isScanning = false
                    }
                },
                // Disables the button if the scan is in progress.
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isScanning) "Scanning..." else "Start scan")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Displays a loading indicator.
            if (isScanning) {
                CircularProgressIndicator()
            }

            // The results list.
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(discoveredDevices) { device ->
                    DeviceItem(device)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: DeviceInfo) {
    // A simple item for the list.
    ListItem(
        headlineContent = { Text(device.hostname) },
        supportingContent = { Text(device.ip) }
    )
}

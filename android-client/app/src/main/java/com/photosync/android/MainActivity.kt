package com.photosync.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.photosync.android.data.SettingsManager
import com.photosync.android.service.SyncService
import com.photosync.android.ui.theme.PicsTheme

class MainActivity : ComponentActivity() {
    private lateinit var settings: SettingsManager
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied - cannot sync photos", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)
        
        setContent {
            PicsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        settings = settings,
                        onStartSync = { startSyncService() },
                        onRequestPermission = { requestStoragePermission() }
                    )
                }
            }
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
            }
            else -> {
                permissionLauncher.launch(permission)
            }
        }
    }

    private fun startSyncService() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please grant storage permission first", Toast.LENGTH_LONG).show()
            return
        }
        
        val intent = Intent(this, SyncService::class.java).apply {
            putExtra("server_ip", settings.serverIp)
            putExtra("server_port", settings.serverPort)
        }
        startService(intent)
        Toast.makeText(this, "Sync started...", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsScreen(
    settings: SettingsManager,
    onStartSync: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var serverIp by remember { mutableStateOf(settings.serverIp) }
    var serverPort by remember { mutableStateOf(settings.serverPort.toString()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Auto-start connection service if settings are valid
    LaunchedEffect(Unit) {
        if (settings.serverIp.isNotBlank()) {
            val intent = Intent(context, com.photosync.android.service.ConnectionService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PhotoSync Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = serverIp,
            onValueChange = { serverIp = it },
            label = { Text("Server IP Address") },
            placeholder = { Text("192.168.0.10") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        OutlinedTextField(
            value = serverPort,
            onValueChange = { serverPort = it },
            label = { Text("Server Port") },
            placeholder = { Text("50505") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Button(
            onClick = {
                val port = serverPort.toIntOrNull()
                if (serverIp.isNotBlank() && port != null && port in 1..65535) {
                    settings.serverIp = serverIp
                    settings.serverPort = port
                    
                    // Restart connection service with new settings
                    val intent = Intent(context, com.photosync.android.service.ConnectionService::class.java)
                    context.stopService(intent)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    Toast.makeText(context, "Settings saved & reconnecting...", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Connect")
        }
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("Request Storage Permission")
        }
        
        Button(
            onClick = onStartSync,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Start Sync")
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Current Settings:\nIP: ${settings.serverIp}\nPort: ${settings.serverPort}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
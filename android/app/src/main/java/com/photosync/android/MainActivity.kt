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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.photosync.android.ui.navigation.PhotoSyncNavigation
import com.photosync.android.ui.theme.PicsTheme

class MainActivity : ComponentActivity() {
    
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
        
        setContent {
            PicsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val settingsManager = com.photosync.android.data.SettingsManager(applicationContext)
                    val startDestination = if (settingsManager.isOnboardingCompleted) {
                        startServerMonitoring()
                        com.photosync.android.ui.navigation.Screen.Home.route
                    } else {
                        "permissions"
                    }
                    PhotoSyncNavigation(
                        startDestination = startDestination,
                        onStartSync = { startServerMonitoring() }
                    )
                }
            }
        }
    }

    private fun startServerMonitoring() {
        val intent = Intent(this, com.photosync.android.service.EnhancedSyncService::class.java)
        intent.action = com.photosync.android.service.EnhancedSyncService.ACTION_START_MONITORING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
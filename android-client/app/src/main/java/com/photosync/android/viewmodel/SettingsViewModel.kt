package com.photosync.android.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.photosync.android.data.SettingsManager
import com.photosync.android.service.ConnectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsManager(application)
    private val context = application
    
    private val _serverIp = MutableStateFlow(settings.serverIp)
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()
    
    private val _serverPort = MutableStateFlow(settings.serverPort.toString())
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()
    
    fun updateServerIp(ip: String) {
        _serverIp.value = ip
    }
    
    fun updateServerPort(port: String) {
        _serverPort.value = port
    }
    
    fun saveSettings(): Boolean {
        val port = _serverPort.value.toIntOrNull()
        if (_serverIp.value.isBlank() || port == null || port !in 1..65535) {
            return false
        }
        
        settings.serverIp = _serverIp.value
        settings.serverPort = port
        
        // Restart connection service with new settings
        restartConnectionService()
        
        return true
    }
    
    private fun restartConnectionService() {
        val intent = Intent(context, ConnectionService::class.java)
        context.stopService(intent)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

package com.photosync.android.viewmodel

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photosync.android.data.AppDatabase
import com.photosync.android.data.SettingsManager
import com.photosync.android.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsManager(application)
    private val context = application
    private val database = AppDatabase.getDatabase(application)
    private val mediaRepository = MediaRepository(application.contentResolver, database)
    
    private val _serverIp = MutableStateFlow(settings.serverIp)
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()
    
    private val _serverPort = MutableStateFlow(settings.serverPort.toString())
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()

    private val _userName = MutableStateFlow(settings.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()
    
    fun updateServerIp(ip: String) {
        _serverIp.value = ip
    }
    
    fun updateServerPort(port: String) {
        _serverPort.value = port
    }

    fun updateUserName(name: String) {
        _userName.value = name
        // Save immediately to preferences
        settings.userName = name
    }
    
    fun saveSettings(): Boolean {
        val port = _serverPort.value.toIntOrNull()
        if (_serverIp.value.isBlank() || port == null || port !in 1..65535) {
            return false
        }
        
        settings.serverIp = _serverIp.value
        settings.serverPort = port
        settings.userName = _userName.value
        
        // Restart connection service with new settings
        restartSyncService()
        
        return true
    }
    
    private fun restartSyncService() {
        val intent = Intent(context, com.photosync.android.service.EnhancedSyncService::class.java).apply {
            action = com.photosync.android.service.EnhancedSyncService.ACTION_START_SYNC
        }
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
    
    fun resetSyncHistory() {
        viewModelScope.launch {
            database.syncStatusDao().clearAll()
            refreshDebugInfo()
        }
    }
    
    // Debug Info
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()
    
    private val _totalPhotos = MutableStateFlow(0)
    val totalPhotos: StateFlow<Int> = _totalPhotos.asStateFlow()
    
    private val _pendingPhotos = MutableStateFlow(0)
    val pendingPhotos: StateFlow<Int> = _pendingPhotos.asStateFlow()
    
    init {
        refreshDebugInfo()
    }
    
    fun refreshDebugInfo() {
        viewModelScope.launch {
            val syncedCount = mediaRepository.getSyncedCount().first()
            val pendingCount = mediaRepository.getPendingCount().first()
            
            _totalPhotos.value = syncedCount + pendingCount
            _pendingPhotos.value = pendingCount
            
            // Get last sync time from server config
            val serverConfig = database.serverConfigDao().getServerConfig()
            _lastSyncTime.value = serverConfig?.lastConnected ?: 0L
        }
    }
}

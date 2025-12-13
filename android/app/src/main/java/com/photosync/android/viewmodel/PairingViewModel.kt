package com.photosync.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.provider.Settings
import com.photosync.android.data.AppDatabase
import com.photosync.android.data.SettingsManager
import com.photosync.android.data.entity.ServerConfigEntity
import com.photosync.android.network.UdpDiscoveryListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val serverConfigDao = database.serverConfigDao()
    
    private val settingsManager = SettingsManager(application)
    
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    private val _discoveredServers = MutableStateFlow<List<UdpDiscoveryListener.ServerInfo>>(emptyList())
    val discoveredServers: StateFlow<List<UdpDiscoveryListener.ServerInfo>> = _discoveredServers.asStateFlow()
    
    sealed class PairingState {
        object Idle : PairingState()
        object Discovering : PairingState()
        object Connecting : PairingState()
        object Success : PairingState()
        data class Error(val message: String) : PairingState()
    }
    
    fun pairWithServer(serverIp: String, token: String = "") {
        viewModelScope.launch {
            try {
                _pairingState.value = PairingState.Connecting
                
                // 1. Validate connection and token
                val client = com.photosync.android.network.TcpSyncClient(serverIp)
                if (!client.connect()) {
                    _pairingState.value = PairingState.Error("Could not connect to server")
                    return@launch
                }
                
                val deviceId = Settings.Secure.getString(
                    getApplication<Application>().contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                
                // 2. Try to start session with token
                val sessionId = client.startSession(deviceId, token)
                client.disconnect()
                
                if (sessionId == null) {
                    _pairingState.value = PairingState.Error("Authentication failed. Check pairing code.")
                    return@launch
                }
                
                // 3. Save server configuration if successful
                val config = ServerConfigEntity(
                    serverIp = serverIp,
                    deviceId = deviceId,
                    isPaired = true,
                    lastConnected = System.currentTimeMillis()
                )
                
                withContext(Dispatchers.IO) {
                    serverConfigDao.insertServerConfig(config)
                }
                
                // Update SettingsManager for SyncService
                settingsManager.serverIp = serverIp
                settingsManager.serverPort = 50505 // Default or discovered
                
                _pairingState.value = PairingState.Success
            } catch (e: Exception) {
                _pairingState.value = PairingState.Error(e.message ?: "Pairing failed")
            }
        }
    }
    
    fun discoverServer() {
        viewModelScope.launch {
            try {
                _pairingState.value = PairingState.Discovering
                _discoveredServers.value = emptyList()
                
                val discovery = UdpDiscoveryListener()
                val serverInfo = discovery.discoverServer()
                
                if (serverInfo != null) {
                    _discoveredServers.value = listOf(serverInfo)
                    _pairingState.value = PairingState.Idle // Discovery done
                } else {
                    _pairingState.value = PairingState.Error("No server found")
                }
            } catch (e: Exception) {
                _pairingState.value = PairingState.Error(e.message ?: "Discovery failed")
            }
        }
    }
}

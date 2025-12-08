package com.photosync.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.provider.Settings
import com.photosync.android.data.AppDatabase
import com.photosync.android.data.entity.ServerConfigEntity
import com.photosync.android.network.UdpDiscoveryListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val serverConfigDao = database.serverConfigDao()
    
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    sealed class PairingState {
        object Idle : PairingState()
        object Discovering : PairingState()
        object Connecting : PairingState()
        object Success : PairingState()
        data class Error(val message: String) : PairingState()
    }
    
    fun pairWithServer(serverIp: String) {
        viewModelScope.launch {
            try {
                _pairingState.value = PairingState.Connecting
                
                val deviceId = Settings.Secure.getString(
                    getApplication<Application>().contentResolver,
                    Settings.Secure.ANDROID_ID
                )
                
                // Save server configuration
                val config = ServerConfigEntity(
                    serverIp = serverIp,
                    deviceId = deviceId,
                    isPaired = true,
                    lastConnected = System.currentTimeMillis()
                )
                
                serverConfigDao.insertServerConfig(config)
                
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
                
                val discovery = UdpDiscoveryListener()
                val serverInfo = discovery.discoverServer()
                
                if (serverInfo != null) {
                    pairWithServer(serverInfo.ip)
                } else {
                    _pairingState.value = PairingState.Error("No server found")
                }
            } catch (e: Exception) {
                _pairingState.value = PairingState.Error(e.message ?: "Discovery failed")
            }
        }
    }
}

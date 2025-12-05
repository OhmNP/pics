package com.photosync.android.data

import com.photosync.android.model.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

data class ConnectionInfo(
    val socket: Socket,
    val reader: BufferedReader,
    val writer: BufferedWriter
)

class ConnectionManager private constructor() {
    private var connection: ConnectionInfo? = null
    private val lock = Any()
    
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    companion object {
        @Volatile
        private var instance: ConnectionManager? = null
        
        fun getInstance(): ConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: ConnectionManager().also { instance = it }
            }
        }
    }
    
    fun setConnection(socket: Socket, reader: BufferedReader, writer: BufferedWriter) {
        synchronized(lock) {
            connection = ConnectionInfo(socket, reader, writer)
            _connectionStatus.value = ConnectionStatus.Connected
        }
    }
    
    fun getConnection(): ConnectionInfo? {
        synchronized(lock) {
            return connection
        }
    }
    
    fun clearConnection() {
        synchronized(lock) {
            connection = null
            _connectionStatus.value = ConnectionStatus.Disconnected
        }
    }
    
    fun setConnecting() {
        _connectionStatus.value = ConnectionStatus.Connecting
    }
    
    fun setError(message: String) {
        _connectionStatus.value = ConnectionStatus.Error(message)
    }
    
    fun isConnected(): Boolean {
        synchronized(lock) {
            return connection?.socket?.isConnected == true && 
                   connection?.socket?.isClosed == false
        }
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun setSyncing(syncing: Boolean) {
        _isSyncing.value = syncing
    }
}

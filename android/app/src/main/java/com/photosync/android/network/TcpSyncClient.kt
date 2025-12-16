package com.photosync.android.network

import android.util.Log
import com.photosync.android.model.ConnectionStatus
import com.photosync.android.model.SyncProgress
import com.photosync.android.network.protocol.NetworkPacket
import com.photosync.android.network.protocol.PacketType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class TcpSyncClient(
    private val serverIp: String,
    private val serverPort: Int = 50505
) {
    private var socket: Socket? = null
    private var inputStream: DataInputStream? = null
    private var outputStream: DataOutputStream? = null

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _syncProgress = MutableStateFlow<SyncProgress>(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress

    companion object {
        private const val TAG = "TcpSyncClient"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val HEARTBEAT_INTERVAL_MS = 10000L
    }
    
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            _connectionStatus.value = ConnectionStatus.Connecting
            Log.d(TAG, "Connecting to $serverIp:$serverPort")
            
            socket = Socket()
            socket?.tcpNoDelay = true
            socket?.connect(InetSocketAddress(serverIp, serverPort), CONNECT_TIMEOUT_MS)
            
            val bufferSize = 1024 * 1024 // 1MB buffer
            inputStream = DataInputStream(BufferedInputStream(socket!!.getInputStream(), bufferSize))
            outputStream = DataOutputStream(BufferedOutputStream(socket!!.getOutputStream(), bufferSize))
            
            _connectionStatus.value = ConnectionStatus.Connected
            Log.i(TAG, "Connected to server")
            
            // Start periodic heartbeat
            startHeartbeatLoop()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            _connectionStatus.value = ConnectionStatus.Error(e.message ?: "Connection failed")
            disconnect()
            false
        }
    }

    fun disconnect() {
        try {
            stopHeartbeatLoop()
            socket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        } finally {
            socket = null
            inputStream = null
            outputStream = null
            _connectionStatus.value = ConnectionStatus.Disconnected
        }
    }

    suspend fun disconnectSuspend() = withContext(Dispatchers.IO) {
        disconnect()
    }
    
    private fun startHeartbeatLoop() {
        stopHeartbeatLoop()
        heartbeatJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                sendHeartbeat()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun sendHeartbeat() {
        try {
            val packet = NetworkPacket.create(PacketType.HEARTBEAT, null)
            val out = outputStream ?: return
            out.write(packet.toBytes())
            out.flush()
            Log.d(TAG, "Sent Heartbeat")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send heartbeat", e)
            disconnect()
        }
    }
    
    // Send a packet and wait for a response packet
    private suspend fun sendRequest(packet: NetworkPacket): NetworkPacket? = withContext(Dispatchers.IO) {
        try {
            val out = outputStream ?: throw java.io.IOException("Not connected")
            val `in` = inputStream ?: throw java.io.IOException("Not connected")
            
            // Write packet
            val bytes = packet.toBytes()
            out.write(bytes)
            out.flush()
            
            // Read response header
            val headerBytes = ByteArray(NetworkPacket.HEADER_SIZE)
            `in`.readFully(headerBytes)
            
            val (magic, version, type, length) = NetworkPacket.parseHeader(headerBytes)
            
            // Read payload
            val payloadBytes = ByteArray(length)
            if (length > 0) {
                `in`.readFully(payloadBytes)
            }
            
            NetworkPacket(
                com.photosync.android.network.protocol.PacketHeader(
                    type = type,
                    payloadLength = length
                ),
                payload = payloadBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendRequest: ${e.message}")
            disconnect()
            throw e // Rethrow to let caller handle or crash
        }
    }

    suspend fun batchCheck(hashes: List<String>): List<String> = withContext(Dispatchers.IO) {
        if (hashes.isEmpty()) return@withContext emptyList()
        
        try {
            // Prepare payload
            val json = JSONObject()
            json.put("action", "BATCH_CHECK")
            json.put("hashes", org.json.JSONArray(hashes))
            
            val packet = NetworkPacket.create(PacketType.METADATA, json)
            val response = sendRequest(packet)
            
            if (response != null && response.header.type == PacketType.METADATA) {
                val responseJson = response.getJsonPayload()
                if (responseJson?.optString("status") == "ok") {
                    val existingHashes = responseJson.optJSONArray("existingHashes")
                    val result = mutableListOf<String>()
                    if (existingHashes != null) {
                        for (i in 0 until existingHashes.length()) {
                            result.add(existingHashes.getString(i))
                        }
                    }
                    return@withContext result
                }
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking hashes", e)
            emptyList()
        }
    }
    
    suspend fun startSession(deviceId: String, token: String = "", userName: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("deviceId", deviceId)
            json.put("token", token)
            json.put("userName", userName)
            
            val packet = NetworkPacket.create(PacketType.PAIRING_REQUEST, json)
            val response = sendRequest(packet)
            
            if (response != null && response.header.type == PacketType.PAIRING_RESPONSE) {
                val responseJson = response.getJsonPayload()
                if (responseJson?.optBoolean("success") == true) {
                    val sessionId = responseJson.optInt("sessionId", -1)
                    if (sessionId != -1) return@withContext sessionId
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error starting session", e)
            null
        }
    }
    
    suspend fun sendPhotoMetadata(filename: String, size: Long, hash: String): Boolean = withContext(Dispatchers.IO) {
        // Do NOT catch exceptions here, let them propagate to stop sync loop on error
        val json = JSONObject()
        json.put("filename", filename)
        json.put("size", size)
        json.put("hash", hash)
        
        val packet = NetworkPacket.create(PacketType.METADATA, json)
        val response = sendRequest(packet)
        
        // Expect TRANSFER_READY to proceed with upload
        if (response != null && response.header.type == PacketType.TRANSFER_READY) {
            return@withContext true
        }
        // Any other response (like ACK or SKIP if implemented) means we don't upload
        false
    }
    
    suspend fun sendPhotoDataStream(inputStream: java.io.InputStream): Boolean = withContext(Dispatchers.IO) {
        // Do NOT catch exceptions here
        val buffer = ByteArray(1024 * 1024) // 1MB buffer
        var bytesRead: Int
        
        val out = outputStream ?: throw java.io.IOException("Not connected")
        
        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                // Send FILE_CHUNK
                // Create a slice of the buffer if bytesRead < buffer.size, or use buffer directly
                // To avoid copying for the last chunk if possible, but NetworkPacket.createBinary likely copies anyway.
                // Optimally for the last chunk:
                val chunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                
                val packet = NetworkPacket.createBinary(PacketType.FILE_CHUNK, chunk)
                out.write(packet.toBytes())
                out.flush()
                
                _syncProgress.value = _syncProgress.value.copy(bytesTransferred = _syncProgress.value.bytesTransferred + chunk.size)
            }
        } finally {
             // We don't close the inputStream here, we leave it to the caller
        }
        
        // Send TRANSFER_COMPLETE
        val json = JSONObject()
        json.put("status", "completed")
        val completePacket = NetworkPacket.create(PacketType.TRANSFER_COMPLETE, json)
        val response = sendRequest(completePacket)
        
        return@withContext (response != null && response.header.type == PacketType.TRANSFER_COMPLETE)
    }

    suspend fun sendPhotoData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        // Do NOT catch exceptions here
        val chunkSize = 1024 * 1024 // 1MB chunks
        var offset = 0
        
        val out = outputStream ?: throw java.io.IOException("Not connected")
        
        while (offset < data.size) {
            val end = Math.min(offset + chunkSize, data.size)
            val chunk = data.copyOfRange(offset, end)
            
            // Send FILE_CHUNK
            // In binary protocol, payload IS the chunk bytes.
            val packet = NetworkPacket.createBinary(PacketType.FILE_CHUNK, chunk)
            val out = outputStream ?: throw java.io.IOException("Not connected")
            out.write(packet.toBytes())
            out.flush()
            // No ACK per chunk for speed
            
            offset += chunkSize
            _syncProgress.value = _syncProgress.value.copy(bytesTransferred = _syncProgress.value.bytesTransferred + chunk.size)
        }
        
        // Send TRANSFER_COMPLETE for this file?
        // EnhancedSyncService seems to rely on this method completing the file transfer.
        // TcpListener expects TRANSFER_COMPLETE packet.
        
        val json = JSONObject()
        json.put("status", "completed") // Payload can be empty or status
        val completePacket = NetworkPacket.create(PacketType.TRANSFER_COMPLETE, json)
        val response = sendRequest(completePacket)
        
        return@withContext (response != null && response.header.type == PacketType.TRANSFER_COMPLETE)
    }
    
    suspend fun endSession(): Boolean = withContext(Dispatchers.IO) {
         try {
            val json = JSONObject()
            json.put("status", "completed")
            val packet = NetworkPacket.create(PacketType.TRANSFER_COMPLETE, json)
             
            val out = outputStream ?: return@withContext false
            out.write(packet.toBytes())
            out.flush()
            
            // We might expect an ACK or just close. 
            // In the new protocol, TRANSFER_COMPLETE might not expect a response or maybe a generic ACK.
            // Let's assume fire and forget or check for easy read.
             true
         } catch (e: Exception) {
             false
         }
    }
    
    // Helper to send simple JSON command if needed
    private suspend fun sendCommand(type: PacketType, json: JSONObject): Boolean {
        return try {
             val packet = NetworkPacket.create(type, json)
             val out = outputStream ?: return false
             out.write(packet.toBytes())
             out.flush()
             true
        } catch (e: Exception) {
            false
        }
    }
}

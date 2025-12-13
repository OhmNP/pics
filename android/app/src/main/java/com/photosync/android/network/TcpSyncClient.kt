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
import org.json.JSONObject
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
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            _connectionStatus.value = ConnectionStatus.Connecting
            Log.d(TAG, "Connecting to $serverIp:$serverPort")
            
            socket = Socket()
            socket?.connect(InetSocketAddress(serverIp, serverPort), CONNECT_TIMEOUT_MS)
            
            inputStream = DataInputStream(socket!!.getInputStream())
            outputStream = DataOutputStream(socket!!.getOutputStream())
            
            _connectionStatus.value = ConnectionStatus.Connected
            Log.i(TAG, "Connected to server")
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
    
    // Send a packet and wait for a response packet
    private suspend fun sendRequest(packet: NetworkPacket): NetworkPacket? = withContext(Dispatchers.IO) {
        try {
            val out = outputStream ?: return@withContext null
            val `in` = inputStream ?: return@withContext null
            
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
            null
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
    
    suspend fun startSession(deviceId: String, token: String = ""): Int? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("deviceId", deviceId)
            json.put("token", token)
            
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
        try {
            val json = JSONObject()
            json.put("filename", filename)
            json.put("size", size)
            json.put("hash", hash)
            
            val packet = NetworkPacket.create(PacketType.METADATA, json)
            val response = sendRequest(packet)
            
            // Expect TRANSFER_READY (or sometimes just Ack/Error)
            if (response != null && response.header.type == PacketType.TRANSFER_READY) {
                return@withContext true
            }
            // If file already exists, server might send different response? 
            // Reuse logic: if server has file, it might say "skip" or handle via batch check.
            // But assume here we only send if batchCheck said we need to.
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending metadata: ${e.message}")
            false
        }
    }
    
    suspend fun sendPhotoData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val chunkSize = 4096 // 4KB chunks
            var offset = 0
            
            while (offset < data.size) {
                val end = Math.min(offset + chunkSize, data.size)
                val chunk = data.copyOfRange(offset, end)
                
                // Send FILE_CHUNK
                // In binary protocol, payload IS the chunk bytes.
                val packet = NetworkPacket.createBinary(PacketType.FILE_CHUNK, chunk)
                val out = outputStream ?: return@withContext false
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
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending photo data: ${e.message}")
            false
        }
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
    }}

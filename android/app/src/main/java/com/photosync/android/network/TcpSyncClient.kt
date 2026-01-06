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
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TcpSyncClient(
    private val serverIp: String,
    private val serverPort: Int = 50505
) {
    private var socket: Socket? = null
    private var inputStream: DataInputStream? = null
    private var outputStream: DataOutputStream? = null
    private var currentTraceId: String? = null // Store trace ID for current operation
    private val isDisconnecting = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    companion object {
        private const val TAG = "TcpSyncClient"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val HEARTBEAT_INTERVAL_MS = 10000L
    }
    
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            isDisconnecting.set(false)
            _connectionStatus.value = ConnectionStatus.Connecting
            Log.d(TAG, "Connecting to $serverIp:$serverPort")
            
            val sslContext = SSLContext.getInstance("TLS")
            
            if (com.photosync.android.BuildConfig.DEBUG) {
                Log.w(TAG, "DEBUG BUILD: Trusting all certificates")
                // Trust all certificates for development/self-signed support
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                })
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            } else {
                 // Production: Use system default trust manager (rejects self-signed unless pinned or CA installed)
                 // This satisfies "Task A: Remove Insecure TLS Trust"
                 sslContext.init(null, null, null)
            }

            val socketFactory = sslContext.socketFactory

            val sslSocket = socketFactory.createSocket() as SSLSocket
            sslSocket.tcpNoDelay = true
            sslSocket.connect(InetSocketAddress(serverIp, serverPort), CONNECT_TIMEOUT_MS)
            
            // Start handshake manually if needed, or it happens on first IO
            sslSocket.startHandshake()

            socket = sslSocket
            
            val bufferSize = 1024 * 1024 // 1MB buffer
            inputStream = DataInputStream(BufferedInputStream(socket!!.getInputStream(), bufferSize))
            outputStream = DataOutputStream(BufferedOutputStream(socket!!.getOutputStream(), bufferSize))
            
            _connectionStatus.value = ConnectionStatus.Connected
            Log.i(TAG, "Connected to server")
            
            // Start periodic heartbeat
            startHeartbeatLoop()
            
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Connection failed: ${e.message}")
            _connectionStatus.value = ConnectionStatus.Error(e.message ?: "Connection failed")
            disconnect()
            false
        }
    }
    
    suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Attempting to reconnect...")
        disconnect()
        delay(1000) // Small wait before reconnect
        connect()
    }

    fun disconnect() {
        if (isDisconnecting.getAndSet(true)) return // Already disconnecting
        try {
            stopHeartbeatLoop()
            socket?.close()
        } catch (e: Exception) {
            Log.d(TAG, "Error closing socket: ${e.message}") // Debug log only
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
        if (isDisconnecting.get()) return
        try {
            val packet = NetworkPacket.create(PacketType.HEARTBEAT, null)
            val out = outputStream ?: return
            out.write(packet.toBytes())
            out.flush()
            Log.d(TAG, "Sent Heartbeat")
        } catch (e: Exception) {
            if (!isDisconnecting.get()) {
                 Log.e(TAG, "Failed to send heartbeat", e)
            }
            disconnect()
        }
    }
    
    // Send a packet and wait for a response packet
    private suspend fun sendRequest(packet: NetworkPacket): NetworkPacket? = withContext(Dispatchers.IO) {
        try {
            if (isDisconnecting.get()) throw CancellationException("Client disconnecting")

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
            
            // Safety check: Prevent massive allocations from corrupt packets or non-protocol data
            if (length > 10 * 1024 * 1024) { // 10MB limit for response
                throw java.io.IOException("Packet length too large: $length. Potential protocol mismatch or corruption.")
            }
            
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
            if (e is CancellationException) throw e
            
            if (isDisconnecting.get()) {
                Log.d(TAG, "sendRequest interrupted during disconnect: ${e.message}")
                throw CancellationException("Interrupted during disconnect", e)
            }
            
            // Integrated retry for transient errors unless we are already disconnecting
            if (e is java.io.IOException && !isDisconnecting.get()) {
                Log.w(TAG, "Transient error in sendRequest: ${e.message}. Attempting auto-reconnect...")
                if (reconnect()) {
                    try {
                        // Re-send packet once
                        val out = outputStream ?: throw java.io.IOException("Not connected after reconnect")
                        out.write(packet.toBytes())
                        out.flush()
                        // Continue reading response... 
                        // Actually, recursive call is safer to get the whole flow again
                        return@withContext sendRequest(packet)
                    } catch (retryEx: Exception) {
                        Log.e(TAG, "Retry failed after reconnect: ${retryEx.message}")
                    }
                }
            }

            Log.e(TAG, "Error in sendRequest: ${e.message}")
            disconnect()
            throw e
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
            if (e is CancellationException) throw e
            if (!isDisconnecting.get()) {
                Log.e(TAG, "Error checking hashes", e)
            }
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
            if (e is CancellationException) throw e
            if (!isDisconnecting.get()) {
                Log.e(TAG, "Error starting session", e)
            }
            null
        }
    }
    
    suspend fun sendPhotoMetadata(filename: String, size: Long, hash: String): Boolean = withContext(Dispatchers.IO) {
        // Do NOT catch exceptions here, let them propagate to stop sync loop on error
        val traceId = java.util.UUID.randomUUID().toString()
        currentTraceId = traceId
        Log.i(TAG, "Starting upload for $filename with TraceID: $traceId")

        val json = JSONObject()
        json.put("filename", filename)
        json.put("size", size)
        json.put("hash", hash)
        json.put("traceId", traceId)
        
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
                if (isDisconnecting.get()) throw CancellationException("Disconnecting during transfer")
                
                // Send FILE_CHUNK
                val chunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                
                val packet = NetworkPacket.createBinary(PacketType.FILE_CHUNK, chunk)
                try {
                    out.write(packet.toBytes())
                    out.flush()
                } catch (e: Exception) {
                    if (isDisconnecting.get()) throw CancellationException("Disconnecting", e)
                    throw e
                }
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
            if (isDisconnecting.get()) throw CancellationException("Disconnecting during transfer")
            
            val end = Math.min(offset + chunkSize, data.size)
            val chunk = data.copyOfRange(offset, end)
            
            // Send FILE_CHUNK
            // In binary protocol, payload IS the chunk bytes.
            val packet = NetworkPacket.createBinary(PacketType.FILE_CHUNK, chunk)
            try {
                out.write(packet.toBytes())
                out.flush()
            } catch (e: Exception) {
                if (isDisconnecting.get()) throw CancellationException("Disconnecting", e)
                throw e
            }
            // No ACK per chunk for speed
            
            offset += chunkSize
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
             if (e is CancellationException) throw e
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


    // Phase 2: Resumable Uploads
    
    data class UploadInitResult(
        val uploadId: String,
        val offset: Long,
        val chunkSize: Int,
        val isNew: Boolean
    )

    suspend fun resumeUpload(filename: String, size: Long, hash: String): UploadInitResult? = withContext(Dispatchers.IO) {
        try {
            if (isDisconnecting.get()) return@withContext null

            val traceId = java.util.UUID.randomUUID().toString()
            Log.i(TAG, "Initializing upload (V2) for $filename ($size bytes) TraceID: $traceId")
            
            val json = JSONObject()
            json.put("filename", filename)
            json.put("size", size)
            json.put("hash", hash)
            json.put("traceId", traceId)
            
            // Manual construction to force V2
            val payloadBytes = json.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            val header = com.photosync.android.network.protocol.PacketHeader(
                version = com.photosync.android.network.protocol.SyncProtocol.VERSION_2.toByte(), 
                type = PacketType.UPLOAD_INIT,
                payloadLength = payloadBytes.size
            )
            val packet = NetworkPacket(header, payloadBytes)
            
            val response = sendRequest(packet)
            
            if (response != null && response.header.type == PacketType.UPLOAD_ACK) {
                val respJson = response.getJsonPayload()
                if (respJson != null) {
                    val uploadId = respJson.getString("uploadId")
                    val receivedBytes = respJson.getLong("receivedBytes")
                    val chunkSize = respJson.optInt("chunkSize", 1024 * 1024)
                    val status = respJson.getString("status")
                    
                    return@withContext UploadInitResult(
                        uploadId = uploadId,
                        offset = receivedBytes,
                        chunkSize = chunkSize,
                        isNew = (status == "NEW")
                    )
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (isDisconnecting.get()) {
                throw CancellationException("Interrupted during resume", e)
            }
            Log.e(TAG, "Error in resumeUpload", e)
            null
        }
    }
    
    suspend fun sendChunk(uploadId: String, offset: Long, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isDisconnecting.get()) return@withContext false

            // Payload: UploadID (36 bytes fixed) + Offset (8 bytes) + Data
            val idStrBytes = uploadId.toByteArray(java.nio.charset.StandardCharsets.US_ASCII)
            val uploadIdBytes = ByteArray(36)
            System.arraycopy(idStrBytes, 0, uploadIdBytes, 0, Math.min(idStrBytes.size, 36))
            
            val buffer = ByteBuffer.allocate(36 + 8 + data.size).order(ByteOrder.BIG_ENDIAN)
            
            // UploadID (36 bytes, padded)
            buffer.put(uploadIdBytes)
            
            // Offset (8 bytes)
            buffer.putLong(offset)
            
            // Data
            buffer.put(data)
            
            val payload = buffer.array()
            
            val header = com.photosync.android.network.protocol.PacketHeader(
                version = com.photosync.android.network.protocol.SyncProtocol.VERSION_2.toByte(),
                type = PacketType.UPLOAD_CHUNK,
                payloadLength = payload.size
            )
            val packet = NetworkPacket(header, payload)
            
            val response = sendRequest(packet)
            
            if (response != null && response.header.type == PacketType.UPLOAD_CHUNK_ACK) {
                // Check success
                return@withContext true
            }
            false
        } catch (e: Exception) {
             if (e is CancellationException) throw e
             if (isDisconnecting.get()) {
                 Log.d(TAG, "Chunk send skipped (disconnecting)")
                 throw CancellationException("Interrupted during chunk send", e)
             } else {
                 Log.e(TAG, "Error sending chunk", e)
             }
             false
        }
    }
    
    suspend fun finishUpload(uploadId: String, hash: String): Boolean = withContext(Dispatchers.IO) {
        try {
             val json = JSONObject()
             json.put("uploadId", uploadId)
             json.put("sha256", hash) // Reverted to "sha256" as per server expectation
             
             val payloadBytes = json.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
             val header = com.photosync.android.network.protocol.PacketHeader(
                version = com.photosync.android.network.protocol.SyncProtocol.VERSION_2.toByte(), 
                type = PacketType.UPLOAD_FINISH,
                payloadLength = payloadBytes.size
            )
            val packet = NetworkPacket(header, payloadBytes)
            
            val response = sendRequest(packet)
            
            if (response != null) {
                if (response.header.type == PacketType.UPLOAD_RESULT) {
                     val respJson = response.getJsonPayload()
                     val status = respJson?.optString("status")
                     if (status == "COMPLETE" || status == "SUCCESS") {
                         return@withContext true
                     } else {
                         Log.e(TAG, "Upload failed at finish. Status: $status, Message: ${respJson?.optString("message")}")
                     }
                } else if (response.header.type == PacketType.PROTOCOL_ERROR) {
                    val respJson = response.getJsonPayload()
                    val msg = respJson?.optString("message") ?: "Unknown Error"
                    Log.e(TAG, "Server returned ERROR during finishUpload: $msg")
                } else {
                    Log.e(TAG, "Invalid response type to finishUpload: ${response.header.type}")
                }
            } else {
                Log.e(TAG, "No response to finishUpload")
            }
            false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!isDisconnecting.get()) {
                Log.e(TAG, "Error finishing upload", e)
            }
            false
        }
    }
    suspend fun abortUpload(uploadId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("uploadId", uploadId)
            
            val payloadBytes = json.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            val header = com.photosync.android.network.protocol.PacketHeader(
                version = com.photosync.android.network.protocol.SyncProtocol.VERSION_2.toByte(), 
                type = PacketType.UPLOAD_ABORT,
                payloadLength = payloadBytes.size
            )
            val packet = NetworkPacket(header, payloadBytes)
            
            val response = sendRequest(packet)
            
            // Server should respond with UPLOAD_RESULT (status=ABORTED) or just ACK
            // Assume success if we get a response
            return@withContext (response != null)
        } catch (e: Exception) {
            Log.e(TAG, "Error aborting upload", e)
            false
        }
    }
}


package com.photosync.android.network

import android.util.Log
import com.photosync.android.network.protocol.ProtocolMessages
import com.photosync.android.network.protocol.ProtocolParser
import com.photosync.android.network.protocol.ProtocolResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class TcpSyncClient(
    private val serverIp: String,
    private val serverPort: Int = 50505
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    
    companion object {
        private const val TAG = "TcpSyncClient"
    }
    
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket(serverIp, serverPort)
            reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
            writer = BufferedWriter(OutputStreamWriter(socket?.getOutputStream()))
            Log.d(TAG, "Connected to server: $serverIp:$serverPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            false
        }
    }
    
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            writer?.close()
            reader?.close()
            socket?.close()
            Log.d(TAG, "Disconnected from server")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}", e)
        }
    }
    
    suspend fun sendCommand(command: String): ProtocolResponse? = withContext(Dispatchers.IO) {
        try {
            writer?.write(command)
            writer?.flush()
            val response = reader?.readLine() ?: return@withContext null
            Log.d(TAG, "Sent: ${command.trim()}, Received: $response")
            ProtocolParser.parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: ${e.message}", e)
            null
        }
    }
    
    suspend fun startSession(deviceId: String, token: String = ""): Int? = withContext(Dispatchers.IO) {
        val response = sendCommand(ProtocolMessages.sessionStart(deviceId, token))
        when (response) {
            is ProtocolResponse.SessionAck -> response.sessionId
            else -> null
        }
    }
    
    suspend fun sendPhotoMetadata(filename: String, size: Long, hash: String): Boolean = withContext(Dispatchers.IO) {
        val response = sendCommand(ProtocolMessages.photoMetadata(filename, size, hash))
        response is ProtocolResponse.SendRequest
    }
    
    suspend fun sendPhotoData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            // Send DATA_TRANSFER command
            sendCommand(ProtocolMessages.dataTransfer(data.size.toLong()))
            
            // Send binary data
            socket?.getOutputStream()?.write(data)
            socket?.getOutputStream()?.flush()
            
            // Wait for ACK
            val response = reader?.readLine()
            val ack = response?.let { ProtocolParser.parseResponse(it) }
            ack is ProtocolResponse.Ack
        } catch (e: Exception) {
            Log.e(TAG, "Error sending photo data: ${e.message}", e)
            false
        }
    }
    
    suspend fun batchCheck(hashes: List<String>): List<String> = withContext(Dispatchers.IO) {
        try {
            // Send BATCH_CHECK command
            sendCommand(ProtocolMessages.batchCheck(hashes.size))
            
            // Send all hashes
            hashes.forEach { hash ->
                writer?.write("$hash\n")
                writer?.flush()
            }
            
            // Read BATCH_RESULT response
            val resultLine = reader?.readLine() ?: return@withContext emptyList()
            val result = ProtocolParser.parseResponse(resultLine)
            
            if (result is ProtocolResponse.BatchResult) {
                // Read the found hashes
                val foundHashes = mutableListOf<String>()
                repeat(result.count) {
                    reader?.readLine()?.let { foundHashes.add(it) }
                }
                foundHashes
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in batch check: ${e.message}", e)
            emptyList()
        }
    }
    
    suspend fun endSession(): Boolean = withContext(Dispatchers.IO) {
        val response = sendCommand(ProtocolMessages.sessionEnd())
        response is ProtocolResponse.Ack
    }
}

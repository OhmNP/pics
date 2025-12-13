package com.photosync.android.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket

class UdpDiscoveryListener {
    
    companion object {
        private const val TAG = "UdpDiscovery"
        private const val UDP_PORT = 50505
        private const val BUFFER_SIZE = 1024
        private const val TIMEOUT_MS = 10000 // 10 seconds
    }
    
    data class ServerInfo(
        val ip: String,
        val name: String = "PhotoSync Server",
        val port: Int = 50505
    )
    
    suspend fun discoverServer(): ServerInfo? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(UDP_PORT)
            socket.soTimeout = TIMEOUT_MS
            socket.broadcast = true
            
            val buffer = ByteArray(BUFFER_SIZE)
            val packet = DatagramPacket(buffer, buffer.size)
            
            Log.d(TAG, "Listening for server broadcasts on port $UDP_PORT...")
            socket.receive(packet)
            
            val message = String(packet.data, 0, packet.length)
            Log.d(TAG, "Received broadcast: $message from ${packet.address.hostAddress}")
            
            // Parse JSON: {"service":"photosync","ip":"...","serverName":"..."}
            try {
                val json = JSONObject(message)
                if (json.getString("service") == "photosync") {
                    val serverIp = packet.address.hostAddress?.let { json.optString("ip", it) }
                    val name = json.optString("serverName", "PhotoSync Server")
                    ServerInfo(serverIp.toString(), name)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse broadcast message: ${e.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Discovery failed: ${e.message}", e)
            null
        } finally {
            socket?.close()
        }
    }
}

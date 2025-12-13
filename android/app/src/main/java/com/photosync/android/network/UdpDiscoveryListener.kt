package com.photosync.android.network

import android.util.Log
import com.photosync.android.network.protocol.NetworkPacket
import com.photosync.android.network.protocol.PacketType
import com.photosync.android.network.protocol.SyncProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.charset.StandardCharsets

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
            
            // New Protocol: Parse Binary Packet
            // Header is 8 bytes
            if (packet.length < NetworkPacket.HEADER_SIZE) {
                Log.w(TAG, "Received packet too small: ${packet.length}")
                return@withContext null
            }
            
            val data = packet.data.copyOfRange(0, packet.length)
            
            try {
                val header = NetworkPacket.parseHeader(data)
                
                if (header.magic != SyncProtocol.MAGIC_NUMBER.toShort()) {
                    Log.w(TAG, "Invalid magic number: ${header.magic}")
                    return@withContext null
                }
                
                if (header.type != PacketType.DISCOVERY) {
                    Log.w(TAG, "Unexpected packet type: ${header.type}")
                    return@withContext null
                }
                
                // Extract Paylaod
                val payloadBytes = data.copyOfRange(NetworkPacket.HEADER_SIZE, NetworkPacket.HEADER_SIZE + header.payloadLength)
                val message = String(payloadBytes, StandardCharsets.UTF_8)
                
                Log.d(TAG, "Received broadcast payload: $message from ${packet.address.hostAddress}")
                
                val json = JSONObject(message)
                if (json.optString("service") == "photosync") {
                    val serverIp = packet.address.hostAddress?.let { json.optString("ip", it) }
                    val name = json.optString("serverName", "PhotoSync Server")
                    ServerInfo(serverIp.toString(), name, json.optInt("port", 50505))
                } else {
                    null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse packet: ${e.message}")
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

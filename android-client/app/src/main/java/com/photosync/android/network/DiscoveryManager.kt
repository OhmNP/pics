package com.photosync.android.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class DiscoveryManager {
    private val TAG = "DiscoveryManager"
    private val DISCOVERY_PORT = 50505
    private val TIMEOUT_MS = 5000 // 5 seconds timeout

    suspend fun discoverServer(): String? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(DISCOVERY_PORT)
            socket.soTimeout = TIMEOUT_MS
            
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            
            Log.i(TAG, "Listening for server broadcast on port $DISCOVERY_PORT...")
            socket.receive(packet)
            
            val message = String(packet.data, 0, packet.length)
            Log.i(TAG, "Received broadcast from ${packet.address.hostAddress}: $message")
            
            val json = JSONObject(message)
            if (json.optString("service") == "photosync") {
                val serverIp = packet.address.hostAddress
                Log.i(TAG, "PhotoSync server found at $serverIp")
                return@withContext serverIp
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Discovery timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Discovery failed", e)
        } finally {
            socket?.close()
        }
        return@withContext null
    }
}

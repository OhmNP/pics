package com.photosync.android.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.photosync.android.MainActivity
import com.photosync.android.R
import com.photosync.android.data.ConnectionManager
import com.photosync.android.data.SettingsManager
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.nio.charset.StandardCharsets

class ConnectionService : Service() {
    private val TAG = "ConnectionService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "photosync_connection"
    
    private var connectionJob: Job? = null
    private var isConnecting = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "ConnectionService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "ConnectionService started")
        
        // Start as foreground service
        val notification = createNotification("Connecting to server...", false)
        startForeground(NOTIFICATION_ID, notification)
        
        // Start connection in background
        startConnection()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "ConnectionService destroyed")
        disconnectFromServer()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PhotoSync Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains connection to PhotoSync server"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(message: String, isConnected: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val icon = if (isConnected) 
            android.R.drawable.presence_online 
        else 
            android.R.drawable.presence_invisible
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhotoSync")
            .setContentText(message)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(message: String, isConnected: Boolean) {
        val notification = createNotification(message, isConnected)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun startConnection() {
        if (isConnecting) {
            Log.w(TAG, "Already connecting")
            return
        }
        
        connectionJob?.cancel()
        connectionJob = GlobalScope.launch(Dispatchers.IO) {
            connectToServer()
        }
    }
    
    private suspend fun connectToServer() {
        isConnecting = true
        val settings = SettingsManager(this)
        val serverIp = settings.serverIp
        val serverPort = settings.serverPort
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "android_device"
        
        val connMgr = ConnectionManager.getInstance()
        connMgr.setConnecting()
        
        Log.i(TAG, "Attempting to connect to $serverIp:$serverPort")
        updateNotification("Connecting to $serverIp:$serverPort...", false)
        
        try {
            val socket = Socket(serverIp, serverPort)
            socket.soTimeout = 30000 // 30 second timeout for reads
            
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            
            // Send HELLO handshake
            writeCommand(writer, "HELLO $deviceId")
            val response = readResponse(reader)
            
            if (!response.startsWith("SESSION_START")) {
                Log.e(TAG, "Handshake failed: $response")
                socket.close()
                connMgr.setError("Handshake failed")
                scheduleReconnect()
                return
            }
            
            Log.i(TAG, "Connected successfully: $response")
            reconnectAttempts = 0
            
            // Store connection in ConnectionManager (this also sets status to Connected)
            connMgr.setConnection(socket, reader, writer)
            
            updateNotification("Connected to server", true)
            
            // Keep connection alive - listen for server messages
            maintainConnection(socket, reader, writer)
            
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            updateNotification("Connection failed: ${e.message}", false)
            connMgr.setError(e.message ?: "Unknown error")
            scheduleReconnect()
        } finally {
            isConnecting = false
        }
    }
    
    private suspend fun maintainConnection(
        socket: Socket,
        reader: BufferedReader,
        writer: BufferedWriter
    ) {
        try {
            // Keep connection alive by reading from server
            // Server might send commands or close connection
            while (socket.isConnected && !socket.isClosed) {
                // Check if there's data available (non-blocking check)
                if (reader.ready()) {
                    val message = readResponse(reader)
                    if (message.isEmpty()) {
                        // Connection closed by server
                        Log.i(TAG, "Server closed connection")
                        break
                    }
                    Log.d(TAG, "Received from server: $message")
                }
                
                // Sleep briefly to avoid busy waiting
                delay(1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error maintaining connection", e)
        } finally {
            socket.close()
            ConnectionManager.getInstance().clearConnection()
            updateNotification("Disconnected from server", false)
            scheduleReconnect()
        }
    }
    
    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(TAG, "Max reconnect attempts reached")
            updateNotification("Connection failed - max retries exceeded", false)
            return
        }
        
        reconnectAttempts++
        val delayMs = minOf(1000L * (1 shl reconnectAttempts), 60000L) // Exponential backoff, max 60s
        
        Log.i(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${delayMs}ms")
        
        GlobalScope.launch(Dispatchers.IO) {
            delay(delayMs)
            connectToServer()
        }
    }
    
    private fun disconnectFromServer() {
        connectionJob?.cancel()
        
        try {
            val conn = ConnectionManager.getInstance().getConnection()
            if (conn != null) {
                // Send END_SESSION before closing
                writeCommand(conn.writer, "END_SESSION")
                conn.socket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        } finally {
            ConnectionManager.getInstance().clearConnection()
        }
    }
    
    private fun writeCommand(writer: BufferedWriter, command: String) {
        writer.write(command)
        writer.write("\n")
        writer.flush()
    }
    
    private fun readResponse(reader: BufferedReader): String {
        return reader.readLine() ?: ""
    }
}

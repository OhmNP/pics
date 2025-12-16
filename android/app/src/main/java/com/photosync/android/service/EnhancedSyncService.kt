package com.photosync.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.photosync.android.data.AppDatabase
import com.photosync.android.data.entity.SyncStatus
import com.photosync.android.model.SyncProgress
import com.photosync.android.network.TcpSyncClient
import com.photosync.android.network.UdpDiscoveryListener
import com.photosync.android.repository.MediaRepository
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EnhancedSyncService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    
    private lateinit var mediaRepository: MediaRepository
    private lateinit var database: AppDatabase
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    
    private val _serverStatus = MutableStateFlow(ServerConnectivityStatus.DISCONNECTED)
    val serverStatus: StateFlow<ServerConnectivityStatus> = _serverStatus.asStateFlow()
    
    private var monitoringJob: Job? = null
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    
    // Track active clients to force-close them on destruction
    private val activeClients = java.util.Collections.synchronizedList(mutableListOf<TcpSyncClient>())
    
    companion object {
        private const val TAG = "EnhancedSyncService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sync_channel"
        
        const val ACTION_START_SYNC = "com.photosync.android.START_SYNC"
        const val ACTION_PAUSE_SYNC = "com.photosync.android.PAUSE_SYNC"
        const val ACTION_RESUME_SYNC = "com.photosync.android.RESUME_SYNC"
        const val ACTION_STOP_SYNC = "com.photosync.android.STOP_SYNC"
        const val ACTION_RECONCILE = "com.photosync.android.RECONCILE"
        const val ACTION_START_MONITORING = "com.photosync.android.START_MONITORING"
        
        private var instance: EnhancedSyncService? = null
        
        fun getInstance(): EnhancedSyncService? = instance
    }
    
    sealed class SyncState {
        object Idle : SyncState()
        object Discovering : SyncState()
        object Connecting : SyncState()
        object Syncing : SyncState()
        object Paused : SyncState()
        object Reconciling : SyncState()
        data class Error(val message: String) : SyncState()
    }
    
    enum class ServerConnectivityStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        mediaRepository = MediaRepository(contentResolver, database)
        createNotificationChannel()
        setupNetworkMonitoring()
        startMonitoring()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SYNC -> startSync()
            ACTION_PAUSE_SYNC -> pauseSync()
            ACTION_RESUME_SYNC -> resumeSync()
            ACTION_STOP_SYNC -> stopSync()
            ACTION_RECONCILE -> startReconciliation()
            ACTION_START_MONITORING -> startMonitoring()
        }
        return START_STICKY
    }
    
    private fun startSync() {
        // Ensure monitoring is active
        startMonitoring()
        
        if (syncJob?.isActive == true) {
            if (_syncState.value is SyncState.Paused) {
                resumeSync()
                return
            }
            Log.w(TAG, "Sync already in progress")
            return
        }
        
        startForeground(NOTIFICATION_ID, createNotification("Starting sync..."))
        
        syncJob = serviceScope.launch {
            try {
                performSync()
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private var monitoringClient: TcpSyncClient? = null

    private fun startMonitoring() {
        // Must call startForeground since we are using startForegroundService
        startForeground(NOTIFICATION_ID, createNotification("Monitoring server connection..."))
        
        if (monitoringJob?.isActive == true) return
        
        monitoringJob = serviceScope.launch {
            val settingsManager = com.photosync.android.data.SettingsManager(this@EnhancedSyncService)
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            
            while (isActive) {
                try {
                    val serverIp = settingsManager.serverIp
                    if (serverIp.isBlank()) {
                        _serverStatus.value = ServerConnectivityStatus.DISCONNECTED
                        delay(5000)
                        continue
                    }

                    if (monitoringClient == null) {
                        monitoringClient = TcpSyncClient(serverIp)
                    }

                    _serverStatus.value = ServerConnectivityStatus.CONNECTING
                    val connected = monitoringClient!!.connect()

                    if (connected) {
                        // Start Session to register with ConnectionManager
                        val sessionId = monitoringClient!!.startSession(deviceId)

                        if (sessionId != null) {
                            _serverStatus.value = ServerConnectivityStatus.CONNECTED
                            Log.i(TAG, "Connected to server with session ID: $sessionId")
                            
                            // Keep monitoring until disconnected
                            monitoringClient!!.connectionStatus.collect { status ->
                                if (status is com.photosync.android.model.ConnectionStatus.Disconnected || 
                                    status is com.photosync.android.model.ConnectionStatus.Error) {
                                    throw Exception("Disconnected") // Break inner loop to retry
                                }
                            }
                        } else {
                            Log.w(TAG, "Failed to start session")
                            monitoringClient!!.disconnect()
                            _serverStatus.value = ServerConnectivityStatus.DISCONNECTED // or ERROR
                            delay(5000) // Wait before retry
                        }
                    } else {
                         _serverStatus.value = ServerConnectivityStatus.DISCONNECTED
                         delay(5000)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Connection monitor error: ${e.message}")
                    _serverStatus.value = ServerConnectivityStatus.DISCONNECTED
                    monitoringClient?.disconnect()
                    monitoringClient = null
                    delay(5000)
                }
            }
        }
    }
    
    private fun pauseSync() {
        _syncState.value = SyncState.Paused
        updateNotification("Sync paused")
    }
    
    private fun resumeSync() {
        if (_syncState.value is SyncState.Paused) {
            _syncState.value = SyncState.Syncing
            updateNotification("Resuming sync...")
        }
    }
    
    private fun stopSync() {
        syncJob?.cancel()
        _syncState.value = SyncState.Idle
        _syncProgress.value = SyncProgress()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
    
    private fun startReconciliation() {
        serviceScope.launch {
            try {
                performReconciliation()
            } catch (e: Exception) {
                Log.e(TAG, "Reconciliation failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private suspend fun performSync() {
        // 1. Discover server
        _syncState.value = SyncState.Discovering
        updateNotification("Discovering server...")
        
        val settingsManager = com.photosync.android.data.SettingsManager(this)
        val serverIp = settingsManager.serverIp
        
        if (serverIp.isBlank()) {
            _syncState.value = SyncState.Error("Server not configured")
            return
        }
        
        // 2. Initial connection check (optional, but good for quick fail)
        _syncState.value = SyncState.Connecting
        updateNotification("Connecting to server...")
        
        // 3. Get all media items
        val mediaItems = mediaRepository.getAllMediaItems()
        if (mediaItems.isEmpty()) {
            _syncState.value = SyncState.Idle
            return
        }
        
        _syncState.value = SyncState.Syncing
        _syncProgress.value = SyncProgress(totalFiles = mediaItems.size, isActive = true)
        
        // Parallel Sync Configuration
        val workerCount = 4
        val itemChannel = kotlinx.coroutines.channels.Channel<com.photosync.android.model.MediaItem>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        
        // Load items into channel
        mediaItems.forEach { itemChannel.trySend(it) }
        itemChannel.close() // Signal no more items
        
        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val totalBytesTransferred = java.util.concurrent.atomic.AtomicLong(0)
        
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        
        // Launch workers
        coroutineScope {
            val jobs = List(workerCount) { workerId ->
                launch(Dispatchers.IO) {
                    val client = TcpSyncClient(serverIp)
                    activeClients.add(client)
                    try {
                        if (!client.connect()) {
                            Log.w(TAG, "Worker $workerId failed to connect")
                            return@launch
                        }
                        
                        val sessionId = client.startSession(deviceId)
                        if (sessionId == null) {
                            Log.w(TAG, "Worker $workerId failed to start session")
                            return@launch
                        }
                        
                        for (item in itemChannel) {
                            // Check if paused
                            while (_syncState.value is SyncState.Paused) {
                                delay(1000)
                            }
                            
                            // Calculate hash
                            val hash = if (item.hash.isEmpty()) {
                                mediaRepository.calculateHash(item.uri)
                            } else {
                                item.hash
                            }
                            
                            // Update status
                            mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.SYNCING)
                            
                            val index = completedCount.get() + 1
                            updateNotification("Syncing... ($index/${mediaItems.size})")
                             _syncProgress.value = _syncProgress.value.copy(
                                currentFile = item.name, // Just show last started
                                currentFileIndex = index
                            )
                            
                            // Send metadata
                            val needsUpload = client.sendPhotoMetadata(item.name, item.size, hash)
                            
                            if (needsUpload) {
                                val inputStream = contentResolver.openInputStream(item.uri)
                                if (inputStream != null) {
                                    inputStream.use { stream ->
                                        if (client.sendPhotoDataStream(stream)) {
                                            totalBytesTransferred.addAndGet(item.size) // Assuming item.size is the correct size
                                            mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.SYNCED)
                                        } else {
                                            Log.e(TAG, "Failed to send photo data for ${item.name}")
                                            mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.ERROR)
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "Failed to open input stream for ${item.name}")
                                    mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.ERROR)
                                }
                            } else {
                                mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.SYNCED)
                            }
                            
                            completedCount.incrementAndGet()
                            _syncProgress.value = _syncProgress.value.copy(bytesTransferred = totalBytesTransferred.get())
                        }
                        
                        client.endSession()
                    } catch (e: Exception) {
                        Log.e(TAG, "Worker $workerId error", e)
                    } finally {
                        client.disconnect()
                        activeClients.remove(client)
                    }
                }
            }
            jobs.joinAll()
        }
        
        _syncState.value = SyncState.Idle
        updateNotification("Sync completed: ${completedCount.get()} files")
        _syncProgress.value = _syncProgress.value.copy(isActive = false)
    }
    
    private suspend fun performReconciliation() {
        _syncState.value = SyncState.Reconciling
        updateNotification("Reconciling sync status...")
        
        val settingsManager = com.photosync.android.data.SettingsManager(this)
        val serverIp = settingsManager.serverIp
        
        if (serverIp.isBlank()) {
            _syncState.value = SyncState.Error("Server not configured")
            return
        }
        
        val client = TcpSyncClient(serverIp)
        if (!client.connect()) {
            _syncState.value = SyncState.Error("Failed to connect to server")
            return
        }
        
        try {
            // Get all media items
            val mediaItems = mediaRepository.getAllMediaItems()
            
            // Calculate hashes for items that don't have them
            val itemsWithHashes = mediaItems.map { item ->
                val hash = if (item.hash.isEmpty()) {
                    mediaRepository.calculateHash(item.uri)
                } else {
                    item.hash
                }
                item.copy(hash = hash)
            }
            
            // Batch check in groups of 100
            val batchSize = 100
            itemsWithHashes.chunked(batchSize).forEach { batch ->
                val hashes = batch.map { it.hash }
                val foundHashes = client.batchCheck(hashes)
                
                // Update status for found hashes
                mediaRepository.updateSyncStatusByHashes(foundHashes)
                
                Log.d(TAG, "Reconciliation: ${foundHashes.size}/${hashes.size} files already synced")
            }
            
            _syncState.value = SyncState.Idle
            updateNotification("Reconciliation completed")
            
        } finally {
            client.disconnect()
        }
    }
    

    
    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.i(TAG, "Network lost: Disconnecting...")
                serviceScope.launch {
                   try {
                       _serverStatus.value = ServerConnectivityStatus.DISCONNECTED
                       monitoringClient?.disconnect()
                   } catch (e: Exception) {
                       Log.e(TAG, "Error handling network loss", e)
                   }
                }
            }

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(TAG, "Network available")
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "PhotoSync background synchronization"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhotoSync")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "Task removed, stopping service")
        
        // Signal system immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        forceCleanup()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        forceCleanup()
    }
    
    private fun forceCleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Check if init before logging effectively
             Log.e(TAG, "Error unregistering network callback", e)
        }
        
        try {
            monitoringClient?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting in onDestroy", e)
        }
        
        // Force disconnect all active workers
        synchronized(activeClients) {
            activeClients.forEach { 
                try { it.disconnect() } catch (e: Exception) { Log.e(TAG, "Error forcing disconnect", e) }
            }
            activeClients.clear()
        }
        
        serviceScope.cancel()
    }
}

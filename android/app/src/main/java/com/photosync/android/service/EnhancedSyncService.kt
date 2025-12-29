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
import androidx.core.app.NotificationManagerCompat
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import android.os.BatteryManager
import com.photosync.android.data.SettingsManager

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
    
    private var contentObserver: android.database.ContentObserver? = null
    private var debounceJob: Job? = null
    
    // Track active clients to force-close them on destruction
    private val activeClients = java.util.Collections.synchronizedList(mutableListOf<TcpSyncClient>())
    
    // Orchestration State
    private val syncMutex = Mutex()
    private var lastSyncRequestTime = 0L
    private val requestDebounceJob = AtomicReference<Job?>(null)
    
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
        database = AppDatabase.getDatabase(this)
        mediaRepository = MediaRepository(this, database)
        createNotificationChannel()
        createNotificationChannel()
        setupNetworkMonitoring()
        startMonitoring()
        setupContentObserver()
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
        requestSync("MANUAL", manual = true)
    }

    fun requestSync(reason: String, manual: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        
        // 1. Rate Limit: 15s (unless manual)
        if (!manual && (currentTime - lastSyncRequestTime < 15000)) {
            Log.d(TAG, "[Orchestrator] RequestSync($reason) rejected - rate limited")
            return
        }
        
        Log.i(TAG, "[Orchestrator] RequestSync: reason=$reason, manual=$manual")
        
        // 2. Debounce (3s)
        requestDebounceJob.getAndSet(null)?.cancel()
        val job = serviceScope.launch {
            if (!manual) delay(3000)
            
            // 3. Single-flight guard
            if (syncMutex.isLocked) {
                Log.i(TAG, "[Orchestrator] Single-flight: execution skipped (already running)")
                return@launch
            }
            
            syncMutex.withLock {
                lastSyncRequestTime = System.currentTimeMillis()
                performSyncInternal()
            }
        }
        requestDebounceJob.set(job)
    }

    private suspend fun performSyncInternal() {
        if (!checkConstraints(SettingsManager(this))) {
            Log.i(TAG, "Sync constraints not met, skipping")
            return
        }
        
        startForeground(NOTIFICATION_ID, createNotification("Starting sync..."))
        
        syncJob = serviceScope.launch {
            try {
                performSync()
            } catch (e: CancellationException) {
                Log.i(TAG, "Sync job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            }
        }
        syncJob?.join()
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
                        val userName = settingsManager.userName
                        val sessionId = monitoringClient!!.startSession(deviceId, "", userName)

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
                    if (e !is CancellationException) {
                        Log.d(TAG, "Connection monitor resetting: ${e.message}")
                    }
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

        // 2. Initial connection check
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
        val itemChannel =
            kotlinx.coroutines.channels.Channel<com.photosync.android.model.MediaItem>(kotlinx.coroutines.channels.Channel.UNLIMITED)

        // Load items into channel
        mediaItems.forEach { itemChannel.trySend(it) }
        itemChannel.close()

        val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val totalBytesTransferred = java.util.concurrent.atomic.AtomicLong(0)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val userName = settingsManager.userName

        try {
            coroutineScope {
                val jobs = List(workerCount) { workerId ->
                    launch(Dispatchers.IO) {
                        Log.d(TAG, "Worker $workerId started")
                        val client = TcpSyncClient(serverIp)
                        activeClients.add(client)
                        
                        try {
                            if (!client.connect()) {
                                Log.w(TAG, "Worker $workerId failed to connect")
                                return@launch
                            }
                            
                            val sessionId = client.startSession(deviceId, "", userName)
                            if (sessionId == null) {
                                Log.w(TAG, "Worker $workerId failed to start session")
                                return@launch
                            }
                            
                            for (item in itemChannel) {
                                try {
                                    // Check if paused (Manual or Constraint)
                                    while (_syncState.value is SyncState.Paused) {
                                        delay(1000)
                                    }

                                    val hash = if (item.hash.isEmpty()) {
                                        mediaRepository.calculateHash(item.uri)
                                    } else {
                                        item.hash
                                    }

                                    mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.UPLOADING)

                                    // Constraint check periodically
                                    if (!checkConstraints(settingsManager)) {
                                        Log.w(TAG, "Constraints violated. Pausing.")
                                        _syncState.value = SyncState.Paused
                                        updateNotification("Waiting for charging/Wi-Fi...")
                                        while (_syncState.value is SyncState.Paused && !checkConstraints(settingsManager)) {
                                            delay(5000)
                                            if (!isActive) break
                                        }
                                        if (_syncState.value is SyncState.Paused && checkConstraints(settingsManager)) {
                                            _syncState.value = SyncState.Syncing
                                            updateNotification("Resuming...")
                                        }
                                    }

                                    val index = completedCount.get() + 1
                                    updateNotification("Syncing... ($index/${mediaItems.size})")
                                    _syncProgress.value = _syncProgress.value.copy(
                                        currentFile = item.name,
                                        currentFileIndex = index
                                    )

                                    // V2 / V1 Upload Flow
                                    var initResult = client.resumeUpload(item.name, item.size, hash)
                                    
                                    if (initResult == null) {
                                        // V1 Fallback
                                        Log.w(TAG, "V2 Init failed, trying V1")
                                        if (client.sendPhotoMetadata(item.name, item.size, hash)) {
                                            contentResolver.openInputStream(item.uri)?.use { stream ->
                                                if (client.sendPhotoDataStream(stream)) {
                                                    totalBytesTransferred.addAndGet(item.size)
                                                    mediaRepository.markAsSynced(item.id, hash)
                                                } else {
                                                    mediaRepository.markAsFailed(item.id, "V1 Transfer Failed")
                                                }
                                            }
                                        } else {
                                            mediaRepository.markAsFailed(item.id, "V1 Init Failed")
                                        }
                                    } else {
                                        // V2 Flow
                                        val uploadId = initResult.uploadId
                                        var currentOffset = initResult.offset
                                        val chunkSize = initResult.chunkSize
                                        
                                        contentResolver.openInputStream(item.uri)?.use { stream ->
                                            if (currentOffset > 0) stream.skip(currentOffset)
                                            
                                            val buffer = ByteArray(chunkSize)
                                            var bytesRead: Int
                                            var success = true
                                            
                                            while (currentOffset < item.size && success) {
                                                while (_syncState.value is SyncState.Paused) delay(1000)
                                                
                                                bytesRead = stream.read(buffer)
                                                if (bytesRead == -1) break
                                                
                                                val chunk = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)
                                                if (client.sendChunk(uploadId, currentOffset, chunk)) {
                                                    currentOffset += bytesRead
                                                    totalBytesTransferred.addAndGet(bytesRead.toLong())
                                                    _syncProgress.value = _syncProgress.value.copy(bytesTransferred = totalBytesTransferred.get())
                                                } else {
                                                    success = false
                                                }
                                            }
                                            
                                            if (success) {
                                                if (client.finishUpload(uploadId, hash)) {
                                                    mediaRepository.markAsSynced(item.id, hash)
                                                } else {
                                                    mediaRepository.markAsFailed(item.id, "Finalize Failed")
                                                }
                                            } else {
                                                mediaRepository.markAsFailed(item.id, "Chunk Transfer Failed")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e(TAG, "Error syncing ${item.name}", e)
                                    
                                    val status = when (e) {
                                        is java.io.IOException -> SyncStatus.PAUSED_NETWORK
                                        else -> SyncStatus.ERROR
                                    }
                                    val reason = if (status == SyncStatus.PAUSED_NETWORK) "Network issue" else (e.message ?: "Unknown error")
                                    mediaRepository.markAsFailed(item.id, reason, status)
                                    
                                    if (status == SyncStatus.PAUSED_NETWORK) {
                                        Log.w(TAG, "Stopping worker $workerId due to network issue")
                                        break
                                    }
                                }
                                completedCount.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e(TAG, "Worker $workerId fatal error", e)
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
            
            // Update last successful backup timestamp
            if (completedCount.get() > 0) {
                settingsManager.lastSuccessfulBackupTimestamp = System.currentTimeMillis()
            }
            
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Sync process error", e)
            _syncState.value = SyncState.Error(e.message ?: "Unknown error")
        }
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
                Log.i(TAG, "Network lost: Pausing sync...")
                serviceScope.launch {
                   try {
                       _serverStatus.value = ServerConnectivityStatus.DISCONNECTED
                       
                       // 1. Cancel current sync job
                       syncJob?.cancelAndJoin()
                       
                       // 2. Mark UPLOADING items as PAUSED_NETWORK
                       mediaRepository.pauseUploadingItems("PAUSED_NETWORK")
                       
                       // 3. Force disconnect monitoring client
                       monitoringClient?.disconnect()
                       
                       Log.i(TAG, "Sync paused and sockets closed due to network loss")
                   } catch (e: Exception) {
                       Log.e(TAG, "Error handling network loss", e)
                   }
                }
            }

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(TAG, "Network available")
                requestSync("NETWORK_AVAILABLE")
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    private fun setupContentObserver() {
        contentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "MediaStore change detected")
                
                val settings = com.photosync.android.data.SettingsManager(this@EnhancedSyncService)
                if (!settings.autoSyncEnabled) {
                     Log.d(TAG, "Auto-sync disabled, ignoring change")
                     return
                }

                debounceJob?.cancel()
                debounceJob = serviceScope.launch {
                    delay(5000) // Debounce 5 seconds
                    Log.i(TAG, "Triggering auto-sync after debounce")
                    startSync()
                }
            }
        }
        
        try {
            contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
            contentResolver.registerContentObserver(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register content observer", e)
        }
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
    
    private val notificationLimiter = com.photosync.android.util.RateLimiter(500)
    
    private fun updateNotification(content: String) {
        // Throttle updates unless "Sync completed"
        if (!content.contains("Sync completed") && !notificationLimiter.tryAcquire()) {
            return
        }
        
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
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
        contentObserver?.let { 
            try { contentResolver.unregisterContentObserver(it) } catch(e: Exception) {} 
        }
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
        
        // Offload network disconnects to avoid NetworkOnMainThreadException
        Thread {
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
        }.start()
        
        serviceScope.cancel()
    }
    
    private fun checkConstraints(settings: SettingsManager): Boolean {
        // Charging
        if (settings.chargingOnly) {
             val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
             if (!batteryManager.isCharging) return false
        }
        
        // Battery Threshold (only if not charging)
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (!batteryManager.isCharging) {
             val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
             if (level < settings.batteryThreshold) return false
        }
        
        // Wifi (Already handled by network callback mostly, but good to check)
        if (settings.wifiOnly) {
             val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
             if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
        }
        
        return true
    }
}

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
    
    private fun startMonitoring() {
        // Must call startForeground since we are using startForegroundService
        startForeground(NOTIFICATION_ID, createNotification("Monitoring server connection..."))
        
        if (monitoringJob?.isActive == true) return
        
        monitoringJob = serviceScope.launch {
            while (isActive) {
                checkServerConnection()
                delay(30_000) // Check every 30 seconds
            }
        }
    }
    
    private suspend fun checkServerConnection() {
        try {
            val settingsManager = com.photosync.android.data.SettingsManager(this)
            val serverIp = settingsManager.serverIp
            
            if (serverIp.isBlank()) {
                _serverStatus.value = ServerConnectivityStatus.DISCONNECTED
                return
            }
            
            _serverStatus.value = ServerConnectivityStatus.CONNECTING
            
            val client = TcpSyncClient(serverIp)
            val isConnected = client.connect()
            
            if (isConnected) {
                // Send weak ping or just connect check
                // Ideally we should send a ping packet, but connect + disconnect is a basic check for now
                // Or better, reuse client.connect() result.
                _serverStatus.value = ServerConnectivityStatus.CONNECTED
                client.disconnect()
            } else {
                 _serverStatus.value = ServerConnectivityStatus.DISCONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server connection check failed: ${e.message}")
            _serverStatus.value = ServerConnectivityStatus.ERROR
        } catch (e: Throwable) {
            // Catch service specific exceptions or other runtime errors
            Log.e(TAG, "Fatal error checking server connection: ${e.message}")
             _serverStatus.value = ServerConnectivityStatus.ERROR
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
                                val data = contentResolver.openInputStream(item.uri)?.readBytes()
                                if (data != null) {
                                    if (client.sendPhotoData(data)) {
                                        totalBytesTransferred.addAndGet(data.size.toLong())
                                        mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.SYNCED)
                                    } else {
                                        mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.ERROR)
                                    }
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
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }
}

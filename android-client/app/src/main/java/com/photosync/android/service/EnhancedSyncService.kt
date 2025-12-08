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
    
    companion object {
        private const val TAG = "EnhancedSyncService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sync_channel"
        
        const val ACTION_START_SYNC = "com.photosync.android.START_SYNC"
        const val ACTION_PAUSE_SYNC = "com.photosync.android.PAUSE_SYNC"
        const val ACTION_RESUME_SYNC = "com.photosync.android.RESUME_SYNC"
        const val ACTION_STOP_SYNC = "com.photosync.android.STOP_SYNC"
        const val ACTION_RECONCILE = "com.photosync.android.RECONCILE"
        
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
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getDatabase(this)
        mediaRepository = MediaRepository(contentResolver, database)
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SYNC -> startSync()
            ACTION_PAUSE_SYNC -> pauseSync()
            ACTION_RESUME_SYNC -> resumeSync()
            ACTION_STOP_SYNC -> stopSync()
            ACTION_RECONCILE -> startReconciliation()
        }
        return START_STICKY
    }
    
    private fun startSync() {
        if (syncJob?.isActive == true) {
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
        stopForeground(true)
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
        
        val serverConfig = database.serverConfigDao().getServerConfig()
        val serverIp = serverConfig?.serverIp ?: discoverServer() ?: run {
            _syncState.value = SyncState.Error("Server not found")
            return
        }
        
        // 2. Connect to server
        _syncState.value = SyncState.Connecting
        updateNotification("Connecting to server...")
        
        val client = TcpSyncClient(serverIp)
        if (!client.connect()) {
            _syncState.value = SyncState.Error("Failed to connect to server")
            return
        }
        
        try {
            // 3. Start session
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val sessionId = client.startSession(deviceId)
            if (sessionId == null) {
                _syncState.value = SyncState.Error("Failed to start session")
                return
            }
            
            Log.d(TAG, "Session started: $sessionId")
            
            // 4. Get all media items
            _syncState.value = SyncState.Syncing
            val mediaItems = mediaRepository.getAllMediaItems()
            _syncProgress.value = SyncProgress(totalFiles = mediaItems.size, isActive = true)
            
            // 5. Sync each item
            var bytesTransferred = 0L
            mediaItems.forEachIndexed { index, item ->
                // Check if paused
                while (_syncState.value is SyncState.Paused) {
                    delay(1000)
                }
                
                _syncProgress.value = _syncProgress.value.copy(
                    currentFile = item.name,
                    currentFileIndex = index + 1
                )
                updateNotification("Syncing ${index + 1}/${mediaItems.size}: ${item.name}")
                
                // Calculate hash if not already done
                val hash = if (item.hash.isEmpty()) {
                    mediaRepository.calculateHash(item.uri)
                } else {
                    item.hash
                }
                
                // Update status to syncing
                mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.SYNCING)
                
                // Send metadata
                val needsUpload = client.sendPhotoMetadata(item.name, item.size, hash)
                
                if (needsUpload) {
                    // Read file data
                    val data = contentResolver.openInputStream(item.uri)?.readBytes()
                    if (data != null) {
                        val success = client.sendPhotoData(data)
                        if (success) {
                            bytesTransferred += data.size
                            mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.SYNCED)
                        } else {
                            mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.ERROR)
                        }
                    }
                } else {
                    // Already exists on server
                    mediaRepository.updateSyncStatus(item.id, hash, SyncStatus.SYNCED)
                }
                
                _syncProgress.value = _syncProgress.value.copy(bytesTransferred = bytesTransferred)
            }
            
            // 6. End session
            client.endSession()
            
            _syncState.value = SyncState.Idle
            updateNotification("Sync completed: ${mediaItems.size} files")
            
        } finally {
            client.disconnect()
            _syncProgress.value = _syncProgress.value.copy(isActive = false)
        }
    }
    
    private suspend fun performReconciliation() {
        _syncState.value = SyncState.Reconciling
        updateNotification("Reconciling sync status...")
        
        val serverConfig = database.serverConfigDao().getServerConfig()
        val serverIp = serverConfig?.serverIp ?: run {
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
    
    private suspend fun discoverServer(): String? {
        val discovery = UdpDiscoveryListener()
        return discovery.discoverServer()?.ip
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

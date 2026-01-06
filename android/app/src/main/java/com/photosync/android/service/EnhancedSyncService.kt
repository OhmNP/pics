package com.photosync.android.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.photosync.android.data.AppDatabase
import com.photosync.android.data.SettingsManager
import com.photosync.android.data.entity.SyncStatus
import com.photosync.android.model.SyncProgress
import com.photosync.android.network.TcpSyncClient
import com.photosync.android.repository.MediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
        
        // Reset any stale UPLOADING items to PENDING (e.g. from crash/force-stop)
        serviceScope.launch {
            try {
                mediaRepository.resetStuckUploadingToPending()
                Log.i(TAG, "Reset stuck UPLOADING items to PENDING")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset stuck items", e)
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
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
        val settings = SettingsManager(this)

        // 0. Auto-Sync Check
        if (!manual && !settings.autoSyncEnabled) {
            Log.d(TAG, "[Orchestrator] RequestSync($reason) rejected - Auto-sync disabled")
            return
        }
        
        // 0.5. Paused Check
        if (!manual && _syncState.value is SyncState.Paused) {
            Log.d(TAG, "[Orchestrator] RequestSync($reason) rejected - Sync is Paused")
            return
        }
        
        // 1. Rate Limit: 15s (unless manual)
        if (!manual && (currentTime - lastSyncRequestTime < 15000)) {
            Log.d(TAG, "[Orchestrator] RequestSync($reason) rejected - rate limited")
            return
        }
        
        Log.i(TAG, "[Orchestrator] RequestSync: reason=$reason, manual=$manual")
        
        // 2. Debounce (3s)
        requestDebounceJob.getAndSet(null)?.cancel()
        val job = serviceScope.launch {
            if (reason == "NETWORK_AVAILABLE") {
                 mediaRepository.resumePendingFromNetworkPause()
                 Log.i(TAG, "Resumed PAUSED_NETWORK items")
            }
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
        
        syncJob = serviceScope.launch @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS) {
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

                    // Strict Network Check
                    if (!isNetworkAvailable(settingsManager)) {
                        _serverStatus.value = ServerConnectivityStatus.DISCONNECTED
                        delay(3000)
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
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun pauseSync() {
        _syncState.value = SyncState.Paused
        updateNotification("Sync paused")
    }
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
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
        serviceScope.launch @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS) {
            try {
                performReconciliation()
            } catch (e: Exception) {
                Log.e(TAG, "Reconciliation failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun performSync() {
        // 1. Discover server
        _syncState.value = SyncState.Discovering
        updateNotification("Discovering server...")

        val settingsManager = SettingsManager(this)
        val serverIp = settingsManager.serverIp

        if (serverIp.isBlank()) {
            _syncState.value = SyncState.Error("Server not configured")
            return
        }

        // 2. Initial connection check
        _syncState.value = SyncState.Connecting
        updateNotification("Connecting to server...")
        
        // 3. Scan & Queue (Pipeline Phase 1) - PARALLEL
        // We launch scanning in background, allowing workers to start picking up items immediately.
        val isScanning = AtomicBoolean(true)
        
        val scanningJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // If auto-sync, insert directly as PENDING. Else DISCOVERED.
                val targetStatus = if (settingsManager.autoSyncEnabled) SyncStatus.PENDING else SyncStatus.DISCOVERED
                mediaRepository.scanForNewMedia(targetStatus)
                
                // If NOT auto-sync, we still queue existing DISCOVERED items if configured? 
                // Previously, queueAllDiscovered was conditional on autoSync.
                // If autoSync is ON, scanForNewMedia puts them in PENDING, so queueAllDiscovered is redundant for *new* items.
                // But we should run queueAllDiscovered once to pick up any old DISCOVERED items that were missed?
                if (settingsManager.autoSyncEnabled) {
                     mediaRepository.queueAllDiscovered()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scanning failed", e)
            } finally {
                isScanning.set(false)
                Log.d(TAG, "Scanning finished")
            }
        }
        
        // 4. Upload Loop (Pipeline Phase 2)
        _syncState.value = SyncState.Syncing
        _syncProgress.value = SyncProgress(eligibleCount = 0, isActive = true) 
        
        val workerCount = 5
        val completedCount = AtomicInteger(0)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val userName = settingsManager.userName
        
        try {
            coroutineScope {
                val jobs = List(workerCount) { workerId ->
                    launch(Dispatchers.IO) {
                        Log.d(TAG, "Worker $workerId started")
                        val client = TcpSyncClient(serverIp)
                        activeClients.add(client)
                        var isConnected = false
                        
                        try {
                            // Lazy Connection: We don't connect until we actually have an item to sync.
                            // This prevents "Trying to do a sync" when there is nothing to upload.
                            
                            // Work Loop
                            while (isActive) {
                                // Pull next item (Transactional)
                                var item = mediaRepository.claimNextPendingItem()
                                
                                if (item == null) {
                                    // If scanning is active, wait and retry.
                                    if (isScanning.get()) {
                                        delay(1000)
                                        continue
                                    } else {
                                        // Double check after scanning finished to ensure no race
                                        item = mediaRepository.claimNextPendingItem()
                                        if (item == null) break // Truly done
                                    }
                                }
                                
                                try {
                                    // Check if paused
                                    while (_syncState.value is SyncState.Paused) {
                                        delay(1000)
                                    }

                                    // Lazy Connect
                                    if (!isConnected) {
                                        if (!client.connect()) throw IOException("Connection failed")
                                        val sid = client.startSession(deviceId, "", userName)
                                        if (sid == null) throw IOException("Session failed")
                                        isConnected = true
                                        Log.d(TAG, "Worker $workerId connected lazily")
                                    }

                                    // Upload Logic ... (Same as before)
                                    
                                    val hash = if (item.hash.isEmpty()) {
                                        mediaRepository.calculateHash(item.uri)
                                    } else {
                                        item.hash
                                    }
                                    
                                    // Item is already UPLOADING from claim

                                    // Constraint check ...
                                    if (!checkConstraints(settingsManager)) {
                                         // Pause logic ...
                                         // If we pause, we should keep this item or return to pending? 
                                         // For now, hold.
                                         _syncState.value = SyncState.Paused
                                         while (_syncState.value is SyncState.Paused) delay(5000)
                                         _syncState.value = SyncState.Syncing
                                    }

                                    // V2 / V1 Upload Flow (Copied from previous)
                                    
                                    // Check for Manual Restart Signal
                                    // If uploadId exists but lastKnownOffset is 0, it means we requested a restart (queueManualUpload).
                                    // We should tell server to ABORT the previous session first.
                                    if (!item.uploadId.isNullOrEmpty() && item.lastKnownOffset == 0L) {
                                        Log.i(TAG, "Process explicit restart for ${item.name}: Aborting session ${item.uploadId}")
                                        client.abortUpload(item.uploadId)
                                        // We don't need to clear it locally, as we will get a new one from resumeUpload (which starts new)
                                    }

                                    var initResult = client.resumeUpload(item.name, item.size, hash)
                                    
                                    if (initResult == null) {
                                        // V1 Fallback
                                        if (client.sendPhotoMetadata(item.name, item.size, hash)) {
                                            contentResolver.openInputStream(item.uri)?.use { stream ->
                                                if (client.sendPhotoDataStream(stream)) {
                                                    mediaRepository.markAsSynced(item.id, hash, item.size)
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
                                        
                                        if (currentOffset > 0) {
                                            mediaRepository.updateUploadProgress(item.id, currentOffset, item.size)
                                        }

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
                                                    mediaRepository.updateUploadProgress(item.id, currentOffset, item.size)
                                                } else {
                                                    success = false
                                                }
                                            }
                                            
                                            // Verify we actually read the expected amount of data
                                            if (currentOffset != item.size) {
                                                Log.e(TAG, "Source file truncated: expected ${item.size}, got $currentOffset")
                                                success = false
                                            }

                                            if (success) {
                                                if (client.finishUpload(uploadId, hash)) {
                                                    mediaRepository.markAsSynced(item.id, hash, item.size)
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
                                    
                                    // Move back to PENDING if network error, else FAILED
                                    val status = when (e) {
                                        is java.io.FileNotFoundException -> SyncStatus.FAILED // File gone, don't retry
                                        is IOException -> SyncStatus.PENDING // Retry later
                                        else -> SyncStatus.FAILED
                                    }
                                    val reason = e.message ?: "Unknown error"
                                    
                                    if (status == SyncStatus.PENDING) {
                                         // If PENDING, we just mark it as failed attempt but keep status PENDING (or rely on retry logic)
                                         // Actually, updateStatusWithError increments retry count.
                                         mediaRepository.markAsFailed(item.id, reason, status)
                                    } else {
                                         mediaRepository.markAsFailed(item.id, reason, status)
                                    }

                                    if (status == SyncStatus.PENDING) { // Network issue logic
                                        Log.w(TAG, "Stopping worker $workerId due to network issue")
                                        break
                                    }
                                }
                                completedCount.incrementAndGet()
                            }
                        } catch (e: Exception) {
                             Log.e(TAG, "Worker $workerId fatal", e)
                        } finally {
                            client.disconnect()
                            activeClients.remove(client)
                        }
                    }
                }
                
                // Wait for scanning to finish AND workers to finish
                // Note: Workers finish when queue is empty AND scanning is done.
                // So joinAll() covers both conditions effectively.
                jobs.joinAll()
                scanningJob.join() // Should be done by now, but good to be sure.
            }
            
            _syncState.value = SyncState.Idle
            updateNotification("Sync completed: ${completedCount.get()} files")
            _syncProgress.value = _syncProgress.value.copy(isActive = false)
            
            if (completedCount.get() > 0) {
                settingsManager.lastSuccessfulBackupTimestamp = System.currentTimeMillis()
            }
            
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Sync process error", e)
            _syncState.value = SyncState.Error(e.message ?: "Unknown error")
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun performReconciliation() {
        _syncState.value = SyncState.Reconciling
        updateNotification("Reconciling sync status...")
        
        val settingsManager = SettingsManager(this)
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
            // Initialize all items in DB to stabilize totalBytes denominator
            val statusMap = mediaRepository.getSyncStatusMap()
            mediaRepository.initializeSyncStatus(mediaItems, statusMap)

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
                    requestSync("CONTENT_CHANGE", manual = false)
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
    
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun updateNotification(content: String) {
        // Throttle updates unless "Sync completed"
        if (!content.contains("Sync completed") && !notificationLimiter.tryAcquire()) {
            return
        }
        
        val notification = createNotification(content)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
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
             if (!batteryManager.isCharging) {
                 Log.w(TAG, "Constraint Init Failed: Not Charging")
                 return false
             }
        }
        
        // Battery Threshold (only if not charging)
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        if (!batteryManager.isCharging) {
             val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
             if (level < settings.batteryThreshold) {
                 Log.w(TAG, "Constraint Init Failed: Low Battery ($level < ${settings.batteryThreshold})")
                 return false
             }
        }
        
        if (!isNetworkAvailable(settings)) {
             Log.w(TAG, "Constraint Init Failed: No Network/Wifi")
             return false
        }
        
        return true
    }

    private fun isNetworkAvailable(settings: SettingsManager): Boolean {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false
        }
        
        if (settings.wifiOnly) {
             if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                 return false
             }
        }
        return true
    }
}

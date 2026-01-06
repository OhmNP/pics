package com.photosync.android.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photosync.android.data.AppDatabase
import com.photosync.android.model.SyncProgress
import com.photosync.android.repository.MediaRepository
import com.photosync.android.repository.SyncProgressRepository
import com.photosync.android.service.EnhancedSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    
    private val mediaRepository: MediaRepository
    private val syncProgressRepository: SyncProgressRepository
    private val database: AppDatabase
    
    // Local state that reflects the service state + fallback
    private val _syncState = MutableStateFlow<EnhancedSyncService.SyncState>(EnhancedSyncService.SyncState.Idle)
    val syncState: StateFlow<EnhancedSyncService.SyncState> = _syncState.asStateFlow()

    private val _serviceProgress = MutableStateFlow(SyncProgress())
    
    // Unified Progress Flow from DB
    val syncProgress: StateFlow<SyncProgress> by lazy {
        syncProgressRepository.syncProgressFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncProgress())
    }

    private val _serverStatus = MutableStateFlow(EnhancedSyncService.ServerConnectivityStatus.DISCONNECTED)
    val serverStatus: StateFlow<EnhancedSyncService.ServerConnectivityStatus> = _serverStatus.asStateFlow()
    
    private val settings = com.photosync.android.data.SettingsManager(application)
    private val _userName = MutableStateFlow(settings.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()
    
    private var serviceMonitoringJob: Job? = null

    init {
        database = AppDatabase.getDatabase(application)
        mediaRepository = MediaRepository(application, database)
        syncProgressRepository = SyncProgressRepository(database.syncStatusDao())
        monitorServiceState()
        updateStorageInfo()
    }
    
    private fun monitorServiceState() {
        viewModelScope.launch {
            var currentService: EnhancedSyncService? = null
            var collectionJob: Job? = null
            
            while (true) {
                val service = EnhancedSyncService.getInstance()
                
                if (service != null && service !== currentService) {
                    // Service instance changed or first found
                    currentService = service
                    collectionJob?.cancel()
                    
                    collectionJob = launch {
                        launch { service.syncState.collect { _syncState.value = it } }
                        launch { service.syncProgress.collect { _serviceProgress.value = it } }
                        launch { service.serverStatus.collect { _serverStatus.value = it } }
                    }
                } else if (service == null) {
                    currentService = null
                    collectionJob?.cancel()
                    collectionJob = null
                }
                
                delay(1000)
            }
        }
    }
    
    private val _storageUsage = MutableStateFlow(0f)
    val storageUsage: StateFlow<Float> = _storageUsage.asStateFlow()
    
    private val _storageString = MutableStateFlow("Calculating...")
    val storageString: StateFlow<String> = _storageString.asStateFlow()

    private fun updateStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val storageStatsManager = getApplication<Application>().getSystemService(android.content.Context.STORAGE_STATS_SERVICE) as android.app.usage.StorageStatsManager
                val storageManager = getApplication<Application>().getSystemService(android.content.Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                
                // UUID_DEFAULT works for Internal Storage on most modern Android versions
                val uuid = android.os.storage.StorageManager.UUID_DEFAULT
                
                val totalSpace = storageStatsManager.getTotalBytes(uuid)
                val freeSpace = storageStatsManager.getFreeBytes(uuid)
                val usedSpace = totalSpace - freeSpace
                
                if (totalSpace > 0) {
                    _storageUsage.value = usedSpace.toFloat() / totalSpace.toFloat()
                    
                    // Use Decimal GB (1000^3) to match Android Settings / Box specs
                    val divisor = 1000f * 1000f * 1000f
                    val rawTotalGB = totalSpace / divisor
                    val usedGB = usedSpace / divisor
                    
                    // Round to standard storage sizes (32, 64, 128, 256, 512, 1024)
                    // If simple rounding is close, snap to it.
                    val rawTotalInt = rawTotalGB.toInt()
                    val standardSizes = listOf(32, 64, 128, 256, 512, 1024)
                    val standardTotalGB = standardSizes.firstOrNull { size -> 
                        // Allow ~10% variance for overhead
                        rawTotalGB > (size * 0.9) && rawTotalGB < (size * 1.1)
                    } ?: rawTotalGB // Fallback to raw if weird size

                    _storageString.value = String.format("%.1f / %s GB", usedGB, 
                        if (standardTotalGB is Int) standardTotalGB.toString() else String.format("%.1f", standardTotalGB))
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Failed to get storage info", e)
                _storageString.value = "Unknown"
            }
        }
    }

    private val _lastSuccessfulBackup = MutableStateFlow(settings.lastSuccessfulBackupTimestamp)
    val lastSuccessfulBackup: StateFlow<Long> = _lastSuccessfulBackup.asStateFlow()

    val lastSyncTimeFormatted: StateFlow<String> by lazy {
        mediaRepository.getLastSyncTimeFlow()
            .map { timestamp ->
                if (timestamp != null && timestamp > 0) {
                     val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                     sdf.format(java.util.Date(timestamp))
                } else {
                    "Never"
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Never")
    }
    
    val recentUploads = mediaRepository.getRecentSyncedMedia(10)
    
    fun startSync() {
        val intent = Intent(getApplication(), EnhancedSyncService::class.java).apply {
            action = EnhancedSyncService.ACTION_START_SYNC
        }
        startService(intent)
    }
    
    fun pauseSync() {
        val intent = Intent(getApplication(), EnhancedSyncService::class.java).apply {
            action = EnhancedSyncService.ACTION_PAUSE_SYNC
        }
        startService(intent)
    }
    
    fun resumeSync() {
        val intent = Intent(getApplication(), EnhancedSyncService::class.java).apply {
            action = EnhancedSyncService.ACTION_RESUME_SYNC
        }
        startService(intent)
    }
    
    fun stopSync() {
        val intent = Intent(getApplication(), EnhancedSyncService::class.java).apply {
            action = EnhancedSyncService.ACTION_STOP_SYNC
        }
        startService(intent)
    }
    
    fun reconcile() {
        val intent = Intent(getApplication(), EnhancedSyncService::class.java).apply {
            action = EnhancedSyncService.ACTION_RECONCILE
        }
        startService(intent)
    }
    
    private fun startService(intent: Intent) {
        val context = getApplication<Application>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

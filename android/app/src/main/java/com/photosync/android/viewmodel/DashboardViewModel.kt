package com.photosync.android.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photosync.android.data.AppDatabase
import com.photosync.android.model.SyncProgress
import com.photosync.android.repository.MediaRepository
import com.photosync.android.service.EnhancedSyncService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    
    private val mediaRepository: MediaRepository
    private val database: AppDatabase
    
    // Local state that reflects the service state + fallback
    private val _syncState = MutableStateFlow<EnhancedSyncService.SyncState>(EnhancedSyncService.SyncState.Idle)
    val syncState: StateFlow<EnhancedSyncService.SyncState> = _syncState.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val _serverStatus = MutableStateFlow(EnhancedSyncService.ServerConnectivityStatus.DISCONNECTED)
    val serverStatus: StateFlow<EnhancedSyncService.ServerConnectivityStatus> = _serverStatus.asStateFlow()
    
    private val settings = com.photosync.android.data.SettingsManager(application)
    private val _userName = MutableStateFlow(settings.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()
    
    private var serviceMonitoringJob: Job? = null

    init {
        database = AppDatabase.getDatabase(application)
        mediaRepository = MediaRepository(application.contentResolver, database)
        monitorServiceState()
        // Refresh username when it might change (simplified for now, just load on init)
        // Ideally observe prefs change or reload on resume
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
                        launch { service.syncProgress.collect { _syncProgress.value = it } }
                        launch { service.serverStatus.collect { _serverStatus.value = it } }
                    }
                } else if (service == null) {
                    currentService = null
                    collectionJob?.cancel()
                    collectionJob = null
                    // Optional: reset to default states if service dies?
                    // _serverStatus.value = EnhancedSyncService.ServerConnectivityStatus.DISCONNECTED
                }
                
                delay(1000)
            }
        }
    }
    

    
    val syncedCount = mediaRepository.getSyncedCount()
    val pendingCount = mediaRepository.getPendingCount()
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

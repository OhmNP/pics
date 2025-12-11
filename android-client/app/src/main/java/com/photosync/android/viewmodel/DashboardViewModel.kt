package com.photosync.android.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photosync.android.data.AppDatabase
import com.photosync.android.model.SyncProgress
import com.photosync.android.repository.MediaRepository
import com.photosync.android.service.EnhancedSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    
    private val mediaRepository: MediaRepository
    private val database: AppDatabase
    
    // Fallback if service is not running
    private val _localState = MutableStateFlow<EnhancedSyncService.SyncState>(EnhancedSyncService.SyncState.Idle)
    private val _localProgress = MutableStateFlow(SyncProgress())
    
    init {
        database = AppDatabase.getDatabase(application)
        mediaRepository = MediaRepository(application.contentResolver, database)
    }
    
    // Proxy service state/progress or return local fallback
    val syncState: StateFlow<EnhancedSyncService.SyncState>
        get() = EnhancedSyncService.getInstance()?.syncState ?: _localState.asStateFlow()
        
    val syncProgress: StateFlow<SyncProgress>
        get() = EnhancedSyncService.getInstance()?.syncProgress ?: _localProgress.asStateFlow()
    
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

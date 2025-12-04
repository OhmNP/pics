package com.photosync.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photosync.android.data.ConnectionManager
import com.photosync.android.model.ConnectionStatus
import com.photosync.android.model.SyncHistory
import com.photosync.android.repository.SyncRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val syncRepo = SyncRepository.getInstance(application)
    private val connMgr = ConnectionManager.getInstance()
    
    val connectionStatus: StateFlow<ConnectionStatus> = connMgr.connectionStatus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectionStatus.Disconnected
    )
    
    fun getTotalPhotosSynced(): Int {
        return syncRepo.getTotalPhotosSynced()
    }
    
    fun getLastSyncTime(): Long {
        return syncRepo.getLastSyncTime()
    }
    
    fun getRecentSyncHistory(): List<SyncHistory> {
        return syncRepo.getSyncHistory(limit = 5)
    }
}

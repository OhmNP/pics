package com.photosync.android.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photosync.android.model.SyncHistory
import com.photosync.android.model.SyncProgress
import com.photosync.android.repository.SyncRepository
import com.photosync.android.service.SyncService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val syncRepo = SyncRepository.getInstance(application)
    private val context = application
    
    val syncProgress: StateFlow<SyncProgress> = syncRepo.syncProgress.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncProgress()
    )
    
    fun startSync(serverIp: String, serverPort: Int) {
        val intent = Intent(context, SyncService::class.java).apply {
            putExtra("server_ip", serverIp)
            putExtra("server_port", serverPort)
        }
        context.startService(intent)
    }
    
    fun getSyncHistory(limit: Int = 20): List<SyncHistory> {
        return syncRepo.getSyncHistory(limit)
    }
}

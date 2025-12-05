package com.photosync.android.repository

import android.content.Context
import com.photosync.android.data.LocalDB
import com.photosync.android.model.SyncHistory
import com.photosync.android.model.SyncProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncRepository private constructor(context: Context) {
    private val db = LocalDB(context)
    
    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    
    companion object {
        @Volatile
        private var instance: SyncRepository? = null
        
        fun getInstance(context: Context): SyncRepository {
            return instance ?: synchronized(this) {
                instance ?: SyncRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    fun updateSyncProgress(progress: SyncProgress) {
        _syncProgress.value = progress
    }
    
    fun startSync() {
        _syncProgress.value = SyncProgress(isActive = true)
    }
    
    fun stopSync() {
        _syncProgress.value = SyncProgress(isActive = false)
    }
    
    fun updateCurrentFile(fileName: String, index: Int, total: Int) {
        _syncProgress.value = _syncProgress.value.copy(
            currentFile = fileName,
            currentFileIndex = index,
            totalFiles = total
        )
    }
    
    fun updateBytesTransferred(bytes: Long, total: Long, speed: Float) {
        _syncProgress.value = _syncProgress.value.copy(
            bytesTransferred = bytes,
            totalBytes = total,
            uploadSpeed = speed
        )
    }
    
    fun setSyncError(error: String) {
        _syncProgress.value = _syncProgress.value.copy(
            isActive = false,
            error = error
        )
    }
    
    fun completeSyncSuccess(photosSynced: Int, bytesTransferred: Long, maxModifiedTime: Long? = null) {
        _syncProgress.value = SyncProgress(isActive = false)
        db.addSyncHistory(photosSynced, bytesTransferred, success = true)
        if (maxModifiedTime != null && maxModifiedTime > 0) {
            db.setLastSync(maxModifiedTime)
        }
    }
    
    fun completeSyncError(error: String) {
        _syncProgress.value = SyncProgress(isActive = false, error = error)
        db.addSyncHistory(0, 0, success = false, errorMessage = error)
    }
    
    fun getSyncHistory(limit: Int = 20): List<SyncHistory> {
        return db.getSyncHistory(limit)
    }
    
    fun getLastSyncTime(): Long {
        return db.getLastSync()
    }
    
    fun getTotalPhotosSynced(): Int {
        return db.getTotalPhotosSynced()
    }
    
    fun resetSync() {
        db.setLastSync(0)
    }
}

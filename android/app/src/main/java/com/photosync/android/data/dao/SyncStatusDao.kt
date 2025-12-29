package com.photosync.android.data.dao

import androidx.room.*
import com.photosync.android.data.entity.SyncStatusEntity
import com.photosync.android.data.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStatusDao {
    
    @Query("SELECT * FROM sync_status WHERE mediaId = :mediaId")
    suspend fun getSyncStatus(mediaId: String): SyncStatusEntity?
    
    @Query("SELECT * FROM sync_status WHERE syncStatus = :status")
    fun getSyncStatusByStatus(status: SyncStatus): Flow<List<SyncStatusEntity>>
    
    @Query("SELECT COUNT(*) FROM sync_status WHERE syncStatus = :status")
    suspend fun getCountByStatus(status: SyncStatus): Int
    
    @Query("SELECT COUNT(*) FROM sync_status WHERE syncStatus = 'SYNCED'")
    fun getSyncedCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM sync_status WHERE syncStatus = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_status WHERE syncStatus = 'ERROR'")
    fun getFailedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_status WHERE syncStatus = 'PENDING' OR syncStatus = 'UPLOADING'")
    fun getInProgressCount(): Flow<Int>

    @Query("SELECT * FROM sync_status WHERE syncStatus = 'SYNCED' ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentSynced(limit: Int): Flow<List<SyncStatusEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncStatus(status: SyncStatusEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(statuses: List<SyncStatusEntity>)
    
    @Update
    suspend fun updateSyncStatus(status: SyncStatusEntity)
    
    @Query("UPDATE sync_status SET syncStatus = :newStatus WHERE mediaId = :mediaId")
    suspend fun updateStatus(mediaId: String, newStatus: SyncStatus)
    
    @Query("UPDATE sync_status SET syncStatus = :newStatus WHERE hash IN (:hashes)")
    suspend fun updateStatusByHashes(hashes: List<String>, newStatus: SyncStatus)
    
    @Query("UPDATE sync_status SET retryCount = retryCount + 1, lastAttemptTimestamp = :timestamp, failureReason = :reason, syncStatus = 'ERROR' WHERE mediaId = :mediaId")
    suspend fun incrementRetryError(mediaId: String, timestamp: Long, reason: String)

    @Query("UPDATE sync_status SET retryCount = 0, lastAttemptTimestamp = :timestamp, syncStatus = :newStatus WHERE mediaId = :mediaId")
    suspend fun resetRetryStatus(mediaId: String, timestamp: Long, newStatus: SyncStatus)

    @Query("UPDATE sync_status SET syncStatus = :status, failureReason = :reason WHERE syncStatus = 'UPLOADING'")
    suspend fun pauseUploadingItems(status: SyncStatus, reason: String)
    
    @Query("UPDATE sync_status SET retryCount = retryCount + 1, lastAttemptTimestamp = :timestamp, failureReason = :reason, syncStatus = :status WHERE mediaId = :mediaId")
    suspend fun updateStatusWithError(mediaId: String, timestamp: Long, reason: String, status: SyncStatus)

    
    @Query("DELETE FROM sync_status WHERE mediaId = :mediaId")
    suspend fun deleteSyncStatus(mediaId: String)
    
    @Query("DELETE FROM sync_status")
    suspend fun clearAll()
}

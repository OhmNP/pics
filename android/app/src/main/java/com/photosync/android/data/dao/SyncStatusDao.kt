package com.photosync.android.data.dao

import androidx.room.*
import com.photosync.android.data.entity.SyncStatusEntity
import com.photosync.android.data.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStatusDao {
    
    @Query("SELECT * FROM sync_status WHERE mediaId = :mediaId")
    suspend fun getSyncStatus(mediaId: String): SyncStatusEntity?

    @Query("SELECT * FROM sync_status WHERE mediaId IN (:mediaIds)")
    suspend fun getSyncStatuses(mediaIds: List<String>): List<SyncStatusEntity>

    @Query("SELECT * FROM sync_status")
    suspend fun getAllSyncStatuses(): List<SyncStatusEntity>

    @Query("SELECT * FROM sync_status")
    fun getAllSyncStatusesFlow(): Flow<List<SyncStatusEntity>>
    
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

    @Query("SELECT * FROM sync_status WHERE syncStatus = 'UPLOADING'")
    fun getUploadingItems(): Flow<List<SyncStatusEntity>>

    @Query("SELECT * FROM sync_status WHERE syncStatus = 'SYNCED' ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentSynced(limit: Int): Flow<List<SyncStatusEntity>>
    
    @Query("SELECT MAX(lastUpdated) FROM sync_status WHERE syncStatus = 'SYNCED'")
    fun getLastSuccessfulSyncTime(): Flow<Long?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncStatus(status: SyncStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncStatusList(entities: List<SyncStatusEntity>)
    
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

    @Query("UPDATE sync_status SET lastKnownOffset = :offset, fileSize = :size WHERE mediaId = :mediaId")
    suspend fun updateUploadProgress(mediaId: String, offset: Long, size: Long)

    @Query("""
        SELECT 
            SUM(CASE WHEN syncStatus = 'SYNCED' THEN 1 ELSE 0 END) as syncedCount,
            SUM(CASE WHEN syncStatus = 'PENDING' THEN 1 ELSE 0 END) as pendingCount,
            SUM(CASE WHEN syncStatus = 'UPLOADING' THEN 1 ELSE 0 END) as uploadingCount,
            SUM(CASE WHEN syncStatus = 'FAILED' OR syncStatus = 'ERROR' THEN 1 ELSE 0 END) as failedCount,
            SUM(CASE WHEN syncStatus LIKE 'PAUSED%' THEN 1 ELSE 0 END) as pausedCount,
            SUM(CASE WHEN syncStatus IN ('PENDING', 'UPLOADING', 'SYNCED') THEN fileSize ELSE 0 END) as totalBytes,
            SUM(CASE WHEN syncStatus = 'SYNCED' THEN fileSize WHEN syncStatus = 'UPLOADING' THEN lastKnownOffset ELSE 0 END) as uploadedBytes
        FROM sync_status
    """)
    fun getSyncAggregates(): Flow<SyncStatusAggregate>

    @Query("""
        SELECT 
            SUM(CASE WHEN syncStatus = 'SYNCED' THEN 1 ELSE 0 END) || '_' ||
            SUM(CASE WHEN syncStatus = 'PENDING' OR syncStatus = 'UPLOADING' THEN 1 ELSE 0 END) || '_' ||
            SUM(CASE WHEN syncStatus = 'DISCOVERED' THEN 1 ELSE 0 END) || '_' ||
            COUNT(*)
        FROM sync_status
    """)
    fun getFilterConsistencyToken(): Flow<String>
    
    @Query("SELECT * FROM sync_status WHERE syncStatus = :status LIMIT :limit")
    suspend fun getByStatus(status: SyncStatus, limit: Int): List<SyncStatusEntity>

    @Query("UPDATE sync_status SET syncStatus = 'PENDING', queuedAt = :timestamp WHERE mediaId IN (:mediaIds)")
    suspend fun markAsPending(mediaIds: List<String>, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sync_status SET syncStatus = 'UPLOADING', uploadStartedAt = :timestamp WHERE mediaId = :mediaId")
    suspend fun markAsUploading(mediaId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sync_status SET syncStatus = 'PENDING' WHERE syncStatus = :pausedStatus")
    suspend fun resumePausedItems(pausedStatus: SyncStatus)

    @Query("SELECT * FROM sync_status WHERE syncStatus = 'PENDING' ORDER BY queuedAt ASC LIMIT 1")
    suspend fun getNextPending(): SyncStatusEntity?
    
    @Query("SELECT COUNT(*) FROM sync_status WHERE syncStatus = 'DISCOVERED'")
    fun getDiscoveredCount(): Flow<Int>
    
    @Transaction
    suspend fun claimNextPending(): SyncStatusEntity? {
        val next = getNextPending()
        if (next != null) {
            markAsUploading(next.mediaId)
            return next.copy(syncStatus = SyncStatus.UPLOADING, uploadStartedAt = System.currentTimeMillis())
        }
        return null
    }

    @Query("DELETE FROM sync_status WHERE mediaId = :mediaId")
    suspend fun deleteSyncStatus(mediaId: String)
    
    @Query("DELETE FROM sync_status")
    suspend fun clearAll()
}

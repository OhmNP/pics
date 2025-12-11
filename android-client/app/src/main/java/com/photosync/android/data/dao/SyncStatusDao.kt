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
    
    @Query("DELETE FROM sync_status WHERE mediaId = :mediaId")
    suspend fun deleteSyncStatus(mediaId: String)
    
    @Query("DELETE FROM sync_status")
    suspend fun clearAll()
}

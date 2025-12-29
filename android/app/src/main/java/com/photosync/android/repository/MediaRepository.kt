package com.photosync.android.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.photosync.android.data.AppDatabase
import com.photosync.android.data.entity.SyncStatus
import com.photosync.android.data.entity.SyncStatusEntity
import com.photosync.android.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

import android.content.Context
import com.photosync.android.data.SettingsManager

class MediaRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val contentResolver = context.contentResolver
    private val syncStatusDao = database.syncStatusDao()
    private val settingsManager = SettingsManager(context)
    
    /**
     * Get paged media items with sync status
     */
    /**
     * Get paged media items with sync status
     */
    fun getPagedMediaWithStatus(query: String = ""): Flow<PagingData<MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { MediaPagingSource(contentResolver, syncStatusDao, query) }
        ).flow
    }
    
    /**
     * Get all media items (for sync operations)
     */
    suspend fun getAllMediaItems(): List<MediaItem> {
        val excludedFolders = settingsManager.excludedFolders
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA
        )
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val modified = cursor.getLong(modifiedColumn)
                val path = cursor.getString(dataColumn)
                
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                val syncStatus = syncStatusDao.getSyncStatus(id.toString())
                
                // Exclusion check
                var isExcluded = false
                val relativePath = path.substringAfter("/storage/emulated/0/") // simplified check
                for (excluded in excludedFolders) {
                    if (path.contains("/$excluded/") || relativePath.startsWith(excluded)) {
                        isExcluded = true
                        break
                    }
                }
                
                if (!isExcluded) {
                    items.add(
                        MediaItem(
                            id = id.toString(),
                            uri = contentUri,
                            name = name,
                            size = size,
                            lastModified = modified,
                            path = path,
                            hash = syncStatus?.hash ?: "",
                            syncStatus = syncStatus?.syncStatus ?: SyncStatus.PENDING
                        )
                    )
                }
            }
        }
        
        return items
    }
    
    /**
     * Calculate SHA-256 hash for a media item
     */
    suspend fun calculateHash(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Update sync status for a media item
     */
    suspend fun updateSyncStatus(mediaId: String, hash: String, status: SyncStatus) {
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity(
                mediaId = mediaId,
                hash = hash,
                syncStatus = status
            )
        )
    }
    
    /**
     * Update sync status by hashes (for reconciliation)
     */
    suspend fun updateSyncStatusByHashes(hashes: List<String>) {
        syncStatusDao.updateStatusByHashes(hashes, SyncStatus.SYNCED)
    }
    
    suspend fun markAsFailed(mediaId: String, reason: String, status: SyncStatus = SyncStatus.ERROR) {
        syncStatusDao.updateStatusWithError(mediaId, System.currentTimeMillis(), reason, status)
    }
    
    suspend fun pauseUploadingItems(reason: String) {
        syncStatusDao.pauseUploadingItems(SyncStatus.PAUSED_NETWORK, reason)
    }
    
    suspend fun markAsSynced(mediaId: String, hash: String) {
         // Reset retry count on success
         syncStatusDao.resetRetryStatus(mediaId, System.currentTimeMillis(), SyncStatus.SYNCED)
         // Also ensure hash is updated if it wasn't
         // But resetRetryStatus doesn't update hash. 
         // We might need a proper upsert. For now, let's just insertSyncStatus if we need to set hash,
         // but resetRetryStatus is better for just state update.
         // Actually, let's use insertSyncStatus but with 0 retry count explicitly
         syncStatusDao.insertSyncStatus(
            SyncStatusEntity(
                mediaId = mediaId,
                hash = hash,
                syncStatus = SyncStatus.SYNCED,
                retryCount = 0,
                lastAttemptTimestamp = System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Get synced count
     */
    fun getSyncedCount(): Flow<Int> = syncStatusDao.getSyncedCount()
    
    /**
     * Get pending count
     */
    fun getPendingCount(): Flow<Int> = syncStatusDao.getPendingCount()

    fun getFailedCount(): Flow<Int> = syncStatusDao.getFailedCount()

    fun getInProgressCount(): Flow<Int> = syncStatusDao.getInProgressCount()

    /**
     * Get recently synced media items
     */
    fun getRecentSyncedMedia(limit: Int = 10): Flow<List<MediaItem>> {
        return syncStatusDao.getRecentSynced(limit).map { entities ->
            entities.mapNotNull { entity ->
                // fetch details from MediaStore
                // This is a bit inefficient (N queries), but for small limit (10) it's fine.
                // Optimally we'd do a batch query.
                try {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        entity.mediaId.toLong()
                    )
                    
                    // Simple projection for display
                    val projection = arrayOf(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_MODIFIED
                    )
                    
                    var item: MediaItem? = null
                    
                    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                            val modified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED))
                            
                            item = MediaItem(
                                id = entity.mediaId,
                                uri = uri,
                                name = name,
                                size = 0, // Not needed for thumbnail
                                lastModified = modified,
                                path = "", // Not needed
                                hash = entity.hash,
                                syncStatus = SyncStatus.SYNCED
                            )
                        }
                    }
                    item
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}

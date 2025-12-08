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

class MediaRepository(
    private val contentResolver: ContentResolver,
    private val database: AppDatabase
) {
    
    private val syncStatusDao = database.syncStatusDao()
    
    /**
     * Get paged media items with sync status
     */
    fun getPagedMediaWithStatus(): Flow<PagingData<MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { MediaPagingSource(contentResolver, syncStatusDao) }
        ).flow
    }
    
    /**
     * Get all media items (for sync operations)
     */
    suspend fun getAllMediaItems(): List<MediaItem> {
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
    
    /**
     * Get synced count
     */
    fun getSyncedCount(): Flow<Int> = syncStatusDao.getSyncedCount()
    
    /**
     * Get pending count
     */
    fun getPendingCount(): Flow<Int> = syncStatusDao.getPendingCount()
}

package com.photosync.android.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import com.photosync.android.data.dao.SyncStatusDao
import com.photosync.android.data.entity.SyncStatus
import com.photosync.android.data.entity.SyncStatusEntity
import com.photosync.android.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaScanner(
    private val context: Context,
    private val syncStatusDao: SyncStatusDao
) {
    private val contentResolver = context.contentResolver
    private val settingsManager = SettingsManager(context)

    suspend fun scan(targetStatus: SyncStatus = SyncStatus.DISCOVERED) = withContext(Dispatchers.IO) {
        val excludedFolders = settingsManager.excludedFolders
        val lastScanTime = settingsManager.lastScanTimestamp
        
        val existingIds = syncStatusDao.getAllSyncStatuses().map { it.mediaId }.toSet()
        val newEntities = mutableListOf<SyncStatusEntity>()
        
        // If DB is empty, force full scan (ignore timestamp)
        // This handles cases where DB is wiped (migration) but Prefs persist.
        var maxModifiedTime = if (existingIds.isEmpty()) 0L else lastScanTime
        
        // Define selection for incremental scan
        val selection = "${MediaStore.Images.Media.DATE_MODIFIED} > ?"
        val selectionArgs = arrayOf((maxModifiedTime / 1000).toString()) // MediaStore uses seconds
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_MODIFIED} ASC" // Ascending to track progress safely
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol).toString()
                val modified = cursor.getLong(dateCol) * 1000 // Convert to ms
                
                if (modified > maxModifiedTime) {
                    maxModifiedTime = modified
                }
                
                if (existingIds.contains(id)) continue
                
                val path = cursor.getString(pathCol) ?: ""
                
                // Exclusion check
                var isExcluded = false
                val relativePath = path.substringAfter("/storage/emulated/0/")
                for (excluded in excludedFolders) {
                    if (path.contains("/$excluded/") || relativePath.startsWith(excluded)) {
                        isExcluded = true
                        break
                    }
                }
                
                if (!isExcluded) {
                    newEntities.add(
                        SyncStatusEntity(
                            mediaId = id,
                            hash = "", // Calculated later
                            syncStatus = targetStatus,
                            fileSize = cursor.getLong(sizeCol),
                            lastUpdated = System.currentTimeMillis(),
                            firstSeenAt = System.currentTimeMillis(),
                            queuedAt = if (targetStatus == SyncStatus.PENDING) System.currentTimeMillis() else 0
                        )
                    )
                    
                    // Batch insert every 50 items
                    if (newEntities.size >= 50) {
                        syncStatusDao.insertAll(newEntities)
                        newEntities.clear()
                    }
                }
            }
        }
        
        if (newEntities.isNotEmpty()) {
             syncStatusDao.insertAll(newEntities)
        }
        
        // Update scan time only if we successfully processed
        settingsManager.lastScanTimestamp = maxModifiedTime
    }
}

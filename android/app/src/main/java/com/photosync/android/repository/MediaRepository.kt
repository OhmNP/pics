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
import com.photosync.android.ui.gallery.GalleryFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.security.MessageDigest

import android.content.Context
import com.photosync.android.data.SettingsManager
import com.photosync.android.model.GalleryStats

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
    fun getPagedMediaWithStatus(query: String = "", filter: GalleryFilter = GalleryFilter.DISCOVERED): Flow<PagingData<MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { 
                var failedIds: List<String>? = null
                if (filter == GalleryFilter.FAILED) {
                    try {
                        val potentialIds = kotlinx.coroutines.runBlocking {
                            syncStatusDao.getByStatus(com.photosync.android.data.entity.SyncStatus.FAILED, 1000)
                                .map { it.mediaId } + 
                            syncStatusDao.getByStatus(com.photosync.android.data.entity.SyncStatus.ERROR, 1000)
                                .map { it.mediaId }
                        }
                        
                        // Verify existence in MediaStore to prune orphans (deleted files)
                        if (potentialIds.isNotEmpty()) {
                            val verifiedIds = mutableListOf<String>()
                            val orphanedIds = mutableListOf<String>()
                            
                            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            val projection = arrayOf(MediaStore.Images.Media._ID)
                            // Batch check: "ID IN (...)"
                            // Split into chunks if needed, but 1000 is okay-ish for SQLite limit (usually 999 params).
                            // Let's do a safe loop check or just query all and intersect.
                            // Querying ALL is safest if list is small.
                             val idStr = potentialIds.joinToString(",") { "?" }
                             val selection = "${MediaStore.Images.Media._ID} IN ($idStr)"
                             val args = potentialIds.toTypedArray()
                             
                             contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                                 val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                                 while (cursor.moveToNext()) {
                                     verifiedIds.add(cursor.getString(idCol))
                                 }
                             }
                             
                             // Calculate orphans
                             orphanedIds.addAll(potentialIds.filter { !verifiedIds.contains(it) })
                             
                             if (orphanedIds.isNotEmpty()) {
                                 // Cleanup orphans
                                 kotlinx.coroutines.runBlocking {
                                     // We can't batch delete easily by ID list in DAO yet? 
                                     // Standard Room DELETE FROM x WHERE id IN (list)
                                     // For now, iterate loop delete is fine for corner case.
                                     orphanedIds.forEach { syncStatusDao.deleteSyncStatus(it) }
                                 }
                             }
                             
                             failedIds = verifiedIds
                        } else {
                            failedIds = emptyList()
                        }
                    } catch (e: Exception) {
                        failedIds = emptyList()
                    }
                }
                
                MediaPagingSource(contentResolver, syncStatusDao, query, failedIds, filter) 
            }
        ).flow
    }

    /**
     * Get flow of sync status aggregates that affect filter consistency
     */
    fun getFilterConsistencyFlow(): Flow<String> = syncStatusDao.getFilterConsistencyToken()
    
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
                            lastModified = modified * 1000L,
                            path = path,
                            hash = syncStatus?.hash ?: "",
                            syncStatus = syncStatus?.syncStatus
                        )
                    )
                }
            }
        }
        
        return items
    }
    
    /**
     * Get specific media item by ID
     */
    suspend fun getMediaItem(id: String): MediaItem? {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toLong())
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATA
        )
        
        return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                val modified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                val status = syncStatusDao.getSyncStatus(id)
                
                MediaItem(
                    id = id,
                    uri = uri,
                    name = name,
                    size = size,
                    lastModified = modified * 1000L,
                    path = path,
                    hash = status?.hash ?: "",
                    syncStatus = status?.syncStatus ?: SyncStatus.DISCOVERED
                )
            } else null
        }
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
    suspend fun updateSyncStatus(mediaId: String, hash: String, status: SyncStatus, size: Long = 0) {
        val existing = syncStatusDao.getSyncStatus(mediaId)
        syncStatusDao.insertSyncStatus(
            SyncStatusEntity(
                mediaId = mediaId,
                hash = hash,
                syncStatus = status,
                fileSize = if (size > 0) size else existing?.fileSize ?: 0,
                lastKnownOffset = existing?.lastKnownOffset ?: 0
            )
        )
    }

    suspend fun updateUploadProgress(mediaId: String, offset: Long, size: Long) {
        syncStatusDao.updateUploadProgress(mediaId, offset, size)
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
    
    suspend fun markAsSynced(mediaId: String, hash: String, size: Long = 0) {
         // Reset retry count on success
         val existing = syncStatusDao.getSyncStatus(mediaId)
         syncStatusDao.insertSyncStatus(
            SyncStatusEntity(
                mediaId = mediaId,
                hash = hash,
                syncStatus = SyncStatus.SYNCED,
                fileSize = if (size > 0) size else existing?.fileSize ?: 0,
                retryCount = 0,
                lastAttemptTimestamp = System.currentTimeMillis()
            )
        )
    }
    
    suspend fun getSyncStatusMap(): Map<String, SyncStatus> {
        return syncStatusDao.getAllSyncStatuses().associate { entity -> entity.mediaId to entity.syncStatus }
    }

    fun getSyncStatusMapFlow(): Flow<Map<String, SyncStatus>> {
        return syncStatusDao.getAllSyncStatusesFlow()
            .map { list -> list.associate { it.mediaId to it.syncStatus } }
            .distinctUntilChanged()
    }

    private val mediaScanner = MediaScanner(context, syncStatusDao)

    /**
     * Run full scan for new media
     */
    suspend fun scanForNewMedia(targetStatus: SyncStatus = SyncStatus.DISCOVERED) {
        mediaScanner.scan(targetStatus)
    }
    
    /**
     * Queue all discovered items for upload (DISCOVERED -> PENDING)
     */
    suspend fun queueAllDiscovered() {
        val discoveredItems = syncStatusDao.getByStatus(SyncStatus.DISCOVERED, 1000) // Batch of 1000
        if (discoveredItems.isNotEmpty()) {
            val ids = discoveredItems.map { it.mediaId }
            syncStatusDao.markAsPending(ids)
            // Recursively queue more if needed, or rely on next service loop
        }
    }
    
    /**
     * Claim next pending item for upload (PENDING -> UPLOADING)
     */
    suspend fun claimNextPendingItem(): MediaItem? {
        val entity = syncStatusDao.claimNextPending() ?: return null
        
        // Convert to MediaItem
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                entity.mediaId.toLong()
            )
            // Need to fetch metadata (name, size) since Entity might be stale or minimal
            // Optimization: Entity has fileSize, but not Name. 
            // We should fetch from MediaStore to be safe.
             val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA
            )
             var name = "Unknown"
             var path = ""
             contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                 if (cursor.moveToFirst()) {
                     name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "Unknown"
                     path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)) ?: ""
                 }
             }
             
             return MediaItem(
                 id = entity.mediaId,
                 uri = uri,
                 name = name,
                 size = entity.fileSize,
                 lastModified = entity.lastUpdated,
                 path = path,
                 hash = entity.hash,
                 syncStatus = SyncStatus.UPLOADING,
                 lastKnownOffset = entity.lastKnownOffset,
                 uploadId = entity.uploadId
             )
        } catch (e: Exception) {
            // If MediaStore lookup fails (item deleted?), mark as FAILED
            syncStatusDao.updateStatusWithError(entity.mediaId, System.currentTimeMillis(), "MediaStore lookup failed", SyncStatus.FAILED)
            return null
        }
    }

    suspend fun initializeSyncStatus(items: List<MediaItem>, statusMap: Map<String, SyncStatus>) {
         // Legacy: This was used to bulk-insert PENDING status.
         // Now we should prefer scanning. 
         // But for backward compatibility or reset, strictly insert if missing.
         // Implemented via MediaScanner loop usually.
         // We'll leave this as a "Soft Scan" or "Import" function.
    }

    /**
     * Get flow of all sync statuses (for aggregation)
     */
    fun getAllSyncStatusesFlow(): Flow<List<SyncStatusEntity>> {
        return syncStatusDao.getAllSyncStatusesFlow()
    }



    /**
     * Get snapshot of gallery stats
     */
    suspend fun getSyncStats(): GalleryStats {
        val all = syncStatusDao.getAllSyncStatuses()
        // Note: SyncStatus table only tracks items that have been interacted with (uploaded, pending, etc)
        // It does NOT track all "Discovered" items in MediaStore if they haven't been queued yet.
        // So "Total Items" might be misleading if based only on DB. 
        // Ideally we query MediaStore count, but that's expensive to do constantly.
        // For now, we will report stats on "Tracked Items". 
        // Or we can say "Pending: X, Uploading: Y, Failed: Z" and ignore Total.
        
        return GalleryStats(
            totalItems = all.size,
            syncedItems = all.count { it.syncStatus == SyncStatus.SYNCED },
            pendingItems = all.count { it.syncStatus == SyncStatus.PENDING },
            uploadingItems = all.count { it.syncStatus == SyncStatus.UPLOADING },
            failedItems = all.count { it.syncStatus == SyncStatus.FAILED || it.syncStatus == SyncStatus.ERROR }
        )
    }

    fun getSyncStatsFlow(): Flow<GalleryStats> {
        return syncStatusDao.getSyncAggregates().map { agg ->
            GalleryStats(
                totalItems = agg.syncedCount + agg.pendingCount + agg.uploadingCount + agg.failedCount, // Approximation
                syncedItems = agg.syncedCount,
                pendingItems = agg.pendingCount,
                uploadingItems = agg.uploadingCount,
                failedItems = agg.failedCount
            )
        }.distinctUntilChanged()
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
     * Get real-time upload progress for active items
     * Returns a map of MediaID -> Progress (0.0 - 1.0)
     */
    fun getUploadingProgress(): Flow<Map<String, Float>> {
        return syncStatusDao.getUploadingItems().map { items ->
            items.associate { entity ->
                val progress = if (entity.fileSize > 0) {
                    (entity.lastKnownOffset.toFloat() / entity.fileSize.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                entity.mediaId to progress
            }
        }
    }

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
                                lastModified = modified * 1000L,
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

    /**
     * Queue selected media items for manual upload
     */
    suspend fun queueManualUpload(mediaIds: List<String>): Int {
        var queuedCount = 0
        for (id in mediaIds) {
            try {
                // Check existing status first
                val existing = syncStatusDao.getSyncStatus(id)
                if (existing?.syncStatus == SyncStatus.SYNCED) {
                    continue // Skip already synced items
                }

                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toLong()
                )
                
                val hash = if (existing?.hash.isNullOrEmpty()) {
                    calculateHash(uri)
                } else {
                    existing!!.hash
                }
                
                // Ensure we have the correct file size
                var fileSize = existing?.fileSize ?: 0L
                if (fileSize <= 0) {
                     try {
                         context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                             fileSize = pfd.statSize
                         }
                     } catch (e: Exception) {
                         android.util.Log.e("MediaRepository", "Failed to get size for $uri", e)
                     }
                }

                syncStatusDao.insertSyncStatus(
                    SyncStatusEntity(
                        mediaId = id,
                        hash = hash,
                        syncStatus = SyncStatus.PENDING,
                        fileSize = fileSize,
                        uploadId = existing?.uploadId,
                        lastKnownOffset = 0, // Force restart signal
                        retryCount = 0,
                        lastAttemptTimestamp = System.currentTimeMillis(),
                        queuedAt = System.currentTimeMillis()
                    )
                )
                queuedCount++
            } catch (e: Exception) {
                android.util.Log.e("MediaRepository", "Failed to queue $id", e)
            }
        }
        // Trigger Sync via Service
        val intent = android.content.Intent(context, com.photosync.android.service.EnhancedSyncService::class.java).apply {
            action = com.photosync.android.service.EnhancedSyncService.ACTION_START_SYNC
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        return queuedCount
    }
    
    fun getLastSyncTimeFlow(): Flow<Long?> = syncStatusDao.getLastSuccessfulSyncTime()

    suspend fun resumePendingFromNetworkPause() {
        syncStatusDao.resumePausedItems(SyncStatus.PAUSED_NETWORK)
    }
    
    suspend fun resetStuckUploadingToPending() {
        syncStatusDao.pauseUploadingItems(SyncStatus.PENDING, "Service Restart")
    }
}


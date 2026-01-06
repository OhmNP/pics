package com.photosync.android.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.photosync.android.data.dao.SyncStatusDao
import com.photosync.android.data.entity.SyncStatus
import com.photosync.android.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.photosync.android.ui.gallery.GalleryFilter

class MediaPagingSource(
    private val contentResolver: ContentResolver,
    private val syncStatusDao: SyncStatusDao,
    private val searchQuery: String = "",
    private val includeIds: List<String>? = null, // For strict filtering (e.g. Failed items)
    private val filter: GalleryFilter = GalleryFilter.DISCOVERED
) : PagingSource<Int, MediaItem>() {
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        return try {
            withContext(Dispatchers.IO) {
                // If includeIds is empty string list (not null but empty), return empty immediately
                if (includeIds != null && includeIds.isEmpty()) {
                    return@withContext LoadResult.Page(emptyList(), null, null)
                }

                // The key is the MediaStore offset
                val currentOffset = params.key ?: 0
                val requestedLoadSize = params.loadSize
                
                val items = mutableListOf<MediaItem>()
                
                // For "All Photos" (default), we don't need the complex sparse scanning loop.
                // We just query the requested size + map statuses.
                // If filter is active (rare), we might get fewer items than requested, but Paging 3 handles that fine.
                // We will simplify to just one query batch.
                
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.DATA
                )
                
                val selectionBuilder = StringBuilder()
                val selectionArgsList = mutableListOf<String>()

                if (searchQuery.isNotEmpty()) {
                    selectionBuilder.append("${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?")
                    selectionArgsList.add("%$searchQuery%")
                }
                
                if (includeIds != null) {
                    if (selectionBuilder.isNotEmpty()) selectionBuilder.append(" AND ")
                    // Construct IN clause: _ID IN (?,?,?)
                    // MediaStore might have limits on args, but for "Failed" items it's usually small.
                    // If very large, we might crash. But failures shouldn't be 1000s.
                    selectionBuilder.append("${MediaStore.Images.Media._ID} IN (${includeIds.joinToString(",") { "?" }})")
                    selectionArgsList.addAll(includeIds)
                }

                val selection = if (selectionBuilder.isNotEmpty()) selectionBuilder.toString() else null
                val selectionArgs = if (selectionArgsList.isNotEmpty()) selectionArgsList.toTypedArray() else null

                
                var nextOffset: Int? = null
                
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    // Move to current offset
                    if (currentOffset > 0) {
                        // Note: For large offsets, moveToPosition can be slow. 
                        // But for MediaStore without limit/offset clause support in older APIs, this is standard.
                        // Android Q+ has explicit bundle args for LIMIT/OFFSET, but we stick to compat for now.
                        cursor.moveToPosition(currentOffset - 1)
                    }
                    
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    
                    var count = 0
                    while (cursor.moveToNext() && count < requestedLoadSize) {
                        val id = cursor.getLong(idColumn).toString()
                         // Basic integrity check
                        if (id.isNotBlank()) {
                            val name = cursor.getString(nameColumn) ?: "Unknown"
                            val size = cursor.getLong(sizeColumn)
                            val modified = cursor.getLong(modifiedColumn)
                            val path = cursor.getString(dataColumn) ?: ""
                            
                            val contentUri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id.toLong()
                            )
                            
                            // Check Local DB Status
                            // Note: doing this one-by-by inside loop is N queries. 
                            // Optimization: Collect all IDs first, then batch query?
                            // Yes, let's collect temp items first.
                             items.add(MediaItem(
                                id = id,
                                uri = contentUri,
                                name = name,
                                size = size,
                                lastModified = modified,
                                path = path,
                                hash = "", // Filled later
                                syncStatus = SyncStatus.DISCOVERED // Default, updated later
                            ))
                            count++
                        }
                    }
                    
                    if (!cursor.isAfterLast && count == requestedLoadSize) {
                        nextOffset = currentOffset + count
                    }
                }
                
                // Batch hydration of SyncStatus
                if (items.isNotEmpty()) {
                    val ids = items.map { it.id }
                    val statuses = syncStatusDao.getSyncStatuses(ids).associateBy { it.mediaId }
                    
                    val iterator = items.listIterator()
                    while (iterator.hasNext()) {
                        val item = iterator.next()
                        val statusEntity = statuses[item.id]
                        val status = statusEntity?.syncStatus ?: SyncStatus.DISCOVERED
                        
                        // Strict Filtering (If applicable)
                        // With Unified Gallery, 'filter' is likely DISCOVERED (All) or specific.
                        // If we truly want to support 'Show Only Unsynced', we filter here.
                        
                        val passesFilter = when (filter) {
                            GalleryFilter.DISCOVERED -> true // "All" in new paradigm
                            GalleryFilter.PENDING -> status == SyncStatus.PENDING
                            GalleryFilter.UPLOADING -> status == SyncStatus.UPLOADING
                            GalleryFilter.SYNCED -> status == SyncStatus.SYNCED
                            GalleryFilter.SYNCED -> status == SyncStatus.SYNCED
                            GalleryFilter.FAILED -> status == SyncStatus.FAILED || status == SyncStatus.ERROR || status == SyncStatus.PAUSED || status == SyncStatus.PAUSED_NETWORK
                        }
                        
                        if (passesFilter) {
                            val newItem = item.copy(
                                hash = statusEntity?.hash ?: "",
                                syncStatus = status
                            )
                            iterator.set(newItem)
                        } else {
                            iterator.remove()
                        }
                    }
                }

                LoadResult.Page(
                    data = items,
                    prevKey = if (currentOffset == 0) null else maxOf(0, currentOffset - requestedLoadSize),
                    nextKey = nextOffset
                )
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        // Try to find the page key of the closest item to anchorPosition, from
        // either the prevKey or the nextKey, but you need to handle nullability
        // here:
        //  * prevKey == null -> anchorPage is the first page.
        //  * nextKey == null -> anchorPage is the last page.
        //  * both prevKey and nextKey null -> anchorPage is the initial page, so
        //    just return null.
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    private data class TempMediaItem(
        val id: String,
        val uri: android.net.Uri,
        val name: String,
        val size: Long,
        val lastModified: Long,
        val path: String
    )
}

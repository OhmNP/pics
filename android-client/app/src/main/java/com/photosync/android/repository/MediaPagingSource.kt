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

class MediaPagingSource(
    private val contentResolver: ContentResolver,
    private val syncStatusDao: SyncStatusDao
) : PagingSource<Int, MediaItem>() {
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        return try {
            withContext(Dispatchers.IO) {
                val offset = params.key ?: 0
                val limit = params.loadSize
                
                val items = mutableListOf<MediaItem>()
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.DATA
                )
                
                // Query without LIMIT/OFFSET in the sort order (not supported on all Android versions)
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    // Skip to offset
                    if (offset > 0 && cursor.moveToPosition(offset - 1)) {
                        // Continue from offset
                    } else if (offset == 0) {
                        // Start from beginning
                    } else {
                        // Offset is beyond available items
                        return@use
                    }
                    
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    
                    var count = 0
                    while (cursor.moveToNext() && count < limit) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Unknown"
                        val size = cursor.getLong(sizeColumn)
                        val modified = cursor.getLong(modifiedColumn)
                        val path = cursor.getString(dataColumn) ?: ""
                        
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        
                        // Get sync status (this is a suspend function, so it's fine here)
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
                        count++
                    }
                }
                
                LoadResult.Page(
                    data = items,
                    prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    nextKey = if (items.size < limit) null else offset + limit
                )
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
    
    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(state.config.pageSize)
                ?: anchorPage?.nextKey?.minus(state.config.pageSize)
        }
    }
}

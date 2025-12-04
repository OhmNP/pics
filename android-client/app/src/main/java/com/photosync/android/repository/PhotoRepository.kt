package com.photosync.android.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.photosync.android.data.PhotoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoRepository private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var instance: PhotoRepository? = null
        
        fun getInstance(context: Context): PhotoRepository {
            return instance ?: synchronized(this) {
                instance ?: PhotoRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    suspend fun getAllPhotos(limit: Int = 100, offset: Int = 0): List<PhotoMeta> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoMeta>()
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        
        // Android ContentResolver doesn't support LIMIT/OFFSET in sortOrder
        // We'll sort by date descending and manually handle pagination
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            
            // Skip to offset position
            if (offset > 0 && cursor.moveToPosition(offset - 1)) {
                // Position is now at offset - 1, next moveToNext will be at offset
            }
            
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val size = cursor.getLong(sizeCol)
                val modified = cursor.getLong(modCol)
                val uri = ContentUris.withAppendedId(collection, id)
                
                photos.add(PhotoMeta(uri, name, size, modified, ""))
                count++
            }
        }
        
        photos
    }
    
    suspend fun getPhotoCount(): Int = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            null
        )?.use { cursor ->
            cursor.count
        } ?: 0
    }
}

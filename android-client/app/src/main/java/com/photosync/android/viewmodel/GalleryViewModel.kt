package com.photosync.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.paging.PagingData
import com.photosync.android.data.AppDatabase
import com.photosync.android.model.MediaItem
import com.photosync.android.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val mediaRepository = MediaRepository(application.contentResolver, database)
    
    val pagedMedia: Flow<PagingData<MediaItem>> = mediaRepository.getPagedMediaWithStatus()
}

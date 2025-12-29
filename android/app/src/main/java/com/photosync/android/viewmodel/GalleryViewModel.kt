package com.photosync.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.paging.PagingData
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.photosync.android.data.AppDatabase
import com.photosync.android.model.MediaItem
import com.photosync.android.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val mediaRepository = MediaRepository(application, database)
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedMedia: Flow<PagingData<MediaItem>> = _searchQuery
        .flatMapLatest { query ->
            mediaRepository.getPagedMediaWithStatus(query)
        }
        .cachedIn(viewModelScope)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}

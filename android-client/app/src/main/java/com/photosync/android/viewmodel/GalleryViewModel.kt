package com.photosync.android.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photosync.android.data.PhotoMeta
import com.photosync.android.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GalleryUiState(
    val photos: List<PhotoMeta> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val totalCount: Int = 0
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val photoRepo = PhotoRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()
    
    init {
        loadPhotos()
    }
    
    fun loadPhotos(offset: Int = 0, limit: Int = 100) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val photos = photoRepo.getAllPhotos(limit, offset)
                val totalCount = photoRepo.getPhotoCount()
                _uiState.value = GalleryUiState(
                    photos = photos,
                    isLoading = false,
                    totalCount = totalCount
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load photos"
                )
            }
        }
    }
    
    fun refresh() {
        loadPhotos()
    }
}

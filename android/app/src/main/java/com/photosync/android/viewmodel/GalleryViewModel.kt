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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import android.widget.Toast
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.paging.insertSeparators
import com.photosync.android.data.entity.SyncStatus
import com.photosync.android.ui.gallery.GalleryFilter
import com.photosync.android.model.GalleryStats

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val mediaRepository = MediaRepository(application, database)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState = _uiState.asStateFlow()

    private val settingsManager = com.photosync.android.data.SettingsManager(application)

    // Aggregated trigger for status changes
    // Uses a single DB query that creates a 'token' based on counts of various states.
    private val syncTrigger = mediaRepository.getFilterConsistencyFlow()
        .distinctUntilChanged()

    // Internal flow for Paging parameters
    // Restart Pager if Query, Filter, or Sync Status counts change.
    // Internal flow for Paging parameters
    // Only restart Pager if Query or Filter changes (Structural change)
    private val pagingParams = combine(
        _uiState.map { it.searchQuery }.distinctUntilChanged(),
        _uiState.map { it.filter }.distinctUntilChanged()
    ) { query, filter ->
        query to filter
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val pagedMedia: Flow<PagingData<MediaItem>> = pagingParams
        .flatMapLatest { (query, filter) ->
            mediaRepository.getPagedMediaWithStatus(query, filter)
        }
        .cachedIn(viewModelScope)

        .cachedIn(viewModelScope)
        
    // Optimized: We no longer trigger Pager refreshes for status updates.
    // Instead, we expose a separate map that the UI observes to update icons.
    val syncStatusMap: StateFlow<Map<String, SyncStatus>> = mediaRepository.getSyncStatusMapFlow()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyMap())

    val uploadProgress: Flow<Map<String, Float>> = mediaRepository.getUploadingProgress()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyMap())

    fun getItemProgressFlow(itemId: String): Flow<Float?> {
        return uploadProgress
            .map { it[itemId] }
            .distinctUntilChanged()
    }



    // Aggregated Stats for Banner
    // Aggregated Stats for Banner
    val galleryStats: Flow<GalleryStats> = mediaRepository.getSyncStatsFlow()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), GalleryStats())

    init {
        // Hydrate initial settings
        refreshSettings()
        
        // Removed the "finished items" observer loop because in Unified Gallery,
        // items don't "exit" the view when synced; they just change icon.
        // So we don't need the _exitingItems animation logic for that purpose anymore.
    }

    fun refreshSettings() {
        _uiState.update { it.copy(autoSyncEnabled = settingsManager.autoSyncEnabled) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
    
    // Filter changed -> Now just toggles "Show only unsynced" if we implement that
    // For now, we stick to "All" (DISCOVERED in old enum terms)
    
    fun showFailedItems() {
        _uiState.update { it.copy(filter = GalleryFilter.FAILED, searchQuery = "") }
    }

    fun clearFilter() {
        _uiState.update { it.copy(filter = GalleryFilter.DISCOVERED, searchQuery = "") }
    }
    
    fun toggleSelectionMode() {
        _uiState.update { state -> 
            val newMode = !state.isSelectionMode
            state.copy(
                isSelectionMode = newMode,
                selectedIds = if (!newMode) emptySet() else state.selectedIds
            )
        }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val current = state.selectedIds
            val newSelection = if (current.contains(id)) current - id else current + id
            state.copy(selectedIds = newSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }
    }

    fun uploadSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                val count = mediaRepository.queueManualUpload(ids)
                if (count > 0) {
                    Toast.makeText(getApplication(), "Queued $count items", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(getApplication(), "Selected items already backed up", Toast.LENGTH_SHORT).show()
                }
                clearSelection()
            }
        }
    }
}

data class GalleryUiState(
    val searchQuery: String = "",
    // val filter: GalleryFilter = GalleryFilter.DISCOVERED, // Removed, effectively always ALL
    // We keep 'filter' in PagingSource for now, but we just pass DISCOVERED (All)
    val filter: GalleryFilter = GalleryFilter.DISCOVERED, 
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val autoSyncEnabled: Boolean = false
)



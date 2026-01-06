package com.photosync.android.model

data class GalleryStats(
    val totalItems: Int = 0,
    val syncedItems: Int = 0,
    val pendingItems: Int = 0,
    val uploadingItems: Int = 0,
    val failedItems: Int = 0
)

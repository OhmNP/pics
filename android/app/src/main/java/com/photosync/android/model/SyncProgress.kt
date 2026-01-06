package com.photosync.android.model

data class SyncProgress(
    val eligibleCount: Int = 0,
    val syncedCount: Int = 0,
    val failedCount: Int = 0,
    val uploadingCount: Int = 0,
    val pendingCount: Int = 0,
    val pausedCount: Int = 0,
    val uploadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val percent: Int = 0,
    val lastUpdatedMs: Long = System.currentTimeMillis(),
    
    // Transient UI-specific fields (optional, keeping for backward compatibility in UI)
    val isActive: Boolean = false,
    val currentFile: String = "",
    val uploadSpeed: Float = 0f, // bytes per second
    val error: String? = null
)

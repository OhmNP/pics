package com.photosync.android.data.dao

data class SyncStatusAggregate(
    val syncedCount: Int,
    val pendingCount: Int,
    val uploadingCount: Int,
    val failedCount: Int,
    val pausedCount: Int,
    val totalBytes: Long,
    val uploadedBytes: Long
)

package com.photosync.android.model

data class SyncHistory(
    val id: Long,
    val timestamp: Long,
    val photosSynced: Int,
    val bytesTransferred: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

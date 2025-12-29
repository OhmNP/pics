package com.photosync.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_status")
data class SyncStatusEntity(
    @PrimaryKey
    val mediaId: String,  // MediaStore ID or content URI
    val hash: String,
    val syncStatus: SyncStatus,
    val fileSize: Long = 0,
    val uploadId: String? = null,
    val lastKnownOffset: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastAttemptTimestamp: Long = 0,
    val failureReason: String? = null
)

enum class SyncStatus {
    PENDING,
    UPLOADING, // Active transfer
    SYNCED,
    FAILED,    // Fatal or exhausted retries
    PAUSED,            // Constraints not met
    PAUSED_NETWORK,    // Waiting for network
    ERROR              // Generic error state
}

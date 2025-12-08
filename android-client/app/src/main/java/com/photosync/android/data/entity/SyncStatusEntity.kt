package com.photosync.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_status")
data class SyncStatusEntity(
    @PrimaryKey
    val mediaId: String,  // MediaStore ID or content URI
    val hash: String,
    val syncStatus: SyncStatus,
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    ERROR
}

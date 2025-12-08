package com.photosync.android.model

import android.net.Uri
import com.photosync.android.data.entity.SyncStatus

data class MediaItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val path: String,
    val hash: String,
    val syncStatus: SyncStatus
)

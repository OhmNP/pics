package com.photosync.android.model

data class SyncProgress(
    val isActive: Boolean = false,
    val currentFile: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val uploadSpeed: Float = 0f, // bytes per second
    val error: String? = null
) {
    val progressPercentage: Float
        get() = if (totalFiles > 0) (currentFileIndex.toFloat() / totalFiles) * 100 else 0f
    
    val estimatedTimeRemaining: Long
        get() = if (uploadSpeed > 0) {
            ((totalBytes - bytesTransferred) / uploadSpeed).toLong()
        } else 0L
}

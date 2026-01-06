package com.photosync.android.repository

import com.photosync.android.data.dao.SyncStatusDao
import com.photosync.android.model.SyncProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SyncProgressRepository(private val syncStatusDao: SyncStatusDao) {

    val syncProgressFlow: Flow<SyncProgress> = syncStatusDao.getSyncAggregates().map { aggregate ->
        val totalCount = aggregate.syncedCount + aggregate.pendingCount + aggregate.uploadingCount + aggregate.failedCount + aggregate.pausedCount
        
        val progressPercent = if (aggregate.totalBytes > 0) {
            ((aggregate.uploadedBytes.toDouble() / aggregate.totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else if (totalCount > 0 && aggregate.syncedCount == totalCount) {
             100
        } else {
            0
        }

        SyncProgress(
            eligibleCount = totalCount,
            syncedCount = aggregate.syncedCount,
            failedCount = aggregate.failedCount,
            uploadingCount = aggregate.uploadingCount,
            pendingCount = aggregate.pendingCount,
            pausedCount = aggregate.pausedCount,
            uploadedBytes = aggregate.uploadedBytes,
            totalBytes = aggregate.totalBytes,
            percent = progressPercent,
            lastUpdatedMs = System.currentTimeMillis()
        )
    }
}

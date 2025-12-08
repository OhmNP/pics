package com.photosync.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photosync.android.data.entity.SyncStatus

@Composable
fun SyncStatusIcon(
    status: SyncStatus,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            SyncStatus.SYNCED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Synced",
                    tint = Color(0xFF4CAF50) // Green
                )
            }
            SyncStatus.PENDING -> {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Pending",
                    tint = Color(0xFFFFC107) // Amber
                )
            }
            SyncStatus.SYNCING -> {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Syncing",
                    tint = Color(0xFF2196F3) // Blue
                )
            }
            SyncStatus.ERROR -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFF44336) // Red
                )
            }
        }
    }
}

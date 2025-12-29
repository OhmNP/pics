package com.photosync.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
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
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Synced",
                    tint = MaterialTheme.colorScheme.primary, // Or green
                    modifier = Modifier.size(16.dp)
                )
            }
            SyncStatus.PENDING -> {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Pending",
                    tint = Color(0xFFFFC107) // Amber
                )
            }
            SyncStatus.UPLOADING -> {
                 Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Syncing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                 )
            }
            SyncStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFF44336) // Red
                )
            }
            SyncStatus.PAUSED -> {
                Icon(
                     imageVector = Icons.Default.PauseCircle,
                     contentDescription = "Paused",
                     tint = Color.Gray
                )
            }
            SyncStatus.PAUSED_NETWORK -> {
                Icon(
                     imageVector = Icons.Default.SignalWifiOff,
                     contentDescription = "Waiting for network",
                     tint = Color.Gray,
                     modifier = Modifier.size(16.dp)
                )
            }
            SyncStatus.ERROR -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = Color(0xFFF44336)
                )
            }
        }
    }
}

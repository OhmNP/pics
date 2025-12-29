package com.photosync.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosync.android.service.EnhancedSyncService
import com.photosync.android.ui.components.*
import com.photosync.android.ui.theme.*
import com.photosync.android.viewmodel.DashboardViewModel

@Composable
fun SyncStatusScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val syncedCount by viewModel.syncedCount.collectAsStateWithLifecycle(initialValue = 0)
    val inProgressCount by viewModel.inProgressCount.collectAsStateWithLifecycle(initialValue = 0)
    val failedCount by viewModel.failedCount.collectAsStateWithLifecycle(initialValue = 0)
    val lastBackup by viewModel.lastSuccessfulBackup.collectAsStateWithLifecycle()
    
    val isSyncing = syncState is EnhancedSyncService.SyncState.Syncing
    val isPaused = syncState == EnhancedSyncService.SyncState.Paused
    
    GradientBox(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- Header ---
            NeonText(
                text = "Sync Status",
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.headlineMedium,
                glowColor = if (isSyncing) PrimaryGlow else Color.Transparent
            )

            // --- Hero Progress Circle ---
            Box(
                 modifier = Modifier.fillMaxWidth(),
                 contentAlignment = Alignment.Center
            ) {
                 // Background Glow
                 Box(
                     modifier = Modifier
                         .size(220.dp)
                         .background(
                             brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                 colors = listOf(PrimaryGlow, Color.Transparent)
                             ),
                             shape = CircleShape
                         )
                 )
                 
                 // Main Progress Indicator
                 val animatedProgress by animateFloatAsState(
                     targetValue = syncProgress.progressPercentage / 100f,
                     animationSpec = tween(1000),
                     label = "progress"
                 )
                 
                 CircularProgressIndicator(
                     progress = { animatedProgress },
                     modifier = Modifier.size(200.dp),
                     color = Primary,
                     trackColor = SurfaceVariant,
                     strokeWidth = 12.dp,
                     strokeCap = StrokeCap.Round
                 )
                 
                 // Inner Text
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                     Text(
                         text = "${syncProgress.progressPercentage.toInt()}%",
                         style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                         color = TextPrimary
                     )
                     
                     val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
                     val statusText = when (serverStatus) {
                         EnhancedSyncService.ServerConnectivityStatus.CONNECTED -> "Online"
                         EnhancedSyncService.ServerConnectivityStatus.CONNECTING -> "Connecting..."
                         EnhancedSyncService.ServerConnectivityStatus.DISCONNECTED -> "Offline"
                         EnhancedSyncService.ServerConnectivityStatus.ERROR -> "Error"
                     }
                     val statusColor = if (serverStatus == EnhancedSyncService.ServerConnectivityStatus.CONNECTED) Success else Error
                     
                     Text(
                         text = statusText,
                         style = MaterialTheme.typography.titleMedium,
                         color = statusColor
                     )
                     
                     Spacer(modifier = Modifier.height(4.dp))

                     Text(
                         text = if (syncState is EnhancedSyncService.SyncState.Syncing) "Syncing..." else "Idle",
                         style = MaterialTheme.typography.bodyMedium,
                         color = TextSecondary
                     )
                 }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // --- Detailed Status List ---
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatusRow(
                        title = "Backup in Progress",
                        value = "$inProgressCount Photos", 
                        subtitle = if (inProgressCount > 0) "Pending upload" else "All caught up",
                        progress = if (syncProgress.totalFiles > 0) syncProgress.progressPercentage / 100f else 0f,
                        showProgress = inProgressCount > 0
                    )
                    Divider(color = GlassBorder, modifier = Modifier.padding(vertical = 12.dp))
                    
                    if (failedCount > 0) {
                        StatusRow(
                             title = "Failed Items",
                             value = "$failedCount Failed",
                             subtitle = "check network or retry",
                             icon = Icons.Rounded.ErrorOutline,
                             iconColor = Error
                        )
                         Divider(color = GlassBorder, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    
                    StatusRow(
                        title = "Completed",
                        value = "$syncedCount items total",
                        subtitle = if (syncedCount > 0 && lastBackup > 0) "Last backup: ${formatDate(lastBackup)}" else "No Backup Yet",
                        icon = Icons.Rounded.CheckCircle,
                        iconColor = Success
                    )
                }
                }
            }

            // --- Controls ---
            
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when (syncState) {
                            is EnhancedSyncService.SyncState.Syncing,
                            is EnhancedSyncService.SyncState.Connecting,
                            is EnhancedSyncService.SyncState.Discovering,
                            is EnhancedSyncService.SyncState.Reconciling -> viewModel.pauseSync()
                            is EnhancedSyncService.SyncState.Paused -> viewModel.resumeSync() // Use resumeSync explicitly
                            else -> viewModel.startSync()
                        }
                    }
            ) {
                 Row(
                     modifier = Modifier
                         .padding(20.dp)
                         .fillMaxWidth(),
                     horizontalArrangement = Arrangement.Center,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     val buttonIcon = when(syncState) {
                        is EnhancedSyncService.SyncState.Syncing,
                        is EnhancedSyncService.SyncState.Connecting,
                        is EnhancedSyncService.SyncState.Discovering,
                        is EnhancedSyncService.SyncState.Reconciling -> Icons.Rounded.Pause
                        else -> Icons.Rounded.PlayArrow
                     }
                     
                     val buttonText = when(syncState) {
                        is EnhancedSyncService.SyncState.Syncing,
                        is EnhancedSyncService.SyncState.Connecting,
                        is EnhancedSyncService.SyncState.Discovering,
                        is EnhancedSyncService.SyncState.Reconciling -> "Pause Sync"
                        is EnhancedSyncService.SyncState.Paused -> "Resume Sync"
                        else -> "Start Sync"
                     }
                     
                     Icon(
                         imageVector = buttonIcon,
                         contentDescription = null,
                         tint = Primary
                     )
                     Spacer(modifier = Modifier.width(12.dp))
                     Text(
                         text = buttonText,
                         style = MaterialTheme.typography.titleMedium,
                         color = TextPrimary
                     )
                 }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun StatusRow(
    title: String,
    value: String,
    subtitle: String,
    progress: Float = 0f,
    showProgress: Boolean = false,
    icon: ImageVector? = null,
    iconColor: Color = Primary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Row(verticalAlignment = Alignment.Bottom) {
                 Text(value, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                 Spacer(modifier = Modifier.width(8.dp))
            }
            if (showProgress) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = Primary,
                    trackColor = SurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun animatedProgress(target: Float): Float {
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(500),
        label = "rowProgress"
    )
    return progress
}

fun formatTime(millis: Long): String {
    val minutes = millis / 1000 / 60
    return "$minutes min"
}

fun formatDate(millis: Long): String {
    val date = java.util.Date(millis)
    val format = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}

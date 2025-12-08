package com.photosync.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosync.android.model.SyncProgress
import com.photosync.android.service.EnhancedSyncService
import com.photosync.android.viewmodel.DashboardViewModel

@Composable
fun HomeScreen(
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val syncedCount by viewModel.syncedCount.collectAsState(initial = 0)
    val pendingCount by viewModel.pendingCount.collectAsState(initial = 0)
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PhotoSync Dashboard",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Server Status Card
        ServerStatusCard(state = syncState)
        
        // Sync Statistics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Synced",
                value = syncedCount.toString(),
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Pending",
                value = pendingCount.toString(),
                icon = Icons.Default.Add,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Sync Progress
        if (syncState is EnhancedSyncService.SyncState.Syncing) {
            SyncProgressCard(progress = syncProgress)
        }
        
        // Sync Controls
        SyncControlsCard(
            state = syncState,
            onStartSync = { viewModel.startSync() },
            onPauseSync = { viewModel.pauseSync() },
            onResumeSync = { viewModel.resumeSync() },
            onStopSync = { viewModel.stopSync() },
            onReconcile = { viewModel.reconcile() }
        )
        
        // Quick Actions
        OutlinedButton(
            onClick = onNavigateToGallery,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Gallery")
        }
    }
}

@Composable
private fun ServerStatusCard(state: EnhancedSyncService.SyncState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is EnhancedSyncService.SyncState.Syncing -> MaterialTheme.colorScheme.primaryContainer
                is EnhancedSyncService.SyncState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (state) {
                    is EnhancedSyncService.SyncState.Syncing -> Icons.Default.Refresh
                    is EnhancedSyncService.SyncState.Error -> Icons.Default.Warning
                    EnhancedSyncService.SyncState.Idle -> Icons.Default.CheckCircle
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Server Status",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = when (state) {
                        EnhancedSyncService.SyncState.Idle -> "Ready"
                        EnhancedSyncService.SyncState.Discovering -> "Discovering..."
                        EnhancedSyncService.SyncState.Connecting -> "Connecting..."
                        EnhancedSyncService.SyncState.Syncing -> "Syncing"
                        EnhancedSyncService.SyncState.Paused -> "Paused"
                        EnhancedSyncService.SyncState.Reconciling -> "Reconciling..."
                        is EnhancedSyncService.SyncState.Error -> "Error: ${state.message}"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SyncProgressCard(progress: SyncProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Syncing: ${progress.currentFile}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = if (progress.totalFiles > 0) {
                    progress.currentFileIndex.toFloat() / progress.totalFiles
                } else 0f,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${progress.currentFileIndex}/${progress.totalFiles} files",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SyncControlsCard(
    state: EnhancedSyncService.SyncState,
    onStartSync: () -> Unit,
    onPauseSync: () -> Unit,
    onResumeSync: () -> Unit,
    onStopSync: () -> Unit,
    onReconcile: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sync Controls",
                style = MaterialTheme.typography.titleMedium
            )
            
            when (state) {
                EnhancedSyncService.SyncState.Idle,
                is EnhancedSyncService.SyncState.Error -> {
                    Button(
                        onClick = onStartSync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Sync")
                    }
                    OutlinedButton(
                        onClick = onReconcile,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reconcile Status")
                    }
                }
                EnhancedSyncService.SyncState.Syncing -> {
                    Button(
                        onClick = onPauseSync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pause")
                    }
                    OutlinedButton(
                        onClick = onStopSync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
                EnhancedSyncService.SyncState.Paused -> {
                    Button(
                        onClick = onResumeSync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resume")
                    }
                    OutlinedButton(
                        onClick = onStopSync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
                else -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

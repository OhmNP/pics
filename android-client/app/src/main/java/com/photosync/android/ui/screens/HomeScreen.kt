package com.photosync.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photosync.android.ui.components.ConnectionStatusCard
import com.photosync.android.ui.components.SyncProgressCard
import com.photosync.android.viewmodel.HomeViewModel
import com.photosync.android.viewmodel.SyncViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    onNavigateToGallery: () -> Unit,
    onNavigateToSync: () -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = viewModel(),
    syncViewModel: SyncViewModel = viewModel()
) {
    val connectionStatus by homeViewModel.connectionStatus.collectAsStateWithLifecycle()
    val syncProgress by syncViewModel.syncProgress.collectAsStateWithLifecycle()
    
    val totalPhotosSynced = remember { homeViewModel.getTotalPhotosSynced() }
    val lastSyncTime = remember { homeViewModel.getLastSyncTime() }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PhotoSync Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Connection Status
        ConnectionStatusCard(status = connectionStatus)
        
        // Sync Progress
        SyncProgressCard(progress = syncProgress)
        
        // Statistics Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Photos Synced",
                value = totalPhotosSynced.toString(),
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                title = "Last Sync",
                value = if (lastSyncTime > 0) formatTime(lastSyncTime) else "Never",
                modifier = Modifier.weight(1f)
            )
        }
        
        // Quick Actions
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Button(
            onClick = onNavigateToSync,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Sync")
        }
        
        OutlinedButton(
            onClick = onNavigateToGallery,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("View Gallery")
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
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
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}

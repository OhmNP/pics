package com.photosync.android.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.photosync.android.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.photosync.android.model.MediaItem
import com.photosync.android.service.EnhancedSyncService
import com.photosync.android.ui.components.*
import com.photosync.android.ui.theme.*
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
    val recentUploads by viewModel.recentUploads.collectAsStateWithLifecycle(initialValue = emptyList())
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    // Gradient Background
    GradientBox(modifier = modifier.fillMaxSize()) {
        // Ambient Background Blobs
        Box(
            modifier = Modifier
                .offset(x = (-100).dp, y = (-100).dp)
                .size(400.dp)
                .alpha(0.2f)
                .background(
                    brush = Brush.radialGradient(colors = listOf(Primary, Color.Transparent)),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .size(400.dp)
                .alpha(0.2f)
                .background(
                    brush = Brush.radialGradient(colors = listOf(Secondary, Color.Transparent)),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- Header ---
            HeaderSection(userName)

            // --- Main Status Card (Visualizer) ---
            SyncStatusVisualizerCard(state = syncState, progress = syncProgress)

            // --- Stats Grid ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.weight(1f)){
                    // Hybrid Status Logic:
                    // 1. If Sync is active (Syncing, Paused, Reconciling, or explicit Connecting for sync), show that.
                    // 2. If Sync is Idle, show the background Server Status.
                    
                    val (status, icon, color) = if (syncState !is EnhancedSyncService.SyncState.Idle) {
                        // Priority: Sync Process
                        when(syncState) {
                            is EnhancedSyncService.SyncState.Syncing,
                            is EnhancedSyncService.SyncState.Reconciling -> Triple("Sync Active", Icons.Rounded.Sync, Success)
                            is EnhancedSyncService.SyncState.Connecting,
                            is EnhancedSyncService.SyncState.Discovering -> Triple("Connecting", Icons.Rounded.Wifi, Primary)
                            is EnhancedSyncService.SyncState.Paused -> Triple("Paused", Icons.Rounded.Pause, Secondary)
                            is EnhancedSyncService.SyncState.Error -> Triple("Sync Error", Icons.Rounded.Error, Error)
                            else -> Triple("Standby", Icons.Rounded.Wifi, TextSecondary) // Should not happen given if check
                        }
                    } else {
                        // Priority: Background Monitor
                        when(serverStatus) {
                            EnhancedSyncService.ServerConnectivityStatus.CONNECTED -> Triple("Online", Icons.Rounded.Wifi, Success)
                            EnhancedSyncService.ServerConnectivityStatus.CONNECTING -> Triple("Connecting", Icons.Rounded.Wifi, Primary)
                            EnhancedSyncService.ServerConnectivityStatus.DISCONNECTED -> Triple("Offline", Icons.Rounded.WifiOff, Error)
                            EnhancedSyncService.ServerConnectivityStatus.ERROR -> Triple("Error", Icons.Rounded.WifiOff, Error)
                        }
                    }

                    GlassStatCard(
                        title = "Server Status",
                        value = status,
                        icon = icon,
                        accentColor = color,
                        indicator = status == "Online" || status == "Sync Active"
                    )
                }
                Box(Modifier.weight(1f)) {
                    GlassStatCard(
                        title = "Storage",
                        value = "45%", // Mock value for now, could be passed from VM
                        icon = Icons.Rounded.Storage, // Pie chart would be better but icon is fine
                        accentColor = Primary
                    )
                }
            }

            // --- Recent Uploads ---
            if (recentUploads.isNotEmpty()) {
                RecentUploadsSection(recentUploads)
            }

            Spacer(modifier = Modifier.height(48.dp)) // Bottom padding for nav bar
        }
    }
}

@Composable
private fun HeaderSection(userName: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Brand Logo
            Image(
                painter = painterResource(id = R.drawable.ic_logo_brand),
                contentDescription = "PhotoSync Logo",
                modifier = Modifier
                    .size(60.dp)
                    ,
                contentScale = ContentScale.Fit
            )

            // Multi-colored Neon Title
            Text(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            color = Primary,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = PrimaryGlow,
                                blurRadius = 16f
                            )
                        )
                    ) {
                        append("Photo")
                    }
                    withStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            color = Secondary,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = SecondaryGlow,
                                blurRadius = 16f
                            )
                        )
                    ) {
                        append("Sync")
                    }
                },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (userName.isNotBlank()) "Good Evening, $userName" else "Good Evening",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SyncStatusVisualizerCard(state: EnhancedSyncService.SyncState, progress: com.photosync.android.model.SyncProgress) {
    val isSyncing = state is EnhancedSyncService.SyncState.Syncing
    val isConnecting = state is EnhancedSyncService.SyncState.Connecting || state is EnhancedSyncService.SyncState.Discovering
    
    val statusText = when(state) {
        is EnhancedSyncService.SyncState.Syncing -> "Sync Active"
        is EnhancedSyncService.SyncState.Paused -> "Sync Paused"
        is EnhancedSyncService.SyncState.Connecting -> "Connecting..."
        is EnhancedSyncService.SyncState.Discovering -> "Searching..."
        is EnhancedSyncService.SyncState.Reconciling -> "Analyzing..."
        is EnhancedSyncService.SyncState.Error -> "Connection Failed" // Shortened for UI
        else -> "Ready to Sync"
    }
    
    val statusColor = when(state) {
        is EnhancedSyncService.SyncState.Syncing -> Primary
        is EnhancedSyncService.SyncState.Error -> Error
        else -> TextSecondary
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp) // Adjusted height
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Title + Visualizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonText(
                    text = statusText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = statusColor,
                    glowColor = if (isSyncing) PrimaryGlow else Color.Transparent
                )
                
                // Visualizer (Animated Bars) - Always show icon structure, animate if active
                AudioVisualizerAnim(isAnimating = isSyncing || isConnecting, color = statusColor)
            }
            
            // Progress Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Progress Bar
                Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                    // Track
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceVariant.copy(alpha = 0.5f), CircleShape)
                    )
                    // Indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isSyncing) progress.progressPercentage / 100f else 0.05f) 
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(statusColor.copy(alpha = 0.7f), statusColor)
                                ), 
                                CircleShape
                            )
                    )
                    // Glow effect for indicator
                    if (isSyncing) {
                         Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.progressPercentage / 100f)
                                .fillMaxHeight()
                                .blur(8.dp)
                                .background(statusColor, CircleShape)
                        )
                    }
                }

                // Percentage / Detail Text
                Text(
                    text = if (isSyncing) "${progress.progressPercentage.toInt()}%" else if (state is EnhancedSyncService.SyncState.Error) "Retrying..." else "Waiting...",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AudioVisualizerAnim(isAnimating: Boolean, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(24.dp)
    ) {
        val barCount = 5
        repeat(barCount) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
            // Staggered animation for wave effect
            val duration = 600
            val delay = index * 120
            
            val heightScale by if (isAnimating) {
                infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = duration,
                            delayMillis = delay,
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "barHeight"
                )
            } else {
                remember { mutableFloatStateOf(0.4f) }
            }
            
            // Mirror the center bars to look more like a wave if we had more,
            // but for 5 bars, simple staggered is fine or 1-2-3-2-1 logic
            val currentHeight = if (isAnimating) heightScale else 0.4f

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(currentHeight) // Animate fillMaxHeight fraction
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
private fun GlassStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    indicator: Boolean = false
) {
    GlassCard(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
                if (indicator) {
                    GlowIndicator(
                        color = accentColor,
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                if (title == "Storage") {
                   CircularProgressIndicator(
                       progress = { 0.45f },
                       modifier = Modifier.size(40.dp),
                       color = Primary,
                       trackColor = SurfaceVariant
                   )
                   Spacer(modifier = Modifier.height(4.dp))
                   Text(
                        text = value,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                   )
                } else {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentUploadsSection(recentUploads: List<MediaItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Uploads",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "View All",
                tint = TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recentUploads) { item ->
                RecentUploadItem(item)
            }
        }
    }
}

@Composable
fun RecentUploadItem(item: MediaItem) {
    Box(
        modifier = Modifier
            .size(100.dp, 120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceVariant)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Timestamp overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 50f
                    )
                )
        )
        
        // Mock time
        Text(
            text = "7:30 pm",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            fontSize = 10.sp
        )
    }
}

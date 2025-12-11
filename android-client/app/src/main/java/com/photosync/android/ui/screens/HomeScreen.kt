package com.photosync.android.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
            HeaderSection()

            // --- Main Status Card (Visualizer) ---
            SyncStatusVisualizerCard(state = syncState)

            // --- Stats Grid ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.weight(1f)){
                    val isConnected = syncState !is EnhancedSyncService.SyncState.Error

                    GlassStatCard(
                        title = "Server Connected",
                        value = if (isConnected) "Online" else "Offline",
                        icon = if (isConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                        accentColor = if (isConnected) Success else Error,
                        indicator = true
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
private fun HeaderSection() {
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
            text = "Good Evening, Alex", // Should be dynamic
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SyncStatusVisualizerCard(state: EnhancedSyncService.SyncState) {
    val isSyncing = state is EnhancedSyncService.SyncState.Syncing
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeonText(
                    text = if (isSyncing) "Sync Active" else "Sync Paused",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSyncing) Primary else Color.Gray
                )
                
                // Visualizer (Animated Bars)
                if (isSyncing) {
                    AudioVisualizerAnim()
                }
            }
            
            // Progress Bar (Mocked mostly if not active)
            Column {
                LinearProgressIndicator(
                    progress = { if (isSyncing) 0.85f else 0f }, // Mock progress for visual
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = Primary,
                    trackColor = SurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isSyncing) "85% - Syncing..." else "Waiting to sync...",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun AudioVisualizerAnim() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val infiniteTransition = rememberInfiniteTransition()
            val height by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500,
                        delayMillis = index * 100,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "barHeight"
            )
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp * height)
                    .background(Primary, CircleShape)
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

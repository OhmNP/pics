package com.photosync.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.photosync.android.data.entity.SyncStatus
import com.photosync.android.model.MediaItem
import com.photosync.android.ui.components.*
import com.photosync.android.ui.theme.*
import com.photosync.android.viewmodel.GalleryViewModel
import com.photosync.android.ui.gallery.GalleryFilter
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.HourglassEmpty
import kotlinx.coroutines.flow.Flow
import com.photosync.android.model.GalleryStats

import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel(),
) {
    val lazyPagingItems = viewModel.pagedMedia.collectAsLazyPagingItems()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stats by viewModel.galleryStats.collectAsStateWithLifecycle(initialValue = null)
    
    // Listen for Sync Consistency Signal (Triggered by DB changes)
    // REMOVED: We no longer refresh Paging Source on status changes.
    // Instead we observe status map below.
    val statusMap by viewModel.syncStatusMap.collectAsStateWithLifecycle()
    
    // Media Viewer Overlay State
    var initialPage by remember { mutableStateOf<Int?>(null) }
    
    // BackHandler to close overlay
    if (initialPage != null) {
        androidx.activity.compose.BackHandler {
            initialPage = null
        }
    }

    // Background
    GradientBox(modifier = modifier.fillMaxSize()) {
        AmbientBackground()
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // --- Header & Search ---
                var isSearchExpanded by remember { mutableStateOf(false) }
                
                if (initialPage == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f)) // Subtle header bg
                    ) {
                         // --- Status Banner (New) ---
                         stats?.let { s ->
                             SyncStatusBanner(
                                 stats = s,
                                 isShowingFilter = uiState.filter == GalleryFilter.FAILED,
                                 onShowFailed = { viewModel.showFailedItems() },
                                 onClearFilter = { viewModel.clearFilter() }
                             )
                         }

                        // Top Bar: Search + Actions
                        if (isSearchExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                                    placeholder = { Text("Search Photos...", color = TextSecondary.copy(alpha = 0.5f)) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = Primary
                                    ),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge,
                                    trailingIcon = {
                                        IconButton(onClick = { 
                                            isSearchExpanded = false
                                            viewModel.onSearchQueryChanged("")
                                        }) {
                                            Icon(Icons.Rounded.Close, "Close Search", tint = TextSecondary)
                                        }
                                    }
                                )
                            }
                        } else {
                            // Standard Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp), // More breathing room
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (uiState.isSelectionMode) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.clearSelection() }) {
                                            Icon(Icons.Rounded.Close, "Cancel Selection", tint = TextPrimary)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${uiState.selectedIds.size} Selected",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                    }
                                } else {
                                    NeonText(
                                        text = "Photos", // Simplified
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    
                                    Row {
                                        IconButton(
                                            onClick = { isSearchExpanded = true },
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(SurfaceVariant.copy(alpha = 0.3f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = "Search",
                                                tint = TextPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Button(
                                            onClick = { viewModel.toggleSelectionMode() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SurfaceVariant.copy(alpha = 0.3f),
                                                contentColor = TextPrimary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier.height(40.dp),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Text("Select")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // --- Photo Grid ---
                Box(modifier = Modifier.weight(1f)) {
                     // Only show blocking spinner if we have NO items.
                     // If we have items and are just refreshing (due to Sync Trigger), keep showing list.
                     if (lazyPagingItems.loadState.refresh is LoadState.Loading && lazyPagingItems.itemCount == 0) {
                         Box(
                             modifier = Modifier.fillMaxSize(),
                             contentAlignment = Alignment.Center
                         ) {
                             CircularProgressIndicator(color = Primary)
                         }
                     } else {
                         LazyVerticalGrid(
                             columns = GridCells.Fixed(3),
                             contentPadding = if (initialPage == null) PaddingValues(4.dp) else PaddingValues(0.dp), 
                             horizontalArrangement = Arrangement.spacedBy(4.dp),
                             verticalArrangement = Arrangement.spacedBy(4.dp),
                             modifier = Modifier.fillMaxSize()
                         ) {
                             items(
                                 count = lazyPagingItems.itemCount,
                                 key = { index -> lazyPagingItems[index]?.id ?: index }
                             ) { index ->
                                 val item = lazyPagingItems[index]
                                 if (item != null) {
                                     PhotoGridItem(
                                         modifier = Modifier.animateItemPlacement(), // Keep reordering anim if we need it
                                         item = item,
                                         dynamicStatus = statusMap[item.id],
                                         isSelected = uiState.selectedIds.contains(item.id),
                                         isSelectionMode = uiState.isSelectionMode,
                                         isExiting = false, // Removed exit anim logic for cleaner view
                                         uploadProgressFlow = remember(item.id) { viewModel.getItemProgressFlow(item.id) },
                                         onClick = { 
                                             if (uiState.isSelectionMode) {
                                                 viewModel.toggleSelection(item.id)
                                             } else {
                                                 initialPage = index 
                                             }
                                         },
                                         onLongClick = {
                                             if (!uiState.isSelectionMode) {
                                                 viewModel.toggleSelectionMode()
                                                 viewModel.toggleSelection(item.id)
                                             }
                                         }
                                     )
                                 }
                             }
                         }
                     }
                }
            }

            // --- Bottom Selection Bar ---
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.9f),
                    tonalElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.selectedIds.size} items",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { viewModel.clearSelection() }) {
                                Text("Cancel", color = TextSecondary)
                            }
                            
                            Button(
                                onClick = { viewModel.uploadSelected() },
                                enabled = uiState.selectedIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Primary,
                                    disabledContainerColor = SurfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Upload")
                            }
                        }
                    }
                }
            }

            // --- Full Screen Pager Overlay ---
            if (initialPage != null) {
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = initialPage!!,
                    pageCount = { lazyPagingItems.itemCount }
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // Optimized Pager
                     androidx.compose.foundation.pager.VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        // Pager item content
                        val item = if (page < lazyPagingItems.itemCount) lazyPagingItems[page] else null
                        if (item != null) {
                             Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                     // Placeholder/Error handling
                                )
                            }
                        }
                    }
                    
                    // Back Button Overlay
                    IconButton(
                        onClick = { initialPage = null },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun SyncStatusBanner(
    stats: GalleryStats,
    onShowFailed: () -> Unit,
    isShowingFilter: Boolean,
    onClearFilter: () -> Unit
) {
    if (isShowingFilter) {
         // Filter Active State (Banner shows "Showing Failed Items")
         Surface(
            color = Error.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth().clickable { onClearFilter() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = Error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Showing ${stats.failedItems} failed items",
                        style = MaterialTheme.typography.labelLarge,
                        color = Error
                    )
                }
                Text(
                    text = "CLEAR",
                    style = MaterialTheme.typography.labelLarge,
                    color = Error,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    } else if (stats.pendingItems > 0 || stats.uploadingItems > 0) {
        // Active Sync State
        // Active Sync State
        Surface(
            color = Primary.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Backing up ${stats.pendingItems + stats.uploadingItems} items...",
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary
                    )
                    if (stats.failedItems > 0) {
                        Text(
                            text = "${stats.failedItems} items failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = Error
                        )
                    }
                }
            }
        }
    } else if (stats.failedItems > 0) {
        // Failed State Only
        Surface(
            color = Error.copy(alpha = 0.15f), 
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowFailed() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, null, tint = Error, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                         text = "${stats.failedItems} items failed backup",
                         style = MaterialTheme.typography.labelLarge,
                         color = Error
                    )
                    Text(
                         text = "Tap to view",
                         style = MaterialTheme.typography.labelSmall,
                         color = Error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
    // Else: All Good (or empty), show nothing to be unobtrusive
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotoGridItem(
    modifier: Modifier = Modifier,
    item: MediaItem, 
    dynamicStatus: SyncStatus? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    isExiting: Boolean = false,
    uploadProgressFlow: Flow<Float?>? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    // Determine effective status (prefer dynamic update)
    val effectiveStatus = dynamicStatus ?: item.syncStatus

    // Pulse animation for PENDING state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val contentAlpha = if (effectiveStatus == SyncStatus.PENDING) pulseAlpha else 1f
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Wrapper for dynamic visual effects (Pulse, Selection Scale)
        // We use graphicsLayer to minimize invalidations.
        // Importantly, we wrap the Helper Composable so IT is skipped when 'item' changes but 'uri' doesn't.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    val scale = if (isSelected) 0.85f else 1f
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            GalleryImage(uri = item.uri, name = item.name)
        }
        
        // Selection Overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Primary.copy(alpha = 0.3f))
            )
        }

        // Gradient Content Overlay (Only at bottom for text/icons)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        startY = 300f 
                    )
                )
        )
        
        // --- Selection Indicator ---
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = Primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Not Selected",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // --- Sync Status & Progress ---
        if (!isSelectionMode) {
            // Priority: Dynamic Status Map > Item Initial Status
            val status = item.syncStatus // Fallback

            
            // Collect progress
            val uploadProgress by if (uploadProgressFlow != null) {
                uploadProgressFlow.collectAsStateWithLifecycle(initialValue = null)
            } else {
                remember { mutableStateOf(null) }
            }

            val showProgress = (effectiveStatus == SyncStatus.UPLOADING || uploadProgress != null)
            
            // Progress Bar
            if (showProgress) {
                 val animatedProgress by animateFloatAsState(
                    targetValue = uploadProgress ?: 0f,
                    animationSpec = progressSpec,
                    label = "progress"
                )
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter),
                    color = Primary,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
            
            // Icons (Top Right)
            if (!showProgress) {
                 val (icon, tint) = when (effectiveStatus) {
                    SyncStatus.SYNCED -> Icons.Rounded.CheckCircle to Success
                    SyncStatus.PENDING -> Icons.Rounded.CloudUpload to Color.White.copy(alpha = 0.9f) 
                    SyncStatus.FAILED, SyncStatus.ERROR -> Icons.Rounded.Warning to Error
                    SyncStatus.PAUSED, SyncStatus.PAUSED_NETWORK -> Icons.Rounded.HourglassEmpty to Color.Yellow
                    else -> null to null
                }

                if (icon != null && tint != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Status",
                            tint = tint,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Isolated Image Composable to enable Smart Recomposition skipping.
 * If 'uri' is effectively the same, this component will NOT recompose 
 * even if the parent (Status/Alpha) changes.
 */
@Composable
fun GalleryImage(uri: android.net.Uri, name: String) {
    SubcomposeAsyncImage(
        model = uri,
        contentDescription = name,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null, 
                    tint = TextSecondary.copy(alpha = 0.2f),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        error = {
            Box(
                modifier = Modifier.fillMaxSize().background(SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
               Icon(Icons.Rounded.Warning, null, tint = Error.copy(alpha = 0.5f))
            }
        }
    )
}

private val progressSpec = tween<Float>(durationMillis = 300, easing = LinearEasing)

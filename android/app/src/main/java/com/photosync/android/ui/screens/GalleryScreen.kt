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
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.photosync.android.data.entity.SyncStatus
import com.photosync.android.model.MediaItem
import com.photosync.android.ui.components.*
import com.photosync.android.ui.theme.*
import com.photosync.android.viewmodel.GalleryViewModel

@Composable
fun GalleryScreen(
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel(),
) {
    val lazyPagingItems = viewModel.pagedMedia.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // --- Header & Search ---
                var isSearchExpanded by remember { mutableStateOf(false) }
                
                if (initialPage == null) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isSearchExpanded) {
                                TextField(
                                    value = searchQuery,
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
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelectionMode) {
                                        IconButton(onClick = { viewModel.clearSelection() }) {
                                            Icon(Icons.Rounded.Close, "Cancel Selection", tint = TextPrimary)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${selectedIds.size} Selected",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                    } else {
                                        NeonText(
                                            text = "Gallery",
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                    }
                                }
                                
                                Row {
                                    if (!isSelectionMode) {
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
                                    }
                                    
                                    Button(
                                        onClick = { viewModel.toggleSelectionMode() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelectionMode) Primary.copy(alpha = 0.2f) else SurfaceVariant.copy(alpha = 0.3f),
                                            contentColor = if (isSelectionMode) Primary else TextPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(40.dp),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(if (isSelectionMode) "Done" else "Select")
                                    }
                                }
                            }
                        }
                    }
                }
                
                // --- Photo Grid ---
                Box(modifier = Modifier.weight(1f)) {
                     if (lazyPagingItems.loadState.refresh is LoadState.Loading) {
                         Box(
                             modifier = Modifier.fillMaxSize(),
                             contentAlignment = Alignment.Center
                         ) {
                             CircularProgressIndicator(color = Primary)
                         }
                     } else {
                         LazyVerticalGrid(
                             columns = GridCells.Fixed(3),
                             contentPadding = if (initialPage == null) PaddingValues(4.dp) else PaddingValues(0.dp), // irrelevant when hidden but good practice
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
                                         item = item,
                                         isSelected = selectedIds.contains(item.id),
                                         isSelectionMode = isSelectionMode,
                                         onClick = { 
                                             if (isSelectionMode) {
                                                 viewModel.toggleSelection(item.id)
                                             } else {
                                                 initialPage = index 
                                             }
                                         },
                                         onLongClick = {
                                             if (!isSelectionMode) {
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
                visible = isSelectionMode,
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
                            text = "${selectedIds.size} items",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { viewModel.clearSelection() }) {
                                Text("Cancel", color = TextSecondary)
                            }
                            
                            Button(
                                onClick = { viewModel.uploadSelected() },
                                enabled = selectedIds.isNotEmpty(),
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
                    androidx.compose.foundation.pager.VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val item = lazyPagingItems[page]
                        if (item != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotoGridItem(
    item: MediaItem, 
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = if (isSelected) 0.85f else 1f
                    scaleX = scale
                    scaleY = scale
                },
            contentScale = ContentScale.Crop
        )
        
        // Selection Overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Primary.copy(alpha = 0.3f))
            )
        }

        // Gradient Content Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 150f
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

        // --- Sync Status Dot ---
        val isSynced = item.syncStatus == SyncStatus.SYNCED
        if (!isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(8.dp)
                    .background(
                        color = if (isSynced) Success else Color.Transparent, 
                        shape = CircleShape
                    )
            ) {
                if (isSynced) {
                     Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Success, CircleShape)
                            .graphicsLayer {
                                shadowElevation = 4.dp.toPx()
                                spotShadowColor = Success
                                ambientShadowColor = Success
                            }
                     )
                } else if (item.syncStatus == SyncStatus.FAILED) {
                     Box(
                        modifier = Modifier.size(8.dp).background(Error, CircleShape)
                     )
                }
            }
        }

        // Bottom Text Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = item.name, // Or modify date
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

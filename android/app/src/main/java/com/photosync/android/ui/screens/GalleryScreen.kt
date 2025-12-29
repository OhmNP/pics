package com.photosync.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
                                NeonText(
                                    text = "Gallery",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                
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
                                         onClick = { initialPage = index }
                                     )
                                 }
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
                            imageVector = Icons.Default.ArrowBack, // Changed to Default as AutoMirrored might need specific imports
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
fun PhotoGridItem(
    item: MediaItem, 
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Gradient Content Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        startY = 150f // Start gradient lower down
                    )
                )
        )
        
        // --- Sync Status Dot ---
        val isSynced = item.syncStatus == SyncStatus.SYNCED
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(8.dp)
                .background(
                    color = if (isSynced) Success else Color.Transparent, // Only show if synced? Or different color for pending? 
                    // User Request: "add small green dot overlay for synced vs not synced status"
                    // Usually this means Green for synced, maybe nothing or Gray for others.
                    // Let's stick to Green for Synced.
                    shape = CircleShape
                )
        ) {
            // Include a border if it's not transparent to make it pop
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

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
    viewModel: GalleryViewModel = viewModel()
) {
    val lazyPagingItems = viewModel.pagedMedia.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    // Background
    GradientBox(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // --- Header & Search ---
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                cornerRadius = 32.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Simple search field
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
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                    
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = "Filter",
                        tint = Primary,
                        modifier = Modifier.clickable { /* TODO */ }
                    )
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
                         contentPadding = PaddingValues(4.dp),
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
                                 PhotoGridItem(item)
                             }
                         }
                     }
                 }
            }
        }
    }
}

@Composable
fun PhotoGridItem(item: MediaItem) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariant)
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
            } else if (item.syncStatus == SyncStatus.ERROR) {
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

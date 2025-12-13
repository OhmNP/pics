package com.photosync.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.photosync.android.model.ConnectionStatus

@Composable
fun ConnectionStatusCard(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is ConnectionStatus.Connected -> MaterialTheme.colorScheme.primaryContainer
                is ConnectionStatus.Connecting -> MaterialTheme.colorScheme.secondaryContainer
                is ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.errorContainer
                is ConnectionStatus.Error -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (status) {
                        is ConnectionStatus.Connected -> "Connected to server"
                        is ConnectionStatus.Connecting -> "Connecting..."
                        is ConnectionStatus.Disconnected -> "Disconnected"
                        is ConnectionStatus.Error -> "Error: ${status.message}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            StatusIndicator(status = status)
        }
    }
}

@Composable
private fun StatusIndicator(status: ConnectionStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val color = when (status) {
        is ConnectionStatus.Connected -> Color(0xFF4CAF50)
        is ConnectionStatus.Connecting -> Color(0xFFFFC107)
        is ConnectionStatus.Disconnected -> Color(0xFFF44336)
        is ConnectionStatus.Error -> Color(0xFFF44336)
    }
    
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        if (status is ConnectionStatus.Connecting) {
            // Animated pulsing circle for connecting state
            Canvas(modifier = Modifier.size(32.dp)) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = size.minDimension / 2
                )
            }
        } else {
            // Static circle for other states
            Canvas(modifier = Modifier.size(32.dp)) {
                drawCircle(
                    color = color,
                    radius = size.minDimension / 2
                )
            }
        }
    }
}

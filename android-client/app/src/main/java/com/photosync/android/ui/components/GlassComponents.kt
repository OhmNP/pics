package com.photosync.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photosync.android.ui.theme.GlassBackground
import com.photosync.android.ui.theme.GlassBorder
import com.photosync.android.ui.theme.Primary

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

@Composable
fun GradientBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}

@Composable
fun NeonText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = Primary
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            shadow = Shadow(
                color = color.copy(alpha = 0.5f),
                offset = Offset(0f, 0f),
                blurRadius = 16f
            ),
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
fun GlowIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = color.copy(alpha = 0.5f),
            radius = size.minDimension / 2,
        )
        drawCircle(
            color = color,
            radius = size.minDimension / 4,
        )
    }
}

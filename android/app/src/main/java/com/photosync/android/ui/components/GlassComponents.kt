package com.photosync.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photosync.android.ui.theme.Primary

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    gradientColors: List<Color> = listOf(
        Color(0xB74FACFE).copy(alpha = 0.25f),
        Color(0x9500F2FE).copy(alpha = 0.10f),
        Color.Transparent
    ),
    glowColor: Color = Color(0x234FACFE),
    glowRadius: Dp = 20.dp,
    borderColor: Color = Color(0xF74FACFE).copy(alpha = 0.25f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                shadowElevation = glowRadius.toPx()
                shape = RoundedCornerShape(cornerRadius)
                clip = false
            }
            .drawBehind {
                drawRoundRect(
                    color = glowColor,
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    alpha = 0.5f
                )
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset(0f, 0f),
                    end = Offset(600f, 600f)
                )
            ) .background(
                Brush.radialGradient(
                    colors = gradientColors,
                    radius = 550f,
                    center = Offset(150f, 80f) // slight offset gives realism
                )
            )
            .border( width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(12.dp)
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
    color: Color = Primary,
    glowColor: Color = color.copy(alpha = 0.5f)
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            shadow = Shadow(
                color = glowColor,
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

package com.victustune.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.victustune.player.ui.theme.DesignTokens

/**
 * Applies a crisp, opaque matte elevated surface with a subtle hairline border.
 * Keeps dark UI clean and readable without translucency or blur.
 */
@Composable
fun Modifier.matteSurface(
    cornerRadius: Dp = DesignTokens.RadiusCard,
    surfaceColor: Color = DesignTokens.ElevatedSurface,
    tintColor: Color = Color.Transparent,
    tintFraction: Float = 0.08f,
    borderColor: Color = DesignTokens.HairlineBorder,
    borderWidth: Dp = DesignTokens.HairlineStrokeWidth
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val resolvedSurface = if (tintColor != Color.Transparent) {
        lerp(surfaceColor, tintColor, tintFraction.coerceIn(0f, 1f))
    } else {
        surfaceColor
    }
    return this
        .clip(shape)
        .background(resolvedSurface)
        .border(BorderStroke(borderWidth, borderColor), shape)
}

@Composable
fun Modifier.glassmorphic(
    cornerRadius: Dp = DesignTokens.RadiusCard,
    tintColor: Color = Color.Transparent,
    tintAlpha: Float = 0.10f
): Modifier = matteSurface(
    cornerRadius = cornerRadius,
    surfaceColor = DesignTokens.ElevatedSurface,
    tintColor = tintColor,
    tintFraction = tintAlpha
)

package com.victustune.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Matte dark design tokens aligned to a 4dp / 8dp spacing grid, 4 consistent corner radii,
 * and clear dark tonal contrast tiers without blur or translucent glass layers.
 */
object DesignTokens {
    // Surface Tonal Scale
    val CanvasBackground = Color(0xFF0B0B0E)
    val ElevatedSurface = Color(0xFF15151A)
    val InteractiveSurface = Color(0xFF202028)
    val HairlineBorder = Color.White.copy(alpha = 0.07f)
    val HairlineStrokeWidth = 0.75.dp

    // Typography Opacity Tiers
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.62f)
    val TextTertiary = Color.White.copy(alpha = 0.42f)

    // 4dp / 8dp Spacing Scale
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp

    // Standardized Radius Scale
    val RadiusThumb = 8.dp
    val RadiusCard = 14.dp
    val RadiusMiniPlayer = 18.dp
    val RadiusArtwork = 20.dp
    val RadiusDock = 28.dp

    val ThumbShape = RoundedCornerShape(RadiusThumb)
    val CardShape = RoundedCornerShape(RadiusCard)
    val MiniPlayerShape = RoundedCornerShape(RadiusMiniPlayer)
    val ArtworkShape = RoundedCornerShape(RadiusArtwork)
    val DockShape = RoundedCornerShape(RadiusDock)
}

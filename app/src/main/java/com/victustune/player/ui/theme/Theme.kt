package com.victustune.player.ui.theme

import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val paletteAccentCache = LruCache<String, Int>(100)

@Immutable
data class DynamicThemeColors(
    val accent: Color,
    val surfaceTint: Color
)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1E78),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = VividCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004E5C),
    onSecondaryContainer = Color(0xFFBCEBFF),
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder
)

@Composable
fun VictusTuneTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Extracts, clamps (HSL), and smoothly animates dynamic artwork-driven colors:
 * - [DynamicThemeColors.accent]: vibrant yet legible accent for icons, sliders, and active pills
 * - [DynamicThemeColors.surfaceTint]: dark muted tint (16% blended into #0B0B0E) for matte gradients
 */
@Composable
fun rememberDynamicColors(
    imageUri: Any?,
    defaultColor: Color = ElectricPurple
): DynamicThemeColors {
    val context = LocalContext.current
    var targetAccent by remember { mutableStateOf(clampAccentColor(defaultColor)) }
    var targetSurfaceTint by remember { mutableStateOf(computeMutedSurfaceTint(defaultColor)) }

    LaunchedEffect(imageUri) {
        if (imageUri == null) {
            targetAccent = clampAccentColor(defaultColor)
            targetSurfaceTint = computeMutedSurfaceTint(defaultColor)
            return@LaunchedEffect
        }

        val cacheKey = imageUri.toString()
        val cachedRgb = paletteAccentCache.get(cacheKey)
        if (cachedRgb != null) {
            val seed = Color(cachedRgb)
            targetAccent = clampAccentColor(seed)
            targetSurfaceTint = computeMutedSurfaceTint(seed)
            return@LaunchedEffect
        }

        try {
            val bitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(imageUri)
                    .size(100, 100)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    (result.drawable as? BitmapDrawable)?.bitmap
                } else {
                    null
                }
            }
            if (bitmap != null) {
                val rgb = withContext(Dispatchers.Default) {
                    val palette = Palette.from(bitmap).generate()
                    val swatch = palette.vibrantSwatch
                        ?: palette.mutedSwatch
                        ?: palette.dominantSwatch
                        ?: palette.darkVibrantSwatch
                    swatch?.rgb
                }
                if (rgb != null) {
                    paletteAccentCache.put(cacheKey, rgb)
                    val seed = Color(rgb)
                    targetAccent = clampAccentColor(seed)
                    targetSurfaceTint = computeMutedSurfaceTint(seed)
                }
            }
        } catch (_: Exception) {
            // Keep current palette on error
        }
    }

    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "dynamicAccentColor"
    )
    val animatedSurfaceTint by animateColorAsState(
        targetValue = targetSurfaceTint,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "dynamicSurfaceTint"
    )

    return DynamicThemeColors(
        accent = animatedAccent,
        surfaceTint = animatedSurfaceTint
    )
}

@Composable
fun rememberDynamicAccentColor(
    imageUri: Any?,
    defaultColor: Color = ElectricPurple
): State<Color> {
    val colors = rememberDynamicColors(imageUri = imageUri, defaultColor = defaultColor)
    return rememberUpdatedState(colors.accent)
}

internal fun clampAccentColor(raw: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(raw.toArgb(), hsl)
    // Preserve hue; clamp saturation [0.42..0.78] and lightness [0.58..0.72] so text/icons stay crisp on dark matte UI
    hsl[1] = hsl[1].coerceIn(0.42f, 0.78f)
    hsl[2] = hsl[2].coerceIn(0.58f, 0.72f)
    return Color(ColorUtils.HSLToColor(hsl))
}

internal fun computeMutedSurfaceTint(raw: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(raw.toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.30f, 0.65f)
    hsl[2] = hsl[2].coerceIn(0.35f, 0.55f)
    val mutedSeed = Color(ColorUtils.HSLToColor(hsl))
    return lerp(DesignTokens.CanvasBackground, mutedSeed, 0.17f)
}

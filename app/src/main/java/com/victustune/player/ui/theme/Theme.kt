package com.victustune.player.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Hook to dynamically extract Palette colors from the current track's album art.
 */
@Composable
fun rememberDynamicAccentColor(
    imageUri: Any?,
    defaultColor: Color = ElectricPurple
): State<Color> {
    val context = LocalContext.current
    var extractedColor by remember { mutableStateOf(defaultColor) }

    LaunchedEffect(imageUri) {
        if (imageUri == null) {
            extractedColor = defaultColor
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(imageUri)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        val swatch = palette.vibrantSwatch
                            ?: palette.darkVibrantSwatch
                            ?: palette.dominantSwatch
                            ?: palette.lightVibrantSwatch

                        swatch?.rgb?.let { rgb ->
                            withContext(Dispatchers.Main) {
                                extractedColor = Color(rgb)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Keep default on error
            }
        }
    }

    return animateColorAsState(
        targetValue = extractedColor,
        animationSpec = tween(durationMillis = 600),
        label = "dynamicAccentColor"
    )
}

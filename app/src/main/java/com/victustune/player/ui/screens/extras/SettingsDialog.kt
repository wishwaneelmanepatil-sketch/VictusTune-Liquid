package com.victustune.player.ui.screens.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.victustune.player.data.model.AudioQuality
import com.victustune.player.data.model.AuthState
import com.victustune.player.data.model.SliderColorMode
import com.victustune.player.data.model.SliderPreferences
import com.victustune.player.data.model.SliderStyle
import com.victustune.player.ui.components.SliderStylePreview
import com.victustune.player.ui.components.matteSurface
import com.victustune.player.ui.theme.*

@Composable
fun SettingsDialog(
    currentQuality: AudioQuality,
    onSelectQuality: (AudioQuality) -> Unit,
    currentCacheBytes: Long,
    maxCacheSizeMb: Int,
    onSelectMaxCacheSizeMb: (Int) -> Unit,
    onClearCache: () -> Unit,
    authState: AuthState = AuthState.LoggedOut,
    isSyncingPlaylists: Boolean = false,
    onOpenLogin: () -> Unit = {},
    onSyncPlaylists: () -> Unit = {},
    onSignOut: () -> Unit = {},
    totalDownloadSizeBytes: Long = 0L,
    downloadQuality: String = "High",
    onSelectDownloadQuality: (String) -> Unit = {},
    downloadWifiOnly: Boolean = false,
    onToggleDownloadWifiOnly: (Boolean) -> Unit = {},
    onDeleteAllDownloads: () -> Unit = {},
    sliderPreferences: SliderPreferences = SliderPreferences(),
    onSelectSliderStyle: (SliderStyle) -> Unit = {},
    onSelectSliderColorMode: (SliderColorMode) -> Unit = {},
    onToggleShowSliderThumb: (Boolean) -> Unit = {},
    accentColor: Color = ElectricPurple,
    onDismiss: () -> Unit
) {
    val formattedCache = formatBytes(currentCacheBytes)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Settings & Account",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // YouTube Music Account Section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "YouTube Music Account",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = VividCyan
                    )

                    when (authState) {
                        is AuthState.LoggedOut -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Connect your YouTube Music account to stream and sync your personal playlists.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )

                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenLogin()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sign In with YouTube Music", fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }
                            }
                        }
                        is AuthState.LoggedIn -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (!authState.profile.avatarUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = authState.profile.avatarUrl,
                                                contentDescription = authState.profile.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(ElectricPurple),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = authState.profile.name.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 18.sp
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = authState.profile.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (authState.profile.handle.isNotEmpty()) {
                                                Text(
                                                    text = authState.profile.handle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onSyncPlaylists,
                                            enabled = !isSyncingPlaylists,
                                            shape = RoundedCornerShape(18.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VividCyan)
                                        ) {
                                            if (isSyncingPlaylists) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = VividCyan
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Syncing...", fontSize = 12.sp)
                                            } else {
                                                Icon(VictusIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Sync Playlists", fontSize = 12.sp)
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = onSignOut,
                                            shape = RoundedCornerShape(18.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                                        ) {
                                            Text("Sign Out", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        is AuthState.Expired -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x33FF5252)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Session Expired",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF5252),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Your YouTube Music session has expired. Sign in again to refresh your library.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenLogin()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Sign In Again", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        is AuthState.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = VividCyan)
                                Text("Validating session...", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant)

                // Audio Quality Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Audio Stream Quality",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = VividCyan
                    )

                    AudioQuality.values().forEach { quality ->
                        val selected = quality == currentQuality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectQuality(quality) }
                                .background(if (selected) DarkSurfaceVariant else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = quality.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (selected) TextPrimary else TextSecondary
                                )
                                Text(
                                    text = when (quality) {
                                        AudioQuality.AUTO -> "Auto-detect based on network (Recommended)"
                                        AudioQuality.HIGH -> "Highest bitrate (160kbps Opus / 256kbps M4A)"
                                        AudioQuality.MEDIUM -> "Standard quality (~128kbps)"
                                        AudioQuality.LOW -> "Data saver (~50kbps)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectQuality(quality) },
                                colors = RadioButtonDefaults.colors(selectedColor = VividCyan)
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant)

                // Cache Management Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Media Stream Cache",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = VividCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Disk Usage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = formattedCache,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    // Max cache size selector
                    Text(
                        text = "Cache Limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(256, 512, 1024).forEach { sizeMb ->
                            val isSelected = sizeMb == maxCacheSizeMb
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectMaxCacheSizeMb(sizeMb) },
                                label = { Text(if (sizeMb >= 1024) "1 GB" else "$sizeMb MB") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VividCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = VividCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                    ) {
                        Text("Clear Stream Cache")
                    }
                }

                HorizontalDivider(color = DesignTokens.HairlineBorder, thickness = 0.75.dp)

                // Appearance & Now Playing Slider Section
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = accentColor
                    )

                    Text(
                        text = "Now Playing Slider Style",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignTokens.TextSecondary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SliderStyle.entries.forEach { style ->
                            val isSelected = sliderPreferences.style == style
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .matteSurface(
                                        cornerRadius = DesignTokens.RadiusCard,
                                        surfaceColor = if (isSelected) DesignTokens.InteractiveSurface else DesignTokens.ElevatedSurface,
                                        borderColor = if (isSelected) accentColor.copy(alpha = 0.7f) else DesignTokens.HairlineBorder,
                                        borderWidth = if (isSelected) 1.dp else DesignTokens.HairlineStrokeWidth
                                    )
                                    .clickable { onSelectSliderStyle(style) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = style.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) DesignTokens.TextPrimary else DesignTokens.TextSecondary
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "Active",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accentColor
                                        )
                                    }
                                }
                                val previewColor = if (sliderPreferences.colorMode == SliderColorMode.MONOCHROME) Color.White else accentColor
                                SliderStylePreview(
                                    style = style,
                                    accentColor = previewColor,
                                    showThumb = sliderPreferences.showThumb
                                )
                            }
                        }
                    }

                    Text(
                        text = "Slider Color Mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = DesignTokens.TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SliderColorMode.entries.forEach { mode ->
                            val isSelected = sliderPreferences.colorMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectSliderColorMode(mode) },
                                label = {
                                    Text(
                                        text = if (mode == SliderColorMode.DYNAMIC) "Dynamic (Art)" else "Monochrome",
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                    selectedLabelColor = accentColor
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Thumb Handle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DesignTokens.TextPrimary
                            )
                            Text(
                                text = "Display draggable handle on Minimal & Squiggly sliders",
                                style = MaterialTheme.typography.bodySmall,
                                color = DesignTokens.TextTertiary
                            )
                        }
                        Switch(
                            checked = sliderPreferences.showThumb,
                            onCheckedChange = onToggleShowSliderThumb,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor
                            )
                        )
                    }
                }

                HorizontalDivider(color = DesignTokens.HairlineBorder, thickness = 0.75.dp)

                // Offline Storage & Downloads Section
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Offline Storage & Downloads",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = VividCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Downloaded Music Storage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = formatBytes(totalDownloadSizeBytes),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                    }

                    // Download Quality selector
                    Text(
                        text = "Download Quality",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("High", "Medium", "Low").forEach { q ->
                            val isSelected = q.equals(downloadQuality, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectDownloadQuality(q) },
                                label = { Text(q) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VividCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = VividCyan
                                )
                            )
                        }
                    }

                    // Wi-Fi only toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Download over Wi-Fi only",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Prevent cellular data usage when downloading songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                        Switch(
                            checked = downloadWifiOnly,
                            onCheckedChange = onToggleDownloadWifiOnly,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = VividCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete All Downloads")
                    }

                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmDialog = false },
                            title = { Text("Delete All Downloads?", color = TextPrimary) },
                            text = { Text("This will permanently remove all downloaded songs from offline storage. You can download them again at any time.", color = TextSecondary) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDeleteConfirmDialog = false
                                        onDeleteAllDownloads()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                                ) {
                                    Text("Delete All", color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                    Text("Cancel", color = TextSecondary)
                                }
                            },
                            containerColor = DarkSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = VividCyan, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurface
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb < 1.0) {
        "%.1f KB".format(bytes.toDouble() / 1024)
    } else {
        "%.1f MB".format(mb)
    }
}

package com.victustune.player.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victustune.player.data.model.Playlist
import com.victustune.player.data.transfer.ExportFormat
import com.victustune.player.ui.theme.*
import com.victustune.player.ui.viewmodel.ExportTarget
import com.victustune.player.ui.viewmodel.ImportDialogState

@Composable
fun PlaylistImportDialogs(
    state: ImportDialogState,
    onStartImport: (playlistName: String, matchOnline: Boolean) -> Unit,
    onCancelImport: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit
) {
    when (state) {
        is ImportDialogState.Idle -> Unit

        is ImportDialogState.Parsing -> {
            BackHandler { onDismiss() }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("Reading Playlist", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = VividCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Parsing tracks from file...",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        }

        is ImportDialogState.Preview -> {
            var playlistName by remember(state.defaultName) { mutableStateOf(state.defaultName) }
            var matchOnline by remember { mutableStateOf(true) }

            BackHandler { onDismiss() }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = VictusIcons.ImportPlaylist,
                            contentDescription = null,
                            tint = VividCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Text("Import Playlist", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${state.entries.size} tracks detected",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = state.format.name,
                                color = VividCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            label = { Text("Playlist Name", color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = VividCyan,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = VividCyan
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { matchOnline = !matchOnline }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "Match online via YouTube Music if not found locally",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Automatically finds missing tracks online",
                                    color = TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = matchOnline,
                                onCheckedChange = { matchOnline = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = VividCyan
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onStartImport(
                                playlistName.trim().ifEmpty { state.defaultName },
                                matchOnline
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                    ) {
                        Text("Import", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface
            )
        }

        is ImportDialogState.Importing -> {
            val progress = state.progress
            val fraction = if (progress.totalCount > 0) {
                progress.processedCount.toFloat() / progress.totalCount.toFloat()
            } else {
                0f
            }

            BackHandler { onCancelImport() }
            AlertDialog(
                onDismissRequest = { /* Prevent accidental dismiss while importing */ },
                title = {
                    Text("Importing Playlist", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Matching ${progress.processedCount} / ${progress.totalCount} tracks...",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            color = VividCyan,
                            trackColor = DarkSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        if (progress.currentTrackName.isNotBlank()) {
                            Text(
                                text = progress.currentTrackName,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "${progress.matchedTracks.size} matched • ${progress.unmatchedEntries.size} unmatched",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onCancelImport) {
                        Text("Cancel", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = DarkSurface
            )
        }

        is ImportDialogState.Summary -> {
            val progress = state.progress
            var showUnmatched by remember { mutableStateOf(false) }

            BackHandler { onDismiss() }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("Import Complete", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "${progress.matchedTracks.size} imported, ${progress.unmatchedEntries.size} not found",
                            color = if (progress.matchedTracks.isNotEmpty()) VividCyan else TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )

                        if (progress.unmatchedEntries.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceVariant)
                                    .clickable { showUnmatched = !showUnmatched }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Unmatched Tracks (${progress.unmatchedEntries.size})",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = if (showUnmatched) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }

                            AnimatedVisibility(visible = showUnmatched) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(progress.unmatchedEntries) { entry ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = entry.title.ifBlank { entry.sourcePathOrUrl ?: "Unknown Track" },
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (entry.artist.isNotBlank()) {
                                                Text(
                                                    text = entry.artist,
                                                    color = TextTertiary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    val created = progress.createdPlaylist
                    if (created != null) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenPlaylist(created)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                        ) {
                            Text("Open Playlist", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("OK", color = VividCyan)
                        }
                    }
                },
                dismissButton = {
                    if (progress.createdPlaylist != null) {
                        TextButton(onClick = onDismiss) {
                            Text("Done", color = TextSecondary)
                        }
                    }
                },
                containerColor = DarkSurface
            )
        }

        is ImportDialogState.Error -> {
            BackHandler { onDismiss() }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text("Import Error", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = state.message,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("OK", color = VividCyan)
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}

@Composable
fun ExportFormatPickerDialog(
    target: ExportTarget,
    onSelectFormat: (ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = VictusIcons.ExportPlaylist,
                    contentDescription = null,
                    tint = VividCyan,
                    modifier = Modifier.size(22.dp)
                )
                Text("Export Playlist", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose export format for \"${target.displayName}\":",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectFormat(ExportFormat.CSV) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "CSV (.csv)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Spreadsheet-compatible with full track metadata & source links",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectFormat(ExportFormat.M3U8) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "M3U8 (.m3u8)",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Standard UTF-8 extended media playlist format",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

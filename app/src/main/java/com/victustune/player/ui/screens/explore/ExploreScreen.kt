package com.victustune.player.ui.screens.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.victustune.player.data.model.*
import com.victustune.player.ui.components.MarqueeText
import com.victustune.player.ui.theme.*
import com.victustune.player.ui.viewmodel.ExploreUiState

@Composable
fun ExploreScreen(
    exploreState: ExploreUiState,
    onRetry: () -> Unit,
    onTrackClick: (RemoteTrack) -> Unit,
    onAlbumClick: (RemoteAlbum) -> Unit,
    onArtistClick: (RemoteArtist) -> Unit,
    onPlaylistClick: (RemotePlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (exploreState) {
            is ExploreUiState.Loading -> {
                ExploreSkeleton()
            }
            is ExploreUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Unable to load Explore feed",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = exploreState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry", color = Color.White)
                    }
                }
            }
            is ExploreUiState.Success -> {
                val result = exploreState.result
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    itemsIndexed(
                        items = result.sections,
                        key = { index, section -> "${index}_${section.title}" },
                        contentType = { _, _ -> "explore_section" }
                    ) { _, section ->
                        ExploreSectionRow(
                            section = section,
                            onTrackClick = onTrackClick,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onPlaylistClick = onPlaylistClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreSectionRow(
    section: BrowseSection,
    onTrackClick: (RemoteTrack) -> Unit,
    onAlbumClick: (RemoteAlbum) -> Unit,
    onArtistClick: (RemoteArtist) -> Unit,
    onPlaylistClick: (RemotePlaylist) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = TextPrimary
            )
            section.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tracks Section
        if (section.tracks.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(
                    items = section.tracks,
                    key = { index, track -> "${index}_${track.videoId}" },
                    contentType = { _, _ -> "explore_track_card" }
                ) { _, track ->
                    RemoteTrackCard(
                        track = track,
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }

        // Albums Section
        if (section.albums.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(
                    items = section.albums,
                    key = { index, album -> "${index}_${album.browseId}" },
                    contentType = { _, _ -> "explore_album_card" }
                ) { _, album ->
                    RemoteAlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album) }
                    )
                }
            }
        }

        // Artists Section
        if (section.artists.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(
                    items = section.artists,
                    key = { index, artist -> "${index}_${artist.browseId}" },
                    contentType = { _, _ -> "explore_artist_card" }
                ) { _, artist ->
                    RemoteArtistCard(
                        artist = artist,
                        onClick = { onArtistClick(artist) }
                    )
                }
            }
        }

        // Playlists Section
        if (section.playlists.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(
                    items = section.playlists,
                    key = { index, playlist -> "${index}_${playlist.browseId}" },
                    contentType = { _, _ -> "explore_playlist_card" }
                ) { _, playlist ->
                    RemotePlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteTrackCard(
    track: RemoteTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = com.victustune.player.data.coil.rememberThumbnailRequest(track.thumbnailUrl, sizePx = 280),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = VividCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RemoteAlbumCard(
    album: RemoteAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = com.victustune.player.data.coil.rememberThumbnailRequest(album.thumbnailUrl, sizePx = 280),
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RemoteArtistCard(
    artist: RemoteArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = com.victustune.player.data.coil.rememberThumbnailRequest(artist.thumbnailUrl, sizePx = 240),
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
fun RemotePlaylistCard(
    playlist: RemotePlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = com.victustune.player.data.coil.rememberThumbnailRequest(playlist.thumbnailUrl, sizePx = 280),
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        playlist.author?.let { author ->
            Text(
                text = author,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExploreSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(3) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceVariant)
                            )
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DarkSurfaceVariant)
                            )
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DarkSurfaceVariant)
                            )
                        }
                    }
                }
            }
        }
    }
}

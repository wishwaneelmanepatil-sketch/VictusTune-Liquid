package com.victustune.player.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.victustune.player.data.model.Album
import com.victustune.player.ui.theme.DarkSurfaceVariant
import com.victustune.player.ui.theme.TextPrimary
import com.victustune.player.ui.theme.TextSecondary

@Composable
fun AlbumsScreen(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "${albums.size} albums",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 160.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = albums,
                key = { it.id },
                contentType = { "album_grid_card" }
            ) { album ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onAlbumClick(album) }
                        .padding(4.dp)
                ) {
                    AsyncImage(
                        model = com.victustune.player.data.coil.rememberThumbnailRequest(album.albumArtUri, sizePx = 320),
                        contentDescription = "${album.title} cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = album.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${album.artist} • ${album.trackCount} tracks",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

package com.victustune.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victustune.player.ui.screens.MainTab
import com.victustune.player.ui.screens.SubScreen
import com.victustune.player.ui.theme.TextPrimary
import com.victustune.player.ui.theme.TextTertiary
import com.victustune.player.ui.theme.VictusIcons

enum class NavDockItem(val title: String, val icon: ImageVector) {
    HOME("Home", VictusIcons.Home),
    SEARCH("Search", Icons.Default.Search),
    LIBRARY("Library", VictusIcons.LibraryMusic),
    PLAYLISTS("Playlists", VictusIcons.QueueMusic)
}

@Composable
fun FloatingNavDock(
    currentTab: MainTab,
    currentSubScreen: SubScreen,
    accentColor: Color,
    onItemSelected: (NavDockItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeItem = when {
        currentSubScreen is SubScreen.Search -> NavDockItem.SEARCH
        currentSubScreen is SubScreen.FavoritesDetail ||
                currentSubScreen is SubScreen.PlaylistDetail ||
                (currentSubScreen is SubScreen.Main && currentTab == MainTab.PLAYLISTS) -> NavDockItem.PLAYLISTS
        currentSubScreen is SubScreen.AlbumDetail ||
                currentSubScreen is SubScreen.ArtistDetail ||
                (currentSubScreen is SubScreen.Main && (currentTab == MainTab.TRACKS || currentTab == MainTab.ALBUMS || currentTab == MainTab.ARTISTS || currentTab == MainTab.DOWNLOADS)) -> NavDockItem.LIBRARY
        else -> NavDockItem.HOME
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating pill container with subtle glass border and ambient shadow
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                    ambientColor = Color.Black.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xF2161622)) // 95% opacity dark obsidian slate
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.04f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    ),
                    RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavDockItem.values().forEach { item ->
                val isSelected = activeItem == item
                NavDockTabItem(
                    item = item,
                    isSelected = isSelected,
                    accentColor = accentColor,
                    onClick = { onItemSelected(item) }
                )
            }
        }
    }
}

@Composable
private fun NavDockTabItem(
    item: NavDockItem,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dockIconScale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else TextTertiary,
        animationSpec = tween(220),
        label = "dockIconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) TextPrimary else TextTertiary,
        animationSpec = tween(220),
        label = "dockTextColor"
    )

    val pillBackground by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(250),
        label = "dockPillBackground"
    )

    val pillBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = tween(250),
        label = "dockPillBorder"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(pillBackground)
            .border(
                BorderStroke(1.dp, pillBorderColor),
                RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = iconColor,
                modifier = Modifier
                    .size(22.dp)
                    .scale(scale)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victustune.player.ui.screens.MainTab
import com.victustune.player.ui.screens.SubScreen
import com.victustune.player.ui.theme.DesignTokens
import com.victustune.player.ui.theme.VictusIcons

enum class NavDockItem(
    val title: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
) {
    HOME("Home", VictusIcons.Home, VictusIcons.HomeOutlined),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search),
    LIBRARY("Library", VictusIcons.LibraryMusic, VictusIcons.LibraryMusicOutlined),
    PLAYLISTS("Playlists", VictusIcons.QueueMusic, VictusIcons.PlaylistsOutlined)
}

@Composable
fun NavigationDock(
    currentTab: MainTab,
    currentSubScreen: SubScreen,
    accentColor: Color,
    onItemSelected: (NavDockItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val activeItem = when {
        currentSubScreen is SubScreen.Search -> NavDockItem.SEARCH
        currentSubScreen is SubScreen.FavoritesDetail ||
                currentSubScreen is SubScreen.PlaylistDetail ||
                (currentSubScreen is SubScreen.Main && currentTab == MainTab.PLAYLISTS) -> NavDockItem.PLAYLISTS
        currentSubScreen is SubScreen.AlbumDetail ||
                currentSubScreen is SubScreen.ArtistDetail ||
                (currentSubScreen is SubScreen.Main && (
                    currentTab == MainTab.TRACKS ||
                        currentTab == MainTab.ALBUMS ||
                        currentTab == MainTab.ARTISTS ||
                        currentTab == MainTab.DOWNLOADS
                    )) -> NavDockItem.LIBRARY
        else -> NavDockItem.HOME
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.SpaceMd),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = DesignTokens.DockShape,
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black.copy(alpha = 0.35f)
                )
                .clip(DesignTokens.DockShape)
                .background(DesignTokens.ElevatedSurface)
                .border(
                    BorderStroke(1.dp, DesignTokens.HairlineBorder),
                    DesignTokens.DockShape
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavDockItem.entries.forEach { item ->
                val isSelected = activeItem == item
                NavDockTabItem(
                    item = item,
                    isSelected = isSelected,
                    accentColor = accentColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onItemSelected(item)
                    },
                    modifier = Modifier.weight(1f)
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dockIndicatorScale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else DesignTokens.TextTertiary,
        animationSpec = tween(220),
        label = "dockIconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) DesignTokens.TextPrimary else DesignTokens.TextTertiary,
        animationSpec = tween(220),
        label = "dockTextColor"
    )

    val pillBackground by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(220),
        label = "dockPillBackground"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(indicatorScale)
            .clip(DesignTokens.DockShape)
            .background(pillBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                contentDescription = item.title,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

package com.victustune.player.ui.screens.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.victustune.player.data.model.QueueEntry
import com.victustune.player.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queueEntries: List<QueueEntry>,
    currentQueueIndex: Int,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    // Local working copy during drag so swaps happen at 60/120fps and commit once on drop
    var localQueue by remember { mutableStateOf(queueEntries) }
    var draggedUid by remember { mutableStateOf<String?>(null) }
    var initialDragIndex by remember { mutableIntStateOf(-1) }
    var currentDragIndex by remember { mutableIntStateOf(-1) }
    var startItemTopPx by remember { mutableFloatStateOf(0f) }
    var totalDragYPx by remember { mutableFloatStateOf(0f) }
    var draggedItemHeightPx by remember { mutableIntStateOf(0) }

    val currentPlayingUid = remember(queueEntries, currentQueueIndex) {
        queueEntries.getOrNull(currentQueueIndex)?.uid
    }

    LaunchedEffect(queueEntries, draggedUid) {
        if (draggedUid == null) {
            localQueue = queueEntries
        }
    }

    fun checkAndSwapIfNeeded() {
        val uid = draggedUid ?: return
        val currIdx = currentDragIndex
        if (currIdx !in localQueue.indices) return

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val currInfo = visibleItems.firstOrNull { it.key == uid }
        val itemSize = currInfo?.size ?: draggedItemHeightPx
        val desiredTop = startItemTopPx + totalDragYPx
        val desiredCenter = desiredTop + itemSize / 2f

        val firstVisibleIdx = listState.firstVisibleItemIndex
        val firstVisibleOffset = listState.firstVisibleItemScrollOffset

        // Check downward swap
        if (currIdx < localQueue.lastIndex) {
            val nextUid = localQueue[currIdx + 1].uid
            val nextInfo = visibleItems.firstOrNull { it.key == nextUid }
            if (nextInfo != null) {
                val nextCenter = nextInfo.offset + nextInfo.size / 2f
                if (desiredCenter > nextCenter) {
                    val updated = localQueue.toMutableList()
                    val moved = updated.removeAt(currIdx)
                    updated.add(currIdx + 1, moved)
                    if (currIdx == firstVisibleIdx || currIdx + 1 == firstVisibleIdx) {
                        listState.requestScrollToItem(firstVisibleIdx, firstVisibleOffset)
                    }
                    localQueue = updated
                    currentDragIndex = currIdx + 1
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    return
                }
            }
        }

        // Check upward swap
        if (currIdx > 0) {
            val prevUid = localQueue[currIdx - 1].uid
            val prevInfo = visibleItems.firstOrNull { it.key == prevUid }
            if (prevInfo != null) {
                val prevCenter = prevInfo.offset + prevInfo.size / 2f
                if (desiredCenter < prevCenter) {
                    val updated = localQueue.toMutableList()
                    val moved = updated.removeAt(currIdx)
                    updated.add(currIdx - 1, moved)
                    if (currIdx == firstVisibleIdx || currIdx - 1 == firstVisibleIdx) {
                        listState.requestScrollToItem(firstVisibleIdx, firstVisibleOffset)
                    }
                    localQueue = updated
                    currentDragIndex = currIdx - 1
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    return
                }
            }
        }
    }

    // Smooth edge auto-scrolling while dragging near top or bottom of LazyColumn viewport
    LaunchedEffect(draggedUid) {
        if (draggedUid == null) return@LaunchedEffect
        val edgeThresholdPx = with(density) { 72.dp.toPx() }
        val maxScrollPerFramePx = with(density) { 14.dp.toPx() }

        while (isActive && draggedUid != null) {
            val layoutInfo = listState.layoutInfo
            val viewportStart = layoutInfo.viewportStartOffset.toFloat()
            val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
            val currInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedUid }
            val itemHeight = (currInfo?.size ?: draggedItemHeightPx).toFloat()

            val desiredTop = startItemTopPx + totalDragYPx
            val desiredBottom = desiredTop + itemHeight

            val scrollDelta = when {
                desiredTop < viewportStart + edgeThresholdPx && listState.canScrollBackward -> {
                    val proximity = ((viewportStart + edgeThresholdPx - desiredTop) / edgeThresholdPx)
                        .coerceIn(0f, 1f)
                    -(maxScrollPerFramePx * (0.25f + 0.75f * proximity))
                }
                desiredBottom > viewportEnd - edgeThresholdPx && listState.canScrollForward -> {
                    val proximity = ((desiredBottom - (viewportEnd - edgeThresholdPx)) / edgeThresholdPx)
                        .coerceIn(0f, 1f)
                    maxScrollPerFramePx * (0.25f + 0.75f * proximity)
                }
                else -> 0f
            }

            if (scrollDelta != 0f) {
                val consumed = listState.scrollBy(scrollDelta)
                if (consumed != 0f) {
                    checkAndSwapIfNeeded()
                }
            }
            delay(16L)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextTertiary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "${localQueue.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close queue",
                        tint = TextSecondary
                    )
                }
            }

            if (localQueue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue is empty",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(
                        items = localQueue,
                        key = { _, entry -> entry.uid },
                        contentType = { _, _ -> "queue_entry" }
                    ) { index, entry ->
                        val song = entry.song
                        val isDragged = entry.uid == draggedUid
                        val isCurrent = if (currentPlayingUid != null) {
                            entry.uid == currentPlayingUid
                        } else {
                            index == currentQueueIndex
                        }

                        val scale by animateFloatAsState(
                            targetValue = if (isDragged) 1.03f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "queueRowScale"
                        )

                        val rowContainerColor = when {
                            isDragged -> DarkSurfaceVariant
                            isCurrent -> accentColor.copy(alpha = 0.16f)
                            else -> DarkSurfaceVariant.copy(alpha = 0.5f)
                        }

                        val latestIndex by rememberUpdatedState(index)
                        val latestOnRemove by rememberUpdatedState(onRemove)

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart && draggedUid == null) {
                                    latestOnRemove(latestIndex)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        val rowModifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                placementSpec = if (isDragged) null else spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                if (isDragged) {
                                    val itemInfo = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == entry.uid }
                                    val currentOffset = itemInfo?.offset?.toFloat() ?: startItemTopPx
                                    translationY = (startItemTopPx + totalDragYPx) - currentOffset
                                } else {
                                    translationY = 0f
                                }
                                shadowElevation = if (isDragged) 16.dp.toPx() else 0f
                            }

                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = rowModifier,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = draggedUid == null,
                            gesturesEnabled = draggedUid == null,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x33FF5252))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = VictusIcons.DeleteOutline,
                                        contentDescription = "Remove from queue",
                                        tint = Color(0xFFFF5252)
                                    )
                                }
                            }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = draggedUid == null) { onSongClick(latestIndex) },
                                color = rowContainerColor,
                                tonalElevation = if (isDragged) 8.dp else 0.dp,
                                shadowElevation = if (isDragged) 8.dp else 0.dp,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = com.victustune.player.data.coil.rememberThumbnailRequest(song.albumArtUri, sizePx = 144),
                                        contentDescription = "Cover",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) accentColor else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Remove button
                                    IconButton(
                                        onClick = { latestOnRemove(latestIndex) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = VictusIcons.DeleteOutline,
                                            contentDescription = "Remove",
                                            tint = TextTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Dedicated Drag Handle (hidden on single-item queues)
                                    if (localQueue.size > 1) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .pointerInput(entry.uid) {
                                                    awaitEachGesture {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        var slopDeltaY = 0f
                                                        val dragStartChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                                            change.consume()
                                                            slopDeltaY = over.y
                                                        }
                                                        if (dragStartChange != null) {
                                                            val startIdx = localQueue.indexOfFirst { it.uid == entry.uid }
                                                            val itemInfo = listState.layoutInfo.visibleItemsInfo
                                                                .firstOrNull { it.key == entry.uid }
                                                            if (startIdx >= 0 && itemInfo != null) {
                                                                draggedUid = entry.uid
                                                                initialDragIndex = startIdx
                                                                currentDragIndex = startIdx
                                                                startItemTopPx = itemInfo.offset.toFloat()
                                                                draggedItemHeightPx = itemInfo.size
                                                                totalDragYPx = slopDeltaY
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                checkAndSwapIfNeeded()

                                                                val finished = try {
                                                                    drag(down.id) { change ->
                                                                        val delta = change.positionChange()
                                                                        change.consume()
                                                                        totalDragYPx += delta.y
                                                                        checkAndSwapIfNeeded()
                                                                    }
                                                                } catch (_: Exception) {
                                                                    false
                                                                }

                                                                val from = initialDragIndex
                                                                val to = currentDragIndex
                                                                draggedUid = null
                                                                initialDragIndex = -1
                                                                currentDragIndex = -1
                                                                totalDragYPx = 0f

                                                                if (finished && from >= 0 && to >= 0 && from != to) {
                                                                    onMoveItem(from, to)
                                                                } else if (!finished) {
                                                                    localQueue = queueEntries
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = VictusIcons.DragHandle,
                                                contentDescription = "Reorder track",
                                                tint = if (isDragged) accentColor else TextTertiary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

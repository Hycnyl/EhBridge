package com.hycnyl.ehbridge.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hycnyl.ehbridge.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Fast scrolling reveals a Material thumb without adding chrome to ordinary reading. */
@Composable
fun FastScrollContainer(listState: LazyListState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        content()
        FastScrollThumb(listState, maxHeight, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun FastScrollThumb(listState: LazyListState, availableHeight: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val revealEvents = remember(listState) { Channel<Unit>(Channel.CONFLATED) }
    var showThumb by remember { mutableStateOf(false) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(listState, density.density) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        var previousTime = SystemClock.uptimeMillis()
        var wasScrolling = false
        var previousItems = listState.layoutInfo.visibleItemsInfo.associate { it.index to it.offset }
        snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress) }
            .collect { (index, offset, scrolling) ->
                val now = SystemClock.uptimeMillis()
                val elapsed = now - previousTime
                val info = listState.layoutInfo
                val averageSize = info.visibleItemsInfo.map { it.size + info.mainAxisItemSpacing }.average().takeIf { it.isFinite() } ?: 1.0
                if (scrolling && wasScrolling && !dragging && elapsed in 1..180) {
                    val anchor = info.visibleItemsInfo.firstOrNull { it.index in previousItems }
                    val distance = anchor?.let { previousItems.getValue(it.index).toDouble() - it.offset }
                        ?: ((index - previousIndex) * averageSize + offset - previousOffset)
                    if (abs(distance) * 1000 / elapsed > 1200 * density.density) revealEvents.trySend(Unit)
                }
                previousItems = info.visibleItemsInfo.associate { it.index to it.offset }
                previousIndex = index
                previousOffset = offset
                previousTime = now
                wasScrolling = scrolling
            }
    }
    LaunchedEffect(revealEvents) {
        revealEvents.consumeAsFlow().collectLatest {
            showThumb = true
            delay(1200)
            if (!dragging) showThumb = false
        }
    }
    DisposableEffect(listState) { onDispose { scrollJob?.cancel() } }

    // Equality ignores scroll offsets, so geometry updates only when count or sizes change.
    val geometry by remember(listState) {
        derivedStateOf(structuralEqualityPolicy()) {
            val info = listState.layoutInfo
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
            val average = info.visibleItemsInfo.map { it.size + info.mainAxisItemSpacing }.average().takeIf { it.isFinite() && it > 0 } ?: 1.0
            ScrollGeometry(info.totalItemsCount, viewport, average,
                info.totalItemsCount > 8 && (listState.canScrollForward || listState.canScrollBackward))
        }
    }
    val visibleCount = (geometry.viewport / geometry.averageSize).coerceAtLeast(1.0)
    val lastFirstIndex = (geometry.totalItems - visibleCount).coerceAtLeast(1.0)
    val position = { scrollPosition(listState, geometry.averageSize, lastFirstIndex) }
    val currentFraction = { if (dragging) dragFraction else position() }
    val proportion = (visibleCount / geometry.totalItems.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    val thumbHeight = (availableHeight * proportion).coerceIn(48.dp, 120.dp).coerceAtMost(availableHeight)
    val travel = with(density) { (availableHeight - thumbHeight).toPx().coerceAtLeast(1f) }
    val description = stringResource(R.string.fast_scroll)
    val jumpTo: (Float) -> Unit = { requested ->
        val lastIndex = (geometry.totalItems - 1).coerceAtLeast(0)
        val index = if (requested >= .999f) lastIndex else (requested.coerceIn(0f, 1f) * lastFirstIndex).roundToInt().coerceIn(0, lastIndex)
        scrollJob?.cancel()
        scrollJob = scope.launch { listState.scrollToItem(index) }
    }

    AnimatedVisibility(
        visible = geometry.scrollable && showThumb,
        enter = fadeIn(tween(100)), exit = fadeOut(tween(250)),
        modifier = modifier.offset { IntOffset(0, (travel * currentFraction()).roundToInt()) }
    ) {
        Box(
            Modifier.width(48.dp).height(thumbHeight).testTag("fast_scroll_thumb")
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(currentFraction(), 0f..1f)
                    setProgress { jumpTo(it); revealEvents.trySend(Unit); true }
                }
                .draggable(
                    state = rememberDraggableState { delta ->
                        dragFraction = (dragFraction + delta / travel).coerceIn(0f, 1f)
                        jumpTo(dragFraction)
                    },
                    orientation = Orientation.Vertical,
                    startDragImmediately = listState.isScrollInProgress,
                    onDragStarted = {
                        dragFraction = position()
                        dragging = true
                        showThumb = true
                        listState.stopScroll()
                    },
                    onDragStopped = {
                        dragging = false
                        revealEvents.trySend(Unit)
                    }
                ), contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                Modifier.padding(end = 4.dp).width(if (dragging) 10.dp else 7.dp).fillMaxHeight(),
                shape = CircleShape,
                color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                shadowElevation = if (dragging) 3.dp else 1.dp
            ) {}
        }
    }
}

private data class ScrollGeometry(val totalItems: Int, val viewport: Int, val averageSize: Double, val scrollable: Boolean)

private fun scrollPosition(listState: LazyListState, averageSize: Double, lastFirstIndex: Double): Float =
    ((listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset / averageSize) / lastFirstIndex).toFloat().coerceIn(0f, 1f)

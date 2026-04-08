/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.list.ReorderListEvent.ItemMoved

/**
 * Adapted from the AndroidX Compose demo
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/integration-tests/foundation-demos/src/main/java/androidx/compose/foundation/demos/LazyColumnDragAndDropDemo.kt
 *
 * - Allows for dragging and dropping to reorder within lazy columns.
 * - Supports adding non-draggable headers and footers.
 */
@Composable
fun rememberReorderableListState(
  lazyListState: LazyListState,
  includeHeader: Boolean,
  includeFooter: Boolean,
  onEvent: (ReorderListEvent) -> Unit = {}
): ReorderableListState {
  val scope = rememberCoroutineScope()
  val state = remember(lazyListState) {
    ReorderableListState(state = lazyListState, onEvent = onEvent, includeHeader = includeHeader, includeFooter = includeFooter, scope = scope)
  }
  val maxAutoScrollSpeed = with(LocalDensity.current) { 30.dp.toPx() }
  val baseAutoScrollSpeed = with(LocalDensity.current) { 10.dp.toPx() }
  val scrollAcceleration = 2f

  LaunchedEffect(state) {
    while (true) {
      withFrameNanos { }

      val overscrollAmount = state.dragOverscrollAmount
      if (overscrollAmount != 0f) {
        val scrollDirection = if (overscrollAmount < 0f) -1f else 1f
        val scrollAmount = (scrollDirection * baseAutoScrollSpeed + overscrollAmount * scrollAcceleration)
          .coerceIn(-maxAutoScrollSpeed, maxAutoScrollSpeed)
        lazyListState.scrollBy(scrollAmount)

        state.swapDraggingItemIfNeeded()
      }
    }
  }
  return state
}

class ReorderableListState internal constructor(
  private val state: LazyListState,
  private val scope: CoroutineScope,
  private val includeHeader: Boolean,
  private val includeFooter: Boolean,
  private val onEvent: (ReorderListEvent) -> Unit
) {
  var draggingItemIndex by mutableStateOf<Int?>(null)
    private set

  var dragOverscrollAmount by mutableFloatStateOf(0f)
    private set

  private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
  private var draggingItemInitialOffset by mutableIntStateOf(0)

  internal val draggingItemOffset: Float
    get() = draggingItemLayoutInfo?.let { item ->
      draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
    } ?: 0f

  private val draggingItemLayoutInfo: LazyListItemInfo?
    get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

  internal var previousIndexOfDraggedItem by mutableStateOf<Int?>(null)
    private set

  internal var previousItemOffset = Animatable(0f)
    private set

  internal fun onDragStart(offset: Offset) {
    state.layoutInfo.visibleItemsInfo
      .firstOrNull { item ->
        offset.y.toInt() in item.offset..(item.offset + item.size) &&
          (!includeHeader || item.index != 0) &&
          (!includeFooter || item.index != (state.layoutInfo.totalItemsCount - 1))
      }
      ?.also {
        draggingItemIndex = it.index
        draggingItemInitialOffset = it.offset
      }
  }

  internal fun onDragEnd() {
    onDragInterrupted()
    onEvent(ReorderListEvent.ItemDropped)
  }

  internal fun onDragCancel() {
    onDragInterrupted()
    onEvent(ReorderListEvent.DragCanceled)
  }

  private fun onDragInterrupted() {
    if (draggingItemIndex != null) {
      previousIndexOfDraggedItem = draggingItemIndex
      val startOffset = draggingItemOffset
      scope.launch {
        previousItemOffset.snapTo(startOffset)
        previousItemOffset.animateTo(
          0f,
          spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f)
        )
        previousIndexOfDraggedItem = null
      }
    }

    draggingItemDraggedDelta = 0f
    draggingItemIndex = null
    draggingItemInitialOffset = 0
    dragOverscrollAmount = 0f
  }

  internal fun onDrag(offset: Offset, change: PointerInputChange) {
    if ((includeHeader && draggingItemIndex == 0) ||
      (includeFooter && draggingItemIndex == (state.layoutInfo.totalItemsCount - 1))
    ) return

    change.consume()

    draggingItemDraggedDelta += offset.y

    val draggingItem = draggingItemLayoutInfo
    val isDraggingItemOffScreen = draggingItem == null
    if (isDraggingItemOffScreen) {
      draggingItemIndex?.let { itemIndex ->
        val firstVisibleIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: Int.MAX_VALUE
        val lastVisibleIndex = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: Int.MIN_VALUE
        val scrollingDownPastItem = itemIndex < firstVisibleIndex && dragOverscrollAmount > 0
        val scrollingUpPastItem = itemIndex > lastVisibleIndex && dragOverscrollAmount < 0
        if (scrollingDownPastItem || scrollingUpPastItem) {
          // stop auto-scroll to guard against runaway scrolling
          dragOverscrollAmount = 0f
        }
      }
      return
    }

    val startOffset = draggingItem.offset + draggingItemOffset
    val endOffset = startOffset + draggingItem.size

    findSwapTarget(draggingItem, startOffset, endOffset)
      ?.let { targetItem -> performSwap(draggingItem, targetItem) }

    val topOverscrollAmount = (startOffset - state.layoutInfo.viewportStartOffset).coerceAtMost(0f)
    val bottomOverscrollAmount = (endOffset - state.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
    dragOverscrollAmount = when {
      bottomOverscrollAmount > 0f -> bottomOverscrollAmount
      else -> topOverscrollAmount
    }
  }

  fun swapDraggingItemIfNeeded() {
    val draggingItem = draggingItemLayoutInfo ?: return
    val startOffset = draggingItem.offset + draggingItemOffset
    val endOffset = startOffset + draggingItem.size
    findSwapTarget(draggingItem, startOffset, endOffset)
      ?.let { targetItem -> performSwap(draggingItem, targetItem) }
  }

  private fun findSwapTarget(draggingItem: LazyListItemInfo, startOffset: Float, endOffset: Float): LazyListItemInfo? {
    val middleOffset = startOffset + (endOffset - startOffset) / 2f

    return state.layoutInfo.visibleItemsInfo.find { item ->
      when {
        item.index == draggingItem.index -> false
        includeHeader && item.index == 0 -> false
        includeFooter && item.index == (state.layoutInfo.totalItemsCount - 1) -> false

        item.index > draggingItem.index -> {
          val centerOfDraggedItem = middleOffset.toInt()
          val centerOfItemBelow = item.offset + item.size / 2
          val draggedItemOverlapsItemBelow = centerOfDraggedItem in item.offset..item.offsetEnd
          draggedItemOverlapsItemBelow && centerOfDraggedItem >= centerOfItemBelow
        }

        else -> {
          val isDirectlyAboveDraggingItem = item.index == draggingItem.index - 1
          val topOfItemAbove = item.offset.toFloat()
          isDirectlyAboveDraggingItem && endOffset <= topOfItemAbove
        }
      }
    }
  }

  private fun performSwap(draggingItem: LazyListItemInfo, targetItem: LazyListItemInfo) {
    if (includeHeader) {
      onEvent.invoke(ItemMoved(fromIndex = draggingItem.index - 1, toIndex = targetItem.index - 1))
    } else {
      onEvent.invoke(ItemMoved(fromIndex = draggingItem.index, toIndex = targetItem.index))
    }
    draggingItemIndex = targetItem.index
  }

  private val LazyListItemInfo.offsetEnd: Int
    get() = this.offset + this.size
}

sealed interface ReorderListEvent {
  /**
   * Triggered when an item is moving from one position to another.
   *
   * The ordering of the corresponding UI state should be updated when this event is received.
   */
  data class ItemMoved(val fromIndex: Int, val toIndex: Int) : ReorderListEvent

  /**
   * Triggered when a dragged item is dropped into its final position.
   */
  data object ItemDropped : ReorderListEvent

  /**
   * Triggered when a drag gesture is canceled.
   */
  data object DragCanceled : ReorderListEvent
}

/**
 * Enables drag-to-reorder functionality within a container.
 *
 * @param reorderableListState The state managing the drag operation.
 * @param dragHandleWidth Width of the draggable area (positioned at the end of the container).
 */
@Composable
fun Modifier.reorderableList(
  reorderableListState: ReorderableListState,
  dragHandleWidth: Dp
): Modifier {
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  return pointerInput(reorderableListState, dragHandleWidth, isRtl) {
    val containerWidthPx = size.width.toFloat()
    val handleWidthPx = dragHandleWidth.toPx()

    val dragHandleXRange = if (isRtl) {
      0f..handleWidthPx
    } else {
      (containerWidthPx - handleWidthPx)..containerWidthPx
    }

    detectDragGestures(
      dragHandleXRange = dragHandleXRange,
      onDrag = { change, offset -> reorderableListState.onDrag(offset = offset, change = change) },
      onDragStart = { offset -> reorderableListState.onDragStart(offset) },
      onDragEnd = { reorderableListState.onDragEnd() },
      onDragCancel = { reorderableListState.onDragCancel() }
    )
  }
}

@Composable
fun LazyItemScope.ReorderableItem(
  reorderableListState: ReorderableListState,
  index: Int,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.(isDragging: Boolean) -> Unit
) {
  val dragging = index == reorderableListState.draggingItemIndex
  val draggingModifier =
    if (dragging) {
      Modifier
        .zIndex(1f)
        .graphicsLayer { translationY = reorderableListState.draggingItemOffset }
    } else if (index == reorderableListState.previousIndexOfDraggedItem) {
      Modifier
        .zIndex(1f)
        .graphicsLayer { translationY = reorderableListState.previousItemOffset.value }
    } else {
      Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
    }
  Column(modifier = modifier.then(draggingModifier)) { content(dragging) }
}

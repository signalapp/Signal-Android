/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.list

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/** How close to an edge the pointer has to get before the grid starts scrolling under it. */
private val AUTO_SCROLL_HOT_ZONE = 56.dp

/** Per-frame scroll distance at the outer and inner edge of a hot zone. */
private val MAX_AUTO_SCROLL_SPEED = 30.dp
private val MIN_AUTO_SCROLL_SPEED = 10.dp

/**
 * Range selection by dragging across a lazy grid.
 *
 * A long press arms the gesture, so taps and scrolling still work; dragging from there grows a contiguous range out
 * from the pressed item, and retracting it back towards that item takes the range away again. Nearing the top or bottom
 * edge scrolls the grid, and the range keeps growing while the pointer sits there.
 *
 * The handler owns the selection itself — this only reports which item indices the gesture has covered and uncovered.
 * It may also refuse the gesture outright by calling [DragToSelectState.cancel] from [DragSelectEvent.Started], which
 * is how a caller keeps a drag from starting on an item that is already selected.
 */
@Composable
fun rememberDragToSelectState(
  lazyGridState: LazyGridState,
  onEvent: DragToSelectState.(DragSelectEvent) -> Unit
): DragToSelectState {
  val hapticFeedback = LocalHapticFeedback.current
  val density = LocalDensity.current
  val currentOnEvent by rememberUpdatedState(onEvent)

  val state = remember(lazyGridState, hapticFeedback, density) {
    DragToSelectState(
      state = lazyGridState,
      hapticFeedback = hapticFeedback,
      autoScrollHotZone = with(density) { AUTO_SCROLL_HOT_ZONE.toPx() },
      onEvent = { dragToSelectState, event -> currentOnEvent(dragToSelectState, event) }
    )
  }

  val minAutoScrollSpeed = with(density) { MIN_AUTO_SCROLL_SPEED.toPx() }
  val maxAutoScrollSpeed = with(density) { MAX_AUTO_SCROLL_SPEED.toPx() }

  // Gated on actually being in a hot zone: waiting on frames is what asks for the next one, so an ungated loop would
  // hold the grid at the display's frame rate for as long as it is on screen.
  LaunchedEffect(state) {
    snapshotFlow { state.autoScrollDepth != 0f }
      .distinctUntilChanged()
      .collectLatest { isAutoScrolling ->
        while (isAutoScrolling) {
          withFrameNanos { }

          val depth = state.autoScrollDepth
          val direction = if (depth < 0f) -1f else 1f
          lazyGridState.scrollBy(direction * (minAutoScrollSpeed + (maxAutoScrollSpeed - minAutoScrollSpeed) * abs(depth)))

          // The pointer has not moved, but the items under it have.
          state.extendSelectionToPointer()
        }
      }
  }

  return state
}

class DragToSelectState internal constructor(
  private val state: LazyGridState,
  private val hapticFeedback: HapticFeedback,
  private val autoScrollHotZone: Float,
  private val onEvent: (DragToSelectState, DragSelectEvent) -> Unit
) {

  /** How far into an edge hot zone the pointer is, from 0 to 1, negative towards the start of the grid. */
  internal var autoScrollDepth by mutableFloatStateOf(0f)
    private set

  private var anchorIndex: Int? by mutableStateOf(null)
  private var coveredRange: IntRange? = null
  private var pointerPosition: Offset? = null

  /** Whether a range is currently being dragged out. */
  val isActive: Boolean
    get() = anchorIndex != null

  /**
   * Gives up the gesture where it stands, leaving the selection as it is. The pointer is still down at this point, so
   * the rest of the gesture is ignored rather than waiting for it to lift.
   */
  fun cancel() {
    anchorIndex = null
    coveredRange = null
    pointerPosition = null
    autoScrollDepth = 0f
  }

  internal fun onDragStart(position: Offset) {
    val index = indexAt(position) ?: return

    anchorIndex = index
    coveredRange = index..index
    pointerPosition = position
    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

    onEvent(this, DragSelectEvent.Started(index))

    if (isActive) {
      onEvent(this, DragSelectEvent.RangeSelected(setOf(index)))
    }
  }

  internal fun onDrag(position: Offset) {
    if (!isActive) {
      return
    }

    pointerPosition = position
    autoScrollDepth = autoScrollDepthAt(position)
    extendSelection(position)
  }

  internal fun onDragEnd() = cancel()

  /** Re-runs the hit test where the pointer already is, for when the grid moves rather than the pointer. */
  internal fun extendSelectionToPointer() {
    pointerPosition?.let { extendSelection(it) }
  }

  private fun extendSelection(position: Offset) {
    val anchor = anchorIndex ?: return
    val covered = coveredRange ?: return
    val index = indexAt(position) ?: return

    val updated = minOf(anchor, index)..maxOf(anchor, index)
    if (updated == covered) {
      return
    }

    coveredRange = updated

    val selected = updated.filterNot { it in covered }.toSet()
    val unselected = covered.filterNot { it in updated }.toSet()

    if (selected.isNotEmpty()) {
      hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
      onEvent(this, DragSelectEvent.RangeSelected(selected))
    }

    if (unselected.isNotEmpty()) {
      hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
      onEvent(this, DragSelectEvent.RangeUnselected(unselected))
    }
  }

  /**
   * Zero unless the pointer is within [autoScrollHotZone] of an edge of the viewport. Hot zones are capped at a third of
   * the viewport each, so that a short grid still leaves somewhere in the middle that does not scroll.
   */
  private fun autoScrollDepthAt(position: Offset): Float {
    val viewportStart = state.layoutInfo.viewportStartOffset
    val viewportEnd = state.layoutInfo.viewportEndOffset
    val hotZone = autoScrollHotZone.coerceAtMost((viewportEnd - viewportStart) / 3f)

    if (hotZone <= 0f) {
      return 0f
    }

    return when {
      position.y < viewportStart + hotZone -> -((viewportStart + hotZone - position.y) / hotZone).coerceAtMost(1f)
      position.y > viewportEnd - hotZone -> ((position.y - (viewportEnd - hotZone)) / hotZone).coerceAtMost(1f)
      else -> 0f
    }
  }

  private fun indexAt(position: Offset): Int? {
    return state.layoutInfo.visibleItemsInfo.firstOrNull { position in it }?.index
  }

  private operator fun LazyGridItemInfo.contains(position: Offset): Boolean {
    return position.x >= offset.x &&
      position.x < offset.x + size.width &&
      position.y >= offset.y &&
      position.y < offset.y + size.height
  }
}

sealed interface DragSelectEvent {
  /**
   * A long press has landed on the item at [index] and a range is about to be dragged out from it.
   *
   * The item is reported before it is selected, so that a handler that does not want the gesture — because the item is
   * already selected, say — can call [DragToSelectState.cancel] and be sure nothing was selected on its behalf.
   */
  data class Started(val index: Int) : DragSelectEvent

  /** The range has grown over [indices]. */
  data class RangeSelected(val indices: Set<Int>) : DragSelectEvent

  /** The range has retracted back over [indices], which it had previously selected. */
  data class RangeUnselected(val indices: Set<Int>) : DragSelectEvent
}

/**
 * Enables drag-to-select within a lazy grid. Apply to the grid itself, not to its items.
 */
@Composable
fun Modifier.dragToSelect(dragToSelectState: DragToSelectState): Modifier {
  return pointerInput(dragToSelectState) {
    detectDragGestures(
      dragHandleXRange = null,
      onDragStart = { offset -> dragToSelectState.onDragStart(offset) },
      onDragEnd = { dragToSelectState.onDragEnd() },
      onDragCancel = { dragToSelectState.onDragEnd() },
      onDrag = { change, _ -> dragToSelectState.onDrag(change.position) }
    )
  }
}

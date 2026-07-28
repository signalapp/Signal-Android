/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Holds the order a drag is building up so that the backing state only has to be updated once, when the item is
 * dropped, rather than on every swap. Anything else driven by that state - a pager, a database write, an adjacent list
 * - would otherwise churn on every swap for the length of the drag.
 *
 * Render [items] and hand [onReorderListEvent][ReorderBuffer.onReorderListEvent] to [rememberReorderableListState].
 */
@Composable
fun <T> rememberReorderBuffer(
  items: List<T>,
  onReorder: (fromIndex: Int, toIndex: Int) -> Unit
): ReorderBuffer<T> {
  val currentItems by rememberUpdatedState(items)
  val currentOnReorder by rememberUpdatedState(onReorder)

  val buffer = remember {
    ReorderBuffer(
      source = { currentItems },
      onReorder = { fromIndex, toIndex -> currentOnReorder(fromIndex, toIndex) }
    )
  }

  // Hold the dragged order until the reorder lands in the backing list, otherwise the list snaps back to its pre-drag
  // order for the frame between dropping an item and the new state arriving.
  LaunchedEffect(items) {
    buffer.onSourceChanged()
  }

  return buffer
}

class ReorderBuffer<T> internal constructor(
  private val source: () -> List<T>,
  private val onReorder: (fromIndex: Int, toIndex: Int) -> Unit
) {
  private var dragOrder: List<T>? by mutableStateOf(null)
  private var dragStartIndex: Int? = null
  private var dragEndIndex: Int? = null

  /** The order to render: the order the drag has built up while one is in progress, otherwise the backing list. */
  val items: List<T>
    get() = dragOrder ?: source()

  /**
   * Whether a reorder is still in flight, either because the item is being dragged or because it has been dropped but
   * the resulting order has not reached the backing list yet. Anything that reacts to the list settling should wait for
   * this to clear rather than for the drag to end, or it will act on the pre-drag order first and correct itself after.
   */
  val isReordering: Boolean
    get() = dragOrder != null

  fun onReorderListEvent(event: ReorderListEvent) {
    when (event) {
      is ReorderListEvent.ItemMoved -> onItemMoved(event.fromIndex, event.toIndex)
      ReorderListEvent.ItemDropped, ReorderListEvent.DragCanceled -> onDragFinished()
    }
  }

  private fun onItemMoved(fromIndex: Int, toIndex: Int) {
    val current = items.toMutableList()
    if (fromIndex !in current.indices || toIndex !in current.indices) {
      return
    }

    current.add(toIndex, current.removeAt(fromIndex))

    dragOrder = current
    dragStartIndex = dragStartIndex ?: fromIndex
    dragEndIndex = toIndex
  }

  private fun onDragFinished() {
    val fromIndex = dragStartIndex
    val toIndex = dragEndIndex

    dragStartIndex = null
    dragEndIndex = null

    if (fromIndex != null && toIndex != null && fromIndex != toIndex) {
      onReorder(fromIndex, toIndex)
    } else {
      dragOrder = null
    }
  }

  internal fun onSourceChanged() {
    if (dragStartIndex == null) {
      dragOrder = null
    }
  }
}

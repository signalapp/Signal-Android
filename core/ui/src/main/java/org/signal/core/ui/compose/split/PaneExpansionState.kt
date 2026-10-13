/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Drives how wide the list pane is, in pixels.
 */
@Stable
internal class PaneExpansionState(
  private val density: Density,
  initialOffsetPx: Float,
  private val onAnchorSelected: (PaneAnchor) -> Unit
) {
  private var offsetPx by mutableFloatStateOf(initialOffsetPx)

  private var anchorOffsets: Map<PaneAnchor, Float> by mutableStateOf(emptyMap())

  private val animationMutex = MutatorMutex()

  private var isDragging = false

  /** Current width of the list pane. */
  val listWidth: Dp
    get() = with(density) { offsetPx.toDp() }

  /** Publishes the widths each anchor corresponds to, from layout. */
  fun updateAnchors(offsets: Map<PaneAnchor, Float>) {
    anchorOffsets = offsets
  }

  /**
   * Applies a drag delta.
   */
  fun dragBy(delta: Float) {
    val min = anchorOffsets.values.minOrNull() ?: return
    val max = anchorOffsets.values.maxOrNull() ?: return

    offsetPx = (offsetPx + delta).coerceIn(min, max)
  }

  fun onDragStarted() {
    isDragging = true
  }

  /**
   * Settles onto whichever anchor the drag ended nearest, and reports it as a selection.
   */
  suspend fun settle() {
    isDragging = false

    val nearest = anchorOffsets.minByOrNull { abs(it.value - offsetPx) }?.key ?: return
    onAnchorSelected(nearest)
    animateTo(nearest)
  }

  /**
   * Moves the split to [anchor], ignored while a drag is in progress so that a state change mid-gesture
   * doesn't yank the divider out from under the user's finger.
   */
  suspend fun goTo(anchor: PaneAnchor) {
    if (isDragging) {
      return
    }

    animateTo(anchor)
  }

  /**
   * The anchor an accessibility action should move to next, cycling through them in declared order. Screen
   * readers cannot drag, so this is how the divider is operated without a gesture.
   */
  fun nextAnchor(): PaneAnchor? {
    val current = anchorOffsets.minByOrNull { abs(it.value - offsetPx) }?.key ?: return null
    val anchors = PaneAnchor.entries

    return anchors[(anchors.indexOf(current) + 1) % anchors.size]
  }

  /**
   * Reports [anchor] as selected without dragging to it. The resulting state change is what moves the
   * divider, by the same path a drag takes.
   */
  fun selectAnchor(anchor: PaneAnchor) {
    onAnchorSelected(anchor)
  }

  private suspend fun animateTo(anchor: PaneAnchor) {
    val target = anchorOffsets[anchor] ?: return

    animationMutex.mutate {
      animate(initialValue = offsetPx, targetValue = target) { value, _ -> offsetPx = value }
    }
  }
}

/** [initialOffsetPx] is the width the list pane starts at, read once on the composition that creates this. */
@Composable
internal fun rememberPaneExpansionState(initialOffsetPx: Float, onAnchorSelected: (PaneAnchor) -> Unit): PaneExpansionState {
  val density = LocalDensity.current
  return remember(density) { PaneExpansionState(density, initialOffsetPx, onAnchorSelected) }
}

/** Touch width of the pane divider, which is wider than both the visual handle and the gap it sits in. */
internal val PANE_HANDLE_TOUCH_WIDTH: Dp = 48.dp

/** Width of the visible pill, which tracks the divider rather than the touch target. */
internal val PANE_HANDLE_VISUAL_WIDTH: Dp = 4.dp

internal val PANE_HANDLE_VISUAL_HEIGHT: Dp = 48.dp

/** Height of the grab area. Bounded rather than full-height so the system gesture exclusion is honoured. */
internal val PANE_HANDLE_TOUCH_HEIGHT: Dp = 48.dp

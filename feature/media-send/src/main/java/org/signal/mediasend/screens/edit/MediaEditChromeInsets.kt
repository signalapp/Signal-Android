/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import org.signal.mediasend.EditorState

internal enum class ChromeEdge { TOP, BOTTOM, LEFT, RIGHT }

internal enum class ChromeSlot { TOP_BAND, BOTTOM, SIDE_RAIL }

internal enum class MediaChromeKind { VIDEO_TRIM, DOCUMENT, IMAGE }

/** In pixels. */
@Immutable
internal data class ChromeInsets(
  val left: Float = 0f,
  val top: Float = 0f,
  val right: Float = 0f,
  val bottom: Float = 0f
) {
  fun expandedBy(amount: Float): ChromeInsets {
    return ChromeInsets(left = left + amount, top = top + amount, right = right + amount, bottom = bottom + amount)
  }

  /** Vertical is left asymmetric on purpose, so the media fills the space between the bars. */
  fun mirroredHorizontally(): ChromeInsets {
    val horizontal = maxOf(left, right)
    return copy(left = horizontal, right = horizontal)
  }
}

/**
 * [measured] follows the chrome live; media is fit into the settled value instead, taken once from the resting layout
 * and then frozen so an editor mode's bars overlap the media rather than moving it.
 */
@Stable
internal class MediaEditChromeInsetsState {

  var rootCoordinates: LayoutCoordinates? by mutableStateOf(null)

  private val reported = mutableStateMapOf<ChromeSlot, ChromeInsets>()

  val measured: ChromeInsets by derivedStateOf {
    reported.values.fold(ChromeInsets()) { total, next ->
      ChromeInsets(
        left = maxOf(total.left, next.left),
        top = maxOf(total.top, next.top),
        right = maxOf(total.right, next.right),
        bottom = maxOf(total.bottom, next.bottom)
      )
    }
  }

  private val settledByKind = mutableStateMapOf<MediaChromeKind, ChromeInsets>()
  private val frozenKinds = mutableSetOf<MediaChromeKind>()

  /** Falls back to the image baseline, which the other kinds add to or subtract from. */
  fun settledFor(kind: MediaChromeKind): ChromeInsets? {
    return settledByKind[kind] ?: settledByKind[MediaChromeKind.IMAGE]
  }

  fun settle(kind: MediaChromeKind, insets: ChromeInsets) {
    if (kind !in frozenKinds) {
      settledByKind[kind] = insets
    }
  }

  fun freeze(kind: MediaChromeKind) {
    frozenKinds += kind
  }

  fun isFrozen(kind: MediaChromeKind): Boolean = kind in frozenKinds

  fun thaw() {
    frozenKinds.clear()
  }

  fun report(slot: ChromeSlot, insets: ChromeInsets) {
    reported[slot] = insets
  }

  fun clear(slot: ChromeSlot) {
    reported.remove(slot)
  }
}

internal fun EditorState?.chromeKind(): MediaChromeKind = when (this) {
  is EditorState.VideoTrim -> MediaChromeKind.VIDEO_TRIM
  is EditorState.Document -> MediaChromeKind.DOCUMENT
  else -> MediaChromeKind.IMAGE
}

internal fun MediaEditChromeInsetsState.contentInsetsFor(kind: MediaChromeKind, gutter: Float): ChromeInsets {
  return (settledFor(kind) ?: ChromeInsets()).mirroredHorizontally().expandedBy(gutter)
}

/** Goes last in the chain, so padding anchoring the control to a screen edge is not read as part of the control. */
internal fun Modifier.reportChromeInset(state: MediaEditChromeInsetsState, slot: ChromeSlot, edge: ChromeEdge): Modifier {
  return onGloballyPositioned { coordinates ->
    val root = state.rootCoordinates?.takeIf { it.isAttached } ?: return@onGloballyPositioned

    // A control animated away still measures, at zero size in a corner.
    if (coordinates.size.width == 0 || coordinates.size.height == 0) {
      state.report(slot, ChromeInsets())
      return@onGloballyPositioned
    }

    val bounds = root.localBoundingBoxOf(coordinates, clipBounds = false)
    val insets = when (edge) {
      ChromeEdge.TOP -> ChromeInsets(top = bounds.bottom)
      ChromeEdge.BOTTOM -> ChromeInsets(bottom = root.size.height - bounds.top)
      ChromeEdge.LEFT -> ChromeInsets(left = bounds.right)
      ChromeEdge.RIGHT -> ChromeInsets(right = root.size.width - bounds.left)
    }

    state.report(
      slot,
      ChromeInsets(
        left = insets.left.coerceAtLeast(0f),
        top = insets.top.coerceAtLeast(0f),
        right = insets.right.coerceAtLeast(0f),
        bottom = insets.bottom.coerceAtLeast(0f)
      )
    )
  }
}

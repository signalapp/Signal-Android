/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Spreads children across the full width like [Arrangement.SpaceBetween], but never opens a gap wider than [max].
 * Once the gap is capped, the children are centered as a group rather than pinned to the edges, and if there isn't
 * even room for [max] the gap shrinks to fit instead of overflowing.
 */
fun Arrangement.spaceBetweenUpTo(max: Dp): Arrangement.Horizontal = object : Arrangement.Horizontal {
  override val spacing: Dp = max

  override fun Density.arrange(
    totalSize: Int,
    sizes: IntArray,
    layoutDirection: LayoutDirection,
    outPositions: IntArray
  ) {
    if (sizes.isEmpty()) {
      return
    }

    val gapCount = sizes.size - 1
    val slack = totalSize - sizes.sum()
    val gap = if (gapCount == 0) 0 else (slack / gapCount).coerceIn(0, max.roundToPx())

    var position = ((slack - gap * gapCount) / 2).coerceAtLeast(0)
    for (index in if (layoutDirection == LayoutDirection.Ltr) sizes.indices else sizes.indices.reversed()) {
      outPositions[index] = position
      position += sizes[index] + gap
    }
  }
}

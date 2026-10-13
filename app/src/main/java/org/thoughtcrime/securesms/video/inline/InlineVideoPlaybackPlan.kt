/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.inline

import kotlin.math.abs

/**
 * View-free selection logic for [InlineVideoController].
 */
internal object InlineVideoPlaybackPlan {

  fun <T> selectNearestCenter(candidates: List<T>, viewportCenter: Float, maxSlots: Int, centerOf: (T) -> Float): List<T> {
    return candidates
      .sortedBy { abs(centerOf(it) - viewportCenter) }
      .take(maxSlots)
  }

  /**
   * Prefers a slot that already has [mediaItem] prepared, otherwise the least recently used.
   */
  fun <S, M> pickIdleSlot(idleLeastRecentFirst: Iterable<S>, mediaItem: M, mediaItemOf: (S) -> M?): S? {
    return idleLeastRecentFirst.firstOrNull { mediaItemOf(it) == mediaItem } ?: idleLeastRecentFirst.firstOrNull()
  }
}

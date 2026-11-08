/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import kotlin.math.floor

/** The video page whose trim timeline to show, and how opaque to make it. */
internal data class VideoTimelinePresence(val page: Int, val alpha: Float) {
  companion object {
    /**
     * From the pager's [position] between the one or two pages in view. Between two videos the timeline fades out and
     * back in, switching videos at the midpoint where it is invisible.
     */
    fun at(position: Float, isVideo: (Int) -> Boolean): VideoTimelinePresence? {
      val lower = floor(position).toInt()
      val fraction = position - lower

      val lowerWeight = if (isVideo(lower)) 1f - fraction else 0f
      val upperWeight = if (fraction > 0f && isVideo(lower + 1)) fraction else 0f

      return when {
        lowerWeight == 0f && upperWeight == 0f -> null
        lowerWeight >= upperWeight -> VideoTimelinePresence(lower, lowerWeight - upperWeight)
        else -> VideoTimelinePresence(lower + 1, upperWeight - lowerWeight)
      }
    }
  }
}

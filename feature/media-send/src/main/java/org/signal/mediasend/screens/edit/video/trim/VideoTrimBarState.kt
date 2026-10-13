/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * What [VideoTrimBar] renders. All positions are in microseconds of the source video.
 *
 * [durationUs] is null until the video's duration is known, and nothing can be dragged until then. [thumbnails] has one
 * slot per tile in the strip, each null until that frame has been decoded.
 */
@Immutable
data class VideoTrimBarState(
  val durationUs: Long? = null,
  val startUs: Long = 0,
  val endUs: Long = 0,
  val playheadUs: Long = 0,
  /** The longest range that may be selected, or null for no limit. */
  val maxRangeUs: Long? = null,
  val thumbnails: List<ImageBitmap?> = emptyList(),
  val activeDrag: TrimDragTarget? = null
) {

  /** Whether the selection excludes any of the video. */
  val isTrimmed: Boolean
    get() = durationUs != null && (startUs > 0 || endUs < durationUs)

  val isDragging: Boolean
    get() = activeDrag != null

  override fun toString(): String {
    return "VideoTrimBarState(durationUs=$durationUs, startUs=$startUs, endUs=$endUs, playheadUs=$playheadUs, maxRangeUs=$maxRangeUs, " +
      "thumbnails=${thumbnails.count { it != null }}/${thumbnails.size}, activeDrag=$activeDrag)"
  }
}

/** The part of the bar a drag moves. */
enum class TrimDragTarget {
  START,
  END,
  PLAYHEAD
}

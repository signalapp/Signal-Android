/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video.trim

import android.net.Uri
import org.signal.mediasend.screens.edit.video.VideoTrimData

/**
 * Everything a [VideoTrimBarPresenter] can be told, whether it came from the bar itself or from the screen hosting it.
 * Host events are tagged with their video and dropped unless it is still the bar's source.
 */
sealed interface VideoTrimBarEvents {

  data class SourceChanged(val uri: Uri) : VideoTrimBarEvents

  /** [maxDurationUs] is the longest range that may be selected, or null for no limit. */
  data class TrimDataChanged(val uri: Uri, val videoTrimData: VideoTrimData, val maxDurationUs: Long?) : VideoTrimBarEvents

  data class PlaybackPositionChanged(val uri: Uri, val positionUs: Long) : VideoTrimBarEvents

  /** Decides how many thumbnails the strip needs, and how large. */
  data class StripMeasured(val widthPx: Int, val heightPx: Int) : VideoTrimBarEvents

  data class DragStarted(val target: TrimDragTarget) : VideoTrimBarEvents

  data class HandleDragged(val target: TrimDragTarget, val positionUs: Long) : VideoTrimBarEvents

  data class PlayheadDragged(val positionUs: Long) : VideoTrimBarEvents

  data class PlayheadTapped(val positionUs: Long) : VideoTrimBarEvents

  /** Sent whether the user let go or the gesture was cancelled. */
  data object DragEnded : VideoTrimBarEvents
}

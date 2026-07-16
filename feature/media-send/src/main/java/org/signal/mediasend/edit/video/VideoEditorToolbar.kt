/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.edit.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.Previews
import org.signal.mediasend.edit.MediaEditScreenEvent

/**
 * Timeline/trim toolbar for a video. Trim data and the current playback position are owned elsewhere and passed in;
 * all user-driven changes are reported back through [onEvent] — [MediaEditScreenEvent.VideoTrimChanged] for trim
 * handles and [MediaEditScreenEvent.VideoSeek] for scrubbing the playback position.
 */
@Composable
fun VideoEditorToolbar(
  videoTrimData: VideoTrimData,
  playbackPositionUs: Long = 0L,
  onEvent: (MediaEditScreenEvent) -> Unit = {}
) {
  val currentOnEvent by rememberUpdatedState(onEvent)
  var isDragging by remember { mutableStateOf(false) }

  val positionDragListener = remember {
    object : VideoThumbnailsRangeSelectorView.PositionDragListener {
      override fun onPositionDrag(position: Long) {
        isDragging = true
        currentOnEvent(MediaEditScreenEvent.VideoSeek(positionUs = position, editingComplete = false))
      }

      override fun onEndPositionDrag(position: Long) {
        isDragging = false
        currentOnEvent(MediaEditScreenEvent.VideoSeek(positionUs = position, editingComplete = true))
      }
    }
  }

  val rangeDragListener = remember {
    VideoThumbnailsRangeSelectorView.RangeDragListener { minValue, maxValue, duration, end ->
      isDragging = !end
      currentOnEvent(
        MediaEditScreenEvent.VideoTrimChanged(
          videoTrimData = VideoTrimData(
            isDurationEdited = minValue > 0 || maxValue < duration,
            totalInputDurationUs = duration,
            startTimeUs = minValue,
            endTimeUs = maxValue
          ),
          editingComplete = end
        )
      )
    }
  }

  AndroidView(
    factory = { VideoThumbnailsRangeSelectorView(it) },
    update = { selectorView ->
      selectorView.registerPlayerDragListener(positionDragListener)
      selectorView.registerEditorOnRangeChangeListener(rangeDragListener)

      // The view owns its state during a drag and emits changes outward, so only push the authoritative range and
      // playback position back in when the user isn't actively dragging — otherwise we'd fight the live gesture.
      if (!isDragging) {
        selectorView.setRange(videoTrimData.startTimeUs, videoTrimData.endTimeUs)
        selectorView.setActualPosition(playbackPositionUs)
      }
    },
    onRelease = { selectorView ->
      selectorView.unregisterDragListener()
      selectorView.registerEditorOnRangeChangeListener(null)
    }
  )
}

@Preview
@Composable
fun VideoEditorToolbarPreview() {
  Previews.Preview {
    VideoEditorToolbar(
      videoTrimData = VideoTrimData(isDurationEdited = false)
    )
  }
}

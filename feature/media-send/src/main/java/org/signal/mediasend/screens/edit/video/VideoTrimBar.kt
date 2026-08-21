/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.video

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.util.logging.Log
import org.signal.mediasend.PreviewMediaInputFactory
import org.signal.mediasend.R
import org.signal.mediasend.screens.edit.MediaEditScreenEvents
import org.thoughtcrime.securesms.video.interfaces.MediaInputFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.microseconds

private const val TAG = "VideoEditorToolbar"

/**
 * Timeline/trim toolbar for a video. Trim data and the current playback position are owned elsewhere and passed in;
 * all user-driven changes are reported back through [onEvent] — [MediaEditScreenEvents.VideoTrimChanged] for trim
 * handles and [MediaEditScreenEvents.VideoSeek] for scrubbing the playback position.
 *
 * [videoUri] and [mediaInputFactory] are required to load the underlying video: the timeline renders nothing until an
 * input is set, since that is also what drives thumbnail extraction and duration discovery. [maxSelectableDurationUs]
 * caps how long a range the user can trim to (0 means no cap).
 */
@Composable
fun VideoTrimBar(
  videoUri: Uri,
  mediaInputFactory: MediaInputFactory,
  videoTrimData: VideoTrimData,
  maxSelectableDurationUs: Long = 0L,
  playbackPositionUs: Long = 0L,
  modifier: Modifier = Modifier,
  onEvent: (MediaEditScreenEvents) -> Unit = {}
) {
  val currentOnEvent by rememberUpdatedState(onEvent)
  var isDragging by remember { mutableStateOf(false) }
  val isInInspectionMode = LocalInspectionMode.current

  val positionDragListener = remember {
    object : VideoThumbnailsRangeSelectorView.PositionDragListener {
      override fun onPositionDrag(position: Long) {
        isDragging = true
        currentOnEvent(MediaEditScreenEvents.VideoSeek(positionUs = position, editingComplete = false))
      }

      override fun onEndPositionDrag(position: Long) {
        isDragging = false
        currentOnEvent(MediaEditScreenEvents.VideoSeek(positionUs = position, editingComplete = true))
      }
    }
  }

  val rangeDragListener = remember {
    VideoThumbnailsRangeSelectorView.RangeDragListener { minValue, maxValue, duration, end ->
      isDragging = !end
      currentOnEvent(
        MediaEditScreenEvents.VideoTrimChanged(
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
    // Inflated rather than constructed directly so the styled attributes (thumb size/color, touch radius, hint text)
    // are applied — the view only reads those from an AttributeSet, so a bare constructor leaves the scrubbers
    // zero-width and transparent.
    factory = { context ->
      LayoutInflater.from(context).inflate(R.layout.mediasend_video_timeline, null) as VideoThumbnailsRangeSelectorView
    },
    update = { selectorView ->
      selectorView.registerPlayerDragListener(positionDragListener)
      selectorView.registerEditorOnRangeChangeListener(rangeDragListener)

      // The view draws nothing and never learns the video duration until an input is set, so this is what actually
      // brings the timeline to life. setInput is a no-op when the URI is unchanged, so it's safe to call every update.
      // Skipped under inspection since previews have no real video to decode.
      if (!isInInspectionMode) {
        try {
          selectorView.setInput(videoUri, mediaInputFactory)
        } catch (e: IOException) {
          Log.w(TAG, "Unable to set video input for the trim timeline.", e)
        }
      }

      if (maxSelectableDurationUs > 0) {
        selectorView.setTimeLimit(maxSelectableDurationUs.microseconds.inWholeSeconds.toInt(), TimeUnit.SECONDS)
      }

      // The view owns its state during a drag and emits changes outward, so only push the authoritative range and
      // playback position back in when the user isn't actively dragging — otherwise we'd fight the live gesture.
      if (!isDragging) {
        // Only push a range once we actually know the input duration. A not-yet-initialized trim (totalInputDurationUs
        // == 0, e.g. before the video's metadata is known) would otherwise collapse the selection to the minimum range
        // at the start. Skipping it lets the view establish the full range from the decoded video and report it back.
        if (videoTrimData.totalInputDurationUs > 0) {
          selectorView.setRange(videoTrimData.startTimeUs, videoTrimData.endTimeUs)
        }
        selectorView.setActualPosition(playbackPositionUs)
      }
    },
    onRelease = { selectorView ->
      selectorView.unregisterDragListener()
      selectorView.registerEditorOnRangeChangeListener(null)
    },
    modifier = modifier
      .horizontalGutters()
      .height(48.dp)
      .fillMaxWidth()
      // Trim handles sit at the screen edges where the system back-gesture would otherwise swallow drags.
      .systemGestureExclusion()
  )
}

@Preview
@Composable
fun VideoTrimBarPreview() {
  Previews.Preview {
    VideoTrimBar(
      videoUri = Uri.EMPTY,
      mediaInputFactory = PreviewMediaInputFactory,
      videoTrimData = VideoTrimData(isDurationEdited = false)
    )
  }
}

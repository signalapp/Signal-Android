/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.ScreenshotPreviews
import org.signal.core.util.ContentTypeUtil
import org.signal.imageeditor.core.model.EditorModel
import org.signal.mediasend.EditorState
import org.signal.mediasend.PreviewMediaInputFactory
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.screens.edit.video.VideoTrimData

class MediaEditScreenScreenshotTests {
  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun MediaEditScreenImagePreview() {
    val selectedMedia = rememberPreviewMedia(10)

    Previews.Preview {
      MediaEditScreen(
        state = MediaEditState(
          selectedMedia = selectedMedia,
          focusedMedia = selectedMedia.first(),
          editorStateMap = mapOf(selectedMedia.first().uri to EditorState.Image(EditorModel.create(0)))
        ),
        onEvent = {},
        imageControllers = remember { ImageController.Container() },
        mediaInputFactory = PreviewMediaInputFactory
      )
    }
  }

  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun MediaEditScreenSingleImagePreview() {
    val selectedMedia = rememberPreviewMedia(1)

    Previews.Preview {
      MediaEditScreen(
        state = MediaEditState(
          selectedMedia = selectedMedia,
          focusedMedia = selectedMedia.first(),
          editorStateMap = mapOf(selectedMedia.first().uri to EditorState.Image(EditorModel.create(0))),
          message = "Look at this",
          isViewOnceAvailable = true
        ),
        onEvent = {},
        imageControllers = remember { ImageController.Container() },
        mediaInputFactory = PreviewMediaInputFactory
      )
    }
  }

  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun MediaEditScreenVideoPreview() {
    val selectedMedia = rememberPreviewMedia(10, contentType = ContentTypeUtil.VIDEO_MP4)

    Previews.Preview {
      MediaEditScreen(
        state = MediaEditState(
          selectedMedia = selectedMedia,
          focusedMedia = selectedMedia.first(),
          editorStateMap = mapOf(selectedMedia.first().uri to EditorState.VideoTrim(VideoTrimData())),
          sentMediaQuality = SentMediaQuality.HIGH,
          isMuteVideoAudioEnabled = true
        ),
        onEvent = {},
        imageControllers = remember { ImageController.Container() },
        mediaInputFactory = PreviewMediaInputFactory
      )
    }
  }
}

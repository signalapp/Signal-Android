/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.capture

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview
import org.signal.mediasend.screens.edit.rememberPreviewMedia

class MediaCaptureScreenScreenshotTests {
  @PreviewTest
  @AllNightPreviews
  @RtlPreview
  @Composable
  fun MediaCaptureScreenPhotoPreview() {
    Previews.Preview {
      MediaCaptureScreen(
        state = rememberPreviewCaptureState(),
        onEvent = {},
        textStoryEditorSlot = {}
      )
    }
  }

  @PreviewTest
  @AllNightPreviews
  @RtlPreview
  @Composable
  fun MediaCaptureScreenVideoPreview() {
    Previews.Preview {
      MediaCaptureScreen(
        state = rememberPreviewCaptureState().copy(selectedCameraMode = MediaCaptureMode.VIDEO),
        onEvent = {},
        textStoryEditorSlot = {}
      )
    }
  }

  /**
   * A flow already carrying a capture: the text story is withdrawn from the bar and the next button floats over its end.
   */
  @PreviewTest
  @AllNightPreviews
  @RtlPreview
  @Composable
  fun MediaCaptureScreenWithSelectedMediaPreview() {
    val selectedMedia = rememberPreviewMedia(1)

    Previews.Preview {
      MediaCaptureScreen(
        state = rememberPreviewCaptureState().copy(selectedMedia = selectedMedia),
        onEvent = {},
        textStoryEditorSlot = {}
      )
    }
  }
}

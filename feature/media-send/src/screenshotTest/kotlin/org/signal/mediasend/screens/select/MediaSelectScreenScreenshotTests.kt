/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.select

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.ScreenshotPreviews
import org.signal.mediasend.screens.edit.rememberPreviewMedia

class MediaSelectScreenScreenshotTests {
  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun MediaSelectScreenFoldersPreview() {
    Previews.Preview {
      MediaSelectScreen(
        state = MediaSelectState.Folders(
          mediaFolders = rememberPreviewMediaFolders(20),
          selectedMedia = emptyList()
        ),
        onEvent = {}
      )
    }
  }

  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun MediaSelectScreenFilesPreview() {
    Previews.Preview {
      MediaSelectScreen(
        state = MediaSelectState.Files(
          selectedMediaFolder = rememberPreviewMediaFolders(1).first(),
          selectedMediaFolderItems = rememberPreviewMedia(100),
          selectedMedia = emptyList()
        ),
        onEvent = {}
      )
    }
  }

  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun MediaSelectScreenFilesWithSelectionPreview() {
    val media = rememberPreviewMedia(100)

    Previews.Preview {
      MediaSelectScreen(
        state = MediaSelectState.Files(
          selectedMediaFolder = rememberPreviewMediaFolders(1).first(),
          selectedMediaFolderItems = media,
          selectedMedia = media.take(3)
        ),
        onEvent = {}
      )
    }
  }

  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun MediaSelectScreenNoPermissionPreview() {
    Previews.Preview {
      MediaSelectScreen(
        state = MediaSelectState.Folders(
          mediaFolders = emptyList(),
          selectedMedia = emptyList(),
          mediaPermissions = MediaPermissions.NONE
        ),
        onEvent = {}
      )
    }
  }

  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun MediaSelectScreenPartialPermissionPreview() {
    Previews.Preview {
      MediaSelectScreen(
        state = MediaSelectState.Folders(
          mediaFolders = rememberPreviewMediaFolders(4),
          selectedMedia = emptyList(),
          mediaPermissions = MediaPermissions.PARTIAL
        ),
        onEvent = {}
      )
    }
  }
}

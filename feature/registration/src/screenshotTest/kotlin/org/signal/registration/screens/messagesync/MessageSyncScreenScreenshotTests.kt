/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.messagesync

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview
import org.signal.core.util.kibiBytes
import org.signal.core.util.mebiBytes
import org.signal.registration.screens.messagesync.MessageSyncScreenState.Stage

class MessageSyncScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun MessageSyncScreenPreview() {
    Previews.Preview {
      MessageSyncScreen(
        state = MessageSyncScreenState(
          stage = Stage.Downloading(downloaded = 1.mebiBytes, total = 3300.kibiBytes)
        ),
        onEvent = {}
      )
    }
  }

  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun MessageSyncScreenPreparingPreview() {
    Previews.Preview {
      MessageSyncScreen(
        state = MessageSyncScreenState(stage = Stage.Preparing),
        onEvent = {}
      )
    }
  }

  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun MessageSyncScreenRestoringPreview() {
    Previews.Preview {
      MessageSyncScreen(
        state = MessageSyncScreenState(
          stage = Stage.Restoring(restored = 2.mebiBytes, total = 3300.kibiBytes)
        ),
        onEvent = {}
      )
    }
  }

  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun MessageSyncScreenFinishingPreview() {
    Previews.Preview {
      MessageSyncScreen(
        state = MessageSyncScreenState(stage = Stage.Finishing),
        onEvent = {}
      )
    }
  }
}

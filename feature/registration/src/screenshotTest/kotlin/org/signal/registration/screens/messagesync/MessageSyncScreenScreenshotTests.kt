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

class MessageSyncScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun MessageSyncScreenPreview() {
    Previews.Preview {
      MessageSyncScreen(
        state = MessageSyncScreenState(
          downloadedBytes = 1.mebiBytes,
          totalBytes = 3300.kibiBytes
        ),
        onEvent = {}
      )
    }
  }
}

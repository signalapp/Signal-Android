/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.pinentry

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.ScreenshotPreviews

class PinEntryScreenScreenshotTests {
  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun PinEntryScreenPreview() {
    Previews.Preview {
      PinEntryScreen(
        state = PinEntryState(),
        onEvent = {}
      )
    }
  }
}

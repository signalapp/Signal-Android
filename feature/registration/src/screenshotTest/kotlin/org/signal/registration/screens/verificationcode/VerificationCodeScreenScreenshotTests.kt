/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.verificationcode

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.ScreenshotPreviews

class VerificationCodeScreenScreenshotTests {
  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun VerificationCodeScreenPreview() {
    Previews.Preview {
      VerificationCodeScreen(
        state = VerificationCodeState(e164 = "+1 555-123-4567"),
        onEvent = {}
      )
    }
  }
}

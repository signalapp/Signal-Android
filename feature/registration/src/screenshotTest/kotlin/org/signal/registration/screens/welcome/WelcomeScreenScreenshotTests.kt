/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.welcome

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview

class WelcomeScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun WelcomeScreenPhonePreview() {
    Previews.Preview {
      WelcomeScreen(onEvent = {})
    }
  }
}

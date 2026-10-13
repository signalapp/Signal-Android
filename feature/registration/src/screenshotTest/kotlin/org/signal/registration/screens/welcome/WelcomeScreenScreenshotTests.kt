/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.welcome

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.ScreenshotPreviews
import org.signal.core.ui.compose.TabletPreviews

class WelcomeScreenScreenshotTests {
  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun WelcomeScreenPhonePreview() {
    Previews.Preview {
      WelcomeScreen(state = WelcomeScreenState(), onEvent = {})
    }
  }

  @PreviewTest
  @TabletPreviews
  @Composable
  fun WelcomeScreenLinkedDevicePreview() {
    Previews.Preview {
      WelcomeScreen(state = WelcomeScreenState(isLinkAndSyncAvailable = true), onEvent = {})
    }
  }
}

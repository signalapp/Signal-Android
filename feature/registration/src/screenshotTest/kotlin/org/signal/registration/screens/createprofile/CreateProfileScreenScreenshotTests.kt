/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.createprofile

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview

class CreateProfileScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun CreateProfileScreenPreview() {
    Previews.Preview {
      CreateProfileScreen(
        state = CreateProfileState(
          givenName = "Alice",
          familyName = "Anderson",
          isLoading = false
        ),
        onEvent = {}
      )
    }
  }
}

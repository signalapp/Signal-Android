/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration.screens.allownotifications

import android.Manifest
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview
import org.signal.registration.screens.util.MockPermissionsState

class AllowNotificationsScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun AllowNotificationsScreenPreview() {
    Previews.Preview {
      AllowNotificationsScreen(
        permissionState = MockPermissionsState(permission = Manifest.permission.POST_NOTIFICATIONS),
        onProceed = {}
      )
    }
  }
}

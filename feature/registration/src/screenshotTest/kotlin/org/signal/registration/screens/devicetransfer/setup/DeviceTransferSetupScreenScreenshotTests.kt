/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration.screens.devicetransfer.setup

import android.Manifest
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.ScreenshotPreviews
import org.signal.registration.screens.util.MockPermissionsState

class DeviceTransferSetupScreenScreenshotTests {
  @PreviewTest
  @ScreenshotPreviews
  @Composable
  fun DeviceTransferSetupScreenPreview() {
    Previews.Preview {
      DeviceTransferSetupScreen(
        state = DeviceTransferSetupState(),
        permissionState = MockPermissionsState(permission = Manifest.permission.ACCESS_FINE_LOCATION),
        onEvent = {}
      )
    }
  }
}

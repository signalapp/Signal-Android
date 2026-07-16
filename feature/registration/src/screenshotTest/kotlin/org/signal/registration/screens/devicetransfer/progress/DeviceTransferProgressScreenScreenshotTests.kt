/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.progress

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview

class DeviceTransferProgressScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun DeviceTransferProgressScreenPreview() {
    Previews.Preview {
      DeviceTransferProgressScreen(
        state = DeviceTransferProgressState(messageCount = 1234),
        showCancelDialog = false,
        onEvent = {}
      )
    }
  }
}

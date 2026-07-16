/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.devicetransfer.instructions

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview

class DeviceTransferInstructionsScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun DeviceTransferInstructionsScreenPreview() {
    Previews.Preview {
      DeviceTransferInstructionsScreen(
        state = DeviceTransferInstructionsState(),
        onEvent = {}
      )
    }
  }
}

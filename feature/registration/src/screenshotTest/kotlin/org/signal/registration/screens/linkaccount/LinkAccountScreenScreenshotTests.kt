/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.ui.compose.RtlPreview
import org.signal.registration.screens.quickrestore.QrState

class LinkAccountScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun LinkAccountScreenPreview() {
    Previews.Preview {
      LinkAccountScreen(
        state = LinkAccountScreenState(
          qrCodeState = QrState.Loaded(qrCodeData = QrCodeData.forData("sgnl://rereg?uuid=test&pub_key=test", true)),
          displayQrOverlay = false
        ),
        onEvent = {}
      )
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.quickrestore

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.ui.compose.RtlPreview

class QuickRestoreQrScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun QuickRestoreQrScreenPreview() {
    Previews.Preview {
      QuickRestoreQrScreen(
        state = QuickRestoreQrState(
          qrState = QrState.Loaded(QrCodeData.forData("sgnl://rereg?uuid=test&pub_key=test", false))
        ),
        onEvent = {}
      )
    }
  }
}

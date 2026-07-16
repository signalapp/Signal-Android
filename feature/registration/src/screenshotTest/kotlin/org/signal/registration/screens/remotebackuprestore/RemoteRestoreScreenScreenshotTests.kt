/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview
import java.util.TimeZone

class RemoteRestoreScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun RemoteRestoreScreenPreview() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    Previews.Preview {
      RemoteRestoreScreen(
        state = RemoteBackupRestoreState(
          aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
          loadState = RemoteBackupRestoreState.LoadState.Loaded,
          backupTime = 1700000000000L,
          backupSize = 1234567
        ),
        onEvent = {}
      )
    }
  }
}

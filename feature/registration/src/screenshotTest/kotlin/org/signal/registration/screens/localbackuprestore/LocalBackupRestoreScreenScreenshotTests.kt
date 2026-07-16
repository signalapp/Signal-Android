/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview

class LocalBackupRestoreScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun LocalBackupRestoreScreenPreview() {
    Previews.Preview {
      LocalBackupRestoreScreen(
        state = LocalBackupRestoreState(restorePhase = LocalBackupRestoreState.RestorePhase.SelectFolder),
        onEvent = {}
      )
    }
  }
}

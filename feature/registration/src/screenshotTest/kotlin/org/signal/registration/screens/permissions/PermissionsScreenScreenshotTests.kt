/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration.screens.permissions

import android.Manifest
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview
import org.signal.registration.screens.util.MockMultiplePermissionsState
import org.signal.registration.screens.util.MockPermissionsState

class PermissionsScreenScreenshotTests {
  @PreviewTest
  @AllDevicePreviews
  @RtlPreview
  @Composable
  fun PermissionsScreenPreview() {
    Previews.Preview {
      PermissionsScreen(
        permissionsState = MockMultiplePermissionsState(
          permissions = listOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
          ).map { MockPermissionsState(it) }
        ),
        onProceed = {}
      )
    }
  }
}

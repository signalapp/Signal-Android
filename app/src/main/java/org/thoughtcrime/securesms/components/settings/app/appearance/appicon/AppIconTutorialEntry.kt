/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon

import androidx.compose.runtime.Composable
import org.signal.appsettings.appicon.AppIconTutorialEvent
import org.signal.appsettings.appicon.AppIconTutorialScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack

/**
 * The explanation of where a replacement app icon shows up.
 */
@Composable
fun AppIconTutorialEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  AppIconTutorialScreen(
    onEvent = { event ->
      when (event) {
        AppIconTutorialEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      }
    }
  )
}

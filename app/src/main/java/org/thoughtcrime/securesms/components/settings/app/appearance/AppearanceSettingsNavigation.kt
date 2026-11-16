/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.components.settings.app.appearance.appicon.AppIconSelectionEntry
import org.thoughtcrime.securesms.components.settings.app.appearance.appicon.AppIconTutorialEntry

/**
 * Registers the routes within the appearance section of app settings. Chat colors and wallpaper is its own activity,
 * which the host launches rather than registering here.
 */
fun EntryProviderScope<NavKey>.registerAppearanceRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.AppearanceRoute.Appearance> {
    AppearanceSettingsEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.AppearanceRoute.AppIconSelection> {
    AppIconSelectionEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.AppearanceRoute.AppIconTutorial> {
    AppIconTutorialEntry(parentEventEmitter = parentEventEmitter)
  }
}

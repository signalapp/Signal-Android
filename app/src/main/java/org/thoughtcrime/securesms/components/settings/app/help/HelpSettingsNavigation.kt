/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.help.HelpEntry

/**
 * Registers the routes within the help section of app settings. Submitting a debug log is its own activity, which the
 * host launches rather than registering here.
 */
fun EntryProviderScope<NavKey>.registerHelpRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.HelpRoute.Settings> {
    HelpSettingsEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.HelpRoute.Help> { route ->
    HelpEntry(startCategoryIndex = route.startCategoryIndex, parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.HelpRoute.Licenses> {
    LicenseEntry(parentEventEmitter = parentEventEmitter)
  }
}

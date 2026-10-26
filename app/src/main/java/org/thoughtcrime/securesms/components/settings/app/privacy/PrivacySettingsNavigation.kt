/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry

/**
 * Registers the routes within the privacy section of app settings.
 */
fun EntryProviderScope<NavKey>.registerPrivacyRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.PrivacyRoute.Privacy> {
    PrivacySettingsEntry(scrollToPaymentLock = false, parentEventEmitter = parentEventEmitter)
  }
}

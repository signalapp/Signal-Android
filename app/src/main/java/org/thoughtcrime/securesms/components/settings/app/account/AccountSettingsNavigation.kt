/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.Fragments
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Registers the routes within the account section of app settings. A linked device gets its own, simpler account
 * screen, which is still a fragment and so is hosted as one. Device transfer is its own activity, which the host
 * launches rather than registering here.
 */
fun EntryProviderScope<NavKey>.registerAccountRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.AccountRoute.Account> {
    if (SignalStore.account.isPrimaryDevice) {
      AccountSettingsEntry(parentEventEmitter = parentEventEmitter)
    } else {
      Fragments.Fragment<LinkedDeviceAccountSettingsFragment>(modifier = Modifier.fillMaxSize())
    }
  }
}

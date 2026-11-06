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
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.Fragments
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.navigation.LocalResultEventBus
import org.thoughtcrime.securesms.components.settings.app.AutofillDisabled
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.TwoFactorNameEntryEntry
import org.thoughtcrime.securesms.components.settings.app.account.twofactor.toTwoFactorMethod
import org.thoughtcrime.securesms.delete.DeleteAccountCountryCodeEntry
import org.thoughtcrime.securesms.delete.DeleteAccountCountrySelected
import org.thoughtcrime.securesms.delete.DeleteAccountEntry
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.preferences.AdvancedPinSettingsEntry

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

  detailEntry<AppSettingsRoute.AccountRoute.RenameTwoFactorMethod> { route ->
    val resultEventBus = LocalResultEventBus.current
    val method = route.toTwoFactorMethod()
    AutofillDisabled()
    TwoFactorNameEntryEntry(
      parentEventEmitter = parentEventEmitter,
      methodId = method.id,
      kind = method.kind,
      createdAt = method.createdAt,
      renamedMethod = method,
      onMethodNamed = { result -> resultEventBus.sendResult(result = result) }
    )
  }

  detailEntry<AppSettingsRoute.AccountRoute.AdvancedPinSettings> {
    AdvancedPinSettingsEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.AccountRoute.DeleteAccount> {
    DeleteAccountEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.AccountRoute.DeleteAccountCountryCode> {
    val resultEventBus = LocalResultEventBus.current
    DeleteAccountCountryCodeEntry(
      onCountrySelected = { country ->
        resultEventBus.sendResult(result = DeleteAccountCountrySelected(country.regionCode))
        parentEventEmitter.navigateBack()
      },
      onDismissed = { parentEventEmitter.navigateBack() }
    )
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.Fragments
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.navigation.LocalResultEventBus
import org.thoughtcrime.securesms.components.settings.app.backups.local.LocalBackupsEntry
import org.thoughtcrime.securesms.components.settings.app.backups.local.usesOnDeviceBackupsScreen
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyDisplayEntry
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyRotated
import org.thoughtcrime.securesms.components.settings.app.backups.remote.RemoteBackupsSettingsEntry
import org.thoughtcrime.securesms.preferences.BackupsPreferenceFragment

/**
 * Registers the routes within the backups section of app settings. Users still on the old local backup format get the
 * legacy backups preferences, which are still a fragment and so are hosted as one. That choice is kept for as long as
 * the screen is open, so the legacy screen can close itself once its upgrade flow finishes. A finished key rotation goes
 * back to whichever screen asked for it as a [BackupKeyRotated] result.
 */
fun EntryProviderScope<NavKey>.registerBackupsRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.BackupsRoute.Backups> { route ->
    BackupsSettingsEntry(parentEventEmitter = parentEventEmitter, launchCheckoutFlow = route.launchCheckoutFlow)
  }

  detailEntry<AppSettingsRoute.BackupsRoute.Local> { route ->
    val usesOnDeviceBackupsScreen = rememberSaveable { route.usesOnDeviceBackupsScreen() }
    if (usesOnDeviceBackupsScreen) {
      LocalBackupsEntry(parentEventEmitter = parentEventEmitter, triggerUpdateFlow = route.triggerUpdateFlow)
    } else {
      Fragments.Fragment<BackupsPreferenceFragment>(modifier = Modifier.fillMaxSize())
    }
  }

  detailEntry<AppSettingsRoute.BackupsRoute.Remote> { route ->
    RemoteBackupsSettingsEntry(parentEventEmitter = parentEventEmitter, forQuickRestore = route.forQuickRestore)
  }

  detailEntry<AppSettingsRoute.BackupsRoute.DisplayKey> { route ->
    val resultEventBus = LocalResultEventBus.current
    BackupKeyDisplayEntry(
      startWithKeyRotation = route.startWithKeyRotation,
      onKeyRotated = {
        resultEventBus.sendResult(result = BackupKeyRotated)
        parentEventEmitter.navigateBack()
      },
      onClose = { parentEventEmitter.navigateBack() }
    )
  }
}

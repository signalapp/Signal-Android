/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.navigation.LocalResultEventBus
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyDisplayEntry
import org.thoughtcrime.securesms.components.settings.app.backups.remote.BackupKeyRotated

/**
 * Registers the routes within the backups section of app settings that the new host can show so far. A finished key
 * rotation goes back to whichever screen asked for it as a [BackupKeyRotated] result.
 */
fun EntryProviderScope<NavKey>.registerBackupsRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
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

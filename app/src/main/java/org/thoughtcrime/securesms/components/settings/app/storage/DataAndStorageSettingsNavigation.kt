/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry

/**
 * Registers the routes within the data and storage section of app settings.
 */
fun EntryProviderScope<NavKey>.registerDataAndStorageRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.DataAndStorageRoute.KeepMessages> {
    KeepMessagesSettingsEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.DataAndStorageRoute.ChatLengthLimit> {
    AutofillDisabled()
    ChatLengthLimitSettingsEntry(parentEventEmitter = parentEventEmitter)
  }
}

/** Keeps password managers from offering to save the limit, as [ChatLengthLimitSettingsFragment] does. */
@Composable
private fun AutofillDisabled() {
  if (Build.VERSION.SDK_INT < 26) {
    return
  }

  val view = LocalView.current
  DisposableEffect(view) {
    val previous = view.importantForAutofill
    view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    onDispose { view.importantForAutofill = previous }
  }
}

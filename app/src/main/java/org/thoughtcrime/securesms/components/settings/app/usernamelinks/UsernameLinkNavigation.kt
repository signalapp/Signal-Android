/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker.UsernameLinkQrColorPickerEntry
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.main.UsernameLinkSettingsEntry

/**
 * Registers the screens for the user's username QR code and link.
 */
fun EntryProviderScope<NavKey>.registerUsernameLinkScreens(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.UsernameLinkRoute.UsernameLink> {
    UsernameLinkSettingsEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.UsernameLinkRoute.QRColorPicker> {
    UsernameLinkQrColorPickerEntry(parentEventEmitter = parentEventEmitter)
  }
}

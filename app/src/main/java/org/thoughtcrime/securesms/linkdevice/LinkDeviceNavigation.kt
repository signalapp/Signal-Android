/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.linkdevice

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry

/**
 * Registers the linked device screens of app settings. Their bottom sheets are shown by the screens themselves.
 */
fun EntryProviderScope<NavKey>.registerLinkDeviceRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.LinkDeviceRoute.LinkDevice> {
    LinkDeviceEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.LinkDeviceRoute.Add> {
    AddLinkDeviceEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.LinkDeviceRoute.EditName> {
    EditDeviceNameEntry(parentEventEmitter = parentEventEmitter)
  }
}

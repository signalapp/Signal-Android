/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.Fragments
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.components.settings.app.internal.remoteconfig.InternalRemoteConfigEntry

/**
 * Registers the routes within the internal section of app settings. The internal settings screen is still a View-based
 * fragment, so it is hosted as one.
 */
fun EntryProviderScope<NavKey>.registerInternalRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.InternalRoute.Internal> {
    Fragments.Fragment<InternalSettingsFragment>(modifier = Modifier.fillMaxSize())
  }

  detailEntry<AppSettingsRoute.InternalRoute.RemoteConfig> {
    InternalRemoteConfigEntry(parentEventEmitter = parentEventEmitter)
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list.NotificationProfilesEntry

/**
 * Registers the routes within the notifications section of app settings.
 */
fun EntryProviderScope<NavKey>.registerNotificationsRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.NotificationsRoute.NotificationProfiles> {
    NotificationProfilesEntry(parentEventEmitter = parentEventEmitter)
  }
}

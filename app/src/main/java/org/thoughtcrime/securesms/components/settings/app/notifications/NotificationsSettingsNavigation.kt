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
import org.thoughtcrime.securesms.components.settings.app.notifications.muted.MutedNotificationsEntry
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers.AddAllowedMembersEntry
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details.NotificationProfileDetailsEntry
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit.EditNotificationProfileEntry
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list.NotificationProfilesEntry
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule.EditNotificationProfileScheduleEntry
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.selectrecipients.SelectRecipientsEntry

/**
 * Registers the routes within the notifications section of app settings.
 */
fun EntryProviderScope<NavKey>.registerNotificationsRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.NotificationsRoute.Notifications> {
    NotificationsSettingsEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.NotificationsRoute.MutedNotifications> {
    MutedNotificationsEntry(recipientId = null, parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.NotificationsRoute.NotificationProfiles> {
    NotificationProfilesEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.NotificationsRoute.ProfileDetails> { route ->
    NotificationProfileDetailsEntry(profileId = route.profileId, parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.NotificationsRoute.EditProfile> { route ->
    EditNotificationProfileEntry(profileId = route.profileId, parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.NotificationsRoute.AddAllowedMembers> { route ->
    AddAllowedMembersEntry(profileId = route.profileId, parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.NotificationsRoute.SelectRecipients> { route ->
    SelectRecipientsEntry(profileId = route.profileId, currentMembers = route.currentSelection.toSet(), parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.NotificationsRoute.EditSchedule> { route ->
    EditNotificationProfileScheduleEntry(profileId = route.profileId, createMode = route.createMode, parentEventEmitter = parentEventEmitter)
  }
}

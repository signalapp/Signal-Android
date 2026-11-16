/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface NotificationProfilesEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : NotificationProfilesEvent

  /** The user tapped "New profile" in the list, or "Create profile" on the empty state. */
  data object NewProfileClicked : NotificationProfilesEvent

  /** The user tapped one of their profiles. */
  data class ProfileClicked(val profileId: Long) : NotificationProfilesEvent
}

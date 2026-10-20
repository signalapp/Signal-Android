/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.list

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out
 * by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface NotificationProfilesAction {

  /** Leave the screen. */
  data object NavigateBack : NotificationProfilesAction

  /** Start the flow for creating a new profile. */
  data object NavigateToNewProfile : NotificationProfilesAction

  /** Open the details screen for profile [profileId]. */
  data class NavigateToProfileDetails(val profileId: Long) : NotificationProfilesAction
}

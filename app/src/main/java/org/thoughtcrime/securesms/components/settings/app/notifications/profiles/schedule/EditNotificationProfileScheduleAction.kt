/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.schedule

/**
 * One-shot side effects that need the nav graph, and therefore have to be carried out by the host rather than the
 * screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface EditNotificationProfileScheduleAction {

  /** Leave the screen. */
  data object NavigateBack : EditNotificationProfileScheduleAction

  /** Finish the create flow on the "profile created" screen for profile [profileId]. */
  data class NavigateToProfileCreated(val profileId: Long) : EditNotificationProfileScheduleAction
}

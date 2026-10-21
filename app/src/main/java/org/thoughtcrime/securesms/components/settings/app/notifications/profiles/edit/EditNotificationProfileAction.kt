/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out
 * by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface EditNotificationProfileAction {

  /** Leave the screen. */
  data object NavigateBack : EditNotificationProfileAction

  /** Continue the create flow by choosing who profile [profileId] allows. */
  data class NavigateToAllowedMembers(val profileId: Long) : EditNotificationProfileAction
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notificationprofilecreated

/**
 * One-shot side effects that have to be carried out by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface NotificationProfileCreatedAction {

  /** Replace this screen with the details screen for profile [profileId]. */
  data class NavigateToProfileDetails(val profileId: Long) : NotificationProfileCreatedAction
}

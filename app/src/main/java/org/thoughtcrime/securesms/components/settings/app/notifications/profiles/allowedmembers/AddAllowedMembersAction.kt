/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import org.signal.core.models.recipients.RecipientId

/**
 * One-shot side effects that need an Activity or the nav graph, and therefore have to be carried out
 * by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface AddAllowedMembersAction {

  /** Leave the screen. */
  data object NavigateBack : AddAllowedMembersAction

  /** Open the picker to choose who profile [profileId] allows, starting from [currentSelection]. */
  data class NavigateToSelectRecipients(val profileId: Long, val currentSelection: Set<RecipientId>) : AddAllowedMembersAction

  /** Continue the create flow by setting a schedule for profile [profileId]. */
  data class NavigateToSchedule(val profileId: Long) : AddAllowedMembersAction
}

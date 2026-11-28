/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.allowedmembers

import org.signal.core.models.recipients.RecipientId

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AddAllowedMembersEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : AddAllowedMembersEvent

  /** The user tapped "Add people or groups". */
  data object AddMembersClicked : AddAllowedMembersEvent

  /** The user tapped the remove button on a member. */
  data class RemoveMemberClicked(val recipientId: RecipientId) : AddAllowedMembersEvent

  /** The user tapped undo on the snackbar confirming that a member was removed. */
  data class UndoRemoveMemberClicked(val recipientId: RecipientId) : AddAllowedMembersEvent

  /** The user flipped the "Allow all calls" toggle. */
  data class AllowAllCallsToggled(val enabled: Boolean) : AddAllowedMembersEvent

  /** The user flipped the "Notify for all mentions" toggle. */
  data class NotifyForAllMentionsToggled(val enabled: Boolean) : AddAllowedMembersEvent

  /** The user tapped the next button. */
  data object NextClicked : AddAllowedMembersEvent

  /** The snackbar in [AddAllowedMembersState.snackbar] went away. */
  data object SnackbarDismissed : AddAllowedMembersEvent
}

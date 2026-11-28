/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.details

import org.signal.core.models.recipients.RecipientId

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface NotificationProfileDetailsEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : NotificationProfileDetailsEvent

  /** The user tapped the edit icon in the toolbar. */
  data object EditClicked : NotificationProfileDetailsEvent

  /** The user tapped the profile header or flipped its switch. */
  data object ProfileToggled : NotificationProfileDetailsEvent

  /** The user tapped "Add people or groups". */
  data object AddMembersClicked : NotificationProfileDetailsEvent

  /** The user tapped the remove button on a member. */
  data class RemoveMemberClicked(val recipientId: RecipientId) : NotificationProfileDetailsEvent

  /** The user tapped undo on the snackbar confirming that a member was removed. */
  data class UndoRemoveMemberClicked(val recipientId: RecipientId) : NotificationProfileDetailsEvent

  /** The user tapped "See all" below the collapsed member list. */
  data object SeeAllMembersClicked : NotificationProfileDetailsEvent

  /** The user tapped the schedule row. */
  data object ScheduleClicked : NotificationProfileDetailsEvent

  /** The user flipped the "Allow all calls" toggle. */
  data class AllowAllCallsToggled(val enabled: Boolean) : NotificationProfileDetailsEvent

  /** The user flipped the "Notify for all mentions" toggle. */
  data class NotifyForAllMentionsToggled(val enabled: Boolean) : NotificationProfileDetailsEvent

  /** The user tapped "Delete profile". */
  data object DeleteClicked : NotificationProfileDetailsEvent

  /** The user confirmed the delete dialog. */
  data object DeleteConfirmed : NotificationProfileDetailsEvent

  /** The dialog in [NotificationProfileDetailsState.dialog] was dismissed without confirming. */
  data object DialogDismissed : NotificationProfileDetailsEvent

  /** The snackbar in [NotificationProfileDetailsState.snackbar] went away. */
  data object SnackbarDismissed : NotificationProfileDetailsEvent
}

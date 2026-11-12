/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.hiddencontacts

import org.signal.core.models.recipients.RecipientId

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface HiddenContactsEvent {

  /** The screen came back to the foreground, so the list of hidden contacts may have changed. */
  data object ScreenResumed : HiddenContactsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : HiddenContactsEvent

  /** The user tapped a hidden contact, which asks them whether to unhide it. */
  data class ContactClicked(val id: RecipientId) : HiddenContactsEvent

  /** The user confirmed unhiding the contact in [HiddenContactsState.Dialog.ConfirmUnhide]. */
  data object UnhideConfirmed : HiddenContactsEvent

  /** Dismisses whatever is in [HiddenContactsState.dialog]. */
  data object DialogDismissed : HiddenContactsEvent
}

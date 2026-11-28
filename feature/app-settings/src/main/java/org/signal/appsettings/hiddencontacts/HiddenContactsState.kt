/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.hiddencontacts

import org.signal.core.models.recipients.RecipientId

/**
 * Everything [HiddenContactsScreen] needs to render.
 */
data class HiddenContactsState(
  val contacts: List<Contact> = emptyList(),
  val isLoading: Boolean = true,
  val dialog: Dialog = Dialog.None
) {

  val showEmptyState: Boolean
    get() = !isLoading && contacts.isEmpty()

  override fun toString(): String = "HiddenContactsState(contacts=${contacts.size} contacts, isLoading=$isLoading, dialog=$dialog)"

  /** A person the user has hidden. Their avatar is drawn by the host. */
  data class Contact(val id: RecipientId, val name: String) {
    override fun toString(): String = "Contact(id=$id)"
  }

  /** Whichever dialog the screen is showing, if any. */
  sealed interface Dialog {
    data object None : Dialog

    /** Confirms that [contact] should go back to being a regular contact. */
    data class ConfirmUnhide(val contact: Contact) : Dialog
  }
}

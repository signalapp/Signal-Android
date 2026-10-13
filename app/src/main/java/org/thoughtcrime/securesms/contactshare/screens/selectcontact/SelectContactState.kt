/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.selectcontact

import org.thoughtcrime.securesms.contacts.index.ContactIndexRecord

/** State for the contact picker. */
data class SelectContactState(
  val query: String = "",
  val contactsPermission: ContactsPermissionState = ContactsPermissionState.GRANTED,
  val isLoading: Boolean = true,
  /** Rows are paged, so emptiness is keyed off this rather than off the first loaded page. */
  val indexCount: Int = 0,
  val showPermissionDeniedSheet: Boolean = false
) {
  val isEmpty: Boolean
    get() = !isLoading && indexCount == 0

  val showFullScreenPermissionPrompt: Boolean
    get() = contactsPermission == ContactsPermissionState.DENIED && isEmpty && query.isEmpty()

  /** Shown above the list, so Signal contacts stay reachable while the prompt is up. */
  val showPermissionCard: Boolean
    get() = contactsPermission == ContactsPermissionState.DENIED && !isEmpty && query.isEmpty()

  val showSystemPickerButton: Boolean
    get() = contactsPermission.isAskingOver && query.isEmpty()

  val showPermissionFooter: Boolean
    get() = showSystemPickerButton

  enum class ContactsPermissionState {
    GRANTED,

    /** No permission, and the prompt has not been dismissed. */
    DENIED,

    /** No permission, and the user said no thanks. Only Signal contacts from here on. */
    DISMISSED,

    /** Denied at the device level, so the system will not prompt for it again. */
    PERMANENTLY_DENIED;

    /** Whether there is any point offering to ask for the permission again. */
    val isAskingOver: Boolean
      get() = this == DISMISSED || this == PERMANENTLY_DENIED
  }
}

/** A rendered line in the list. Headers are interleaved into the paged stream to keep it flat. */
sealed interface SelectContactRow {
  data class Header(val label: String) : SelectContactRow

  data class Contact(val contact: ContactIndexRecord) : SelectContactRow
}

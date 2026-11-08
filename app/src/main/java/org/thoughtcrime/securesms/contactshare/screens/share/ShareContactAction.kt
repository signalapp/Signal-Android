/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.share

import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts

sealed interface ShareContactAction {
  data object Exit : ShareContactAction

  data object InvalidContact : ShareContactAction

  data class EditName(val parts: ContactNameParts) : ShareContactAction

  data class Send(val contact: Contact) : ShareContactAction {
    override fun toString(): String = "Send(phones=${contact.phoneNumbers.size}, emails=${contact.emails.size}, addresses=${contact.postalAddresses.size}, hasAvatar=${contact.avatar != null})"
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.details

import org.thoughtcrime.securesms.recipients.RecipientId

sealed interface SharedContactDetailsAction {
  data object Exit : SharedContactDetailsAction

  data class CopyToClipboard(val text: String) : SharedContactDetailsAction {
    override fun toString(): String = "CopyToClipboard(hasText=${text.isNotBlank()})"
  }

  data class OpenInMaps(val address: String) : SharedContactDetailsAction {
    override fun toString(): String = "OpenInMaps(hasAddress=${address.isNotBlank()})"
  }

  data object AddToPhoneContacts : SharedContactDetailsAction

  /** A number gets an SMS, an email only card gets an email. */
  data class InviteBySms(val number: String) : SharedContactDetailsAction {
    override fun toString(): String = "InviteBySms(hasNumber=${number.isNotBlank()})"
  }

  data class InviteByEmail(val email: String) : SharedContactDetailsAction {
    override fun toString(): String = "InviteByEmail(hasEmail=${email.isNotBlank()})"
  }

  data class StartChat(val recipientId: RecipientId) : SharedContactDetailsAction
  data class StartVideoCall(val recipientId: RecipientId) : SharedContactDetailsAction
  data class StartAudioCall(val recipientId: RecipientId) : SharedContactDetailsAction
  data class AddToGroup(val recipientId: RecipientId) : SharedContactDetailsAction
}

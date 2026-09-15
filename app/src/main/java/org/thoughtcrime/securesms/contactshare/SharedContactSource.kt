/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Where a card being shared comes from.
 *
 * The picker can offer Signal connections that have no address book entry at all, and those have no
 * contact URI to identify them by. A shared contact is therefore identified by whichever of the two
 * sources it came from rather than by a URI alone.
 */
sealed interface SharedContactSource : Parcelable {

  /**
   * An address book entry, whether or not it is also on Signal.
   *
   * [recipientId] records which Signal account the picker row stood for, since one contact can hold
   * several registered numbers.
   */
  @Parcelize
  data class AddressBook(val contactUri: Uri, val recipientId: RecipientId? = null) : SharedContactSource

  /** One phone number picked from the system picker, so only a name and that number, never the rest of the entry. */
  @Parcelize
  data class SystemPhone(val dataUri: Uri) : SharedContactSource

  /** A Signal connection with no address book entry, so there is nothing but a profile to share. */
  @Parcelize
  data class SignalContact(val recipientId: RecipientId) : SharedContactSource

  /** A .vcf shared into a conversation from outside, which never passes through the picker. */
  @Parcelize
  data class VCard(val uri: Uri) : SharedContactSource
}

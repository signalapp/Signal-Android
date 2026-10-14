/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.signal.core.models.UriSerializer
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Where a card being shared comes from.
 *
 * The picker can offer Signal connections that have no address book entry at all, and those have no
 * contact URI to identify them by. A shared contact is therefore identified by whichever of the two
 * sources it came from rather than by a URI alone.
 */
@Serializable
sealed interface SharedContactSource : Parcelable {

  /**
   * An address book entry, whether or not it is also on Signal.
   *
   * [recipientId] records which Signal account the picker row stood for, since one contact can hold
   * several registered numbers.
   */
  @Parcelize
  @Serializable
  data class AddressBook(@Serializable(with = UriSerializer::class) val contactUri: Uri, val recipientId: RecipientId? = null) : SharedContactSource

  /** One phone number picked from the system picker, so only a name and that number, never the rest of the entry. */
  @Parcelize
  @Serializable
  data class SystemPhone(@Serializable(with = UriSerializer::class) val dataUri: Uri) : SharedContactSource

  /** A Signal connection with no address book entry, so there is nothing but a profile to share. */
  @Parcelize
  @Serializable
  data class SignalContact(val recipientId: RecipientId) : SharedContactSource

  /** A .vcf shared into a conversation from outside, which never passes through the picker. */
  @Parcelize
  @Serializable
  data class VCard(@Serializable(with = UriSerializer::class) val uri: Uri) : SharedContactSource
}

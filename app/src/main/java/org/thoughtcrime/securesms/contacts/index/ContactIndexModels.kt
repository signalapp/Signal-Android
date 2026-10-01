/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.index

import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Which sources a row came from, which is what decides the badges and actions the row offers.
 */
enum class ContactIndexType(val id: Int) {
  /** In the address book, not a registered Signal user. Can be invited. */
  SYSTEM_ONLY(0),

  /** A Signal recipient with no address book entry. Has no contact detail to share beyond a name. */
  SIGNAL_ONLY(1),

  /** In the address book and on Signal. */
  BOTH(2);

  companion object {
    fun fromId(id: Int): ContactIndexType? = entries.firstOrNull { it.id == id }
  }
}

/**
 * A row on its way into the index. Carries the sort key, which the finished table does not keep.
 */
class ContactIndexEntry(
  val sortKey: ByteArray,
  val type: ContactIndexType,
  val section: String,
  val displayName: String,
  val searchText: String,
  val recipientId: RecipientId? = null,
  val lookupKey: String? = null,
  val contactId: Long? = null,
  /**
   * Whether [displayName] is a personal name. False when the provider had to fall back to a company,
   * email, or phone number, which is what sends a row to the "#" section and makes it render a person
   * glyph rather than initials.
   */
  val hasPersonalName: Boolean = true,
  val hasPhoto: Boolean = false
)

/** A row read back out of the index. Details are fetched from the provider once a row is selected. */
data class ContactIndexRecord(
  /** Position in the full list, 1 based, which is also the row id. */
  val position: Long,
  val type: ContactIndexType,
  val section: String,
  val displayName: String,
  val recipientId: RecipientId?,
  val lookupKey: String?,
  val contactId: Long?,
  val hasPersonalName: Boolean,
  val hasPhoto: Boolean
) {
  val isOnSignal: Boolean
    get() = type == ContactIndexType.SIGNAL_ONLY || type == ContactIndexType.BOTH

  val isInAddressBook: Boolean
    get() = type == ContactIndexType.SYSTEM_ONLY || type == ContactIndexType.BOTH

  override fun toString(): String {
    return "ContactIndexRecord(position=$position, type=$type, recipientId=$recipientId, hasLookupKey=${lookupKey != null}, contactId=$contactId, hasPersonalName=$hasPersonalName, hasPhoto=$hasPhoto)"
  }
}

/**
 * Outcome of building the index.
 */
sealed interface ContactIndexBuildResult {
  data class Success(val count: Int) : ContactIndexBuildResult

  /**
   * Built from Signal recipients alone. Not a failure, since the design shows Signal connections
   * whether or not we can read the address book.
   */
  data class SignalOnly(val count: Int) : ContactIndexBuildResult

  /** Not enough free space to hold the index, so there is nothing to show. */
  data object OutOfSpace : ContactIndexBuildResult

  data class Failure(val cause: Throwable) : ContactIndexBuildResult
}

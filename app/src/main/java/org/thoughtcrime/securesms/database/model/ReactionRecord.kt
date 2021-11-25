package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Represents an individual reaction to a message.
 */
data class ReactionRecord(
  val emoji: String,
  val author: RecipientId,
  val dateSent: Long,
  val dateReceived: Long
)

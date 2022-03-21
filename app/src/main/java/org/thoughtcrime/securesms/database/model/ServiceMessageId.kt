package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Represents the messages "ID" from the service's perspective, which identifies messages via a
 * a (sender, timestamp) pair.
 */
data class ServiceMessageId(
  val sender: RecipientId,
  val sentTimestamp: Long
) {
  override fun toString(): String {
    return "MessageId($sender, $sentTimestamp)"
  }
}

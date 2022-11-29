package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.internal.push.SignalServiceProtos

/**
 * Model class for reading from the [org.thoughtcrime.securesms.database.MessageSendLogTables].
 */
data class MessageLogEntry(
  val recipientId: RecipientId,
  val dateSent: Long,
  val content: SignalServiceProtos.Content,
  val contentHint: ContentHint,
  @get:JvmName("isUrgent")
  val urgent: Boolean,
  val relatedMessages: List<MessageId>
) {
  val hasRelatedMessage: Boolean
    @JvmName("hasRelatedMessage")
    get() = relatedMessages.isNotEmpty()
}

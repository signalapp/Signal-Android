package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.recipients.RecipientId

class StoryResult(
  val recipientId: RecipientId,
  val messageId: Long,
  val messageSentTimestamp: Long,
  val isOutgoing: Boolean
)

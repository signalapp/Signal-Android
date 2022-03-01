package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.recipients.RecipientId

data class DistributionListPartialRecord(
  val id: DistributionListId,
  val name: CharSequence,
  val recipientId: RecipientId,
  val allowsReplies: Boolean
)

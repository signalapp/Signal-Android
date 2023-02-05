package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.recipients.RecipientId

/** A model for [org.thoughtcrime.securesms.database.PendingRetryReceiptTable] */
data class PendingRetryReceiptModel(
  val id: Long,
  val author: RecipientId,
  val authorDevice: Int,
  val sentTimestamp: Long,
  val receivedTimestamp: Long,
  val threadId: Long
)

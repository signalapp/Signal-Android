package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.DistributionId

/**
 * Represents an entry in the [org.thoughtcrime.securesms.database.DistributionListDatabase].
 */
data class DistributionListRecord(
  val id: DistributionListId,
  val name: String,
  val distributionId: DistributionId,
  val allowsReplies: Boolean,
  val members: List<RecipientId>
)

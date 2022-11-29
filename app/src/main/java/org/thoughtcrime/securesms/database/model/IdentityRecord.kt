package org.thoughtcrime.securesms.database.model

import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.recipients.RecipientId

data class IdentityRecord(
  val recipientId: RecipientId,
  val identityKey: IdentityKey,
  val verifiedStatus: IdentityTable.VerifiedStatus,
  @get:JvmName("isFirstUse")
  val firstUse: Boolean,
  val timestamp: Long,
  @get:JvmName("isApprovedNonBlocking")
  val nonblockingApproval: Boolean
)

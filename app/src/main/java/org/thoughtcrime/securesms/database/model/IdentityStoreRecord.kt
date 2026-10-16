package org.thoughtcrime.securesms.database.model

import org.signal.core.models.recipients.RecipientId
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.database.IdentityTable

data class IdentityStoreRecord(
  val addressName: String,
  val identityKey: IdentityKey,
  val verifiedStatus: IdentityTable.VerifiedStatus,
  val firstUse: Boolean,
  val timestamp: Long,
  val nonblockingApproval: Boolean
) {
  fun toIdentityRecord(recipientId: RecipientId): IdentityRecord {
    return IdentityRecord(
      recipientId = recipientId,
      identityKey = identityKey,
      verifiedStatus = verifiedStatus,
      firstUse = firstUse,
      timestamp = timestamp,
      nonblockingApproval = nonblockingApproval
    )
  }
}

package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.database.IdentityDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.libsignal.IdentityKey

data class IdentityRecord(
  val recipientId: RecipientId,
  val identityKey: IdentityKey,
  val verifiedStatus: IdentityDatabase.VerifiedStatus,
  @get:JvmName("isFirstUse")
  val firstUse: Boolean,
  val timestamp: Long,
  @get:JvmName("isApprovedNonBlocking")
  val nonblockingApproval: Boolean
)

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.service

import org.signal.core.models.ServiceId
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.zkgroup.groups.GroupIdentifier
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsement
import org.signal.network.crypto.UnidentifiedAccess
import org.signal.network.service.MessageService.SendSuccess
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.push.DistributionId
import kotlin.time.Duration

/** The app state and bookkeeping [MessageService] needs. */
interface MessageSendDependencies {
  val localAci: ServiceId.ACI
  val localDeviceId: Int

  /** Content larger than this is rejected before sending. 0 means no limit. */
  val maxContentSizeBytes: Long

  val senderKeyMaxAge: Duration
  val isSenderKeyMinimumRemoved: Boolean

  fun getSenderCertificate(): SenderCertificate?
  fun getDistributionId(groupId: GroupIdentifier): DistributionId

  fun getGroupSendInfo(groupId: GroupIdentifier): GroupSendInfo?

  fun refreshGroupSendEndorsements(groupId: GroupIdentifier): GroupSendInfo?

  fun getSenderKeyCreatedAt(distributionId: DistributionId): Long
  fun rotateSenderKey(distributionId: DistributionId)

  fun onMissingGroupSendEndorsement()
  fun onGroupSendFallback()

  /** Adds [success] for [recipientId] to the message send log entry [entryId], creating the entry when [entryId] is -1. Returns the entry id. */
  fun logSendResult(entryId: Long, recipientId: Long, serviceId: ServiceId, success: SendSuccess, timestamp: Long, contentHint: ContentHint, messageId: Long, urgent: Boolean): Long
}

/** [id] is the caller's own id for the recipient. It keys [GroupSendInfo] and is handed back to [MessageSendDependencies.logSendResult]. */
data class GroupSendRecipient(
  val id: Long,
  val serviceId: ServiceId,
  val unidentifiedAccess: UnidentifiedAccess?
)

data class GroupSendInfo(
  val isMember: Boolean,
  val members: Set<Long>,
  val endorsements: Map<Long, GroupSendEndorsement?>,
  val expirationMs: Long,
  val groupSecretParams: GroupSecretParams
)

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messages

import android.content.Context
import org.signal.core.models.ServiceId
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.groups.GroupChangeException
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.zkgroup.groups.GroupIdentifier
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.network.service.GroupSendInfo
import org.signal.network.service.GroupSendRecipient
import org.signal.network.service.MessageSendDependencies
import org.signal.network.service.MessageService.SendSuccess
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.crypto.SenderKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.toSendMessageResult
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.push.DistributionId
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The app's [MessageSendDependencies], backed by the database, protocol stores and job manager. */
class AppMessageSendDependencies(private val context: Context) : MessageSendDependencies {

  companion object {
    private val TAG = Log.tag(AppMessageSendDependencies::class)

    /** Maps recipients for a group send, including each one's sealed sender access. Recipients that are unregistered, unknown or without a service id can't be sent to and are left out. */
    @JvmStatic
    fun toGroupSendRecipients(recipients: List<Recipient>): List<GroupSendRecipient> {
      val (sendable, unsendable) = recipients.partition { it.hasServiceId && !it.isUnregistered && !it.isUnknown }
      if (unsendable.isNotEmpty()) {
        Log.w(TAG, "Leaving ${unsendable.size} unregistered, unknown or service id-less recipients out of a group send.")
      }

      val accessById = SealedSenderAccessUtil.getAccessMapFor(sendable, false)
      return sendable.map { recipient ->
        GroupSendRecipient(
          id = recipient.id.toLong(),
          serviceId = recipient.requireServiceId(),
          unidentifiedAccess = accessById[recipient.id]?.orNull()
        )
      }
    }
  }

  override val localAci: ServiceId.ACI
    get() = SignalStore.account.requireAci()

  override val localDeviceId: Int
    get() = SignalStore.account.deviceId

  override val maxContentSizeBytes: Long
    get() = RemoteConfig.maxEnvelopeSizeBytes

  override val senderKeyMaxAge: Duration
    get() = RemoteConfig.senderKeyMaxAge.milliseconds

  override val isSenderKeyMinimumRemoved: Boolean
    get() = SignalStore.internal.removeSenderKeyMinimum

  override fun getSenderCertificate(): SenderCertificate? = SealedSenderAccessUtil.getSealedSenderCertificate()

  override fun getDistributionId(groupId: GroupIdentifier): DistributionId = SignalDatabase.groups.getOrCreateDistributionId(GroupId.v2(groupId))

  override fun getGroupSendInfo(groupId: GroupIdentifier): GroupSendInfo? {
    val id = GroupId.v2(groupId)
    val group = SignalDatabase.groups.getGroup(id).orNull()?.takeIf { it.hasV2GroupProperties } ?: return null
    return GroupSendInfo(
      isMember = group.isMember,
      members = group.members.map { it.toLong() }.toSet(),
      endorsements = SignalDatabase.groups.getGroupSendEndorsements(id).endorsements.mapKeys { it.key.toLong() },
      expirationMs = group.groupSendEndorsementExpiration,
      groupSecretParams = GroupSecretParams.deriveFromMasterKey(group.requireV2GroupProperties().groupMasterKey)
    )
  }

  override fun refreshGroupSendEndorsements(groupId: GroupIdentifier): GroupSendInfo? {
    val id = GroupId.v2(groupId)
    val masterKey = SignalDatabase.groups.getGroup(id).orNull()?.requireV2GroupProperties()?.groupMasterKey ?: return null

    val refreshed = try {
      GroupManager.updateGroupSendEndorsements(context, masterKey)
      getGroupSendInfo(groupId)
    } catch (e: GroupChangeException) {
      Log.w(TAG, "Unable to update group send endorsements", e)
      null
    } catch (e: IOException) {
      Log.w(TAG, "Unable to update group send endorsements", e)
      null
    }

    AppDependencies.jobManager.add(RequestGroupV2InfoJob(id))
    return refreshed
  }

  override fun getSenderKeyCreatedAt(distributionId: DistributionId): Long = SenderKeyUtil.getCreateTimeForOurKey(distributionId)

  override fun rotateSenderKey(distributionId: DistributionId) = SenderKeyUtil.rotateOurKey(distributionId)

  override fun onMissingGroupSendEndorsement() = GroupSendEndorsementInternalNotifier.maybePostMissingGroupSendEndorsement(context)

  override fun onGroupSendFallback() = GroupSendEndorsementInternalNotifier.maybePostGroupSendFallbackError(context)

  override fun logSendResult(entryId: Long, recipientId: Long, serviceId: ServiceId, success: SendSuccess, timestamp: Long, contentHint: ContentHint, messageId: Long, urgent: Boolean): Long {
    val sendMessageResult = success.toSendMessageResult(serviceId)
    return if (entryId == -1L) {
      SignalDatabase.messageLog.insertIfPossible(RecipientId.from(recipientId), timestamp, sendMessageResult, contentHint, MessageId(messageId), urgent)
    } else {
      SignalDatabase.messageLog.addRecipientToExistingEntryIfPossible(entryId, RecipientId.from(recipientId), timestamp, sendMessageResult, contentHint, MessageId(messageId), urgent)
    }
  }
}

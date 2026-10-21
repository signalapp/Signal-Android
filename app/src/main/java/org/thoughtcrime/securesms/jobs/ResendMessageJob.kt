package org.thoughtcrime.securesms.jobs

import kotlinx.coroutines.delay
import okio.ByteString.Companion.toByteString
import org.signal.core.models.ServiceId
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidSessionException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.network.service.MessageService
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.Content
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Resends a previously-sent message in response to receiving a retry receipt.
 *
 * Not for arbitrary retries due to network flakiness or something -- those should be handled within each individual job.
 */
class ResendMessageJob private constructor(
  parameters: Parameters,
  private val recipientId: RecipientId,
  private val sentTimestamp: Long,
  private val content: Content,
  private val contentHint: ContentHint,
  private val urgent: Boolean,
  private val groupId: GroupId.V2?,
  private val distributionId: DistributionId?
) : CoroutineJob(parameters) {

  companion object {
    const val KEY: String = "ResendMessageJob"

    private val TAG = Log.tag(ResendMessageJob::class.java)

    private const val KEY_RECIPIENT_ID = "recipient_id"
    private const val KEY_SENT_TIMESTAMP = "sent_timestamp"
    private const val KEY_CONTENT = "content"
    private const val KEY_CONTENT_HINT = "content_hint"
    private const val KEY_URGENT = "urgent"
    private const val KEY_GROUP_ID = "group_id"
    private const val KEY_DISTRIBUTION_ID = "distribution_id"
  }

  constructor(
    recipientId: RecipientId,
    sentTimestamp: Long,
    content: Content,
    contentHint: ContentHint,
    urgent: Boolean,
    groupId: GroupId.V2?,
    distributionId: DistributionId?
  ) : this(
    parameters = Parameters.Builder()
      .setQueue(recipientId.toQueueKey())
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .build(),
    recipientId = recipientId,
    sentTimestamp = sentTimestamp,
    content = content,
    contentHint = contentHint,
    urgent = urgent,
    groupId = groupId,
    distributionId = distributionId
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_RECIPIENT_ID, recipientId.serialize())
      .putLong(KEY_SENT_TIMESTAMP, sentTimestamp)
      .putBlobAsString(KEY_CONTENT, content.encode())
      .putInt(KEY_CONTENT_HINT, contentHint.type)
      .putBoolean(KEY_URGENT, urgent)
      .putBlobAsString(KEY_GROUP_ID, groupId?.decodedId)
      .putString(KEY_DISTRIBUTION_ID, distributionId?.toString())
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (SignalStore.internal.delayResends) {
      Log.w(TAG, "Delaying resend by 10 sec because of an internal preference.")
      delay(10.seconds)
    }

    Log.i(TAG, "[$sentTimestamp] Resending message to $recipientId (urgent: $urgent, contentHint: ${contentHint.name}, groupId: $groupId, distributionId: $distributionId)")

    val recipient = SignalDatabase.recipients.getRecord(recipientId)

    if (recipient.registered == RecipientTable.RegisteredState.NOT_REGISTERED) {
      Log.w(TAG, "${recipient.id} is unregistered!")
      return Result.success()
    }

    val serviceId = recipient.serviceId
    if (serviceId == null) {
      Log.w(TAG, "${recipient.id} has no serviceId!")
      return Result.failure()
    }

    var contentToSend = content
    var createGroupSendToken: SealedSenderAccess.CreateGroupSendToken? = null

    if (distributionId != null) {
      if (groupId != null) {
        Log.d(TAG, "GroupId is present. Assuming this is a group message.")
        val groupRecord = SignalDatabase.groups.getGroupByDistributionId(distributionId).orElse(null)

        if (groupRecord == null) {
          Log.w(TAG, "Could not find a matching group for the distributionId! Skipping message send.")
          return Result.success()
        } else if (!groupRecord.members.contains(recipientId)) {
          Log.w(TAG, "The target user is no longer in the group! Skipping message send.")
          return Result.success()
        }

        createGroupSendToken = SealedSenderAccess.CreateGroupSendToken { SignalDatabase.groups.getGroupSendFullToken(groupId, recipientId) }
      } else {
        Log.d(TAG, "GroupId is not present. Assuming this is a message for a distribution list.")
        val listRecord = SignalDatabase.distributionLists.getListByDistributionId(distributionId)

        if (listRecord == null) {
          Log.w(TAG, "Could not find a matching distribution list for the distributionId! Skipping message send.")
          return Result.success()
        } else if (!listRecord.members.contains(recipientId)) {
          Log.w(TAG, "The target user is no longer in the distribution list! Skipping message send.")
          return Result.success()
        }
      }

      val senderKeyDistributionMessage = OutgoingContent.senderKeyDistributionMessage(distributionId)
      contentToSend = contentToSend.newBuilder().senderKeyDistributionMessage(senderKeyDistributionMessage.serialize().toByteString()).build()
    }

    val envelopeContent = EnvelopeContent.encrypted(contentToSend, contentHint, Optional.ofNullable(groupId?.decodedId))

    val result = if (serviceId == SignalStore.account.aci) {
      SignalNetwork.messageService.sendSyncMessage(sentTimestamp, envelopeContent, urgent, null)
    } else {
      SignalNetwork.messageService.sendMessage(
        serviceId = serviceId,
        envelopeContent = envelopeContent,
        timestamp = sentTimestamp,
        sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient, createGroupSendToken),
        story = false,
        isOnline = false,
        urgent = urgent
      )
    }

    return result.fold(
      ifRight = { success ->
        if (distributionId != null) {
          val addresses = success.devices.map { serviceId.toProtocolAddress(it) }
          AppDependencies.protocolStore.aci().markSenderKeySharedWith(distributionId, addresses)
        }
        Result.success()
      },
      ifLeft = { error ->
        when (error) {
          is MessageService.SendError.IdentityMismatch -> {
            Log.w(TAG, "[$sentTimestamp] Identity mismatch for $recipientId, not resending.")
            Result.success()
          }

          is MessageService.SendError.NotRegistered,
          is MessageService.SendError.Unauthorized,
          is MessageService.SendError.ContentTooLarge,
          is MessageService.SendError.PreKeyUnavailable -> {
            Log.w(TAG, "[$sentTimestamp] Failed to resend message.", error)
            Result.failure()
          }

          is MessageService.SendError.ChallengeRequired -> error.tryPushChallengeAndToJobResult(TAG, defaultBackoff = null)

          is MessageService.SendError.RateLimited -> {
            Log.w(TAG, "[$sentTimestamp] Rate limited, retryAfter=${error.retryAfter}. Not resending.")
            Result.failure()
          }

          is MessageService.SendError.SessionAttemptsExhausted -> error.toJobResult(TAG, defaultBackoff())
          is MessageService.SendError.NetworkError -> error.toJobResult(TAG, defaultBackoff())
          is MessageService.SendError.ApplicationError -> {
            if (error.exception is NoSessionException || error.exception is InvalidSessionException) {
              resetSessions(serviceId)
            }
            error.toJobResult(TAG, "resend", defaultBackoff())
          }
        }
      }
    )
  }

  private fun resetSessions(serviceId: ServiceId) {
    Log.w(TAG, "[$sentTimestamp] Bad session for $recipientId. Archiving all of their sessions and clearing sender key shared state.")
    val address = serviceId.toProtocolAddress(SignalServiceAddress.DEFAULT_DEVICE_ID)

    AppDependencies.protocolStore.aci().sessions().archiveSessions(recipientId, SignalServiceAddress.DEFAULT_DEVICE_ID)
    AppDependencies.protocolStore.aci().sessions().archiveSiblingSessions(address)

    AppDependencies.protocolStore.pniOrNull()?.let { pniStore ->
      pniStore.sessions().archiveSessions(recipientId, SignalServiceAddress.DEFAULT_DEVICE_ID)
      pniStore.sessions().archiveSiblingSessions(address)
    }

    SignalDatabase.senderKeyShared.deleteAllFor(recipientId)
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<ResendMessageJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ResendMessageJob {
      val data = JsonJobData.deserialize(serializedData)

      val content = Content.ADAPTER.decode(data.getStringAsBlob(KEY_CONTENT)!!)
      val groupId = data.getStringAsBlob(KEY_GROUP_ID)?.let { GroupId.pushOrThrow(it).requireV2() }
      val distributionId = data.getString(KEY_DISTRIBUTION_ID)?.let { DistributionId.from(it) }

      return ResendMessageJob(
        parameters = parameters,
        recipientId = RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
        sentTimestamp = data.getLong(KEY_SENT_TIMESTAMP),
        content = content,
        contentHint = ContentHint.fromType(data.getInt(KEY_CONTENT_HINT)),
        urgent = data.getBooleanOrDefault(KEY_URGENT, true),
        groupId = groupId,
        distributionId = distributionId
      )
    }
  }
}

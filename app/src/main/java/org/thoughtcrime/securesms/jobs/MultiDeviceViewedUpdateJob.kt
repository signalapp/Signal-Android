package org.thoughtcrime.securesms.jobs

import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.JsonUtils
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage
import kotlin.time.Duration.Companion.days

class MultiDeviceViewedUpdateJob private constructor(parameters: Parameters, messageIds: List<SyncMessageId>) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceViewedUpdateJob"

    private val TAG = Log.tag(MultiDeviceViewedUpdateJob::class.java)

    private const val KEY_MESSAGE_IDS = "message_ids"

    /**
     * Enqueues all the necessary jobs for read receipts, ensuring that they're all within the
     * maximum size.
     */
    @JvmStatic
    fun enqueue(messageIds: List<SyncMessageId>) {
      val messageIdChunks = messageIds.chunked(SendReadReceiptJob.MAX_TIMESTAMPS)

      if (messageIdChunks.size > 1) {
        Log.w(TAG, "Large receipt count! Had to break into multiple chunks. Total count: ${messageIds.size}")
      }

      for (chunk in messageIdChunks) {
        AppDependencies.jobManager.add(MultiDeviceViewedUpdateJob(chunk))
      }
    }
  }

  private val messageIds: List<SerializableSyncMessageId> = messageIds.map { SerializableSyncMessageId(it.recipientId.serialize(), it.timetamp) }

  private constructor(messageIds: List<SyncMessageId>) : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    SendReadReceiptJob.ensureSize(messageIds, SendReadReceiptJob.MAX_TIMESTAMPS)
  )

  override fun serialize(): ByteArray? {
    val ids = messageIds.map { JsonUtils.toJson(it) }.toTypedArray()
    return JsonJobData.Builder().putStringArray(KEY_MESSAGE_IDS, ids).serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered, aborting...")
      return Result.failure()
    }

    if (!SignalStore.account.isMultiDevice) {
      Log.i(TAG, "Not multi device...")
      return Result.success()
    }

    val viewedMessages = messageIds.mapNotNull { messageId ->
      val recipient = SignalDatabase.recipients.getRecord(RecipientId.from(messageId.recipientId))
      val serviceId = recipient.serviceId
      if (recipient.groupId == null && recipient.registered != RegisteredState.NOT_REGISTERED && serviceId != null) {
        SyncMessage.Viewed(senderAciBinary = serviceId.toByteString(), timestamp = messageId.timestamp)
      } else {
        null
      }
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncViewed(viewedMessages),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "viewed sync", defaultBackoff()) }
    )
  }

  override fun onFailure() = Unit

  private data class SerializableSyncMessageId(
    @JsonProperty val recipientId: String,
    @JsonProperty val timestamp: Long
  )

  class Factory : Job.Factory<MultiDeviceViewedUpdateJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceViewedUpdateJob {
      val data = JsonJobData.deserialize(serializedData)

      val ids = data.getStringArray(KEY_MESSAGE_IDS)
        .map { JsonUtils.fromJson(it, SerializableSyncMessageId::class.java) }
        .map { SyncMessageId(RecipientId.from(it.recipientId), it.timestamp) }

      return MultiDeviceViewedUpdateJob(parameters, ids)
    }
  }
}

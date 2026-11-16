package org.thoughtcrime.securesms.jobs

import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.JsonUtils
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.SignalDatabase
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

class MultiDeviceViewOnceOpenJob private constructor(parameters: Parameters, syncMessageId: SyncMessageId) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceRevealUpdateJob"

    private val TAG = Log.tag(MultiDeviceViewOnceOpenJob::class.java)

    private const val KEY_MESSAGE_ID = "message_id"
  }

  private val messageId = SerializableSyncMessageId(syncMessageId.recipientId.serialize(), syncMessageId.timetamp)

  constructor(messageId: SyncMessageId) : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    messageId
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder().putString(KEY_MESSAGE_ID, JsonUtils.toJson(messageId)).serialize()
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

    val recipient = SignalDatabase.recipients.getRecord(RecipientId.from(messageId.recipientId))
    val serviceId = recipient.serviceId

    if (recipient.registered == RegisteredState.NOT_REGISTERED || serviceId == null) {
      Log.w(TAG, "${recipient.id} not registered!")
      return Result.failure()
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncViewOnceOpen(SyncMessage.ViewOnceOpen(senderAciBinary = serviceId.toByteString(), timestamp = messageId.timestamp)),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "view-once open sync", defaultBackoff()) }
    )
  }

  override fun onFailure() = Unit

  private data class SerializableSyncMessageId(
    @JsonProperty val recipientId: String,
    @JsonProperty val timestamp: Long
  )

  class Factory : Job.Factory<MultiDeviceViewOnceOpenJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceViewOnceOpenJob {
      val data = JsonJobData.deserialize(serializedData)
      val messageId = JsonUtils.fromJson(data.getString(KEY_MESSAGE_ID), SerializableSyncMessageId::class.java)

      return MultiDeviceViewOnceOpenJob(parameters, SyncMessageId(RecipientId.from(messageId.recipientId), messageId.timestamp))
    }
  }
}

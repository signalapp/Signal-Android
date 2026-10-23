package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
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

class MultiDeviceMessageRequestResponseJob private constructor(
  parameters: Parameters,
  private val threadRecipient: RecipientId,
  private val type: Type
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceMessageRequestResponseJob"

    private val TAG = Log.tag(MultiDeviceMessageRequestResponseJob::class.java)

    private const val KEY_THREAD_RECIPIENT = "thread_recipient"
    private const val KEY_TYPE = "type"

    @JvmStatic
    fun forAccept(threadRecipient: RecipientId): MultiDeviceMessageRequestResponseJob {
      return MultiDeviceMessageRequestResponseJob(threadRecipient, Type.ACCEPT)
    }

    @JvmStatic
    fun forDelete(threadRecipient: RecipientId): MultiDeviceMessageRequestResponseJob {
      return MultiDeviceMessageRequestResponseJob(threadRecipient, Type.DELETE)
    }

    @JvmStatic
    fun forBlock(threadRecipient: RecipientId): MultiDeviceMessageRequestResponseJob {
      return MultiDeviceMessageRequestResponseJob(threadRecipient, Type.BLOCK)
    }

    @JvmStatic
    fun forBlockAndDelete(threadRecipient: RecipientId): MultiDeviceMessageRequestResponseJob {
      return MultiDeviceMessageRequestResponseJob(threadRecipient, Type.BLOCK_AND_DELETE)
    }

    @JvmStatic
    fun forBlockAndReportSpam(threadRecipient: RecipientId): MultiDeviceMessageRequestResponseJob {
      return MultiDeviceMessageRequestResponseJob(threadRecipient, Type.BLOCK_AND_SPAM)
    }

    @JvmStatic
    fun forReportSpam(threadRecipient: RecipientId): MultiDeviceMessageRequestResponseJob {
      return MultiDeviceMessageRequestResponseJob(threadRecipient, Type.SPAM)
    }
  }

  private constructor(threadRecipient: RecipientId, type: Type) : this(
    Parameters.Builder()
      .setQueue("MultiDeviceMessageRequestResponseJob")
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(1.days.inWholeMilliseconds)
      .build(),
    threadRecipient,
    type
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_THREAD_RECIPIENT, threadRecipient.serialize())
      .putInt(KEY_TYPE, type.value)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered, aborting...")
      return Result.failure()
    }

    if (!SignalStore.account.isMultiDevice) {
      Log.i(TAG, "Not multi device, aborting...")
      return Result.success()
    }

    if (!SignalDatabase.recipients.containsId(threadRecipient)) {
      Log.i(TAG, "Missing record for recipient, likely a deleted group")
      return Result.success()
    }

    val recipient = SignalDatabase.recipients.getRecord(threadRecipient)
    val groupId = recipient.groupId
    val serviceId = recipient.serviceId

    if (groupId == null && serviceId == null) {
      Log.i(TAG, "Queued for non-group recipient without ServiceId")
      return Result.success()
    }

    val response = when {
      groupId != null -> SyncMessage.MessageRequestResponse(groupId = groupId.decodedId.toByteString(), type = type.toRemoteType())
      serviceId != null && recipient.registered != RegisteredState.NOT_REGISTERED -> SyncMessage.MessageRequestResponse(threadAciBinary = serviceId.toByteString(), type = type.toRemoteType())
      else -> null
    }

    if (response == null) {
      Log.w(TAG, "${recipient.id} not registered!")
      return Result.failure()
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncMessageRequestResponse(response),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "message request response sync", defaultBackoff()) }
    )
  }

  override fun onFailure() = Unit

  private enum class Type(val value: Int) {
    UNKNOWN(0),
    ACCEPT(1),
    DELETE(2),
    BLOCK(3),
    BLOCK_AND_DELETE(4),
    SPAM(5),
    BLOCK_AND_SPAM(6);

    fun toRemoteType(): SyncMessage.MessageRequestResponse.Type {
      return when (this) {
        UNKNOWN -> SyncMessage.MessageRequestResponse.Type.UNKNOWN
        ACCEPT -> SyncMessage.MessageRequestResponse.Type.ACCEPT
        DELETE -> SyncMessage.MessageRequestResponse.Type.DELETE
        BLOCK -> SyncMessage.MessageRequestResponse.Type.BLOCK
        BLOCK_AND_DELETE -> SyncMessage.MessageRequestResponse.Type.BLOCK_AND_DELETE
        SPAM -> SyncMessage.MessageRequestResponse.Type.SPAM
        BLOCK_AND_SPAM -> SyncMessage.MessageRequestResponse.Type.BLOCK_AND_SPAM
      }
    }

    companion object {
      fun deserialize(value: Int): Type {
        return entries.firstOrNull { it.value == value } ?: throw AssertionError("Unknown type: $value")
      }
    }
  }

  class Factory : Job.Factory<MultiDeviceMessageRequestResponseJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceMessageRequestResponseJob {
      val data = JsonJobData.deserialize(serializedData)

      return MultiDeviceMessageRequestResponseJob(
        parameters,
        RecipientId.from(data.getString(KEY_THREAD_RECIPIENT)),
        Type.deserialize(data.getInt(KEY_TYPE))
      )
    }
  }
}

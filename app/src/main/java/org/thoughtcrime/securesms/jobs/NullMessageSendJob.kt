package org.thoughtcrime.securesms.jobs

import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidSessionException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.network.service.MessageService
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.createNullMessageEnvelopeContent
import java.util.concurrent.TimeUnit

/**
 * Just sends an empty message to a target recipient. Only suitable for individuals, NOT groups.
 */
class NullMessageSendJob private constructor(
  private val recipientId: RecipientId,
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    const val KEY: String = "NullMessageSendJob"

    private val TAG = Log.tag(NullMessageSendJob::class.java)

    private const val KEY_RECIPIENT_ID = "recipient_id"
  }

  constructor(recipientId: RecipientId) : this(
    recipientId = recipientId,
    parameters = Parameters.Builder()
      .setQueue(recipientId.toQueueKey())
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build()
  )

  override fun serialize(): ByteArray? = JsonJobData.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize()).serialize()

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!SignalDatabase.recipients.containsId(recipientId)) {
      Log.w(TAG, "Cannot find recipient, likely deleted group.")
      return Result.success()
    }

    val recipient = SignalDatabase.recipients.getRecord(recipientId)

    if (recipient.groupId != null) {
      Log.w(TAG, "Groups are not supported!")
      return Result.success()
    }

    if (recipient.registered == RegisteredState.NOT_REGISTERED) {
      Log.w(TAG, "${recipient.id} not registered!")
    }

    val serviceId = recipient.serviceId
    if (serviceId == null) {
      Log.w(TAG, "${recipient.id} has no serviceId!")
      return Result.failure()
    }

    val result = if (serviceId == SignalStore.account.aci) {
      SignalNetwork.messageService.sendSyncMessage(
        timestamp = System.currentTimeMillis(),
        envelopeContent = createNullMessageEnvelopeContent(),
        urgent = false,
        onEncrypted = null
      )
    } else {
      SignalNetwork.messageService.sendMessage(
        serviceId = serviceId,
        envelopeContent = createNullMessageEnvelopeContent(),
        timestamp = System.currentTimeMillis(),
        sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient),
        story = false,
        isOnline = false,
        urgent = false
      )
    }

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error ->
        when (error) {
          is MessageService.SendError.IdentityMismatch -> {
            Log.w(TAG, "Unable to send null message due to an identity mismatch.")
            Result.success()
          }

          is MessageService.SendError.NotRegistered,
          is MessageService.SendError.Unauthorized,
          is MessageService.SendError.ChallengeRequired,
          is MessageService.SendError.ContentTooLarge -> {
            Log.w(TAG, "Failed to send null message.", error)
            Result.failure()
          }

          is MessageService.SendError.SessionAttemptsExhausted,
          is MessageService.SendError.PreKeyUnavailable -> {
            Log.w(TAG, "Unable to establish a session, retrying.", error)
            Result.retry(defaultBackoff())
          }

          is MessageService.SendError.RateLimited -> {
            val backoff = maxOf(defaultBackoff(), error.retryAfter?.inWholeMilliseconds ?: 0L)
            Log.w(TAG, "Rate limited, retryAfter=${error.retryAfter}, using backoff=${backoff}ms", error)
            Result.retry(backoff)
          }

          is MessageService.SendError.NetworkError -> {
            Log.w(TAG, "Network error, retrying.", error.exception)
            Result.retry(defaultBackoff())
          }

          is MessageService.SendError.ApplicationError -> when (val cause = error.exception) {
            is NoSessionException,
            is InvalidSessionException -> {
              Log.w(TAG, "Bad session, retrying.", cause)
              Result.retry(defaultBackoff())
            }

            is RuntimeException -> {
              Log.e(TAG, "Unexpected error sending null message.", cause)
              Result.fatalFailure(cause)
            }

            else -> {
              Log.w(TAG, "Application error.", cause)
              Result.failure()
            }
          }
        }
      }
    )
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<NullMessageSendJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): NullMessageSendJob {
      val data = JsonJobData.deserialize(serializedData)
      return NullMessageSendJob(RecipientId.from(data.getString(KEY_RECIPIENT_ID)), parameters)
    }
  }
}

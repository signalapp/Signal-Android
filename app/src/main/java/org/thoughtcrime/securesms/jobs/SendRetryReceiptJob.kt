package org.thoughtcrime.securesms.jobs

import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.InvalidSessionException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.message.DecryptionErrorMessage
import org.signal.libsignal.protocol.message.PlaintextContent
import org.signal.network.service.MessageService
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
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
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import java.util.Optional
import java.util.concurrent.TimeUnit

class SendRetryReceiptJob private constructor(
  private val recipientId: RecipientId,
  private val groupId: Optional<GroupId>,
  private val errorMessage: DecryptionErrorMessage,
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    const val KEY: String = "SendRetryReceiptJob"

    private val TAG = Log.tag(SendRetryReceiptJob::class.java)

    private const val KEY_RECIPIENT_ID = "recipient_id"
    private const val KEY_ERROR_MESSAGE = "error_message"
    private const val KEY_GROUP_ID = "group_id"
  }

  constructor(recipientId: RecipientId, groupId: Optional<GroupId>, errorMessage: DecryptionErrorMessage) : this(
    recipientId = recipientId,
    groupId = groupId,
    errorMessage = errorMessage,
    parameters = Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setQueue(recipientId.toQueueKey())
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .build()
  )

  override fun serialize(): ByteArray? {
    val builder = JsonJobData.Builder()
      .putString(KEY_RECIPIENT_ID, recipientId.serialize())
      .putBlobAsString(KEY_ERROR_MESSAGE, errorMessage.serialize())

    if (groupId.isPresent) {
      builder.putBlobAsString(KEY_GROUP_ID, groupId.get().decodedId)
    }

    return builder.serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (recipientId.isUnknown) {
      Log.w(TAG, "Recipient is unknown! Skipping retry receipt.", true)
      return Result.success()
    }

    val recipient = SignalDatabase.recipients.getRecord(recipientId)

    if (recipient.registered == RegisteredState.NOT_REGISTERED) {
      Log.w(TAG, "${recipient.id} not registered!")
      return Result.success()
    }

    val serviceId = recipient.serviceId
    if (serviceId == null) {
      Log.w(TAG, "${recipient.id} has no serviceId! Skipping retry receipt.")
      return Result.success()
    }

    if (serviceId == SignalStore.account.aci) {
      Log.w(TAG, "Retry receipts are not sent to our own account. Falling back to a session reset.")
      return Result.failure()
    }

    val timestamp = System.currentTimeMillis()
    val envelopeContent = EnvelopeContent.plaintext(PlaintextContent(errorMessage), groupId.map { it.decodedId })

    Log.i(TAG, "Sending retry receipt for ${errorMessage.timestamp} to $recipientId, device: ${errorMessage.deviceId}")

    val result = SignalNetwork.messageService.sendMessage(
      serviceId = serviceId,
      envelopeContent = envelopeContent,
      timestamp = timestamp,
      sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient),
      story = false,
      isOnline = false,
      urgent = false
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error ->
        when (error) {
          is MessageService.SendError.IdentityMismatch,
          is MessageService.SendError.NotRegistered,
          is MessageService.SendError.Unauthorized,
          is MessageService.SendError.ChallengeRequired,
          is MessageService.SendError.ContentTooLarge,
          is MessageService.SendError.PreKeyUnavailable -> {
            Log.w(TAG, "Failed to send retry receipt.", error)
            Result.failure()
          }

          is MessageService.SendError.SessionAttemptsExhausted -> {
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
              Log.e(TAG, "Unexpected error sending retry receipt.", cause)
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

  override fun onFailure() {
    if (recipientId.isUnknown) {
      Log.w(TAG, "Recipient is unknown! Skipping session reset.", true)
      return
    }

    AppDependencies.jobManager.add(AutomaticSessionResetJob(recipientId, errorMessage.deviceId, System.currentTimeMillis()))
  }

  class Factory : Job.Factory<SendRetryReceiptJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SendRetryReceiptJob {
      val data = JsonJobData.deserialize(serializedData)

      try {
        val recipientId = RecipientId.from(data.getString(KEY_RECIPIENT_ID))
        val errorMessage = DecryptionErrorMessage(data.getStringAsBlob(KEY_ERROR_MESSAGE))
        val groupId = if (data.hasString(KEY_GROUP_ID)) {
          Optional.of<GroupId>(GroupId.pushOrThrow(data.getStringAsBlob(KEY_GROUP_ID)))
        } else {
          Optional.empty()
        }

        return SendRetryReceiptJob(recipientId, groupId, errorMessage, parameters)
      } catch (e: InvalidKeyException) {
        throw AssertionError(e)
      } catch (e: InvalidMessageException) {
        throw AssertionError(e)
      }
    }
  }
}

package org.thoughtcrime.securesms.jobs

import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.network.service.IndividualMessageService
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
import org.thoughtcrime.securesms.util.OutgoingContent
import java.util.concurrent.TimeUnit

/**
 * Just sends an empty message to a target recipient. Only suitable for individuals, NOT groups.
 *
 * With a [resetDeviceId], the session reset cooldown is recorded when the job finishes, including on failure.
 */
class NullMessageSendJob private constructor(
  private val recipientId: RecipientId,
  private val resetDeviceId: Int?,
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    const val KEY: String = "NullMessageSendJob"

    private val TAG = Log.tag(NullMessageSendJob::class.java)

    private const val KEY_RECIPIENT_ID = "recipient_id"
    private const val KEY_RESET_DEVICE_ID = "reset_device_id"

    fun forRetryReceipt(recipientId: RecipientId): NullMessageSendJob {
      return create(recipientId = recipientId, resetDeviceId = null)
    }

    fun forSessionReset(recipientId: RecipientId, deviceId: Int): NullMessageSendJob {
      return create(recipientId = recipientId, resetDeviceId = deviceId)
    }

    private fun create(recipientId: RecipientId, resetDeviceId: Int?): NullMessageSendJob {
      return NullMessageSendJob(
        recipientId,
        resetDeviceId,
        Parameters.Builder()
          .setQueue(if (resetDeviceId != null) "${recipientId.toQueueKey()}::SESSION_RESET::$resetDeviceId" else recipientId.toQueueKey())
          .addConstraint(NetworkConstraint.KEY)
          .addConstraint(SealedSenderConstraint.KEY)
          .setLifespan(TimeUnit.DAYS.toMillis(1))
          .setMaxAttempts(Parameters.UNLIMITED)
          .setMaxInstancesForQueue(if (resetDeviceId != null) 1 else Parameters.UNLIMITED)
          .build()
      )
    }
  }

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_RECIPIENT_ID, recipientId.serialize())
      .apply { resetDeviceId?.let { putInt(KEY_RESET_DEVICE_ID, it) } }
      .serialize()
  }

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
      return Result.failure()
    }

    val serviceId = recipient.serviceId
    if (serviceId == null) {
      Log.w(TAG, "${recipient.id} has no serviceId!")
      return Result.failure()
    }

    val result = if (serviceId == SignalStore.account.aci) {
      SignalNetwork.messageService.sendSyncMessage(
        timestamp = System.currentTimeMillis(),
        envelopeContent = OutgoingContent.nullMessage(),
        urgent = false,
        onEncrypted = null
      )
    } else {
      SignalNetwork.messageService.sendMessage(
        serviceId = serviceId,
        envelopeContent = OutgoingContent.nullMessage(),
        timestamp = System.currentTimeMillis(),
        sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient),
        story = false,
        isOnline = false,
        urgent = false
      )
    }

    return result.fold(
      ifRight = {
        markSessionReset()
        Result.success()
      },
      ifLeft = { error ->
        when (error) {
          is IndividualMessageService.SendError.IdentityMismatch -> {
            Log.w(TAG, "Unable to send null message due to an identity mismatch.")
            markSessionReset()
            Result.success()
          }

          is IndividualMessageService.SendError.NotRegistered,
          is IndividualMessageService.SendError.Unauthorized,
          is IndividualMessageService.SendError.ChallengeRequired,
          is IndividualMessageService.SendError.ContentTooLarge -> {
            Log.w(TAG, "Failed to send null message.", error)
            Result.failure()
          }

          is IndividualMessageService.SendError.PreKeyUnavailable -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.SessionAttemptsExhausted -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.RateLimited -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.NetworkError -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.ApplicationError -> error.toJobResult(TAG, "null message", defaultBackoff())
        }
      }
    )
  }

  override fun onFailure() {
    markSessionReset()
  }

  private fun markSessionReset() {
    if (resetDeviceId != null) {
      val now = System.currentTimeMillis()
      SignalDatabase.recipients.setLastSessionResetTime(recipientId, resetDeviceId, now)
      Log.i(TAG, "Marked last reset time for device $resetDeviceId: $now", true)
    }
  }

  class Factory : Job.Factory<NullMessageSendJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): NullMessageSendJob {
      val data = JsonJobData.deserialize(serializedData)
      return NullMessageSendJob(
        recipientId = RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
        resetDeviceId = if (data.hasInt(KEY_RESET_DEVICE_ID)) data.getInt(KEY_RESET_DEVICE_ID) else null,
        parameters = parameters
      )
    }
  }
}

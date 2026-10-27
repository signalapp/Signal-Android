package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.network.service.IndividualMessageService
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.util.OutgoingContent
import org.thoughtcrime.securesms.util.toSendMessageResult
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.internal.push.ReceiptMessage
import java.util.concurrent.TimeUnit

class SendReadReceiptJob private constructor(
  parameters: Parameters,
  private val threadId: Long,
  private val recipientId: RecipientId,
  private val messageSentTimestamps: List<Long>,
  private val messageIds: List<MessageId>,
  private val timestamp: Long
) : CoroutineJob(parameters) {

  companion object {
    const val KEY: String = "SendReadReceiptJob"

    private val TAG = Log.tag(SendReadReceiptJob::class.java)

    const val MAX_TIMESTAMPS: Int = 500

    private const val KEY_THREAD = "thread"
    private const val KEY_RECIPIENT = "recipient"
    private const val KEY_MESSAGE_SENT_TIMESTAMPS = "message_ids"
    private const val KEY_MESSAGE_IDS = "message_db_ids"
    private const val KEY_TIMESTAMP = "timestamp"

    /**
     * Enqueues all the necessary jobs for read receipts, ensuring that they're all within the
     * maximum size.
     */
    @JvmStatic
    fun enqueue(threadId: Long, recipientId: RecipientId, markedMessageInfos: List<MarkedMessageInfo>) {
      if (!SignalStore.settings.isReadReceiptsEnabled) {
        return
      }

      if (recipientId == Recipient.self().id) {
        return
      }

      val jobManager = AppDependencies.jobManager
      val messageIdChunks = markedMessageInfos.chunked(MAX_TIMESTAMPS)

      if (messageIdChunks.size > 1) {
        Log.w(TAG, "Large receipt count! Had to break into multiple chunks. Total count: ${markedMessageInfos.size}")
      }

      for (chunk in messageIdChunks) {
        val sentTimestamps = chunk.map { it.syncMessageId.timetamp }
        val messageIds = chunk.map { it.messageId }

        jobManager.add(SendReadReceiptJob(threadId, recipientId, sentTimestamps, messageIds))
      }
    }

    @JvmStatic
    fun <E> ensureSize(list: List<E>, maxSize: Int): List<E> {
      if (list.size > maxSize) {
        throw IllegalArgumentException("Too large! Size: ${list.size}, maxSize: $maxSize")
      }
      return list
    }
  }

  @VisibleForTesting
  constructor(threadId: Long, recipientId: RecipientId, messageSentTimestamps: List<Long>, messageIds: List<MessageId>) : this(
    parameters = Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .setQueue(recipientId.toReceiptQueueKey())
      .build(),
    threadId = threadId,
    recipientId = recipientId,
    messageSentTimestamps = ensureSize(messageSentTimestamps, MAX_TIMESTAMPS),
    messageIds = ensureSize(messageIds, MAX_TIMESTAMPS),
    timestamp = System.currentTimeMillis()
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_RECIPIENT, recipientId.serialize())
      .putLongArray(KEY_MESSAGE_SENT_TIMESTAMPS, messageSentTimestamps.toLongArray())
      .putStringListAsArray(KEY_MESSAGE_IDS, messageIds.map { it.serialize() })
      .putLong(KEY_TIMESTAMP, timestamp)
      .putLong(KEY_THREAD, threadId)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      return Result.failure()
    }

    if (!SignalStore.settings.isReadReceiptsEnabled || messageSentTimestamps.isEmpty()) {
      return Result.success()
    }

    if (!RecipientUtil.isMessageRequestAccepted(threadId)) {
      Log.w(TAG, "Refusing to send receipts to untrusted recipient")
      return Result.success()
    }

    val recipient = SignalDatabase.recipients.getRecord(recipientId)

    if (recipient.id == Recipient.self().id) {
      Log.i(TAG, "Not sending to self, aborting.")
      return Result.success()
    }

    if (recipient.isBlocked) {
      Log.w(TAG, "Refusing to send receipts to blocked recipient")
      return Result.success()
    }

    if (recipient.groupId != null) {
      Log.w(TAG, "Refusing to send receipts to group")
      return Result.success()
    }

    if (recipient.distributionListId != null) {
      Log.w(TAG, "Refusing to send receipts to distribution list")
      return Result.success()
    }

    if (recipient.registered == RegisteredState.NOT_REGISTERED) {
      Log.w(TAG, "${recipient.id} not registered!")
      return Result.success()
    }

    val serviceId = recipient.serviceId
    if (serviceId == null) {
      Log.w(TAG, "${recipient.id} has no serviceId!")
      return Result.failure()
    }

    val result = SignalNetwork.messageService.sendMessage(
      serviceId = serviceId,
      envelopeContent = OutgoingContent.receipt(ReceiptMessage.Type.READ, messageSentTimestamps, recipient.needsPniSignature),
      timestamp = timestamp,
      sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient) { SignalDatabase.groups.getGroupSendFullToken(threadId, recipientId) },
      story = false,
      isOnline = false,
      urgent = false
    )

    return result.fold(
      ifRight = { success ->
        if (messageIds.isNotEmpty()) {
          SignalDatabase.messageLog.insertIfPossible(recipientId, timestamp, success.toSendMessageResult(serviceId), ContentHint.IMPLICIT, messageIds, false)
        }
        Result.success()
      },
      ifLeft = { error ->
        when (error) {
          is IndividualMessageService.SendError.IdentityMismatch,
          is IndividualMessageService.SendError.NotRegistered,
          is IndividualMessageService.SendError.Unauthorized,
          is IndividualMessageService.SendError.ContentTooLarge -> {
            Log.w(TAG, "Failed to send read receipt.", error)
            Result.failure()
          }

          is IndividualMessageService.SendError.ChallengeRequired -> error.tryPushChallengeAndToJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.PreKeyUnavailable -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.SessionAttemptsExhausted -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.RateLimited -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.NetworkError -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.ApplicationError -> error.toJobResult(TAG, "read receipt", defaultBackoff())
        }
      }
    )
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to send read receipts to: $recipientId")
  }

  class Factory : Job.Factory<SendReadReceiptJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SendReadReceiptJob {
      val data = JsonJobData.deserialize(serializedData)

      val timestamp = data.getLong(KEY_TIMESTAMP)
      val sentTimestamps = if (data.hasLongArray(KEY_MESSAGE_SENT_TIMESTAMPS)) data.getLongArray(KEY_MESSAGE_SENT_TIMESTAMPS).toList() else emptyList()
      val rawMessageIds = if (data.hasStringArray(KEY_MESSAGE_IDS)) data.getStringArrayAsList(KEY_MESSAGE_IDS) else emptyList()
      val messageIds = rawMessageIds.map { MessageId.deserialize(it) }
      val threadId = data.getLong(KEY_THREAD)
      val recipientId = RecipientId.from(data.getString(KEY_RECIPIENT))

      return SendReadReceiptJob(parameters, threadId, recipientId, sentTimestamps, messageIds, timestamp)
    }
  }
}

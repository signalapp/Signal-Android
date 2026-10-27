package org.thoughtcrime.securesms.jobs

import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.network.service.IndividualMessageService
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.StoryType
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

class SendViewedReceiptJob private constructor(
  parameters: Parameters,
  private val threadId: Long,
  private val recipientId: RecipientId,
  private val messageSentTimestamps: List<Long>,
  private val messageIds: List<MessageId>,
  private val timestamp: Long
) : CoroutineJob(parameters) {

  companion object {
    const val KEY: String = "SendViewedReceiptJob"

    private val TAG = Log.tag(SendViewedReceiptJob::class.java)

    private const val KEY_THREAD = "thread"
    private const val KEY_RECIPIENT = "recipient"
    private const val KEY_MESSAGE_SENT_TIMESTAMPS = "message_ids"
    private const val KEY_MESSAGE_IDS = "message_db_ids"
    private const val KEY_TIMESTAMP = "timestamp"

    /**
     * Enqueues all the necessary jobs for viewed receipts, ensuring that they're all within the
     * maximum size.
     */
    @JvmStatic
    fun enqueue(threadId: Long, recipientId: RecipientId, markedMessageInfos: List<MarkedMessageInfo>) {
      val jobManager = AppDependencies.jobManager
      val messageIdChunks = markedMessageInfos.chunked(SendReadReceiptJob.MAX_TIMESTAMPS)

      if (messageIdChunks.size > 1) {
        Log.w(TAG, "Large receipt count! Had to break into multiple chunks. Total count: ${markedMessageInfos.size}")
      }

      for (chunk in messageIdChunks) {
        val sentTimestamps = chunk.map { it.syncMessageId.timetamp }
        val messageIds = chunk.map { it.messageId }

        jobManager.add(SendViewedReceiptJob(threadId, recipientId, sentTimestamps, messageIds))
      }
    }
  }

  constructor(threadId: Long, recipientId: RecipientId, syncTimestamp: Long, messageId: MessageId) : this(threadId, recipientId, listOf(syncTimestamp), listOf(messageId))

  private constructor(threadId: Long, recipientId: RecipientId, messageSentTimestamps: List<Long>, messageIds: List<MessageId>) : this(
    parameters = Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .setQueue(recipientId.toReceiptQueueKey())
      .build(),
    threadId = threadId,
    recipientId = recipientId,
    messageSentTimestamps = SendReadReceiptJob.ensureSize(messageSentTimestamps, SendReadReceiptJob.MAX_TIMESTAMPS),
    messageIds = SendReadReceiptJob.ensureSize(messageIds, SendReadReceiptJob.MAX_TIMESTAMPS),
    timestamp = System.currentTimeMillis()
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_RECIPIENT, recipientId.serialize())
      .putLongListAsArray(KEY_MESSAGE_SENT_TIMESTAMPS, messageSentTimestamps)
      .putStringListAsArray(KEY_MESSAGE_IDS, messageIds.map { it.serialize() })
      .putLong(KEY_TIMESTAMP, timestamp)
      .putLong(KEY_THREAD, threadId)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    val canSendNonStoryReceipts = SignalStore.settings.isReadReceiptsEnabled
    val canSendStoryReceipts = SignalStore.story.viewedReceiptsEnabled

    val foundMessageIds = mutableListOf<MessageId>()
    val messageSentTimestamps = mutableListOf<Long>()
    val storyTypes = SignalDatabase.messages.getStoryTypes(this.messageIds)

    for (i in storyTypes.indices) {
      val storyType = storyTypes[i]
      if ((storyType == StoryType.NONE && canSendNonStoryReceipts) || (storyType.isStory && canSendStoryReceipts)) {
        foundMessageIds.add(this.messageIds[i])
        messageSentTimestamps.add(this.messageSentTimestamps[i])
      }
    }

    if (!Recipient.self().isRegistered) {
      return Result.failure()
    }

    if (storyTypes.isEmpty() && !SignalStore.settings.isReadReceiptsEnabled) {
      Log.w(TAG, "Read receipts not enabled!")
      return Result.success()
    }

    if (foundMessageIds.isEmpty()) {
      Log.w(TAG, "No messages in this batch are allowed to be sent!")
      return Result.success()
    }

    if (messageSentTimestamps.isEmpty()) {
      Log.w(TAG, "No sync timestamps!")
      return Result.success()
    }

    if (!RecipientUtil.isMessageRequestAccepted(threadId)) {
      Log.w(TAG, "Refusing to send receipts to untrusted recipient")
      return Result.success()
    }

    val recipient = SignalDatabase.recipients.getRecord(recipientId)

    if (recipient.id == Recipient.self().id) {
      Log.i(TAG, "Not sending view receipt to self.")
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

    if (recipient.registered == RegisteredState.NOT_REGISTERED) {
      Log.w(TAG, "${recipient.id} not registered!")
      return Result.success()
    }

    if (recipient.id == SignalStore.releaseChannel.releaseChannelRecipientId) {
      Log.w(TAG, "Refusing to send receipts to release channel")
      return Result.success()
    }

    val serviceId = recipient.serviceId
    if (serviceId == null) {
      Log.w(TAG, "${recipient.id} has no serviceId!")
      return Result.failure()
    }

    val result = SignalNetwork.messageService.sendMessage(
      serviceId = serviceId,
      envelopeContent = OutgoingContent.receipt(ReceiptMessage.Type.VIEWED, messageSentTimestamps, recipient.needsPniSignature),
      timestamp = timestamp,
      sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient) { SignalDatabase.groups.getGroupSendFullToken(threadId, recipientId) },
      story = false,
      isOnline = false,
      urgent = false
    )

    return result.fold(
      ifRight = { success ->
        if (foundMessageIds.isNotEmpty()) {
          SignalDatabase.messageLog.insertIfPossible(recipientId, timestamp, success.toSendMessageResult(serviceId), ContentHint.IMPLICIT, foundMessageIds, false)
        }
        Result.success()
      },
      ifLeft = { error ->
        when (error) {
          is IndividualMessageService.SendError.IdentityMismatch,
          is IndividualMessageService.SendError.NotRegistered,
          is IndividualMessageService.SendError.Unauthorized,
          is IndividualMessageService.SendError.ContentTooLarge -> {
            Log.w(TAG, "Failed to send viewed receipt.", error)
            Result.failure()
          }

          is IndividualMessageService.SendError.ChallengeRequired -> error.tryPushChallengeAndToJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.PreKeyUnavailable -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.SessionAttemptsExhausted -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.RateLimited -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.NetworkError -> error.toJobResult(TAG, defaultBackoff())
          is IndividualMessageService.SendError.ApplicationError -> error.toJobResult(TAG, "viewed receipt", defaultBackoff())
        }
      }
    )
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to send viewed receipts to: $recipientId")
  }

  class Factory : Job.Factory<SendViewedReceiptJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SendViewedReceiptJob {
      val data = JsonJobData.deserialize(serializedData)

      val timestamp = data.getLong(KEY_TIMESTAMP)
      val syncTimestamps = data.getLongArrayAsList(KEY_MESSAGE_SENT_TIMESTAMPS)
      val rawMessageIds = if (data.hasStringArray(KEY_MESSAGE_IDS)) data.getStringArrayAsList(KEY_MESSAGE_IDS) else emptyList()
      val messageIds = rawMessageIds.map { MessageId.deserialize(it) }
      val threadId = data.getLong(KEY_THREAD)
      val recipientId = RecipientId.from(data.getString(KEY_RECIPIENT))

      return SendViewedReceiptJob(parameters, threadId, recipientId, syncTimestamps, messageIds, timestamp)
    }
  }
}

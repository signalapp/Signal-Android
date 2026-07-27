package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.signal.libsignal.zkgroup.groupsend.GroupSendFullToken
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.ReceiptSender.ReceiptSendOperation
import org.thoughtcrime.securesms.jobs.ReceiptSender.sendWithSessionRepair
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.transport.UndeliverableMessageException
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import java.io.IOException
import java.util.concurrent.TimeUnit

class SendDeliveryReceiptJob private constructor(
  parameters: Parameters,
  private val recipientId: RecipientId,
  private val messageSentTimestamps: List<Long>,
  private val messageIds: List<MessageId>,
  private val receiptSentTimestamp: Long
) : Job(parameters) {

  companion object {
    const val KEY: String = "SendDeliveryReceiptJob"

    private const val KEY_RECIPIENT = "recipient"
    private const val KEY_RECEIPT_TIMESTAMP = "timestamp"
    private const val KEY_MESSAGE_SENT_TIMESTAMPS = "message_sent_timestamps"
    private const val KEY_MESSAGE_IDS = "message_db_ids"

    private const val KEY_LEGACY_MESSAGE_SENT_TIMESTAMP = "message_id"
    private const val KEY_LEGACY_MESSAGE_ID = "message_db_id"

    private val TAG = tag(SendDeliveryReceiptJob::class.java)

    fun create(
      recipientId: RecipientId,
      messageSentTimestamps: List<Long>,
      messageIds: List<MessageId>
    ): List<SendDeliveryReceiptJob> {
      return messageSentTimestamps.zip(messageIds)
        .chunked(SendReadReceiptJob.MAX_TIMESTAMPS)
        .map { chunk -> SendDeliveryReceiptJob(recipientId, chunk.map { it.first }, chunk.map { it.second }) }
    }
  }

  private constructor(recipientId: RecipientId, messageSentTimestamps: List<Long>, messageIds: List<MessageId>) : this(
    parameters = Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .addConstraint(DecryptionsDrainedConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .setQueue(recipientId.toReceiptQueueKey())
      .build(),
    recipientId = recipientId,
    messageSentTimestamps = messageSentTimestamps,
    messageIds = messageIds,
    receiptSentTimestamp = System.currentTimeMillis()
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder().putString(KEY_RECIPIENT, recipientId.serialize())
      .putLongArray(KEY_MESSAGE_SENT_TIMESTAMPS, messageSentTimestamps.toLongArray())
      .putStringListAsArray(KEY_MESSAGE_IDS, messageIds.map { it.serialize() })
      .putLong(KEY_RECEIPT_TIMESTAMP, receiptSentTimestamp)
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun run(): Result {
    if (!Recipient.self().isRegistered) {
      return Result.failure()
    }

    if (messageSentTimestamps.isEmpty()) {
      return Result.success()
    }

    val messageSender = AppDependencies.signalServiceMessageSender
    val recipient = SignalDatabase.recipients.getRecord(recipientId)

    if (recipient.id == Recipient.self().id) {
      Log.i(TAG, "Not sending to self, abort")
      return Result.success()
    }

    if (recipient.registered == RecipientTable.RegisteredState.NOT_REGISTERED) {
      Log.w(TAG, "${recipient.id} is unregistered!")
      return Result.failure()
    }

    if (recipient.serviceId == null && recipient.e164 == null) {
      Log.w(TAG, "No serviceId or e164!")
      return Result.failure()
    }

    val result = try {
      val remoteAddress = RecipientUtil.toSignalServiceAddress(recipient)
      val receiptMessage = SignalServiceReceiptMessage(
        SignalServiceReceiptMessage.Type.DELIVERY,
        messageSentTimestamps,
        receiptSentTimestamp
      )

      sendWithSessionRepair(
        recipientId,
        ReceiptSendOperation {
          messageSender.sendReceipt(
            remoteAddress,
            SealedSenderAccessUtil.getSealedSenderAccessFor(recipient) { this.getGroupSendFullToken() },
            receiptMessage,
            recipient.needsPniSignature
          )
        }
      )
    } catch (e: ServerRejectedException) {
      Log.i(TAG, "Send failed", e)
      return Result.failure()
    } catch (e: IOException) {
      Log.d(TAG, "Send failed, retrying", e)
      return Result.retry(defaultBackoff())
    } catch (e: UntrustedIdentityException) {
      Log.i(TAG, "Send failed", e)
      return Result.failure()
    } catch (e: UndeliverableMessageException) {
      Log.i(TAG, "Send failed", e)
      return Result.failure()
    }

    if (result != null && messageIds.isNotEmpty()) {
      SignalDatabase.messageLog.insertIfPossible(recipientId, receiptSentTimestamp, result, ContentHint.IMPLICIT, messageIds, false)
    }

    return Result.success()
  }

  private fun getGroupSendFullToken(): GroupSendFullToken? {
    for (messageId in messageIds) {
      val threadId = SignalDatabase.messages.getThreadIdForMessage(messageId.id)
      if (threadId != -1L) {
        return SignalDatabase.groups.getGroupSendFullToken(threadId, recipientId)
      }
    }

    return null
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to send delivery receipt to: $recipientId")
  }

  class Factory : Job.Factory<SendDeliveryReceiptJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SendDeliveryReceiptJob {
      val data = JsonJobData.deserialize(serializedData)

      val recipientId = RecipientId.from(data.getString(KEY_RECIPIENT))
      val timestamp = data.getLong(KEY_RECEIPT_TIMESTAMP)
      val sentTimestamps: List<Long>
      val messageIds: List<MessageId>

      if (data.hasLongArray(KEY_MESSAGE_SENT_TIMESTAMPS)) {
        sentTimestamps = data.getLongArray(KEY_MESSAGE_SENT_TIMESTAMPS).toList()
        messageIds = if (data.hasStringArray(KEY_MESSAGE_IDS)) data.getStringArrayAsList(KEY_MESSAGE_IDS).map { MessageId.deserialize(it) } else emptyList()
      } else {
        sentTimestamps = listOf(data.getLong(KEY_LEGACY_MESSAGE_SENT_TIMESTAMP))
        messageIds = if (data.hasString(KEY_LEGACY_MESSAGE_ID)) listOf(MessageId.deserialize(data.getString(KEY_LEGACY_MESSAGE_ID))) else emptyList()
      }

      return SendDeliveryReceiptJob(parameters, recipientId, sentTimestamps, messageIds, timestamp)
    }
  }
}

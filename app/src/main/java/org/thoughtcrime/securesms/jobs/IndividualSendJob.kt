package org.thoughtcrime.securesms.jobs

import android.content.Context
import androidx.annotation.WorkerThread
import okio.utf8Size
import org.signal.core.util.UuidUtil.parseOrThrow
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.RecipientTable.SealedSenderAccessMode
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.ConversationShortcutRankingUpdateJob.Companion.enqueueForOutgoingIfNecessary
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob.Companion.enqueue
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.ratelimit.ProofRequiredExceptionHandler
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.thoughtcrime.securesms.transport.UndeliverableMessageException
import org.thoughtcrime.securesms.util.MessageUtil
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.whispersystems.signalservice.api.SignalServiceMessageSender.IndividualSendEvents
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.PaymentActivation
import org.whispersystems.signalservice.api.messages.SignalServiceEditMessage
import org.whispersystems.signalservice.api.messages.SignalServicePreview
import org.whispersystems.signalservice.api.messages.shared.SharedContact
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException
import org.whispersystems.signalservice.internal.push.BodyRange
import org.whispersystems.signalservice.internal.push.DataMessage
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeUnit

class IndividualSendJob private constructor(parameters: Parameters, private val messageId: Long) : PushSendJob(parameters) {

  companion object {
    const val KEY: String = "PushMediaSendJob"

    private val TAG = Log.tag(IndividualSendJob::class.java)

    private const val KEY_MESSAGE_ID = "message_id"

    @JvmStatic
    fun create(messageId: Long, recipient: Recipient, hasMedia: Boolean, isScheduledSend: Boolean): Job {
      if (!recipient.hasServiceId) {
        throw AssertionError("No ServiceId!")
      }

      if (recipient.isGroup) {
        throw AssertionError("This job does not send group messages!")
      }

      return IndividualSendJob(messageId, recipient, hasMedia, isScheduledSend)
    }

    @JvmStatic
    @WorkerThread
    fun enqueue(context: Context, jobManager: JobManager, messageId: Long, recipient: Recipient, isScheduledSend: Boolean) {
      try {
        val message = SignalDatabase.messages.getOutgoingMessage(messageId)
        if (message.scheduledDate != -1L) {
          AppDependencies.scheduledMessageManager.scheduleIfNecessary()
          return
        }

        val attachmentUploadIds: Set<String> = enqueueCompressingAndUploadAttachmentsChains(jobManager, message)
        val hasMedia = attachmentUploadIds.isNotEmpty()
        val addHardDependencies = hasMedia && !isScheduledSend

        jobManager.add(
          create(messageId, recipient, hasMedia, isScheduledSend),
          attachmentUploadIds,
          if (addHardDependencies) recipient.id.toQueueKey() else null
        )
      } catch (e: NoSuchMessageException) {
        Log.w(TAG, "Failed to enqueue message.", e)
        SignalDatabase.messages.markAsSentFailed(messageId)
        notifyMediaMessageDeliveryFailed(context, messageId)
      } catch (e: MmsException) {
        Log.w(TAG, "Failed to enqueue message.", e)
        SignalDatabase.messages.markAsSentFailed(messageId)
        notifyMediaMessageDeliveryFailed(context, messageId)
      }
    }

    @JvmStatic
    fun getMessageId(serializedData: ByteArray?): Long {
      val data = JsonJobData.deserialize(serializedData)
      return data.getLong(KEY_MESSAGE_ID)
    }
  }

  constructor(messageId: Long, recipient: Recipient, hasMedia: Boolean, isScheduledSend: Boolean) : this(
    parameters = Parameters.Builder()
      .setQueue(if (isScheduledSend) recipient.id.toScheduledSendQueueKey() else recipient.id.toQueueKey(hasMedia))
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    messageId = messageId
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder().putLong(KEY_MESSAGE_ID, messageId).serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onAdded() {
    SignalDatabase.messages.markAsSending(messageId)
  }

  @Throws(IOException::class, MmsException::class, NoSuchMessageException::class, UndeliverableMessageException::class, RetryLaterException::class)
  public override fun onPushSend() {
    SignalLocalMetrics.IndividualMessageSend.onJobStarted(messageId)

    val expirationManager = AppDependencies.expiringMessageManager
    val message = SignalDatabase.messages.getOutgoingMessage(messageId)
    val threadId = SignalDatabase.messages.getMessageRecord(messageId).threadId
    val originalEditedMessage = if (message.messageToEdit > 0) SignalDatabase.messages.getMessageRecordOrNull(message.messageToEdit) else null

    if (SignalDatabase.messages.isSent(messageId)) {
      warn(TAG, message.sentTimeMillis.toString(), "Message $messageId was already sent. Ignoring.")
      return
    }

    try {
      log(TAG, message.sentTimeMillis.toString(), "Sending message: $messageId, Recipient: ${message.threadRecipient.id}, Thread: $threadId, Attachments: ${buildAttachmentString(message.attachments)}, Editing: ${originalEditedMessage?.dateSent ?: "N/A"}")

      RecipientUtil.shareProfileIfFirstSecureMessage(message.threadRecipient)

      val recipient = message.threadRecipient.fresh()
      val profileKey = recipient.profileKey
      val accessMode = recipient.sealedSenderAccessMode

      val unidentified = deliver(message, originalEditedMessage)

      SignalDatabase.messages.markAsSent(messageId, true)
      markAttachmentsUploaded(messageId, message)
      SignalDatabase.messages.markUnidentified(messageId, unidentified)

      // For scheduled messages, which may not have updated the thread with its snippet yet
      SignalDatabase.threads.updateSilently(threadId, false)

      if (recipient.isSelf) {
        SignalDatabase.messages.incrementDeliveryReceiptCount(message.sentTimeMillis, recipient.id, System.currentTimeMillis())
        SignalDatabase.messages.incrementReadReceiptCount(message.sentTimeMillis, recipient.id, System.currentTimeMillis())
        SignalDatabase.messages.incrementViewedReceiptCount(message.sentTimeMillis, recipient.id, System.currentTimeMillis())
      }

      if (unidentified && accessMode == SealedSenderAccessMode.UNKNOWN && profileKey == null) {
        log(TAG, message.sentTimeMillis.toString(), "Marking recipient as UD-unrestricted following a UD send.")
        SignalDatabase.recipients.setSealedSenderAccessMode(recipient.id, SealedSenderAccessMode.UNRESTRICTED)
      } else if (unidentified && accessMode == SealedSenderAccessMode.UNKNOWN) {
        log(TAG, message.sentTimeMillis.toString(), "Marking recipient as UD-enabled following a UD send.")
        SignalDatabase.recipients.setSealedSenderAccessMode(recipient.id, SealedSenderAccessMode.ENABLED)
      } else if (!unidentified && accessMode != SealedSenderAccessMode.DISABLED) {
        log(TAG, message.sentTimeMillis.toString(), "Marking recipient as UD-disabled following a non-UD send.")
        SignalDatabase.recipients.setSealedSenderAccessMode(recipient.id, SealedSenderAccessMode.DISABLED)
      }

      if (originalEditedMessage != null && originalEditedMessage.expireStarted > 0) {
        SignalDatabase.messages.markExpireStarted(messageId, originalEditedMessage.expireStarted)
        expirationManager.scheduleDeletion(messageId, true, originalEditedMessage.expireStarted, originalEditedMessage.expiresIn)
      } else if (message.expiresIn > 0 && !message.isExpirationUpdate) {
        SignalDatabase.messages.markExpireStarted(messageId)
        expirationManager.scheduleDeletion(messageId, true, message.expiresIn)
      }

      if (message.isViewOnce) {
        SignalDatabase.attachments.deleteAttachmentFilesForViewOnceMessage(messageId)
      }

      enqueueForOutgoingIfNecessary(recipient)

      log(TAG, message.sentTimeMillis.toString(), "Sent message: $messageId")
    } catch (uue: UnregisteredUserException) {
      warn(TAG, "Failure", uue)

      SignalDatabase.messages.markAsSentFailed(messageId)
      notifyMediaMessageDeliveryFailed(context, messageId)
      AppDependencies.jobManager.add(DirectoryRefreshJob(false))
    } catch (uie: UntrustedIdentityException) {
      warn(TAG, "Failure", uie)

      val recipient = Recipient.external(uie.identifier)
      if (recipient == null) {
        Log.w(TAG, "Failed to create a Recipient for the identifier!")
        return
      }

      SignalDatabase.messages.addMismatchedIdentity(messageId, recipient.id, uie.getIdentityKey())
      SignalDatabase.messages.markAsSentFailed(messageId)
      enqueue(recipient.id, true)
    } catch (e: ProofRequiredException) {
      val result = ProofRequiredExceptionHandler.handle(context, e, SignalDatabase.threads.getRecipientForThreadId(threadId), threadId, messageId)
      if (result.isRetry()) {
        throw RetryLaterException()
      } else {
        throw e
      }
    }

    SignalLocalMetrics.IndividualMessageSend.onJobFinished(messageId)
  }

  public override fun onRetry() {
    SignalLocalMetrics.IndividualMessageSend.cancel(messageId)
    super.onRetry()
  }

  override fun onFailure() {
    SignalLocalMetrics.IndividualMessageSend.cancel(messageId)
    SignalDatabase.messages.markAsSentFailed(messageId)
    notifyMediaMessageDeliveryFailed(context, messageId)
  }

  @Throws(IOException::class, UnregisteredUserException::class, UntrustedIdentityException::class, UndeliverableMessageException::class)
  private fun deliver(message: OutgoingMessage, originalEditedMessage: MessageRecord?): Boolean {
    if (message.body.utf8Size() > MessageUtil.MAX_INLINE_BODY_SIZE_BYTES) {
      throw UndeliverableMessageException("The total body size was greater than our limit of " + MessageUtil.MAX_INLINE_BODY_SIZE_BYTES + " bytes.")
    }

    try {
      var messageRecipient = message.threadRecipient.fresh()

      if (messageRecipient.isUnregistered) {
        throw UndeliverableMessageException(messageRecipient.id.toString() + " not registered!")
      }

      if (!messageRecipient.hasServiceId) {
        messageRecipient = messageRecipient.fresh()

        if (!messageRecipient.hasServiceId) {
          throw UndeliverableMessageException(messageRecipient.id.toString() + " has no serviceId!")
        }
      }

      val messageSender = AppDependencies.signalServiceMessageSender
      val address = RecipientUtil.toSignalServiceAddress(context, messageRecipient)
      val attachments = message.attachments.filter { !it.isSticker }
      val serviceAttachments: List<SignalServiceAttachment> = getAttachmentPointersFor(attachments)
      val profileKey: Optional<ByteArray> = getProfileKey(messageRecipient)
      val sticker: Optional<SignalServiceDataMessage.Sticker> = getStickerFor(message)
      val sharedContacts: List<SharedContact> = getSharedContactsFor(message)
      val previews: List<SignalServicePreview> = getPreviewsFor(message)
      val giftBadge = getGiftBadgeFor(message)
      val payment = getPayment(message)
      val bodyRanges: List<BodyRange>? = getBodyRanges(message)
      val pollCreate = getPollCreate(message)
      val pollTerminate = getPollTerminate(message)
      val pinnedMessage = getPinnedMessage(message)
      val mediaMessageBuilder = SignalServiceDataMessage.newBuilder()
        .withBody(message.body)
        .withAttachments(serviceAttachments)
        .withTimestamp(message.sentTimeMillis)
        .withExpiration((message.expiresIn / 1000).toInt())
        .withExpireTimerVersion(message.expireTimerVersion)
        .withViewOnce(message.isViewOnce)
        .withProfileKey(profileKey.orElse(null))
        .withSticker(sticker.orElse(null))
        .withSharedContacts(sharedContacts)
        .withPreviews(previews)
        .withGiftBadge(giftBadge)
        .asExpirationUpdate(message.isExpirationUpdate)
        .asEndSessionMessage(message.isEndSession)
        .withPayment(payment)
        .withBodyRanges(bodyRanges)
        .withPollCreate(pollCreate)
        .withPollTerminate(pollTerminate)
        .withPinnedMessage(pinnedMessage)

      if (message.parentStoryId != null) {
        try {
          val storyRecord = SignalDatabase.messages.getMessageRecord(message.parentStoryId.asMessageId().id)
          val storyRecipient = storyRecord.fromRecipient

          val storyContext = SignalServiceDataMessage.StoryContext(storyRecipient.requireServiceId(), storyRecord.dateSent)
          mediaMessageBuilder.withStoryContext(storyContext)

          val reaction: Optional<SignalServiceDataMessage.Reaction> = getStoryReactionFor(message, storyContext)
          if (reaction.isPresent) {
            mediaMessageBuilder.withReaction(reaction.get())
            mediaMessageBuilder.withBody(null)
          }
        } catch (e: NoSuchMessageException) {
          throw UndeliverableMessageException(e)
        }
      } else {
        mediaMessageBuilder.withQuote(getQuoteFor(message).orElse(null))
      }

      if (message.giftBadge != null || message.isPaymentsNotification) {
        mediaMessageBuilder.withBody(null)
      }

      val mediaMessage = mediaMessageBuilder.build()

      if (originalEditedMessage != null) {
        if (SignalStore.account.aci == address.serviceId) {
          val result = messageSender.sendSelfSyncEditMessage(SignalServiceEditMessage(originalEditedMessage.dateSent, mediaMessage))
          SignalDatabase.messageLog.insertIfPossible(messageRecipient.id, message.sentTimeMillis, result, ContentHint.RESENDABLE, MessageId(messageId), false)

          return SealedSenderAccessUtil.getSealedSenderCertificate() != null
        } else {
          val result = messageSender.sendEditMessage(
            address,
            SealedSenderAccessUtil.getSealedSenderAccessFor(messageRecipient),
            ContentHint.RESENDABLE,
            mediaMessage,
            IndividualSendEvents.EMPTY,
            message.isUrgent,
            originalEditedMessage.dateSent
          )
          SignalDatabase.messageLog.insertIfPossible(messageRecipient.id, message.sentTimeMillis, result, ContentHint.RESENDABLE, MessageId(messageId), false)

          return result.success.isUnidentified
        }
      } else if (SignalStore.account.aci == address.serviceId) {
        val result = messageSender.sendSyncMessage(mediaMessage)
        SignalDatabase.messageLog.insertIfPossible(messageRecipient.id, message.sentTimeMillis, result, ContentHint.RESENDABLE, MessageId(messageId), false)
        return SealedSenderAccessUtil.getSealedSenderCertificate() != null
      } else {
        SignalLocalMetrics.IndividualMessageSend.onDeliveryStarted(messageId, message.sentTimeMillis)
        val result = messageSender.sendDataMessage(
          address,
          SealedSenderAccessUtil.getSealedSenderAccessFor(messageRecipient),
          ContentHint.RESENDABLE,
          mediaMessage,
          MetricEventListener(messageId),
          message.isUrgent,
          messageRecipient.needsPniSignature
        )

        SignalDatabase.messageLog.insertIfPossible(messageRecipient.id, message.sentTimeMillis, result, ContentHint.RESENDABLE, MessageId(messageId), message.isUrgent)

        if (messageRecipient.needsPniSignature) {
          SignalDatabase.pendingPniSignatureMessages.insertIfNecessary(messageRecipient.id, message.sentTimeMillis, result)
        }

        return result.success.isUnidentified
      }
    } catch (e: FileNotFoundException) {
      warn(TAG, message.sentTimeMillis.toString(), e)
      throw UndeliverableMessageException(e)
    } catch (e: ServerRejectedException) {
      throw UndeliverableMessageException(e)
    }
  }

  private fun getPayment(message: OutgoingMessage): SignalServiceDataMessage.Payment? {
    if (message.isPaymentsNotification) {
      val paymentUuid = parseOrThrow(message.body)
      val payment = SignalDatabase.payments.getPayment(paymentUuid)

      if (payment == null) {
        Log.w(TAG, "Could not find payment, cannot send notification $paymentUuid")
        return null
      }

      if (payment.receipt == null) {
        Log.w(TAG, "Could not find payment receipt, cannot send notification $paymentUuid")
        return null
      }

      return SignalServiceDataMessage.Payment(SignalServiceDataMessage.PaymentNotification(payment.receipt!!, payment.note), null)
    } else {
      var type: DataMessage.Payment.Activation.Type? = null

      if (message.isRequestToActivatePayments) {
        type = DataMessage.Payment.Activation.Type.REQUEST
      } else if (message.isPaymentsActivated) {
        type = DataMessage.Payment.Activation.Type.ACTIVATED
      }

      return if (type != null) {
        SignalServiceDataMessage.Payment(null, PaymentActivation(type))
      } else {
        null
      }
    }
  }

  private class MetricEventListener(private val messageId: Long) : IndividualSendEvents {
    override fun onMessageEncrypted() {
      SignalLocalMetrics.IndividualMessageSend.onMessageEncrypted(messageId)
    }

    override fun onMessageSent() {
      SignalLocalMetrics.IndividualMessageSend.onMessageSent(messageId)
    }

    override fun onSyncMessageSent() {
      SignalLocalMetrics.IndividualMessageSend.onSyncMessageSent(messageId)
    }
  }

  class Factory : Job.Factory<IndividualSendJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): IndividualSendJob {
      val data = JsonJobData.deserialize(serializedData)
      return IndividualSendJob(parameters, data.getLong(KEY_MESSAGE_ID))
    }
  }
}

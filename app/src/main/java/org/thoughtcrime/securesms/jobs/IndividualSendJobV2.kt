/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.content.Context
import androidx.annotation.WorkerThread
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.Raise
import arrow.core.raise.either
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.utf8Size
import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.libsignal.net.ChallengeOption
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.network.service.MessageService
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.RecipientTable.SealedSenderAccessMode
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.jobs.protos.IndividualSendJobV2Data
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.ratelimit.ProofRequiredExceptionHandler
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.util.MessageUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.isUrgent
import org.thoughtcrime.securesms.util.toDataMessage
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.EnvelopeContent
import org.whispersystems.signalservice.api.messages.SendMessageResult
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.EditMessage
import org.whispersystems.signalservice.internal.push.ProofRequiredResponse
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

/**
 * Alternate implementation of [IndividualSendJob] that:
 * - Extends [Job] directly rather than going through [BaseJob]/[PushSendJob].
 * - Routes the actual send through the new [MessageService] (which encapsulates device resolution,
 *   prekey fetching, session building, encryption, and sync-transcript delivery).
 *
 * Used when [RemoteConfig.useIndividualSendJobV2] is true.
 *
 * Behavior should match [IndividualSendJob] exactly for observable state changes (marking sent,
 * UD-mode updates, expiration starts, view-once cleanup, etc.). The primary divergence is the
 * network layer.
 */
class IndividualSendJobV2 private constructor(parameters: Parameters, private val messageId: Long) : CoroutineJob(parameters) {

  companion object {
    const val KEY: String = "IndividualSendJobV2"

    private val TAG = Log.tag(IndividualSendJobV2::class.java)

    @JvmStatic
    fun create(messageId: Long, recipient: Recipient, hasMedia: Boolean, isScheduledSend: Boolean): Job {
      check(recipient.hasServiceId) { "No ServiceId!" }
      check(!recipient.isGroup) { "This job does not send group messages!" }
      return IndividualSendJobV2(messageId, recipient, hasMedia, isScheduledSend)
    }

    @JvmStatic
    @WorkerThread
    fun enqueue(context: Context, messageId: Long, recipient: Recipient, isScheduledSend: Boolean) {
      val message = SignalDatabase.messages.getOutgoingMessageOrNull(messageId)
      if (message == null) {
        Log.w(TAG, "${logPrefix(null, messageId)} Failed to enqueue message.")
        SignalDatabase.messages.markAsSentFailed(messageId)
        PushSendJob.notifyMediaMessageDeliveryFailed(context, messageId)
        return
      }

      if (message.scheduledDate != -1L) {
        AppDependencies.scheduledMessageManager.scheduleIfNecessary()
        return
      }

      val attachmentUploadIds: Set<String> = PushSendJob.enqueueCompressingAndUploadAttachmentsChains(AppDependencies.jobManager, message)
      val hasMedia = attachmentUploadIds.isNotEmpty()
      val addHardDependencies = hasMedia && !isScheduledSend

      AppDependencies.jobManager.add(
        create(messageId, recipient, hasMedia, isScheduledSend),
        attachmentUploadIds,
        if (addHardDependencies) recipient.id.toQueueKey() else null
      )
    }

    private fun logPrefix(sentTimestamp: Long? = null, messageId: Long): String = "[${sentTimestamp ?: "?"}][$messageId]"
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

  override fun serialize(): ByteArray = IndividualSendJobV2Data(messageId = messageId).encode()

  override fun getFactoryKey(): String = KEY

  override fun onAdded() {
    SignalDatabase.messages.markAsSending(messageId)
  }

  override suspend fun doRun(): Result {
    SignalLocalMetrics.IndividualMessageSend.onJobStarted(messageId)
    val result = doWork()
    SignalLocalMetrics.IndividualMessageSend.onJobFinished(messageId)
    return result
  }

  suspend fun doWork(): Result {
    syncPreKeysIfNecessary().getOrElse { return it }

    if (SignalStore.misc.isClientDeprecated) {
      Log.w(TAG, "${logPrefix()} Client is deprecated (build ${BuildConfig.BUILD_TIMESTAMP}); failing message.")
      return Result.failure()
    }

    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "${logPrefix()} Self is not registered; failing.")
      return Result.failure()
    }

    val message = SignalDatabase.messages.getOutgoingMessageOrNull(messageId)
    if (message == null) {
      Log.w(TAG, "${logPrefix()} No outgoing message found for id; failing.")
      return Result.failure()
    }

    val messageRecord = SignalDatabase.messages.getMessageRecordOrNull(messageId)
    if (messageRecord == null) {
      Log.w(TAG, "${logPrefix(message.sentTimeMillis)} No message record found for id; failing.")
      return Result.failure()
    }

    if (MessageTypes.isSentType(messageRecord.type)) {
      Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Message was already sent. Ignoring.")
      return Result.success()
    }

    val threadId = messageRecord.threadId
    val originalEditedMessage = if (message.messageToEdit > 0) {
      SignalDatabase.messages.getMessageRecordOrNull(message.messageToEdit)
    } else {
      null
    }

    if (message.body.utf8Size() > MessageUtil.MAX_INLINE_BODY_SIZE_BYTES) {
      Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Body size exceeds limit of ${MessageUtil.MAX_INLINE_BODY_SIZE_BYTES} bytes; failing.")
      return Result.failure()
    }

    val recipient = message.threadRecipient.fresh().validated(message.sentTimeMillis).getOrElse { return it }

    val dataMessage = message.toDataMessage().getOrElse { error ->
      Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Failed to create a data message! Reason: $error")
      return Result.failure()
    }

    RecipientUtil.shareProfileIfFirstSecureMessage(message.threadRecipient)

    Log.i(TAG, "${logPrefix(message.sentTimeMillis)} Sending message. Recipient: ${message.threadRecipient.id}, Thread: $threadId, Attachments: ${buildAttachmentString(message.attachments)}, Editing: ${originalEditedMessage?.dateSent ?: "N/A"}")
    SignalLocalMetrics.IndividualMessageSend.onDeliveryStarted(messageId, message.sentTimeMillis)

    return sendMessage(recipient, dataMessage, originalEditedMessage?.timestamp).fold(
      ifRight = { success ->
        val content = success.envelopeContent.content.get()

        val syntheticResult = SendMessageResult.success(
          SignalServiceAddress(recipient.requireServiceId(), recipient.e164.orNull()),
          success.devices,
          success.sentSealedSender,
          false,
          0L,
          Optional.of(content)
        )

        SignalDatabase.messageLog.insertIfPossible(
          recipientId = recipient.id,
          sentTimestamp = message.sentTimeMillis,
          sendMessageResult = syntheticResult,
          contentHint = ContentHint.RESENDABLE,
          messageId = MessageId(messageId),
          urgent = content.isUrgent()
        )

        if (recipient.needsPniSignature) {
          SignalDatabase.pendingPniSignatureMessages.insertIfNecessary(recipient.id, message.sentTimeMillis, syntheticResult)
        }

        SignalDatabase.messages.markAsSent(messageId, success.sentSealedSender)
        PushSendJob.markAttachmentsUploaded(messageId, message)

        SignalDatabase.threads.updateSilently(threadId, false)

        if (recipient.isSelf) {
          SignalDatabase.messages.incrementDeliveryReceiptCount(message.sentTimeMillis, recipient.id, System.currentTimeMillis())
          SignalDatabase.messages.incrementReadReceiptCount(message.sentTimeMillis, recipient.id, System.currentTimeMillis())
          SignalDatabase.messages.incrementViewedReceiptCount(message.sentTimeMillis, recipient.id, System.currentTimeMillis())
        }

        val accessMode = recipient.sealedSenderAccessMode
        if (success.sentSealedSender && accessMode == SealedSenderAccessMode.UNKNOWN && recipient.profileKey == null) {
          SignalDatabase.recipients.setSealedSenderAccessMode(recipient.id, SealedSenderAccessMode.UNRESTRICTED)
        } else if (success.sentSealedSender && accessMode == SealedSenderAccessMode.UNKNOWN) {
          SignalDatabase.recipients.setSealedSenderAccessMode(recipient.id, SealedSenderAccessMode.ENABLED)
        } else if (!success.sentSealedSender && accessMode != SealedSenderAccessMode.DISABLED) {
          SignalDatabase.recipients.setSealedSenderAccessMode(recipient.id, SealedSenderAccessMode.DISABLED)
        }

        if (originalEditedMessage != null && originalEditedMessage.expireStarted > 0) {
          SignalDatabase.messages.markExpireStarted(messageId, originalEditedMessage.expireStarted)
          AppDependencies.expiringMessageManager.scheduleDeletion(messageId, true, originalEditedMessage.expireStarted, originalEditedMessage.expiresIn)
        } else if (message.expiresIn > 0 && !message.isExpirationUpdate) {
          SignalDatabase.messages.markExpireStarted(messageId)
          AppDependencies.expiringMessageManager.scheduleDeletion(messageId, true, message.expiresIn)
        }

        if (message.isViewOnce) {
          SignalDatabase.attachments.deleteAttachmentFilesForViewOnceMessage(messageId)
        }

        ConversationShortcutRankingUpdateJob.enqueueForOutgoingIfNecessary(recipient)
        Log.i(TAG, "${logPrefix(message.sentTimeMillis)} Sent message.")
        Result.success()
      },
      ifLeft = { error ->
        when (error) {
          is MessageService.SendError.IdentityMismatch -> {
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Identity mismatch for ${error.serviceId}", error.exception)
            val externalRecipient = Recipient.external(error.serviceId.toString())
            if (externalRecipient == null) {
              Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Failed to create a Recipient for the identifier!")
            } else {
              SignalDatabase.messages.addMismatchedIdentity(messageId, externalRecipient.id, error.exception.untrustedIdentity)
              SignalDatabase.messages.markAsSentFailed(messageId)
              RetrieveProfileJob.enqueue(externalRecipient.id, true)
            }
            Result.success()
          }

          is MessageService.SendError.NotRegistered -> {
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Recipient not registered", error)
            SignalDatabase.messages.markAsSentFailed(messageId)
            PushSendJob.notifyMediaMessageDeliveryFailed(context, messageId)
            AppDependencies.jobManager.add(DirectoryRefreshJob(false))
            Result.success()
          }

          is MessageService.SendError.Unauthorized -> {
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Unauthorized send", error)
            Result.failure()
          }

          is MessageService.SendError.ChallengeRequired -> {
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Challenge required (options=${error.options})", error)
            val proofResponse = ProofRequiredResponse().apply {
              token = error.token
              options = error.options.map {
                when (it) {
                  ChallengeOption.PUSH_CHALLENGE -> "pushChallenge"
                  ChallengeOption.CAPTCHA -> "captcha"
                }
              }
            }
            val proofException = ProofRequiredException(proofResponse, error.retryAfter?.inWholeSeconds ?: 0L)
            val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
            when (ProofRequiredExceptionHandler.handle(context, proofException, threadRecipient, threadId, messageId)) {
              ProofRequiredExceptionHandler.Result.RETRY_NOW -> Result.retry(0L)
              ProofRequiredExceptionHandler.Result.RETRY_LATER,
              ProofRequiredExceptionHandler.Result.RETHROW -> Result.retry(nextRunAttemptBackoff(runAttempt + 1))
            }
          }

          is MessageService.SendError.ServerRejected -> {
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Server rejected the send", error)
            Result.failure()
          }

          is MessageService.SendError.ContentTooLarge -> {
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Content too large (${error.size} > ${error.maxAllowed} bytes). Failing.", error)
            Result.failure()
          }

          is MessageService.SendError.SessionAttemptsExhausted -> {
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Exhausted device-resolution attempts. Retrying", error)
            Result.retry(nextRunAttemptBackoff(runAttempt + 1))
          }

          is MessageService.SendError.PreKeyUnavailable -> {
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Prekey unavailable: ${error.reason}", error)
            Result.retry(nextRunAttemptBackoff(runAttempt + 1))
          }

          is MessageService.SendError.RateLimited -> {
            val defaultBackoff = nextRunAttemptBackoff(runAttempt + 1)
            val serverBackoff = error.retryAfter?.inWholeMilliseconds ?: 0L
            val backoff = maxOf(defaultBackoff, serverBackoff)
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Rate limited, retryAfter=${error.retryAfter}, using backoff=${backoff}ms", error)
            Result.retry(backoff)
          }

          is MessageService.SendError.NetworkError -> {
            Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Network error", error.exception)
            Result.retry(nextRunAttemptBackoff(runAttempt + 1))
          }

          is MessageService.SendError.ApplicationError -> when (val cause = error.exception) {
            is RuntimeException -> {
              Log.e(TAG, "${logPrefix(message.sentTimeMillis)} Encountered a fatal application error. Crash imminent.", cause)
              Result.fatalFailure(cause)
            }

            else -> {
              Log.w(TAG, "${logPrefix(message.sentTimeMillis)} Application error", cause)
              Result.retry(nextRunAttemptBackoff(runAttempt + 1))
            }
          }
        }
      }
    )
  }

  private suspend fun sendMessage(recipient: Recipient, dataMessage: DataMessage, editMessageTarget: Long?): Either<MessageService.SendError, MessageService.SendSuccess> = either {
    val primaryResult = sendPrimaryMessage(
      recipient = recipient,
      dataMessage = dataMessage,
      editMessageTarget = editMessageTarget
    ).also {
      SignalLocalMetrics.IndividualMessageSend.onMessageSent(messageId)
    }

    if (SignalStore.account.isMultiDevice) {
      sendSyncMessage(recipient, primaryResult).also {
        SignalLocalMetrics.IndividualMessageSend.onSyncMessageSent(messageId)
      }
    }

    primaryResult
  }

  private suspend fun Raise<MessageService.SendError>.sendPrimaryMessage(recipient: Recipient, dataMessage: DataMessage, editMessageTarget: Long?): MessageService.SendSuccess {
    val content: Content = if (editMessageTarget != null) {
      Content(
        editMessage = EditMessage(
          targetSentTimestamp = editMessageTarget,
          dataMessage = dataMessage
        )
      )
    } else {
      val pniSignature = if (recipient.needsPniSignature) {
        Log.i(TAG, "${logPrefix(dataMessage.timestamp)} Including PNI signature.")
        AppDependencies.signalServiceMessageSender.createPniSignatureMessage()
      } else {
        null
      }

      Content(
        dataMessage = dataMessage,
        pniSignatureMessage = pniSignature
      )
    }

    val envelopeContent = EnvelopeContent.encrypted(content, ContentHint.RESENDABLE, Optional.empty())

    // If this is a note to self message, don't actually send it. Instead, craft a result of what we *would* send. Then it'll be sent via sync message if appropriate.
    if (SignalStore.account.aci == recipient.serviceId.getOrNull()) {
      Log.i(TAG, "${logPrefix(dataMessage.timestamp)} Note to self. Skipping primary send.")
      return MessageService.SendSuccess(envelopeContent, true, listOf(SignalServiceAddress.DEFAULT_DEVICE_ID))
    }

    return AppDependencies.messageService.sendMessage(
      serviceId = recipient.requireServiceId(),
      envelopeContent = envelopeContent,
      timestamp = dataMessage.timestamp!!,
      sealedSenderAccess = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient),
      story = false,
      isOnline = false,
      urgent = content.isUrgent(),
      onEncrypted = { SignalLocalMetrics.IndividualMessageSend.onMessageEncrypted(messageId) }
    ).bind()
  }

  private suspend fun Raise<MessageService.SendError>.sendSyncMessage(targetRecipient: Recipient, primaryResult: MessageService.SendSuccess): MessageService.SendSuccess {
    val dataMessage = primaryResult.envelopeContent.content.get().dataMessage
    val editMessage = primaryResult.envelopeContent.content.get().editMessage
    val timestamp = dataMessage?.timestamp ?: editMessage?.dataMessage?.timestamp ?: raise(MessageService.SendError.ApplicationError(IllegalStateException("No timestamp on primary message send!")))

    val recipientServiceId = targetRecipient.requireServiceId()
    val pniIdentityKey: ByteString? = if (recipientServiceId is ServiceId.PNI) {
      AppDependencies
        .protocolStore
        .aci()
        .identities()
        .getIdentity(SignalProtocolAddress(recipientServiceId.toString(), SignalServiceAddress.DEFAULT_DEVICE_ID))?.publicKey?.serialize()?.toByteString()
    } else {
      null
    }

    val syncContent = Content(
      syncMessage = SyncMessage(
        sent = SyncMessage.Sent(
          destinationServiceId = targetRecipient.serviceId.get().toString(),
          timestamp = timestamp,
          message = dataMessage,
          editMessage = editMessage,
          unidentifiedStatus = listOf(
            SyncMessage.Sent.UnidentifiedDeliveryStatus(
              destinationServiceIdBinary = recipientServiceId.toByteString(),
              unidentified = primaryResult.sentSealedSender,
              destinationPniIdentityKey = pniIdentityKey
            )
          )
        )
      )
    )
    val syncEnvelope = EnvelopeContent.encrypted(syncContent, ContentHint.IMPLICIT, Optional.empty())

    return AppDependencies.messageService.sendSyncMessage(
      envelopeContent = syncEnvelope,
      timestamp = timestamp,
      urgent = true,
      onEncrypted = { SignalLocalMetrics.IndividualMessageSend.onSyncMessageEncrypted(messageId) }
    ).bind()
  }

  override fun onRetry() {
    SignalLocalMetrics.IndividualMessageSend.cancel(messageId)
    if (runAttempt > 1) {
      AppDependencies.jobManager.add(ServiceOutageDetectionJob())
    }
  }

  override fun onFailure() {
    SignalLocalMetrics.IndividualMessageSend.cancel(messageId)
    SignalDatabase.messages.markAsSentFailed(messageId)
    PushSendJob.notifyMediaMessageDeliveryFailed(context, messageId)
  }

  private fun nextRunAttemptBackoff(pastAttemptCount: Int): Long {
    return BackoffUtil.exponentialBackoff(pastAttemptCount, RemoteConfig.defaultMaxBackoff)
  }

  /**
   * Syncs prekeys if we haven't done so for a long time. In practice, we shouldn't hit this -- it's a failsafe.
   * @return if non-null, this should be used as the overall job result.
   */
  private fun syncPreKeysIfNecessary(): Either<Result, Unit> = either {
    val timeSinceAciSignedPreKeyRotation = System.currentTimeMillis() - SignalStore.account.aciPreKeys.lastSignedPreKeyRotationTime
    val timeSincePniSignedPreKeyRotation = System.currentTimeMillis() - SignalStore.account.pniPreKeys.lastSignedPreKeyRotationTime
    if (timeSinceAciSignedPreKeyRotation > PreKeysSyncJob.MAXIMUM_ALLOWED_SIGNED_PREKEY_AGE ||
      timeSinceAciSignedPreKeyRotation < 0 ||
      timeSincePniSignedPreKeyRotation > PreKeysSyncJob.MAXIMUM_ALLOWED_SIGNED_PREKEY_AGE ||
      timeSincePniSignedPreKeyRotation < 0
    ) {
      Log.w(TAG, "${logPrefix()} It's been too long since rotating our signed prekeys. Attempting to rotate now.")
      val state = AppDependencies.jobManager.runSynchronously(PreKeysSyncJob.create(), TimeUnit.SECONDS.toMillis(30))
      if (state.isPresent && state.get() == JobTracker.JobState.SUCCESS) {
        Log.i(TAG, "${logPrefix()} Successfully refreshed prekeys. Continuing.")
      } else {
        Log.w(TAG, "${logPrefix()} Failed to refresh prekeys; retrying. State: ${if (state.isEmpty) "<empty>" else state.get()}")
        raise(Result.retry(nextRunAttemptBackoff(runAttempt + 1)))
      }
    }
  }

  private fun Recipient.validated(sentTime: Long): Either<Result, Recipient> = either {
    if (isUnregistered) {
      Log.w(TAG, "${logPrefix(sentTime)} Recipient $id not registered; failing.")
      raise(Result.failure())
    }

    if (!hasServiceId) {
      Log.w(TAG, "${logPrefix(sentTime)} Recipient $id has no serviceId; failing.")
      raise(Result.failure())
    }

    this@validated
  }

  private fun logPrefix(sentTimestamp: Long? = null): String = logPrefix(sentTimestamp, messageId)

  private fun buildAttachmentString(attachments: List<Attachment>): String {
    return attachments.joinToString(", ") { attachment ->
      when {
        attachment is DatabaseAttachment -> attachment.attachmentId.toString()
        attachment.uri != null -> attachment.uri.toString()
        else -> attachment.toString()
      }
    }
  }

  class Factory : Job.Factory<IndividualSendJobV2> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): IndividualSendJobV2 {
      val data = IndividualSendJobV2Data.ADAPTER.decode(serializedData!!)
      return IndividualSendJobV2(parameters, data.messageId)
    }
  }
}

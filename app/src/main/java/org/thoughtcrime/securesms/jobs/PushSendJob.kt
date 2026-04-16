/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import android.content.Context
import android.text.TextUtils
import org.greenrobot.eventbus.EventBus
import org.signal.blurhash.BlurHash
import org.signal.core.models.ServiceId
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.TextSecureExpiredException
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactModelMapper
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messages
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.stickers
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.threads
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.net.NotPushRegisteredException
import org.thoughtcrime.securesms.notifications.v2.ConversationId.Companion.forConversation
import org.thoughtcrime.securesms.notifications.v2.ConversationId.Companion.fromThreadAndReply
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.Companion.resolved
import org.thoughtcrime.securesms.recipients.Recipient.Companion.self
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.thoughtcrime.securesms.transport.UndeliverableMessageException
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.InvalidPreKeyException
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil.getCiphertextLength
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServicePreview
import org.whispersystems.signalservice.api.messages.shared.SharedContact
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.BodyRange
import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeUnit

abstract class PushSendJob protected constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(PushSendJob::class.java)

    @JvmStatic
    protected fun enqueueCompressingAndUploadAttachmentsChains(jobManager: JobManager, message: OutgoingMessage): Set<String> {
      val attachments: MutableList<Attachment> = mutableListOf()

      attachments += message.attachments
      attachments += message.linkPreviews.mapNotNull { it.thumbnail.orElse(null) }
      attachments += message.sharedContacts.mapNotNull { it.avatar?.attachment }

      val jobs: MutableSet<String> = attachments
        .map {
          val attachmentId = (it as DatabaseAttachment).attachmentId
          Log.d(TAG, "Enqueueing job chain to upload $attachmentId")

          val attachmentUploadJob = AttachmentUploadJob(attachmentId)

          AppDependencies.jobManager.startChain(AttachmentCompressionJob.fromAttachment(it, false, -1))
            .then(attachmentUploadJob)
            .enqueue()

          attachmentUploadJob.id
        }
        .toMutableSet()

      if (message.outgoingQuote?.attachment != null) {
        val attachmentId = (message.outgoingQuote.attachment as DatabaseAttachment).attachmentId

        if (SignalDatabase.attachments.hasData(attachmentId)) {
          val quoteUploadJob = AttachmentUploadJob(attachmentId)
          jobManager.add(quoteUploadJob)
          jobs.add(quoteUploadJob.id)
        }
      }

      return jobs
    }

    @JvmStatic
    protected fun notifyMediaMessageDeliveryFailed(context: Context, messageId: Long) {
      val threadId = messages.getThreadIdForMessage(messageId)
      val recipient = threads.getRecipientForThreadId(threadId)
      val groupReplyStoryId = messages.getParentStoryIdForGroupReply(messageId)

      var isStory = false
      try {
        val record = messages.getMessageRecord(messageId)
        if (record is MmsMessageRecord) {
          isStory = record.storyType.isStory
        }
      } catch (e: NoSuchMessageException) {
        Log.e(TAG, e)
      }

      if (threadId != -1L && recipient != null) {
        if (isStory) {
          messages.markAsNotNotified(messageId)
          AppDependencies.messageNotifier.notifyStoryDeliveryFailed(context, recipient, forConversation(threadId))
        } else {
          AppDependencies.messageNotifier.notifyMessageDeliveryFailed(context, recipient, fromThreadAndReply(threadId, groupReplyStoryId))
        }
      }
    }

    @JvmStatic
    protected fun markAttachmentsUploaded(messageId: Long, message: OutgoingMessage) {
      val attachments: MutableList<Attachment> = mutableListOf()

      attachments += message.attachments
      attachments += message.linkPreviews.mapNotNull { it.thumbnail.orElse(null) }
      attachments += message.sharedContacts.mapNotNull { it.avatarAttachment }

      message.outgoingQuote?.attachment?.let { attachments.add(it) }

      for (attachment in attachments) {
        SignalDatabase.attachments.markAttachmentUploaded(messageId, attachment)
      }
    }
  }

  @Throws(Exception::class)
  public override fun onRun() {
    if (SignalStore.misc.isClientDeprecated) {
      throw TextSecureExpiredException("TextSecure expired (build ${BuildConfig.BUILD_TIMESTAMP}, now ${System.currentTimeMillis()})")
    }

    Log.i(TAG, "Starting message send attempt")

    val timeSinceAciSignedPreKeyRotation = System.currentTimeMillis() - SignalStore.account.aciPreKeys.lastSignedPreKeyRotationTime
    val timeSincePniSignedPreKeyRotation = System.currentTimeMillis() - SignalStore.account.pniPreKeys.lastSignedPreKeyRotationTime

    if (timeSinceAciSignedPreKeyRotation > PreKeysSyncJob.MAXIMUM_ALLOWED_SIGNED_PREKEY_AGE || timeSinceAciSignedPreKeyRotation < 0 || timeSincePniSignedPreKeyRotation > PreKeysSyncJob.MAXIMUM_ALLOWED_SIGNED_PREKEY_AGE || timeSincePniSignedPreKeyRotation < 0) {
      warn(TAG, "It's been too long since rotating our signed prekeys (ACI: $timeSinceAciSignedPreKeyRotation ms, PNI: $timeSincePniSignedPreKeyRotation ms)! Attempting to rotate now.")

      val state = AppDependencies.jobManager.runSynchronously(PreKeysSyncJob.create(), TimeUnit.SECONDS.toMillis(30))

      if (state.isPresent && state.get() == JobTracker.JobState.SUCCESS) {
        log(TAG, "Successfully refreshed prekeys. Continuing.")
      } else {
        throw RetryLaterException(TextSecureExpiredException("Failed to refresh prekeys! State: ${if (state.isEmpty) "<empty>" else state.get()}"))
      }
    }

    if (!self().isRegistered) {
      throw NotPushRegisteredException()
    }

    onPushSend()

    if (SignalStore.rateLimit.needsRecaptcha()) {
      Log.i(TAG, "Successfully sent message. Assuming reCAPTCHA no longer needed.")
      SignalStore.rateLimit.onProofAccepted()
    }

    Log.i(TAG, "Message send completed")
  }

  override fun onRetry() {
    Log.i(TAG, "onRetry()")

    if (runAttempt > 1) {
      Log.i(TAG, "Scheduling service outage detection job.")
      AppDependencies.jobManager.add(ServiceOutageDetectionJob())
    }
  }

  override fun shouldTrace(): Boolean {
    return true
  }

  public override fun onShouldRetry(exception: Exception): Boolean {
    return when (exception) {
      is ServerRejectedException -> false
      is NotPushRegisteredException -> false
      is InvalidPreKeyException -> false
      is ProofRequiredException -> true
      is RetryLaterException -> true
      is IOException -> true
      else -> false
    }
  }

  override fun getNextRunAttemptBackoff(pastAttemptCount: Int, exception: Exception): Long {
    return this.getBackoffMillisFromException(TAG, pastAttemptCount, exception) { super.getNextRunAttemptBackoff(pastAttemptCount, exception) }
  }

  protected fun getProfileKey(recipient: Recipient): Optional<ByteArray> {
    if (!recipient.resolve().isSystemContact && !recipient.resolve().isProfileSharing) {
      return Optional.empty()
    }

    return Optional.of(ProfileKeyUtil.getSelfProfileKey().serialize())
  }

  protected fun getAttachmentFor(avatar: Contact.Avatar): SignalServiceAttachment? {
    val attachment = avatar.attachment ?: return null

    try {
      if (attachment.uri == null || attachment.size == 0L) {
        throw IOException("Assertion failed, outgoing attachment has no data!")
      }

      val inputStream = PartAuthority.getAttachmentStream(context, attachment.uri!!)
      val ciphertextLength = getCiphertextLength(PaddingInputStream.getPaddedSize(attachment.size))
      val uploadSpec = AppDependencies.signalServiceMessageSender.getResumableUploadSpec(ciphertextLength)

      return SignalServiceAttachment.newStreamBuilder()
        .withStream(inputStream)
        .withContentType(attachment.contentType)
        .withLength(attachment.size)
        .withFileName(attachment.fileName)
        .withVoiceNote(attachment.voiceNote)
        .withBorderless(attachment.borderless)
        .withGif(attachment.videoGif)
        .withFaststart(attachment.transformProperties!!.mp4FastStart)
        .withWidth(attachment.width)
        .withHeight(attachment.height)
        .withCaption(attachment.caption)
        .withUuid(attachment.uuid)
        .withResumableUploadSpec(uploadSpec)
        .withListener(object : SignalServiceAttachment.ProgressListener {
          override fun onAttachmentProgress(progress: AttachmentTransferProgress) {
            EventBus.getDefault().postSticky(PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, progress))
          }

          override fun shouldCancel(): Boolean {
            return isCanceled
          }
        })
        .build()
    } catch (ioe: IOException) {
      Log.w(TAG, "Couldn't open attachment", ioe)
    }
    return null
  }

  protected fun getAttachmentPointersFor(attachments: List<Attachment>): List<SignalServiceAttachment> {
    return attachments.mapNotNull { getAttachmentPointerFor(it) }
  }

  protected fun getAttachmentPointerFor(attachment: Attachment): SignalServiceAttachment? {
    if (TextUtils.isEmpty(attachment.remoteLocation)) {
      Log.w(TAG, "empty content id")
      return null
    }

    if (TextUtils.isEmpty(attachment.remoteKey)) {
      Log.w(TAG, "empty encrypted key")
      return null
    }

    try {
      val remoteId = SignalServiceAttachmentRemoteId.from(attachment.remoteLocation!!)
      val key = Base64.decode(attachment.remoteKey!!)

      var width = attachment.width
      var height = attachment.height

      if ((width == 0 || height == 0) && MediaUtil.hasVideoThumbnail(context, attachment.uri)) {
        val thumbnail = MediaUtil.getVideoThumbnail(context, attachment.uri, 1000)

        if (thumbnail != null) {
          width = thumbnail.width
          height = thumbnail.height
        }
      }

      return SignalServiceAttachmentPointer(
        cdnNumber = attachment.cdn.cdnNumber,
        remoteId = remoteId,
        contentType = attachment.contentType,
        key = key,
        size = Optional.of(Util.toIntExact(attachment.size)),
        preview = Optional.empty(),
        width = width,
        height = height,
        digest = Optional.ofNullable(attachment.remoteDigest),
        incrementalDigest = Optional.ofNullable(attachment.incrementalDigest),
        incrementalMacChunkSize = attachment.incrementalMacChunkSize,
        fileName = Optional.ofNullable(attachment.fileName),
        voiceNote = attachment.voiceNote,
        isBorderless = attachment.borderless,
        isGif = attachment.videoGif,
        caption = Optional.ofNullable(attachment.caption),
        blurHash = Optional.ofNullable(attachment.blurHash).map(BlurHash::hash),
        uploadTimestamp = attachment.uploadTimestamp,
        uuid = attachment.uuid
      )
    } catch (e: IOException) {
      Log.w(TAG, e)
      return null
    } catch (e: ArithmeticException) {
      Log.w(TAG, e)
      return null
    }
  }

  @Throws(IOException::class)
  protected fun getQuoteFor(message: OutgoingMessage): Optional<SignalServiceDataMessage.Quote> {
    if (message.outgoingQuote == null) {
      return Optional.empty()
    }

    if (message.isMessageEdit) {
      return Optional.of(SignalServiceDataMessage.Quote(0, ACI.UNKNOWN, "", null, null, SignalServiceDataMessage.Quote.Type.NORMAL, null))
    }

    val quoteId = message.outgoingQuote.id
    val quoteBody = message.outgoingQuote.text
    val quoteAuthor = message.outgoingQuote.author
    val quoteMentions = getMentionsFor(message.outgoingQuote.mentions)
    val bodyRanges = getBodyRanges(message.outgoingQuote.bodyRanges)
    val quoteType = message.outgoingQuote.type
    val quoteAttachments = mutableListOf<SignalServiceDataMessage.Quote.QuotedAttachment>()
    var localQuoteAttachment: Attachment? = message.outgoingQuote.attachment

    if (localQuoteAttachment != null && MediaUtil.isViewOnceType(localQuoteAttachment.contentType)) {
      localQuoteAttachment = null
    }

    if (localQuoteAttachment != null) {
      val quoteAttachmentPointer = getAttachmentPointerFor(localQuoteAttachment)

      quoteAttachments.add(
        SignalServiceDataMessage.Quote.QuotedAttachment(
          contentType = localQuoteAttachment.quoteTargetContentType ?: MediaUtil.IMAGE_JPEG,
          fileName = localQuoteAttachment.fileName,
          thumbnail = quoteAttachmentPointer
        )
      )
    }

    val quoteAuthorRecipient = resolved(quoteAuthor)

    if (quoteAuthorRecipient.isMaybeRegistered) {
      return Optional.of(
        SignalServiceDataMessage.Quote(
          id = quoteId,
          author = RecipientUtil.getOrFetchServiceId(context, quoteAuthorRecipient),
          text = quoteBody,
          attachments = quoteAttachments,
          mentions = quoteMentions,
          type = quoteType.dataMessageType,
          bodyRanges = bodyRanges
        )
      )
    } else if (quoteAuthorRecipient.hasServiceId) {
      return Optional.of(
        SignalServiceDataMessage.Quote(
          id = quoteId,
          author = quoteAuthorRecipient.requireAci(),
          text = quoteBody,
          attachments = quoteAttachments,
          mentions = quoteMentions,
          type = quoteType.dataMessageType,
          bodyRanges = bodyRanges
        )
      )
    } else {
      return Optional.empty()
    }
  }

  protected fun getStickerFor(message: OutgoingMessage): Optional<SignalServiceDataMessage.Sticker> {
    val stickerAttachment = message.attachments.firstOrNull { it.isSticker } ?: return Optional.empty()

    if (stickerAttachment.stickerLocator == null) {
      return Optional.empty()
    }

    try {
      val packId = Hex.fromStringCondensed(stickerAttachment.stickerLocator!!.packId)
      val packKey = Hex.fromStringCondensed(stickerAttachment.stickerLocator.packKey)
      val stickerId = stickerAttachment.stickerLocator.stickerId
      val record = stickers.getSticker(stickerAttachment.stickerLocator.packId, stickerId, false)
      val emoji = record?.emoji
      val attachment = getAttachmentPointerFor(stickerAttachment)

      return Optional.of(
        SignalServiceDataMessage.Sticker(
          packId = packId,
          packKey = packKey,
          stickerId = stickerId,
          emoji = emoji,
          attachment = attachment
        )
      )
    } catch (e: IOException) {
      Log.w(TAG, "Failed to decode sticker id/key", e)
      return Optional.empty()
    }
  }

  protected fun getStoryReactionFor(message: OutgoingMessage, storyContext: SignalServiceDataMessage.StoryContext): Optional<SignalServiceDataMessage.Reaction> {
    if (!message.isStoryReaction) {
      return Optional.empty()
    }

    return Optional.of(
      SignalServiceDataMessage.Reaction(
        emoji = message.body,
        isRemove = false,
        targetAuthor = storyContext.authorServiceId,
        targetSentTimestamp = storyContext.sentTimestamp
      )
    )
  }

  fun getSharedContactsFor(mediaMessage: OutgoingMessage): List<SharedContact> {
    return mediaMessage.sharedContacts.map { contact ->
      val builder = ContactModelMapper.localToRemoteBuilder(contact)
      val avatar = contact.avatar

      if (avatar != null) {
        val avatarAttachment = avatar.attachment
        if (avatarAttachment != null) {
          val attachment = getAttachmentPointerFor(avatarAttachment) ?: getAttachmentFor(avatar)
          builder.setAvatar(
            SharedContact.Avatar.newBuilder()
              .withAttachment(attachment)
              .withProfileFlag(avatar.isProfile)
              .build()
          )
        }
      }

      builder.build()
    }
  }

  fun getPreviewsFor(mediaMessage: OutgoingMessage): List<SignalServicePreview> {
    return mediaMessage.linkPreviews.map { lp ->
      val attachment = if (lp.thumbnail.isPresent) getAttachmentPointerFor(lp.thumbnail.get()) else null
      SignalServicePreview(lp.url, lp.title, lp.description, lp.date, Optional.ofNullable(attachment))
    }
  }

  fun getMentionsFor(mentions: List<Mention>): List<SignalServiceDataMessage.Mention> {
    return mentions.map { m -> SignalServiceDataMessage.Mention(Recipient.resolved(m.recipientId).requireAci(), m.start, m.length) }
  }

  @Throws(UndeliverableMessageException::class)
  fun getGiftBadgeFor(message: OutgoingMessage): SignalServiceDataMessage.GiftBadge? {
    val giftBadge = message.giftBadge ?: return null

    try {
      val presentation = ReceiptCredentialPresentation(giftBadge.redemptionToken.toByteArray())
      return SignalServiceDataMessage.GiftBadge(presentation)
    } catch (invalidInputException: InvalidInputException) {
      throw UndeliverableMessageException(invalidInputException)
    }
  }

  protected fun getBodyRanges(message: OutgoingMessage): List<BodyRange>? {
    return getBodyRanges(message.bodyRanges)
  }

  protected fun getPollCreate(message: OutgoingMessage): SignalServiceDataMessage.PollCreate? {
    val poll = message.poll ?: return null
    return SignalServiceDataMessage.PollCreate(poll.question, poll.allowMultipleVotes, poll.pollOptions)
  }

  protected fun getPollTerminate(message: OutgoingMessage): SignalServiceDataMessage.PollTerminate? {
    val pollTerminate = message.messageExtras?.pollTerminate ?: return null
    return SignalServiceDataMessage.PollTerminate(pollTerminate.targetTimestamp)
  }

  protected fun getBodyRanges(bodyRanges: BodyRangeList?): List<BodyRange>? {
    if (bodyRanges == null || bodyRanges.ranges.isEmpty()) {
      return null
    }

    return bodyRanges.ranges.map { range ->
      val builder = BodyRange.Builder().start(range.start).length(range.length)
      when (range.style) {
        BodyRangeList.BodyRange.Style.BOLD -> builder.style(BodyRange.Style.BOLD)
        BodyRangeList.BodyRange.Style.ITALIC -> builder.style(BodyRange.Style.ITALIC)
        BodyRangeList.BodyRange.Style.SPOILER -> builder.style(BodyRange.Style.SPOILER)
        BodyRangeList.BodyRange.Style.STRIKETHROUGH -> builder.style(BodyRange.Style.STRIKETHROUGH)
        BodyRangeList.BodyRange.Style.MONOSPACE -> builder.style(BodyRange.Style.MONOSPACE)
        null -> throw IllegalArgumentException("Only supports style")
        else -> throw IllegalArgumentException("Unrecognized style")
      }
      builder.build()
    }
  }

  protected fun getPinnedMessage(message: OutgoingMessage): SignalServiceDataMessage.PinnedMessage? {
    val pinnedMessage = message.messageExtras?.pinnedMessage ?: return null
    val targetAuthor = ServiceId.parseOrNull(pinnedMessage.targetAuthorAci) ?: return null

    return if (pinnedMessage.pinDurationInSeconds == MessageTable.PIN_FOREVER) {
      SignalServiceDataMessage.PinnedMessage(
        targetAuthor = targetAuthor,
        targetSentTimestamp = pinnedMessage.targetTimestamp,
        pinDurationInSeconds = null,
        forever = true
      )
    } else {
      SignalServiceDataMessage.PinnedMessage(
        targetAuthor = targetAuthor,
        targetSentTimestamp = pinnedMessage.targetTimestamp,
        pinDurationInSeconds = pinnedMessage.pinDurationInSeconds.toInt(),
        forever = null
      )
    }
  }

  protected fun buildAttachmentString(attachments: List<Attachment>): String {
    return attachments.joinToString(", ") { attachment ->
      when {
        attachment is DatabaseAttachment -> attachment.attachmentId.toString()
        attachment.uri != null -> attachment.uri.toString()
        else -> attachment.toString()
      }
    }
  }

  @Throws(Exception::class)
  protected abstract fun onPushSend()
}

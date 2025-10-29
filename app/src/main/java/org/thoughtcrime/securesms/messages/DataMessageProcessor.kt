package org.thoughtcrime.securesms.messages

import android.content.Context
import android.text.TextUtils
import com.mobilecoin.lib.exceptions.SerializationException
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.isNotEmpty
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.core.util.toOptional
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.LocalStickerAttachment
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.attachments.TombstoneAttachment
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactModelMapper
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.crypto.SecurityEvent
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageTable.InsertResult
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.PaymentTable.PublicKeyConflictException
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.ParentStoryId.DirectReply
import org.thoughtcrime.securesms.database.model.ParentStoryId.GroupReply
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.database.model.databaseprotos.PollTerminate
import org.thoughtcrime.securesms.database.model.toBodyRangeList
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob
import org.thoughtcrime.securesms.jobs.GroupCallPeekJob
import org.thoughtcrime.securesms.jobs.GroupV2UpdateSelfProfileKeyJob
import org.thoughtcrime.securesms.jobs.PaymentLedgerUpdateJob
import org.thoughtcrime.securesms.jobs.PaymentTransactionCheckJob
import org.thoughtcrime.securesms.jobs.ProfileKeySendJob
import org.thoughtcrime.securesms.jobs.PushProcessEarlyMessagesJob
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob
import org.thoughtcrime.securesms.jobs.StorageSyncJob
import org.thoughtcrime.securesms.jobs.TrimThreadJob
import org.thoughtcrime.securesms.jobs.protos.GroupCallPeekJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.debug
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.log
import org.thoughtcrime.securesms.messages.MessageContentProcessor.Companion.warn
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.expireTimerDuration
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupMasterKey
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.hasGroupContext
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.hasRemoteDelete
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isEndSession
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isExpirationUpdate
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isInvalid
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isMediaMessage
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isPaymentActivated
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isPaymentActivationRequest
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isStoryReaction
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toPointer
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toPointersWithinLimit
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.polls.Poll
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.Recipient.HiddenState
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.EarlyMessageCacheEntry
import org.thoughtcrime.securesms.util.LinkUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MessageConstraintsUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.isStory
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.payments.Money
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.util.Preconditions
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.push.BodyRange
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.push.GroupContextV2
import org.whispersystems.signalservice.internal.push.Preview
import org.whispersystems.signalservice.internal.util.Util
import java.util.Optional
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object DataMessageProcessor {

  private const val BODY_RANGE_PROCESSING_LIMIT = 250
  private const val POLL_CHARACTER_LIMIT = 100
  private const val POLL_OPTIONS_LIMIT = 10

  fun process(
    context: Context,
    senderRecipient: Recipient,
    threadRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    receivedTime: Long,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?,
    localMetrics: SignalLocalMetrics.MessageReceive?
  ) {
    val message: DataMessage = content.dataMessage!!
    val groupSecretParams = if (message.hasGroupContext) GroupSecretParams.deriveFromMasterKey(message.groupV2!!.groupMasterKey) else null
    val groupId: GroupId.V2? = if (groupSecretParams != null) GroupId.v2(groupSecretParams.publicParams.groupIdentifier) else null

    var groupProcessResult: MessageContentProcessor.Gv2PreProcessResult? = null
    if (groupId != null) {
      groupProcessResult = MessageContentProcessor.handleGv2PreProcessing(
        context = context,
        timestamp = envelope.timestamp!!,
        content = content,
        metadata = metadata,
        groupId = groupId,
        groupV2 = message.groupV2!!,
        senderRecipient = senderRecipient,
        groupSecretParams = groupSecretParams,
        serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary)
      )

      if (groupProcessResult == MessageContentProcessor.Gv2PreProcessResult.IGNORE) {
        return
      }
      localMetrics?.onGv2Processed()
    }

    var insertResult: InsertResult? = null
    var messageId: MessageId? = null
    when {
      message.isInvalid -> handleInvalidMessage(context, senderRecipient.id, groupId, envelope.timestamp!!)
      message.isEndSession -> insertResult = handleEndSessionMessage(context, senderRecipient.id, envelope, metadata)
      message.isExpirationUpdate -> insertResult = handleExpirationUpdate(envelope, metadata, senderRecipient, threadRecipient.id, groupId, message.expireTimerDuration, message.expireTimerVersion, receivedTime, false)
      message.isStoryReaction -> insertResult = handleStoryReaction(context, envelope, metadata, message, senderRecipient.id, groupId)
      message.reaction != null -> messageId = handleReaction(context, envelope, message, senderRecipient.id, earlyMessageCacheEntry)
      message.hasRemoteDelete -> messageId = handleRemoteDelete(context, envelope, message, senderRecipient.id, earlyMessageCacheEntry)
      message.isPaymentActivationRequest -> insertResult = handlePaymentActivation(envelope, metadata, message, senderRecipient.id, receivedTime, isActivatePaymentsRequest = true, isPaymentsActivated = false)
      message.isPaymentActivated -> insertResult = handlePaymentActivation(envelope, metadata, message, senderRecipient.id, receivedTime, isActivatePaymentsRequest = false, isPaymentsActivated = true)
      message.payment != null -> insertResult = handlePayment(context, envelope, metadata, message, senderRecipient.id, receivedTime)
      message.storyContext != null -> insertResult = handleStoryReply(context, envelope, metadata, message, senderRecipient, groupId, receivedTime)
      message.giftBadge != null -> insertResult = handleGiftMessage(context, envelope, metadata, message, senderRecipient, threadRecipient.id, receivedTime)
      message.isMediaMessage -> insertResult = handleMediaMessage(context, envelope, metadata, message, senderRecipient, threadRecipient, groupId, receivedTime, localMetrics)
      message.body != null -> insertResult = handleTextMessage(context, envelope, metadata, message, senderRecipient, threadRecipient, groupId, receivedTime, localMetrics)
      message.groupCallUpdate != null -> handleGroupCallUpdateMessage(envelope, message, senderRecipient.id, groupId)
      message.pollCreate != null -> insertResult = handlePollCreate(context, envelope, metadata, message, senderRecipient, threadRecipient, groupId, receivedTime)
      message.pollTerminate != null -> insertResult = handlePollTerminate(context, envelope, metadata, message, senderRecipient, earlyMessageCacheEntry, threadRecipient, groupId, receivedTime)
      message.pollVote != null -> messageId = handlePollVote(context, envelope, message, senderRecipient, earlyMessageCacheEntry)
    }

    messageId = messageId ?: insertResult?.messageId?.let { MessageId(it) }
    if (messageId != null) {
      log(envelope.timestamp!!, "Inserted as messageId $messageId")
    }

    if (groupId != null) {
      val unknownGroup = when (groupProcessResult) {
        MessageContentProcessor.Gv2PreProcessResult.GROUP_UP_TO_DATE -> threadRecipient.isUnknownGroup
        else -> SignalDatabase.groups.isUnknownGroup(groupId)
      }
      if (unknownGroup) {
        handleUnknownGroupMessage(envelope.timestamp!!, message.groupV2!!)
      }
    }

    if (message.profileKey.isNotEmpty()) {
      handleProfileKey(envelope.timestamp!!, message.profileKey!!.toByteArray(), senderRecipient)
    }

    if (groupId == null && senderRecipient.hiddenState == HiddenState.HIDDEN) {
      SignalDatabase.recipients.markHidden(senderRecipient.id, clearProfileKey = false, showMessageRequest = true)
    }

    if (metadata.sealedSender && messageId != null) {
      SignalExecutors.BOUNDED.execute { AppDependencies.jobManager.add(SendDeliveryReceiptJob(senderRecipient.id, message.timestamp!!, messageId)) }
    } else if (!metadata.sealedSender) {
      if (RecipientUtil.shouldHaveProfileKey(threadRecipient)) {
        Log.w(MessageContentProcessor.TAG, "Received an unsealed sender message from " + senderRecipient.id + ", but they should already have our profile key. Correcting.")

        if (groupId != null) {
          Log.i(MessageContentProcessor.TAG, "Message was to a GV2 group. Ensuring our group profile keys are up to date.")
          AppDependencies
            .jobManager
            .startChain(RefreshAttributesJob(false))
            .then(GroupV2UpdateSelfProfileKeyJob.withQueueLimits(groupId))
            .enqueue()
        } else if (!threadRecipient.isGroup) {
          Log.i(MessageContentProcessor.TAG, "Message was to a 1:1. Ensuring this user has our profile key.")
          val profileSendJob = ProfileKeySendJob.create(SignalDatabase.threads.getOrCreateThreadIdFor(threadRecipient), true)
          if (profileSendJob != null) {
            AppDependencies
              .jobManager
              .startChain(RefreshAttributesJob(false))
              .then(profileSendJob)
              .enqueue()
          }
        }
      }
    }

    if (insertResult != null && insertResult.threadWasNewlyCreated && !threadRecipient.isGroup && !threadRecipient.isSelf && !senderRecipient.isSystemContact) {
      val timeSinceLastSync = System.currentTimeMillis() - SignalStore.misc.lastCdsForegroundSyncTime
      if (timeSinceLastSync > RemoteConfig.cdsForegroundSyncInterval || timeSinceLastSync < 0) {
        log(envelope.timestamp!!, "New 1:1 chat. Scheduling a CDS sync to see if they match someone in our contacts.")
        AppDependencies.jobManager.add(DirectoryRefreshJob(false))
        SignalStore.misc.lastCdsForegroundSyncTime = System.currentTimeMillis()
      } else {
        warn(envelope.timestamp!!, "New 1:1 chat, but performed a CDS sync $timeSinceLastSync ms ago, which is less than our threshold. Skipping CDS sync.")
      }
    }

    localMetrics?.onPostProcessComplete()
    localMetrics?.complete(groupId != null)
  }

  private fun handleProfileKey(
    timestamp: Long,
    messageProfileKeyBytes: ByteArray,
    senderRecipient: Recipient
  ) {
    val messageProfileKey = ProfileKeyUtil.profileKeyOrNull(messageProfileKeyBytes)

    if (senderRecipient.isSelf) {
      if (ProfileKeyUtil.getSelfProfileKey() != messageProfileKey) {
        warn(timestamp, "Saw a sync message whose profile key doesn't match our records. Scheduling a storage sync to check.")
        AppDependencies.jobManager.add(StorageSyncJob.forRemoteChange())
      }
    } else if (messageProfileKey != null) {
      if (messageProfileKeyBytes.contentEquals(senderRecipient.profileKey)) {
        return
      }
      if (SignalDatabase.recipients.setProfileKey(senderRecipient.id, messageProfileKey)) {
        log(timestamp, "Profile key on message from " + senderRecipient.id + " didn't match our local store. It has been updated.")
        SignalDatabase.runPostSuccessfulTransaction {
          RetrieveProfileJob.enqueue(senderRecipient.id, skipDebounce = true)
        }
      }
    } else {
      warn(timestamp.toString(), "Ignored invalid profile key seen in message")
    }
  }

  @Throws(BadGroupIdException::class)
  fun handleUnknownGroupMessage(timestamp: Long, groupContextV2: GroupContextV2) {
    log(timestamp, "Unknown group message.")
    warn(timestamp, "Received a GV2 message for a group we have no knowledge of -- attempting to fix this state.")
    SignalDatabase.groups.fixMissingMasterKey(groupContextV2.groupMasterKey)
  }

  private fun handleInvalidMessage(
    context: Context,
    sender: RecipientId,
    groupId: GroupId?,
    timestamp: Long
  ) {
    log(timestamp, "Invalid message.")

    val insertResult: InsertResult? = insertPlaceholder(sender, timestamp, groupId)
    if (insertResult != null) {
      SignalDatabase.messages.markAsInvalidMessage(insertResult.messageId)
      AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
    }
  }

  private fun handleEndSessionMessage(
    context: Context,
    senderRecipientId: RecipientId,
    envelope: Envelope,
    metadata: EnvelopeMetadata
  ): InsertResult? {
    log(envelope.timestamp!!, "End session message.")

    val incomingMessage = IncomingMessage(
      from = senderRecipientId,
      sentTimeMillis = envelope.timestamp!!,
      serverTimeMillis = envelope.serverTimestamp!!,
      receivedTimeMillis = System.currentTimeMillis(),
      isUnidentified = metadata.sealedSender,
      serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary),
      type = MessageType.END_SESSION
    )

    val insertResult: InsertResult? = SignalDatabase.messages.insertMessageInbox(incomingMessage).orNull()

    return if (insertResult != null) {
      AppDependencies.protocolStore.aci().deleteAllSessions(metadata.sourceServiceId.toString())
      SecurityEvent.broadcastSecurityUpdateEvent(context)
      AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
      insertResult
    } else {
      null
    }
  }

  /**
   * @param sideEffect True if the event is side effect of a different message, false if the message itself was an expiration update.
   * @throws StorageFailedException
   */
  @Throws(StorageFailedException::class)
  private fun handleExpirationUpdate(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    senderRecipient: Recipient,
    threadRecipientId: RecipientId,
    groupId: GroupId.V2?,
    expiresIn: Duration,
    expireTimerVersion: Int?,
    receivedTime: Long,
    sideEffect: Boolean
  ): InsertResult? {
    log(envelope.timestamp!!, "Expiration update. Side effect: $sideEffect")

    if (groupId != null) {
      warn(envelope.timestamp!!, "Expiration update received for GV2. Ignoring.")
      return null
    }

    if (SignalDatabase.recipients.getExpiresInSeconds(threadRecipientId) == expiresIn.inWholeSeconds) {
      log(envelope.timestamp!!, "No change in message expiry for group. Ignoring.")
      return null
    }

    if (expireTimerVersion != null && expireTimerVersion < senderRecipient.expireTimerVersion) {
      log(envelope.timestamp!!, "Old expireTimerVersion. Received: $expireTimerVersion, Current: ${senderRecipient.expireTimerVersion}. Ignoring.")
      return null
    }

    try {
      val mediaMessage = IncomingMessage(
        type = MessageType.EXPIRATION_UPDATE,
        from = senderRecipient.id,
        sentTimeMillis = envelope.timestamp!! - if (sideEffect) 1 else 0,
        serverTimeMillis = envelope.serverTimestamp!!,
        receivedTimeMillis = receivedTime,
        expiresIn = expiresIn.inWholeMilliseconds,
        isUnidentified = metadata.sealedSender,
        serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary)
      )

      val insertResult: InsertResult? = SignalDatabase.messages.insertMessageInbox(mediaMessage, -1).orNull()

      if (expireTimerVersion != null) {
        SignalDatabase.recipients.setExpireMessages(threadRecipientId, expiresIn.inWholeSeconds.toInt(), expireTimerVersion)
      } else {
        // TODO [expireVersion] After unsupported builds expire, we can remove this branch
        SignalDatabase.recipients.setExpireMessagesWithoutIncrementingVersion(threadRecipientId, expiresIn.inWholeSeconds.toInt())
      }

      if (insertResult != null) {
        return insertResult
      }
    } catch (e: MmsException) {
      throw StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    }

    return null
  }

  /**
   * Inserts an expiration update if the message timer doesn't match the thread timer.
   */
  @Throws(StorageFailedException::class)
  fun handlePossibleExpirationUpdate(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    senderRecipient: Recipient,
    threadRecipient: Recipient,
    groupId: GroupId.V2?,
    expiresIn: Duration,
    expireTimerVersion: Int?,
    receivedTime: Long
  ) {
    if (threadRecipient.expiresInSeconds.toLong() != expiresIn.inWholeSeconds || ((expireTimerVersion ?: -1) > threadRecipient.expireTimerVersion)) {
      warn(envelope.timestamp!!, "Message expire time didn't match thread expire time. Handling timer update.")
      handleExpirationUpdate(envelope, metadata, senderRecipient, threadRecipient.id, groupId, expiresIn, expireTimerVersion, receivedTime, true)
    }
  }

  @Throws(StorageFailedException::class)
  private fun handleStoryReaction(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    senderRecipientId: RecipientId,
    groupId: GroupId.V2?
  ): InsertResult? {
    log(envelope.timestamp!!, "Story reaction.")

    val storyContext = message.storyContext!!
    val emoji = message.reaction!!.emoji

    if (!EmojiUtil.isEmoji(emoji)) {
      warn(envelope.timestamp!!, "Story reaction text is not a valid emoji! Ignoring the message.")
      return null
    }

    val authorServiceId: ServiceId = ACI.parseOrThrow(storyContext.authorAci, storyContext.authorAciBinary)
    val sentTimestamp = storyContext.sentTimestamp!!

    SignalDatabase.messages.beginTransaction()
    return try {
      val authorRecipientId = RecipientId.from(authorServiceId)
      val parentStoryId: ParentStoryId
      var quoteModel: QuoteModel? = null
      var expiresIn: Duration = 0L.seconds

      try {
        val storyId = SignalDatabase.messages.getStoryId(authorRecipientId, sentTimestamp).id

        if (groupId != null) {
          parentStoryId = GroupReply(storyId)
        } else if (SignalDatabase.storySends.canReply(senderRecipientId, sentTimestamp)) {
          val story = SignalDatabase.messages.getMessageRecord(storyId) as MmsMessageRecord
          var displayText = ""
          var bodyRanges: BodyRangeList? = null

          if (story.storyType.isTextStory) {
            displayText = story.body
            bodyRanges = story.messageRanges
          }

          parentStoryId = DirectReply(storyId)
          quoteModel = QuoteModel(sentTimestamp, authorRecipientId, displayText, false, story.slideDeck.asAttachments().firstOrNull(), emptyList(), QuoteModel.Type.NORMAL, bodyRanges)
          expiresIn = message.expireTimerDuration
        } else {
          warn(envelope.timestamp!!, "Story has reactions disabled. Dropping reaction.")
          return null
        }
      } catch (e: NoSuchMessageException) {
        warn(envelope.timestamp!!, "Couldn't find story for reaction.", e)
        return null
      }

      val mediaMessage = IncomingMessage(
        type = MessageType.STORY_REACTION,
        from = senderRecipientId,
        sentTimeMillis = envelope.timestamp!!,
        serverTimeMillis = envelope.serverTimestamp!!,
        receivedTimeMillis = System.currentTimeMillis(),
        parentStoryId = parentStoryId,
        isStoryReaction = true,
        expiresIn = expiresIn.inWholeMilliseconds,
        isUnidentified = metadata.sealedSender,
        body = emoji,
        groupId = groupId,
        quote = quoteModel,
        serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary)
      )

      val insertResult: InsertResult? = SignalDatabase.messages.insertMessageInbox(mediaMessage, -1).orNull()
      if (insertResult != null) {
        SignalDatabase.messages.setTransactionSuccessful()

        if (parentStoryId.isGroupReply()) {
          AppDependencies.messageNotifier.updateNotification(context, ConversationId.fromThreadAndReply(insertResult.threadId, parentStoryId as GroupReply))
        } else {
          AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
          TrimThreadJob.enqueueAsync(insertResult.threadId)
        }

        if (parentStoryId.isDirectReply()) {
          insertResult
        } else {
          null
        }
      } else {
        warn(envelope.timestamp!!, "Failed to insert story reaction")
        null
      }
    } catch (e: MmsException) {
      throw StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    } finally {
      SignalDatabase.messages.endTransaction()
    }
  }

  @Throws(StorageFailedException::class)
  fun handleReaction(
    context: Context,
    envelope: Envelope,
    message: DataMessage,
    senderRecipientId: RecipientId,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?
  ): MessageId? {
    val reaction: DataMessage.Reaction = message.reaction!!

    log(envelope.timestamp!!, "Handle reaction for message " + reaction.targetSentTimestamp!!)

    val emoji: String? = reaction.emoji
    val isRemove: Boolean = reaction.remove ?: false
    val targetAuthorServiceId: ServiceId = ACI.parseOrThrow(reaction.targetAuthorAci, reaction.targetAuthorAciBinary)
    val targetSentTimestamp: Long = reaction.targetSentTimestamp!!

    if (targetAuthorServiceId.isUnknown) {
      warn(envelope.timestamp!!, "Reaction was to an unknown UUID! Ignoring the message.")
      return null
    }

    if (!EmojiUtil.isEmoji(emoji)) {
      warn(envelope.timestamp!!, "Reaction text is not a valid emoji! Ignoring the message.")
      return null
    }

    val targetAuthor = Recipient.externalPush(targetAuthorServiceId)
    val targetMessage = SignalDatabase.messages.getMessageFor(targetSentTimestamp, targetAuthor.id)
    if (targetMessage == null) {
      warn(envelope.timestamp!!, "[handleReaction] Could not find matching message! Putting it in the early message cache. timestamp: " + targetSentTimestamp + "  author: " + targetAuthor.id)
      if (earlyMessageCacheEntry != null) {
        AppDependencies.earlyMessageCache.store(targetAuthor.id, targetSentTimestamp, earlyMessageCacheEntry)
        PushProcessEarlyMessagesJob.enqueue()
      }
      return null
    }

    if (targetMessage.isRemoteDelete) {
      warn(envelope.timestamp!!, "[handleReaction] Found a matching message, but it's flagged as remotely deleted. timestamp: " + targetSentTimestamp + "  author: " + targetAuthor.id)
      return null
    }

    val targetThread = SignalDatabase.threads.getThreadRecord(targetMessage.threadId)
    if (targetThread == null) {
      warn(envelope.timestamp!!, "[handleReaction] Could not find a thread for the message! timestamp: " + targetSentTimestamp + "  author: " + targetAuthor.id)
      return null
    }

    val targetThreadRecipientId = targetThread.recipient.id
    val groupRecord = SignalDatabase.groups.getGroup(targetThreadRecipientId).orNull()
    if (groupRecord != null && !groupRecord.members.contains(senderRecipientId)) {
      warn(envelope.timestamp!!, "[handleReaction] Reaction author is not in the group! timestamp: " + targetSentTimestamp + "  author: " + targetAuthor.id)
      return null
    }

    if (groupRecord == null && senderRecipientId != targetThreadRecipientId && Recipient.self().id != senderRecipientId) {
      warn(envelope.timestamp!!, "[handleReaction] Reaction author is not a part of the 1:1 thread! timestamp: " + targetSentTimestamp + "  author: " + targetAuthor.id)
      return null
    }

    val targetMessageId = (targetMessage as? MmsMessageRecord)?.latestRevisionId ?: MessageId(targetMessage.id)

    if (isRemove) {
      SignalDatabase.reactions.deleteReaction(targetMessageId, senderRecipientId)
      AppDependencies.messageNotifier.updateNotification(context)
    } else {
      val reactionRecord = ReactionRecord(emoji!!, senderRecipientId, message.timestamp!!, System.currentTimeMillis())
      SignalDatabase.reactions.addReaction(targetMessageId, reactionRecord)
      AppDependencies.messageNotifier.updateNotification(context, ConversationId.fromMessageRecord(targetMessage))
    }

    return targetMessageId
  }

  fun handleRemoteDelete(context: Context, envelope: Envelope, message: DataMessage, senderRecipientId: RecipientId, earlyMessageCacheEntry: EarlyMessageCacheEntry?): MessageId? {
    val delete = message.delete!!

    log(envelope.timestamp!!, "Remote delete for message ${delete.targetSentTimestamp}")

    val targetSentTimestamp: Long = delete.targetSentTimestamp!!
    val targetMessage: MessageRecord? = SignalDatabase.messages.getMessageFor(targetSentTimestamp, senderRecipientId)

    return if (targetMessage != null && MessageConstraintsUtil.isValidRemoteDeleteReceive(targetMessage, senderRecipientId, envelope.serverTimestamp!!)) {
      SignalDatabase.messages.markAsRemoteDelete(targetMessage)
      if (targetMessage.isStory()) {
        SignalDatabase.messages.deleteRemotelyDeletedStory(targetMessage.id)
      }

      AppDependencies.messageNotifier.updateNotification(context, ConversationId.fromMessageRecord(targetMessage))

      MessageId(targetMessage.id)
    } else if (targetMessage == null) {
      warn(envelope.timestamp!!, "[handleRemoteDelete] Could not find matching message! timestamp: $targetSentTimestamp  author: $senderRecipientId")
      if (earlyMessageCacheEntry != null) {
        AppDependencies.earlyMessageCache.store(senderRecipientId, targetSentTimestamp, earlyMessageCacheEntry)
        PushProcessEarlyMessagesJob.enqueue()
      }

      null
    } else {
      warn(envelope.timestamp!!, "[handleRemoteDelete] Invalid remote delete! deleteTime: ${envelope.serverTimestamp!!}, targetTime: ${targetMessage.serverTimestamp}, deleteAuthor: $senderRecipientId, targetAuthor: ${targetMessage.fromRecipient.id}")
      null
    }
  }

  /**
   * @param isActivatePaymentsRequest True if payments activation request message.
   * @param isPaymentsActivated       True if payments activated message.
   * @throws StorageFailedException
   */
  @Throws(StorageFailedException::class)
  private fun handlePaymentActivation(
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    senderRecipientId: RecipientId,
    receivedTime: Long,
    isActivatePaymentsRequest: Boolean,
    isPaymentsActivated: Boolean
  ): InsertResult? {
    log(envelope.timestamp!!, "Payment activation request: $isActivatePaymentsRequest activated: $isPaymentsActivated")
    Preconditions.checkArgument(isActivatePaymentsRequest || isPaymentsActivated)

    try {
      val mediaMessage = IncomingMessage(
        from = senderRecipientId,
        sentTimeMillis = envelope.timestamp!!,
        serverTimeMillis = envelope.serverTimestamp!!,
        receivedTimeMillis = receivedTime,
        expiresIn = message.expireTimerDuration.inWholeMilliseconds,
        isUnidentified = metadata.sealedSender,
        serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary),
        type = if (isActivatePaymentsRequest) MessageType.ACTIVATE_PAYMENTS_REQUEST else MessageType.PAYMENTS_ACTIVATED
      )

      return SignalDatabase.messages.insertMessageInbox(mediaMessage, -1).orNull()
    } catch (e: MmsException) {
      throw StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    }
    return null
  }

  @Throws(StorageFailedException::class)
  private fun handlePayment(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    senderRecipientId: RecipientId,
    receivedTime: Long
  ): InsertResult? {
    log(envelope.timestamp!!, "Payment message.")

    if (message.payment?.notification?.mobileCoin?.receipt == null) {
      warn(envelope.timestamp!!, "Ignoring payment message without notification")
      return null
    }

    val paymentNotification = message.payment!!.notification!!
    val uuid = UUID.randomUUID()
    val queue = "Payment_" + PushProcessMessageJob.getQueueName(senderRecipientId)

    try {
      SignalDatabase.payments.createIncomingPayment(
        uuid,
        senderRecipientId,
        message.timestamp!!,
        paymentNotification.note ?: "",
        Money.MobileCoin.ZERO,
        Money.MobileCoin.ZERO,
        paymentNotification.mobileCoin!!.receipt!!.toByteArray(),
        true
      )

      val mediaMessage = IncomingMessage(
        from = senderRecipientId,
        body = uuid.toString(),
        sentTimeMillis = envelope.timestamp!!,
        serverTimeMillis = envelope.serverTimestamp!!,
        receivedTimeMillis = receivedTime,
        expiresIn = message.expireTimerDuration.inWholeMilliseconds,
        isUnidentified = metadata.sealedSender,
        serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary),
        type = MessageType.PAYMENTS_NOTIFICATION
      )

      val insertResult: InsertResult? = SignalDatabase.messages.insertMessageInbox(mediaMessage, -1).orNull()
      if (insertResult != null) {
        AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
        return insertResult
      }
    } catch (e: PublicKeyConflictException) {
      warn(envelope.timestamp!!, "Ignoring payment with public key already in database")
    } catch (e: SerializationException) {
      warn(envelope.timestamp!!, "Ignoring payment with bad data.", e)
    } catch (e: MmsException) {
      throw StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    } finally {
      SignalDatabase.runPostSuccessfulTransaction {
        AppDependencies.jobManager
          .startChain(PaymentTransactionCheckJob(uuid, queue))
          .then(PaymentLedgerUpdateJob.updateLedger())
          .enqueue()
      }
    }

    return null
  }

  @Throws(StorageFailedException::class)
  private fun handleStoryReply(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    senderRecipient: Recipient,
    groupId: GroupId.V2?,
    receivedTime: Long
  ): InsertResult? {
    log(envelope.timestamp!!, "Story reply.")

    val storyContext: DataMessage.StoryContext = message.storyContext!!
    val authorServiceId: ServiceId = ACI.parseOrThrow(storyContext.authorAci, storyContext.authorAciBinary)
    val sentTimestamp: Long = if (storyContext.sentTimestamp != null) {
      storyContext.sentTimestamp!!
    } else {
      warn(envelope.timestamp!!, "Invalid story reply, missing sentTimestamp")
      return null
    }

    SignalDatabase.messages.beginTransaction()
    return try {
      val storyAuthorRecipientId = RecipientId.from(authorServiceId)
      val selfId = Recipient.self().id
      val parentStoryId: ParentStoryId
      var quoteModel: QuoteModel? = null
      var expiresInMillis: Duration = 0L.seconds
      var storyMessageId: MessageId? = null

      try {
        if (selfId == storyAuthorRecipientId) {
          storyMessageId = SignalDatabase.storySends.getStoryMessageFor(senderRecipient.id, sentTimestamp)
        }

        if (storyMessageId == null) {
          storyMessageId = SignalDatabase.messages.getStoryId(storyAuthorRecipientId, sentTimestamp)
        }

        val story: MmsMessageRecord = SignalDatabase.messages.getMessageRecord(storyMessageId.id) as MmsMessageRecord
        var threadRecipient: Recipient = SignalDatabase.threads.getRecipientForThreadId(story.threadId)!!
        val groupRecord: GroupRecord? = SignalDatabase.groups.getGroup(threadRecipient.id).orNull()
        val groupStory: Boolean = groupRecord?.isActive ?: false

        if (!groupStory) {
          threadRecipient = senderRecipient
        }

        handlePossibleExpirationUpdate(envelope, metadata, senderRecipient, threadRecipient, groupId, message.expireTimerDuration, message.expireTimerVersion, receivedTime)

        if (message.hasGroupContext) {
          parentStoryId = GroupReply(storyMessageId.id)
        } else if (groupStory || SignalDatabase.storySends.canReply(senderRecipient.id, sentTimestamp)) {
          parentStoryId = DirectReply(storyMessageId.id)

          var displayText = ""
          var bodyRanges: BodyRangeList? = null
          if (story.storyType.isTextStory) {
            displayText = story.body
            bodyRanges = story.messageRanges
          }

          quoteModel = QuoteModel(sentTimestamp, storyAuthorRecipientId, displayText, false, story.slideDeck.asAttachments().firstOrNull(), emptyList(), QuoteModel.Type.NORMAL, bodyRanges)
          expiresInMillis = message.expireTimerDuration
        } else {
          warn(envelope.timestamp!!, "Story has replies disabled. Dropping reply.")
          return null
        }
      } catch (e: NoSuchMessageException) {
        warn(envelope.timestamp!!, "Couldn't find story for reply.", e)
        return null
      }

      val bodyRanges: BodyRangeList? = message.bodyRanges.filter { Util.allAreNull(it.mentionAci, it.mentionAciBinary) }.toList().toBodyRangeList()

      val mediaMessage = IncomingMessage(
        type = MessageType.NORMAL,
        from = senderRecipient.id,
        sentTimeMillis = envelope.timestamp!!,
        serverTimeMillis = envelope.serverTimestamp!!,
        receivedTimeMillis = System.currentTimeMillis(),
        parentStoryId = parentStoryId,
        expiresIn = expiresInMillis.inWholeMilliseconds,
        isUnidentified = metadata.sealedSender,
        body = message.body,
        groupId = groupId,
        quote = quoteModel,
        mentions = getMentions(message.bodyRanges),
        serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary),
        messageRanges = bodyRanges
      )

      val insertResult: InsertResult? = SignalDatabase.messages.insertMessageInbox(mediaMessage, -1).orNull()

      if (insertResult != null) {
        SignalDatabase.messages.setTransactionSuccessful()

        if (parentStoryId.isGroupReply()) {
          AppDependencies.messageNotifier.updateNotification(context, ConversationId.fromThreadAndReply(insertResult.threadId, parentStoryId as GroupReply))
        } else {
          AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
          TrimThreadJob.enqueueAsync(insertResult.threadId)
        }

        if (parentStoryId.isDirectReply()) {
          insertResult
        } else {
          null
        }
      } else {
        warn(envelope.timestamp!!, "Failed to insert story reply.")
        null
      }
    } catch (e: MmsException) {
      throw StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    } finally {
      SignalDatabase.messages.endTransaction()
    }
  }

  @Throws(StorageFailedException::class)
  private fun handleGiftMessage(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    senderRecipient: Recipient,
    threadRecipientId: RecipientId,
    receivedTime: Long
  ): InsertResult? {
    log(message.timestamp!!, "Gift message.")

    val giftBadge: DataMessage.GiftBadge = message.giftBadge!!
    check(giftBadge.receiptCredentialPresentation != null)

    notifyTypingStoppedFromIncomingMessage(context, senderRecipient, threadRecipientId, metadata.sourceDeviceId)

    val token = ReceiptCredentialPresentation(giftBadge.receiptCredentialPresentation!!.toByteArray()).serialize()
    val dbGiftBadge = GiftBadge.Builder()
      .redemptionToken(token.toByteString())
      .redemptionState(GiftBadge.RedemptionState.PENDING)
      .build()

    val insertResult: InsertResult? = try {
      val mediaMessage = IncomingMessage(
        type = MessageType.NORMAL,
        from = senderRecipient.id,
        sentTimeMillis = envelope.timestamp!!,
        serverTimeMillis = envelope.serverTimestamp!!,
        receivedTimeMillis = receivedTime,
        expiresIn = message.expireTimerDuration.inWholeMilliseconds,
        isUnidentified = metadata.sealedSender,
        body = Base64.encodeWithPadding(dbGiftBadge.encode()),
        serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary),
        giftBadge = dbGiftBadge
      )

      SignalDatabase.messages.insertMessageInbox(mediaMessage, -1).orNull()
    } catch (e: MmsException) {
      throw StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    }

    return if (insertResult != null) {
      AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
      TrimThreadJob.enqueueAsync(insertResult.threadId)
      insertResult
    } else {
      null
    }
  }

  @Throws(StorageFailedException::class)
  private fun handleMediaMessage(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    senderRecipient: Recipient,
    threadRecipient: Recipient,
    groupId: GroupId.V2?,
    receivedTime: Long,
    localMetrics: SignalLocalMetrics.MessageReceive?
  ): InsertResult? {
    log(envelope.timestamp!!, "Media message.")

    notifyTypingStoppedFromIncomingMessage(context, senderRecipient, threadRecipient.id, metadata.sourceDeviceId)

    val insertResult: InsertResult?

    SignalDatabase.messages.beginTransaction()
    try {
      val quoteModel: QuoteModel? = getValidatedQuote(context, envelope.timestamp!!, message, senderRecipient, threadRecipient)
      val contacts: List<Contact> = getContacts(message)
      val linkPreviews: List<LinkPreview> = getLinkPreviews(message.preview, message.body ?: "", false)
      val mentions: List<Mention> = getMentions(message.bodyRanges.take(BODY_RANGE_PROCESSING_LIMIT))
      val sticker: Attachment? = getStickerAttachment(envelope.timestamp!!, message)
      val attachments: List<Attachment> = message.attachments.toPointersWithinLimit()
      val messageRanges: BodyRangeList? = if (message.bodyRanges.isNotEmpty()) message.bodyRanges.asSequence().take(BODY_RANGE_PROCESSING_LIMIT).filter { Util.allAreNull(it.mentionAci, it.mentionAciBinary) }.toList().toBodyRangeList() else null

      handlePossibleExpirationUpdate(envelope, metadata, senderRecipient, threadRecipient, groupId, message.expireTimerDuration, message.expireTimerVersion, receivedTime)

      val mediaMessage = IncomingMessage(
        type = MessageType.NORMAL,
        from = senderRecipient.id,
        sentTimeMillis = envelope.timestamp!!,
        serverTimeMillis = envelope.serverTimestamp!!,
        receivedTimeMillis = receivedTime,
        expiresIn = message.expireTimerDuration.inWholeMilliseconds,
        isViewOnce = message.isViewOnce == true,
        isUnidentified = metadata.sealedSender,
        body = message.body?.ifEmpty { null },
        groupId = groupId,
        attachments = attachments + if (sticker != null) listOf(sticker) else emptyList(),
        quote = quoteModel,
        sharedContacts = contacts,
        linkPreviews = linkPreviews,
        mentions = mentions,
        serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary),
        messageRanges = messageRanges
      )

      insertResult = SignalDatabase.messages.insertMessageInbox(mediaMessage, -1).orNull()
      if (insertResult != null) {
        SignalDatabase.messages.setTransactionSuccessful()
      }
    } catch (e: MmsException) {
      throw StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    } finally {
      SignalDatabase.messages.endTransaction()
    }
    localMetrics?.onInsertedMediaMessage()

    return if (insertResult != null) {
      SignalDatabase.runPostSuccessfulTransaction {
        if (insertResult.insertedAttachments != null) {
          val downloadJobs: List<AttachmentDownloadJob> = insertResult.insertedAttachments.mapNotNull { (attachment, attachmentId) ->
            if (attachment.isSticker) {
              if (attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE) {
                AttachmentDownloadJob(insertResult.messageId, attachmentId, true)
              } else {
                null
              }
            } else {
              AttachmentDownloadJob(insertResult.messageId, attachmentId, false)
            }
          }
          AppDependencies.jobManager.addAll(downloadJobs)
        }

        AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
        TrimThreadJob.enqueueAsync(insertResult.threadId)

        if (message.isViewOnce == true) {
          AppDependencies.viewOnceMessageManager.scheduleIfNecessary()
        }
      }

      insertResult
    } else {
      null
    }
  }

  @Throws(StorageFailedException::class)
  private fun handleTextMessage(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    senderRecipient: Recipient,
    threadRecipient: Recipient,
    groupId: GroupId.V2?,
    receivedTime: Long,
    localMetrics: SignalLocalMetrics.MessageReceive?
  ): InsertResult? {
    log(envelope.timestamp!!, "Text message.")

    val body = message.body ?: ""

    handlePossibleExpirationUpdate(envelope, metadata, senderRecipient, threadRecipient, groupId, message.expireTimerDuration, message.expireTimerVersion, receivedTime)

    notifyTypingStoppedFromIncomingMessage(context, senderRecipient, threadRecipient.id, metadata.sourceDeviceId)

    val textMessage = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderRecipient.id,
      sentTimeMillis = envelope.timestamp!!,
      serverTimeMillis = envelope.serverTimestamp!!,
      receivedTimeMillis = receivedTime,
      body = body,
      groupId = groupId,
      expiresIn = message.expireTimerDuration.inWholeMilliseconds,
      isUnidentified = metadata.sealedSender,
      serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary)
    )

    val insertResult: InsertResult? = SignalDatabase.messages.insertMessageInbox(textMessage).orNull()
    localMetrics?.onInsertedTextMessage()

    return if (insertResult != null) {
      AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
      insertResult
    } else {
      null
    }
  }

  fun handleGroupCallUpdateMessage(
    envelope: Envelope,
    message: DataMessage,
    senderRecipientId: RecipientId,
    groupId: GroupId.V2?
  ) {
    log(envelope.timestamp!!, "Group call update message.")

    val groupCallUpdate: DataMessage.GroupCallUpdate = message.groupCallUpdate!!

    if (groupId == null) {
      warn(envelope.timestamp!!, "Invalid group for group call update message")
      return
    }

    val groupRecipientId = SignalDatabase.recipients.getOrInsertFromPossiblyMigratedGroupId(groupId)

    GroupCallPeekJob.enqueue(
      GroupCallPeekJobData(
        groupRecipientId.toLong(),
        senderRecipientId.toLong(),
        envelope.serverTimestamp!!
      )
    )
  }

  fun handlePollCreate(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    senderRecipient: Recipient,
    threadRecipient: Recipient,
    groupId: GroupId.V2?,
    receivedTime: Long
  ): InsertResult? {
    if (!RemoteConfig.receivePolls) {
      log(envelope.timestamp!!, "Poll creation not allowed due to remote config.")
      return null
    }

    log(envelope.timestamp!!, "Handle poll creation")
    val poll: DataMessage.PollCreate = message.pollCreate!!

    handlePossibleExpirationUpdate(envelope, metadata, senderRecipient, threadRecipient, groupId, message.expireTimerDuration, message.expireTimerVersion, receivedTime)

    if (groupId == null) {
      warn(envelope.timestamp!!, "[handlePollCreate] Polls can only be sent to groups. author: $senderRecipient")
      return null
    }

    val groupRecord = SignalDatabase.groups.getGroup(groupId).orNull()
    if (groupRecord == null || !groupRecord.members.contains(senderRecipient.id)) {
      warn(envelope.timestamp!!, "[handlePollCreate] Poll author is not in the group. author $senderRecipient")
      return null
    }

    if (poll.question == null || poll.question!!.isEmpty() || poll.question!!.length > POLL_CHARACTER_LIMIT) {
      warn(envelope.timestamp!!, "[handlePollCreate] Poll question is invalid.")
      return null
    }

    if (poll.options.isEmpty() || poll.options.size > POLL_OPTIONS_LIMIT || poll.options.any { it.isEmpty() || it.length > POLL_CHARACTER_LIMIT }) {
      warn(envelope.timestamp!!, "[handlePollCreate] Poll option is invalid.")
      return null
    }

    val pollMessage = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderRecipient.id,
      sentTimeMillis = envelope.timestamp!!,
      serverTimeMillis = envelope.serverTimestamp!!,
      receivedTimeMillis = receivedTime,
      groupId = groupId,
      expiresIn = message.expireTimerDuration.inWholeMilliseconds,
      isUnidentified = metadata.sealedSender,
      serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary),
      poll = Poll(
        question = poll.question!!,
        allowMultipleVotes = poll.allowMultiple!!,
        pollOptions = poll.options,
        authorId = senderRecipient.id.toLong()
      ),
      body = poll.question!!
    )

    val insertResult: InsertResult? = SignalDatabase.messages.insertMessageInbox(pollMessage).orNull()
    return if (insertResult != null) {
      AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
      insertResult
    } else {
      null
    }
  }

  fun handlePollTerminate(
    context: Context,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    senderRecipient: Recipient,
    earlyMessageCacheEntry: EarlyMessageCacheEntry? = null,
    threadRecipient: Recipient,
    groupId: GroupId.V2?,
    receivedTime: Long
  ): InsertResult? {
    if (!RemoteConfig.receivePolls) {
      log(envelope.timestamp!!, "Poll terminate not allowed due to remote config.")
      return null
    }

    val pollTerminate: DataMessage.PollTerminate = message.pollTerminate!!
    val targetSentTimestamp = pollTerminate.targetSentTimestamp!!

    log(envelope.timestamp!!, "Handle poll termination for poll $targetSentTimestamp")

    handlePossibleExpirationUpdate(envelope, metadata, senderRecipient, threadRecipient, groupId, message.expireTimerDuration, message.expireTimerVersion, receivedTime)

    val messageId = handlePollValidation(envelope = envelope, targetSentTimestamp = targetSentTimestamp, senderRecipient = senderRecipient, earlyMessageCacheEntry = earlyMessageCacheEntry, targetAuthor = senderRecipient)
    if (messageId == null) {
      return null
    }

    val poll = SignalDatabase.polls.getPoll(messageId.id)
    if (poll == null) {
      warn(envelope.timestamp!!, "[handlePollTerminate] Poll was not found. timestamp: $targetSentTimestamp  author: ${senderRecipient.id}")
      return null
    }

    val pollMessage = IncomingMessage(
      type = MessageType.POLL_TERMINATE,
      from = senderRecipient.id,
      sentTimeMillis = envelope.timestamp!!,
      serverTimeMillis = envelope.serverTimestamp!!,
      receivedTimeMillis = receivedTime,
      groupId = groupId,
      expiresIn = message.expireTimerDuration.inWholeMilliseconds,
      isUnidentified = metadata.sealedSender,
      serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary),
      messageExtras = MessageExtras(pollTerminate = PollTerminate(poll.question, poll.messageId, targetSentTimestamp))
    )

    val insertResult: InsertResult? = SignalDatabase.messages.insertMessageInbox(pollMessage).orNull()

    return if (insertResult != null) {
      AppDependencies.messageNotifier.updateNotification(context, ConversationId.forConversation(insertResult.threadId))
      insertResult
    } else {
      null
    }
  }

  fun handlePollVote(
    context: Context,
    envelope: Envelope,
    message: DataMessage,
    senderRecipient: Recipient,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?
  ): MessageId? {
    if (!RemoteConfig.receivePolls) {
      log(envelope.timestamp!!, "Poll vote not allowed due to remote config.")
      return null
    }

    val pollVote: DataMessage.PollVote = message.pollVote!!
    val targetSentTimestamp = pollVote.targetSentTimestamp!!

    log(envelope.timestamp!!, "Handle poll vote for poll $targetSentTimestamp")

    val targetAuthorServiceId: ServiceId = ServiceId.parseOrThrow(pollVote.targetAuthorAciBinary!!)
    if (targetAuthorServiceId.isUnknown) {
      warn(envelope.timestamp!!, "[handlePollVote] Vote was to an unknown UUID! Ignoring the message.")
      return null
    }

    val messageId = handlePollValidation(envelope, targetSentTimestamp, senderRecipient, earlyMessageCacheEntry, Recipient.externalPush(targetAuthorServiceId))
    if (messageId == null) {
      return null
    }

    val targetMessage = SignalDatabase.messages.getMessageRecord(messageId.id)
    val pollId = SignalDatabase.polls.getPollId(messageId.id)
    if (pollId == null) {
      warn(envelope.timestamp!!, "[handlePollVote] Poll was not found. timestamp: $targetSentTimestamp  author: ${senderRecipient.id}")
      return null
    }

    val existingVoteCount = SignalDatabase.polls.getCurrentPollVoteCount(pollId, senderRecipient.id.toLong())
    val currentVoteCount = pollVote.voteCount?.toLong() ?: 0
    if (currentVoteCount <= existingVoteCount) {
      warn(envelope.timestamp!!, "[handlePollVote] Incoming vote count was not higher. timestamp: $targetSentTimestamp author: ${senderRecipient.id}")
      return null
    }

    val allOptionIds = SignalDatabase.polls.getPollOptionIds(pollId)
    if (pollVote.optionIndexes.any { it < 0 || it >= allOptionIds.size }) {
      warn(envelope.timestamp!!, "[handlePollVote] Invalid option indexes. timestamp: $targetSentTimestamp author: ${senderRecipient.id}")
      return null
    }

    if (!SignalDatabase.polls.canAllowMultipleVotes(pollId) && pollVote.optionIndexes.size > 1) {
      warn(envelope.timestamp!!, "[handlePollVote] Can not vote multiple times. timestamp: $targetSentTimestamp author: ${senderRecipient.id}")
      return null
    }

    if (SignalDatabase.polls.hasEnded(pollId)) {
      warn(envelope.timestamp!!, "[handlePollVote] Poll has already ended. timestamp: $targetSentTimestamp author: ${senderRecipient.id}")
      return null
    }

    SignalDatabase.polls.insertVotes(
      pollId = pollId,
      pollOptionIds = pollVote.optionIndexes.map { index -> allOptionIds[index] },
      voterId = senderRecipient.id.toLong(),
      voteCount = pollVote.voteCount?.toLong() ?: 0,
      messageId = messageId
    )

    AppDependencies.messageNotifier.updateNotification(context, ConversationId.fromMessageRecord(targetMessage))

    return messageId
  }

  fun notifyTypingStoppedFromIncomingMessage(context: Context, senderRecipient: Recipient, threadRecipientId: RecipientId, device: Int) {
    val threadId = SignalDatabase.threads.getThreadIdIfExistsFor(threadRecipientId)

    if (threadId > 0 && TextSecurePreferences.isTypingIndicatorsEnabled(context)) {
      debug("Typing stopped on thread $threadId due to an incoming message.")
      AppDependencies.typingStatusRepository.onTypingStopped(threadId, senderRecipient, device, true)
    }
  }

  fun getMentions(mentionBodyRanges: List<BodyRange>): List<Mention> {
    return mentionBodyRanges
      .filter { Util.anyNotNull(it.mentionAci, it.mentionAciBinary) && it.start != null && it.length != null }
      .mapNotNull {
        val aci = ACI.parseOrNull(it.mentionAci, it.mentionAciBinary)

        if (aci != null && !aci.isUnknown) {
          val id = Recipient.externalPush(aci).id
          Mention(id, it.start!!, it.length!!)
        } else {
          null
        }
      }
  }

  private fun insertPlaceholder(sender: RecipientId, timestamp: Long, groupId: GroupId?): InsertResult? {
    val textMessage = IncomingMessage(
      type = MessageType.NORMAL,
      from = sender,
      sentTimeMillis = timestamp,
      serverTimeMillis = -1,
      receivedTimeMillis = System.currentTimeMillis(),
      body = "",
      groupId = groupId
    )

    return SignalDatabase.messages.insertMessageInbox(textMessage).orNull()
  }

  fun getValidatedQuote(context: Context, timestamp: Long, message: DataMessage, senderRecipient: Recipient, threadRecipient: Recipient): QuoteModel? {
    val quote: DataMessage.Quote = message.quote ?: return null

    if (quote.id == null) {
      warn(timestamp, "Received quote without an ID! Ignoring...")
      return null
    }

    val authorId = Recipient.externalPush(ACI.parseOrThrow(quote.authorAci, quote.authorAciBinary)).id
    var quotedMessage = SignalDatabase.messages.getMessageFor(quote.id!!, authorId) as? MmsMessageRecord

    if (quotedMessage != null && isSenderValid(quotedMessage, timestamp, senderRecipient, threadRecipient) && !quotedMessage.isRemoteDelete) {
      log(timestamp, "Found matching message record...")

      var thumbnailAttachment: Attachment? = null
      val targetMessageAttachments = SignalDatabase.attachments.getAttachmentsForMessage(quotedMessage.id)
      val mentions: List<Mention> = SignalDatabase.mentions.getMentionsForMessage(quotedMessage.id)

      // We want our thumbnail attachment to be the first "thumbnailable" item from the target message.
      // That means we want to pick the earliest image/video that has data.
      thumbnailAttachment = targetMessageAttachments
        .sortedBy { it.displayOrder }
        .sortedBy {
          if (MediaUtil.isImageType(it.contentType) || MediaUtil.isVideoType(it.contentType)) {
            0
          } else {
            1
          }
        }
        .firstOrNull { it.hasData }

      if (quotedMessage.isViewOnce) {
        thumbnailAttachment = TombstoneAttachment.forQuote()
      } else if (thumbnailAttachment == null) {
        thumbnailAttachment = quotedMessage
          .linkPreviews
          .filter { it.thumbnail.isPresent }
          .map { it.thumbnail.get() }
          .firstOrNull()
      }

      if (quotedMessage.isPaymentNotification) {
        quotedMessage = SignalDatabase.payments.updateMessageWithPayment(quotedMessage) as MmsMessageRecord
      }

      val body = if (quotedMessage.isPaymentNotification) quotedMessage.getDisplayBody(context).toString() else quotedMessage.body

      return QuoteModel(
        id = quote.id!!,
        author = authorId,
        text = body,
        isOriginalMissing = false,
        attachment = thumbnailAttachment,
        mentions = mentions,
        type = QuoteModel.Type.fromProto(quote.type),
        bodyRanges = quotedMessage.messageRanges
      )
    } else if (quotedMessage != null && quotedMessage.isRemoteDelete) {
      warn(timestamp, "Found the target for the quote, but it's flagged as remotely deleted.")
    }

    warn(timestamp, "Didn't find matching message record...")
    return QuoteModel(
      id = quote.id!!,
      author = authorId,
      text = quote.text ?: "",
      isOriginalMissing = true,
      attachment = quote.attachments.firstNotNullOfOrNull { PointerAttachment.forPointer(it).orNull() },
      mentions = getMentions(quote.bodyRanges),
      type = QuoteModel.Type.fromProto(quote.type),
      bodyRanges = quote.bodyRanges.filter { it.mentionAci == null }.toBodyRangeList()
    )
  }

  private fun isSenderValid(quotedMessage: MmsMessageRecord, timestamp: Long, senderRecipient: Recipient, threadRecipient: Recipient): Boolean {
    if (threadRecipient.isGroup) {
      val groupRecord = SignalDatabase.groups.getGroup(threadRecipient.id).orNull()
      if (groupRecord != null && !groupRecord.members.contains(senderRecipient.id)) {
        warn(timestamp, "Sender is not in the group! Thread: ${quotedMessage.threadId} Sender: ${senderRecipient.id}")
        return false
      }
    } else if (senderRecipient.id != threadRecipient.id) {
      warn(timestamp, "Sender is not a part of the 1:1 thread! Thread: ${quotedMessage.threadId} Sender: ${senderRecipient.id}")
      return false
    }

    return true
  }

  /**
   * When ending or voting on a poll, checks validity of the message. Specifically
   * that the message exists, was only sent to a group, and the sender
   * is a member of the group. Returns the messageId of the poll if valid, null otherwise.
   */
  private fun handlePollValidation(
    envelope: Envelope,
    targetSentTimestamp: Long,
    senderRecipient: Recipient,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?,
    targetAuthor: Recipient
  ): MessageId? {
    val targetMessage = SignalDatabase.messages.getMessageFor(targetSentTimestamp, targetAuthor.id)
    if (targetMessage == null) {
      warn(envelope.timestamp!!, "[handlePollValidation] Could not find matching message! Putting it in the early message cache. timestamp: $targetSentTimestamp  author: ${targetAuthor.id}")
      if (earlyMessageCacheEntry != null) {
        AppDependencies.earlyMessageCache.store(senderRecipient.id, targetSentTimestamp, earlyMessageCacheEntry)
        PushProcessEarlyMessagesJob.enqueue()
      }
      return null
    }

    if (targetMessage.isRemoteDelete) {
      warn(envelope.timestamp!!, "[handlePollValidation] Found a matching message, but it's flagged as remotely deleted. timestamp: $targetSentTimestamp  author: ${targetAuthor.id}")
      return null
    }

    val targetThread = SignalDatabase.threads.getThreadRecord(targetMessage.threadId)
    if (targetThread == null) {
      warn(envelope.timestamp!!, "[handlePollValidation] Could not find a thread for the message. timestamp: $targetSentTimestamp  author: ${targetAuthor.id}")
      return null
    }

    val groupRecord = SignalDatabase.groups.getGroup(targetThread.recipient.id).orNull()
    if (groupRecord == null) {
      warn(envelope.timestamp!!, "[handlePollValidation] Target thread needs to be a group. timestamp: $targetSentTimestamp  author: ${targetAuthor.id}")
      return null
    }

    if (!groupRecord.members.contains(senderRecipient.id)) {
      warn(envelope.timestamp!!, "[handlePollValidation] Sender is not in the group. timestamp: $targetSentTimestamp author: ${targetAuthor.id}")
      return null
    }

    return MessageId(targetMessage.id)
  }

  fun getContacts(message: DataMessage): List<Contact> {
    return message.contact.map { ContactModelMapper.remoteToLocal(it) }
  }

  fun getLinkPreviews(previews: List<Preview>, body: String, isStoryEmbed: Boolean): List<LinkPreview> {
    if (previews.isEmpty()) {
      return emptyList()
    }

    val urlsInMessage = LinkPreviewUtil.findValidPreviewUrls(body)

    return previews
      .mapNotNull { preview ->
        val thumbnail: Attachment? = preview.image?.toPointer()
        val url: Optional<String> = preview.url.toOptional()
        val title: Optional<String> = preview.title.toOptional()
        val description: Optional<String> = preview.description.toOptional()
        val hasTitle = !TextUtils.isEmpty(title.orElse(""))
        val presentInBody = url.isPresent && urlsInMessage.containsUrl(url.get())
        val validDomain = url.isPresent && LinkUtil.isValidPreviewUrl(url.get())
        val isForCallLink = url.isPresent && CallLinks.isCallLink(url.get())

        if ((hasTitle || isForCallLink || isStoryEmbed) && (presentInBody || isStoryEmbed) && validDomain) {
          val linkPreview = LinkPreview(url.get(), title.orElse(""), description.orElse(""), preview.date ?: 0, thumbnail.toOptional())
          linkPreview
        } else {
          warn(String.format("Discarding an invalid link preview. hasTitle: %b presentInBody: %b isStoryEmbed: %b validDomain: %b", hasTitle, presentInBody, isStoryEmbed, validDomain))
          null
        }
      }
  }

  fun getStickerAttachment(timestamp: Long, message: DataMessage): Attachment? {
    val sticker = message.sticker ?: return null

    if (sticker.packId == null || sticker.packKey == null || sticker.stickerId == null || sticker.data_ == null) {
      warn(timestamp, "Malformed sticker!")
      return null
    }

    val packId: String = Hex.toStringCondensed(sticker.packId!!.toByteArray())
    val packKey: String = Hex.toStringCondensed(sticker.packKey!!.toByteArray())
    val stickerId: Int = sticker.stickerId!!
    val emoji: String? = sticker.emoji

    val stickerLocator = StickerLocator(packId, packKey, stickerId, emoji)
    val stickerRecord: StickerRecord? = SignalDatabase.stickers.getSticker(stickerLocator.packId, stickerLocator.stickerId, false)

    return if (stickerRecord != null) {
      LocalStickerAttachment(stickerRecord, stickerLocator)
    } else {
      sticker.data_!!.toPointer(stickerLocator)
    }
  }
}

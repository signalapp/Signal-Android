package org.thoughtcrime.securesms.messages

import android.content.Context
import com.google.protobuf.ByteString
import com.mobilecoin.lib.exceptions.SerializationException
import org.signal.core.util.Hex
import org.signal.core.util.orNull
import org.signal.libsignal.protocol.util.Pair
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.TombstoneAttachment
import org.thoughtcrime.securesms.components.emoji.EmojiUtil
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.crypto.SecurityEvent
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.PaymentMetaDataUtil
import org.thoughtcrime.securesms.database.SentStorySyncManifest
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.ParentStoryId
import org.thoughtcrime.securesms.database.model.ParentStoryId.DirectReply
import org.thoughtcrime.securesms.database.model.ParentStoryId.GroupReply
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.toBodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.BadGroupIdException
import org.thoughtcrime.securesms.groups.GroupChangeBusyException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.MultiDeviceBlockedUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceContactSyncJob
import org.thoughtcrime.securesms.jobs.MultiDeviceContactUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceGroupUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceKeysUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDevicePniIdentityUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceStickerPackSyncJob
import org.thoughtcrime.securesms.jobs.PushProcessEarlyMessagesJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.messages.MessageContentProcessor.StorageFailedException
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2.Companion.log
import org.thoughtcrime.securesms.messages.MessageContentProcessorV2.Companion.warn
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupId
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.groupMasterKey
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.hasGroupContext
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.hasRemoteDelete
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isEmptyGroupV2Message
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isEndSession
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isExpirationUpdate
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isGroupV2Update
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isMediaMessage
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.isUnidentified
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.serviceIdsToUnidentifiedStatus
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toMobileCoinMoney
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toPointer
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toPointers
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.toSignalServiceAttachmentPointer
import org.thoughtcrime.securesms.messages.SignalServiceProtoUtil.type
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage.Companion.endSessionMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage.Companion.expirationUpdateMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage.Companion.text
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.notifications.MarkReadReceiver
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress
import org.thoughtcrime.securesms.ratelimit.RateLimitUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.EarlyMessageCacheEntry
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.IdentityUtil
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.storage.StorageKey
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.StoryMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.Blocked
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.Configuration
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.FetchLatest
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.MessageRequestResponse
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.Read
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.Request
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.Sent
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.StickerPackOperation
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage.ViewOnceOpen
import java.io.IOException
import java.util.Optional
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

object SyncMessageProcessor {

  fun process(
    context: Context,
    senderRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?
  ) {
    val syncMessage = content.syncMessage

    when {
      syncMessage.hasSent() -> handleSynchronizeSentMessage(context, envelope, content, metadata, syncMessage.sent, senderRecipient, earlyMessageCacheEntry)
      syncMessage.hasRequest() -> handleSynchronizeRequestMessage(context, syncMessage.request, envelope.timestamp)
      syncMessage.readList.isNotEmpty() -> handleSynchronizeReadMessage(context, syncMessage.readList, envelope.timestamp, earlyMessageCacheEntry)
      syncMessage.viewedList.isNotEmpty() -> handleSynchronizeViewedMessage(context, syncMessage.viewedList, envelope.timestamp)
      syncMessage.hasViewOnceOpen() -> handleSynchronizeViewOnceOpenMessage(context, syncMessage.viewOnceOpen, envelope.timestamp, earlyMessageCacheEntry)
      syncMessage.hasVerified() -> handleSynchronizeVerifiedMessage(context, syncMessage.verified)
      syncMessage.stickerPackOperationList.isNotEmpty() -> handleSynchronizeStickerPackOperation(syncMessage.stickerPackOperationList, envelope.timestamp)
      syncMessage.hasConfiguration() -> handleSynchronizeConfigurationMessage(context, syncMessage.configuration, envelope.timestamp)
      syncMessage.hasBlocked() -> handleSynchronizeBlockedListMessage(syncMessage.blocked)
      syncMessage.hasFetchLatest() && syncMessage.fetchLatest.hasType() -> handleSynchronizeFetchMessage(syncMessage.fetchLatest.type, envelope.timestamp)
      syncMessage.hasMessageRequestResponse() -> handleSynchronizeMessageRequestResponse(syncMessage.messageRequestResponse, envelope.timestamp)
      syncMessage.hasOutgoingPayment() -> handleSynchronizeOutgoingPayment(syncMessage.outgoingPayment, envelope.timestamp)
      syncMessage.hasKeys() && syncMessage.keys.hasStorageService() -> handleSynchronizeKeys(syncMessage.keys.storageService, envelope.timestamp)
      syncMessage.hasContacts() -> handleSynchronizeContacts(syncMessage.contacts, envelope.timestamp)
      syncMessage.hasCallEvent() -> handleSynchronizeCallEvent(syncMessage.callEvent, envelope.timestamp)
      else -> warn(envelope.timestamp, "Contains no known sync types...")
    }
  }

  @Throws(StorageFailedException::class, BadGroupIdException::class, IOException::class, GroupChangeBusyException::class)
  private fun handleSynchronizeSentMessage(
    context: Context,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    sent: Sent,
    senderRecipient: Recipient,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?
  ) {
    log(envelope.timestamp, "Processing sent transcript for message with ID ${sent.timestamp}")

    try {
      if (sent.hasStoryMessage() || sent.storyMessageRecipientsList.isNotEmpty()) {
        handleSynchronizeSentStoryMessage(envelope, sent)
        return
      }

      val dataMessage = sent.message
      val groupId: GroupId.V2? = if (dataMessage.hasGroupContext) GroupId.v2(dataMessage.groupV2.groupMasterKey) else null

      if (groupId != null) {
        if (MessageContentProcessorV2.handleGv2PreProcessing(context, envelope.timestamp, content, metadata, groupId, dataMessage.groupV2, senderRecipient)) {
          return
        }
      }

      var threadId: Long = -1
      when {
        sent.isRecipientUpdate -> handleGroupRecipientUpdate(sent, envelope.timestamp)
        dataMessage.isEndSession -> threadId = handleSynchronizeSentEndSessionMessage(context, sent, envelope.timestamp)
        dataMessage.isGroupV2Update -> {
          handleSynchronizeSentGv2Update(context, envelope, sent)
          threadId = SignalDatabase.threads.getOrCreateThreadIdFor(getSyncMessageDestination(sent))
        }
        dataMessage.hasGroupCallUpdate() -> DataMessageProcessor.handleGroupCallUpdateMessage(envelope, dataMessage, senderRecipient.id, groupId)
        dataMessage.isEmptyGroupV2Message -> warn(envelope.timestamp, "Empty GV2 message! Doing nothing.")
        dataMessage.isExpirationUpdate -> threadId = handleSynchronizeSentExpirationUpdate(sent)
        dataMessage.hasStoryContext() -> threadId = handleSynchronizeSentStoryReply(sent, envelope.timestamp)
        dataMessage.hasReaction() -> {
          DataMessageProcessor.handleReaction(context, envelope, dataMessage, senderRecipient.id, earlyMessageCacheEntry)
          threadId = SignalDatabase.threads.getOrCreateThreadIdFor(getSyncMessageDestination(sent))
        }
        dataMessage.hasRemoteDelete -> DataMessageProcessor.handleRemoteDelete(context, envelope, dataMessage, senderRecipient.id, earlyMessageCacheEntry)
        dataMessage.isMediaMessage -> threadId = handleSynchronizeSentMediaMessage(context, sent, envelope.timestamp)
        else -> threadId = handleSynchronizeSentTextMessage(sent, envelope.timestamp)
      }

      if (groupId != null && SignalDatabase.groups.isUnknownGroup(groupId)) {
        DataMessageProcessor.handleUnknownGroupMessage(envelope.timestamp, dataMessage.groupV2)
      }

      if (dataMessage.hasProfileKey()) {
        val recipient: Recipient = getSyncMessageDestination(sent)
        if (!recipient.isSystemContact && !recipient.isProfileSharing) {
          SignalDatabase.recipients.setProfileSharing(recipient.id, true)
        }
      }

      if (threadId != -1L) {
        SignalDatabase.threads.setRead(threadId, true)
        ApplicationDependencies.getMessageNotifier().updateNotification(context)
      }

      if (SignalStore.rateLimit().needsRecaptcha()) {
        log(envelope.timestamp, "Got a sent transcript while in reCAPTCHA mode. Assuming we're good to message again.")
        RateLimitUtil.retryAllRateLimitedMessages(context)
      }

      ApplicationDependencies.getMessageNotifier().setLastDesktopActivityTimestamp(sent.timestamp)
    } catch (e: MmsException) {
      throw StorageFailedException(e, metadata.sourceServiceId.toString(), metadata.sourceDeviceId)
    }
  }

  private fun getSyncMessageDestination(message: Sent): Recipient {
    return if (message.message.hasGroupContext) {
      Recipient.externalPossiblyMigratedGroup(GroupId.v2(message.message.groupV2.groupMasterKey))
    } else {
      Recipient.externalPush(ServiceId.parseOrThrow(message.destinationUuid))
    }
  }

  @Throws(MmsException::class)
  private fun handleSynchronizeSentStoryMessage(envelope: Envelope, sent: Sent) {
    log(envelope.timestamp, "Synchronize sent story message for " + sent.timestamp)

    val manifest = SentStorySyncManifest.fromRecipientsSet(sent.storyMessageRecipientsList)

    if (sent.isRecipientUpdate) {
      log(envelope.timestamp, "Processing recipient update for story message and exiting...")
      SignalDatabase.storySends.applySentStoryManifest(manifest, sent.timestamp)
      return
    }

    val storyMessage: StoryMessage = sent.storyMessage
    val distributionIds: Set<DistributionId> = manifest.getDistributionIdSet()
    val groupId: GroupId.V2? = storyMessage.group.groupId
    val textStoryBody: String? = StoryMessageProcessor.serializeTextAttachment(storyMessage)
    val bodyRanges: BodyRangeList? = storyMessage.bodyRangesList.toBodyRangeList()
    val storyType: StoryType = storyMessage.type

    val linkPreviews: List<LinkPreview> = DataMessageProcessor.getLinkPreviews(
      previews = if (storyMessage.textAttachment.hasPreview()) listOf(storyMessage.textAttachment.preview) else emptyList(),
      body = "",
      isStoryEmbed = true
    )

    val attachments: List<Attachment> = if (storyMessage.hasFileAttachment()) listOfNotNull(storyMessage.fileAttachment.toPointer()) else emptyList()

    for (distributionId in distributionIds) {
      val distributionRecipientId = SignalDatabase.distributionLists.getOrCreateByDistributionId(distributionId, manifest)
      val distributionListRecipient = Recipient.resolved(distributionRecipientId)
      insertSentStoryMessage(sent, distributionListRecipient, null, textStoryBody, attachments, sent.timestamp, storyType, linkPreviews, bodyRanges)
    }

    if (groupId != null) {
      val groupRecipient: Optional<RecipientId> = SignalDatabase.recipients.getByGroupId(groupId)
      if (groupRecipient.isPresent) {
        insertSentStoryMessage(sent, Recipient.resolved(groupRecipient.get()), groupId, textStoryBody, attachments, sent.timestamp, storyType, linkPreviews, bodyRanges)
      }
    }

    SignalDatabase.storySends.applySentStoryManifest(manifest, sent.timestamp)
  }

  @Throws(MmsException::class)
  private fun insertSentStoryMessage(
    sent: Sent,
    recipient: Recipient,
    groupId: GroupId.V2?,
    textStoryBody: String?,
    pendingAttachments: List<Attachment>,
    sentAtTimestamp: Long,
    storyType: StoryType,
    linkPreviews: List<LinkPreview>,
    bodyRanges: BodyRangeList?
  ) {
    if (SignalDatabase.messages.isOutgoingStoryAlreadyInDatabase(recipient.id, sentAtTimestamp)) {
      warn(sentAtTimestamp, "Already inserted this story.")
      return
    }

    val mediaMessage = OutgoingMessage(
      recipient = recipient,
      body = textStoryBody,
      attachments = pendingAttachments,
      timestamp = sentAtTimestamp,
      storyType = storyType,
      previews = linkPreviews,
      bodyRanges = bodyRanges,
      isSecure = true
    )

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    val messageId: Long
    val attachments: List<DatabaseAttachment>

    SignalDatabase.messages.beginTransaction()
    try {
      messageId = SignalDatabase.messages.insertMessageOutbox(mediaMessage, threadId, false, GroupReceiptTable.STATUS_UNDELIVERED, null)

      if (groupId != null) {
        updateGroupReceiptStatus(sent, messageId, recipient.requireGroupId())
      } else if (recipient.distributionListId.isPresent) {
        updateGroupReceiptStatusForDistributionList(sent, messageId, recipient.distributionListId.get())
      } else {
        SignalDatabase.messages.markUnidentified(messageId, sent.isUnidentified(recipient.serviceId.orNull()))
      }

      SignalDatabase.messages.markAsSent(messageId, true)

      val allAttachments = SignalDatabase.attachments.getAttachmentsForMessage(messageId)
      attachments = allAttachments.filterNot { it.isSticker }

      if (recipient.isSelf) {
        val id = SyncMessageId(recipient.id, sent.timestamp)
        SignalDatabase.messages.incrementDeliveryReceiptCount(id, System.currentTimeMillis())
        SignalDatabase.messages.incrementReadReceiptCount(id, System.currentTimeMillis())
      }

      SignalDatabase.messages.setTransactionSuccessful()
    } finally {
      SignalDatabase.messages.endTransaction()
    }

    for (attachment in attachments) {
      ApplicationDependencies.getJobManager().add(AttachmentDownloadJob(messageId, attachment.attachmentId, false))
    }
  }

  private fun handleGroupRecipientUpdate(sent: Sent, envelopeTimestamp: Long) {
    log(envelopeTimestamp, "Group recipient update.")

    val recipient = getSyncMessageDestination(sent)
    if (!recipient.isGroup) {
      warn("Got recipient update for a non-group message! Skipping.")
      return
    }

    val record = SignalDatabase.messages.getMessageFor(sent.timestamp, Recipient.self().id)
    if (record == null) {
      warn("Got recipient update for non-existing message! Skipping.")
      return
    }

    updateGroupReceiptStatus(sent, record.id, recipient.requireGroupId())
  }

  private fun updateGroupReceiptStatus(sent: Sent, messageId: Long, groupString: GroupId) {
    val messageRecipientIds: Map<RecipientId, Boolean> = sent.serviceIdsToUnidentifiedStatus.mapKeys { RecipientId.from(it.key) }
    val members: List<RecipientId> = SignalDatabase.groups.getGroupMembers(groupString, GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF).map { it.id }
    val localReceipts: Map<RecipientId, Int> = SignalDatabase.groupReceipts.getGroupReceiptInfo(messageId).associate { it.recipientId to it.status }

    for (messageRecipientId in messageRecipientIds.keys) {
      if ((localReceipts[messageRecipientId] ?: GroupReceiptTable.STATUS_UNKNOWN) < GroupReceiptTable.STATUS_UNDELIVERED) {
        SignalDatabase.groupReceipts.update(messageRecipientId, messageId, GroupReceiptTable.STATUS_UNDELIVERED, sent.timestamp)
      } else if (!localReceipts.containsKey(messageRecipientId)) {
        SignalDatabase.groupReceipts.insert(listOf(messageRecipientId), messageId, GroupReceiptTable.STATUS_UNDELIVERED, sent.timestamp)
      }
    }

    val unidentifiedStatus = members.map { Pair(it, messageRecipientIds[it] ?: false) }

    SignalDatabase.groupReceipts.setUnidentified(unidentifiedStatus, messageId)
  }

  private fun updateGroupReceiptStatusForDistributionList(sent: Sent, messageId: Long, distributionListId: DistributionListId) {
    val messageRecipientIds: Map<RecipientId, Boolean> = sent.serviceIdsToUnidentifiedStatus.mapKeys { RecipientId.from(it.key) }
    val members: List<RecipientId> = SignalDatabase.distributionLists.getMembers(distributionListId)
    val localReceipts: Map<RecipientId, Int> = SignalDatabase.groupReceipts.getGroupReceiptInfo(messageId).associate { it.recipientId to it.status }

    for (messageRecipientId in messageRecipientIds.keys) {
      if ((localReceipts[messageRecipientId] ?: GroupReceiptTable.STATUS_UNKNOWN) < GroupReceiptTable.STATUS_UNDELIVERED) {
        SignalDatabase.groupReceipts.update(messageRecipientId, messageId, GroupReceiptTable.STATUS_UNDELIVERED, sent.timestamp)
      } else if (!localReceipts.containsKey(messageRecipientId)) {
        SignalDatabase.groupReceipts.insert(listOf(messageRecipientId), messageId, GroupReceiptTable.STATUS_UNDELIVERED, sent.timestamp)
      }
    }

    val unidentifiedStatus = members.map { Pair(it, messageRecipientIds[it] ?: false) }

    SignalDatabase.groupReceipts.setUnidentified(unidentifiedStatus, messageId)
  }

  @Throws(MmsException::class)
  private fun handleSynchronizeSentEndSessionMessage(context: Context, sent: Sent, envelopeTimestamp: Long): Long {
    log(envelopeTimestamp, "Synchronize end session message.")

    val recipient: Recipient = getSyncMessageDestination(sent)
    val outgoingEndSessionMessage: OutgoingMessage = endSessionMessage(recipient, sent.timestamp)
    val threadId: Long = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)

    if (!recipient.isGroup) {
      ApplicationDependencies.getProtocolStore().aci().deleteAllSessions(recipient.requireServiceId().toString())
      SecurityEvent.broadcastSecurityUpdateEvent(context)
      val messageId = SignalDatabase.messages.insertMessageOutbox(
        outgoingEndSessionMessage,
        threadId,
        false,
        null
      )

      SignalDatabase.messages.markAsSent(messageId, true)
      SignalDatabase.threads.update(threadId, true)
    }

    return threadId
  }

  @Throws(IOException::class, GroupChangeBusyException::class)
  private fun handleSynchronizeSentGv2Update(context: Context, envelope: Envelope, sent: Sent) {
    log(envelope.timestamp, "Synchronize sent GV2 update for message with timestamp " + sent.timestamp)

    val dataMessage: DataMessage = sent.message
    val groupId: GroupId.V2? = dataMessage.groupV2.groupId

    if (!MessageContentProcessorV2.updateGv2GroupFromServerOrP2PChange(context, envelope.timestamp, dataMessage.groupV2)) {
      log(envelope.timestamp, "Ignoring GV2 message for group we are not currently in $groupId")
    }
  }

  @Throws(MmsException::class)
  private fun handleSynchronizeSentExpirationUpdate(sent: Sent): Long {
    log(sent.timestamp, "Synchronize sent expiration update.")

    val groupId: GroupId? = getSyncMessageDestination(sent).groupId.orNull()

    if (groupId != null && groupId.isV2) {
      warn(sent.timestamp, "Expiration update received for GV2. Ignoring.")
      return -1
    }

    val recipient: Recipient = getSyncMessageDestination(sent)
    val expirationUpdateMessage: OutgoingMessage = expirationUpdateMessage(recipient, sent.timestamp, sent.message.expireTimer.seconds.inWholeMilliseconds)
    val threadId: Long = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    val messageId: Long = SignalDatabase.messages.insertMessageOutbox(expirationUpdateMessage, threadId, false, null)

    SignalDatabase.messages.markAsSent(messageId, true)

    SignalDatabase.recipients.setExpireMessages(recipient.id, sent.message.expireTimer)

    return threadId
  }

  @Throws(MmsException::class, BadGroupIdException::class)
  private fun handleSynchronizeSentStoryReply(sent: Sent, envelopeTimestamp: Long): Long {
    log(envelopeTimestamp, "Synchronize sent story reply for " + sent.timestamp)

    try {
      val reaction: DataMessage.Reaction = sent.message.reaction
      val parentStoryId: ParentStoryId
      val authorServiceId: ServiceId = ServiceId.parseOrThrow(sent.message.storyContext.authorUuid)
      val sentTimestamp: Long = sent.message.storyContext.sentTimestamp
      val recipient: Recipient = getSyncMessageDestination(sent)
      var quoteModel: QuoteModel? = null
      var expiresInMillis = 0L
      val storyAuthorRecipient: RecipientId = RecipientId.from(authorServiceId)
      val storyMessageId: Long = SignalDatabase.messages.getStoryId(storyAuthorRecipient, sentTimestamp).id
      val story: MmsMessageRecord = SignalDatabase.messages.getMessageRecord(storyMessageId) as MmsMessageRecord
      val threadRecipientId: RecipientId? = SignalDatabase.threads.getRecipientForThreadId(story.threadId)?.id
      val groupStory: Boolean = threadRecipientId != null && (SignalDatabase.groups.getGroup(threadRecipientId).orNull()?.isActive ?: false)
      var bodyRanges: BodyRangeList? = null

      val body: String? = if (sent.message.hasReaction() && EmojiUtil.isEmoji(reaction.emoji)) {
        reaction.emoji
      } else if (sent.message.hasBody()) {
        bodyRanges = sent.message.bodyRangesList.toBodyRangeList()
        sent.message.body
      } else {
        null
      }

      if (sent.message.hasGroupContext) {
        parentStoryId = GroupReply(storyMessageId)
      } else if (groupStory || story.storyType.isStoryWithReplies) {
        parentStoryId = DirectReply(storyMessageId)

        var quoteBody = ""
        var bodyBodyRanges: BodyRangeList? = null
        if (story.storyType.isTextStory) {
          quoteBody = story.body
          bodyBodyRanges = story.messageRanges
        }
        quoteModel = QuoteModel(sentTimestamp, storyAuthorRecipient, quoteBody, false, story.slideDeck.asAttachments(), emptyList(), QuoteModel.Type.NORMAL, bodyBodyRanges)
        expiresInMillis = sent.message.expireTimer.seconds.inWholeMilliseconds
      } else {
        warn(envelopeTimestamp, "Story has replies disabled. Dropping reply.")
        return -1L
      }

      val mediaMessage = OutgoingMessage(
        recipient = recipient,
        body = body,
        timestamp = sent.timestamp,
        expiresIn = expiresInMillis,
        parentStoryId = parentStoryId,
        isStoryReaction = sent.message.hasReaction(),
        quote = quoteModel,
        mentions = DataMessageProcessor.getMentions(sent.message.bodyRangesList),
        bodyRanges = bodyRanges,
        isSecure = true
      )

      if (recipient.expiresInSeconds != sent.message.expireTimer) {
        handleSynchronizeSentExpirationUpdate(sent)
      }

      val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
      val messageId: Long

      SignalDatabase.messages.beginTransaction()
      try {
        messageId = SignalDatabase.messages.insertMessageOutbox(mediaMessage, threadId, false, GroupReceiptTable.STATUS_UNKNOWN, null)
        if (recipient.isGroup) {
          updateGroupReceiptStatus(sent, messageId, recipient.requireGroupId())
        } else {
          SignalDatabase.messages.markUnidentified(messageId, sent.isUnidentified(recipient.serviceId.orNull()))
        }

        SignalDatabase.messages.markAsSent(messageId, true)
        if (sent.message.expireTimer > 0) {
          SignalDatabase.messages.markExpireStarted(messageId, sent.expirationStartTimestamp)

          ApplicationDependencies
            .getExpiringMessageManager()
            .scheduleDeletion(messageId, true, sent.expirationStartTimestamp, sent.message.expireTimer.seconds.inWholeMilliseconds)
        }
        if (recipient.isSelf) {
          val id = SyncMessageId(recipient.id, sent.timestamp)
          SignalDatabase.messages.incrementDeliveryReceiptCount(id, System.currentTimeMillis())
          SignalDatabase.messages.incrementReadReceiptCount(id, System.currentTimeMillis())
        }
        SignalDatabase.messages.setTransactionSuccessful()
      } finally {
        SignalDatabase.messages.endTransaction()
      }

      return threadId
    } catch (e: NoSuchMessageException) {
      warn(envelopeTimestamp, "Couldn't find story for reply.", e)
      return -1L
    }
  }

  @Throws(MmsException::class, BadGroupIdException::class)
  private fun handleSynchronizeSentMediaMessage(context: Context, sent: Sent, envelopeTimestamp: Long): Long {
    log(envelopeTimestamp, "Synchronize sent media message for " + sent.timestamp)

    val recipient: Recipient = getSyncMessageDestination(sent)
    val quote: QuoteModel? = DataMessageProcessor.getValidatedQuote(context, envelopeTimestamp, sent.message)
    val sticker: Attachment? = DataMessageProcessor.getStickerAttachment(envelopeTimestamp, sent.message)
    val sharedContacts: List<Contact> = DataMessageProcessor.getContacts(sent.message)
    val previews: List<LinkPreview> = DataMessageProcessor.getLinkPreviews(sent.message.previewList, sent.message.body ?: "", false)
    val mentions: List<Mention> = DataMessageProcessor.getMentions(sent.message.bodyRangesList)
    val giftBadge: GiftBadge? = if (sent.message.hasGiftBadge()) GiftBadge.newBuilder().setRedemptionToken(sent.message.giftBadge.receiptCredentialPresentation).build() else null
    val viewOnce: Boolean = sent.message.isViewOnce
    val bodyRanges: BodyRangeList? = sent.message.bodyRangesList.toBodyRangeList()
    val syncAttachments: List<Attachment> = listOfNotNull(sticker) + if (viewOnce) listOf<Attachment>(TombstoneAttachment(MediaUtil.VIEW_ONCE, false)) else sent.message.attachmentsList.toPointers()

    val mediaMessage = OutgoingMessage(
      recipient = recipient,
      body = sent.message.body ?: "",
      attachments = syncAttachments,
      timestamp = sent.timestamp,
      expiresIn = sent.message.expireTimer.seconds.inWholeMilliseconds,
      viewOnce = viewOnce,
      quote = quote,
      contacts = sharedContacts,
      previews = previews,
      mentions = mentions,
      giftBadge = giftBadge,
      bodyRanges = bodyRanges,
      isSecure = true
    )

    if (recipient.expiresInSeconds != sent.message.expireTimer) {
      handleSynchronizeSentExpirationUpdate(sent)
    }

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    val messageId: Long
    val attachments: List<DatabaseAttachment>
    val stickerAttachments: List<DatabaseAttachment>

    SignalDatabase.messages.beginTransaction()
    try {
      messageId = SignalDatabase.messages.insertMessageOutbox(mediaMessage, threadId, false, GroupReceiptTable.STATUS_UNKNOWN, null)

      if (recipient.isGroup) {
        updateGroupReceiptStatus(sent, messageId, recipient.requireGroupId())
      } else {
        SignalDatabase.messages.markUnidentified(messageId, sent.isUnidentified(recipient.serviceId.orNull()))
      }

      SignalDatabase.messages.markAsSent(messageId, true)

      val allAttachments = SignalDatabase.attachments.getAttachmentsForMessage(messageId)
      stickerAttachments = allAttachments.filter { it.isSticker }
      attachments = allAttachments.filterNot { it.isSticker }

      if (sent.message.expireTimer > 0) {
        SignalDatabase.messages.markExpireStarted(messageId, sent.expirationStartTimestamp)

        ApplicationDependencies.getExpiringMessageManager().scheduleDeletion(messageId, true, sent.expirationStartTimestamp, sent.message.expireTimer.seconds.inWholeMilliseconds)
      }
      if (recipient.isSelf) {
        val id = SyncMessageId(recipient.id, sent.timestamp)
        SignalDatabase.messages.incrementDeliveryReceiptCount(id, System.currentTimeMillis())
        SignalDatabase.messages.incrementReadReceiptCount(id, System.currentTimeMillis())
      }
      SignalDatabase.messages.setTransactionSuccessful()
    } finally {
      SignalDatabase.messages.endTransaction()
    }

    for (attachment in attachments) {
      ApplicationDependencies.getJobManager().add(AttachmentDownloadJob(messageId, attachment.attachmentId, false))
    }

    DataMessageProcessor.forceStickerDownloadIfNecessary(context, messageId, stickerAttachments)

    return threadId
  }

  @Throws(MmsException::class, BadGroupIdException::class)
  private fun handleSynchronizeSentTextMessage(sent: Sent, envelopeTimestamp: Long): Long {
    log(envelopeTimestamp, "Synchronize sent text message for " + sent.timestamp)

    val recipient = getSyncMessageDestination(sent)
    val body = sent.message.body ?: ""
    val expiresInMillis = sent.message.expireTimer.seconds.inWholeMilliseconds
    val bodyRanges = sent.message.bodyRangesList.filterNot { it.hasMentionUuid() }.toBodyRangeList()

    if (recipient.expiresInSeconds != sent.message.expireTimer) {
      handleSynchronizeSentExpirationUpdate(sent)
    }

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    val isGroup = recipient.isGroup
    val messageId: Long

    if (isGroup) {
      val outgoingMessage = OutgoingMessage(
        recipient = recipient,
        body = body,
        timestamp = sent.timestamp,
        expiresIn = expiresInMillis,
        isSecure = true,
        bodyRanges = bodyRanges
      )

      messageId = SignalDatabase.messages.insertMessageOutbox(outgoingMessage, threadId, false, GroupReceiptTable.STATUS_UNKNOWN, null)
      updateGroupReceiptStatus(sent, messageId, recipient.requireGroupId())
    } else {
      val outgoingTextMessage = text(recipient = recipient, body = body, expiresIn = expiresInMillis, sentTimeMillis = sent.timestamp, bodyRanges = bodyRanges)
      messageId = SignalDatabase.messages.insertMessageOutbox(outgoingTextMessage, threadId, false, null)
      SignalDatabase.messages.markUnidentified(messageId, sent.isUnidentified(recipient.serviceId.orNull()))
    }
    SignalDatabase.threads.update(threadId, true)
    SignalDatabase.messages.markAsSent(messageId, true)
    if (expiresInMillis > 0) {
      SignalDatabase.messages.markExpireStarted(messageId, sent.expirationStartTimestamp)
      ApplicationDependencies.getExpiringMessageManager().scheduleDeletion(messageId, isGroup, sent.expirationStartTimestamp, expiresInMillis)
    }

    if (recipient.isSelf) {
      val id = SyncMessageId(recipient.id, sent.timestamp)
      SignalDatabase.messages.incrementDeliveryReceiptCount(id, System.currentTimeMillis())
      SignalDatabase.messages.incrementReadReceiptCount(id, System.currentTimeMillis())
    }

    return threadId
  }

  private fun handleSynchronizeRequestMessage(context: Context, message: Request, envelopeTimestamp: Long) {
    if (SignalStore.account().isPrimaryDevice) {
      log(envelopeTimestamp, "Synchronize request message.")
    } else {
      log(envelopeTimestamp, "Linked device ignoring synchronize request message.")
      return
    }

    when (message.type) {
      Request.Type.CONTACTS -> ApplicationDependencies.getJobManager().add(MultiDeviceContactUpdateJob(true))
      Request.Type.GROUPS -> ApplicationDependencies.getJobManager().add(MultiDeviceGroupUpdateJob())
      Request.Type.BLOCKED -> ApplicationDependencies.getJobManager().add(MultiDeviceBlockedUpdateJob())
      Request.Type.CONFIGURATION -> {
        ApplicationDependencies.getJobManager().add(
          MultiDeviceConfigurationUpdateJob(
            TextSecurePreferences.isReadReceiptsEnabled(context),
            TextSecurePreferences.isTypingIndicatorsEnabled(context),
            TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(context),
            SignalStore.settings().isLinkPreviewsEnabled
          )
        )
        ApplicationDependencies.getJobManager().add(MultiDeviceStickerPackSyncJob())
      }
      Request.Type.KEYS -> ApplicationDependencies.getJobManager().add(MultiDeviceKeysUpdateJob())
      Request.Type.PNI_IDENTITY -> ApplicationDependencies.getJobManager().add(MultiDevicePniIdentityUpdateJob())
      else -> warn(envelopeTimestamp, "Unknown request type: ${message.type}")
    }
  }

  private fun handleSynchronizeReadMessage(
    context: Context,
    readMessages: List<Read>,
    envelopeTimestamp: Long,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?
  ) {
    log(envelopeTimestamp, "Synchronize read message. Count: ${readMessages.size}, Timestamps: ${readMessages.map { it.timestamp }}")

    val threadToLatestRead: Map<Long, Long> = HashMap()
    val unhandled = SignalDatabase.messages.setTimestampReadFromSyncMessageProto(readMessages, envelopeTimestamp, threadToLatestRead.toMutableMap())
    val markedMessages: List<MarkedMessageInfo?> = SignalDatabase.threads.setReadSince(threadToLatestRead, false)

    if (Util.hasItems(markedMessages)) {
      log("Updating past SignalDatabase.messages: " + markedMessages.size)
      MarkReadReceiver.process(context, markedMessages)
    }

    for (id in unhandled) {
      warn(envelopeTimestamp, "[handleSynchronizeReadMessage] Could not find matching message! timestamp: ${id.timetamp}  author: ${id.recipientId}")
      if (earlyMessageCacheEntry != null) {
        ApplicationDependencies.getEarlyMessageCache().store(id.recipientId, id.timetamp, earlyMessageCacheEntry)
      }
    }

    if (unhandled.isNotEmpty() && earlyMessageCacheEntry != null) {
      PushProcessEarlyMessagesJob.enqueue()
    }

    ApplicationDependencies
      .getMessageNotifier()
      .apply {
        setLastDesktopActivityTimestamp(envelopeTimestamp)
        cancelDelayedNotifications()
        updateNotification(context)
      }
  }

  private fun handleSynchronizeViewedMessage(context: Context, viewedMessages: MutableList<SyncMessage.Viewed>, envelopeTimestamp: Long) {
    log(envelopeTimestamp, "Synchronize view message. Count: ${viewedMessages.size}, Timestamps: ${viewedMessages.map { it.timestamp }}")

    val records = viewedMessages
      .mapNotNull { message ->
        val author = Recipient.externalPush(ServiceId.parseOrThrow(message.senderUuid)).id
        SignalDatabase.messages.getMessageFor(message.timestamp, author)
      }

    val toMarkViewed = records.map { it.id }

    val toEnqueueDownload = records
      .map { it as MediaMmsMessageRecord }
      .filter { it.storyType.isStory && !it.storyType.isTextStory }

    for (mediaMmsMessageRecord in toEnqueueDownload) {
      Stories.enqueueAttachmentsFromStoryForDownloadSync(mediaMmsMessageRecord, false)
    }

    SignalDatabase.messages.setIncomingMessagesViewed(toMarkViewed)
    SignalDatabase.messages.setOutgoingGiftsRevealed(toMarkViewed)

    ApplicationDependencies.getMessageNotifier().apply {
      setLastDesktopActivityTimestamp(envelopeTimestamp)
      cancelDelayedNotifications()
      updateNotification(context)
    }
  }

  private fun handleSynchronizeViewOnceOpenMessage(context: Context, openMessage: ViewOnceOpen, envelopeTimestamp: Long, earlyMessageCacheEntry: EarlyMessageCacheEntry?) {
    log(envelopeTimestamp, "Handling a view-once open for message: " + openMessage.timestamp)

    val author: RecipientId = Recipient.externalPush(ServiceId.parseOrThrow(openMessage.senderUuid)).id
    val timestamp: Long = openMessage.timestamp
    val record: MessageRecord? = SignalDatabase.messages.getMessageFor(timestamp, author)

    if (record != null) {
      SignalDatabase.attachments.deleteAttachmentFilesForViewOnceMessage(record.id)
    } else {
      warn(envelopeTimestamp.toString(), "Got a view-once open message for a message we don't have!")
      if (earlyMessageCacheEntry != null) {
        ApplicationDependencies.getEarlyMessageCache().store(author, timestamp, earlyMessageCacheEntry)
        PushProcessEarlyMessagesJob.enqueue()
      }
    }

    ApplicationDependencies.getMessageNotifier().apply {
      setLastDesktopActivityTimestamp(envelopeTimestamp)
      cancelDelayedNotifications()
      updateNotification(context)
    }
  }

  private fun handleSynchronizeVerifiedMessage(context: Context, verifiedMessage: SignalServiceProtos.Verified) {
    log("Synchronize verified message.")

    IdentityUtil.processVerifiedMessage(context, verifiedMessage)
  }

  private fun handleSynchronizeStickerPackOperation(stickerPackOperations: List<StickerPackOperation>, envelopeTimestamp: Long) {
    log(envelopeTimestamp, "Synchronize sticker pack operation.")

    val jobManager = ApplicationDependencies.getJobManager()

    for (operation in stickerPackOperations) {
      if (operation.hasPackId() && operation.hasPackKey() && operation.hasType()) {
        val packId = Hex.toStringCondensed(operation.packId.toByteArray())
        val packKey = Hex.toStringCondensed(operation.packKey.toByteArray())

        when (operation.type) {
          StickerPackOperation.Type.INSTALL -> jobManager.add(StickerPackDownloadJob.forInstall(packId, packKey, false))
          StickerPackOperation.Type.REMOVE -> SignalDatabase.stickers.uninstallPack(packId)
          else -> warn("Unknown sticker operation: ${operation.type}")
        }
      } else {
        warn("Received incomplete sticker pack operation sync.")
      }
    }
  }

  private fun handleSynchronizeConfigurationMessage(context: Context, configurationMessage: Configuration, envelopeTimestamp: Long) {
    log(envelopeTimestamp, "Synchronize configuration message.")

    if (configurationMessage.hasReadReceipts()) {
      TextSecurePreferences.setReadReceiptsEnabled(context, configurationMessage.readReceipts)
    }

    if (configurationMessage.hasUnidentifiedDeliveryIndicators()) {
      TextSecurePreferences.setShowUnidentifiedDeliveryIndicatorsEnabled(context, configurationMessage.unidentifiedDeliveryIndicators)
    }

    if (configurationMessage.hasTypingIndicators()) {
      TextSecurePreferences.setTypingIndicatorsEnabled(context, configurationMessage.typingIndicators)
    }

    if (configurationMessage.hasLinkPreviews()) {
      SignalStore.settings().isLinkPreviewsEnabled = configurationMessage.linkPreviews
    }
  }

  private fun handleSynchronizeBlockedListMessage(blockMessage: Blocked) {
    val addresses: List<SignalServiceAddress> = blockMessage.uuidsList.mapNotNull { SignalServiceAddress.fromRaw(it, null).orNull() }
    val groupIds: List<ByteArray> = blockMessage.groupIdsList.mapNotNull { it.toByteArray() }

    SignalDatabase.recipients.applyBlockedUpdate(addresses, groupIds)
  }

  private fun handleSynchronizeFetchMessage(fetchType: FetchLatest.Type, envelopeTimestamp: Long) {
    log(envelopeTimestamp, "Received fetch request with type: $fetchType")
    when (fetchType) {
      FetchLatest.Type.LOCAL_PROFILE -> ApplicationDependencies.getJobManager().add(RefreshOwnProfileJob())
      FetchLatest.Type.STORAGE_MANIFEST -> StorageSyncHelper.scheduleSyncForDataChange()
      FetchLatest.Type.SUBSCRIPTION_STATUS -> warn(envelopeTimestamp, "Dropping subscription status fetch message.")
      else -> warn(envelopeTimestamp, "Received a fetch message for an unknown type.")
    }
  }

  @Throws(BadGroupIdException::class)
  private fun handleSynchronizeMessageRequestResponse(response: MessageRequestResponse, envelopeTimestamp: Long) {
    log(envelopeTimestamp, "Synchronize message request response.")

    val recipient: Recipient = if (response.hasThreadUuid()) {
      Recipient.externalPush(ServiceId.parseOrThrow(response.threadUuid))
    } else if (response.hasGroupId()) {
      val groupId: GroupId = GroupId.push(response.groupId)
      Recipient.externalPossiblyMigratedGroup(groupId)
    } else {
      warn("Message request response was missing a thread recipient! Skipping.")
      return
    }

    val threadId: Long = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)

    when (response.type) {
      MessageRequestResponse.Type.ACCEPT -> {
        SignalDatabase.recipients.setProfileSharing(recipient.id, true)
        SignalDatabase.recipients.setBlocked(recipient.id, false)
      }
      MessageRequestResponse.Type.DELETE -> {
        SignalDatabase.recipients.setProfileSharing(recipient.id, false)
        if (threadId > 0) {
          SignalDatabase.threads.deleteConversation(threadId)
        }
      }
      MessageRequestResponse.Type.BLOCK -> {
        SignalDatabase.recipients.setBlocked(recipient.id, true)
        SignalDatabase.recipients.setProfileSharing(recipient.id, false)
      }
      MessageRequestResponse.Type.BLOCK_AND_DELETE -> {
        SignalDatabase.recipients.setBlocked(recipient.id, true)
        SignalDatabase.recipients.setProfileSharing(recipient.id, false)
        if (threadId > 0) {
          SignalDatabase.threads.deleteConversation(threadId)
        }
      }
      else -> warn("Got an unknown response type! Skipping")
    }
  }

  private fun handleSynchronizeOutgoingPayment(outgoingPayment: SyncMessage.OutgoingPayment, envelopeTimestamp: Long) {
    if (!outgoingPayment.hasMobileCoin()) {
      log("Unknown outgoing payment, ignoring.")
      return
    }

    var recipientId: RecipientId? = ServiceId.parseOrNull(outgoingPayment.recipientUuid)?.let { RecipientId.from(it) }

    var timestamp = outgoingPayment.mobileCoin.ledgerBlockTimestamp
    if (timestamp == 0L) {
      timestamp = System.currentTimeMillis()
    }

    var address: MobileCoinPublicAddress? = if (outgoingPayment.mobileCoin.hasRecipientAddress()) {
      MobileCoinPublicAddress.fromBytes(outgoingPayment.mobileCoin.recipientAddress.toByteArray())
    } else {
      null
    }

    if (address == null && recipientId == null) {
      log(envelopeTimestamp, "Inserting defrag")
      address = ApplicationDependencies.getPayments().wallet.mobileCoinPublicAddress
      recipientId = Recipient.self().id
    }

    val uuid = UUID.randomUUID()
    try {
      SignalDatabase.payments
        .createSuccessfulPayment(
          uuid,
          recipientId,
          address!!,
          timestamp,
          outgoingPayment.mobileCoin.ledgerBlockIndex,
          outgoingPayment.note ?: "",
          outgoingPayment.mobileCoin.amountPicoMob.toMobileCoinMoney(),
          outgoingPayment.mobileCoin.feePicoMob.toMobileCoinMoney(),
          outgoingPayment.mobileCoin.receipt.toByteArray(),
          PaymentMetaDataUtil.fromKeysAndImages(outgoingPayment.mobileCoin.outputPublicKeysList, outgoingPayment.mobileCoin.spentKeyImagesList)
        )
    } catch (e: SerializationException) {
      warn(envelopeTimestamp, "Ignoring synchronized outgoing payment with bad data.", e)
    }

    log("Inserted synchronized payment $uuid")
  }

  private fun handleSynchronizeKeys(storageKey: ByteString, envelopeTimestamp: Long) {
    if (SignalStore.account().isLinkedDevice) {
      log(envelopeTimestamp, "Synchronize keys.")
    } else {
      log(envelopeTimestamp, "Primary device ignores synchronize keys.")
      return
    }

    SignalStore.storageService().setStorageKeyFromPrimary(StorageKey(storageKey.toByteArray()))
  }

  @Throws(IOException::class)
  private fun handleSynchronizeContacts(contactsMessage: SyncMessage.Contacts, envelopeTimestamp: Long) {
    if (SignalStore.account().isLinkedDevice) {
      log(envelopeTimestamp, "Synchronize contacts.")
    } else {
      log(envelopeTimestamp, "Primary device ignores synchronize contacts.")
      return
    }

    val attachment: SignalServiceAttachmentPointer = contactsMessage.blob.toSignalServiceAttachmentPointer()

    ApplicationDependencies.getJobManager().add(MultiDeviceContactSyncJob(attachment))
  }

  private fun handleSynchronizeCallEvent(callEvent: SyncMessage.CallEvent, envelopeTimestamp: Long) {
    if (!callEvent.hasId()) {
      log(envelopeTimestamp, "Synchronize call event missing call id, ignoring. type: ${callEvent.type}")
      return
    }

    if (callEvent.type == SyncMessage.CallEvent.Type.GROUP_CALL || callEvent.type == SyncMessage.CallEvent.Type.AD_HOC_CALL) {
      handleSynchronizeGroupOrAdHocCallEvent(callEvent, envelopeTimestamp)
    } else {
      handleSynchronizeOneToOneCallEvent(callEvent, envelopeTimestamp)
    }
  }

  private fun handleSynchronizeOneToOneCallEvent(callEvent: SyncMessage.CallEvent, envelopeTimestamp: Long) {
    val callId: Long = callEvent.id
    val timestamp: Long = callEvent.timestamp
    val type: CallTable.Type? = CallTable.Type.from(callEvent.type)
    val direction: CallTable.Direction? = CallTable.Direction.from(callEvent.direction)
    val event: CallTable.Event? = CallTable.Event.from(callEvent.event)

    if (timestamp == 0L || type == null || direction == null || event == null || !callEvent.hasConversationId()) {
      warn(envelopeTimestamp, "Call event sync message is not valid, ignoring. timestamp: " + timestamp + " type: " + type + " direction: " + direction + " event: " + event + " hasPeer: " + callEvent.hasConversationId())
      return
    }

    val serviceId = ServiceId.fromByteString(callEvent.conversationId)
    val recipientId = RecipientId.from(serviceId)

    log(envelopeTimestamp, "Synchronize call event call: $callId")

    val call = SignalDatabase.calls.getCallById(callId, CallTable.CallConversationId.Peer(recipientId))
    if (call != null) {
      val typeMismatch = call.type !== type
      val directionMismatch = call.direction !== direction
      val eventDowngrade = call.event === CallTable.Event.ACCEPTED && event !== CallTable.Event.ACCEPTED
      val peerMismatch = call.peer != recipientId

      if (typeMismatch || directionMismatch || eventDowngrade || peerMismatch) {
        warn(envelopeTimestamp, "Call event sync message is not valid for existing call record, ignoring. type: $type direction: $direction  event: $event peerMismatch: $peerMismatch")
      } else {
        SignalDatabase.calls.updateOneToOneCall(callId, event)
      }
    } else {
      SignalDatabase.calls.insertOneToOneCall(callId, timestamp, recipientId, type, direction, event)
    }
  }

  @Throws(BadGroupIdException::class)
  private fun handleSynchronizeGroupOrAdHocCallEvent(callEvent: SyncMessage.CallEvent, envelopeTimestamp: Long) {
    if (!FeatureFlags.adHocCalling() && callEvent.type == SyncMessage.CallEvent.Type.AD_HOC_CALL) {
      log(envelopeTimestamp, "Ad-Hoc calling is not currently supported by this client, ignoring.")
      return
    }

    val callId: Long = callEvent.id
    val timestamp: Long = callEvent.timestamp
    val type: CallTable.Type? = CallTable.Type.from(callEvent.type)
    val direction: CallTable.Direction? = CallTable.Direction.from(callEvent.direction)
    val event: CallTable.Event? = CallTable.Event.from(callEvent.event)

    if (timestamp == 0L || type == null || direction == null || event == null || !callEvent.hasConversationId()) {
      warn(envelopeTimestamp, "Group/Ad-hoc call event sync message is not valid, ignoring. timestamp: " + timestamp + " type: " + type + " direction: " + direction + " event: " + event + " hasPeer: " + callEvent.hasConversationId())
      return
    }

    val groupId: GroupId = GroupId.push(callEvent.conversationId.toByteArray())
    val recipientId = Recipient.externalGroupExact(groupId).id
    val conversationId = CallTable.CallConversationId.Peer(recipientId)

    val call = SignalDatabase.calls.getCallById(callId, CallTable.CallConversationId.Peer(recipientId))

    if (call != null) {
      if (call.type !== type) {
        warn(envelopeTimestamp, "Group/Ad-hoc call event type mismatch, ignoring. timestamp: " + timestamp + " type: " + type + " direction: " + direction + " event: " + event + " hasPeer: " + callEvent.hasConversationId())
        return
      }
      when (event) {
        CallTable.Event.DELETE -> SignalDatabase.calls.deleteGroupCall(call)
        CallTable.Event.ACCEPTED -> {
          if (call.timestamp < callEvent.timestamp) {
            SignalDatabase.calls.setTimestamp(call.callId, conversationId, callEvent.timestamp)
          }
          if (callEvent.direction == SyncMessage.CallEvent.Direction.INCOMING) {
            SignalDatabase.calls.acceptIncomingGroupCall(call)
          } else {
            warn(envelopeTimestamp, "Invalid direction OUTGOING for event ACCEPTED")
          }
        }
        CallTable.Event.NOT_ACCEPTED -> warn("Unsupported event type " + event + ". Ignoring. timestamp: " + timestamp + " type: " + type + " direction: " + direction + " event: " + event + " hasPeer: " + callEvent.hasConversationId())
        else -> warn("Unsupported event type " + event + ". Ignoring. timestamp: " + timestamp + " type: " + type + " direction: " + direction + " event: " + event + " hasPeer: " + callEvent.hasConversationId())
      }
    } else {
      when (event) {
        CallTable.Event.DELETE -> SignalDatabase.calls.insertDeletedGroupCallFromSyncEvent(callEvent.id, recipientId, direction, timestamp)
        CallTable.Event.ACCEPTED -> SignalDatabase.calls.insertAcceptedGroupCall(callEvent.id, recipientId, direction, timestamp)
        CallTable.Event.NOT_ACCEPTED -> warn("Unsupported event type " + event + ". Ignoring. timestamp: " + timestamp + " type: " + type + " direction: " + direction + " event: " + event + " hasPeer: " + callEvent.hasConversationId())
        else -> warn("Unsupported event type " + event + ". Ignoring. timestamp: " + timestamp + " type: " + type + " direction: " + direction + " event: " + event + " hasPeer: " + callEvent.hasConversationId())
      }
    }
  }
}

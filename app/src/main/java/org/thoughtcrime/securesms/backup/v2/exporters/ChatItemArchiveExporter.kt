/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.exporters

import android.database.Cursor
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONException
import org.signal.core.util.Base64
import org.signal.core.util.EventTimer
import org.signal.core.util.Hex
import org.signal.core.util.ParallelEventTimer
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.core.util.nullIfBlank
import org.signal.core.util.nullIfEmpty
import org.signal.core.util.orNull
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireLongOrNull
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.backup.v2.ExportOddities
import org.thoughtcrime.securesms.backup.v2.ExportSkips
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.ChatUpdateMessage
import org.thoughtcrime.securesms.backup.v2.proto.ContactAttachment
import org.thoughtcrime.securesms.backup.v2.proto.ContactMessage
import org.thoughtcrime.securesms.backup.v2.proto.DirectStoryReplyMessage
import org.thoughtcrime.securesms.backup.v2.proto.ExpirationTimerChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GenericGroupUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupCall
import org.thoughtcrime.securesms.backup.v2.proto.GroupChangeChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupExpirationTimerUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupV2MigrationUpdate
import org.thoughtcrime.securesms.backup.v2.proto.IndividualCall
import org.thoughtcrime.securesms.backup.v2.proto.LearnedProfileChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.MessageAttachment
import org.thoughtcrime.securesms.backup.v2.proto.PaymentNotification
import org.thoughtcrime.securesms.backup.v2.proto.ProfileChangeChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.Quote
import org.thoughtcrime.securesms.backup.v2.proto.Reaction
import org.thoughtcrime.securesms.backup.v2.proto.RemoteDeletedMessage
import org.thoughtcrime.securesms.backup.v2.proto.SendStatus
import org.thoughtcrime.securesms.backup.v2.proto.SessionSwitchoverChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.SimpleChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.StandardMessage
import org.thoughtcrime.securesms.backup.v2.proto.Sticker
import org.thoughtcrime.securesms.backup.v2.proto.StickerMessage
import org.thoughtcrime.securesms.backup.v2.proto.Text
import org.thoughtcrime.securesms.backup.v2.proto.ThreadMergeChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.ViewOnceMessage
import org.thoughtcrime.securesms.backup.v2.util.clampToValidBackupRange
import org.thoughtcrime.securesms.backup.v2.util.toRemoteFilePointer
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.PaymentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet
import org.thoughtcrime.securesms.database.documents.NetworkFailureSet
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil
import org.thoughtcrime.securesms.database.model.GroupsV2UpdateMessageConverter
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.payments.FailureReason
import org.thoughtcrime.securesms.payments.State
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.MediaUtil
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.util.toByteArray
import java.io.Closeable
import java.io.IOException
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import kotlin.math.max
import kotlin.time.Duration.Companion.days
import org.thoughtcrime.securesms.backup.v2.proto.BodyRange as BackupBodyRange
import org.thoughtcrime.securesms.backup.v2.proto.GiftBadge as BackupGiftBadge

private val TAG = Log.tag(ChatItemArchiveExporter::class.java)

/**
 * An iterator for chat items with a clever performance twist: rather than do the extra queries one at a time (for reactions,
 * attachments, etc), this will populate items in batches, doing bulk lookups to improve throughput. We keep these in a buffer
 * and only do more queries when the buffer is empty.
 *
 * All of this complexity is hidden from the user -- they just get a normal iterator interface.
 */
class ChatItemArchiveExporter(
  private val db: SignalDatabase,
  private val selfRecipientId: RecipientId,
  private val noteToSelfThreadId: Long,
  private val backupStartTime: Long,
  private val batchSize: Int,
  private val mediaArchiveEnabled: Boolean,
  private val exportState: ExportState,
  private val cursorGenerator: (Long, Int) -> Cursor
) : Iterator<ChatItem?>, Closeable {

  /** Timer for more macro-level events, like fetching extra data vs transforming the data. */
  private val eventTimer = EventTimer()

  /** Timer for just the transformation process, to see what types of transformations are taking more time.  */
  private val transformTimer = EventTimer()

  /** Timer for fetching extra data. */
  private val extraDataTimer = ParallelEventTimer()

  /**
   * A queue of already-parsed ChatItems. Processing in batches means that we read ahead in the cursor and put
   * the pending items here.
   */
  private val buffer: Queue<ChatItem> = LinkedList()

  private val revisionMap: HashMap<Long, ArrayList<ChatItem>> = HashMap()

  private var lastSeenReceivedTime = 0L

  private var records: LinkedHashMap<Long, BackupMessageRecord> = readNextMessageRecordBatch(emptySet())

  override fun hasNext(): Boolean {
    return buffer.isNotEmpty() || records.isNotEmpty()
  }

  override fun next(): ChatItem? {
    if (buffer.isNotEmpty()) {
      return buffer.remove()
    }

    val extraData = fetchExtraMessageData(db, records.keys)
    eventTimer.emit("extra-data")
    transformTimer.emit("ignore")

    for ((id, record) in records) {
      val builder = record.toBasicChatItemBuilder(selfRecipientId, extraData.groupReceiptsById[id], exportState, backupStartTime)
      transformTimer.emit("basic")

      if (builder == null) {
        continue
      }

      when {
        record.remoteDeleted -> {
          builder.remoteDeletedMessage = RemoteDeletedMessage()
          transformTimer.emit("remote-delete")
        }

        MessageTypes.isJoinedType(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.JOINED_SIGNAL)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isIdentityUpdate(record.type) -> {
          if (record.fromRecipientId == selfRecipientId.toLong()) {
            Log.w(TAG, ExportSkips.identityUpdateForSelf(record.dateSent))
            continue
          }
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.IDENTITY_UPDATE)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isIdentityVerified(record.type) -> {
          if (record.toRecipientId == selfRecipientId.toLong()) {
            Log.w(TAG, ExportSkips.identityVerifiedForSelf(record.dateSent))
            continue
          }
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.IDENTITY_VERIFIED)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isIdentityDefault(record.type) -> {
          if (record.toRecipientId == selfRecipientId.toLong()) {
            Log.w(TAG, ExportSkips.identityDefaultForSelf(record.dateSent))
            continue
          }
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.IDENTITY_DEFAULT)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isChangeNumber(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.CHANGE_NUMBER)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isReleaseChannelDonationRequest(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.RELEASE_CHANNEL_DONATION_REQUEST)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isEndSessionType(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.END_SESSION)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isChatSessionRefresh(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.CHAT_SESSION_REFRESH)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isBadDecryptType(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.BAD_DECRYPT)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isPaymentsActivated(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.PAYMENTS_ACTIVATED)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isPaymentsRequestToActivate(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.PAYMENT_ACTIVATION_REQUEST)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isUnsupportedMessageType(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.UNSUPPORTED_PROTOCOL_MESSAGE)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isReportedSpam(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.REPORTED_SPAM)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isMessageRequestAccepted(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.MESSAGE_REQUEST_ACCEPTED)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isBlocked(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.BLOCKED)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isUnblocked(record.type) -> {
          builder.updateMessage = simpleUpdate(SimpleChatUpdate.Type.UNBLOCKED)
          transformTimer.emit("simple-update")
        }

        MessageTypes.isExpirationTimerUpdate(record.type) -> {
          if (exportState.threadIdToRecipientId[record.threadId] in exportState.groupRecipientIds) {
            builder.updateMessage = record.toRemoteGroupExpireTimerUpdateFromGv1(exportState) ?: continue
          } else {
            builder.updateMessage = ChatUpdateMessage(expirationTimerChange = ExpirationTimerChatUpdate(record.expiresIn))
          }

          builder.expireStartDate = null
          builder.expiresInMs = null
          transformTimer.emit("expire-update")
        }

        MessageTypes.isProfileChange(record.type) -> {
          if (record.threadId == noteToSelfThreadId) {
            Log.w(TAG, ExportSkips.profileChangeInNoteToSelf(record.dateSent))
            continue
          }

          builder.updateMessage = record.toRemoteProfileChangeUpdate() ?: continue
          transformTimer.emit("profile-change")
        }

        MessageTypes.isSessionSwitchoverType(record.type) -> {
          builder.updateMessage = record.toRemoteSessionSwitchoverUpdate()
          transformTimer.emit("sse")
        }

        MessageTypes.isThreadMergeType(record.type) -> {
          builder.updateMessage = record.toRemoteThreadMergeUpdate() ?: continue
          transformTimer.emit("thread-merge")
        }

        MessageTypes.isGroupV2(record.type) && MessageTypes.isGroupUpdate(record.type) -> {
          val update = record.toRemoteGroupUpdate() ?: continue
          if (update.groupChange!!.updates.isEmpty()) {
            Log.w(TAG, ExportSkips.groupUpdateHasNoUpdates(record.dateSent))
            continue
          }
          builder.updateMessage = update
          transformTimer.emit("group-update-v2")
        }

        MessageTypes.isGroupUpdate(record.type) || MessageTypes.isGroupQuit(record.type) -> {
          builder.updateMessage = record.toRemoteGroupUpdateFromGv1(exportState) ?: continue
          transformTimer.emit("group-update-v1")
        }

        MessageTypes.isGroupV1MigrationEvent(record.type) -> {
          builder.updateMessage = ChatUpdateMessage(
            groupChange = GroupChangeChatUpdate(
              updates = listOf(GroupChangeChatUpdate.Update(groupV2MigrationUpdate = GroupV2MigrationUpdate()))
            )
          )
          transformTimer.emit("gv1-migration")
        }

        MessageTypes.isCallLog(record.type) -> {
          val call = db.callTable.getCallByMessageId(record.id)
          builder.updateMessage = call?.toRemoteCallUpdate(exportState, record) ?: continue
          transformTimer.emit("call-log")
        }

        MessageTypes.isPaymentsNotification(record.type) -> {
          if (record.threadId == noteToSelfThreadId) {
            Log.w(TAG, ExportSkips.paymentNotificationInNoteToSelf(record.dateSent))
            continue
          }
          builder.paymentNotification = record.toRemotePaymentNotificationUpdate(db)
          transformTimer.emit("payment")
        }

        MessageTypes.isGiftBadge(record.type) -> {
          builder.giftBadge = record.toRemoteGiftBadgeUpdate() ?: continue
          transformTimer.emit("gift-badge")
        }

        !record.sharedContacts.isNullOrEmpty() -> {
          builder.contactMessage = record.toRemoteContactMessage(mediaArchiveEnabled = mediaArchiveEnabled, reactionRecords = extraData.reactionsById[id], attachments = extraData.attachmentsById[id]) ?: continue
          transformTimer.emit("contact")
        }

        record.viewOnce -> {
          builder.viewOnceMessage = record.toRemoteViewOnceMessage(mediaArchiveEnabled = mediaArchiveEnabled, reactionRecords = extraData.reactionsById[id], attachments = extraData.attachmentsById[id])
          transformTimer.emit("voice")
        }

        record.parentStoryId != 0L -> {
          if (record.threadId == noteToSelfThreadId) {
            Log.w(TAG, ExportSkips.directStoryReplyInNoteToSelf(record.dateSent))
            continue
          }
          builder.directStoryReplyMessage = record.toRemoteDirectStoryReplyMessage(mediaArchiveEnabled = mediaArchiveEnabled, reactionRecords = extraData.reactionsById[id], attachments = extraData.attachmentsById[record.id]) ?: continue
          transformTimer.emit("story")
        }

        else -> {
          val attachments = extraData.attachmentsById[record.id]
          if (attachments?.isNotEmpty() == true && attachments.any { it.contentType == MediaUtil.LONG_TEXT } && record.body.isNullOrBlank()) {
            Log.w(TAG, ExportSkips.invalidLongTextChatItem(record.dateSent))
            continue
          }

          val sticker = attachments?.firstOrNull { dbAttachment -> dbAttachment.isSticker }

          if (sticker?.stickerLocator != null) {
            builder.stickerMessage = sticker.toRemoteStickerMessage(sentTimestamp = record.dateSent, mediaArchiveEnabled = mediaArchiveEnabled, reactions = extraData.reactionsById[id])
          } else {
            val standardMessage = record.toRemoteStandardMessage(
              exportState = exportState,
              mediaArchiveEnabled = mediaArchiveEnabled,
              reactionRecords = extraData.reactionsById[id],
              mentions = extraData.mentionsById[id],
              attachments = extraData.attachmentsById[record.id]
            )

            if (standardMessage.text.isNullOrBlank() && standardMessage.attachments.isEmpty()) {
              Log.w(TAG, ExportSkips.emptyStandardMessage(record.dateSent))
              continue
            }

            builder.standardMessage = standardMessage
            transformTimer.emit("standard")
          }
        }
      }

      if (record.latestRevisionId == null) {
        builder.revisions = revisionMap.remove(record.id)?.repairRevisions(builder) ?: emptyList()
        val chatItem = builder.build().validateChatItem() ?: continue
        buffer += chatItem
      } else {
        var previousEdits = revisionMap[record.latestRevisionId]
        if (previousEdits == null) {
          previousEdits = ArrayList()
          revisionMap[record.latestRevisionId] = previousEdits
        }
        previousEdits += builder.build()
      }
      transformTimer.emit("revisions")
    }
    eventTimer.emit("transform")

    val recordIds = HashSet(records.keys)
    records.clear()

    records = readNextMessageRecordBatch(recordIds)
    eventTimer.emit("messages")

    return if (buffer.isNotEmpty()) {
      buffer.remove()
    } else {
      null
    }
  }

  override fun close() {
    Log.d(TAG, "[ChatItemArchiveExporter][batchSize = $batchSize] ${eventTimer.stop().summary}")
    Log.d(TAG, "[ChatItemArchiveExporterTransform][batchSize = $batchSize] ${transformTimer.stop().summary}")
    Log.d(TAG, "[ChatItemArchiveExporterExtraData][batchSize = $batchSize] ${extraDataTimer.stop().summary}")
  }

  private fun readNextMessageRecordBatch(pastIds: Set<Long>): LinkedHashMap<Long, BackupMessageRecord> {
    return cursorGenerator(lastSeenReceivedTime, batchSize).use { cursor ->
      val records: LinkedHashMap<Long, BackupMessageRecord> = LinkedHashMap(batchSize)
      while (cursor.moveToNext()) {
        cursor.toBackupMessageRecord(pastIds, backupStartTime)?.let { record ->
          records[record.id] = record
          lastSeenReceivedTime = record.dateReceived
        }
      }
      records
    }
  }

  private fun fetchExtraMessageData(db: SignalDatabase, messageIds: Set<Long>): ExtraMessageData {
    val executor = SignalExecutors.BOUNDED

    val mentionsFuture = executor.submitTyped {
      extraDataTimer.timeEvent("mentions") {
        db.mentionTable.getMentionsForMessages(messageIds)
      }
    }

    val reactionsFuture = executor.submitTyped {
      extraDataTimer.timeEvent("reactions") {
        db.reactionTable.getReactionsForMessages(messageIds)
      }
    }

    val attachmentsFuture = executor.submitTyped {
      extraDataTimer.timeEvent("attachments") {
        db.attachmentTable.getAttachmentsForMessages(messageIds)
      }
    }

    val groupReceiptsFuture = executor.submitTyped {
      extraDataTimer.timeEvent("group-receipts") {
        db.groupReceiptTable.getGroupReceiptInfoForMessages(messageIds)
      }
    }

    val mentionsResult = mentionsFuture.get()
    val reactionsResult = reactionsFuture.get()
    val attachmentsResult = attachmentsFuture.get()
    val groupReceiptsResult = groupReceiptsFuture.get()

    return ExtraMessageData(
      mentionsById = mentionsResult,
      reactionsById = reactionsResult,
      attachmentsById = attachmentsResult,
      groupReceiptsById = groupReceiptsResult
    )
  }
}

private fun simpleUpdate(type: SimpleChatUpdate.Type): ChatUpdateMessage {
  return ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = type))
}

private fun BackupMessageRecord.toBasicChatItemBuilder(selfRecipientId: RecipientId, groupReceipts: List<GroupReceiptTable.GroupReceiptInfo>?, exportState: ExportState, backupStartTime: Long): ChatItem.Builder? {
  val record = this

  if (this.threadId !in exportState.threadIds) {
    return null
  }

  val direction = when {
    record.type.isDirectionlessType() && !record.remoteDeleted -> {
      Direction.DIRECTIONLESS
    }
    MessageTypes.isOutgoingMessageType(record.type) || record.fromRecipientId == selfRecipientId.toLong() -> {
      Direction.OUTGOING
    }
    else -> {
      Direction.INCOMING
    }
  }

  // If a user restores a backup with a different number, then they'll have outgoing messages from a non-self contact.
  // We want to ensure all outgoing messages are from ourselves.
  val fromRecipientId = when {
    direction == Direction.OUTGOING -> selfRecipientId.toLong()
    record.type.isIdentityVerifyType() -> record.toRecipientId
    MessageTypes.isEndSessionType(record.type) && MessageTypes.isOutgoingMessageType(record.type) -> record.toRecipientId
    MessageTypes.isExpirationTimerUpdate(record.type) && MessageTypes.isOutgoingMessageType(type) -> selfRecipientId.toLong()
    MessageTypes.isOutgoingAudioCall(type) || MessageTypes.isOutgoingVideoCall(type) -> selfRecipientId.toLong()
    MessageTypes.isMessageRequestAccepted(type) -> selfRecipientId.toLong()
    else -> record.fromRecipientId
  }

  if (!exportState.contactRecipientIds.contains(fromRecipientId)) {
    Log.w(TAG, ExportSkips.fromRecipientIsNotAnIndividual(this.dateSent))
    return null
  }

  val threadRecipientId = exportState.threadIdToRecipientId[record.threadId]!!
  if (exportState.contactRecipientIds.contains(threadRecipientId) && fromRecipientId != threadRecipientId && fromRecipientId != selfRecipientId.toLong()) {
    Log.w(TAG, ExportSkips.oneOnOneMessageInTheWrongChat(this.dateSent))
    return null
  }

  val builder = ChatItem.Builder().apply {
    chatId = record.threadId
    authorId = fromRecipientId
    dateSent = record.dateSent.clampToValidBackupRange()
    expireStartDate = record.expireStarted.takeIf { it > 0 }
    expiresInMs = record.expiresIn.takeIf { it > 0 }
    revisions = emptyList()
    sms = record.type.isSmsType()
    when (direction) {
      Direction.DIRECTIONLESS -> {
        directionless = ChatItem.DirectionlessMessageDetails()
      }
      Direction.OUTGOING -> {
        outgoing = ChatItem.OutgoingMessageDetails(
          sendStatus = record.toRemoteSendStatus(isGroupThread = exportState.threadIdToRecipientId[this.chatId] in exportState.groupRecipientIds, groupReceipts = groupReceipts, exportState = exportState)
        )

        if (expiresInMs != null && outgoing?.sendStatus?.all { it.pending == null && it.failed == null } == true) {
          Log.w(TAG, ExportOddities.outgoingMessageWasSentButTimerNotStarted(record.dateSent))
          expireStartDate = record.dateReceived
        }
      }
      Direction.INCOMING -> {
        incoming = ChatItem.IncomingMessageDetails(
          dateServerSent = record.dateServer.takeIf { it > 0 },
          dateReceived = record.dateReceived,
          read = record.read,
          sealedSender = record.sealedSender
        )

        if (expiresInMs != null && incoming?.read == true && expireStartDate == null) {
          Log.w(TAG, ExportOddities.incomingMessageWasReadButTimerNotStarted(record.dateSent))
          expireStartDate = record.dateReceived
        }
      }
    }
  }

  if (!MessageTypes.isExpirationTimerUpdate(record.type) && builder.expiresInMs != null && builder.expireStartDate != null) {
    val expiresAt = builder.expireStartDate!! + builder.expiresInMs!!
    val threshold = if (exportState.forTransfer) backupStartTime else backupStartTime + 1.days.inWholeMilliseconds

    if (expiresAt < threshold) {
      Log.w(TAG, ExportSkips.messageExpiresTooSoon(record.dateSent))
      return null
    }
  }

  if (builder.expireStartDate != null && builder.expiresInMs == null) {
    builder.expireStartDate = null
  }

  return builder
}

private fun BackupMessageRecord.toRemoteProfileChangeUpdate(): ChatUpdateMessage? {
  val profileChangeDetails = this.messageExtras?.profileChangeDetails
    ?: Base64.decodeOrNull(this.body)?.let { ProfileChangeDetails.ADAPTER.decode(it) }

  return if (profileChangeDetails?.profileNameChange != null) {
    if (profileChangeDetails.profileNameChange.previous.isNotBlank() && profileChangeDetails.profileNameChange.newValue.isNotBlank()) {
      ChatUpdateMessage(profileChange = ProfileChangeChatUpdate(previousName = profileChangeDetails.profileNameChange.previous, newName = profileChangeDetails.profileNameChange.newValue))
    } else {
      Log.w(TAG, ExportSkips.emptyProfileNameChange(this.dateSent))
      null
    }
  } else if (profileChangeDetails?.learnedProfileName != null) {
    val e164 = profileChangeDetails.learnedProfileName.e164?.e164ToLong()
    val username = profileChangeDetails.learnedProfileName.username
    if (e164 != null || username.isNotNullOrBlank()) {
      ChatUpdateMessage(learnedProfileChange = LearnedProfileChatUpdate(e164 = e164, username = username))
    } else {
      Log.w(TAG, ExportSkips.emptyLearnedProfileChange(this.dateSent))
      null
    }
  } else {
    null
  }
}

private fun BackupMessageRecord.toRemoteSessionSwitchoverUpdate(): ChatUpdateMessage {
  if (this.body == null) {
    return ChatUpdateMessage(sessionSwitchover = SessionSwitchoverChatUpdate())
  }

  return ChatUpdateMessage(
    sessionSwitchover = try {
      val event = SessionSwitchoverEvent.ADAPTER.decode(Base64.decodeOrThrow(this.body))
      SessionSwitchoverChatUpdate(event.e164.e164ToLong() ?: 0)
    } catch (e: IOException) {
      SessionSwitchoverChatUpdate()
    }
  )
}

private fun BackupMessageRecord.toRemoteThreadMergeUpdate(): ChatUpdateMessage? {
  if (this.body == null) {
    return null
  }

  try {
    val e164 = ThreadMergeEvent.ADAPTER.decode(Base64.decodeOrThrow(this.body)).previousE164.e164ToLong()
    if (e164 != null) {
      return ChatUpdateMessage(threadMerge = ThreadMergeChatUpdate(e164))
    }
  } catch (_: IOException) {
  }

  return null
}

private fun BackupMessageRecord.toRemoteGroupUpdate(): ChatUpdateMessage? {
  val groupChange = this.messageExtras?.gv2UpdateDescription?.groupChangeUpdate
  if (groupChange != null) {
    return ChatUpdateMessage(
      groupChange = groupChange
    )
  }

  body?.let { body ->
    return try {
      val decoded: ByteArray = Base64.decode(body)
      val context = DecryptedGroupV2Context.ADAPTER.decode(decoded)
      ChatUpdateMessage(
        groupChange = GroupsV2UpdateMessageConverter.translateDecryptedChange(selfIds = SignalStore.account.getServiceIds(), context)
      )
    } catch (e: IOException) {
      Log.w(TAG, ExportSkips.failedToParseGroupUpdate(this.dateSent), e)
      null
    }
  }

  return null
}

private fun BackupMessageRecord.toRemoteGroupUpdateFromGv1(exportState: ExportState): ChatUpdateMessage? {
  val aci = exportState.recipientIdToAci[this.fromRecipientId] ?: return null
  return ChatUpdateMessage(
    groupChange = GroupChangeChatUpdate(
      updates = listOf(
        GroupChangeChatUpdate.Update(
          genericGroupUpdate = GenericGroupUpdate(
            updaterAci = aci
          )
        )
      )
    )
  )
}

private fun BackupMessageRecord.toRemoteGroupExpireTimerUpdateFromGv1(exportState: ExportState): ChatUpdateMessage? {
  val updater = exportState.recipientIdToAci[this.fromRecipientId] ?: return null
  return ChatUpdateMessage(
    groupChange = GroupChangeChatUpdate(
      updates = listOf(
        GroupChangeChatUpdate.Update(
          groupExpirationTimerUpdate = GroupExpirationTimerUpdate(
            expiresInMs = this.expiresIn,
            updaterAci = updater
          )
        )
      )
    )
  )
}

private fun CallTable.Call.toRemoteCallUpdate(exportState: ExportState, messageRecord: BackupMessageRecord): ChatUpdateMessage? {
  return when (this.type) {
    CallTable.Type.GROUP_CALL -> {
      val groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(messageRecord.body)

      ChatUpdateMessage(
        groupCall = GroupCall(
          callId = this.callId,
          state = when (this.event) {
            CallTable.Event.MISSED -> GroupCall.State.MISSED
            CallTable.Event.ONGOING -> GroupCall.State.GENERIC
            CallTable.Event.ACCEPTED -> GroupCall.State.ACCEPTED
            CallTable.Event.NOT_ACCEPTED -> GroupCall.State.GENERIC
            CallTable.Event.MISSED_NOTIFICATION_PROFILE -> GroupCall.State.MISSED_NOTIFICATION_PROFILE
            CallTable.Event.GENERIC_GROUP_CALL -> GroupCall.State.GENERIC
            CallTable.Event.JOINED -> GroupCall.State.JOINED
            CallTable.Event.RINGING -> GroupCall.State.RINGING
            CallTable.Event.DECLINED -> GroupCall.State.DECLINED
            CallTable.Event.OUTGOING_RING -> GroupCall.State.OUTGOING_RING
            CallTable.Event.DELETE -> return null
          },
          ringerRecipientId = this.ringerRecipient?.toLong(),
          startedCallRecipientId = groupCallUpdateDetails.startedCallUuid.takeIf { it.isNotEmpty() }?.let { exportState.aciToRecipientId[it] },
          startedCallTimestamp = this.timestamp.clampToValidBackupRange(),
          endedCallTimestamp = groupCallUpdateDetails.endedCallTimestamp.clampToValidBackupRange().takeIf { it > 0 },
          read = messageRecord.read
        )
      )
    }

    CallTable.Type.AUDIO_CALL,
    CallTable.Type.VIDEO_CALL -> {
      ChatUpdateMessage(
        individualCall = IndividualCall(
          callId = this.callId,
          type = if (this.type == CallTable.Type.VIDEO_CALL) IndividualCall.Type.VIDEO_CALL else IndividualCall.Type.AUDIO_CALL,
          direction = if (this.direction == CallTable.Direction.INCOMING) IndividualCall.Direction.INCOMING else IndividualCall.Direction.OUTGOING,
          state = when (this.event) {
            CallTable.Event.MISSED -> IndividualCall.State.MISSED
            CallTable.Event.MISSED_NOTIFICATION_PROFILE -> IndividualCall.State.MISSED_NOTIFICATION_PROFILE
            CallTable.Event.ACCEPTED -> IndividualCall.State.ACCEPTED
            CallTable.Event.NOT_ACCEPTED -> IndividualCall.State.NOT_ACCEPTED
            CallTable.Event.ONGOING -> IndividualCall.State.ACCEPTED
            CallTable.Event.DELETE -> return null
            // Past bugs have caused some calls to have group event state (all below), map to 1:1 as best effort
            CallTable.Event.JOINED -> IndividualCall.State.ACCEPTED
            CallTable.Event.DECLINED -> IndividualCall.State.NOT_ACCEPTED
            CallTable.Event.GENERIC_GROUP_CALL,
            CallTable.Event.RINGING,
            CallTable.Event.OUTGOING_RING -> {
              Log.w(TAG, ExportSkips.individualCallStateNotMappable(messageRecord.dateSent, this.event))
              return null
            }
          },
          startedCallTimestamp = this.timestamp.clampToValidBackupRange(),
          read = messageRecord.read
        )
      )
    }

    CallTable.Type.AD_HOC_CALL -> throw IllegalArgumentException("AdHoc calls are not update messages!")
  }
}

private fun BackupMessageRecord.toRemotePaymentNotificationUpdate(db: SignalDatabase): PaymentNotification {
  val paymentUuid = UuidUtil.parseOrNull(this.body)
  val payment = if (paymentUuid != null) {
    db.paymentTable.getPayment(paymentUuid)
  } else {
    null
  }

  return if (payment == null) {
    PaymentNotification()
  } else {
    PaymentNotification(
      amountMob = payment.amount.serializeAmountString(),
      feeMob = payment.fee.serializeAmountString(),
      note = payment.note.takeUnless { it.isEmpty() },
      transactionDetails = payment.toRemoteTransactionDetails()
    )
  }
}

private fun BackupMessageRecord.toRemoteSharedContact(attachments: List<DatabaseAttachment>?): Contact? {
  if (this.sharedContacts.isNullOrEmpty()) {
    return null
  }

  val attachmentIdMap: Map<AttachmentId, DatabaseAttachment> = attachments?.associateBy { it.attachmentId } ?: emptyMap()

  return try {
    val jsonContacts = JSONArray(sharedContacts)
    if (jsonContacts.length() == 0) {
      return null
    }

    val contact: Contact = Contact.deserialize(jsonContacts.getJSONObject(0).toString())

    return if (contact.avatar != null && contact.avatar!!.attachmentId != null) {
      val attachment = attachmentIdMap[contact.avatar!!.attachmentId]

      val updatedAvatar = Contact.Avatar(
        contact.avatar!!.attachmentId,
        attachment,
        contact.avatar!!.isProfile
      )

      Contact(contact, updatedAvatar)
    } else {
      contact
    }
  } catch (e: JSONException) {
    Log.w(TAG, ExportSkips.failedToParseSharedContact(this.dateSent), e)
    null
  } catch (e: IOException) {
    Log.w(TAG, ExportSkips.failedToParseSharedContact(this.dateSent), e)
    null
  }
}

private fun BackupMessageRecord.toRemoteLinkPreviews(attachments: List<DatabaseAttachment>?): List<LinkPreview> {
  if (linkPreview.isNullOrEmpty()) {
    return emptyList()
  }
  val attachmentIdMap: Map<AttachmentId, DatabaseAttachment> = attachments?.associateBy { it.attachmentId } ?: emptyMap()

  try {
    val previews: MutableList<LinkPreview> = LinkedList()
    val jsonPreviews = JSONArray(linkPreview)

    for (i in 0 until jsonPreviews.length()) {
      val preview = LinkPreview.deserialize(jsonPreviews.getJSONObject(i).toString())

      if (preview.attachmentId != null) {
        val attachment = attachmentIdMap[preview.attachmentId]

        if (attachment != null) {
          previews += LinkPreview(preview.url, preview.title, preview.description, preview.date.clampToValidBackupRange(), attachment)
        } else {
          previews += preview
        }
      } else {
        previews += preview
      }
    }

    return previews
  } catch (e: JSONException) {
    Log.w(TAG, ExportOddities.failedToParseLinkPreview(this.dateSent), e)
  } catch (e: IOException) {
    Log.w(TAG, ExportOddities.failedToParseLinkPreview(this.dateSent), e)
  }

  return emptyList()
}

private fun LinkPreview.toRemoteLinkPreview(mediaArchiveEnabled: Boolean): org.thoughtcrime.securesms.backup.v2.proto.LinkPreview {
  return org.thoughtcrime.securesms.backup.v2.proto.LinkPreview(
    url = url,
    title = title.nullIfEmpty(),
    image = (thumbnail.orNull() as? DatabaseAttachment)?.toRemoteMessageAttachment(mediaArchiveEnabled)?.pointer,
    description = description.nullIfEmpty(),
    date = date.clampToValidBackupRange()
  )
}

private fun BackupMessageRecord.toRemoteViewOnceMessage(mediaArchiveEnabled: Boolean, reactionRecords: List<ReactionRecord>?, attachments: List<DatabaseAttachment>?): ViewOnceMessage {
  val attachment: DatabaseAttachment? = attachments?.firstOrNull()?.takeUnless { !it.hasData && it.size == 0L && it.archiveMediaId == null && it.width == 0 && it.height == 0 && it.blurHash == null }

  return ViewOnceMessage(
    attachment = attachment?.toRemoteMessageAttachment(mediaArchiveEnabled),
    reactions = reactionRecords?.toRemote() ?: emptyList()
  )
}

private fun BackupMessageRecord.toRemoteContactMessage(mediaArchiveEnabled: Boolean, reactionRecords: List<ReactionRecord>?, attachments: List<DatabaseAttachment>?): ContactMessage? {
  val sharedContact = toRemoteSharedContact(attachments) ?: return null

  return ContactMessage(
    contact = ContactAttachment(
      name = sharedContact.name.toRemote(),
      avatar = (sharedContact.avatar?.attachment as? DatabaseAttachment)?.toRemoteMessageAttachment(mediaArchiveEnabled)?.pointer,
      organization = sharedContact.organization ?: "",
      number = sharedContact.phoneNumbers.map { phone ->
        ContactAttachment.Phone(
          value_ = phone.number,
          type = phone.type.toRemote(),
          label = phone.label ?: ""
        )
      },
      email = sharedContact.emails.map { email ->
        ContactAttachment.Email(
          value_ = email.email,
          label = email.label ?: "",
          type = email.type.toRemote()
        )
      },
      address = sharedContact.postalAddresses.map { address ->
        ContactAttachment.PostalAddress(
          type = address.type.toRemote(),
          label = address.label ?: "",
          street = address.street ?: "",
          pobox = address.poBox ?: "",
          neighborhood = address.neighborhood ?: "",
          city = address.city ?: "",
          region = address.region ?: "",
          postcode = address.postalCode ?: "",
          country = address.country ?: ""
        )
      }
    ),
    reactions = reactionRecords.toRemote()
  )
}

private fun Contact.Name.toRemote(): ContactAttachment.Name? {
  if (givenName.isNullOrEmpty() &&
    familyName.isNullOrEmpty() &&
    prefix.isNullOrEmpty() &&
    suffix.isNullOrEmpty() &&
    middleName.isNullOrEmpty() &&
    nickname.isNullOrEmpty()
  ) {
    return null
  }

  return ContactAttachment.Name(
    givenName = givenName ?: "",
    familyName = familyName ?: "",
    prefix = prefix ?: "",
    suffix = suffix ?: "",
    middleName = middleName ?: "",
    nickname = nickname ?: ""
  )
}

private fun Contact.Phone.Type.toRemote(): ContactAttachment.Phone.Type {
  return when (this) {
    Contact.Phone.Type.HOME -> ContactAttachment.Phone.Type.HOME
    Contact.Phone.Type.MOBILE -> ContactAttachment.Phone.Type.MOBILE
    Contact.Phone.Type.WORK -> ContactAttachment.Phone.Type.WORK
    Contact.Phone.Type.CUSTOM -> ContactAttachment.Phone.Type.CUSTOM
  }
}

private fun Contact.Email.Type.toRemote(): ContactAttachment.Email.Type {
  return when (this) {
    Contact.Email.Type.HOME -> ContactAttachment.Email.Type.HOME
    Contact.Email.Type.MOBILE -> ContactAttachment.Email.Type.MOBILE
    Contact.Email.Type.WORK -> ContactAttachment.Email.Type.WORK
    Contact.Email.Type.CUSTOM -> ContactAttachment.Email.Type.CUSTOM
  }
}

private fun Contact.PostalAddress.Type.toRemote(): ContactAttachment.PostalAddress.Type {
  return when (this) {
    Contact.PostalAddress.Type.HOME -> ContactAttachment.PostalAddress.Type.HOME
    Contact.PostalAddress.Type.WORK -> ContactAttachment.PostalAddress.Type.WORK
    Contact.PostalAddress.Type.CUSTOM -> ContactAttachment.PostalAddress.Type.CUSTOM
  }
}

private fun BackupMessageRecord.toRemoteDirectStoryReplyMessage(mediaArchiveEnabled: Boolean, reactionRecords: List<ReactionRecord>?, attachments: List<DatabaseAttachment>?): DirectStoryReplyMessage? {
  if (this.body.isNullOrBlank()) {
    Log.w(TAG, ExportSkips.directStoryReplyHasNoBody(this.dateSent))
    return null
  }

  val isReaction = MessageTypes.isStoryReaction(this.type)

  return DirectStoryReplyMessage(
    emoji = if (isReaction) {
      this.body
    } else {
      null
    },
    textReply = if (!isReaction) {
      DirectStoryReplyMessage.TextReply(
        text = Text(
          body = this.body,
          bodyRanges = this.bodyRanges?.toRemoteBodyRanges(this.dateSent) ?: emptyList()
        ),
        longText = attachments?.firstOrNull { it.contentType == MediaUtil.LONG_TEXT }?.toRemoteFilePointer(mediaArchiveEnabled)
      )
    } else {
      null
    },
    reactions = reactionRecords.toRemote()
  )
}

private fun BackupMessageRecord.toRemoteStandardMessage(exportState: ExportState, mediaArchiveEnabled: Boolean, reactionRecords: List<ReactionRecord>?, mentions: List<Mention>?, attachments: List<DatabaseAttachment>?): StandardMessage {
  val text = body.nullIfBlank()?.let {
    Text(
      body = it,
      bodyRanges = (this.bodyRanges?.toRemoteBodyRanges(this.dateSent) ?: emptyList()) + (mentions?.toRemoteBodyRanges(exportState) ?: emptyList())
    )
  }

  val linkPreviews = this.toRemoteLinkPreviews(attachments)
  val linkPreviewAttachments = linkPreviews.mapNotNull { it.thumbnail.orElse(null) }.toSet()
  val quotedAttachments = attachments?.filter { it.quote } ?: emptyList()
  val longTextAttachment = attachments?.firstOrNull { it.contentType == "text/x-signal-plain" }
  val messageAttachments = attachments
    ?.filterNot { it.quote }
    ?.filterNot { linkPreviewAttachments.contains(it) }
    ?.filterNot { it == longTextAttachment }
    ?: emptyList()
  val hasVoiceNote = messageAttachments.any { it.voiceNote }
  return StandardMessage(
    quote = this.toRemoteQuote(mediaArchiveEnabled, quotedAttachments),
    text = text.takeUnless { hasVoiceNote },
    attachments = messageAttachments.toRemoteAttachments(mediaArchiveEnabled).withFixedVoiceNotes(textPresent = text != null || longTextAttachment != null),
    linkPreview = linkPreviews.map { it.toRemoteLinkPreview(mediaArchiveEnabled) },
    longText = longTextAttachment?.toRemoteFilePointer(mediaArchiveEnabled),
    reactions = reactionRecords.toRemote()
  )
}

private fun BackupMessageRecord.toRemoteQuote(mediaArchiveEnabled: Boolean, attachments: List<DatabaseAttachment>? = null): Quote? {
  if (this.quoteTargetSentTimestamp == MessageTable.QUOTE_NOT_PRESENT_ID || this.quoteAuthor <= 0) {
    return null
  }

  val localType = QuoteModel.Type.fromCode(this.quoteType)
  val remoteType = when (localType) {
    QuoteModel.Type.NORMAL -> {
      if (attachments?.any { it.contentType == MediaUtil.VIEW_ONCE } == true) {
        Quote.Type.VIEW_ONCE
      } else {
        Quote.Type.NORMAL
      }
    }
    QuoteModel.Type.GIFT_BADGE -> Quote.Type.GIFT_BADGE
  }

  val bodyRanges = this.quoteBodyRanges?.toRemoteBodyRanges(dateSent) ?: emptyList()
  val body = this.quoteBody?.takeUnless { it.isBlank() }?.let { body ->
    Text(
      body = body,
      bodyRanges = bodyRanges
    )
  }
  val attachments = if (remoteType == Quote.Type.VIEW_ONCE) {
    emptyList()
  } else {
    attachments?.toRemoteQuoteAttachments(mediaArchiveEnabled) ?: emptyList()
  }

  if (remoteType == Quote.Type.NORMAL && body == null && attachments.isEmpty()) {
    Log.w(TAG, ExportOddities.emptyQuote(this.dateSent))
    return null
  }

  return Quote(
    targetSentTimestamp = this.quoteTargetSentTimestamp.takeIf { !this.quoteMissing && it != MessageTable.QUOTE_TARGET_MISSING_ID }?.clampToValidBackupRange(),
    authorId = this.quoteAuthor,
    text = body,
    attachments = attachments,
    type = remoteType
  )
}

private fun BackupMessageRecord.toRemoteGiftBadgeUpdate(): BackupGiftBadge? {
  val giftBadge = try {
    GiftBadge.ADAPTER.decode(Base64.decode(this.body ?: ""))
  } catch (e: IOException) {
    Log.w(TAG, ExportSkips.failedToParseGiftBadge(this.dateSent), e)
    return null
  }

  return BackupGiftBadge(
    receiptCredentialPresentation = giftBadge.redemptionToken,
    state = when (giftBadge.redemptionState) {
      GiftBadge.RedemptionState.REDEEMED -> BackupGiftBadge.State.REDEEMED
      GiftBadge.RedemptionState.FAILED -> BackupGiftBadge.State.FAILED
      GiftBadge.RedemptionState.PENDING -> BackupGiftBadge.State.UNOPENED
      GiftBadge.RedemptionState.STARTED -> BackupGiftBadge.State.OPENED
    }
  )
}

private fun DatabaseAttachment.toRemoteStickerMessage(sentTimestamp: Long, mediaArchiveEnabled: Boolean, reactions: List<ReactionRecord>?): StickerMessage? {
  val stickerLocator = this.stickerLocator!!

  val packId = try {
    Hex.fromStringCondensed(stickerLocator.packId).takeIf { it.size == 16 } ?: throw IOException("Incorrect length!")
  } catch (e: IOException) {
    Log.w(TAG, ExportSkips.invalidChatItemStickerPackId(sentTimestamp), e)
    return null
  }

  val packKey = try {
    Hex.fromStringCondensed(stickerLocator.packKey).takeIf { it.size == 32 } ?: throw IOException("Incorrect length!")
  } catch (e: IOException) {
    Log.w(TAG, ExportSkips.invalidChatItemStickerPackKey(sentTimestamp), e)
    return null
  }

  return StickerMessage(
    sticker = Sticker(
      packId = packId.toByteString(),
      packKey = packKey.toByteString(),
      stickerId = stickerLocator.stickerId,
      emoji = stickerLocator.emoji,
      data_ = this.toRemoteMessageAttachment(mediaArchiveEnabled).pointer
    ),
    reactions = reactions.toRemote()
  )
}

private fun List<DatabaseAttachment>.toRemoteQuoteAttachments(mediaArchiveEnabled: Boolean): List<Quote.QuotedAttachment> {
  return this.map { attachment ->
    Quote.QuotedAttachment(
      contentType = attachment.contentType,
      fileName = attachment.fileName,
      thumbnail = attachment.toRemoteMessageAttachment(
        mediaArchiveEnabled = mediaArchiveEnabled,
        flagOverride = MessageAttachment.Flag.NONE,
        contentTypeOverride = "image/jpeg"
      )
    )
  }
}

private fun DatabaseAttachment.toRemoteMessageAttachment(mediaArchiveEnabled: Boolean, flagOverride: MessageAttachment.Flag? = null, contentTypeOverride: String? = null): MessageAttachment {
  return MessageAttachment(
    pointer = this.toRemoteFilePointer(mediaArchiveEnabled, contentTypeOverride),
    wasDownloaded = this.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE || this.transferState == AttachmentTable.TRANSFER_NEEDS_RESTORE,
    flag = if (flagOverride != null) {
      flagOverride
    } else if (this.voiceNote) {
      MessageAttachment.Flag.VOICE_MESSAGE
    } else if (this.videoGif) {
      MessageAttachment.Flag.GIF
    } else if (this.borderless) {
      MessageAttachment.Flag.BORDERLESS
    } else {
      MessageAttachment.Flag.NONE
    },
    clientUuid = this.uuid?.let { UuidUtil.toByteString(uuid) }
  )
}

private fun List<DatabaseAttachment>.toRemoteAttachments(mediaArchiveEnabled: Boolean): List<MessageAttachment> {
  return this.map { attachment ->
    attachment.toRemoteMessageAttachment(mediaArchiveEnabled)
  }
}

private fun PaymentTable.PaymentTransaction.toRemoteTransactionDetails(): PaymentNotification.TransactionDetails {
  if (this.failureReason != null || this.state == State.FAILED) {
    return PaymentNotification.TransactionDetails(failedTransaction = PaymentNotification.TransactionDetails.FailedTransaction(reason = this.failureReason.toRemote()))
  }

  return PaymentNotification.TransactionDetails(
    transaction = PaymentNotification.TransactionDetails.Transaction(
      status = this.state.toRemote(),
      timestamp = this.timestamp.clampToValidBackupRange(),
      blockIndex = this.blockIndex,
      blockTimestamp = this.blockTimestamp.clampToValidBackupRange(),
      mobileCoinIdentification = this.paymentMetaData.mobileCoinTxoIdentification?.let {
        PaymentNotification.TransactionDetails.MobileCoinTxoIdentification(
          publicKey = it.publicKey.takeIf { this.direction.isReceived } ?: emptyList(),
          keyImages = it.keyImages.takeIf { this.direction.isSent } ?: emptyList()
        )
      },
      transaction = this.transaction?.toByteString(),
      receipt = this.receipt?.toByteString()
    )
  )
}

private fun State.toRemote(): PaymentNotification.TransactionDetails.Transaction.Status {
  return when (this) {
    State.INITIAL -> PaymentNotification.TransactionDetails.Transaction.Status.INITIAL
    State.SUBMITTED -> PaymentNotification.TransactionDetails.Transaction.Status.SUBMITTED
    State.SUCCESSFUL -> PaymentNotification.TransactionDetails.Transaction.Status.SUCCESSFUL
    State.FAILED -> throw IllegalArgumentException("state cannot be failed")
  }
}

private fun FailureReason?.toRemote(): PaymentNotification.TransactionDetails.FailedTransaction.FailureReason {
  return when (this) {
    FailureReason.UNKNOWN -> PaymentNotification.TransactionDetails.FailedTransaction.FailureReason.GENERIC
    FailureReason.INSUFFICIENT_FUNDS -> PaymentNotification.TransactionDetails.FailedTransaction.FailureReason.INSUFFICIENT_FUNDS
    FailureReason.NETWORK -> PaymentNotification.TransactionDetails.FailedTransaction.FailureReason.NETWORK
    else -> PaymentNotification.TransactionDetails.FailedTransaction.FailureReason.GENERIC
  }
}

private fun List<Mention>.toRemoteBodyRanges(exportState: ExportState): List<BackupBodyRange> {
  return this.map {
    BackupBodyRange(
      start = it.start,
      length = it.length,
      mentionAci = exportState.recipientIdToAci[it.recipientId.toLong()]
    )
  }
}

private fun ByteArray.toRemoteBodyRanges(dateSent: Long): List<BackupBodyRange> {
  val decoded: BodyRangeList = try {
    BodyRangeList.ADAPTER.decode(this)
  } catch (e: IOException) {
    Log.w(TAG, ExportOddities.failedToParseBodyRangeList(dateSent), e)
    return emptyList()
  }

  return decoded.ranges.map {
    val mention = it.mentionUuid?.let { uuid -> UuidUtil.parseOrThrow(uuid) }?.toByteArray()?.toByteString()
    val style = if (mention == null) {
      it.style?.toRemote() ?: BackupBodyRange.Style.NONE
    } else {
      null
    }

    BackupBodyRange(
      start = it.start,
      length = it.length,
      mentionAci = mention,
      style = style
    )
  }
}

private fun BodyRangeList.BodyRange.Style.toRemote(): BackupBodyRange.Style {
  return when (this) {
    BodyRangeList.BodyRange.Style.BOLD -> BackupBodyRange.Style.BOLD
    BodyRangeList.BodyRange.Style.ITALIC -> BackupBodyRange.Style.ITALIC
    BodyRangeList.BodyRange.Style.STRIKETHROUGH -> BackupBodyRange.Style.STRIKETHROUGH
    BodyRangeList.BodyRange.Style.MONOSPACE -> BackupBodyRange.Style.MONOSPACE
    BodyRangeList.BodyRange.Style.SPOILER -> BackupBodyRange.Style.SPOILER
  }
}

private fun List<ReactionRecord>?.toRemote(): List<Reaction> {
  return this
    ?.map {
      Reaction(
        emoji = it.emoji,
        authorId = it.author.toLong(),
        sentTimestamp = it.dateSent.clampToValidBackupRange(),
        sortOrder = it.dateReceived
      )
    } ?: emptyList()
}

private fun BackupMessageRecord.toRemoteSendStatus(isGroupThread: Boolean, groupReceipts: List<GroupReceiptTable.GroupReceiptInfo>?, exportState: ExportState): List<SendStatus> {
  if (isGroupThread || !groupReceipts.isNullOrEmpty()) {
    return groupReceipts.toRemoteSendStatus(this, this.networkFailureRecipientIds, this.identityMismatchRecipientIds, exportState)
  }

  if (!exportState.recipientIds.contains(this.toRecipientId)) {
    return emptyList()
  }

  val statusBuilder = SendStatus.Builder()
    .recipientId(this.toRecipientId)
    .timestamp(max(this.receiptTimestamp, 0))

  when {
    this.identityMismatchRecipientIds.contains(this.toRecipientId) -> {
      statusBuilder.failed = SendStatus.Failed(
        reason = SendStatus.Failed.FailureReason.IDENTITY_KEY_MISMATCH
      )
    }
    this.networkFailureRecipientIds.contains(this.toRecipientId) -> {
      statusBuilder.failed = SendStatus.Failed(
        reason = SendStatus.Failed.FailureReason.NETWORK
      )
    }
    this.viewed -> {
      statusBuilder.viewed = SendStatus.Viewed(
        sealedSender = this.sealedSender
      )
    }
    this.hasReadReceipt -> {
      statusBuilder.read = SendStatus.Read(
        sealedSender = this.sealedSender
      )
    }
    this.hasDeliveryReceipt -> {
      statusBuilder.delivered = SendStatus.Delivered(
        sealedSender = this.sealedSender
      )
    }
    this.baseType == MessageTypes.BASE_SENT_FAILED_TYPE -> {
      statusBuilder.failed = SendStatus.Failed(
        reason = SendStatus.Failed.FailureReason.UNKNOWN
      )
    }
    this.baseType == MessageTypes.BASE_SENDING_SKIPPED_TYPE -> {
      statusBuilder.skipped = SendStatus.Skipped()
    }
    this.baseType == MessageTypes.BASE_SENT_TYPE -> {
      statusBuilder.sent = SendStatus.Sent(
        sealedSender = this.sealedSender
      )
    }
    else -> {
      statusBuilder.pending = SendStatus.Pending()
    }
  }

  return listOf(statusBuilder.build())
}

private fun List<GroupReceiptTable.GroupReceiptInfo>?.toRemoteSendStatus(messageRecord: BackupMessageRecord, networkFailureRecipientIds: Set<Long>, identityMismatchRecipientIds: Set<Long>, exportState: ExportState): List<SendStatus> {
  if (this == null) {
    return emptyList()
  }

  return this
    .filter { exportState.recipientIds.contains(it.recipientId.toLong()) }
    .map {
      val statusBuilder = SendStatus.Builder()
        .recipientId(it.recipientId.toLong())
        .timestamp(max(it.timestamp, 0))

      when {
        identityMismatchRecipientIds.contains(it.recipientId.toLong()) -> {
          statusBuilder.failed = SendStatus.Failed(
            reason = SendStatus.Failed.FailureReason.IDENTITY_KEY_MISMATCH
          )
        }
        MessageTypes.isFailedMessageType(messageRecord.type) && networkFailureRecipientIds.contains(it.recipientId.toLong()) -> {
          statusBuilder.failed = SendStatus.Failed(
            reason = SendStatus.Failed.FailureReason.NETWORK
          )
        }
        MessageTypes.isFailedMessageType(messageRecord.type) -> {
          statusBuilder.failed = SendStatus.Failed(
            reason = SendStatus.Failed.FailureReason.UNKNOWN
          )
        }
        it.status == GroupReceiptTable.STATUS_UNKNOWN -> {
          statusBuilder.pending = SendStatus.Pending()
        }
        it.status == GroupReceiptTable.STATUS_UNDELIVERED -> {
          statusBuilder.sent = SendStatus.Sent(
            sealedSender = it.isUnidentified
          )
        }
        it.status == GroupReceiptTable.STATUS_DELIVERED -> {
          statusBuilder.delivered = SendStatus.Delivered(
            sealedSender = it.isUnidentified
          )
        }
        it.status == GroupReceiptTable.STATUS_READ -> {
          statusBuilder.read = SendStatus.Read(
            sealedSender = it.isUnidentified
          )
        }
        it.status == GroupReceiptTable.STATUS_VIEWED -> {
          statusBuilder.viewed = SendStatus.Viewed(
            sealedSender = it.isUnidentified
          )
        }
        it.status == GroupReceiptTable.STATUS_SKIPPED -> {
          statusBuilder.skipped = SendStatus.Skipped()
        }
        else -> {
          statusBuilder.pending = SendStatus.Pending()
        }
      }

      statusBuilder.build()
    }
}

private fun String?.parseNetworkFailures(): Set<Long> {
  if (this.isNullOrBlank()) {
    return emptySet()
  }

  return try {
    JsonUtils.fromJson(this, NetworkFailureSet::class.java).items.map { it.recipientId.toLong() }.toSet()
  } catch (e: IOException) {
    emptySet()
  }
}

private fun String?.parseIdentityMismatches(): Set<Long> {
  if (this.isNullOrBlank()) {
    return emptySet()
  }

  return try {
    JsonUtils.fromJson(this, IdentityKeyMismatchSet::class.java).items.map { it.recipientId.toLong() }.toSet()
  } catch (e: IOException) {
    emptySet()
  }
}

private fun ByteArray?.parseMessageExtras(): MessageExtras? {
  if (this == null) {
    return null
  }
  return try {
    MessageExtras.ADAPTER.decode(this)
  } catch (e: java.lang.Exception) {
    null
  }
}

private fun Long.isSmsType(): Boolean {
  if (MessageTypes.isSecureType(this)) {
    return false
  }

  if (MessageTypes.isCallLog(this)) {
    return false
  }

  return MessageTypes.isOutgoingMessageType(this) || MessageTypes.isInboxType(this)
}

private fun Long.isDirectionlessType(): Boolean {
  return MessageTypes.isCallLog(this) ||
    MessageTypes.isExpirationTimerUpdate(this) ||
    MessageTypes.isThreadMergeType(this) ||
    MessageTypes.isSessionSwitchoverType(this) ||
    MessageTypes.isProfileChange(this) ||
    MessageTypes.isJoinedType(this) ||
    MessageTypes.isIdentityUpdate(this) ||
    MessageTypes.isIdentityVerified(this) ||
    MessageTypes.isIdentityDefault(this) ||
    MessageTypes.isReleaseChannelDonationRequest(this) ||
    MessageTypes.isChangeNumber(this) ||
    MessageTypes.isEndSessionType(this) ||
    MessageTypes.isChatSessionRefresh(this) ||
    MessageTypes.isBadDecryptType(this) ||
    MessageTypes.isPaymentsActivated(this) ||
    MessageTypes.isPaymentsRequestToActivate(this) ||
    MessageTypes.isUnsupportedMessageType(this) ||
    MessageTypes.isReportedSpam(this) ||
    MessageTypes.isMessageRequestAccepted(this) ||
    MessageTypes.isBlocked(this) ||
    MessageTypes.isUnblocked(this) ||
    MessageTypes.isGroupCall(this) ||
    MessageTypes.isGroupUpdate(this) ||
    MessageTypes.isGroupV1MigrationEvent(this) ||
    MessageTypes.isGroupQuit(this)
}

private fun Long.isIdentityVerifyType(): Boolean {
  return MessageTypes.isIdentityVerified(this) ||
    MessageTypes.isIdentityDefault(this)
}

private fun String.e164ToLong(): Long? {
  val fixed = if (this.startsWith("+")) {
    this.substring(1)
  } else {
    this
  }

  return fixed.toLongOrNull()
}

private fun <T> ExecutorService.submitTyped(callable: Callable<T>): Future<T> {
  return this.submit(callable)
}

fun ChatItem.validateChatItem(): ChatItem? {
  if (this.standardMessage == null &&
    this.contactMessage == null &&
    this.stickerMessage == null &&
    this.remoteDeletedMessage == null &&
    this.updateMessage == null &&
    this.paymentNotification == null &&
    this.giftBadge == null &&
    this.viewOnceMessage == null &&
    this.directStoryReplyMessage == null
  ) {
    Log.w(TAG, ExportSkips.emptyChatItem(this.dateSent))
    return null
  }
  return this
}

fun List<ChatItem>.repairRevisions(current: ChatItem.Builder): List<ChatItem> {
  return if (current.standardMessage != null) {
    val filtered = this
      .filter { it.standardMessage != null }
      .map { it.withDowngradeVoiceNotes() }

    if (this.size != filtered.size) {
      Log.w(TAG, ExportOddities.mismatchedRevisionHistory(current.dateSent))
    }

    filtered
  } else if (current.directStoryReplyMessage != null) {
    val filtered = this.filter { it.directStoryReplyMessage != null }
    if (this.size != filtered.size) {
      Log.w(TAG, ExportOddities.mismatchedRevisionHistory(current.dateSent))
    }
    filtered
  } else {
    Log.w(TAG, ExportOddities.revisionsOnUnexpectedMessageType(current.dateSent))
    emptyList()
  }
}

private fun Text?.isNullOrBlank(): Boolean {
  return this == null || this.body.isBlank()
}

private fun List<MessageAttachment>.withFixedVoiceNotes(textPresent: Boolean): List<MessageAttachment> {
  return this.map {
    if (textPresent && it.flag == MessageAttachment.Flag.VOICE_MESSAGE) {
      it.copy(flag = MessageAttachment.Flag.NONE)
    } else {
      it
    }
  }
}

private fun ChatItem.withDowngradeVoiceNotes(): ChatItem {
  if (this.standardMessage == null) {
    return this
  }

  if (this.standardMessage.attachments.none { it.flag == MessageAttachment.Flag.VOICE_MESSAGE }) {
    return this
  }

  return this.copy(
    standardMessage = this.standardMessage.copy(
      attachments = this.standardMessage.attachments.map {
        if (it.flag == MessageAttachment.Flag.VOICE_MESSAGE) {
          it.copy(flag = MessageAttachment.Flag.NONE)
        } else {
          it
        }
      }
    )
  )
}

private fun Cursor.toBackupMessageRecord(pastIds: Set<Long>, backupStartTime: Long): BackupMessageRecord? {
  val id = this.requireLong(MessageTable.ID)
  if (pastIds.contains(id)) {
    return null
  }

  val expiresIn = this.requireLong(MessageTable.EXPIRES_IN)
  val expireStarted = this.requireLong(MessageTable.EXPIRE_STARTED)

  return BackupMessageRecord(
    id = id,
    dateSent = this.requireLong(MessageTable.DATE_SENT).clampToValidBackupRange(),
    dateReceived = this.requireLong(MessageTable.DATE_RECEIVED).clampToValidBackupRange(),
    dateServer = this.requireLong(MessageTable.DATE_SERVER).clampToValidBackupRange(),
    type = this.requireLong(MessageTable.TYPE),
    threadId = this.requireLong(MessageTable.THREAD_ID),
    body = this.requireString(MessageTable.BODY),
    bodyRanges = this.requireBlob(MessageTable.MESSAGE_RANGES),
    fromRecipientId = this.requireLong(MessageTable.FROM_RECIPIENT_ID),
    toRecipientId = this.requireLong(MessageTable.TO_RECIPIENT_ID),
    expiresIn = expiresIn,
    expireStarted = expireStarted,
    remoteDeleted = this.requireBoolean(MessageTable.REMOTE_DELETED),
    sealedSender = this.requireBoolean(MessageTable.UNIDENTIFIED),
    linkPreview = this.requireString(MessageTable.LINK_PREVIEWS),
    sharedContacts = this.requireString(MessageTable.SHARED_CONTACTS),
    quoteTargetSentTimestamp = this.requireLong(MessageTable.QUOTE_ID).clampToValidBackupRange(),
    quoteAuthor = this.requireLong(MessageTable.QUOTE_AUTHOR),
    quoteBody = this.requireString(MessageTable.QUOTE_BODY),
    quoteMissing = this.requireBoolean(MessageTable.QUOTE_MISSING),
    quoteBodyRanges = this.requireBlob(MessageTable.QUOTE_BODY_RANGES),
    quoteType = this.requireInt(MessageTable.QUOTE_TYPE),
    originalMessageId = this.requireLongOrNull(MessageTable.ORIGINAL_MESSAGE_ID),
    latestRevisionId = this.requireLongOrNull(MessageTable.LATEST_REVISION_ID),
    hasDeliveryReceipt = this.requireBoolean(MessageTable.HAS_DELIVERY_RECEIPT),
    viewed = this.requireBoolean(MessageTable.VIEWED_COLUMN),
    hasReadReceipt = this.requireBoolean(MessageTable.HAS_READ_RECEIPT),
    read = this.requireBoolean(MessageTable.READ),
    receiptTimestamp = this.requireLong(MessageTable.RECEIPT_TIMESTAMP),
    networkFailureRecipientIds = this.requireString(MessageTable.NETWORK_FAILURES).parseNetworkFailures(),
    identityMismatchRecipientIds = this.requireString(MessageTable.MISMATCHED_IDENTITIES).parseIdentityMismatches(),
    baseType = this.requireLong(MessageTable.TYPE) and MessageTypes.BASE_TYPE_MASK,
    messageExtras = this.requireBlob(MessageTable.MESSAGE_EXTRAS).parseMessageExtras(),
    viewOnce = this.requireBoolean(MessageTable.VIEW_ONCE),
    parentStoryId = this.requireLong(MessageTable.PARENT_STORY_ID)
  )
}

private class BackupMessageRecord(
  val id: Long,
  val dateSent: Long,
  val dateReceived: Long,
  val dateServer: Long,
  val type: Long,
  val threadId: Long,
  val body: String?,
  val bodyRanges: ByteArray?,
  val fromRecipientId: Long,
  val toRecipientId: Long,
  val expiresIn: Long,
  val expireStarted: Long,
  val remoteDeleted: Boolean,
  val sealedSender: Boolean,
  val linkPreview: String?,
  val sharedContacts: String?,
  val quoteTargetSentTimestamp: Long,
  val quoteAuthor: Long,
  val quoteBody: String?,
  val quoteMissing: Boolean,
  val quoteBodyRanges: ByteArray?,
  val quoteType: Int,
  val originalMessageId: Long?,
  val parentStoryId: Long,
  val latestRevisionId: Long?,
  val hasDeliveryReceipt: Boolean,
  val hasReadReceipt: Boolean,
  val viewed: Boolean,
  val receiptTimestamp: Long,
  val read: Boolean,
  val networkFailureRecipientIds: Set<Long>,
  val identityMismatchRecipientIds: Set<Long>,
  val baseType: Long,
  val messageExtras: MessageExtras?,
  val viewOnce: Boolean
)

private data class ExtraMessageData(
  val mentionsById: Map<Long, List<Mention>>,
  val reactionsById: Map<Long, List<ReactionRecord>>,
  val attachmentsById: Map<Long, List<DatabaseAttachment>>,
  val groupReceiptsById: Map<Long, List<GroupReceiptTable.GroupReceiptInfo>>
)

private enum class Direction {
  OUTGOING, INCOMING, DIRECTIONLESS
}

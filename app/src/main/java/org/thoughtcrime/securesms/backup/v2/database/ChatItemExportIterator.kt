/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import com.annimon.stream.Stream
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.Base64.decodeOrThrow
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.backup.v2.proto.CallChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.ChatUpdateMessage
import org.thoughtcrime.securesms.backup.v2.proto.ExpirationTimerChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.GroupCallChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.IndividualCallChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.ProfileChangeChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.Quote
import org.thoughtcrime.securesms.backup.v2.proto.Reaction
import org.thoughtcrime.securesms.backup.v2.proto.RemoteDeletedMessage
import org.thoughtcrime.securesms.backup.v2.proto.SendStatus
import org.thoughtcrime.securesms.backup.v2.proto.SessionSwitchoverChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.SimpleChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.StandardMessage
import org.thoughtcrime.securesms.backup.v2.proto.Text
import org.thoughtcrime.securesms.backup.v2.proto.ThreadMergeChatUpdate
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.calls
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet
import org.thoughtcrime.securesms.database.documents.NetworkFailureSet
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.util.JsonUtils
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.util.toByteArray
import java.io.Closeable
import java.io.IOException
import java.util.LinkedList
import java.util.Queue
import java.util.UUID
import org.thoughtcrime.securesms.backup.v2.proto.BodyRange as BackupBodyRange

/**
 * An iterator for chat items with a clever performance twist: rather than do the extra queries one at a time (for reactions,
 * attachments, etc), this will populate items in batches, doing bulk lookups to improve throughput. We keep these in a buffer
 * and only do more queries when the buffer is empty.
 *
 * All of this complexity is hidden from the user -- they just get a normal iterator interface.
 */
class ChatItemExportIterator(private val cursor: Cursor, private val batchSize: Int) : Iterator<ChatItem>, Closeable {

  companion object {
    private val TAG = Log.tag(ChatItemExportIterator::class.java)

    const val COLUMN_BASE_TYPE = "base_type"
  }

  /**
   * A queue of already-parsed ChatItems. Processing in batches means that we read ahead in the cursor and put
   * the pending items here.
   */
  private val buffer: Queue<ChatItem> = LinkedList()

  override fun hasNext(): Boolean {
    return buffer.isNotEmpty() || (cursor.count > 0 && !cursor.isLast && !cursor.isAfterLast)
  }

  override fun next(): ChatItem {
    if (buffer.isNotEmpty()) {
      return buffer.remove()
    }

    val records: LinkedHashMap<Long, BackupMessageRecord> = linkedMapOf()

    for (i in 0 until batchSize) {
      if (cursor.moveToNext()) {
        val record = cursor.toBackupMessageRecord()
        records[record.id] = record
      } else {
        break
      }
    }

    val reactionsById: Map<Long, List<ReactionRecord>> = SignalDatabase.reactions.getReactionsForMessages(records.keys)
    val groupReceiptsById: Map<Long, List<GroupReceiptTable.GroupReceiptInfo>> = SignalDatabase.groupReceipts.getGroupReceiptInfoForMessages(records.keys)

    for ((id, record) in records) {
      val builder = record.toBasicChatItemBuilder(groupReceiptsById[id])

      when {
        record.remoteDeleted -> builder.remoteDeletedMessage = RemoteDeletedMessage()
        MessageTypes.isJoinedType(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.JOINED_SIGNAL))
        MessageTypes.isIdentityUpdate(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.IDENTITY_UPDATE))
        MessageTypes.isIdentityVerified(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.IDENTITY_VERIFIED))
        MessageTypes.isIdentityDefault(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.IDENTITY_DEFAULT))
        MessageTypes.isChangeNumber(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.CHANGE_NUMBER))
        MessageTypes.isBoostRequest(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.BOOST_REQUEST))
        MessageTypes.isEndSessionType(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.END_SESSION))
        MessageTypes.isChatSessionRefresh(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.CHAT_SESSION_REFRESH))
        MessageTypes.isBadDecryptType(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.BAD_DECRYPT))
        MessageTypes.isPaymentsActivated(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.PAYMENTS_ACTIVATED))
        MessageTypes.isPaymentsRequestToActivate(record.type) -> builder.updateMessage = ChatUpdateMessage(simpleUpdate = SimpleChatUpdate(type = SimpleChatUpdate.Type.PAYMENT_ACTIVATION_REQUEST))
        MessageTypes.isExpirationTimerUpdate(record.type) -> builder.updateMessage = ChatUpdateMessage(expirationTimerChange = ExpirationTimerChatUpdate((record.expiresIn / 1000).toInt()))
        MessageTypes.isProfileChange(record.type) -> {
          builder.updateMessage = ChatUpdateMessage(
            profileChange = try {
              val decoded: ByteArray = Base64.decode(record.body!!)
              val profileChangeDetails = ProfileChangeDetails.ADAPTER.decode(decoded)
              if (profileChangeDetails.profileNameChange != null) {
                ProfileChangeChatUpdate(previousName = profileChangeDetails.profileNameChange.previous, newName = profileChangeDetails.profileNameChange.newValue)
              } else {
                ProfileChangeChatUpdate()
              }
            } catch (e: IOException) {
              Log.w(TAG, "Profile name change details could not be read", e)
              ProfileChangeChatUpdate()
            }
          )
        }
        MessageTypes.isSessionSwitchoverType(record.type) -> {
          builder.updateMessage = ChatUpdateMessage(
            sessionSwitchover = try {
              val event = SessionSwitchoverEvent.ADAPTER.decode(decodeOrThrow(record.body!!))
              SessionSwitchoverChatUpdate(event.e164.e164ToLong()!!)
            } catch (e: Exception) {
              SessionSwitchoverChatUpdate()
            }
          )
        }
        MessageTypes.isThreadMergeType(record.type) -> {
          builder.updateMessage = ChatUpdateMessage(
            threadMerge = try {
              val event = ThreadMergeEvent.ADAPTER.decode(decodeOrThrow(record.body!!))
              ThreadMergeChatUpdate(event.previousE164.e164ToLong()!!)
            } catch (e: Exception) {
              ThreadMergeChatUpdate()
            }
          )
        }
        MessageTypes.isCallLog(record.type) -> {
          val call = calls.getCallByMessageId(record.id)
          if (call != null) {
            builder.updateMessage = ChatUpdateMessage(callingMessage = CallChatUpdate(callId = call.callId))
          } else {
            when {
              MessageTypes.isMissedAudioCall(record.type) -> {
                builder.updateMessage = ChatUpdateMessage(callingMessage = CallChatUpdate(callMessage = IndividualCallChatUpdate(type = IndividualCallChatUpdate.Type.MISSED_AUDIO_CALL)))
              }
              MessageTypes.isMissedVideoCall(record.type) -> {
                builder.updateMessage = ChatUpdateMessage(callingMessage = CallChatUpdate(callMessage = IndividualCallChatUpdate(type = IndividualCallChatUpdate.Type.MISSED_VIDEO_CALL)))
              }
              MessageTypes.isIncomingAudioCall(record.type) -> {
                builder.updateMessage = ChatUpdateMessage(callingMessage = CallChatUpdate(callMessage = IndividualCallChatUpdate(type = IndividualCallChatUpdate.Type.INCOMING_AUDIO_CALL)))
              }
              MessageTypes.isIncomingVideoCall(record.type) -> {
                builder.updateMessage = ChatUpdateMessage(callingMessage = CallChatUpdate(callMessage = IndividualCallChatUpdate(type = IndividualCallChatUpdate.Type.INCOMING_VIDEO_CALL)))
              }
              MessageTypes.isOutgoingAudioCall(record.type) -> {
                builder.updateMessage = ChatUpdateMessage(callingMessage = CallChatUpdate(callMessage = IndividualCallChatUpdate(type = IndividualCallChatUpdate.Type.OUTGOING_AUDIO_CALL)))
              }
              MessageTypes.isOutgoingVideoCall(record.type) -> {
                builder.updateMessage = ChatUpdateMessage(callingMessage = CallChatUpdate(callMessage = IndividualCallChatUpdate(type = IndividualCallChatUpdate.Type.OUTGOING_VIDEO_CALL)))
              }
              MessageTypes.isGroupCall(record.type) -> {
                try {
                  val groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(record.body)

                  val joinedMembers = Stream.of(groupCallUpdateDetails.inCallUuids)
                    .map { uuid: String? -> UuidUtil.parseOrNull(uuid) }
                    .withoutNulls()
                    .map { obj: UUID? -> ACI.from(obj!!).toByteString() }
                    .toList()
                  builder.updateMessage = ChatUpdateMessage(
                    callingMessage = CallChatUpdate(
                      groupCall = GroupCallChatUpdate(
                        startedCallAci = ACI.from(UuidUtil.parseOrThrow(groupCallUpdateDetails.startedCallUuid)).toByteString(),
                        startedCallTimestamp = groupCallUpdateDetails.startedCallTimestamp,
                        inCallAcis = joinedMembers
                      )
                    )
                  )
                } catch (exception: java.lang.Exception) {
                  continue
                }
              }
            }
          }
        }
        record.body == null -> {
          Log.w(TAG, "Record missing a body, skipping")
          continue
        }
        else -> builder.standardMessage = record.toTextMessage(reactionsById[id])
      }

      buffer += builder.build()
    }

    return if (buffer.isNotEmpty()) {
      buffer.remove()
    } else {
      throw NoSuchElementException()
    }
  }

  override fun close() {
    cursor.close()
  }

  private fun String.e164ToLong(): Long? {
    val fixed = if (this.startsWith("+")) {
      this.substring(1)
    } else {
      this
    }

    return fixed.toLongOrNull()
  }

  private fun BackupMessageRecord.toBasicChatItemBuilder(groupReceipts: List<GroupReceiptTable.GroupReceiptInfo>?): ChatItem.Builder {
    val record = this

    return ChatItem.Builder().apply {
      chatId = record.threadId
      authorId = record.fromRecipientId
      dateSent = record.dateSent
      sealedSender = record.sealedSender
      expireStartDate = if (record.expireStarted > 0) record.expireStarted else null
      expiresInMs = if (record.expiresIn > 0) record.expiresIn else null
      revisions = emptyList()
      sms = !MessageTypes.isSecureType(record.type)

      if (MessageTypes.isOutgoingMessageType(record.type)) {
        outgoing = ChatItem.OutgoingMessageDetails(
          sendStatus = record.toBackupSendStatus(groupReceipts)
        )
      } else {
        incoming = ChatItem.IncomingMessageDetails(
          dateServerSent = record.dateServer,
          dateReceived = record.dateReceived,
          read = record.read
        )
      }
    }
  }

  private fun BackupMessageRecord.toTextMessage(reactionRecords: List<ReactionRecord>?): StandardMessage {
    return StandardMessage(
      quote = this.toQuote(),
      text = Text(
        body = this.body!!,
        bodyRanges = this.bodyRanges?.toBackupBodyRanges() ?: emptyList()
      ),
      // TODO Link previews!
      linkPreview = emptyList(),
      longText = null,
      reactions = reactionRecords.toBackupReactions()
    )
  }

  private fun BackupMessageRecord.toQuote(): Quote? {
    return if (this.quoteTargetSentTimestamp != MessageTable.QUOTE_NOT_PRESENT_ID && this.quoteAuthor > 0) {
      // TODO Attachments!
      val type = QuoteModel.Type.fromCode(this.quoteType)
      Quote(
        targetSentTimestamp = this.quoteTargetSentTimestamp.takeIf { !this.quoteMissing && it != MessageTable.QUOTE_TARGET_MISSING_ID },
        authorId = this.quoteAuthor,
        text = this.quoteBody,
        bodyRanges = this.quoteBodyRanges?.toBackupBodyRanges() ?: emptyList(),
        type = when (type) {
          QuoteModel.Type.NORMAL -> Quote.Type.NORMAL
          QuoteModel.Type.GIFT_BADGE -> Quote.Type.GIFTBADGE
        }
      )
    } else {
      null
    }
  }

  private fun ByteArray.toBackupBodyRanges(): List<BackupBodyRange> {
    val decoded: BodyRangeList = try {
      BodyRangeList.ADAPTER.decode(this)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to decode BodyRangeList!")
      return emptyList()
    }

    return decoded.ranges.map {
      BackupBodyRange(
        start = it.start,
        length = it.length,
        mentionAci = it.mentionUuid?.let { UuidUtil.parseOrThrow(it) }?.toByteArray()?.toByteString(),
        style = it.style?.toBackupBodyRangeStyle()
      )
    }
  }

  private fun BodyRangeList.BodyRange.Style.toBackupBodyRangeStyle(): BackupBodyRange.Style {
    return when (this) {
      BodyRangeList.BodyRange.Style.BOLD -> BackupBodyRange.Style.BOLD
      BodyRangeList.BodyRange.Style.ITALIC -> BackupBodyRange.Style.ITALIC
      BodyRangeList.BodyRange.Style.STRIKETHROUGH -> BackupBodyRange.Style.STRIKETHROUGH
      BodyRangeList.BodyRange.Style.MONOSPACE -> BackupBodyRange.Style.MONOSPACE
      BodyRangeList.BodyRange.Style.SPOILER -> BackupBodyRange.Style.SPOILER
    }
  }

  private fun List<ReactionRecord>?.toBackupReactions(): List<Reaction> {
    return this
      ?.map {
        Reaction(
          emoji = it.emoji,
          authorId = it.author.toLong(),
          sentTimestamp = it.dateSent,
          receivedTimestamp = it.dateReceived
        )
      } ?: emptyList()
  }

  private fun BackupMessageRecord.toBackupSendStatus(groupReceipts: List<GroupReceiptTable.GroupReceiptInfo>?): List<SendStatus> {
    if (!MessageTypes.isOutgoingMessageType(this.type)) {
      return emptyList()
    }

    if (!groupReceipts.isNullOrEmpty()) {
      return groupReceipts.toBackupSendStatus(this.networkFailureRecipientIds, this.identityMismatchRecipientIds)
    }

    val status: SendStatus.Status = when {
      this.viewed -> SendStatus.Status.VIEWED
      this.hasReadReceipt -> SendStatus.Status.READ
      this.hasDeliveryReceipt -> SendStatus.Status.DELIVERED
      this.baseType == MessageTypes.BASE_SENT_TYPE -> SendStatus.Status.SENT
      MessageTypes.isFailedMessageType(this.type) -> SendStatus.Status.FAILED
      else -> SendStatus.Status.PENDING
    }

    return listOf(
      SendStatus(
        recipientId = this.toRecipientId,
        deliveryStatus = status,
        lastStatusUpdateTimestamp = this.receiptTimestamp,
        sealedSender = this.sealedSender,
        networkFailure = this.networkFailureRecipientIds.contains(this.toRecipientId),
        identityKeyMismatch = this.identityMismatchRecipientIds.contains(this.toRecipientId)
      )
    )
  }

  private fun List<GroupReceiptTable.GroupReceiptInfo>.toBackupSendStatus(networkFailureRecipientIds: Set<Long>, identityMismatchRecipientIds: Set<Long>): List<SendStatus> {
    return this.map {
      SendStatus(
        recipientId = it.recipientId.toLong(),
        deliveryStatus = it.status.toBackupDeliveryStatus(),
        sealedSender = it.isUnidentified,
        lastStatusUpdateTimestamp = it.timestamp,
        networkFailure = networkFailureRecipientIds.contains(it.recipientId.toLong()),
        identityKeyMismatch = identityMismatchRecipientIds.contains(it.recipientId.toLong())
      )
    }
  }

  private fun Int.toBackupDeliveryStatus(): SendStatus.Status {
    return when (this) {
      GroupReceiptTable.STATUS_UNDELIVERED -> SendStatus.Status.PENDING
      GroupReceiptTable.STATUS_DELIVERED -> SendStatus.Status.DELIVERED
      GroupReceiptTable.STATUS_READ -> SendStatus.Status.READ
      GroupReceiptTable.STATUS_VIEWED -> SendStatus.Status.VIEWED
      GroupReceiptTable.STATUS_SKIPPED -> SendStatus.Status.SKIPPED
      else -> SendStatus.Status.SKIPPED
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

  private fun Cursor.toBackupMessageRecord(): BackupMessageRecord {
    return BackupMessageRecord(
      id = this.requireLong(MessageTable.ID),
      dateSent = this.requireLong(MessageTable.DATE_SENT),
      dateReceived = this.requireLong(MessageTable.DATE_RECEIVED),
      dateServer = this.requireLong(MessageTable.DATE_SERVER),
      type = this.requireLong(MessageTable.TYPE),
      threadId = this.requireLong(MessageTable.THREAD_ID),
      body = this.requireString(MessageTable.BODY),
      bodyRanges = this.requireBlob(MessageTable.MESSAGE_RANGES),
      fromRecipientId = this.requireLong(MessageTable.FROM_RECIPIENT_ID),
      toRecipientId = this.requireLong(MessageTable.TO_RECIPIENT_ID),
      expiresIn = this.requireLong(MessageTable.EXPIRES_IN),
      expireStarted = this.requireLong(MessageTable.EXPIRE_STARTED),
      remoteDeleted = this.requireBoolean(MessageTable.REMOTE_DELETED),
      sealedSender = this.requireBoolean(MessageTable.UNIDENTIFIED),
      quoteTargetSentTimestamp = this.requireLong(MessageTable.QUOTE_ID),
      quoteAuthor = this.requireLong(MessageTable.QUOTE_AUTHOR),
      quoteBody = this.requireString(MessageTable.QUOTE_BODY),
      quoteMissing = this.requireBoolean(MessageTable.QUOTE_MISSING),
      quoteBodyRanges = this.requireBlob(MessageTable.QUOTE_BODY_RANGES),
      quoteType = this.requireInt(MessageTable.QUOTE_TYPE),
      originalMessageId = this.requireLong(MessageTable.ORIGINAL_MESSAGE_ID),
      latestRevisionId = this.requireLong(MessageTable.LATEST_REVISION_ID),
      hasDeliveryReceipt = this.requireBoolean(MessageTable.HAS_DELIVERY_RECEIPT),
      viewed = this.requireBoolean(MessageTable.VIEWED_COLUMN),
      hasReadReceipt = this.requireBoolean(MessageTable.HAS_READ_RECEIPT),
      read = this.requireBoolean(MessageTable.READ),
      receiptTimestamp = this.requireLong(MessageTable.RECEIPT_TIMESTAMP),
      networkFailureRecipientIds = this.requireString(MessageTable.NETWORK_FAILURES).parseNetworkFailures(),
      identityMismatchRecipientIds = this.requireString(MessageTable.MISMATCHED_IDENTITIES).parseIdentityMismatches(),
      baseType = this.requireLong(COLUMN_BASE_TYPE)
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
    val quoteTargetSentTimestamp: Long,
    val quoteAuthor: Long,
    val quoteBody: String?,
    val quoteMissing: Boolean,
    val quoteBodyRanges: ByteArray?,
    val quoteType: Int,
    val originalMessageId: Long,
    val latestRevisionId: Long,
    val hasDeliveryReceipt: Boolean,
    val hasReadReceipt: Boolean,
    val viewed: Boolean,
    val receiptTimestamp: Long,
    val read: Boolean,
    val networkFailureRecipientIds: Set<Long>,
    val identityMismatchRecipientIds: Set<Long>,
    val baseType: Long
  )
}

/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import org.signal.core.util.Base64
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.backup.v2.BackupState
import org.thoughtcrime.securesms.backup.v2.proto.BodyRange
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.ChatUpdateMessage
import org.thoughtcrime.securesms.backup.v2.proto.IndividualCallChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.Quote
import org.thoughtcrime.securesms.backup.v2.proto.Reaction
import org.thoughtcrime.securesms.backup.v2.proto.SendStatus
import org.thoughtcrime.securesms.backup.v2.proto.SimpleChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.StandardMessage
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.ReactionTable
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet
import org.thoughtcrime.securesms.database.documents.NetworkFailure
import org.thoughtcrime.securesms.database.documents.NetworkFailureSet
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.JsonUtils
import org.whispersystems.signalservice.api.util.UuidUtil

/**
 * An object that will ingest all fo the [ChatItem]s you want to write, buffer them until hitting a specified batch size, and then batch insert them
 * for fast throughput.
 */
class ChatItemImportInserter(
  private val db: SQLiteDatabase,
  private val backupState: BackupState,
  private val batchSize: Int
) {
  companion object {
    private val TAG = Log.tag(ChatItemImportInserter::class.java)

    private val MESSAGE_COLUMNS = arrayOf(
      MessageTable.DATE_SENT,
      MessageTable.DATE_RECEIVED,
      MessageTable.DATE_SERVER,
      MessageTable.TYPE,
      MessageTable.THREAD_ID,
      MessageTable.READ,
      MessageTable.BODY,
      MessageTable.FROM_RECIPIENT_ID,
      MessageTable.TO_RECIPIENT_ID,
      MessageTable.HAS_DELIVERY_RECEIPT,
      MessageTable.HAS_READ_RECEIPT,
      MessageTable.VIEWED_COLUMN,
      MessageTable.MISMATCHED_IDENTITIES,
      MessageTable.EXPIRES_IN,
      MessageTable.EXPIRE_STARTED,
      MessageTable.UNIDENTIFIED,
      MessageTable.REMOTE_DELETED,
      MessageTable.REMOTE_DELETED,
      MessageTable.NETWORK_FAILURES,
      MessageTable.QUOTE_ID,
      MessageTable.QUOTE_AUTHOR,
      MessageTable.QUOTE_BODY,
      MessageTable.QUOTE_MISSING,
      MessageTable.QUOTE_BODY_RANGES,
      MessageTable.QUOTE_TYPE,
      MessageTable.SHARED_CONTACTS,
      MessageTable.LINK_PREVIEWS,
      MessageTable.MESSAGE_RANGES,
      MessageTable.VIEW_ONCE
    )

    private val REACTION_COLUMNS = arrayOf(
      ReactionTable.MESSAGE_ID,
      ReactionTable.AUTHOR_ID,
      ReactionTable.EMOJI,
      ReactionTable.DATE_SENT,
      ReactionTable.DATE_RECEIVED
    )

    private val GROUP_RECEIPT_COLUMNS = arrayOf(
      GroupReceiptTable.MMS_ID,
      GroupReceiptTable.RECIPIENT_ID,
      GroupReceiptTable.STATUS,
      GroupReceiptTable.TIMESTAMP,
      GroupReceiptTable.UNIDENTIFIED
    )
  }

  private val selfId = Recipient.self().id
  private val buffer: Buffer = Buffer()
  private var messageId: Long = SqlUtil.getNextAutoIncrementId(db, MessageTable.TABLE_NAME)

  /**
   * Indicate that you want to insert the [ChatItem] into the database.
   * If this item causes the buffer to hit the batch size, then a batch of items will actually be inserted.
   */
  fun insert(chatItem: ChatItem) {
    val fromLocalRecipientId: RecipientId? = backupState.backupToLocalRecipientId[chatItem.authorId]
    if (fromLocalRecipientId == null) {
      Log.w(TAG, "[insert] Could not find a local recipient for backup recipient ID ${chatItem.authorId}! Skipping.")
      return
    }

    val chatLocalRecipientId: RecipientId? = backupState.chatIdToLocalRecipientId[chatItem.chatId]
    if (chatLocalRecipientId == null) {
      Log.w(TAG, "[insert] Could not find a local recipient for chatId ${chatItem.chatId}! Skipping.")
      return
    }

    val localThreadId: Long? = backupState.chatIdToLocalThreadId[chatItem.chatId]
    if (localThreadId == null) {
      Log.w(TAG, "[insert] Could not find a local threadId for backup chatId ${chatItem.chatId}! Skipping.")
      return
    }

    val chatBackupRecipientId: Long? = backupState.chatIdToBackupRecipientId[chatItem.chatId]
    if (chatBackupRecipientId == null) {
      Log.w(TAG, "[insert] Could not find a backup recipientId for backup chatId ${chatItem.chatId}! Skipping.")
      return
    }

    buffer.messages += chatItem.toMessageInsert(fromLocalRecipientId, chatLocalRecipientId, localThreadId)
    buffer.reactions += chatItem.toReactionContentValues(messageId)
    buffer.groupReceipts += chatItem.toGroupReceiptContentValues(messageId, chatBackupRecipientId)

    messageId++

    if (buffer.size >= batchSize) {
      flush()
    }
  }

  /** Returns true if something was written to the db, otherwise false. */
  fun flush(): Boolean {
    if (buffer.size == 0) {
      return false
    }

    buildBulkInsert(MessageTable.TABLE_NAME, MESSAGE_COLUMNS, buffer.messages).forEach {
      db.rawQuery("${it.query.where} RETURNING ${MessageTable.ID}", it.query.whereArgs).use { cursor ->
        var index = 0
        while (cursor.moveToNext()) {
          val rowId = cursor.requireLong(MessageTable.ID)
          val followup = it.inserts[index].followUp
          if (followup != null) {
            followup(rowId)
          }
          index++
        }
      }
    }

    SqlUtil.buildBulkInsert(ReactionTable.TABLE_NAME, REACTION_COLUMNS, buffer.reactions).forEach {
      db.execSQL(it.where, it.whereArgs)
    }

    SqlUtil.buildBulkInsert(GroupReceiptTable.TABLE_NAME, GROUP_RECEIPT_COLUMNS, buffer.groupReceipts).forEach {
      db.execSQL(it.where, it.whereArgs)
    }

    messageId = SqlUtil.getNextAutoIncrementId(db, MessageTable.TABLE_NAME)

    return true
  }

  private fun buildBulkInsert(tableName: String, columns: Array<String>, messageInserts: List<MessageInsert>, maxQueryArgs: Int = 999): List<BatchInsert> {
    val batchSize = maxQueryArgs / columns.size

    return messageInserts
      .chunked(batchSize)
      .map { batch: List<MessageInsert> -> BatchInsert(batch, SqlUtil.buildSingleBulkInsert(tableName, columns, batch.map { it.contentValues })) }
      .toList()
  }

  private fun ChatItem.toMessageInsert(fromRecipientId: RecipientId, chatRecipientId: RecipientId, threadId: Long): MessageInsert {
    val contentValues = this.toMessageContentValues(fromRecipientId, chatRecipientId, threadId)

    var followUp: ((Long) -> Unit)? = null
    if (this.updateMessage != null) {
      if (this.updateMessage.callingMessage != null && this.updateMessage.callingMessage.callId != null) {
        followUp = { messageRowId ->
          val callContentValues = ContentValues()
          callContentValues.put(CallTable.MESSAGE_ID, messageRowId)
          db.update(CallTable.TABLE_NAME, SQLiteDatabase.CONFLICT_IGNORE, callContentValues, "${CallTable.CALL_ID} = ?", SqlUtil.buildArgs(this.updateMessage.callingMessage.callId))
        }
      }
    }
    return MessageInsert(contentValues, followUp)
  }

  private class BatchInsert(val inserts: List<MessageInsert>, val query: SqlUtil.Query)

  private fun ChatItem.toMessageContentValues(fromRecipientId: RecipientId, chatRecipientId: RecipientId, threadId: Long): ContentValues {
    val contentValues = ContentValues()

    contentValues.put(MessageTable.TYPE, this.getMessageType())
    contentValues.put(MessageTable.DATE_SENT, this.dateSent)
    contentValues.put(MessageTable.DATE_SERVER, this.incoming?.dateServerSent ?: -1)
    contentValues.put(MessageTable.FROM_RECIPIENT_ID, fromRecipientId.serialize())
    contentValues.put(MessageTable.TO_RECIPIENT_ID, (if (this.outgoing != null) chatRecipientId else selfId).serialize())
    contentValues.put(MessageTable.THREAD_ID, threadId)
    contentValues.put(MessageTable.DATE_RECEIVED, this.incoming?.dateReceived ?: this.dateSent)
    contentValues.put(MessageTable.RECEIPT_TIMESTAMP, this.outgoing?.sendStatus?.maxOf { it.lastStatusUpdateTimestamp } ?: 0)
    contentValues.putNull(MessageTable.LATEST_REVISION_ID)
    contentValues.putNull(MessageTable.ORIGINAL_MESSAGE_ID)
    contentValues.put(MessageTable.REVISION_NUMBER, 0)
    contentValues.put(MessageTable.EXPIRES_IN, this.expiresInMs ?: 0)
    contentValues.put(MessageTable.EXPIRE_STARTED, this.expireStartDate ?: 0)

    if (this.outgoing != null) {
      val viewed = this.outgoing.sendStatus.any { it.deliveryStatus == SendStatus.Status.VIEWED }
      val hasReadReceipt = viewed || this.outgoing.sendStatus.any { it.deliveryStatus == SendStatus.Status.READ }
      val hasDeliveryReceipt = viewed || hasReadReceipt || this.outgoing.sendStatus.any { it.deliveryStatus == SendStatus.Status.DELIVERED }

      contentValues.put(MessageTable.VIEWED_COLUMN, viewed.toInt())
      contentValues.put(MessageTable.HAS_READ_RECEIPT, hasReadReceipt.toInt())
      contentValues.put(MessageTable.HAS_DELIVERY_RECEIPT, hasDeliveryReceipt.toInt())
      contentValues.put(MessageTable.UNIDENTIFIED, this.outgoing.sendStatus.count { it.sealedSender })
      contentValues.put(MessageTable.READ, 1)

      contentValues.addNetworkFailures(this, backupState)
      contentValues.addIdentityKeyMismatches(this, backupState)
    } else {
      contentValues.put(MessageTable.VIEWED_COLUMN, 0)
      contentValues.put(MessageTable.HAS_READ_RECEIPT, 0)
      contentValues.put(MessageTable.HAS_DELIVERY_RECEIPT, 0)
      contentValues.put(MessageTable.UNIDENTIFIED, this.sealedSender?.toInt())
      contentValues.put(MessageTable.READ, this.incoming?.read?.toInt() ?: 0)
    }

    contentValues.put(MessageTable.QUOTE_ID, 0)
    contentValues.put(MessageTable.QUOTE_AUTHOR, 0)
    contentValues.put(MessageTable.QUOTE_MISSING, 0)
    contentValues.put(MessageTable.QUOTE_TYPE, 0)
    contentValues.put(MessageTable.VIEW_ONCE, 0)
    contentValues.put(MessageTable.REMOTE_DELETED, 0)

    when {
      this.standardMessage != null -> contentValues.addStandardMessage(this.standardMessage)
      this.remoteDeletedMessage != null -> contentValues.put(MessageTable.REMOTE_DELETED, 1)
      this.updateMessage != null -> contentValues.addUpdateMessage(this.updateMessage)
    }

    return contentValues
  }

  private fun ChatItem.toReactionContentValues(messageId: Long): List<ContentValues> {
    val reactions: List<Reaction> = when {
      this.standardMessage != null -> this.standardMessage.reactions
      this.contactMessage != null -> this.contactMessage.reactions
      this.voiceMessage != null -> this.voiceMessage.reactions
      this.stickerMessage != null -> this.stickerMessage.reactions
      else -> emptyList()
    }

    return reactions
      .mapNotNull {
        val authorId: Long? = backupState.backupToLocalRecipientId[it.authorId]?.toLong()

        if (authorId != null) {
          contentValuesOf(
            ReactionTable.MESSAGE_ID to messageId,
            ReactionTable.AUTHOR_ID to authorId,
            ReactionTable.DATE_SENT to it.sentTimestamp,
            ReactionTable.DATE_RECEIVED to it.receivedTimestamp,
            ReactionTable.EMOJI to it.emoji
          )
        } else {
          Log.w(TAG, "[Reaction] Could not find a local recipient for backup recipient ID ${it.authorId}! Skipping.")
          null
        }
      }
  }

  private fun ChatItem.toGroupReceiptContentValues(messageId: Long, chatBackupRecipientId: Long): List<ContentValues> {
    if (this.outgoing == null) {
      return emptyList()
    }

    // TODO This seems like an indirect/bad way to detect if this is a 1:1 or group convo
    if (this.outgoing.sendStatus.size == 1 && this.outgoing.sendStatus[0].recipientId == chatBackupRecipientId) {
      return emptyList()
    }

    return this.outgoing.sendStatus.mapNotNull { sendStatus ->
      val recipientId = backupState.backupToLocalRecipientId[sendStatus.recipientId]

      if (recipientId != null) {
        contentValuesOf(
          GroupReceiptTable.MMS_ID to messageId,
          GroupReceiptTable.RECIPIENT_ID to recipientId.serialize(),
          GroupReceiptTable.STATUS to sendStatus.deliveryStatus.toLocalSendStatus(),
          GroupReceiptTable.TIMESTAMP to sendStatus.lastStatusUpdateTimestamp,
          GroupReceiptTable.UNIDENTIFIED to sendStatus.sealedSender
        )
      } else {
        Log.w(TAG, "[GroupReceipts] Could not find a local recipient for backup recipient ID ${sendStatus.recipientId}! Skipping.")
        null
      }
    }
  }

  private fun ChatItem.getMessageType(): Long {
    var type: Long = if (this.outgoing != null) {
      if (this.outgoing.sendStatus.count { it.identityKeyMismatch } > 0) {
        MessageTypes.BASE_SENT_FAILED_TYPE
      } else if (this.outgoing.sendStatus.count { it.networkFailure } > 0) {
        MessageTypes.BASE_SENDING_TYPE
      } else {
        MessageTypes.BASE_SENT_TYPE
      }
    } else {
      MessageTypes.BASE_INBOX_TYPE
    }

    if (!this.sms) {
      type = type or MessageTypes.SECURE_MESSAGE_BIT or MessageTypes.PUSH_MESSAGE_BIT
    }

    return type
  }

  private fun ContentValues.addStandardMessage(standardMessage: StandardMessage) {
    if (standardMessage.text != null) {
      this.put(MessageTable.BODY, standardMessage.text.body)

      if (standardMessage.text.bodyRanges.isNotEmpty()) {
        this.put(MessageTable.MESSAGE_RANGES, standardMessage.text.bodyRanges.toLocalBodyRanges()?.encode() as ByteArray?)
      }
    }

    if (standardMessage.quote != null) {
      this.addQuote(standardMessage.quote)
    }
  }

  private fun ContentValues.addUpdateMessage(updateMessage: ChatUpdateMessage) {
    var typeFlags: Long = 0
    when {
      updateMessage.simpleUpdate != null -> {
        typeFlags = when (updateMessage.simpleUpdate.type) {
          SimpleChatUpdate.Type.UNKNOWN -> 0
          SimpleChatUpdate.Type.JOINED_SIGNAL -> MessageTypes.JOINED_TYPE
          SimpleChatUpdate.Type.IDENTITY_UPDATE -> MessageTypes.KEY_EXCHANGE_IDENTITY_UPDATE_BIT
          SimpleChatUpdate.Type.IDENTITY_VERIFIED -> MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT
          SimpleChatUpdate.Type.IDENTITY_DEFAULT -> MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT
          SimpleChatUpdate.Type.CHANGE_NUMBER -> MessageTypes.CHANGE_NUMBER_TYPE
          SimpleChatUpdate.Type.BOOST_REQUEST -> MessageTypes.BOOST_REQUEST_TYPE
          SimpleChatUpdate.Type.END_SESSION -> MessageTypes.END_SESSION_BIT
          SimpleChatUpdate.Type.CHAT_SESSION_REFRESH -> MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT
          SimpleChatUpdate.Type.BAD_DECRYPT -> MessageTypes.BAD_DECRYPT_TYPE
          SimpleChatUpdate.Type.PAYMENTS_ACTIVATED -> MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED
          SimpleChatUpdate.Type.PAYMENT_ACTIVATION_REQUEST -> MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST
        }
      }
      updateMessage.expirationTimerChange != null -> {
        typeFlags = MessageTypes.EXPIRATION_TIMER_UPDATE_BIT
        put(MessageTable.EXPIRES_IN, updateMessage.expirationTimerChange.expiresInMs.toLong())
      }
      updateMessage.profileChange != null -> {
        typeFlags = MessageTypes.PROFILE_CHANGE_TYPE
        val profileChangeDetails = ProfileChangeDetails(profileNameChange = ProfileChangeDetails.StringChange(previous = updateMessage.profileChange.previousName, newValue = updateMessage.profileChange.newName))
          .encode()
        put(MessageTable.BODY, Base64.encodeWithPadding(profileChangeDetails))
      }
      updateMessage.sessionSwitchover != null -> {
        typeFlags = MessageTypes.SESSION_SWITCHOVER_TYPE
        val sessionSwitchoverDetails = SessionSwitchoverEvent(e164 = updateMessage.sessionSwitchover.e164.toString()).encode()
        put(MessageTable.BODY, Base64.encodeWithPadding(sessionSwitchoverDetails))
      }
      updateMessage.threadMerge != null -> {
        typeFlags = MessageTypes.THREAD_MERGE_TYPE
        val threadMergeDetails = ThreadMergeEvent(previousE164 = updateMessage.threadMerge.previousE164.toString()).encode()
        put(MessageTable.BODY, Base64.encodeWithPadding(threadMergeDetails))
      }
      updateMessage.callingMessage != null -> {
        when {
          updateMessage.callingMessage.callId != null -> {
            typeFlags = backupState.callIdToType[updateMessage.callingMessage.callId]!!
          }
          updateMessage.callingMessage.callMessage != null -> {
            typeFlags = when (updateMessage.callingMessage.callMessage.type) {
              IndividualCallChatUpdate.Type.INCOMING_AUDIO_CALL -> MessageTypes.INCOMING_AUDIO_CALL_TYPE
              IndividualCallChatUpdate.Type.INCOMING_VIDEO_CALL -> MessageTypes.INCOMING_VIDEO_CALL_TYPE
              IndividualCallChatUpdate.Type.OUTGOING_AUDIO_CALL -> MessageTypes.OUTGOING_AUDIO_CALL_TYPE
              IndividualCallChatUpdate.Type.OUTGOING_VIDEO_CALL -> MessageTypes.OUTGOING_VIDEO_CALL_TYPE
              IndividualCallChatUpdate.Type.MISSED_AUDIO_CALL -> MessageTypes.MISSED_AUDIO_CALL_TYPE
              IndividualCallChatUpdate.Type.MISSED_VIDEO_CALL -> MessageTypes.MISSED_VIDEO_CALL_TYPE
              IndividualCallChatUpdate.Type.UNKNOWN -> typeFlags
            }
          }
        }
        // Calls don't use the incoming/outgoing flags, so we overwrite the flags here
        this.put(MessageTable.TYPE, typeFlags)
      }
    }
    this.put(MessageTable.TYPE, getAsLong(MessageTable.TYPE) or typeFlags)
  }

  private fun ContentValues.addQuote(quote: Quote) {
    this.put(MessageTable.QUOTE_ID, quote.targetSentTimestamp ?: MessageTable.QUOTE_TARGET_MISSING_ID)
    this.put(MessageTable.QUOTE_AUTHOR, backupState.backupToLocalRecipientId[quote.authorId]!!.serialize())
    this.put(MessageTable.QUOTE_BODY, quote.text)
    this.put(MessageTable.QUOTE_TYPE, quote.type.toLocalQuoteType())
    this.put(MessageTable.QUOTE_BODY_RANGES, quote.bodyRanges.toLocalBodyRanges()?.encode())
    // TODO quote attachments
    this.put(MessageTable.QUOTE_MISSING, (quote.targetSentTimestamp == null).toInt())
  }

  private fun Quote.Type.toLocalQuoteType(): Int {
    return when (this) {
      Quote.Type.UNKNOWN -> QuoteModel.Type.NORMAL.code
      Quote.Type.NORMAL -> QuoteModel.Type.NORMAL.code
      Quote.Type.GIFTBADGE -> QuoteModel.Type.GIFT_BADGE.code
    }
  }

  private fun ContentValues.addNetworkFailures(chatItem: ChatItem, backupState: BackupState) {
    if (chatItem.outgoing == null) {
      return
    }

    val networkFailures = chatItem.outgoing.sendStatus
      .filter { status -> status.networkFailure }
      .mapNotNull { status -> backupState.backupToLocalRecipientId[status.recipientId] }
      .map { recipientId -> NetworkFailure(recipientId) }
      .toSet()

    if (networkFailures.isNotEmpty()) {
      this.put(MessageTable.NETWORK_FAILURES, JsonUtils.toJson(NetworkFailureSet(networkFailures)))
    }
  }

  private fun ContentValues.addIdentityKeyMismatches(chatItem: ChatItem, backupState: BackupState) {
    if (chatItem.outgoing == null) {
      return
    }

    val mismatches = chatItem.outgoing.sendStatus
      .filter { status -> status.identityKeyMismatch }
      .mapNotNull { status -> backupState.backupToLocalRecipientId[status.recipientId] }
      .map { recipientId -> IdentityKeyMismatch(recipientId, null) } // TODO We probably want the actual identity key in this status situation?
      .toSet()

    if (mismatches.isNotEmpty()) {
      this.put(MessageTable.MISMATCHED_IDENTITIES, JsonUtils.toJson(IdentityKeyMismatchSet(mismatches)))
    }
  }

  private fun List<BodyRange>.toLocalBodyRanges(): BodyRangeList? {
    if (this.isEmpty()) {
      return null
    }

    return BodyRangeList(
      ranges = this.map { bodyRange ->
        BodyRangeList.BodyRange(
          mentionUuid = bodyRange.mentionAci?.let { UuidUtil.fromByteString(it) }?.toString(),
          style = bodyRange.style?.let {
            when (bodyRange.style) {
              BodyRange.Style.BOLD -> BodyRangeList.BodyRange.Style.BOLD
              BodyRange.Style.ITALIC -> BodyRangeList.BodyRange.Style.ITALIC
              BodyRange.Style.MONOSPACE -> BodyRangeList.BodyRange.Style.MONOSPACE
              BodyRange.Style.SPOILER -> BodyRangeList.BodyRange.Style.SPOILER
              BodyRange.Style.STRIKETHROUGH -> BodyRangeList.BodyRange.Style.STRIKETHROUGH
              else -> null
            }
          },
          start = bodyRange.start ?: 0,
          length = bodyRange.length ?: 0
        )
      }
    )
  }

  private fun SendStatus.Status.toLocalSendStatus(): Int {
    return when (this) {
      SendStatus.Status.UNKNOWN -> GroupReceiptTable.STATUS_UNKNOWN
      SendStatus.Status.FAILED -> GroupReceiptTable.STATUS_UNKNOWN
      SendStatus.Status.PENDING -> GroupReceiptTable.STATUS_UNDELIVERED
      SendStatus.Status.SENT -> GroupReceiptTable.STATUS_UNDELIVERED
      SendStatus.Status.DELIVERED -> GroupReceiptTable.STATUS_DELIVERED
      SendStatus.Status.READ -> GroupReceiptTable.STATUS_READ
      SendStatus.Status.VIEWED -> GroupReceiptTable.STATUS_VIEWED
      SendStatus.Status.SKIPPED -> GroupReceiptTable.STATUS_SKIPPED
    }
  }

  private class MessageInsert(val contentValues: ContentValues, val followUp: ((Long) -> Unit)?)

  private class Buffer(
    val messages: MutableList<MessageInsert> = mutableListOf(),
    val reactions: MutableList<ContentValues> = mutableListOf(),
    val groupReceipts: MutableList<ContentValues> = mutableListOf()
  ) {
    val size: Int
      get() = listOf(messages.size, reactions.size, groupReceipts.size).max()
  }
}

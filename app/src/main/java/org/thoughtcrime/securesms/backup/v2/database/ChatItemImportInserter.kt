/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.backup.v2.BackupState
import org.thoughtcrime.securesms.backup.v2.proto.BodyRange
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.Quote
import org.thoughtcrime.securesms.backup.v2.proto.Reaction
import org.thoughtcrime.securesms.backup.v2.proto.SendStatus
import org.thoughtcrime.securesms.backup.v2.proto.StandardMessage
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
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.JsonUtils

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
      MessageTable.DELIVERY_RECEIPT_COUNT,
      MessageTable.READ_RECEIPT_COUNT,
      MessageTable.VIEWED_RECEIPT_COUNT,
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

    buffer.messages += chatItem.toMessageContentValues(fromLocalRecipientId, chatLocalRecipientId, localThreadId)
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

    SqlUtil.buildBulkInsert(MessageTable.TABLE_NAME, MESSAGE_COLUMNS, buffer.messages).forEach {
      db.execSQL(it.where, it.whereArgs)
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

  private fun ChatItem.toMessageContentValues(fromRecipientId: RecipientId, chatRecipientId: RecipientId, threadId: Long): ContentValues {
    val contentValues = ContentValues()

    contentValues.put(MessageTable.TYPE, this.getMessageType())
    contentValues.put(MessageTable.DATE_SENT, this.dateSent)
    contentValues.put(MessageTable.DATE_SERVER, this.incoming?.dateServerSent ?: -1)
    contentValues.put(MessageTable.FROM_RECIPIENT_ID, fromRecipientId.serialize())
    contentValues.put(MessageTable.TO_RECIPIENT_ID, (if (this.outgoing != null) chatRecipientId else selfId).serialize())
    contentValues.put(MessageTable.THREAD_ID, threadId)
    contentValues.put(MessageTable.DATE_RECEIVED, this.dateReceived)
    contentValues.put(MessageTable.RECEIPT_TIMESTAMP, this.outgoing?.sendStatus?.maxOf { it.timestamp } ?: 0)
    contentValues.putNull(MessageTable.LATEST_REVISION_ID)
    contentValues.putNull(MessageTable.ORIGINAL_MESSAGE_ID)
    contentValues.put(MessageTable.REVISION_NUMBER, 0)
    contentValues.put(MessageTable.EXPIRES_IN, this.expiresIn ?: 0)
    contentValues.put(MessageTable.EXPIRE_STARTED, this.expireStart ?: 0)

    if (this.outgoing != null) {
      val viewReceiptCount = this.outgoing.sendStatus.count { it.deliveryStatus == SendStatus.Status.VIEWED }
      val readReceiptCount = Integer.max(viewReceiptCount, this.outgoing.sendStatus.count { it.deliveryStatus == SendStatus.Status.READ })
      val deliveryReceiptCount = Integer.max(readReceiptCount, this.outgoing.sendStatus.count { it.deliveryStatus == SendStatus.Status.DELIVERED })

      contentValues.put(MessageTable.VIEWED_RECEIPT_COUNT, viewReceiptCount)
      contentValues.put(MessageTable.READ_RECEIPT_COUNT, readReceiptCount)
      contentValues.put(MessageTable.DELIVERY_RECEIPT_COUNT, deliveryReceiptCount)
      contentValues.put(MessageTable.UNIDENTIFIED, this.outgoing.sendStatus.count { it.sealedSender })
      contentValues.put(MessageTable.READ, 1)

      contentValues.addNetworkFailures(this, backupState)
      contentValues.addIdentityKeyMismatches(this, backupState)
    } else {
      contentValues.put(MessageTable.VIEWED_RECEIPT_COUNT, 0)
      contentValues.put(MessageTable.READ_RECEIPT_COUNT, 0)
      contentValues.put(MessageTable.DELIVERY_RECEIPT_COUNT, 0)
      contentValues.put(MessageTable.UNIDENTIFIED, this.incoming?.sealedSender?.toInt() ?: 0)
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
          GroupReceiptTable.TIMESTAMP to sendStatus.timestamp,
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

  private fun ContentValues.addQuote(quote: Quote) {
    this.put(MessageTable.QUOTE_ID, quote.targetSentTimestamp)
    this.put(MessageTable.QUOTE_AUTHOR, backupState.backupToLocalRecipientId[quote.authorId]!!.serialize())
    this.put(MessageTable.QUOTE_BODY, quote.text)
    this.put(MessageTable.QUOTE_TYPE, quote.type.toLocalQuoteType())
    this.put(MessageTable.QUOTE_BODY_RANGES, quote.bodyRanges.toLocalBodyRanges()?.encode())
    // TODO quote attachments
    this.put(MessageTable.QUOTE_MISSING, quote.originalMessageMissing.toInt())
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
          mentionUuid = bodyRange.mentionAci,
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
      SendStatus.Status.FAILED -> GroupReceiptTable.STATUS_UNKNOWN
      SendStatus.Status.PENDING -> GroupReceiptTable.STATUS_UNDELIVERED
      SendStatus.Status.SENT -> GroupReceiptTable.STATUS_UNDELIVERED
      SendStatus.Status.DELIVERED -> GroupReceiptTable.STATUS_DELIVERED
      SendStatus.Status.READ -> GroupReceiptTable.STATUS_READ
      SendStatus.Status.VIEWED -> GroupReceiptTable.STATUS_VIEWED
      SendStatus.Status.SKIPPED -> GroupReceiptTable.STATUS_SKIPPED
    }
  }

  private class Buffer(
    val messages: MutableList<ContentValues> = mutableListOf(),
    val reactions: MutableList<ContentValues> = mutableListOf(),
    val groupReceipts: MutableList<ContentValues> = mutableListOf()
  ) {
    val size: Int
      get() = listOf(messages.size, reactions.size, groupReceipts.size).max()
  }
}

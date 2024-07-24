/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import okio.ByteString
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.core.util.requireLong
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.PointerAttachment
import org.thoughtcrime.securesms.attachments.TombstoneAttachment
import org.thoughtcrime.securesms.backup.v2.BackupState
import org.thoughtcrime.securesms.backup.v2.proto.BodyRange
import org.thoughtcrime.securesms.backup.v2.proto.ChatItem
import org.thoughtcrime.securesms.backup.v2.proto.ChatUpdateMessage
import org.thoughtcrime.securesms.backup.v2.proto.ContactAttachment
import org.thoughtcrime.securesms.backup.v2.proto.FilePointer
import org.thoughtcrime.securesms.backup.v2.proto.GroupCall
import org.thoughtcrime.securesms.backup.v2.proto.IndividualCall
import org.thoughtcrime.securesms.backup.v2.proto.LinkPreview
import org.thoughtcrime.securesms.backup.v2.proto.MessageAttachment
import org.thoughtcrime.securesms.backup.v2.proto.PaymentNotification
import org.thoughtcrime.securesms.backup.v2.proto.Quote
import org.thoughtcrime.securesms.backup.v2.proto.Reaction
import org.thoughtcrime.securesms.backup.v2.proto.SendStatus
import org.thoughtcrime.securesms.backup.v2.proto.SimpleChatUpdate
import org.thoughtcrime.securesms.backup.v2.proto.StandardMessage
import org.thoughtcrime.securesms.backup.v2.proto.Sticker
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.database.GroupReceiptTable
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.ReactionTable
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet
import org.thoughtcrime.securesms.database.documents.NetworkFailure
import org.thoughtcrime.securesms.database.documents.NetworkFailureSet
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.CryptoValue
import org.thoughtcrime.securesms.database.model.databaseprotos.GV2UpdateDescription
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras
import org.thoughtcrime.securesms.database.model.databaseprotos.PaymentTombstone
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.payments.CryptoValueUtil
import org.thoughtcrime.securesms.payments.Direction
import org.thoughtcrime.securesms.payments.State
import org.thoughtcrime.securesms.payments.proto.PaymentMetaData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.JsonUtils
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId
import org.whispersystems.signalservice.api.payments.Money
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.push.DataMessage
import java.math.BigInteger
import java.util.Optional
import java.util.UUID
import org.thoughtcrime.securesms.backup.v2.proto.GiftBadge as BackupGiftBadge

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
      MessageTable.VIEW_ONCE,
      MessageTable.MESSAGE_EXTRAS,
      MessageTable.ORIGINAL_MESSAGE_ID,
      MessageTable.LATEST_REVISION_ID
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
    val messageInsert = chatItem.toMessageInsert(fromLocalRecipientId, chatLocalRecipientId, localThreadId)
    if (chatItem.revisions.isNotEmpty()) {
      // Flush to avoid having revisions cross batch boundaries, which will cause a foreign key failure
      flush()
      val originalId = messageId
      val latestRevisionId = originalId + chatItem.revisions.size
      val sortedRevisions = chatItem.revisions.sortedBy { it.dateSent }.map { it.toMessageInsert(fromLocalRecipientId, chatLocalRecipientId, localThreadId) }
      for (revision in sortedRevisions) {
        revision.contentValues.put(MessageTable.ORIGINAL_MESSAGE_ID, originalId)
        revision.contentValues.put(MessageTable.LATEST_REVISION_ID, latestRevisionId)
        revision.contentValues.put(MessageTable.REVISION_NUMBER, (messageId - originalId))
        buffer.messages += revision
        messageId++
      }

      messageInsert.contentValues.put(MessageTable.ORIGINAL_MESSAGE_ID, originalId)
    }
    buffer.messages += messageInsert
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

    buffer.reset()

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
      if (this.updateMessage.individualCall != null && this.updateMessage.individualCall.callId != null) {
        followUp = { messageRowId ->
          val values = contentValuesOf(
            CallTable.CALL_ID to updateMessage.individualCall.callId,
            CallTable.MESSAGE_ID to messageRowId,
            CallTable.PEER to chatRecipientId.serialize(),
            CallTable.TYPE to CallTable.Type.serialize(if (updateMessage.individualCall.type == IndividualCall.Type.VIDEO_CALL) CallTable.Type.VIDEO_CALL else CallTable.Type.AUDIO_CALL),
            CallTable.DIRECTION to CallTable.Direction.serialize(if (updateMessage.individualCall.direction == IndividualCall.Direction.OUTGOING) CallTable.Direction.OUTGOING else CallTable.Direction.INCOMING),
            CallTable.EVENT to CallTable.Event.serialize(
              when (updateMessage.individualCall.state) {
                IndividualCall.State.MISSED -> CallTable.Event.MISSED
                IndividualCall.State.MISSED_NOTIFICATION_PROFILE -> CallTable.Event.MISSED_NOTIFICATION_PROFILE
                IndividualCall.State.ACCEPTED -> CallTable.Event.ACCEPTED
                IndividualCall.State.NOT_ACCEPTED -> CallTable.Event.NOT_ACCEPTED
                else -> CallTable.Event.MISSED
              }
            ),
            CallTable.TIMESTAMP to updateMessage.individualCall.startedCallTimestamp,
            CallTable.READ to CallTable.ReadState.serialize(CallTable.ReadState.UNREAD)
          )
          db.insert(CallTable.TABLE_NAME, SQLiteDatabase.CONFLICT_IGNORE, values)
        }
      } else if (this.updateMessage.groupCall != null && this.updateMessage.groupCall.callId != null) {
        followUp = { messageRowId ->
          val values = contentValuesOf(
            CallTable.CALL_ID to updateMessage.groupCall.callId,
            CallTable.MESSAGE_ID to messageRowId,
            CallTable.PEER to chatRecipientId.serialize(),
            CallTable.TYPE to CallTable.Type.serialize(CallTable.Type.GROUP_CALL),
            CallTable.DIRECTION to CallTable.Direction.serialize(if (backupState.backupToLocalRecipientId[updateMessage.groupCall.ringerRecipientId] == selfId) CallTable.Direction.OUTGOING else CallTable.Direction.INCOMING),
            CallTable.EVENT to CallTable.Event.serialize(
              when (updateMessage.groupCall.state) {
                GroupCall.State.ACCEPTED -> CallTable.Event.ACCEPTED
                GroupCall.State.MISSED -> CallTable.Event.MISSED
                GroupCall.State.MISSED_NOTIFICATION_PROFILE -> CallTable.Event.MISSED_NOTIFICATION_PROFILE
                GroupCall.State.GENERIC -> CallTable.Event.GENERIC_GROUP_CALL
                GroupCall.State.JOINED -> CallTable.Event.JOINED
                GroupCall.State.RINGING -> CallTable.Event.RINGING
                GroupCall.State.OUTGOING_RING -> CallTable.Event.OUTGOING_RING
                GroupCall.State.DECLINED -> CallTable.Event.DECLINED
                else -> CallTable.Event.GENERIC_GROUP_CALL
              }
            ),
            CallTable.TIMESTAMP to updateMessage.groupCall.startedCallTimestamp,
            CallTable.READ to CallTable.ReadState.serialize(CallTable.ReadState.UNREAD)
          )
          db.insert(CallTable.TABLE_NAME, SQLiteDatabase.CONFLICT_IGNORE, values)
        }
      }
    }
    if (this.paymentNotification != null) {
      followUp = { messageRowId ->
        val uuid = tryRestorePayment(this, chatRecipientId)
        if (uuid != null) {
          db.update(
            MessageTable.TABLE_NAME,
            contentValuesOf(
              MessageTable.BODY to uuid.toString(),
              MessageTable.TYPE to ((contentValues.getAsLong(MessageTable.TYPE) and MessageTypes.SPECIAL_TYPES_MASK.inv()) or MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION)
            ),
            "${MessageTable.ID}=?",
            SqlUtil.buildArgs(messageRowId)
          )
        }
      }
    }
    if (this.contactMessage != null) {
      val contacts = this.contactMessage.contact.map { backupContact ->
        Contact(
          backupContact.name.toLocal(),
          backupContact.organization,
          backupContact.number.map { phone ->
            Contact.Phone(
              phone.value_ ?: "",
              phone.type.toLocal(),
              phone.label
            )
          },
          backupContact.email.map { email ->
            Contact.Email(
              email.value_ ?: "",
              email.type.toLocal(),
              email.label
            )
          },
          backupContact.address.map { address ->
            Contact.PostalAddress(
              address.type.toLocal(),
              address.label,
              address.street,
              address.pobox,
              address.neighborhood,
              address.city,
              address.region,
              address.postcode,
              address.country
            )
          },
          Contact.Avatar(null, backupContact.avatar.toLocalAttachment(voiceNote = false, borderless = false, gif = false, wasDownloaded = true), true)
        )
      }
      val contactAttachments = contacts.mapNotNull { it.avatarAttachment }
      if (contacts.isNotEmpty()) {
        followUp = { messageRowId ->
          val attachmentMap = if (contactAttachments.isNotEmpty()) {
            SignalDatabase.attachments.insertAttachmentsForMessage(messageRowId, contactAttachments, emptyList())
          } else {
            emptyMap()
          }
          db.update(
            MessageTable.TABLE_NAME,
            contentValuesOf(
              MessageTable.SHARED_CONTACTS to SignalDatabase.messages.getSerializedSharedContacts(attachmentMap, contacts)
            ),
            "${MessageTable.ID} = ?",
            SqlUtil.buildArgs(messageRowId)
          )
        }
      }
    }
    if (this.standardMessage != null) {
      val bodyRanges = this.standardMessage.text?.bodyRanges
      if (!bodyRanges.isNullOrEmpty()) {
        val mentions = bodyRanges.filter { it.mentionAci != null && it.start != null && it.length != null }
          .mapNotNull {
            val aci = ServiceId.ACI.parseOrNull(it.mentionAci!!)

            if (aci != null && !aci.isUnknown) {
              val id = RecipientId.from(aci)
              Mention(id, it.start!!, it.length!!)
            } else {
              null
            }
          }
        if (mentions.isNotEmpty()) {
          followUp = { messageId ->
            SignalDatabase.mentions.insert(threadId, messageId, mentions)
          }
        }
      }
      val linkPreviews = this.standardMessage.linkPreview.map { it.toLocalLinkPreview() }
      val linkPreviewAttachments = linkPreviews.mapNotNull { it.thumbnail.orNull() }
      val attachments = this.standardMessage.attachments.mapNotNull { attachment ->
        attachment.toLocalAttachment()
      }
      val quoteAttachments = this.standardMessage.quote?.attachments?.mapNotNull {
        it.toLocalAttachment()
      } ?: emptyList()
      if (attachments.isNotEmpty() || linkPreviewAttachments.isNotEmpty() || quoteAttachments.isNotEmpty()) {
        followUp = { messageRowId ->
          val attachmentMap = SignalDatabase.attachments.insertAttachmentsForMessage(messageRowId, attachments + linkPreviewAttachments, quoteAttachments)
          if (linkPreviews.isNotEmpty()) {
            db.update(
              MessageTable.TABLE_NAME,
              contentValuesOf(
                MessageTable.LINK_PREVIEWS to SignalDatabase.messages.getSerializedLinkPreviews(attachmentMap, linkPreviews)
              ),
              "${MessageTable.ID} = ?",
              SqlUtil.buildArgs(messageRowId)
            )
          }
        }
      }
    }
    if (this.stickerMessage != null) {
      val sticker = this.stickerMessage.sticker
      val attachment = sticker.toLocalAttachment()
      if (attachment != null) {
        followUp = { messageRowId ->
          SignalDatabase.attachments.insertAttachmentsForMessage(messageRowId, listOf(attachment), emptyList())
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
    contentValues.put(MessageTable.RECEIPT_TIMESTAMP, this.outgoing?.sendStatus?.maxOfOrNull { it.lastStatusUpdateTimestamp } ?: 0)
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
      contentValues.put(MessageTable.UNIDENTIFIED, this.incoming?.sealedSender?.toInt() ?: 0)
      contentValues.put(MessageTable.READ, this.incoming?.read?.toInt() ?: 0)
      contentValues.put(MessageTable.NOTIFIED, 1)
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
      this.paymentNotification != null -> contentValues.addPaymentNotification(this, chatRecipientId)
      this.giftBadge != null -> contentValues.addGiftBadge(this.giftBadge)
    }

    return contentValues
  }

  private fun tryRestorePayment(chatItem: ChatItem, chatRecipientId: RecipientId): UUID? {
    val paymentNotification = chatItem.paymentNotification!!

    val amount = paymentNotification.amountMob?.tryParseMoney() ?: return null
    val fee = paymentNotification.feeMob?.tryParseMoney() ?: return null

    if (paymentNotification.transactionDetails?.failedTransaction != null) {
      return null
    }

    val transaction = paymentNotification.transactionDetails?.transaction

    val mobileCoinIdentification = transaction?.mobileCoinIdentification?.toLocal() ?: return null

    return SignalDatabase.payments.restoreFromBackup(
      chatRecipientId,
      transaction.timestamp ?: 0,
      transaction.blockIndex ?: 0,
      paymentNotification.note ?: "",
      if (chatItem.outgoing != null) Direction.SENT else Direction.RECEIVED,
      transaction.status.toLocalStatus(),
      amount,
      fee,
      transaction.transaction?.toByteArray(),
      transaction.receipt?.toByteArray(),
      mobileCoinIdentification,
      chatItem.incoming?.read ?: true
    )
  }

  private fun ChatItem.toReactionContentValues(messageId: Long): List<ContentValues> {
    val reactions: List<Reaction> = when {
      this.standardMessage != null -> this.standardMessage.reactions
      this.contactMessage != null -> this.contactMessage.reactions
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

    if (this.giftBadge != null) {
      type = type or MessageTypes.SPECIAL_TYPE_GIFT_BADGE
    }

    return type
  }

  private fun ContentValues.addStandardMessage(standardMessage: StandardMessage) {
    if (standardMessage.text != null) {
      this.put(MessageTable.BODY, standardMessage.text.body)

      if (standardMessage.text.bodyRanges.isNotEmpty()) {
        this.put(MessageTable.MESSAGE_RANGES, standardMessage.text.bodyRanges.toLocalBodyRanges()?.encode())
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
        val typeWithoutBase = (getAsLong(MessageTable.TYPE) and MessageTypes.BASE_TYPE_MASK.inv())
        typeFlags = when (updateMessage.simpleUpdate.type) {
          SimpleChatUpdate.Type.UNKNOWN -> typeWithoutBase
          SimpleChatUpdate.Type.JOINED_SIGNAL -> MessageTypes.JOINED_TYPE or typeWithoutBase
          SimpleChatUpdate.Type.IDENTITY_UPDATE -> MessageTypes.KEY_EXCHANGE_IDENTITY_UPDATE_BIT or typeWithoutBase
          SimpleChatUpdate.Type.IDENTITY_VERIFIED -> MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT or typeWithoutBase
          SimpleChatUpdate.Type.IDENTITY_DEFAULT -> MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT or typeWithoutBase
          SimpleChatUpdate.Type.CHANGE_NUMBER -> MessageTypes.CHANGE_NUMBER_TYPE
          SimpleChatUpdate.Type.RELEASE_CHANNEL_DONATION_REQUEST -> MessageTypes.RELEASE_CHANNEL_DONATION_REQUEST_TYPE
          SimpleChatUpdate.Type.END_SESSION -> MessageTypes.END_SESSION_BIT or typeWithoutBase
          SimpleChatUpdate.Type.CHAT_SESSION_REFRESH -> MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT or typeWithoutBase
          SimpleChatUpdate.Type.BAD_DECRYPT -> MessageTypes.BAD_DECRYPT_TYPE or typeWithoutBase
          SimpleChatUpdate.Type.PAYMENTS_ACTIVATED -> MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED or typeWithoutBase
          SimpleChatUpdate.Type.PAYMENT_ACTIVATION_REQUEST -> MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST or typeWithoutBase
          SimpleChatUpdate.Type.UNSUPPORTED_PROTOCOL_MESSAGE -> MessageTypes.UNSUPPORTED_MESSAGE_TYPE or typeWithoutBase
          SimpleChatUpdate.Type.REPORTED_SPAM -> MessageTypes.SPECIAL_TYPE_REPORTED_SPAM or typeWithoutBase
        }
      }
      updateMessage.expirationTimerChange != null -> {
        typeFlags = getAsLong(MessageTable.TYPE) or MessageTypes.EXPIRATION_TIMER_UPDATE_BIT
        put(MessageTable.EXPIRES_IN, updateMessage.expirationTimerChange.expiresInMs.toLong())
      }
      updateMessage.profileChange != null -> {
        typeFlags = MessageTypes.PROFILE_CHANGE_TYPE
        val profileChangeDetails = ProfileChangeDetails(profileNameChange = ProfileChangeDetails.StringChange(previous = updateMessage.profileChange.previousName, newValue = updateMessage.profileChange.newName))
        val messageExtras = MessageExtras(profileChangeDetails = profileChangeDetails).encode()
        put(MessageTable.MESSAGE_EXTRAS, messageExtras)
      }
      updateMessage.learnedProfileChange != null -> {
        typeFlags = MessageTypes.PROFILE_CHANGE_TYPE
        val profileChangeDetails = ProfileChangeDetails(learnedProfileName = ProfileChangeDetails.LearnedProfileName(e164 = updateMessage.learnedProfileChange.e164?.toString(), username = updateMessage.learnedProfileChange.username))
        val messageExtras = MessageExtras(profileChangeDetails = profileChangeDetails).encode()
        put(MessageTable.MESSAGE_EXTRAS, messageExtras)
      }
      updateMessage.sessionSwitchover != null -> {
        typeFlags = MessageTypes.SESSION_SWITCHOVER_TYPE or (getAsLong(MessageTable.TYPE) and MessageTypes.BASE_TYPE_MASK.inv())
        val sessionSwitchoverDetails = SessionSwitchoverEvent(e164 = updateMessage.sessionSwitchover.e164.toString()).encode()
        put(MessageTable.BODY, Base64.encodeWithPadding(sessionSwitchoverDetails))
      }
      updateMessage.threadMerge != null -> {
        typeFlags = MessageTypes.THREAD_MERGE_TYPE or (getAsLong(MessageTable.TYPE) and MessageTypes.BASE_TYPE_MASK.inv())
        val threadMergeDetails = ThreadMergeEvent(previousE164 = updateMessage.threadMerge.previousE164.toString()).encode()
        put(MessageTable.BODY, Base64.encodeWithPadding(threadMergeDetails))
      }
      updateMessage.individualCall != null -> {
        if (updateMessage.individualCall.state == IndividualCall.State.MISSED || updateMessage.individualCall.state == IndividualCall.State.MISSED_NOTIFICATION_PROFILE) {
          typeFlags = if (updateMessage.individualCall.type == IndividualCall.Type.AUDIO_CALL) MessageTypes.MISSED_AUDIO_CALL_TYPE else MessageTypes.MISSED_VIDEO_CALL_TYPE
        } else {
          typeFlags = if (updateMessage.individualCall.direction == IndividualCall.Direction.OUTGOING) {
            if (updateMessage.individualCall.type == IndividualCall.Type.AUDIO_CALL) MessageTypes.OUTGOING_AUDIO_CALL_TYPE else MessageTypes.OUTGOING_VIDEO_CALL_TYPE
          } else {
            if (updateMessage.individualCall.type == IndividualCall.Type.AUDIO_CALL) MessageTypes.INCOMING_AUDIO_CALL_TYPE else MessageTypes.INCOMING_VIDEO_CALL_TYPE
          }
        }
        this.put(MessageTable.TYPE, typeFlags)
      }
      updateMessage.groupCall != null -> {
        val startedCallRecipientId = if (updateMessage.groupCall.startedCallRecipientId != null) {
          backupState.backupToLocalRecipientId[updateMessage.groupCall.startedCallRecipientId]
        } else {
          null
        }
        val startedCall = if (startedCallRecipientId != null) {
          recipients.getRecord(startedCallRecipientId).aci
        } else {
          null
        }
        this.put(MessageTable.BODY, GroupCallUpdateDetailsUtil.createBodyFromBackup(updateMessage.groupCall, startedCall))
        this.put(MessageTable.TYPE, MessageTypes.GROUP_CALL_TYPE)
      }
      updateMessage.groupChange != null -> {
        put(MessageTable.BODY, "")
        put(
          MessageTable.MESSAGE_EXTRAS,
          MessageExtras(
            gv2UpdateDescription =
            GV2UpdateDescription(groupChangeUpdate = updateMessage.groupChange)
          ).encode()
        )
        typeFlags = getAsLong(MessageTable.TYPE) or MessageTypes.GROUP_V2_BIT or MessageTypes.GROUP_UPDATE_BIT
      }
    }
    this.put(MessageTable.TYPE, typeFlags)
  }

  /**
   * Add the payment notification to the chat item.
   *
   * Note we add a tombstone first, then post insertion update it to a proper notification
   */
  private fun ContentValues.addPaymentNotification(chatItem: ChatItem, chatRecipientId: RecipientId) {
    val paymentNotification = chatItem.paymentNotification!!
    if (chatItem.paymentNotification.amountMob.isNullOrEmpty()) {
      addPaymentTombstoneNoAmount()
      return
    }
    val amount = paymentNotification.amountMob?.tryParseMoney() ?: return addPaymentTombstoneNoAmount()
    val fee = paymentNotification.feeMob?.tryParseMoney() ?: return addPaymentTombstoneNoAmount()

    if (chatItem.paymentNotification.transactionDetails?.failedTransaction != null) {
      addFailedPaymentNotification(chatItem, amount, fee, chatRecipientId)
      return
    }
    addPaymentTombstoneNoMetadata(chatItem.paymentNotification)
  }

  private fun PaymentNotification.TransactionDetails.MobileCoinTxoIdentification.toLocal(): PaymentMetaData {
    return PaymentMetaData(
      mobileCoinTxoIdentification = PaymentMetaData.MobileCoinTxoIdentification(
        publicKey = this.publicKey,
        keyImages = this.keyImages
      )
    )
  }

  private fun ContentValues.addFailedPaymentNotification(chatItem: ChatItem, amount: Money, fee: Money, chatRecipientId: RecipientId) {
    val uuid = SignalDatabase.payments.restoreFromBackup(
      chatRecipientId,
      0,
      0,
      chatItem.paymentNotification?.note ?: "",
      if (chatItem.outgoing != null) Direction.SENT else Direction.RECEIVED,
      State.FAILED,
      amount,
      fee,
      null,
      null,
      null,
      chatItem.incoming?.read ?: true
    )
    if (uuid != null) {
      put(MessageTable.BODY, uuid.toString())
      put(MessageTable.TYPE, getAsLong(MessageTable.TYPE) or MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION)
    } else {
      addPaymentTombstoneNoMetadata(chatItem.paymentNotification!!)
    }
  }

  private fun ContentValues.addPaymentTombstoneNoAmount() {
    put(MessageTable.TYPE, getAsLong(MessageTable.TYPE) or MessageTypes.SPECIAL_TYPE_PAYMENTS_TOMBSTONE)
  }

  private fun ContentValues.addPaymentTombstoneNoMetadata(paymentNotification: PaymentNotification) {
    put(MessageTable.TYPE, getAsLong(MessageTable.TYPE) or MessageTypes.SPECIAL_TYPE_PAYMENTS_TOMBSTONE)
    val amount = tryParseCryptoValue(paymentNotification.amountMob)
    val fee = tryParseCryptoValue(paymentNotification.feeMob)
    put(
      MessageTable.MESSAGE_EXTRAS,
      MessageExtras(
        paymentTombstone = PaymentTombstone(
          note = paymentNotification.note,
          amount = amount,
          fee = fee
        )
      ).encode()
    )
  }

  private fun ContentValues.addGiftBadge(giftBadge: BackupGiftBadge) {
    val dbGiftBadge = GiftBadge(
      redemptionToken = giftBadge.receiptCredentialPresentation,
      redemptionState = when (giftBadge.state) {
        BackupGiftBadge.State.UNOPENED -> GiftBadge.RedemptionState.PENDING
        BackupGiftBadge.State.OPENED -> GiftBadge.RedemptionState.STARTED
        BackupGiftBadge.State.REDEEMED -> GiftBadge.RedemptionState.REDEEMED
        BackupGiftBadge.State.FAILED -> GiftBadge.RedemptionState.FAILED
      }
    )

    put(MessageTable.BODY, Base64.encodeWithPadding(GiftBadge.ADAPTER.encode(dbGiftBadge)))
  }

  private fun String?.tryParseMoney(): Money? {
    if (this.isNullOrEmpty()) {
      return null
    }

    val amountCryptoValue = tryParseCryptoValue(this)
    return if (amountCryptoValue != null) {
      CryptoValueUtil.cryptoValueToMoney(amountCryptoValue)
    } else {
      null
    }
  }

  private fun tryParseCryptoValue(bigIntegerString: String?): CryptoValue? {
    if (bigIntegerString == null) {
      return null
    }
    val amount = try {
      BigInteger(bigIntegerString).toString()
    } catch (e: NumberFormatException) {
      return null
    }
    return CryptoValue(mobileCoinValue = CryptoValue.MobileCoinValue(picoMobileCoin = amount))
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

  private fun PaymentNotification.TransactionDetails.Transaction.Status?.toLocalStatus(): State {
    return when (this) {
      PaymentNotification.TransactionDetails.Transaction.Status.INITIAL -> State.INITIAL
      PaymentNotification.TransactionDetails.Transaction.Status.SUBMITTED -> State.SUBMITTED
      PaymentNotification.TransactionDetails.Transaction.Status.SUCCESSFUL -> State.SUCCESSFUL
      else -> State.INITIAL
    }
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
      ranges = this.filter { it.mentionAci == null }.map { bodyRange ->
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

  private fun FilePointer?.toLocalAttachment(voiceNote: Boolean, borderless: Boolean, gif: Boolean, wasDownloaded: Boolean, stickerLocator: StickerLocator? = null, contentType: String? = this?.contentType, fileName: String? = this?.fileName, uuid: ByteString? = null): Attachment? {
    if (this == null) return null

    if (attachmentLocator != null) {
      val signalAttachmentPointer = SignalServiceAttachmentPointer(
        attachmentLocator.cdnNumber,
        SignalServiceAttachmentRemoteId.from(attachmentLocator.cdnKey),
        contentType,
        attachmentLocator.key.toByteArray(),
        Optional.ofNullable(attachmentLocator.size),
        Optional.empty(),
        width ?: 0,
        height ?: 0,
        Optional.ofNullable(attachmentLocator.digest.toByteArray()),
        Optional.ofNullable(incrementalMac?.toByteArray()),
        incrementalMacChunkSize ?: 0,
        Optional.ofNullable(fileName),
        voiceNote,
        borderless,
        gif,
        Optional.ofNullable(caption),
        Optional.ofNullable(blurHash),
        attachmentLocator.uploadTimestamp,
        UuidUtil.fromByteStringOrNull(uuid)
      )
      return PointerAttachment.forPointer(
        pointer = Optional.of(signalAttachmentPointer),
        stickerLocator = stickerLocator,
        transferState = if (wasDownloaded) AttachmentTable.TRANSFER_NEEDS_RESTORE else AttachmentTable.TRANSFER_PROGRESS_PENDING
      ).orNull()
    } else if (invalidAttachmentLocator != null) {
      return TombstoneAttachment(
        contentType = contentType,
        incrementalMac = incrementalMac?.toByteArray(),
        incrementalMacChunkSize = incrementalMacChunkSize,
        width = width,
        height = height,
        caption = caption,
        blurHash = blurHash,
        voiceNote = voiceNote,
        borderless = borderless,
        gif = gif,
        quote = false,
        uuid = UuidUtil.fromByteStringOrNull(uuid)
      )
    } else if (backupLocator != null) {
      return ArchivedAttachment(
        contentType = contentType,
        size = backupLocator.size.toLong(),
        cdn = backupLocator.transitCdnNumber ?: Cdn.CDN_0.cdnNumber,
        key = backupLocator.key.toByteArray(),
        cdnKey = backupLocator.transitCdnKey,
        archiveCdn = backupLocator.cdnNumber,
        archiveMediaName = backupLocator.mediaName,
        archiveMediaId = backupState.backupKey.deriveMediaId(MediaName(backupLocator.mediaName)).encode(),
        archiveThumbnailMediaId = backupState.backupKey.deriveMediaId(MediaName.forThumbnailFromMediaName(backupLocator.mediaName)).encode(),
        digest = backupLocator.digest.toByteArray(),
        incrementalMac = incrementalMac?.toByteArray(),
        incrementalMacChunkSize = incrementalMacChunkSize,
        width = width,
        height = height,
        caption = caption,
        blurHash = blurHash,
        voiceNote = voiceNote,
        borderless = borderless,
        gif = gif,
        quote = false,
        stickerLocator = stickerLocator,
        uuid = UuidUtil.fromByteStringOrNull(uuid)
      )
    }
    return null
  }

  private fun Sticker?.toLocalAttachment(): Attachment? {
    if (this == null) return null

    return data_.toLocalAttachment(
      voiceNote = false,
      gif = false,
      borderless = false,
      wasDownloaded = true,
      stickerLocator = StickerLocator(
        packId = Hex.toStringCondensed(packId.toByteArray()),
        packKey = Hex.toStringCondensed(packKey.toByteArray()),
        stickerId = stickerId,
        emoji = emoji
      )
    )
  }

  private fun LinkPreview.toLocalLinkPreview(): org.thoughtcrime.securesms.linkpreview.LinkPreview {
    return org.thoughtcrime.securesms.linkpreview.LinkPreview(
      this.url,
      this.title ?: "",
      this.description ?: "",
      this.date ?: 0,
      Optional.ofNullable(this.image?.toLocalAttachment(voiceNote = false, borderless = false, gif = false, wasDownloaded = true))
    )
  }

  private fun MessageAttachment.toLocalAttachment(): Attachment? {
    return pointer?.toLocalAttachment(
      voiceNote = flag == MessageAttachment.Flag.VOICE_MESSAGE,
      gif = flag == MessageAttachment.Flag.GIF,
      borderless = flag == MessageAttachment.Flag.BORDERLESS,
      wasDownloaded = wasDownloaded,
      uuid = clientUuid
    )
  }

  private fun ContactAttachment.Name?.toLocal(): Contact.Name {
    return Contact.Name(this?.displayName, this?.givenName, this?.familyName, this?.prefix, this?.suffix, this?.middleName)
  }

  private fun ContactAttachment.Phone.Type?.toLocal(): Contact.Phone.Type {
    return when (this) {
      ContactAttachment.Phone.Type.HOME -> Contact.Phone.Type.HOME
      ContactAttachment.Phone.Type.MOBILE -> Contact.Phone.Type.MOBILE
      ContactAttachment.Phone.Type.WORK -> Contact.Phone.Type.WORK
      ContactAttachment.Phone.Type.CUSTOM,
      ContactAttachment.Phone.Type.UNKNOWN,
      null -> Contact.Phone.Type.CUSTOM
    }
  }

  private fun ContactAttachment.Email.Type?.toLocal(): Contact.Email.Type {
    return when (this) {
      ContactAttachment.Email.Type.HOME -> Contact.Email.Type.HOME
      ContactAttachment.Email.Type.MOBILE -> Contact.Email.Type.MOBILE
      ContactAttachment.Email.Type.WORK -> Contact.Email.Type.WORK
      ContactAttachment.Email.Type.CUSTOM,
      ContactAttachment.Email.Type.UNKNOWN,
      null -> Contact.Email.Type.CUSTOM
    }
  }

  private fun ContactAttachment.PostalAddress.Type?.toLocal(): Contact.PostalAddress.Type {
    return when (this) {
      ContactAttachment.PostalAddress.Type.HOME -> Contact.PostalAddress.Type.HOME
      ContactAttachment.PostalAddress.Type.WORK -> Contact.PostalAddress.Type.WORK
      ContactAttachment.PostalAddress.Type.CUSTOM,
      ContactAttachment.PostalAddress.Type.UNKNOWN,
      null -> Contact.PostalAddress.Type.CUSTOM
    }
  }

  private fun MessageAttachment.toLocalAttachment(contentType: String?, fileName: String?): Attachment? {
    return pointer?.toLocalAttachment(
      voiceNote = flag == MessageAttachment.Flag.VOICE_MESSAGE,
      gif = flag == MessageAttachment.Flag.GIF,
      borderless = flag == MessageAttachment.Flag.BORDERLESS,
      wasDownloaded = wasDownloaded,
      contentType = contentType,
      fileName = fileName,
      uuid = clientUuid
    )
  }

  private fun Quote.QuotedAttachment.toLocalAttachment(): Attachment? {
    return thumbnail?.toLocalAttachment(this.contentType, this.fileName)
      ?: if (this.contentType == null) null else PointerAttachment.forPointer(quotedAttachment = DataMessage.Quote.QuotedAttachment(contentType = this.contentType, fileName = this.fileName, thumbnail = null)).orNull()
  }

  private class MessageInsert(
    val contentValues: ContentValues,
    val followUp: ((Long) -> Unit)?,
    val edits: List<MessageInsert>? = null
  )

  private class Buffer(
    val messages: MutableList<MessageInsert> = mutableListOf(),
    val reactions: MutableList<ContentValues> = mutableListOf(),
    val groupReceipts: MutableList<ContentValues> = mutableListOf()
  ) {
    val size: Int
      get() = listOf(messages.size, reactions.size, groupReceipts.size).max()

    fun reset() {
      messages.clear()
      reactions.clear()
      groupReceipts.clear()
    }
  }
}

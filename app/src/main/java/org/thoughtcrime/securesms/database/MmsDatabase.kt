/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.text.TextUtils
import com.annimon.stream.Stream
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduHeaders
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.session.libsession.messaging.messages.signal.IncomingMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingExpirationUpdateMessage
import org.session.libsession.messaging.messages.signal.OutgoingGroupMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingMediaMessage
import org.session.libsession.messaging.messages.signal.OutgoingSecureMediaMessage
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.Address.Companion.UNKNOWN
import org.session.libsession.utilities.Address.Companion.fromExternal
import org.session.libsession.utilities.Address.Companion.fromSerialized
import org.session.libsession.utilities.Contact
import org.session.libsession.utilities.GroupUtil.doubleEncodeGroupID
import org.session.libsession.utilities.IdentityKeyMismatch
import org.session.libsession.utilities.IdentityKeyMismatchList
import org.session.libsession.utilities.NetworkFailure
import org.session.libsession.utilities.NetworkFailureList
import org.session.libsession.utilities.TextSecurePreferences.Companion.isReadReceiptsEnabled
import org.session.libsession.utilities.Util.toIsoBytes
import org.session.libsession.utilities.Util.toIsoString
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.recipients.RecipientFormattingException
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.ThreadUtils.queue
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.attachments.MmsNotificationAttachment
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.NoSuchMessageException
import org.thoughtcrime.securesms.database.SmsDatabase.InsertListener
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.dependencies.DatabaseComponent.Companion.get
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.SlideDeck
import java.io.Closeable
import java.io.IOException
import java.security.SecureRandom
import java.util.LinkedList

class MmsDatabase(context: Context, databaseHelper: SQLCipherOpenHelper) : MessagingDatabase(context, databaseHelper) {
    private val earlyDeliveryReceiptCache = EarlyReceiptCache()
    private val earlyReadReceiptCache = EarlyReceiptCache()
    override fun getTableName() = TABLE_NAME

    fun getMessageCountForThread(threadId: Long): Int {
        val db = databaseHelper.readableDatabase
        db.query(
            TABLE_NAME,
            arrayOf("COUNT(*)"),
            "$THREAD_ID = ?",
            arrayOf(threadId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }
        return 0
    }

    fun addFailures(messageId: Long, failure: List<NetworkFailure>) {
        try {
            addToDocument(messageId, NETWORK_FAILURE, failure, NetworkFailureList::class.java)
        } catch (e: IOException) {
            Log.w(TAG, e)
        }
    }

    fun removeFailure(messageId: Long, failure: NetworkFailure?) {
        try {
            removeFromDocument(messageId, NETWORK_FAILURE, failure, NetworkFailureList::class.java)
        } catch (e: IOException) {
            Log.w(TAG, e)
        }
    }

    fun isOutgoingMessage(timestamp: Long): Boolean {
        val database = databaseHelper.writableDatabase
        var cursor: Cursor? = null
        var isOutgoing = false
        try {
            cursor = database.query(
                TABLE_NAME,
                arrayOf<String>(ID, THREAD_ID, MESSAGE_BOX, ADDRESS),
                DATE_SENT + " = ?",
                arrayOf(timestamp.toString()),
                null,
                null,
                null,
                null
            )
            while (cursor.moveToNext()) {
                if (MmsSmsColumns.Types.isOutgoingMessageType(
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                MESSAGE_BOX
                            )
                        )
                    )
                ) {
                    isOutgoing = true
                }
            }
        } finally {
            cursor?.close()
        }
        return isOutgoing
    }

    fun incrementReceiptCount(
        messageId: SyncMessageId,
        timestamp: Long,
        deliveryReceipt: Boolean,
        readReceipt: Boolean
    ) {
        val database = databaseHelper.writableDatabase
        var cursor: Cursor? = null
        var found = false
        try {
            cursor = database.query(
                TABLE_NAME,
                arrayOf(ID, THREAD_ID, MESSAGE_BOX, ADDRESS),
                "$DATE_SENT = ?",
                arrayOf(messageId.timetamp.toString()),
                null,
                null,
                null,
                null
            )
            while (cursor.moveToNext()) {
                if (MmsSmsColumns.Types.isOutgoingMessageType(
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                MESSAGE_BOX
                            )
                        )
                    )
                ) {
                    val theirAddress = fromSerialized(
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                ADDRESS
                            )
                        )
                    )
                    val ourAddress = messageId.address
                    val columnName =
                        if (deliveryReceipt) DELIVERY_RECEIPT_COUNT else READ_RECEIPT_COUNT
                    if (ourAddress.equals(theirAddress) || theirAddress.isGroup) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(ID))
                        val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID))
                        val status =
                            if (deliveryReceipt) GroupReceiptDatabase.STATUS_DELIVERED else GroupReceiptDatabase.STATUS_READ
                        found = true
                        database.execSQL(
                            "UPDATE " + TABLE_NAME + " SET " +
                                    columnName + " = " + columnName + " + 1 WHERE " + ID + " = ?",
                            arrayOf(id.toString())
                        )
                        get(context).groupReceiptDatabase()
                            .update(ourAddress, id, status, timestamp)
                        get(context).threadDatabase().update(threadId, false)
                        notifyConversationListeners(threadId)
                    }
                }
            }
            if (!found) {
                if (deliveryReceipt) earlyDeliveryReceiptCache.increment(
                    messageId.timetamp,
                    messageId.address
                )
                if (readReceipt) earlyReadReceiptCache.increment(
                    messageId.timetamp,
                    messageId.address
                )
            }
        } finally {
            cursor?.close()
        }
    }

    fun updateSentTimestamp(messageId: Long, newTimestamp: Long, threadId: Long) {
        val db = databaseHelper.writableDatabase
        db.execSQL(
            "UPDATE $TABLE_NAME SET $DATE_SENT = ? WHERE $ID = ?",
            arrayOf(newTimestamp.toString(), messageId.toString())
        )
        notifyConversationListeners(threadId)
        notifyConversationListListeners()
    }

    fun getThreadIdForMessage(id: Long): Long {
        val sql = "SELECT $THREAD_ID FROM $TABLE_NAME WHERE $ID = ?"
        val sqlArgs = arrayOf(id.toString())
        val db = databaseHelper.readableDatabase
        var cursor: Cursor? = null
        return try {
            cursor = db.rawQuery(sql, sqlArgs)
            if (cursor != null && cursor.moveToFirst()) cursor.getLong(0) else -1
        } finally {
            cursor?.close()
        }
    }

    @Throws(RecipientFormattingException::class, MmsException::class)
    private fun getThreadIdFor(retrieved: IncomingMediaMessage): Long {
        return if (retrieved.groupId != null) {
            val groupRecipients = Recipient.from(
                context,
                retrieved.groupId,
                true
            )
            get(context).threadDatabase().getOrCreateThreadIdFor(groupRecipients)
        } else {
            val sender = Recipient.from(
                context,
                retrieved.from,
                true
            )
            get(context).threadDatabase().getOrCreateThreadIdFor(sender)
        }
    }

    private fun getThreadIdFor(notification: NotificationInd): Long {
        val fromString =
            if (notification.from != null && notification.from.textString != null) toIsoString(
                notification.from.textString
            ) else ""
        val recipient = Recipient.from(context, fromExternal(context, fromString), false)
        return get(context).threadDatabase().getOrCreateThreadIdFor(recipient)
    }

    private fun rawQuery(where: String, arguments: Array<String>?): Cursor {
        val database = databaseHelper.readableDatabase
        return database.rawQuery(
            "SELECT " + MMS_PROJECTION.joinToString(",")+
                    " FROM " + TABLE_NAME + " LEFT OUTER JOIN " + AttachmentDatabase.TABLE_NAME +
                    " ON (" + TABLE_NAME + "." + ID + " = " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + ")" +
                    " WHERE " + where + " GROUP BY " + TABLE_NAME + "." + ID, arguments
        )
    }

    fun getMessages(idsAsString: String): Cursor {
        return rawQuery(idsAsString, null)
    }

    fun getMessage(messageId: Long): Cursor {
        val cursor = rawQuery(RAW_ID_WHERE, arrayOf(messageId.toString()))
        setNotifyConverationListeners(cursor, getThreadIdForMessage(messageId))
        return cursor
    }

    val expireStartedMessages: Reader
        get() {
            val where = "$EXPIRE_STARTED > 0"
            return readerFor(rawQuery(where, null))!!
        }

    private fun updateMailboxBitmask(
        id: Long,
        maskOff: Long,
        maskOn: Long,
        threadId: Optional<Long>
    ) {
        val db = databaseHelper.writableDatabase
        db.execSQL(
            "UPDATE " + TABLE_NAME +
                    " SET " + MESSAGE_BOX + " = (" + MESSAGE_BOX + " & " + (MmsSmsColumns.Types.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
                    " WHERE " + ID + " = ?", arrayOf(id.toString() + "")
        )
        if (threadId.isPresent) {
            get(context).threadDatabase().update(threadId.get(), false)
        }
    }

    fun markAsPendingInsecureSmsFallback(messageId: Long) {
        val threadId = getThreadIdForMessage(messageId)
        updateMailboxBitmask(
            messageId,
            MmsSmsColumns.Types.BASE_TYPE_MASK,
            MmsSmsColumns.Types.BASE_PENDING_INSECURE_SMS_FALLBACK,
            Optional.of(threadId)
        )
        notifyConversationListeners(threadId)
    }

    fun markAsSending(messageId: Long) {
        val threadId = getThreadIdForMessage(messageId)
        updateMailboxBitmask(
            messageId,
            MmsSmsColumns.Types.BASE_TYPE_MASK,
            MmsSmsColumns.Types.BASE_SENDING_TYPE,
            Optional.of(threadId)
        )
        notifyConversationListeners(threadId)
    }

    fun markAsSentFailed(messageId: Long) {
        val threadId = getThreadIdForMessage(messageId)
        updateMailboxBitmask(
            messageId,
            MmsSmsColumns.Types.BASE_TYPE_MASK,
            MmsSmsColumns.Types.BASE_SENT_FAILED_TYPE,
            Optional.of(threadId)
        )
        notifyConversationListeners(threadId)
    }

    override fun markAsSent(messageId: Long, secure: Boolean) {
        val threadId = getThreadIdForMessage(messageId)
        updateMailboxBitmask(
            messageId,
            MmsSmsColumns.Types.BASE_TYPE_MASK,
            MmsSmsColumns.Types.BASE_SENT_TYPE or if (secure) MmsSmsColumns.Types.PUSH_MESSAGE_BIT or MmsSmsColumns.Types.SECURE_MESSAGE_BIT else 0,
            Optional.of(threadId)
        )
        notifyConversationListeners(threadId)
    }

    override fun markUnidentified(messageId: Long, unidentified: Boolean) {
        val contentValues = ContentValues()
        contentValues.put(UNIDENTIFIED, if (unidentified) 1 else 0)
        val db = databaseHelper.writableDatabase
        db.update(TABLE_NAME, contentValues, ID_WHERE, arrayOf(messageId.toString()))
    }

    override fun markAsDeleted(messageId: Long, read: Boolean) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(READ, 1)
        contentValues.put(BODY, "")
        database.update(TABLE_NAME, contentValues, ID_WHERE, arrayOf(messageId.toString()))
        val attachmentDatabase = get(context).attachmentDatabase()
        queue(Runnable { attachmentDatabase.deleteAttachmentsForMessage(messageId) })
        val threadId = getThreadIdForMessage(messageId)
        if (!read) {
            get(context).threadDatabase().decrementUnread(threadId, 1)
        }
        updateMailboxBitmask(
            messageId,
            MmsSmsColumns.Types.BASE_TYPE_MASK,
            MmsSmsColumns.Types.BASE_DELETED_TYPE,
            Optional.of(threadId)
        )
        notifyConversationListeners(threadId)
    }

    override fun markExpireStarted(messageId: Long) {
        markExpireStarted(messageId, System.currentTimeMillis())
    }

    override fun markExpireStarted(messageId: Long, startedTimestamp: Long) {
        val contentValues = ContentValues()
        contentValues.put(EXPIRE_STARTED, startedTimestamp)
        val db = databaseHelper.writableDatabase
        db.update(TABLE_NAME, contentValues, ID_WHERE, arrayOf(messageId.toString()))
        val threadId = getThreadIdForMessage(messageId)
        notifyConversationListeners(threadId)
    }

    fun markAsNotified(id: Long) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues()
        contentValues.put(NOTIFIED, 1)
        database.update(TABLE_NAME, contentValues, ID_WHERE, arrayOf(id.toString()))
    }

    fun setMessagesRead(threadId: Long): List<MarkedMessageInfo> {
        return setMessagesRead(
            THREAD_ID + " = ? AND " + READ + " = 0",
            arrayOf(threadId.toString())
        )
    }

    fun setAllMessagesRead(): List<MarkedMessageInfo> {
        return setMessagesRead(READ + " = 0", null)
    }

    private fun setMessagesRead(where: String, arguments: Array<String>?): List<MarkedMessageInfo> {
        val database = databaseHelper.writableDatabase
        val result: MutableList<MarkedMessageInfo> = LinkedList()
        var cursor: Cursor? = null
        database.beginTransaction()
        try {
            cursor = database.query(
                TABLE_NAME,
                arrayOf<String>(ID, ADDRESS, DATE_SENT, MESSAGE_BOX, EXPIRES_IN, EXPIRE_STARTED),
                where,
                arguments,
                null,
                null,
                null
            )
            while (cursor != null && cursor.moveToNext()) {
                if (MmsSmsColumns.Types.isSecureType(cursor.getLong(3))) {
                    val syncMessageId =
                        SyncMessageId(fromSerialized(cursor.getString(1)), cursor.getLong(2))
                    val expirationInfo = ExpirationInfo(
                        cursor.getLong(0),
                        cursor.getLong(4),
                        cursor.getLong(5),
                        true
                    )
                    result.add(MarkedMessageInfo(syncMessageId, expirationInfo))
                }
            }
            val contentValues = ContentValues()
            contentValues.put(READ, 1)
            database.update(TABLE_NAME, contentValues, where, arguments)
            database.setTransactionSuccessful()
        } finally {
            cursor?.close()
            database.endTransaction()
        }
        return result
    }

    @Throws(MmsException::class, NoSuchMessageException::class)
    fun getOutgoingMessage(messageId: Long): OutgoingMediaMessage {
        val attachmentDatabase = get(context).attachmentDatabase()
        var cursor: Cursor? = null
        try {
            cursor = rawQuery(RAW_ID_WHERE, arrayOf(messageId.toString()))
            if (cursor.moveToNext()) {
                val associatedAttachments = attachmentDatabase.getAttachmentsForMessage(messageId)
                val outboxType = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(BODY))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(NORMALIZED_DATE_SENT))
                val subscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(SUBSCRIPTION_ID))
                val expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN))
                val address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS))
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID))
                val distributionType = get(context).threadDatabase().getDistributionType(threadId)
                val mismatchDocument = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        MISMATCHED_IDENTITIES
                    )
                )
                val networkDocument = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        NETWORK_FAILURE
                    )
                )
                val quoteId = cursor.getLong(cursor.getColumnIndexOrThrow(QUOTE_ID))
                val quoteAuthor = cursor.getString(cursor.getColumnIndexOrThrow(QUOTE_AUTHOR))
                val quoteText = cursor.getString(cursor.getColumnIndexOrThrow(QUOTE_BODY)) // TODO: this should be the referenced quote
                val quoteMissing = cursor.getInt(cursor.getColumnIndexOrThrow(QUOTE_MISSING)) == 1
                val quoteAttachments = associatedAttachments
                    .filter { obj: DatabaseAttachment -> obj.isQuote }
                val contacts = getSharedContacts(cursor, associatedAttachments)
                val contactAttachments: Set<Attachment> =
                    contacts.mapNotNull { obj: Contact -> obj.avatarAttachment }.toSet()
                val previews = getLinkPreviews(cursor, associatedAttachments)
                val previewAttachments =
                    previews.filter { lp: LinkPreview -> lp.getThumbnail().isPresent }
                        .map { lp: LinkPreview -> lp.getThumbnail().get() }
                val attachments = associatedAttachments
                    .asSequence()
                    .filterNot { obj: DatabaseAttachment -> obj.isQuote || contactAttachments.contains(obj) || previewAttachments.contains(obj) }
                    .toList()
                val recipient = Recipient.from(context, fromSerialized(address), false)
                var networkFailures: List<NetworkFailure?>? = LinkedList()
                var mismatches: List<IdentityKeyMismatch?>? = LinkedList()
                var quote: QuoteModel? = null
                if (quoteId > 0 && (!TextUtils.isEmpty(quoteText) || quoteAttachments.isNotEmpty())) {
                    quote = QuoteModel(
                        quoteId,
                        fromSerialized(quoteAuthor),
                        quoteText, // TODO: refactor this to use referenced quote
                        quoteMissing,
                        quoteAttachments
                    )
                }
                if (!TextUtils.isEmpty(mismatchDocument)) {
                    try {
                        mismatches = JsonUtil.fromJson(
                            mismatchDocument,
                            IdentityKeyMismatchList::class.java
                        ).list
                    } catch (e: IOException) {
                        Log.w(TAG, e)
                    }
                }
                if (!TextUtils.isEmpty(networkDocument)) {
                    try {
                        networkFailures =
                            JsonUtil.fromJson(networkDocument, NetworkFailureList::class.java).list
                    } catch (e: IOException) {
                        Log.w(TAG, e)
                    }
                }
                val message = OutgoingMediaMessage(
                    recipient,
                    body,
                    attachments,
                    timestamp,
                    subscriptionId,
                    expiresIn,
                    distributionType,
                    quote,
                    contacts,
                    previews,
                    networkFailures!!,
                    mismatches!!
                )
                return if (MmsSmsColumns.Types.isSecureType(outboxType)) {
                    OutgoingSecureMediaMessage(message)
                } else message
            }
            throw NoSuchMessageException("No record found for id: $messageId")
        } finally {
            cursor?.close()
        }
    }

    private fun getSharedContacts(
        cursor: Cursor,
        attachments: List<DatabaseAttachment>
    ): List<Contact> {
        val serializedContacts = cursor.getString(cursor.getColumnIndexOrThrow(SHARED_CONTACTS))
        if (TextUtils.isEmpty(serializedContacts)) {
            return emptyList()
        }
        val attachmentIdMap: MutableMap<AttachmentId?, DatabaseAttachment> = HashMap()
        for (attachment in attachments) {
            attachmentIdMap[attachment.attachmentId] = attachment
        }
        try {
            val contacts: MutableList<Contact> = LinkedList()
            val jsonContacts = JSONArray(serializedContacts)
            for (i in 0 until jsonContacts.length()) {
                val contact = Contact.deserialize(jsonContacts.getJSONObject(i).toString())
                if (contact.avatar != null && contact.avatar!!.attachmentId != null) {
                    val attachment = attachmentIdMap[contact.avatar!!.attachmentId]
                    val updatedAvatar = Contact.Avatar(
                        contact.avatar!!.attachmentId,
                        attachment,
                        contact.avatar!!.isProfile
                    )
                    contacts.add(Contact(contact, updatedAvatar))
                } else {
                    contacts.add(contact)
                }
            }
            return contacts
        } catch (e: JSONException) {
            Log.w(TAG, "Failed to parse shared contacts.", e)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to parse shared contacts.", e)
        }
        return emptyList()
    }

    private fun getLinkPreviews(
        cursor: Cursor,
        attachments: List<DatabaseAttachment>
    ): List<LinkPreview> {
        val serializedPreviews = cursor.getString(cursor.getColumnIndexOrThrow(LINK_PREVIEWS))
        if (TextUtils.isEmpty(serializedPreviews)) {
            return emptyList()
        }
        val attachmentIdMap: MutableMap<AttachmentId?, DatabaseAttachment> = HashMap()
        for (attachment in attachments) {
            attachmentIdMap[attachment.attachmentId] = attachment
        }
        try {
            val previews: MutableList<LinkPreview> = LinkedList()
            val jsonPreviews = JSONArray(serializedPreviews)
            for (i in 0 until jsonPreviews.length()) {
                val preview = LinkPreview.deserialize(jsonPreviews.getJSONObject(i).toString())
                if (preview.attachmentId != null) {
                    val attachment = attachmentIdMap[preview.attachmentId]
                    if (attachment != null) {
                        previews.add(LinkPreview(preview.url, preview.title, attachment))
                    }
                } else {
                    previews.add(preview)
                }
            }
            return previews
        } catch (e: JSONException) {
            Log.w(TAG, "Failed to parse shared contacts.", e)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to parse shared contacts.", e)
        }
        return emptyList()
    }

    @Throws(MmsException::class)
    private fun insertMessageInbox(
        retrieved: IncomingMediaMessage,
        contentLocation: String,
        threadId: Long, mailbox: Long,
        serverTimestamp: Long,
        runIncrement: Boolean,
        runThreadUpdate: Boolean
    ): Optional<InsertResult> {
        var threadId = threadId
        if (threadId == -1L || retrieved.isGroupMessage) {
            try {
                threadId = getThreadIdFor(retrieved)
            } catch (e: RecipientFormattingException) {
                Log.w("MmsDatabase", e)
                if (threadId == -1L) throw MmsException(e)
            }
        }
        val contentValues = ContentValues()
        contentValues.put(DATE_SENT, retrieved.sentTimeMillis)
        contentValues.put(ADDRESS, retrieved.from.serialize())
        contentValues.put(MESSAGE_BOX, mailbox)
        contentValues.put(MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF)
        contentValues.put(THREAD_ID, threadId)
        contentValues.put(CONTENT_LOCATION, contentLocation)
        contentValues.put(STATUS, Status.DOWNLOAD_INITIALIZED)
        // In open groups messages should be sorted by their server timestamp
        var receivedTimestamp = serverTimestamp
        if (serverTimestamp == 0L) {
            receivedTimestamp = retrieved.sentTimeMillis
        }
        contentValues.put(
            DATE_RECEIVED,
            receivedTimestamp
        ) // Loki - This is important due to how we handle GIFs
        contentValues.put(PART_COUNT, retrieved.attachments.size)
        contentValues.put(SUBSCRIPTION_ID, retrieved.subscriptionId)
        contentValues.put(EXPIRES_IN, retrieved.expiresIn)
        contentValues.put(READ, if (retrieved.isExpirationUpdate) 1 else 0)
        contentValues.put(UNIDENTIFIED, retrieved.isUnidentified)
        contentValues.put(MESSAGE_REQUEST_RESPONSE, retrieved.isMessageRequestResponse)
        if (!contentValues.containsKey(DATE_SENT)) {
            contentValues.put(DATE_SENT, contentValues.getAsLong(DATE_RECEIVED))
        }
        var quoteAttachments: List<Attachment?>? = LinkedList()
        if (retrieved.quote != null) {
            contentValues.put(QUOTE_ID, retrieved.quote.id)
            contentValues.put(QUOTE_AUTHOR, retrieved.quote.author.serialize())
            contentValues.put(QUOTE_MISSING, if (retrieved.quote.missing) 1 else 0)
            quoteAttachments = retrieved.quote.attachments
        }
        if (retrieved.isPushMessage && isDuplicate(retrieved, threadId) ||
            retrieved.isMessageRequestResponse && isDuplicateMessageRequestResponse(
                retrieved,
                threadId
            )
        ) {
            Log.w(TAG, "Ignoring duplicate media message (" + retrieved.sentTimeMillis + ")")
            return Optional.absent()
        }
        val messageId = insertMediaMessage(
            retrieved.body,
            retrieved.attachments,
            quoteAttachments!!,
            retrieved.sharedContacts,
            retrieved.linkPreviews,
            contentValues,
            null,
        )
        if (!MmsSmsColumns.Types.isExpirationTimerUpdate(mailbox)) {
            if (runIncrement) {
                get(context).threadDatabase().incrementUnread(threadId, 1)
            }
            if (runThreadUpdate) {
                get(context).threadDatabase().update(threadId, true)
            }
        }
        notifyConversationListeners(threadId)
        return Optional.of(InsertResult(messageId, threadId))
    }

    @Throws(MmsException::class)
    fun insertSecureDecryptedMessageOutbox(
        retrieved: OutgoingMediaMessage,
        threadId: Long,
        serverTimestamp: Long,
        runThreadUpdate: Boolean
    ): Optional<InsertResult> {
        var threadId = threadId
        if (threadId == -1L) {
            if (retrieved.isGroup) {
                val decodedGroupId: String = if (retrieved is OutgoingExpirationUpdateMessage) {
                    retrieved.groupId
                } else {
                    (retrieved as OutgoingGroupMediaMessage).groupId
                }
                val groupId: String
                groupId = try {
                    doubleEncodeGroupID(decodedGroupId)
                } catch (e: IOException) {
                    Log.e(TAG, "Couldn't encrypt group ID")
                    throw MmsException(e)
                }
                val group = Recipient.from(context, fromSerialized(groupId), false)
                threadId = get(context).threadDatabase().getOrCreateThreadIdFor(group)
            } else {
                threadId = get(context).threadDatabase().getOrCreateThreadIdFor(retrieved.recipient)
            }
        }
        val messageId = insertMessageOutbox(retrieved, threadId, false, null, serverTimestamp, runThreadUpdate)
        if (messageId == -1L) {
            return Optional.absent()
        }
        markAsSent(messageId, true)
        return Optional.fromNullable(InsertResult(messageId, threadId))
    }

    @JvmOverloads
    @Throws(MmsException::class)
    fun insertSecureDecryptedMessageInbox(
        retrieved: IncomingMediaMessage,
        threadId: Long,
        serverTimestamp: Long = 0,
        runIncrement: Boolean,
        runThreadUpdate: Boolean
    ): Optional<InsertResult> {
        var type = MmsSmsColumns.Types.BASE_INBOX_TYPE or MmsSmsColumns.Types.SECURE_MESSAGE_BIT
        if (retrieved.isPushMessage) {
            type = type or MmsSmsColumns.Types.PUSH_MESSAGE_BIT
        }
        if (retrieved.isExpirationUpdate) {
            type = type or MmsSmsColumns.Types.EXPIRATION_TIMER_UPDATE_BIT
        }
        if (retrieved.isScreenshotDataExtraction) {
            type = type or MmsSmsColumns.Types.SCREENSHOT_EXTRACTION_BIT
        }
        if (retrieved.isMediaSavedDataExtraction) {
            type = type or MmsSmsColumns.Types.MEDIA_SAVED_EXTRACTION_BIT
        }
        if (retrieved.isMessageRequestResponse) {
            type = type or MmsSmsColumns.Types.MESSAGE_REQUEST_RESPONSE_BIT
        }
        return insertMessageInbox(retrieved, "", threadId, type, serverTimestamp, runIncrement, runThreadUpdate)
    }

    @JvmOverloads
    @Throws(MmsException::class)
    fun insertMessageOutbox(
        message: OutgoingMediaMessage,
        threadId: Long, forceSms: Boolean,
        insertListener: InsertListener?,
        serverTimestamp: Long = 0,
        runThreadUpdate: Boolean
    ): Long {
        var type = MmsSmsColumns.Types.BASE_SENDING_TYPE
        if (message.isSecure) type =
            type or (MmsSmsColumns.Types.SECURE_MESSAGE_BIT or MmsSmsColumns.Types.PUSH_MESSAGE_BIT)
        if (forceSms) type = type or MmsSmsColumns.Types.MESSAGE_FORCE_SMS_BIT
        if (message.isGroup && message is OutgoingGroupMediaMessage) {
            if (message.isUpdateMessage) type = type or MmsSmsColumns.Types.GROUP_UPDATE_MESSAGE_BIT
        }
        if (message.isExpirationUpdate) {
            type = type or MmsSmsColumns.Types.EXPIRATION_TIMER_UPDATE_BIT
        }
        val earlyDeliveryReceipts = earlyDeliveryReceiptCache.remove(message.sentTimeMillis)
        val earlyReadReceipts = earlyReadReceiptCache.remove(message.sentTimeMillis)
        val contentValues = ContentValues()
        contentValues.put(DATE_SENT, message.sentTimeMillis)
        contentValues.put(MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ)
        contentValues.put(MESSAGE_BOX, type)
        contentValues.put(THREAD_ID, threadId)
        contentValues.put(READ, 1)
        // In open groups messages should be sorted by their server timestamp
        var receivedTimestamp = serverTimestamp
        if (serverTimestamp == 0L) {
            receivedTimestamp = System.currentTimeMillis()
        }
        contentValues.put(DATE_RECEIVED, receivedTimestamp)
        contentValues.put(SUBSCRIPTION_ID, message.subscriptionId)
        contentValues.put(EXPIRES_IN, message.expiresIn)
        contentValues.put(ADDRESS, message.recipient.address.serialize())
        contentValues.put(
            DELIVERY_RECEIPT_COUNT,
            Stream.of(earlyDeliveryReceipts.values).mapToLong { obj: Long -> obj }
                .sum())
        contentValues.put(
            READ_RECEIPT_COUNT,
            Stream.of(earlyReadReceipts.values).mapToLong { obj: Long -> obj }
                .sum())
        val quoteAttachments: MutableList<Attachment?> = LinkedList()
        if (message.outgoingQuote != null) {
            contentValues.put(QUOTE_ID, message.outgoingQuote!!.id)
            contentValues.put(QUOTE_AUTHOR, message.outgoingQuote!!.author.serialize())
            contentValues.put(QUOTE_MISSING, if (message.outgoingQuote!!.missing) 1 else 0)
            quoteAttachments.addAll(message.outgoingQuote!!.attachments!!)
        }
        if (isDuplicate(message, threadId)) {
            Log.w(TAG, "Ignoring duplicate media message (" + message.sentTimeMillis + ")")
            return -1
        }
        val messageId = insertMediaMessage(
            message.body,
            message.attachments,
            quoteAttachments,
            message.sharedContacts,
            message.linkPreviews,
            contentValues,
            insertListener,
        )
        if (message.recipient.address.isGroup) {
            val members = get(context).groupDatabase()
                .getGroupMembers(message.recipient.address.toGroupString(), false)
            val receiptDatabase = get(context).groupReceiptDatabase()
            receiptDatabase.insert(Stream.of(members).map { obj: Recipient -> obj.address }
                .toList(),
                messageId, GroupReceiptDatabase.STATUS_UNDELIVERED, message.sentTimeMillis
            )
            for (address in earlyDeliveryReceipts.keys) receiptDatabase.update(
                address,
                messageId,
                GroupReceiptDatabase.STATUS_DELIVERED,
                -1
            )
            for (address in earlyReadReceipts.keys) receiptDatabase.update(
                address,
                messageId,
                GroupReceiptDatabase.STATUS_READ,
                -1
            )
        }
        with (get(context).threadDatabase()) {
            setLastSeen(threadId)
            setHasSent(threadId, true)
            if (runThreadUpdate) {
                update(threadId, true)
            }
        }
        return messageId
    }

    @Throws(MmsException::class)
    private fun insertMediaMessage(
        body: String?,
        attachments: List<Attachment?>,
        quoteAttachments: List<Attachment?>,
        sharedContacts: List<Contact>,
        linkPreviews: List<LinkPreview>,
        contentValues: ContentValues,
        insertListener: InsertListener?,
    ): Long {
        val db = databaseHelper.writableDatabase
        val partsDatabase = get(context).attachmentDatabase()
        val allAttachments: MutableList<Attachment?> = LinkedList()
        val contactAttachments =
            Stream.of(sharedContacts).map { obj: Contact -> obj.avatarAttachment }
                .filter { a: Attachment? -> a != null }
                .toList()
        val previewAttachments =
            Stream.of(linkPreviews).filter { lp: LinkPreview -> lp.getThumbnail().isPresent }
                .map { lp: LinkPreview -> lp.getThumbnail().get() }
                .toList()
        allAttachments.addAll(attachments)
        allAttachments.addAll(contactAttachments)
        allAttachments.addAll(previewAttachments)
        contentValues.put(BODY, body)
        contentValues.put(PART_COUNT, allAttachments.size)
        db.beginTransaction()
        return try {
            val messageId = db.insert(TABLE_NAME, null, contentValues)
            val insertedAttachments = partsDatabase.insertAttachmentsForMessage(
                messageId,
                allAttachments,
                quoteAttachments
            )
            val serializedContacts =
                getSerializedSharedContacts(insertedAttachments, sharedContacts)
            val serializedPreviews = getSerializedLinkPreviews(insertedAttachments, linkPreviews)
            if (!TextUtils.isEmpty(serializedContacts)) {
                val contactValues = ContentValues()
                contactValues.put(SHARED_CONTACTS, serializedContacts)
                val database = databaseHelper.readableDatabase
                val rows = database.update(
                    TABLE_NAME,
                    contactValues,
                    "$ID = ?",
                    arrayOf(messageId.toString())
                )
                if (rows <= 0) {
                    Log.w(TAG, "Failed to update message with shared contact data.")
                }
            }
            if (!TextUtils.isEmpty(serializedPreviews)) {
                val contactValues = ContentValues()
                contactValues.put(LINK_PREVIEWS, serializedPreviews)
                val database = databaseHelper.readableDatabase
                val rows = database.update(
                    TABLE_NAME,
                    contactValues,
                    "$ID = ?",
                    arrayOf(messageId.toString())
                )
                if (rows <= 0) {
                    Log.w(TAG, "Failed to update message with link preview data.")
                }
            }
            db.setTransactionSuccessful()
            messageId
        } finally {
            db.endTransaction()
            insertListener?.onComplete()
            notifyConversationListeners(contentValues.getAsLong(THREAD_ID))
        }
    }

    private fun deleteQuotedFromMessages(toDeleteRecords: List<MessageRecord>) {
        if (toDeleteRecords.isEmpty()) return
        val queryBuilder = StringBuilder()
        for (i in toDeleteRecords.indices) {
            queryBuilder.append("$QUOTE_ID = ").append(toDeleteRecords[i].getId())
            if (i + 1 < toDeleteRecords.size) {
                queryBuilder.append(" OR ")
            }
        }
        val query = queryBuilder.toString()
        val db = databaseHelper.writableDatabase
        val values = ContentValues(2)
        values.put(QUOTE_MISSING, 1)
        values.put(QUOTE_AUTHOR, "")
        db!!.update(TABLE_NAME, values, query, null)
    }

    /**
     * Delete all the messages in single queries where possible
     * @param messageIds a String array representation of regularly Long types representing message IDs
     */
    private fun deleteMessages(messageIds: Array<String?>) {
        if (messageIds.isEmpty()) {
            return
        }
        // don't need thread IDs
        val queryBuilder = StringBuilder()
        for (i in messageIds.indices) {
            queryBuilder.append("$TABLE_NAME.$ID").append(" = ").append(
                messageIds[i]
            )
            if (i + 1 < messageIds.size) {
                queryBuilder.append(" OR ")
            }
        }
        val idsAsString = queryBuilder.toString()
        val attachmentDatabase = get(context).attachmentDatabase()
        queue(Runnable { attachmentDatabase.deleteAttachmentsForMessages(messageIds) })
        val groupReceiptDatabase = get(context).groupReceiptDatabase()
        groupReceiptDatabase.deleteRowsForMessages(messageIds)
        val database = databaseHelper.writableDatabase
        database.delete(TABLE_NAME, idsAsString, null)
        notifyConversationListListeners()
        notifyStickerListeners()
        notifyStickerPackListeners()
    }

    override fun deleteMessage(messageId: Long): Boolean {
        val threadId = getThreadIdForMessage(messageId)
        val attachmentDatabase = get(context).attachmentDatabase()
        queue(Runnable { attachmentDatabase.deleteAttachmentsForMessage(messageId) })
        val groupReceiptDatabase = get(context).groupReceiptDatabase()
        groupReceiptDatabase.deleteRowsForMessage(messageId)
        val database = databaseHelper.writableDatabase
        database!!.delete(TABLE_NAME, ID_WHERE, arrayOf(messageId.toString()))
        val threadDeleted = get(context).threadDatabase().update(threadId, false)
        notifyConversationListeners(threadId)
        notifyStickerListeners()
        notifyStickerPackListeners()
        return threadDeleted
    }

    override fun updateThreadId(fromId: Long, toId: Long) {
        val contentValues = ContentValues(1)
        contentValues.put(THREAD_ID, toId)

        val db = databaseHelper.writableDatabase
        db.update(SmsDatabase.TABLE_NAME, contentValues, "$THREAD_ID = ?", arrayOf("$fromId"))
        notifyConversationListeners(toId)
        notifyConversationListListeners()
    }

    fun deleteThread(threadId: Long) {
        deleteThreads(setOf(threadId))
    }

    private fun getSerializedSharedContacts(
        insertedAttachmentIds: Map<Attachment?, AttachmentId?>,
        contacts: List<Contact?>
    ): String? {
        if (contacts.isEmpty()) return null
        val sharedContactJson = JSONArray()
        for (contact in contacts) {
            try {
                var attachmentId: AttachmentId? = null
                if (contact!!.avatarAttachment != null) {
                    attachmentId = insertedAttachmentIds[contact.avatarAttachment]
                }
                val updatedAvatar = Contact.Avatar(
                    attachmentId,
                    contact.avatarAttachment,
                    contact.avatar != null && contact.avatar!!
                        .isProfile
                )
                val updatedContact = Contact(
                    contact, updatedAvatar
                )
                sharedContactJson.put(JSONObject(updatedContact.serialize()))
            } catch (e: JSONException) {
                Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e)
            } catch (e: IOException) {
                Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e)
            }
        }
        return sharedContactJson.toString()
    }

    private fun getSerializedLinkPreviews(
        insertedAttachmentIds: Map<Attachment?, AttachmentId?>,
        previews: List<LinkPreview?>
    ): String? {
        if (previews.isEmpty()) return null
        val linkPreviewJson = JSONArray()
        for (preview in previews) {
            try {
                var attachmentId: AttachmentId? = null
                if (preview!!.getThumbnail().isPresent) {
                    attachmentId = insertedAttachmentIds[preview.getThumbnail().get()]
                }
                val updatedPreview = LinkPreview(
                    preview.url, preview.title, attachmentId
                )
                linkPreviewJson.put(JSONObject(updatedPreview.serialize()))
            } catch (e: JSONException) {
                Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e)
            } catch (e: IOException) {
                Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e)
            }
        }
        return linkPreviewJson.toString()
    }

    private fun isDuplicateMessageRequestResponse(
        message: IncomingMediaMessage?,
        threadId: Long
    ): Boolean {
        val database = databaseHelper.readableDatabase
        val cursor: Cursor? = database!!.query(
            TABLE_NAME,
            null,
            MESSAGE_REQUEST_RESPONSE + " = 1 AND " + ADDRESS + " = ? AND " + THREAD_ID + " = ?",
            arrayOf<String?>(
                message!!.from.serialize(), threadId.toString()
            ),
            null,
            null,
            null,
            "1"
        )
        return try {
            cursor != null && cursor.moveToFirst()
        } finally {
            cursor?.close()
        }
    }

    private fun isDuplicate(message: IncomingMediaMessage?, threadId: Long): Boolean {
        val database = databaseHelper.readableDatabase
        val cursor: Cursor? = database!!.query(
            TABLE_NAME,
            null,
            DATE_SENT + " = ? AND " + ADDRESS + " = ? AND " + THREAD_ID + " = ?",
            arrayOf<String?>(
                message!!.sentTimeMillis.toString(), message.from.serialize(), threadId.toString()
            ),
            null,
            null,
            null,
            "1"
        )
        return try {
            cursor != null && cursor.moveToFirst()
        } finally {
            cursor?.close()
        }
    }

    private fun isDuplicate(message: OutgoingMediaMessage?, threadId: Long): Boolean {
        val database = databaseHelper.readableDatabase
        val cursor: Cursor? = database!!.query(
            TABLE_NAME,
            null,
            DATE_SENT + " = ? AND " + ADDRESS + " = ? AND " + THREAD_ID + " = ?",
            arrayOf<String?>(
                message!!.sentTimeMillis.toString(),
                message.recipient.address.serialize(),
                threadId.toString()
            ),
            null,
            null,
            null,
            "1"
        )
        return try {
            cursor != null && cursor.moveToFirst()
        } finally {
            cursor?.close()
        }
    }

    fun isSent(messageId: Long): Boolean {
        val database = databaseHelper.readableDatabase
        database!!.query(
            TABLE_NAME,
            arrayOf(MESSAGE_BOX),
            "$ID = ?",
            arrayOf<String?>(messageId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor != null && cursor.moveToNext()) {
                val type = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX))
                return MmsSmsColumns.Types.isSentType(type)
            }
        }
        return false
    }

    /*package*/
    private fun deleteThreads(threadIds: Set<Long>) {
        val db = databaseHelper.writableDatabase
        val where = StringBuilder()
        var cursor: Cursor? = null
        for (threadId in threadIds) {
            where.append(THREAD_ID).append(" = '").append(threadId).append("' OR ")
        }
        val whereString = where.substring(0, where.length - 4)
        try {
            cursor =
                db!!.query(TABLE_NAME, arrayOf<String?>(ID), whereString, null, null, null, null)
            val toDeleteStringMessageIds = mutableListOf<String>()
            while (cursor.moveToNext()) {
                toDeleteStringMessageIds += cursor.getLong(0).toString()
            }
            // TODO: this can probably be optimized out,
            //  currently attachmentDB uses MmsID not threadID which makes it difficult to delete
            //  and clean up on threadID alone
            toDeleteStringMessageIds.toList().chunked(50).forEach { sublist ->
                deleteMessages(sublist.toTypedArray())
            }
        } finally {
            cursor?.close()
        }
        val threadDb = get(context).threadDatabase()
        for (threadId in threadIds) {
            val threadDeleted = threadDb.update(threadId, false)
            notifyConversationListeners(threadId)
        }
        notifyStickerListeners()
        notifyStickerPackListeners()
    }

    /*package*/
    fun deleteMessagesInThreadBeforeDate(threadId: Long, date: Long) {
        var cursor: Cursor? = null
        try {
            val db = databaseHelper.readableDatabase
            var where =
                THREAD_ID + " = ? AND (CASE (" + MESSAGE_BOX + " & " + MmsSmsColumns.Types.BASE_TYPE_MASK + ") "
            for (outgoingType in MmsSmsColumns.Types.OUTGOING_MESSAGE_TYPES) {
                where += " WHEN $outgoingType THEN $DATE_SENT < $date"
            }
            where += " ELSE $DATE_RECEIVED < $date END)"
            cursor = db!!.query(
                TABLE_NAME,
                arrayOf<String?>(ID),
                where,
                arrayOf<String?>(threadId.toString() + ""),
                null,
                null,
                null
            )
            while (cursor != null && cursor.moveToNext()) {
                Log.i("MmsDatabase", "Trimming: " + cursor.getLong(0))
                deleteMessage(cursor.getLong(0))
            }
        } finally {
            cursor?.close()
        }
    }

    fun readerFor(cursor: Cursor?): Reader {
        return Reader(cursor)
    }

    fun readerFor(message: OutgoingMediaMessage?, threadId: Long): OutgoingMessageReader {
        return OutgoingMessageReader(message, threadId)
    }

    fun setQuoteMissing(messageId: Long): Int {
        val contentValues = ContentValues()
        contentValues.put(QUOTE_MISSING, 1)
        val database = databaseHelper.writableDatabase
        return database!!.update(
            TABLE_NAME,
            contentValues,
            "$ID = ?",
            arrayOf<String?>(messageId.toString())
        )
    }

    object Status {
        const val DOWNLOAD_INITIALIZED = 1
        const val DOWNLOAD_NO_CONNECTIVITY = 2
        const val DOWNLOAD_CONNECTING = 3
    }

    inner class OutgoingMessageReader(private val message: OutgoingMediaMessage?,
                                      private val threadId: Long) {
        private val id = SecureRandom().nextLong()
        val current: MessageRecord
            get() {
                val slideDeck = SlideDeck(context, message!!.attachments)
                return MediaMmsMessageRecord(
                    id, message.recipient, message.recipient,
                    1, System.currentTimeMillis(), System.currentTimeMillis(),
                    0, threadId, message.body,
                    slideDeck, slideDeck.slides.size,
                    if (message.isSecure) MmsSmsColumns.Types.getOutgoingEncryptedMessageType() else MmsSmsColumns.Types.getOutgoingSmsMessageType(),
                    LinkedList(),
                    LinkedList(),
                    message.subscriptionId,
                    message.expiresIn,
                    System.currentTimeMillis(), 0,
                    if (message.outgoingQuote != null) Quote(
                        message.outgoingQuote!!.id,
                        message.outgoingQuote!!.author,
                        message.outgoingQuote!!.text, // TODO: use the referenced message's content
                        message.outgoingQuote!!.missing,
                        SlideDeck(context, message.outgoingQuote!!.attachments!!)
                    ) else null,
                    message.sharedContacts, message.linkPreviews, false
                )
            }

    }

    inner class Reader(private val cursor: Cursor?) : Closeable {
        val next: MessageRecord?
            get() = if (cursor == null || !cursor.moveToNext()) null else current
        val current: MessageRecord
            get() {
                val mmsType = cursor!!.getLong(cursor.getColumnIndexOrThrow(MESSAGE_TYPE))
                return if (mmsType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND.toLong()) {
                    getNotificationMmsMessageRecord(cursor)
                } else {
                    getMediaMmsMessageRecord(cursor)
                }
            }

        private fun getNotificationMmsMessageRecord(cursor: Cursor): NotificationMmsMessageRecord {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(ID))
            val dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(NORMALIZED_DATE_SENT))
            val dateReceived = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    NORMALIZED_DATE_RECEIVED
                )
            )
            val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID))
            val mailbox = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX))
            val address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS))
            val addressDeviceId = cursor.getInt(cursor.getColumnIndexOrThrow(ADDRESS_DEVICE_ID))
            val recipient = getRecipientFor(address)
            val contentLocation = cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_LOCATION))
            val transactionId = cursor.getString(cursor.getColumnIndexOrThrow(TRANSACTION_ID))
            val messageSize = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_SIZE))
            val expiry = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRY))
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(STATUS))
            val deliveryReceiptCount = cursor.getInt(
                cursor.getColumnIndexOrThrow(
                    DELIVERY_RECEIPT_COUNT
                )
            )
            var readReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(READ_RECEIPT_COUNT))
            val subscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(SUBSCRIPTION_ID))
            if (!isReadReceiptsEnabled(context)) {
                readReceiptCount = 0
            }
            var contentLocationBytes: ByteArray? = null
            var transactionIdBytes: ByteArray? = null
            if (!TextUtils.isEmpty(contentLocation)) contentLocationBytes = toIsoBytes(
                contentLocation!!
            )
            if (!TextUtils.isEmpty(transactionId)) transactionIdBytes = toIsoBytes(
                transactionId!!
            )
            val slideDeck = SlideDeck(context, MmsNotificationAttachment(status, messageSize))
            return NotificationMmsMessageRecord(
                id, recipient, recipient,
                dateSent, dateReceived, deliveryReceiptCount, threadId,
                contentLocationBytes, messageSize, expiry, status,
                transactionIdBytes, mailbox, slideDeck,
                readReceiptCount
            )
        }

        private fun getMediaMmsMessageRecord(cursor: Cursor): MediaMmsMessageRecord {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(ID))
            val dateSent = cursor.getLong(cursor.getColumnIndexOrThrow(NORMALIZED_DATE_SENT))
            val dateReceived = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    NORMALIZED_DATE_RECEIVED
                )
            )
            val box = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX))
            val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID))
            val address = cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS))
            val addressDeviceId = cursor.getInt(cursor.getColumnIndexOrThrow(ADDRESS_DEVICE_ID))
            val deliveryReceiptCount = cursor.getInt(
                cursor.getColumnIndexOrThrow(
                    DELIVERY_RECEIPT_COUNT
                )
            )
            var readReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(READ_RECEIPT_COUNT))
            val body = cursor.getString(cursor.getColumnIndexOrThrow(BODY))
            val partCount = cursor.getInt(cursor.getColumnIndexOrThrow(PART_COUNT))
            val mismatchDocument = cursor.getString(
                cursor.getColumnIndexOrThrow(
                    MISMATCHED_IDENTITIES
                )
            )
            val networkDocument = cursor.getString(cursor.getColumnIndexOrThrow(NETWORK_FAILURE))
            val subscriptionId = cursor.getInt(cursor.getColumnIndexOrThrow(SUBSCRIPTION_ID))
            val expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN))
            val expireStarted = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRE_STARTED))
            val unidentified = cursor.getInt(cursor.getColumnIndexOrThrow(UNIDENTIFIED)) == 1
            if (!isReadReceiptsEnabled(context)) {
                readReceiptCount = 0
            }
            val recipient = getRecipientFor(address)
            val mismatches = getMismatchedIdentities(mismatchDocument)
            val networkFailures = getFailures(networkDocument)
            val attachments = get(context).attachmentDatabase().getAttachment(
                cursor
            )
            val contacts: List<Contact?> = getSharedContacts(
                cursor, attachments
            )
            val contactAttachments =
                contacts.map { obj: Contact? -> obj!!.avatarAttachment }
                    .filter { a: Attachment? -> a != null }
                    .toSet()
            val previews: List<LinkPreview?> = getLinkPreviews(
                cursor, attachments
            )
            val previewAttachments =
                previews.filter { lp: LinkPreview? -> lp!!.getThumbnail().isPresent }
                    .map { lp: LinkPreview? -> lp!!.getThumbnail().get() }
                    .toSet()
            val slideDeck = getSlideDeck(
                Stream.of(attachments)
                    .filterNot { o: DatabaseAttachment? -> contactAttachments.contains(o) }
                    .filterNot { o: DatabaseAttachment? -> previewAttachments.contains(o) }
                    .toList()
            )
            val quote = getQuote(cursor)
            return MediaMmsMessageRecord(
                id, recipient, recipient,
                addressDeviceId, dateSent, dateReceived, deliveryReceiptCount,
                threadId, body, slideDeck!!, partCount, box, mismatches,
                networkFailures, subscriptionId, expiresIn, expireStarted,
                readReceiptCount, quote, contacts, previews, unidentified
            )
        }

        private fun getRecipientFor(serialized: String?): Recipient {
            val address: Address = if (TextUtils.isEmpty(serialized) || "insert-address-token" == serialized) {
                UNKNOWN
            } else {
                fromSerialized(serialized!!)
            }
            return Recipient.from(context, address, true)
        }

        private fun getMismatchedIdentities(document: String?): List<IdentityKeyMismatch?>? {
            if (!TextUtils.isEmpty(document)) {
                try {
                    return JsonUtil.fromJson(document, IdentityKeyMismatchList::class.java).list
                } catch (e: IOException) {
                    Log.w(TAG, e)
                }
            }
            return LinkedList()
        }

        private fun getFailures(document: String?): List<NetworkFailure?>? {
            if (!TextUtils.isEmpty(document)) {
                try {
                    return JsonUtil.fromJson(document, NetworkFailureList::class.java).list
                } catch (ioe: IOException) {
                    Log.w(TAG, ioe)
                }
            }
            return LinkedList()
        }

        private fun getSlideDeck(attachments: List<DatabaseAttachment?>): SlideDeck? {
            val messageAttachments: List<Attachment?>? = Stream.of(attachments)
                .filterNot { obj: DatabaseAttachment? -> obj!!.isQuote }
                .toList()
            return SlideDeck(context, messageAttachments!!)
        }

        private fun getQuote(cursor: Cursor): Quote? {
            val quoteId = cursor.getLong(cursor.getColumnIndexOrThrow(QUOTE_ID))
            val quoteAuthor = cursor.getString(cursor.getColumnIndexOrThrow(QUOTE_AUTHOR))
            val retrievedQuote = get(context).mmsSmsDatabase().getMessageFor(quoteId, quoteAuthor)
            val quoteText = retrievedQuote?.body
            val quoteMissing = retrievedQuote == null
            val attachments = get(context).attachmentDatabase().getAttachment(cursor)
            val quoteAttachments: List<Attachment?>? =
                Stream.of(attachments).filter { obj: DatabaseAttachment? -> obj!!.isQuote }
                    .toList()
            val quoteDeck = SlideDeck(context, quoteAttachments!!)
            return if (quoteId > 0 && !TextUtils.isEmpty(quoteAuthor)) {
                Quote(
                    quoteId,
                    fromExternal(context, quoteAuthor),
                    quoteText,
                    quoteMissing,
                    quoteDeck
                )
            } else {
                null
            }
        }

        override fun close() {
            cursor?.close()
        }
    }

    companion object {
        private val TAG = MmsDatabase::class.java.simpleName
        const val TABLE_NAME: String = "mms"
        const val DATE_SENT: String = "date"
        const val DATE_RECEIVED: String = "date_received"
        const val MESSAGE_BOX: String = "msg_box"
        const val CONTENT_LOCATION: String = "ct_l"
        const val EXPIRY: String = "exp"
        const val MESSAGE_TYPE: String = "m_type"
        const val MESSAGE_SIZE: String = "m_size"
        const val STATUS: String = "st"
        const val TRANSACTION_ID: String = "tr_id"
        const val PART_COUNT: String = "part_count"
        const val NETWORK_FAILURE: String = "network_failures"
        const val QUOTE_ID: String = "quote_id"
        const val QUOTE_AUTHOR: String = "quote_author"
        const val QUOTE_BODY: String = "quote_body"
        const val QUOTE_ATTACHMENT: String = "quote_attachment"
        const val QUOTE_MISSING: String = "quote_missing"
        const val SHARED_CONTACTS: String = "shared_contacts"
        const val LINK_PREVIEWS: String = "previews"
        const val CREATE_TABLE: String =
            "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, " +
                    THREAD_ID + " INTEGER, " + DATE_SENT + " INTEGER, " + DATE_RECEIVED + " INTEGER, " + MESSAGE_BOX + " INTEGER, " +
                    READ + " INTEGER DEFAULT 0, " + "m_id" + " TEXT, " + "sub" + " TEXT, " +
                    "sub_cs" + " INTEGER, " + BODY + " TEXT, " + PART_COUNT + " INTEGER, " +
                    "ct_t" + " TEXT, " + CONTENT_LOCATION + " TEXT, " + ADDRESS + " TEXT, " +
                    ADDRESS_DEVICE_ID + " INTEGER, " +
                    EXPIRY + " INTEGER, " + "m_cls" + " TEXT, " + MESSAGE_TYPE + " INTEGER, " +
                    "v" + " INTEGER, " + MESSAGE_SIZE + " INTEGER, " + "pri" + " INTEGER, " +
                    "rr" + " INTEGER, " + "rpt_a" + " INTEGER, " + "resp_st" + " INTEGER, " +
                    STATUS + " INTEGER, " + TRANSACTION_ID + " TEXT, " + "retr_st" + " INTEGER, " +
                    "retr_txt" + " TEXT, " + "retr_txt_cs" + " INTEGER, " + "read_status" + " INTEGER, " +
                    "ct_cls" + " INTEGER, " + "resp_txt" + " TEXT, " + "d_tm" + " INTEGER, " +
                    DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + MISMATCHED_IDENTITIES + " TEXT DEFAULT NULL, " +
                    NETWORK_FAILURE + " TEXT DEFAULT NULL," + "d_rpt" + " INTEGER, " +
                    SUBSCRIPTION_ID + " INTEGER DEFAULT -1, " + EXPIRES_IN + " INTEGER DEFAULT 0, " +
                    EXPIRE_STARTED + " INTEGER DEFAULT 0, " + NOTIFIED + " INTEGER DEFAULT 0, " +
                    READ_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + QUOTE_ID + " INTEGER DEFAULT 0, " +
                    QUOTE_AUTHOR + " TEXT, " + QUOTE_BODY + " TEXT, " + QUOTE_ATTACHMENT + " INTEGER DEFAULT -1, " +
                    QUOTE_MISSING + " INTEGER DEFAULT 0, " + SHARED_CONTACTS + " TEXT, " + UNIDENTIFIED + " INTEGER DEFAULT 0, " +
                    LINK_PREVIEWS + " TEXT);"

        @JvmField
        val CREATE_INDEXS: Array<String> = arrayOf(
            "CREATE INDEX IF NOT EXISTS mms_thread_id_index ON $TABLE_NAME ($THREAD_ID);",
            "CREATE INDEX IF NOT EXISTS mms_read_index ON $TABLE_NAME ($READ);",
            "CREATE INDEX IF NOT EXISTS mms_read_and_notified_and_thread_id_index ON $TABLE_NAME($READ,$NOTIFIED,$THREAD_ID);",
            "CREATE INDEX IF NOT EXISTS mms_message_box_index ON $TABLE_NAME ($MESSAGE_BOX);",
            "CREATE INDEX IF NOT EXISTS mms_date_sent_index ON $TABLE_NAME ($DATE_SENT);",
            "CREATE INDEX IF NOT EXISTS mms_thread_date_index ON $TABLE_NAME ($THREAD_ID, $DATE_RECEIVED);"
        )
        private val MMS_PROJECTION: Array<String> = arrayOf(
            "$TABLE_NAME.$ID AS $ID",
            THREAD_ID,
            "$DATE_SENT AS $NORMALIZED_DATE_SENT",
            "$DATE_RECEIVED AS $NORMALIZED_DATE_RECEIVED",
            MESSAGE_BOX,
            READ,
            CONTENT_LOCATION,
            EXPIRY,
            MESSAGE_TYPE,
            MESSAGE_SIZE,
            STATUS,
            TRANSACTION_ID,
            BODY,
            PART_COUNT,
            ADDRESS,
            ADDRESS_DEVICE_ID,
            DELIVERY_RECEIPT_COUNT,
            READ_RECEIPT_COUNT,
            MISMATCHED_IDENTITIES,
            NETWORK_FAILURE,
            SUBSCRIPTION_ID,
            EXPIRES_IN,
            EXPIRE_STARTED,
            NOTIFIED,
            QUOTE_ID,
            QUOTE_AUTHOR,
            QUOTE_BODY,
            QUOTE_ATTACHMENT,
            QUOTE_MISSING,
            SHARED_CONTACTS,
            LINK_PREVIEWS,
            UNIDENTIFIED,
            "json_group_array(json_object(" +
                    "'" + AttachmentDatabase.ROW_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + ", " +
                    "'" + AttachmentDatabase.UNIQUE_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UNIQUE_ID + ", " +
                    "'" + AttachmentDatabase.MMS_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + ", " +
                    "'" + AttachmentDatabase.SIZE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE + ", " +
                    "'" + AttachmentDatabase.FILE_NAME + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FILE_NAME + ", " +
                    "'" + AttachmentDatabase.DATA + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DATA + ", " +
                    "'" + AttachmentDatabase.THUMBNAIL + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.THUMBNAIL + ", " +
                    "'" + AttachmentDatabase.CONTENT_TYPE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_TYPE + ", " +
                    "'" + AttachmentDatabase.CONTENT_LOCATION + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_LOCATION + ", " +
                    "'" + AttachmentDatabase.FAST_PREFLIGHT_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FAST_PREFLIGHT_ID + "," +
                    "'" + AttachmentDatabase.VOICE_NOTE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VOICE_NOTE + "," +
                    "'" + AttachmentDatabase.WIDTH + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.WIDTH + "," +
                    "'" + AttachmentDatabase.HEIGHT + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.HEIGHT + "," +
                    "'" + AttachmentDatabase.QUOTE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.QUOTE + ", " +
                    "'" + AttachmentDatabase.CONTENT_DISPOSITION + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_DISPOSITION + ", " +
                    "'" + AttachmentDatabase.NAME + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.NAME + ", " +
                    "'" + AttachmentDatabase.TRANSFER_STATE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFER_STATE + ", " +
                    "'" + AttachmentDatabase.CAPTION + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CAPTION + ", " +
                    "'" + AttachmentDatabase.STICKER_PACK_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_ID + ", " +
                    "'" + AttachmentDatabase.STICKER_PACK_KEY + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_KEY + ", " +
                    "'" + AttachmentDatabase.STICKER_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_ID +
                    ")) AS " + AttachmentDatabase.ATTACHMENT_JSON_ALIAS
        )
        private const val RAW_ID_WHERE: String = "$TABLE_NAME._id = ?"
        const val createMessageRequestResponseCommand: String = "ALTER TABLE $TABLE_NAME ADD COLUMN $MESSAGE_REQUEST_RESPONSE INTEGER DEFAULT 0;"
    }
}
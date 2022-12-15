package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteConstraintException
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBoolean
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageLogEntry
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.RecipientAccessList
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.SendMessageResult
import org.whispersystems.signalservice.internal.push.SignalServiceProtos

/**
 * Stores a 24-hr buffer of all outgoing messages. Used for the retry logic required for sender key.
 *
 * General note: This class is actually three tables:
 * - one to store the entry
 * - one to store all the devices that were sent it, and
 * - one to store the set of related messages.
 *
 * The general lifecycle of entries in the store goes something like this:
 * - Upon sending a message, put an entry in the 'payload table', an entry for each recipient you sent it to in the 'recipient table', and an entry for each
 *   related message in the 'message table'
 * - Whenever you get a delivery receipt, delete the entries in the 'recipient table'
 * - Whenever there's no more records in the 'recipient table' for a given message, delete the entry in the 'message table'
 * - Whenever you delete a message, delete the relevant entries from the 'payload table'
 * - Whenever you read an entry from the table, first trim off all the entries that are too old
 *
 * Because of all of this, you can be sure that if an entry is in this store, it's safe to resend to someone upon request
 *
 * Worth noting that we use triggers + foreign keys to make sure entries in this table are properly cleaned up. Triggers for when you delete a message, and
 * cascading delete foreign keys between these three tables.
 *
 * Performance considerations:
 * - The most common operations by far are:
 *    - Inserting into the table
 *    - Deleting a recipient (in response to a delivery receipt)
 * - We should also optimize for when we delete messages from the sms/mms tables, since you can delete a bunch at once
 * - We *don't* really need to optimize for retrieval, since that happens very infrequently. In particular, we don't want to slow down inserts in order to
 *   improve retrieval time. That means we shouldn't be adding indexes that optimize for retrieval.
 */
class MessageSendLogTables constructor(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(MessageSendLogTables::class.java)

    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(PayloadTable.CREATE_TABLE, RecipientTable.CREATE_TABLE, MessageTable.CREATE_TABLE)

    @JvmField
    val CREATE_INDEXES: Array<String> = PayloadTable.CREATE_INDEXES + RecipientTable.CREATE_INDEXES + MessageTable.CREATE_INDEXES

    @JvmField
    val CREATE_TRIGGERS: Array<String> = PayloadTable.CREATE_TRIGGERS
  }

  private object PayloadTable {
    const val TABLE_NAME = "msl_payload"

    const val ID = "_id"
    const val DATE_SENT = "date_sent"
    const val CONTENT = "content"
    const val CONTENT_HINT = "content_hint"
    const val URGENT = "urgent"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $DATE_SENT INTEGER NOT NULL,
        $CONTENT BLOB NOT NULL,
        $CONTENT_HINT INTEGER NOT NULL,
        $URGENT INTEGER NOT NULL DEFAULT 1
      )
    """

    /** Created for [deleteEntriesForRecipient] */
    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX msl_payload_date_sent_index ON $TABLE_NAME ($DATE_SENT)",
    )

    val CREATE_TRIGGERS = arrayOf(
      """
        CREATE TRIGGER msl_mms_delete AFTER DELETE ON ${org.thoughtcrime.securesms.database.MessageTable.TABLE_NAME} 
        BEGIN 
        	DELETE FROM $TABLE_NAME WHERE $ID IN (SELECT ${MessageTable.PAYLOAD_ID} FROM ${MessageTable.TABLE_NAME} WHERE ${MessageTable.MESSAGE_ID} = old.${org.thoughtcrime.securesms.database.MessageTable.ID} AND ${MessageTable.IS_MMS} = 1);
        END
      """,
      """
        CREATE TRIGGER msl_attachment_delete AFTER DELETE ON ${AttachmentTable.TABLE_NAME}
        BEGIN
        	DELETE FROM $TABLE_NAME WHERE $ID IN (SELECT ${MessageTable.PAYLOAD_ID} FROM ${MessageTable.TABLE_NAME} WHERE ${MessageTable.MESSAGE_ID} = old.${AttachmentTable.MMS_ID} AND ${MessageTable.IS_MMS} = 1);
        END
      """
    )
  }

  private object RecipientTable {
    const val TABLE_NAME = "msl_recipient"

    const val ID = "_id"
    const val PAYLOAD_ID = "payload_id"
    const val RECIPIENT_ID = "recipient_id"
    const val DEVICE = "device"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $PAYLOAD_ID INTEGER NOT NULL REFERENCES ${PayloadTable.TABLE_NAME} (${PayloadTable.ID}) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL, 
        $DEVICE INTEGER NOT NULL
      )
    """

    /** Created for [deleteEntriesForRecipient] */
    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX msl_recipient_recipient_index ON $TABLE_NAME ($RECIPIENT_ID, $DEVICE, $PAYLOAD_ID)",
      "CREATE INDEX msl_recipient_payload_index ON $TABLE_NAME ($PAYLOAD_ID)"
    )
  }

  private object MessageTable {
    const val TABLE_NAME = "msl_message"

    const val ID = "_id"
    const val PAYLOAD_ID = "payload_id"
    const val MESSAGE_ID = "message_id"
    const val IS_MMS = "is_mms"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $PAYLOAD_ID INTEGER NOT NULL REFERENCES ${PayloadTable.TABLE_NAME} (${PayloadTable.ID}) ON DELETE CASCADE,
        $MESSAGE_ID INTEGER NOT NULL, 
        $IS_MMS INTEGER NOT NULL
      )
    """

    /** Created for [PayloadTable.CREATE_TRIGGERS] and [deleteAllRelatedToMessage] */
    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX msl_message_message_index ON $TABLE_NAME ($MESSAGE_ID, $IS_MMS, $PAYLOAD_ID)"
    )
  }

  /** @return The ID of the inserted entry, or -1 if none was inserted. Can be used with [addRecipientToExistingEntryIfPossible] */
  fun insertIfPossible(recipientId: RecipientId, sentTimestamp: Long, sendMessageResult: SendMessageResult, contentHint: ContentHint, messageId: MessageId, urgent: Boolean): Long {
    if (!FeatureFlags.retryReceipts()) return -1

    if (sendMessageResult.isSuccess && sendMessageResult.success.content.isPresent) {
      val recipientDevice = listOf(RecipientDevice(recipientId, sendMessageResult.success.devices))
      return insert(recipientDevice, sentTimestamp, sendMessageResult.success.content.get(), contentHint, listOf(messageId), urgent)
    }

    return -1
  }

  /** @return The ID of the inserted entry, or -1 if none was inserted. Can be used with [addRecipientToExistingEntryIfPossible] */
  fun insertIfPossible(recipientId: RecipientId, sentTimestamp: Long, sendMessageResult: SendMessageResult, contentHint: ContentHint, messageIds: List<MessageId>, urgent: Boolean): Long {
    if (!FeatureFlags.retryReceipts()) return -1

    if (sendMessageResult.isSuccess && sendMessageResult.success.content.isPresent) {
      val recipientDevice = listOf(RecipientDevice(recipientId, sendMessageResult.success.devices))
      return insert(recipientDevice, sentTimestamp, sendMessageResult.success.content.get(), contentHint, messageIds, urgent)
    }

    return -1
  }

  /** @return The ID of the inserted entry, or -1 if none was inserted. Can be used with [addRecipientToExistingEntryIfPossible] */
  fun insertIfPossible(sentTimestamp: Long, possibleRecipients: List<Recipient>, results: List<SendMessageResult>, contentHint: ContentHint, messageId: MessageId, urgent: Boolean): Long {
    if (!FeatureFlags.retryReceipts()) return -1

    val accessList = RecipientAccessList(possibleRecipients)

    val recipientDevices: List<RecipientDevice> = results
      .filter { it.isSuccess && it.success.content.isPresent }
      .map { result ->
        val recipient: Recipient = accessList.requireByAddress(result.address)
        RecipientDevice(recipient.id, result.success.devices)
      }

    if (recipientDevices.isEmpty()) {
      return -1
    }

    val content: SignalServiceProtos.Content = results.first { it.isSuccess && it.success.content.isPresent }.success.content.get()

    return insert(recipientDevices, sentTimestamp, content, contentHint, listOf(messageId), urgent)
  }

  fun addRecipientToExistingEntryIfPossible(payloadId: Long, recipientId: RecipientId, sentTimestamp: Long, sendMessageResult: SendMessageResult, contentHint: ContentHint, messageId: MessageId, urgent: Boolean): Long {
    if (!FeatureFlags.retryReceipts()) return payloadId

    if (sendMessageResult.isSuccess && sendMessageResult.success.content.isPresent) {
      val db = databaseHelper.signalWritableDatabase

      db.beginTransaction()
      try {
        sendMessageResult.success.devices.forEach { device ->
          val recipientValues = ContentValues().apply {
            put(RecipientTable.PAYLOAD_ID, payloadId)
            put(RecipientTable.RECIPIENT_ID, recipientId.serialize())
            put(RecipientTable.DEVICE, device)
          }

          db.insert(RecipientTable.TABLE_NAME, null, recipientValues)
        }

        db.setTransactionSuccessful()
      } catch (e: SQLiteConstraintException) {
        Log.w(TAG, "Failed to append to existing entry. Creating a new one.")
        val newPayloadId = insertIfPossible(recipientId, sentTimestamp, sendMessageResult, contentHint, messageId, urgent)
        db.setTransactionSuccessful()
        return newPayloadId
      } finally {
        db.endTransaction()
      }
    }

    return payloadId
  }

  private fun insert(recipients: List<RecipientDevice>, dateSent: Long, content: SignalServiceProtos.Content, contentHint: ContentHint, messageIds: List<MessageId>, urgent: Boolean): Long {
    val db = databaseHelper.signalWritableDatabase

    db.beginTransaction()
    try {
      val payloadValues = ContentValues().apply {
        put(PayloadTable.DATE_SENT, dateSent)
        put(PayloadTable.CONTENT, content.toByteArray())
        put(PayloadTable.CONTENT_HINT, contentHint.type)
        put(PayloadTable.URGENT, urgent.toInt())
      }

      val payloadId: Long = db.insert(PayloadTable.TABLE_NAME, null, payloadValues)

      val recipientValues: MutableList<ContentValues> = mutableListOf()
      recipients.forEach { recipientDevice ->
        recipientDevice.devices.forEach { device ->
          recipientValues += ContentValues().apply {
            put(RecipientTable.PAYLOAD_ID, payloadId)
            put(RecipientTable.RECIPIENT_ID, recipientDevice.recipientId.serialize())
            put(RecipientTable.DEVICE, device)
          }
        }
      }
      SqlUtil.buildBulkInsert(RecipientTable.TABLE_NAME, arrayOf(RecipientTable.PAYLOAD_ID, RecipientTable.RECIPIENT_ID, RecipientTable.DEVICE), recipientValues)
        .forEach { query -> db.execSQL(query.where, query.whereArgs) }

      val messageValues: MutableList<ContentValues> = mutableListOf()
      messageIds.forEach { messageId ->
        messageValues += ContentValues().apply {
          put(MessageTable.PAYLOAD_ID, payloadId)
          put(MessageTable.MESSAGE_ID, messageId.id)
          put(MessageTable.IS_MMS, 0)
        }
      }
      SqlUtil.buildBulkInsert(MessageTable.TABLE_NAME, arrayOf(MessageTable.PAYLOAD_ID, MessageTable.MESSAGE_ID, MessageTable.IS_MMS), messageValues)
        .forEach { query -> db.execSQL(query.where, query.whereArgs) }

      db.setTransactionSuccessful()
      return payloadId
    } finally {
      db.endTransaction()
    }
  }

  fun getLogEntry(recipientId: RecipientId, device: Int, dateSent: Long): MessageLogEntry? {
    if (!FeatureFlags.retryReceipts()) return null

    trimOldMessages(System.currentTimeMillis(), FeatureFlags.retryRespondMaxAge())

    val db = databaseHelper.signalReadableDatabase
    val table = "${PayloadTable.TABLE_NAME} LEFT JOIN ${RecipientTable.TABLE_NAME} ON ${PayloadTable.TABLE_NAME}.${PayloadTable.ID} = ${RecipientTable.TABLE_NAME}.${RecipientTable.PAYLOAD_ID}"
    val query = "${PayloadTable.DATE_SENT} = ? AND ${RecipientTable.RECIPIENT_ID} = ? AND ${RecipientTable.DEVICE} = ?"
    val args = SqlUtil.buildArgs(dateSent, recipientId, device)

    db.query(table, null, query, args, null, null, null).use { entryCursor ->
      if (entryCursor.moveToFirst()) {
        val payloadId = CursorUtil.requireLong(entryCursor, RecipientTable.PAYLOAD_ID)

        db.query(MessageTable.TABLE_NAME, null, "${MessageTable.PAYLOAD_ID}  = ?", SqlUtil.buildArgs(payloadId), null, null, null).use { messageCursor ->
          val messageIds: MutableList<MessageId> = mutableListOf()

          while (messageCursor.moveToNext()) {
            messageIds.add(
              MessageId(
                id = CursorUtil.requireLong(messageCursor, MessageTable.MESSAGE_ID)
              )
            )
          }

          return MessageLogEntry(
            recipientId = RecipientId.from(CursorUtil.requireLong(entryCursor, RecipientTable.RECIPIENT_ID)),
            dateSent = CursorUtil.requireLong(entryCursor, PayloadTable.DATE_SENT),
            content = SignalServiceProtos.Content.parseFrom(CursorUtil.requireBlob(entryCursor, PayloadTable.CONTENT)),
            contentHint = ContentHint.fromType(CursorUtil.requireInt(entryCursor, PayloadTable.CONTENT_HINT)),
            urgent = entryCursor.requireBoolean(PayloadTable.URGENT),
            relatedMessages = messageIds
          )
        }
      }
    }

    return null
  }

  fun deleteAllRelatedToMessage(messageId: Long, mms: Boolean) {
    if (!FeatureFlags.retryReceipts()) return

    val db = databaseHelper.signalWritableDatabase
    val query = "${PayloadTable.ID} IN (SELECT ${MessageTable.PAYLOAD_ID} FROM ${MessageTable.TABLE_NAME} WHERE ${MessageTable.MESSAGE_ID} = ? AND ${MessageTable.IS_MMS} = ?)"
    val args = SqlUtil.buildArgs(messageId, if (mms) 1 else 0)

    db.delete(PayloadTable.TABLE_NAME, query, args)
  }

  fun deleteEntryForRecipient(dateSent: Long, recipientId: RecipientId, device: Int) {
    if (!FeatureFlags.retryReceipts()) return

    deleteEntriesForRecipient(listOf(dateSent), recipientId, device)
  }

  fun deleteEntriesForRecipient(dateSent: List<Long>, recipientId: RecipientId, device: Int) {
    if (!FeatureFlags.retryReceipts()) return

    val db = databaseHelper.signalWritableDatabase

    db.beginTransaction()
    try {
      val query = """
        ${RecipientTable.RECIPIENT_ID} = ? AND
        ${RecipientTable.DEVICE} = ? AND
        ${RecipientTable.PAYLOAD_ID} IN (
          SELECT ${PayloadTable.ID} 
          FROM ${PayloadTable.TABLE_NAME} 
          WHERE ${PayloadTable.DATE_SENT} IN (${dateSent.joinToString(",")}) 
        )"""
      val args = SqlUtil.buildArgs(recipientId, device)

      db.delete(RecipientTable.TABLE_NAME, query, args)

      val cleanQuery = "${PayloadTable.ID} NOT IN (SELECT ${RecipientTable.PAYLOAD_ID} FROM ${RecipientTable.TABLE_NAME})"
      db.delete(PayloadTable.TABLE_NAME, cleanQuery, null)

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun deleteAll() {
    if (!FeatureFlags.retryReceipts()) return

    databaseHelper.signalWritableDatabase.delete(PayloadTable.TABLE_NAME, null, null)
  }

  fun trimOldMessages(currentTime: Long, maxAge: Long) {
    if (!FeatureFlags.retryReceipts()) return

    val db = databaseHelper.signalWritableDatabase
    val query = "${PayloadTable.DATE_SENT} < ?"
    val args = SqlUtil.buildArgs(currentTime - maxAge)

    db.delete(PayloadTable.TABLE_NAME, query, args)
  }

  override fun remapRecipient(oldRecipientId: RecipientId, newRecipientId: RecipientId) {
    val values = ContentValues().apply {
      put(RecipientTable.RECIPIENT_ID, newRecipientId.serialize())
    }

    val db = databaseHelper.signalWritableDatabase
    val query = "${RecipientTable.RECIPIENT_ID} = ?"
    val args = SqlUtil.buildArgs(oldRecipientId.serialize())

    db.update(RecipientTable.TABLE_NAME, values, query, args)
  }

  private data class RecipientDevice(val recipientId: RecipientId, val devices: List<Int>)
}

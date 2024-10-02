package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireLong
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageLogEntry
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RecipientAccessList
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.SendMessageResult
import org.whispersystems.signalservice.internal.push.Content

/**
 * Stores a rolling buffer of all outgoing messages. Used for the retry logic required for sender key.
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
    val CREATE_TABLE: Array<String> = arrayOf(MslPayloadTable.CREATE_TABLE, MslRecipientTable.CREATE_TABLE, MslMessageTable.CREATE_TABLE)

    @JvmField
    val CREATE_INDEXES: Array<String> = MslPayloadTable.CREATE_INDEXES + MslRecipientTable.CREATE_INDEXES + MslMessageTable.CREATE_INDEXES

    @JvmField
    val CREATE_TRIGGERS: Array<String> = MslPayloadTable.CREATE_TRIGGERS
  }

  private object MslPayloadTable {
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
      "CREATE INDEX msl_payload_date_sent_index ON $TABLE_NAME ($DATE_SENT)"
    )

    const val AFTER_MESSAGE_DELETE_TRIGGER_NAME = "msl_message_delete"
    const val AFTER_MESSAGE_DELETE_TRIGGER = """
      CREATE TRIGGER $AFTER_MESSAGE_DELETE_TRIGGER_NAME AFTER DELETE ON ${MessageTable.TABLE_NAME} 
      BEGIN 
        DELETE FROM $TABLE_NAME WHERE $ID IN (SELECT ${MslMessageTable.PAYLOAD_ID} FROM ${MslMessageTable.TABLE_NAME} WHERE ${MslMessageTable.MESSAGE_ID} = old.${MessageTable.ID});
      END
    """

    val CREATE_TRIGGERS = arrayOf(
      AFTER_MESSAGE_DELETE_TRIGGER,
      """
        CREATE TRIGGER msl_attachment_delete AFTER DELETE ON ${AttachmentTable.TABLE_NAME}
        BEGIN
          DELETE FROM $TABLE_NAME WHERE $ID IN (SELECT ${MslMessageTable.PAYLOAD_ID} FROM ${MslMessageTable.TABLE_NAME} WHERE ${MslMessageTable.TABLE_NAME}.${MslMessageTable.MESSAGE_ID} = old.${AttachmentTable.MESSAGE_ID});
        END
      """
    )
  }

  private object MslRecipientTable {
    const val TABLE_NAME = "msl_recipient"

    const val ID = "_id"
    const val PAYLOAD_ID = "payload_id"
    const val RECIPIENT_ID = "recipient_id"
    const val DEVICE = "device"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $PAYLOAD_ID INTEGER NOT NULL REFERENCES ${MslPayloadTable.TABLE_NAME} (${MslPayloadTable.ID}) ON DELETE CASCADE,
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

  private object MslMessageTable {
    const val TABLE_NAME = "msl_message"

    const val ID = "_id"
    const val PAYLOAD_ID = "payload_id"
    const val MESSAGE_ID = "message_id"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $PAYLOAD_ID INTEGER NOT NULL REFERENCES ${MslPayloadTable.TABLE_NAME} (${MslPayloadTable.ID}) ON DELETE CASCADE,
        $MESSAGE_ID INTEGER NOT NULL
      )
    """

    /** Created for [MslPayloadTable.CREATE_TRIGGERS] and [deleteAllRelatedToMessage] */
    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX msl_message_message_index ON $TABLE_NAME ($MESSAGE_ID, $PAYLOAD_ID)",
      "CREATE INDEX msl_message_payload_index ON $TABLE_NAME ($PAYLOAD_ID)"
    )
  }

  /** @return The ID of the inserted entry, or -1 if none was inserted. Can be used with [addRecipientToExistingEntryIfPossible] */
  fun insertIfPossible(recipientId: RecipientId, sentTimestamp: Long, sendMessageResult: SendMessageResult, contentHint: ContentHint, messageId: MessageId, urgent: Boolean): Long {
    if (!RemoteConfig.retryReceipts) return -1

    if (sendMessageResult.isSuccess && sendMessageResult.success.content.isPresent) {
      val recipientDevice = listOf(RecipientDevice(recipientId, sendMessageResult.success.devices))
      return insert(recipientDevice, sentTimestamp, sendMessageResult.success.content.get(), contentHint, listOf(messageId), urgent)
    }

    return -1
  }

  /** @return The ID of the inserted entry, or -1 if none was inserted. Can be used with [addRecipientToExistingEntryIfPossible] */
  fun insertIfPossible(recipientId: RecipientId, sentTimestamp: Long, sendMessageResult: SendMessageResult, contentHint: ContentHint, messageIds: List<MessageId>, urgent: Boolean): Long {
    if (!RemoteConfig.retryReceipts) return -1

    if (sendMessageResult.isSuccess && sendMessageResult.success.content.isPresent) {
      val recipientDevice = listOf(RecipientDevice(recipientId, sendMessageResult.success.devices))
      return insert(recipientDevice, sentTimestamp, sendMessageResult.success.content.get(), contentHint, messageIds, urgent)
    }

    return -1
  }

  /** @return The ID of the inserted entry, or -1 if none was inserted. Can be used with [addRecipientToExistingEntryIfPossible] */
  fun insertIfPossible(sentTimestamp: Long, possibleRecipients: List<Recipient>, results: List<SendMessageResult>, contentHint: ContentHint, messageId: MessageId, urgent: Boolean): Long {
    if (!RemoteConfig.retryReceipts) return -1

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

    val content: Content = results.first { it.isSuccess && it.success.content.isPresent }.success.content.get()

    return insert(recipientDevices, sentTimestamp, content, contentHint, listOf(messageId), urgent)
  }

  fun addRecipientToExistingEntryIfPossible(payloadId: Long, recipientId: RecipientId, sentTimestamp: Long, sendMessageResult: SendMessageResult, contentHint: ContentHint, messageId: MessageId, urgent: Boolean): Long {
    if (!RemoteConfig.retryReceipts) return payloadId

    if (sendMessageResult.isSuccess && sendMessageResult.success.content.isPresent) {
      val db = databaseHelper.signalWritableDatabase

      db.beginTransaction()
      try {
        sendMessageResult.success.devices.forEach { device ->
          val recipientValues = ContentValues().apply {
            put(MslRecipientTable.PAYLOAD_ID, payloadId)
            put(MslRecipientTable.RECIPIENT_ID, recipientId.serialize())
            put(MslRecipientTable.DEVICE, device)
          }

          db.insert(MslRecipientTable.TABLE_NAME, null, recipientValues)
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

  private fun insert(recipients: List<RecipientDevice>, dateSent: Long, content: Content, contentHint: ContentHint, messageIds: List<MessageId>, urgent: Boolean): Long {
    val db = databaseHelper.signalWritableDatabase

    db.beginTransaction()
    try {
      val payloadValues = ContentValues().apply {
        put(MslPayloadTable.DATE_SENT, dateSent)
        put(MslPayloadTable.CONTENT, content.encode())
        put(MslPayloadTable.CONTENT_HINT, contentHint.type)
        put(MslPayloadTable.URGENT, urgent.toInt())
      }

      val payloadId: Long = db.insert(MslPayloadTable.TABLE_NAME, null, payloadValues)

      val recipientValues: MutableList<ContentValues> = mutableListOf()
      recipients.forEach { recipientDevice ->
        recipientDevice.devices.forEach { device ->
          recipientValues += ContentValues().apply {
            put(MslRecipientTable.PAYLOAD_ID, payloadId)
            put(MslRecipientTable.RECIPIENT_ID, recipientDevice.recipientId.serialize())
            put(MslRecipientTable.DEVICE, device)
          }
        }
      }
      SqlUtil.buildBulkInsert(MslRecipientTable.TABLE_NAME, arrayOf(MslRecipientTable.PAYLOAD_ID, MslRecipientTable.RECIPIENT_ID, MslRecipientTable.DEVICE), recipientValues)
        .forEach { query -> db.execSQL(query.where, query.whereArgs) }

      val messageValues: MutableList<ContentValues> = mutableListOf()
      messageIds.forEach { messageId ->
        messageValues += ContentValues().apply {
          put(MslMessageTable.PAYLOAD_ID, payloadId)
          put(MslMessageTable.MESSAGE_ID, messageId.id)
        }
      }
      SqlUtil.buildBulkInsert(MslMessageTable.TABLE_NAME, arrayOf(MslMessageTable.PAYLOAD_ID, MslMessageTable.MESSAGE_ID), messageValues)
        .forEach { query -> db.execSQL(query.where, query.whereArgs) }

      db.setTransactionSuccessful()
      return payloadId
    } finally {
      db.endTransaction()
    }
  }

  fun getLogEntry(recipientId: RecipientId, device: Int, dateSent: Long): MessageLogEntry? {
    if (!RemoteConfig.retryReceipts) return null

    trimOldMessages(System.currentTimeMillis(), RemoteConfig.retryRespondMaxAge)

    val db = databaseHelper.signalReadableDatabase
    val table = "${MslPayloadTable.TABLE_NAME} LEFT JOIN ${MslRecipientTable.TABLE_NAME} ON ${MslPayloadTable.TABLE_NAME}.${MslPayloadTable.ID} = ${MslRecipientTable.TABLE_NAME}.${MslRecipientTable.PAYLOAD_ID}"
    val query = "${MslPayloadTable.DATE_SENT} = ? AND ${MslRecipientTable.RECIPIENT_ID} = ? AND ${MslRecipientTable.DEVICE} = ?"
    val args = SqlUtil.buildArgs(dateSent, recipientId, device)

    db.query(table, null, query, args, null, null, null).use { entryCursor ->
      if (entryCursor.moveToFirst()) {
        val payloadId = CursorUtil.requireLong(entryCursor, MslRecipientTable.PAYLOAD_ID)

        db.query(MslMessageTable.TABLE_NAME, null, "${MslMessageTable.PAYLOAD_ID}  = ?", SqlUtil.buildArgs(payloadId), null, null, null).use { messageCursor ->
          val messageIds: MutableList<MessageId> = mutableListOf()

          while (messageCursor.moveToNext()) {
            messageIds.add(
              MessageId(
                id = CursorUtil.requireLong(messageCursor, MslMessageTable.MESSAGE_ID)
              )
            )
          }

          return MessageLogEntry(
            recipientId = RecipientId.from(CursorUtil.requireLong(entryCursor, MslRecipientTable.RECIPIENT_ID)),
            dateSent = CursorUtil.requireLong(entryCursor, MslPayloadTable.DATE_SENT),
            content = Content.ADAPTER.decode(CursorUtil.requireBlob(entryCursor, MslPayloadTable.CONTENT)),
            contentHint = ContentHint.fromType(CursorUtil.requireInt(entryCursor, MslPayloadTable.CONTENT_HINT)),
            urgent = entryCursor.requireBoolean(MslPayloadTable.URGENT),
            relatedMessages = messageIds
          )
        }
      }
    }

    return null
  }

  fun deleteAllRelatedToMessage(messageId: Long) {
    val db = databaseHelper.signalWritableDatabase
    val query = "${MslPayloadTable.ID} IN (SELECT ${MslMessageTable.PAYLOAD_ID} FROM ${MslMessageTable.TABLE_NAME} WHERE ${MslMessageTable.MESSAGE_ID} = ?)"
    val args = SqlUtil.buildArgs(messageId)

    db.delete(MslPayloadTable.TABLE_NAME, query, args)
  }

  fun deleteEntryForRecipient(dateSent: Long, recipientId: RecipientId, device: Int) {
    deleteEntriesForRecipient(listOf(dateSent), recipientId, device)
  }

  fun deleteEntriesForRecipient(dateSent: List<Long>, recipientId: RecipientId, device: Int) {
    val db = databaseHelper.signalWritableDatabase
    db.beginTransaction()
    try {
      val query = """
        DELETE FROM ${MslRecipientTable.TABLE_NAME} WHERE
        ${MslRecipientTable.RECIPIENT_ID} = ? AND
        ${MslRecipientTable.DEVICE} = ? AND
        ${MslRecipientTable.PAYLOAD_ID} IN (
          SELECT ${MslPayloadTable.ID} 
          FROM ${MslPayloadTable.TABLE_NAME} 
          WHERE ${MslPayloadTable.DATE_SENT} IN (${dateSent.joinToString(",")}) 
        )
        RETURNING ${MslRecipientTable.PAYLOAD_ID}"""
      val args = SqlUtil.buildArgs(recipientId, device)

      val payloadIds = db.rawQuery(query, args).readToList {
        it.requireLong(MslRecipientTable.PAYLOAD_ID)
      }

      val queries = SqlUtil.buildCollectionQuery(MslPayloadTable.ID, payloadIds)
      queries.forEach {
        db.delete(MslPayloadTable.TABLE_NAME, "${it.where} AND ${MslPayloadTable.ID} NOT IN (SELECT ${MslRecipientTable.PAYLOAD_ID} FROM ${MslRecipientTable.TABLE_NAME})", it.whereArgs)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun deleteAllForRecipient(recipientId: RecipientId) {
    if (!RemoteConfig.retryReceipts) return

    writableDatabase
      .delete(MslRecipientTable.TABLE_NAME)
      .where("${MslRecipientTable.RECIPIENT_ID} = ?", recipientId)
      .run()

    writableDatabase
      .delete(MslPayloadTable.TABLE_NAME)
      .where("${MslPayloadTable.ID} NOT IN (SELECT ${MslRecipientTable.PAYLOAD_ID} FROM ${MslRecipientTable.TABLE_NAME})")
      .run()
  }

  fun deleteAll() {
    databaseHelper.signalWritableDatabase.delete(MslPayloadTable.TABLE_NAME, null, null)
  }

  fun trimOldMessages(currentTime: Long, maxAge: Long) {
    val db = databaseHelper.signalWritableDatabase
    val query = "${MslPayloadTable.DATE_SENT} < ?"
    val args = SqlUtil.buildArgs(currentTime - maxAge)

    db.delete(MslPayloadTable.TABLE_NAME, query, args)
  }

  /**
   * Drop the trigger for updating the [MslPayloadTable] on message deletes. Should only be used for expected large deletes.
   * The caller must be in a transaction and called with a matching [restoreAfterMessageDeleteTrigger] before the transaction
   * completes.
   *
   * Note: The caller is not responsible for performing the missing trigger operations and they will be performed in
   * [restoreAfterMessageDeleteTrigger].
   */
  fun dropAfterMessageDeleteTrigger() {
    check(SignalDatabase.inTransaction)
    writableDatabase.execSQL("DROP TRIGGER IF EXISTS ${MslPayloadTable.AFTER_MESSAGE_DELETE_TRIGGER_NAME}")
  }

  /**
   * Restore the trigger for updating the [MslPayloadTable] on message deletes. Must only be called within the same transaction after calling
   * [dropAfterMessageDeleteTrigger].
   */
  fun restoreAfterMessageDeleteTrigger() {
    check(SignalDatabase.inTransaction)

    val restoreDeleteMessagesOperation = """
      DELETE FROM ${MslPayloadTable.TABLE_NAME} 
      WHERE ${MslPayloadTable.TABLE_NAME}.${MslPayloadTable.ID} IN (
        SELECT ${MslMessageTable.TABLE_NAME}.${MslMessageTable.PAYLOAD_ID} 
        FROM ${MslMessageTable.TABLE_NAME} 
        WHERE ${MslMessageTable.TABLE_NAME}.${MslMessageTable.MESSAGE_ID} NOT IN (
          SELECT ${MessageTable.TABLE_NAME}.${MessageTable.ID} FROM ${MessageTable.TABLE_NAME}
        )
      )
    """

    writableDatabase.execSQL(restoreDeleteMessagesOperation)
    writableDatabase.execSQL(MslPayloadTable.AFTER_MESSAGE_DELETE_TRIGGER)
  }

  override fun remapRecipient(oldRecipientId: RecipientId, newRecipientId: RecipientId) {
    val values = ContentValues().apply {
      put(MslRecipientTable.RECIPIENT_ID, newRecipientId.serialize())
    }

    val db = databaseHelper.signalWritableDatabase
    val query = "${MslRecipientTable.RECIPIENT_ID} = ?"
    val args = SqlUtil.buildArgs(oldRecipientId.serialize())

    db.update(MslRecipientTable.TABLE_NAME, values, query, args)
  }

  private data class RecipientDevice(val recipientId: RecipientId, val devices: List<Int>)
}

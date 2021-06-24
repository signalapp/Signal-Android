package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.database.model.MessageLogEntry
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.CursorUtil
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.SqlUtil
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.messages.SendMessageResult
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import java.util.UUID

/**
 * Stores a 24-hr buffer of all outgoing messages. Used for the retry logic required for sender key.
 *
 * General note: This class is actually two tables -- one to store the entry, and another to store all the devices that were sent it.
 *
 * The general lifecycle of entries in the store goes something like this:
 * - Upon sending a message, throw an entry in the 'message table' and throw an entry for each recipient you sent it to in the 'recipient table'
 * - Whenever you get a delivery receipt, delete the entries in the 'recipient table'
 * - Whenever there's no more records in the 'recipient table' for a given message, delete the entry in the 'message table'
 * - Whenever you delete a message, delete the entry in the 'message table'
 * - Whenever you read an entry from the table, first trim off all the entries that are too old
 *
 * Because of all of this, you can be sure that if an entry is in this store, it's safe to resend to someone upon request
 *
 * Worth noting that we use triggers + foreign keys to make sure entries in this table are properly cleaned up. Triggers for when you delete a message, and
 * a cascading delete foreign key between these two tables.
 */
class MessageSendLogDatabase constructor(context: Context?, databaseHelper: SQLCipherOpenHelper?) : Database(context, databaseHelper) {

  companion object {
    @JvmField
    val CREATE_TABLE: Array<String> = arrayOf(MessageTable.CREATE_TABLE, RecipientTable.CREATE_TABLE)

    @JvmField
    val CREATE_INDEXES: Array<String> = MessageTable.CREATE_INDEXES + RecipientTable.CREATE_INDEXES

    @JvmField
    val CREATE_TRIGGERS: Array<String> = MessageTable.CREATE_TRIGGERS
  }

  private object MessageTable {
    const val TABLE_NAME = "message_send_log"

    const val ID = "_id"
    const val DATE_SENT = "date_sent"
    const val CONTENT = "content"
    const val RELATED_MESSAGE_ID = "related_message_id"
    const val IS_RELATED_MESSAGE_MMS = "is_related_message_mms"
    const val CONTENT_HINT = "content_hint"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $DATE_SENT INTEGER NOT NULL,
        $CONTENT BLOB NOT NULL,
        $RELATED_MESSAGE_ID INTEGER DEFAULT -1,
        $IS_RELATED_MESSAGE_MMS INTEGER DEFAULT 0,
        $CONTENT_HINT INTEGER NOT NULL
      )
    """

    @JvmField
    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX message_log_date_sent_index ON $TABLE_NAME ($DATE_SENT)",
      "CREATE INDEX message_log_related_message_index ON $TABLE_NAME ($RELATED_MESSAGE_ID, $IS_RELATED_MESSAGE_MMS)"
    )

    @JvmField
    val CREATE_TRIGGERS = arrayOf(
      """
        CREATE TRIGGER msl_sms_delete AFTER DELETE ON ${SmsDatabase.TABLE_NAME} 
        BEGIN 
        	DELETE FROM $TABLE_NAME WHERE $RELATED_MESSAGE_ID = old.${SmsDatabase.ID} AND $IS_RELATED_MESSAGE_MMS = 0;
        END
      """,
      """
        CREATE TRIGGER msl_mms_delete AFTER DELETE ON ${MmsDatabase.TABLE_NAME} 
        BEGIN 
        	DELETE FROM $TABLE_NAME WHERE $RELATED_MESSAGE_ID = old.${MmsDatabase.ID} AND $IS_RELATED_MESSAGE_MMS = 1;
        END
      """
    )
  }

  private object RecipientTable {
    const val TABLE_NAME = "message_send_log_recipients"

    const val ID = "_id"
    const val MESSAGE_LOG_ID = "message_send_log_id"
    const val RECIPIENT_ID = "recipient_id"
    const val DEVICE = "device"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MESSAGE_LOG_ID INTEGER NOT NULL REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL, 
        $DEVICE INTEGER NOT NULL
      )
    """

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX message_send_log_recipients_recipient_index ON $TABLE_NAME ($RECIPIENT_ID, $DEVICE)"
    )
  }

  fun insertIfPossible(recipientId: RecipientId, sentTimestamp: Long, sendMessageResult: SendMessageResult, contentHint: ContentHint, relatedMessageId: Long, isRelatedMessageMms: Boolean) {
    if (!FeatureFlags.senderKey()) return

    if (sendMessageResult.isSuccess && sendMessageResult.success.content.isPresent) {
      val recipientDevice = listOf(RecipientDevice(recipientId, sendMessageResult.success.devices))
      insert(recipientDevice, sentTimestamp, sendMessageResult.success.content.get(), contentHint, relatedMessageId, isRelatedMessageMms)
    }
  }

  fun insertIfPossible(sentTimestamp: Long, possibleRecipients: List<Recipient>, results: List<SendMessageResult>, contentHint: ContentHint, relatedMessageId: Long, isRelatedMessageMms: Boolean) {
    if (!FeatureFlags.senderKey()) return

    val recipientsByUuid: Map<UUID, Recipient> = possibleRecipients.filter(Recipient::hasUuid).associateBy(Recipient::requireUuid, { it })
    val recipientsByE164: Map<String, Recipient> = possibleRecipients.filter(Recipient::hasE164).associateBy(Recipient::requireE164, { it })

    val recipientDevices: List<RecipientDevice> = results
      .filter { it.isSuccess && it.success.content.isPresent }
      .map { result ->
        val recipient: Recipient =
          if (result.address.uuid.isPresent) {
            recipientsByUuid[result.address.uuid.get()]!!
          } else {
            recipientsByE164[result.address.number.get()]!!
          }

        RecipientDevice(recipient.id, result.success.devices)
      }

    val content: SignalServiceProtos.Content = results.first { it.isSuccess && it.success.content.isPresent }.success.content.get()

    insert(recipientDevices, sentTimestamp, content, contentHint, relatedMessageId, isRelatedMessageMms)
  }

  private fun insert(recipients: List<RecipientDevice>, dateSent: Long, content: SignalServiceProtos.Content, contentHint: ContentHint, relatedMessageId: Long, isRelatedMessageMms: Boolean) {
    val db = databaseHelper.writableDatabase

    db.beginTransaction()
    try {
      val logValues = ContentValues().apply {
        put(MessageTable.DATE_SENT, dateSent)
        put(MessageTable.CONTENT, content.toByteArray())
        put(MessageTable.CONTENT_HINT, contentHint.type)
        put(MessageTable.RELATED_MESSAGE_ID, relatedMessageId)
        put(MessageTable.IS_RELATED_MESSAGE_MMS, if (isRelatedMessageMms) 1 else 0)
      }

      val messageLogId: Long = db.insert(MessageTable.TABLE_NAME, null, logValues)

      recipients.forEach { recipientDevice ->
        recipientDevice.devices.forEach { device ->
          val recipientValues = ContentValues()
          recipientValues.put(RecipientTable.MESSAGE_LOG_ID, messageLogId)
          recipientValues.put(RecipientTable.RECIPIENT_ID, recipientDevice.recipientId.serialize())
          recipientValues.put(RecipientTable.DEVICE, device)

          db.insert(RecipientTable.TABLE_NAME, null, recipientValues)
        }
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun getLogEntry(recipientId: RecipientId, device: Int, dateSent: Long): MessageLogEntry? {
    if (!FeatureFlags.senderKey()) return null

    trimOldMessages(System.currentTimeMillis(), FeatureFlags.retryRespondMaxAge())

    val db = databaseHelper.readableDatabase
    val table = "${MessageTable.TABLE_NAME} LEFT JOIN ${RecipientTable.TABLE_NAME} ON ${MessageTable.TABLE_NAME}.${MessageTable.ID} = ${RecipientTable.TABLE_NAME}.${RecipientTable.MESSAGE_LOG_ID}"
    val query = "${MessageTable.DATE_SENT} = ? AND ${RecipientTable.RECIPIENT_ID} = ? AND ${RecipientTable.DEVICE} = ?"
    val args = SqlUtil.buildArgs(dateSent, recipientId, device)

    db.query(table, null, query, args, null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        return MessageLogEntry(
          recipientId = RecipientId.from(CursorUtil.requireLong(cursor, RecipientTable.RECIPIENT_ID)),
          dateSent = CursorUtil.requireLong(cursor, MessageTable.DATE_SENT),
          content = SignalServiceProtos.Content.parseFrom(CursorUtil.requireBlob(cursor, MessageTable.CONTENT)),
          contentHint = ContentHint.fromType(CursorUtil.requireInt(cursor, MessageTable.CONTENT_HINT)),
          relatedMessageId = CursorUtil.requireLong(cursor, MessageTable.RELATED_MESSAGE_ID),
          isRelatedMessageMms = CursorUtil.requireBoolean(cursor, MessageTable.IS_RELATED_MESSAGE_MMS)
        )
      }
    }

    return null
  }

  fun deleteAllRelatedToMessage(messageId: Long, mms: Boolean) {
    if (!FeatureFlags.senderKey()) return

    val db = databaseHelper.writableDatabase
    val query = "${MessageTable.RELATED_MESSAGE_ID} = ? AND ${MessageTable.IS_RELATED_MESSAGE_MMS} = ?"
    val args = SqlUtil.buildArgs(messageId, if (mms) 1 else 0)

    db.delete(MessageTable.TABLE_NAME, query, args)
  }

  fun deleteEntryForRecipient(dateSent: Long, recipientId: RecipientId, device: Int) {
    if (!FeatureFlags.senderKey()) return

    deleteEntriesForRecipient(listOf(dateSent), recipientId, device)
  }

  fun deleteEntriesForRecipient(dateSent: List<Long>, recipientId: RecipientId, device: Int) {
    if (!FeatureFlags.senderKey()) return

    val db = databaseHelper.writableDatabase

    db.beginTransaction()
    try {
      val query = """
        ${RecipientTable.RECIPIENT_ID} = ? AND
        ${RecipientTable.DEVICE} = ? AND
        ${RecipientTable.MESSAGE_LOG_ID} IN (
          SELECT ${MessageTable.ID} 
          FROM ${MessageTable.TABLE_NAME} 
          WHERE ${MessageTable.DATE_SENT} IN (${dateSent.joinToString(",")}) 
        )"""
      val args = SqlUtil.buildArgs(recipientId, device)

      db.delete(RecipientTable.TABLE_NAME, query, args)

      val cleanQuery = "${MessageTable.ID} NOT IN (SELECT ${RecipientTable.MESSAGE_LOG_ID} FROM ${RecipientTable.TABLE_NAME})"
      db.delete(MessageTable.TABLE_NAME, cleanQuery, null)

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun deleteAll() {
    if (!FeatureFlags.senderKey()) return

    databaseHelper.writableDatabase.delete(MessageTable.TABLE_NAME, null, null)
  }

  fun trimOldMessages(currentTime: Long, maxAge: Long) {
    if (!FeatureFlags.senderKey()) return

    val db = databaseHelper.writableDatabase
    val query = "${MessageTable.DATE_SENT} < ?"
    val args = SqlUtil.buildArgs(currentTime - maxAge)

    db.delete(MessageTable.TABLE_NAME, query, args)
  }

  private data class RecipientDevice(val recipientId: RecipientId, val devices: List<Int>)
}

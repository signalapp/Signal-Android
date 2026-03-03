package org.thoughtcrime.securesms.database

import android.content.ContentValues
import org.signal.core.util.SqlUtil.buildArgs
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.internal.push.Content

object TestDbUtils {

  fun setMessageReceived(messageId: Long, timestamp: Long) {
    val database: SQLiteDatabase = SignalDatabase.messages.databaseHelper.signalWritableDatabase
    val contentValues = ContentValues()
    contentValues.put(MessageTable.DATE_RECEIVED, timestamp)
    val rowsUpdated = database.update(MessageTable.TABLE_NAME, contentValues, DatabaseTable.ID_WHERE, buildArgs(messageId))
  }

  /**
   * Bulk-inserts a reaction on every Nth message (by _id modulo) in the given thread.
   */
  fun insertReactionsForThread(threadId: Long, authorId: RecipientId, moduloFilter: Int) {
    val db = SignalDatabase.messages.databaseHelper.signalWritableDatabase
    db.execSQL(
      """
      INSERT INTO reaction (message_id, author_id, emoji, date_sent, date_received)
      SELECT ${MessageTable.ID}, ?, '👍', ${MessageTable.DATE_SENT}, ${MessageTable.DATE_RECEIVED}
      FROM ${MessageTable.TABLE_NAME}
      WHERE ${MessageTable.THREAD_ID} = ? AND ${MessageTable.ID} % ? = 0
      """.trimIndent(),
      arrayOf(authorId.toLong().toString(), threadId.toString(), moduloFilter.toString())
    )
  }

  /**
   * Bulk-inserts group receipt rows for every message in the given thread, one row per member.
   */
  fun insertGroupReceiptsForThread(threadId: Long, memberRecipientIds: List<RecipientId>) {
    val db = SignalDatabase.messages.databaseHelper.signalWritableDatabase
    db.beginTransaction()
    try {
      for (recipientId in memberRecipientIds) {
        db.execSQL(
          """
          INSERT INTO group_receipts (mms_id, address, status, timestamp)
          SELECT ${MessageTable.ID}, ?, 2, ${MessageTable.DATE_SENT}
          FROM ${MessageTable.TABLE_NAME}
          WHERE ${MessageTable.THREAD_ID} = ?
          """.trimIndent(),
          arrayOf(recipientId.toLong().toString(), threadId.toString())
        )
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  /**
   * Bulk-inserts a mention on every Nth message (by _id modulo) in the given thread.
   */
  fun insertMentionsForThread(threadId: Long, mentionedRecipientId: RecipientId, moduloFilter: Int) {
    val db = SignalDatabase.messages.databaseHelper.signalWritableDatabase
    db.execSQL(
      """
      INSERT INTO mention (thread_id, message_id, recipient_id, range_start, range_length)
      SELECT ${MessageTable.THREAD_ID}, ${MessageTable.ID}, ?, 0, 5
      FROM ${MessageTable.TABLE_NAME}
      WHERE ${MessageTable.THREAD_ID} = ? AND ${MessageTable.ID} % ? = 0
      """.trimIndent(),
      arrayOf(mentionedRecipientId.toLong().toString(), threadId.toString(), moduloFilter.toString())
    )
  }

  fun getOutgoingMessageTimestamps(threadId: Long, selfRecipientId: Long): List<Long> {
    val timestamps = mutableListOf<Long>()
    SignalDatabase.messages.databaseHelper.signalReadableDatabase.query(
      MessageTable.TABLE_NAME,
      arrayOf(MessageTable.DATE_SENT),
      "${MessageTable.THREAD_ID} = ? AND ${MessageTable.FROM_RECIPIENT_ID} = ?",
      arrayOf(threadId.toString(), selfRecipientId.toString()),
      null,
      null,
      "${MessageTable.DATE_SENT} ASC"
    ).use { cursor ->
      while (cursor.moveToNext()) {
        timestamps += cursor.getLong(0)
      }
    }
    return timestamps
  }

  fun insertMessageSendLogEntries(messageIds: List<Long>, timestamps: List<Long>, recipientIds: List<RecipientId>) {
    val db = SignalDatabase.messages.databaseHelper.signalWritableDatabase
    val dummyContent = Content.Builder().build().encode()

    db.beginTransaction()
    try {
      for (i in messageIds.indices) {
        val payloadValues = ContentValues().apply {
          put("date_sent", timestamps[i])
          put("content", dummyContent)
          put("content_hint", 0)
          put("urgent", 1)
        }
        val payloadId = db.insert("msl_payload", null, payloadValues)

        val messageValues = ContentValues().apply {
          put("payload_id", payloadId)
          put("message_id", messageIds[i])
        }
        db.insert("msl_message", null, messageValues)

        for (recipientId in recipientIds) {
          val recipientValues = ContentValues().apply {
            put("payload_id", payloadId)
            put("recipient_id", recipientId.toLong())
            put("device", 1)
          }
          db.insert("msl_recipient", null, recipientValues)
        }
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }
}

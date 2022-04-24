package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.requireLong
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Sending to a distribution list is a bit trickier. When we send to multiple distribution lists with overlapping membership, we want to
 * show them as distinct items on the sending side, but as a single item on the receiving side. Basically, if Alice has two lists and Bob
 * is on both, Bob should always see a story for "Alice" and not know that Alice has him in multiple lists. And when Bob views the story,
 * Alice should update the UI to show a view in each list. To do this, we need to:
 * 1. Only send a single copy of each story to a given recipient, while
 * 2. Knowing which people would have gotten duplicate copies.
 */
class StorySendsDatabase(context: Context, databaseHelper: SignalDatabase) : Database(context, databaseHelper) {

  companion object {
    const val TABLE_NAME = "story_sends"
    const val ID = "_id"
    const val MESSAGE_ID = "message_id"
    const val RECIPIENT_ID = "recipient_id"
    const val SENT_TIMESTAMP = "sent_timestamp"
    const val ALLOWS_REPLIES = "allows_replies"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MESSAGE_ID INTEGER NOT NULL REFERENCES ${MmsDatabase.TABLE_NAME} (${MmsDatabase.ID}) ON DELETE CASCADE,
        $RECIPIENT_ID INTEGER NOT NULL REFERENCES ${RecipientDatabase.TABLE_NAME} (${RecipientDatabase.ID}) ON DELETE CASCADE,
        $SENT_TIMESTAMP INTEGER NOT NULL,
        $ALLOWS_REPLIES INTEGER NOT NULL
      )
    """.trimIndent()

    val CREATE_INDEX = """
      CREATE INDEX story_sends_recipient_id_sent_timestamp_allows_replies_index ON $TABLE_NAME ($RECIPIENT_ID, $SENT_TIMESTAMP, $ALLOWS_REPLIES)
    """.trimIndent()
  }

  fun insert(messageId: Long, recipientIds: Collection<RecipientId>, sentTimestamp: Long, allowsReplies: Boolean) {
    val db = writableDatabase

    db.beginTransaction()
    try {
      val insertValues: List<ContentValues> = recipientIds.map { id ->
        contentValuesOf(
          MESSAGE_ID to messageId,
          RECIPIENT_ID to id.serialize(),
          SENT_TIMESTAMP to sentTimestamp,
          ALLOWS_REPLIES to allowsReplies.toInt()
        )
      }

      SqlUtil.buildBulkInsert(TABLE_NAME, arrayOf(MESSAGE_ID, RECIPIENT_ID, SENT_TIMESTAMP, ALLOWS_REPLIES), insertValues)
        .forEach { query -> db.execSQL(query.where, query.whereArgs) }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun getRecipientsToSendTo(messageId: Long, sentTimestamp: Long, allowsReplies: Boolean): List<RecipientId> {
    val recipientIds = mutableListOf<RecipientId>()

    val query = """
      SELECT DISTINCT $RECIPIENT_ID
      FROM $TABLE_NAME
      WHERE 
        $MESSAGE_ID = $messageId
        AND $RECIPIENT_ID NOT IN (
          SELECT $RECIPIENT_ID
          FROM $TABLE_NAME
          WHERE
            $SENT_TIMESTAMP = $sentTimestamp
            AND $MESSAGE_ID < $messageId
            AND $ALLOWS_REPLIES >= ${allowsReplies.toInt()}
        )
        AND $RECIPIENT_ID NOT IN (
          SELECT $RECIPIENT_ID
          FROM $TABLE_NAME
          WHERE
            $SENT_TIMESTAMP = $sentTimestamp
            AND $MESSAGE_ID > $messageId
            AND $ALLOWS_REPLIES > ${allowsReplies.toInt()}
        )
    """.trimIndent()

    readableDatabase.rawQuery(query, null).use { cursor ->
      while (cursor.moveToNext()) {
        recipientIds += RecipientId.from(cursor.requireLong(RECIPIENT_ID))
      }
    }

    return recipientIds
  }

  /**
   * The weirdness with remote deletes and stories is that just because you remote-delete a story to List A doesn’t mean you
   * send the delete to everyone on the list – some people have it through multiple lists.
   *
   * The general idea is to find all recipients for a story that still have a non-deleted copy of it.
   */
  fun getRemoteDeleteRecipients(messageId: Long, sentTimestamp: Long): List<RecipientId> {
    val recipientIds = mutableListOf<RecipientId>()

    val query = """
      SELECT $RECIPIENT_ID
      FROM $TABLE_NAME
      WHERE
        $MESSAGE_ID = $messageId
        AND $RECIPIENT_ID NOT IN (
          SELECT $RECIPIENT_ID
          FROM $TABLE_NAME
          WHERE $MESSAGE_ID != $messageId
          AND $SENT_TIMESTAMP = $sentTimestamp
          AND $MESSAGE_ID IN (
            SELECT ${MmsDatabase.ID}
            FROM ${MmsDatabase.TABLE_NAME}
            WHERE ${MmsDatabase.REMOTE_DELETED} = 0
          )
        )
    """.trimIndent()

    readableDatabase.rawQuery(query, null).use { cursor ->
      while (cursor.moveToNext()) {
        recipientIds += RecipientId.from(cursor.requireLong(RECIPIENT_ID))
      }
    }

    return recipientIds
  }

  fun canReply(recipientId: RecipientId, sentTimestamp: Long): Boolean {
    readableDatabase.query(
      TABLE_NAME,
      arrayOf("1"),
      "$RECIPIENT_ID = ? AND $SENT_TIMESTAMP = ? AND $ALLOWS_REPLIES = ?",
      SqlUtil.buildArgs(recipientId, sentTimestamp, 1),
      null,
      null,
      null
    ).use {
      return it.moveToFirst()
    }
  }

  fun getStoryMessagesFor(syncMessageId: MessageDatabase.SyncMessageId): Set<MessageId> {
    val messageIds = mutableSetOf<MessageId>()

    readableDatabase.query(
      TABLE_NAME,
      arrayOf(MESSAGE_ID),
      "$RECIPIENT_ID = ? AND $SENT_TIMESTAMP = ?",
      SqlUtil.buildArgs(syncMessageId.recipientId, syncMessageId.timetamp),
      null,
      null,
      null
    ).use { cursor ->
      while (cursor.moveToNext()) {
        messageIds += MessageId(cursor.requireLong(MESSAGE_ID), true)
      }
    }

    return messageIds
  }

  fun remapRecipient(oldId: RecipientId, newId: RecipientId) {
    val query = "$RECIPIENT_ID = ?"
    val args = SqlUtil.buildArgs(oldId)
    val values = contentValuesOf(RECIPIENT_ID to newId.serialize())

    writableDatabase.update(TABLE_NAME, values, query, args)
  }
}

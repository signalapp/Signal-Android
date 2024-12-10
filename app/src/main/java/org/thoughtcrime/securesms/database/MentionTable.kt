package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
import org.signal.core.util.insertInto
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.recipients.RecipientId

class MentionTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference, ThreadIdDatabaseReference {

  companion object {
    private val TAG = Log.tag(MentionTable::class)

    const val TABLE_NAME = "mention"
    const val ID = "_id"
    const val THREAD_ID = "thread_id"
    const val MESSAGE_ID = "message_id"
    const val RECIPIENT_ID = "recipient_id"
    const val RANGE_START = "range_start"
    const val RANGE_LENGTH = "range_length"

    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME(
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $THREAD_ID INTEGER,
        $MESSAGE_ID INTEGER,
        $RECIPIENT_ID INTEGER,
        $RANGE_START INTEGER,
        $RANGE_LENGTH INTEGER
      )
    """

    private const val MESSAGE_ID_INDEX = "mention_message_id_index"
    private const val RECIPIENT_ID_INDEX = "mention_recipient_id_thread_id_index "

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX IF NOT EXISTS $MESSAGE_ID_INDEX ON $TABLE_NAME ($MESSAGE_ID);",
      "CREATE INDEX IF NOT EXISTS $RECIPIENT_ID_INDEX ON $TABLE_NAME ($RECIPIENT_ID, $THREAD_ID);"
    )
  }

  fun insert(threadId: Long, messageId: Long, mentions: Collection<Mention>) {
    writableDatabase.withinTransaction { db ->
      for (mention in mentions) {
        db.insertInto(TABLE_NAME)
          .values(
            THREAD_ID to threadId,
            MESSAGE_ID to messageId,
            RECIPIENT_ID to mention.recipientId.toLong(),
            RANGE_START to mention.start,
            RANGE_LENGTH to mention.length
          )
          .run()
      }
    }
  }

  fun getMentionsForMessage(messageId: Long): List<Mention> {
    return readableDatabase
      .select()
      .from("$TABLE_NAME INDEXED BY $MESSAGE_ID_INDEX")
      .where("$MESSAGE_ID = $messageId")
      .run()
      .readToList { cursor ->
        Mention(
          RecipientId.from(cursor.requireLong(RECIPIENT_ID)),
          cursor.requireInt(RANGE_START),
          cursor.requireInt(RANGE_LENGTH)
        )
      }
  }

  fun getMentionsForMessages(messageIds: Collection<Long>): Map<Long, List<Mention>> {
    if (messageIds.isEmpty()) {
      return emptyMap()
    }

    val query = SqlUtil.buildFastCollectionQuery(MESSAGE_ID, messageIds)

    return readableDatabase
      .select()
      .from("$TABLE_NAME INDEXED BY $MESSAGE_ID_INDEX")
      .where(query.where, query.whereArgs)
      .run()
      .use { cursor -> readMentions(cursor) }
  }

  fun getMentionsContainingRecipients(recipientIds: Collection<RecipientId>, limit: Long): Map<Long, List<Mention>> {
    return getMentionsContainingRecipients(recipientIds, -1, limit)
  }

  fun getMentionsContainingRecipients(recipientIds: Collection<RecipientId>, threadId: Long, limit: Long): Map<Long, List<Mention>> {
    val ids = recipientIds.joinToString(separator = ",") { it.serialize() }

    var where = "$RECIPIENT_ID IN ($ids)"
    if (threadId != -1L) {
      where += " AND $THREAD_ID = $threadId"
    }

    return readableDatabase
      .select()
      .from("$TABLE_NAME INDEXED BY $MESSAGE_ID_INDEX")
      .where(
        """
        $MESSAGE_ID IN (
          SELECT DISTINCT $MESSAGE_ID
          FROM $TABLE_NAME
          WHERE $where
          ORDER BY $ID DESC LIMIT $limit
        )
      """
      )
      .run()
      .use { cursor -> readMentions(cursor) }
  }

  fun deleteMentionsForMessage(messageId: Long) {
    writableDatabase
      .delete("$TABLE_NAME INDEXED BY $MESSAGE_ID_INDEX")
      .where("$MESSAGE_ID = $messageId")
      .run()
  }

  fun deleteAbandonedMentions() {
    writableDatabase
      .delete("$TABLE_NAME INDEXED BY $MESSAGE_ID_INDEX")
      .where(
        """
        $MESSAGE_ID NOT IN (
          SELECT ${MessageTable.ID}
          FROM ${MessageTable.TABLE_NAME}
        )
        OR $THREAD_ID NOT IN (
          SELECT ${ThreadTable.ID}
          FROM ${ThreadTable.TABLE_NAME}
          WHERE ${ThreadTable.ACTIVE} = 1
        )
      """
      )
      .run()
  }

  fun deleteAllMentions() {
    writableDatabase.deleteAll(TABLE_NAME)
  }

  private fun readMentions(cursor: Cursor): Map<Long, List<Mention>> {
    return cursor.readToList {
      val messageId = it.requireLong(MESSAGE_ID)
      val mention = Mention(
        RecipientId.from(it.requireLong(RECIPIENT_ID)),
        it.requireInt(RANGE_START),
        it.requireInt(RANGE_LENGTH)
      )

      messageId to mention
    }.groupBy({ it.first }, { it.second })
  }

  override fun remapRecipient(fromId: RecipientId, toId: RecipientId) {
    val count = writableDatabase
      .update("$TABLE_NAME INDEXED BY $RECIPIENT_ID_INDEX")
      .values(RECIPIENT_ID to toId.serialize())
      .where("$RECIPIENT_ID = ?", fromId)
      .run()

    Log.d(TAG, "Remapped $fromId to $toId. count: $count")
  }

  override fun remapThread(fromId: Long, toId: Long) {
    writableDatabase
      .update("$TABLE_NAME INDEXED BY $RECIPIENT_ID_INDEX")
      .values(THREAD_ID to toId)
      .where("$THREAD_ID = $fromId")
      .run()
  }
}

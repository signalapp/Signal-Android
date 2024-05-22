package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.update
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Store reactions on messages.
 */
class ReactionTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    const val TABLE_NAME = "reaction"

    private const val ID = "_id"
    const val MESSAGE_ID = "message_id"
    const val AUTHOR_ID = "author_id"
    const val EMOJI = "emoji"
    const val DATE_SENT = "date_sent"
    const val DATE_RECEIVED = "date_received"

    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MESSAGE_ID INTEGER NOT NULL REFERENCES ${MessageTable.TABLE_NAME} (${MessageTable.ID}) ON DELETE CASCADE,
        $AUTHOR_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $EMOJI TEXT NOT NULL,
        $DATE_SENT INTEGER NOT NULL,
        $DATE_RECEIVED INTEGER NOT NULL,
        UNIQUE($MESSAGE_ID, $AUTHOR_ID) ON CONFLICT REPLACE
      )
    """

    @JvmField
    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX IF NOT EXISTS reaction_author_id_index ON $TABLE_NAME ($AUTHOR_ID)"
    )

    private fun readReaction(cursor: Cursor): ReactionRecord {
      return ReactionRecord(
        emoji = CursorUtil.requireString(cursor, EMOJI),
        author = RecipientId.from(CursorUtil.requireLong(cursor, AUTHOR_ID)),
        dateSent = CursorUtil.requireLong(cursor, DATE_SENT),
        dateReceived = CursorUtil.requireLong(cursor, DATE_RECEIVED)
      )
    }
  }

  fun getReactions(messageId: MessageId): List<ReactionRecord> {
    val query = "$MESSAGE_ID = ?"
    val args = SqlUtil.buildArgs(messageId.id)

    val reactions: MutableList<ReactionRecord> = mutableListOf()

    readableDatabase.query(TABLE_NAME, null, query, args, null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        reactions += readReaction(cursor)
      }
    }

    return reactions
  }

  fun getReactionsForMessages(messageIds: Collection<Long>): Map<Long, List<ReactionRecord>> {
    if (messageIds.isEmpty()) {
      return emptyMap()
    }

    val messageIdToReactions: MutableMap<Long, MutableList<ReactionRecord>> = mutableMapOf()

    val args: List<Array<String>> = messageIds.map { SqlUtil.buildArgs(it) }

    for (query: SqlUtil.Query in SqlUtil.buildCustomCollectionQuery("$MESSAGE_ID = ?", args)) {
      readableDatabase.query(TABLE_NAME, null, query.where, query.whereArgs, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val reaction: ReactionRecord = readReaction(cursor)
          val messageId = CursorUtil.requireLong(cursor, MESSAGE_ID)

          var reactionsList: MutableList<ReactionRecord>? = messageIdToReactions[messageId]

          if (reactionsList == null) {
            reactionsList = mutableListOf()
            messageIdToReactions[messageId] = reactionsList
          }

          reactionsList.add(reaction)
        }
      }
    }

    return messageIdToReactions
  }

  fun addReaction(messageId: MessageId, reaction: ReactionRecord) {
    writableDatabase.beginTransaction()
    try {
      val values = ContentValues().apply {
        put(MESSAGE_ID, messageId.id)
        put(EMOJI, reaction.emoji)
        put(AUTHOR_ID, reaction.author.serialize())
        put(DATE_SENT, reaction.dateSent)
        put(DATE_RECEIVED, reaction.dateReceived)
      }

      writableDatabase.insert(TABLE_NAME, null, values)
      SignalDatabase.messages.updateReactionsUnread(writableDatabase, messageId.id, hasReactions(messageId), false)

      writableDatabase.setTransactionSuccessful()
    } finally {
      writableDatabase.endTransaction()
    }

    AppDependencies.databaseObserver.notifyMessageUpdateObservers(messageId)
  }

  fun deleteReaction(messageId: MessageId, recipientId: RecipientId) {
    writableDatabase.beginTransaction()
    try {
      writableDatabase
        .delete(TABLE_NAME)
        .where("$MESSAGE_ID = ? AND $AUTHOR_ID = ?", messageId.id, recipientId)
        .run()

      SignalDatabase.messages.updateReactionsUnread(writableDatabase, messageId.id, hasReactions(messageId), true)

      writableDatabase.setTransactionSuccessful()
    } finally {
      writableDatabase.endTransaction()
    }

    AppDependencies.databaseObserver.notifyMessageUpdateObservers(messageId)
  }

  fun deleteReactions(messageId: MessageId) {
    writableDatabase
      .delete(TABLE_NAME)
      .where("$MESSAGE_ID = ?", messageId.id)
      .run()
  }

  fun hasReaction(messageId: MessageId, reaction: ReactionRecord): Boolean {
    val query = "$MESSAGE_ID = ? AND $AUTHOR_ID = ? AND $EMOJI = ?"
    val args = SqlUtil.buildArgs(messageId.id, reaction.author, reaction.emoji)

    readableDatabase.query(TABLE_NAME, arrayOf(MESSAGE_ID), query, args, null, null, null).use { cursor ->
      return cursor.moveToFirst()
    }
  }

  private fun hasReactions(messageId: MessageId): Boolean {
    val query = "$MESSAGE_ID = ?"
    val args = SqlUtil.buildArgs(messageId.id)

    readableDatabase.query(TABLE_NAME, arrayOf(MESSAGE_ID), query, args, null, null, null).use { cursor ->
      return cursor.moveToFirst()
    }
  }

  override fun remapRecipient(oldAuthorId: RecipientId, newAuthorId: RecipientId) {
    val query = "$AUTHOR_ID = ?"
    val args = SqlUtil.buildArgs(oldAuthorId)
    val values = ContentValues().apply {
      put(AUTHOR_ID, newAuthorId.serialize())
    }

    readableDatabase.update(TABLE_NAME, values, query, args)
  }

  fun deleteAbandonedReactions() {
    writableDatabase
      .delete(TABLE_NAME)
      .where("$MESSAGE_ID NOT IN (SELECT ${MessageTable.ID} FROM ${MessageTable.TABLE_NAME})")
      .run()
  }

  fun moveReactionsToNewMessage(newMessageId: Long, previousId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(MESSAGE_ID to newMessageId)
      .where("$MESSAGE_ID = ?", previousId)
      .run()
  }
}

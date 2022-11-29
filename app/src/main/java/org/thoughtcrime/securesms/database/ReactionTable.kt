package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import org.signal.core.util.CursorUtil
import org.signal.core.util.SqlUtil
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.ReactionRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Store reactions on messages.
 */
class ReactionTable(context: Context, databaseHelper: SignalDatabase) : DatabaseTable(context, databaseHelper), RecipientIdDatabaseReference {

  companion object {
    const val TABLE_NAME = "reaction"

    private const val ID = "_id"
    const val MESSAGE_ID = "message_id"
    const val IS_MMS = "is_mms"
    private const val AUTHOR_ID = "author_id"
    private const val EMOJI = "emoji"
    private const val DATE_SENT = "date_sent"
    private const val DATE_RECEIVED = "date_received"

    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MESSAGE_ID INTEGER NOT NULL,
        $IS_MMS INTEGER NOT NULL,
        $AUTHOR_ID INTEGER NOT NULL REFERENCES ${RecipientTable.TABLE_NAME} (${RecipientTable.ID}) ON DELETE CASCADE,
        $EMOJI TEXT NOT NULL,
        $DATE_SENT INTEGER NOT NULL,
        $DATE_RECEIVED INTEGER NOT NULL,
        UNIQUE($MESSAGE_ID, $IS_MMS, $AUTHOR_ID) ON CONFLICT REPLACE
      )
    """.trimIndent()

    @JvmField
    val CREATE_TRIGGERS = arrayOf(
      """
        CREATE TRIGGER reactions_sms_delete AFTER DELETE ON ${SmsTable.TABLE_NAME} 
        BEGIN 
        	DELETE FROM $TABLE_NAME WHERE $MESSAGE_ID = old.${SmsTable.ID} AND $IS_MMS = 0;
        END
      """,
      """
        CREATE TRIGGER reactions_mms_delete AFTER DELETE ON ${MmsTable.TABLE_NAME} 
        BEGIN 
        	DELETE FROM $TABLE_NAME WHERE $MESSAGE_ID = old.${MmsTable.ID} AND $IS_MMS = 1;
        END
      """
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
    val query = "$MESSAGE_ID = ? AND $IS_MMS = ?"
    val args = SqlUtil.buildArgs(messageId.id, if (messageId.mms) 1 else 0)

    val reactions: MutableList<ReactionRecord> = mutableListOf()

    readableDatabase.query(TABLE_NAME, null, query, args, null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        reactions += readReaction(cursor)
      }
    }

    return reactions
  }

  fun getReactionsForMessages(messageIds: Collection<MessageId>): Map<MessageId, List<ReactionRecord>> {
    if (messageIds.isEmpty()) {
      return emptyMap()
    }

    val messageIdToReactions: MutableMap<MessageId, MutableList<ReactionRecord>> = mutableMapOf()

    val args: List<Array<String>> = messageIds.map { SqlUtil.buildArgs(it.id, if (it.mms) 1 else 0) }

    for (query: SqlUtil.Query in SqlUtil.buildCustomCollectionQuery("$MESSAGE_ID = ? AND $IS_MMS = ?", args)) {
      readableDatabase.query(TABLE_NAME, null, query.where, query.whereArgs, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val reaction: ReactionRecord = readReaction(cursor)
          val messageId = MessageId(
            id = CursorUtil.requireLong(cursor, MESSAGE_ID),
            mms = CursorUtil.requireBoolean(cursor, IS_MMS)
          )

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
        put(IS_MMS, if (messageId.mms) 1 else 0)
        put(EMOJI, reaction.emoji)
        put(AUTHOR_ID, reaction.author.serialize())
        put(DATE_SENT, reaction.dateSent)
        put(DATE_RECEIVED, reaction.dateReceived)
      }

      writableDatabase.insert(TABLE_NAME, null, values)

      if (messageId.mms) {
        SignalDatabase.mms.updateReactionsUnread(writableDatabase, messageId.id, hasReactions(messageId), false)
      } else {
        SignalDatabase.sms.updateReactionsUnread(writableDatabase, messageId.id, hasReactions(messageId), false)
      }

      writableDatabase.setTransactionSuccessful()
    } finally {
      writableDatabase.endTransaction()
    }

    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(messageId)
  }

  fun deleteReaction(messageId: MessageId, recipientId: RecipientId) {

    writableDatabase.beginTransaction()
    try {
      val query = "$MESSAGE_ID = ? AND $IS_MMS = ? AND $AUTHOR_ID = ?"
      val args = SqlUtil.buildArgs(messageId.id, if (messageId.mms) 1 else 0, recipientId)

      writableDatabase.delete(TABLE_NAME, query, args)

      if (messageId.mms) {
        SignalDatabase.mms.updateReactionsUnread(writableDatabase, messageId.id, hasReactions(messageId), true)
      } else {
        SignalDatabase.sms.updateReactionsUnread(writableDatabase, messageId.id, hasReactions(messageId), true)
      }

      writableDatabase.setTransactionSuccessful()
    } finally {
      writableDatabase.endTransaction()
    }

    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(messageId)
  }

  fun deleteReactions(messageId: MessageId) {
    writableDatabase.delete(TABLE_NAME, "$MESSAGE_ID = ? AND $IS_MMS = ?", SqlUtil.buildArgs(messageId.id, if (messageId.mms) 1 else 0))
  }

  fun hasReaction(messageId: MessageId, reaction: ReactionRecord): Boolean {
    val query = "$MESSAGE_ID = ? AND $IS_MMS = ? AND $AUTHOR_ID = ? AND $EMOJI = ?"
    val args = SqlUtil.buildArgs(messageId.id, if (messageId.mms) 1 else 0, reaction.author, reaction.emoji)

    readableDatabase.query(TABLE_NAME, arrayOf(MESSAGE_ID), query, args, null, null, null).use { cursor ->
      return cursor.moveToFirst()
    }
  }

  private fun hasReactions(messageId: MessageId): Boolean {
    val query = "$MESSAGE_ID = ? AND $IS_MMS = ?"
    val args = SqlUtil.buildArgs(messageId.id, if (messageId.mms) 1 else 0)

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
    val query = """
      ($IS_MMS = 0 AND $MESSAGE_ID NOT IN (SELECT ${SmsTable.ID} FROM ${SmsTable.TABLE_NAME}))
      OR
      ($IS_MMS = 1 AND $MESSAGE_ID NOT IN (SELECT ${MmsTable.ID} FROM ${MmsTable.TABLE_NAME}))
    """.trimIndent()

    writableDatabase.delete(TABLE_NAME, query, null)
  }
}

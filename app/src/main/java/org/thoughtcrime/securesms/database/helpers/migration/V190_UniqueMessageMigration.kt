package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.forEach
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong

/**
 * We want to have a unique constraint on message (author, timestamp, thread). Unfortunately, because we haven't had one for all this time, some dupes
 * have snuck in through various conditions.
 *
 * This migration safely removes those dupes, and then adds the desired unique constraint.
 */
object V190_UniqueMessageMigration : SignalDatabaseMigration {

  private val TAG = Log.tag(V190_UniqueMessageMigration::class.java)

  private const val EXPIRATION_TIMER_UPDATE_BIT = 0x40000
  private const val CHAT_SESSION_REFRESHED_BIT = 0x10000000
  private const val GROUP_UPDATE_BIT = 0x10000
  private const val BAD_DECRYPT_TYPE = 13

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")

    // Back in the day, we used to insert expiration updates with the same timestamp as the message that triggered them. To resolve the conflict, we can just
    // shift the timestamp 1ms into the past (which is what we do today for new expiration updates, fwiw).
    // Also, when we insert errors for bad-decrypt/chat-session-refresh messages, it's still possible for the actual message to be successfully resent later.
    // That can result in  duplicates between errors and the originals. We should be able to resolve this conflict the same way: shifting the timestamp back 1ms
    // (which is also what we'll do for new errors moving forward).

    // First, we define a temp table "needs_update", representing all the messages that need to be updated.
    // A message should be updated if it's an expiration or bad-decrypt message and there is more than one message with the same (date_sent, from_recipient_id, thread_id) values.
    // Then we shift all of the date_sent times back 1 ms.
    db.execSQL(
      """
      WITH needs_update AS (
        SELECT
          _id
        FROM
          message M
        WHERE
          (
            type & $EXPIRATION_TIMER_UPDATE_BIT != 0
            OR type & $CHAT_SESSION_REFRESHED_BIT != 0
            OR type = $BAD_DECRYPT_TYPE
          )
          AND (
            SELECT
              COUNT(*)
            FROM
              message INDEXED BY message_date_sent_from_to_thread_index
            WHERE
              date_sent = M.date_sent
              AND from_recipient_id = M.from_recipient_id
              AND thread_id = M.thread_id
          ) > 1
      )
      UPDATE
        message
      SET
        date_sent = date_sent - 1
      WHERE
        _id IN needs_update
      """
    )
    stopwatch.split("fix-timers-errors")

    // Now that we've corrected data that we know we want to preserve, the rest should all be duplicates that we can safely delete.
    // Note that I did a ton of digging into my own database, and all of the duplicates I found were "true" duplicates. Meaning they were literally
    // the same message twice.

    // That being said, this query is overly-constrictive. Instead of deleting all dupes based on (date_sent, from_recipient_id, thread_id), which is what our
    // unique index is doing, we're also going to include the body in that check. This is because, based on my investigation, all of the messages remaining
    // should also have the same body. If they don't, then that means there's a duplicate case I haven't seen and therefore didn't expect, and we don't want to
    // delete it. The index creation will crash and we'll hear about it.

    // Ok, so do this, first we define a temp table "needs_delete", representing all the messages that need to be deleted.
    // A message should be deleted if it has an _id that's greater than the smallest _id with the same (date_sent, from_recipient_id, thread_id, body) values.
    // Note that we coerce null bodies to empty string because I saw examples of duplicate timer events where one had a null body and one had an empty string.
    // Also, there's a known situation where duplicate group update events were found that had differing bodies despite being duplicates in effect, so those
    // are also accounted for.
    // Then we delete all the messages from that temp table.
    db.execSQL(
      """
      WITH needs_delete AS (
        SELECT
          _id
        FROM
          message M
        WHERE
          _id > (
            SELECT
              min(_id)
            FROM
              message INDEXED BY message_date_sent_from_to_thread_index
            WHERE
              date_sent = M.date_sent
              AND from_recipient_id = M.from_recipient_id
              AND thread_id = M.thread_id
              AND (
                COALESCE(body, '') = COALESCE(M.body, '')
                OR type & $GROUP_UPDATE_BIT != 0
              )
          )
      )
      DELETE FROM
        message
      WHERE
        _id IN needs_delete
      """
    )
    stopwatch.split("dedupe")

    db.execSQL("DELETE FROM reaction WHERE message_id NOT IN (SELECT _id FROM message)")
    db.execSQL("DELETE FROM story_sends WHERE message_id NOT IN (SELECT _id FROM message)")
    db.execSQL("DELETE FROM call WHERE message_id NOT NULL AND message_id NOT IN (SELECT _id FROM message)")
    stopwatch.split("fk-deletes")

    // At this point, we should have no more duplicates and can therefore safely create the index
    try {
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS message_unique_sent_from_thread ON message (date_sent, from_recipient_id, thread_id)")
      stopwatch.split("index")
    } catch (e: Exception) {
      logDebugInfo(db)
      throw e
    }

    val foreignKeyViolations: List<SqlUtil.ForeignKeyViolation> = SqlUtil.getForeignKeyViolations(db, "message")
    if (foreignKeyViolations.isNotEmpty()) {
      Log.w(TAG, "Foreign key violations!\n${foreignKeyViolations.joinToString(separator = "\n")}")
      throw IllegalStateException("Foreign key violations!")
    }
    stopwatch.split("fk-check")

    stopwatch.stop(TAG)
  }

  private fun logDebugInfo(db: SQLiteDatabase) {
    var count = 0
    val uniqueTypes = mutableSetOf<Long>()

    db.rawQuery(
      """
      WITH dupes AS (
      SELECT
        _id
        FROM
        message M
        WHERE
          (
            SELECT
              COUNT(*)
            FROM
              message INDEXED BY message_date_sent_from_to_thread_index
            WHERE
              date_sent = M.date_sent
              AND from_recipient_id = M.from_recipient_id
              AND thread_id = M.thread_id
          ) > 1
      )
      SELECT 
        type
      FROM
        message
      WHERE
      _id IN dupes
      """,
      null
    ).forEach { cursor ->
      count++
      uniqueTypes += cursor.requireLong("type")
    }

    Log.w(TAG, "Table still contains $count duplicates! Among those, there are ${uniqueTypes.size} unique types: $uniqueTypes")
  }
}

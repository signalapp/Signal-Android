package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleBoolean
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We want to have a unique constraint on message (author, timestamp, thread). Unfortunately, because we haven't had one for all this time, some dupes
 * may be present.
 *
 * This migration safely removes those dupes, and then adds the desired unique constraint.
 */
@Suppress("ClassName")
object V191_UniqueMessageMigrationV2 : SignalDatabaseMigration {

  private val TAG = Log.tag(V191_UniqueMessageMigrationV2::class.java)

  private const val EXPIRATION_TIMER_UPDATE_BIT = 0x40000
  private const val CHAT_SESSION_REFRESHED_BIT = 0x10000000
  private const val GROUP_UPDATE_BIT = 0x10000
  private const val BAD_DECRYPT_TYPE = 13

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // There was a window where some internal users ran this migration in V190. If it succeeded, they would have this index.
    // We're dropping it to put everyone back on the same playing field.
    db.execSQL("DROP INDEX IF EXISTS message_unique_sent_from_thread")

    // To aid people who may have bad old migration state
    db.execSQL("INSERT INTO message_fts(message_fts) VALUES ('rebuild')")

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

    val remainingDupes: List<Duplicate> = findRemainingDuplicates(db)

    if (remainingDupes.isNotEmpty()) {
      val uniqueTimestamps = remainingDupes.distinctBy { it.dateSent }
      val uniqueTypes = remainingDupes.map { it.type }.toSet()

      Log.w(TAG, "Still had ${remainingDupes.size} remaining duplicates! There are ${uniqueTimestamps.size} unique timestamp(s) and ${uniqueTypes.size} unique type(s): $uniqueTypes")

      // Group each dupe by its (date_sent, thread_id) pair and fix each set
      remainingDupes
        .groupBy { UniqueId(it.dateSent, it.fromRecipientId, it.threadId) }
        .forEach { entry -> fixDuplicate(db, entry.value) }
    }
    stopwatch.split("dupe-purge")

    db.execSQL("DELETE FROM reaction WHERE message_id NOT IN (SELECT _id FROM message)")
    db.execSQL("DELETE FROM story_sends WHERE message_id NOT IN (SELECT _id FROM message)")
    db.execSQL("DELETE FROM call WHERE message_id NOT NULL AND message_id NOT IN (SELECT _id FROM message)")
    stopwatch.split("fk-deletes")

    // At this point, we should have no more duplicates and can therefore safely create the index
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS message_unique_sent_from_thread ON message (date_sent, from_recipient_id, thread_id)")
    stopwatch.split("index")

    val foreignKeyViolations: List<SqlUtil.ForeignKeyViolation> = SqlUtil.getForeignKeyViolations(db, "message")
    if (foreignKeyViolations.isNotEmpty()) {
      Log.w(TAG, "Foreign key violations!\n${foreignKeyViolations.joinToString(separator = "\n")}")
      throw IllegalStateException("Foreign key violations!")
    }
    stopwatch.split("fk-check")

    stopwatch.stop(TAG)
  }

  private fun findRemainingDuplicates(db: SQLiteDatabase): List<Duplicate> {
    return db.rawQuery(
      """
      WITH dupes AS (
        SELECT
          _id,
          date_sent,
          from_recipient_id,
          thread_id,
          type
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
        _id,
        date_sent,
        from_recipient_id,
        thread_id,
        type
      FROM
        message
      WHERE
        _id IN (SELECT _id FROM dupes)
      ORDER BY
        date_sent ASC,
        _id ASC
      """,
      null
    ).readToList { cursor ->
      Duplicate(
        id = cursor.requireLong("_id"),
        dateSent = cursor.requireLong("date_sent"),
        fromRecipientId = cursor.requireLong("from_recipient_id"),
        threadId = cursor.requireLong("thread_id"),
        type = cursor.requireLong("type")
      )
    }
  }

  /**
   * Fixes a single set of dupes that all have the same date_sent. The process for fixing them is as follows:
   *
   * Remember that all of the messages passed in have the same date_sent and thread_id.
   * What we want to do is shift messages back so that they can all have unique date_sent's within the thread.
   *
   * So if we had data like this:
   *
   * _id | date_sent
   * 98  | 1000
   * 99  | 1000
   * 100 | 1000
   *
   * We'd want to turn it into this:
   *
   * _id | date_sent
   * 98  | 998
   * 99  | 999
   * 100 | 1000
   *
   * However, we don't want to create new duplicates along the way, so we have to make sure the the date we move
   * to is actually free, and therefore have to do a little extra peeking and bookkeeping along the way.
   */
  private fun fixDuplicate(db: SQLiteDatabase, duplicates: List<Duplicate>) {
    var candidateDateSent = duplicates[0].dateSent - 1

    // Moving from highest-to-lowest _id (skipping the highest, since it can keep the original date_sent), we find the next
    // available date_sent in the table and move it there.
    duplicates
      .sortedByDescending { it.id }
      .drop(1)
      .forEach { duplicate ->
        while (isDateTaken(db, candidateDateSent, duplicate.fromRecipientId, duplicate.threadId)) {
          Log.d(TAG, "(date=$candidateDateSent, from=${duplicate.fromRecipientId}, thread=${duplicate.threadId} not available! Decrementing.")
          candidateDateSent--
        }

        db.execSQL(
          """
            UPDATE message
            SET date_sent = $candidateDateSent
            WHERE _id = ${duplicate.id}
          """
        )

        candidateDateSent--
      }
  }

  /** True if there already exists a message with the provided tuple, otherwise false. */
  private fun isDateTaken(db: SQLiteDatabase, dateSent: Long, fromRecipientId: Long, threadId: Long): Boolean {
    return db.rawQuery(
      """
      SELECT EXISTS (
        SELECT 1 
        FROM message INDEXED BY message_date_sent_from_to_thread_index 
        WHERE date_sent = ? AND from_recipient_id = ? AND thread_id = ?
      )
      """,
      dateSent,
      fromRecipientId,
      threadId
    ).readToSingleBoolean()
  }

  data class UniqueId(
    val dateSent: Long,
    val fromRecipientId: Long,
    val threadId: Long
  )

  data class Duplicate(
    val id: Long,
    val dateSent: Long,
    val fromRecipientId: Long,
    val threadId: Long,
    val type: Long
  )
}

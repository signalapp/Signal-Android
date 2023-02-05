package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.Stopwatch
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.toSingleLine
import org.signal.core.util.update

/**
 * When we ran [V166_ThreadAndMessageForeignKeys], we forgot to update the actual table definition in [ThreadTable].
 * We could make this conditional, but I'd rather run it on everyone just so it's more predictable.
 */
object V171_ThreadForeignKeyFix : SignalDatabaseMigration {

  private val TAG = Log.tag(V171_ThreadForeignKeyFix::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")

    removeDuplicateThreadEntries(db)
    stopwatch.split("thread-dupes")

    updateThreadTableSchema(db)
    stopwatch.split("thread-schema")

    stopwatch.stop(TAG)
  }

  private fun removeDuplicateThreadEntries(db: SQLiteDatabase) {
    db.rawQuery(
      """
      SELECT 
        recipient_id, 
        COUNT(*) AS thread_count 
      FROM thread 
      GROUP BY recipient_id HAVING thread_count > 1
    """.toSingleLine()
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val recipientId = cursor.requireLong("recipient_id")
        val count = cursor.requireLong("thread_count")
        Log.w(TAG, "There were $count threads for RecipientId::$recipientId. Merging.", true)

        val threads: List<ThreadInfo> = getThreadsByRecipientId(db, cursor.requireLong("recipient_id"))
        mergeThreads(db, threads)
      }
    }
  }

  private fun getThreadsByRecipientId(db: SQLiteDatabase, recipientId: Long): List<ThreadInfo> {
    return db.rawQuery("SELECT _id, date FROM thread WHERE recipient_id = ?".trimIndent(), recipientId).readToList { cursor ->
      ThreadInfo(cursor.requireLong("_id"), cursor.requireLong("date"))
    }
  }

  private fun mergeThreads(db: SQLiteDatabase, threads: List<ThreadInfo>) {
    val primaryThread: ThreadInfo = threads.maxByOrNull { it.date }!!
    val secondaryThreads: List<ThreadInfo> = threads.filterNot { it.id == primaryThread.id }

    secondaryThreads.forEach { secondaryThread ->
      remapThread(db, primaryThread.id, secondaryThread.id)
    }
  }

  private fun remapThread(db: SQLiteDatabase, primaryId: Long, secondaryId: Long) {
    db.update("drafts")
      .values("thread_id" to primaryId)
      .where("thread_id = ?", secondaryId)
      .run()

    db.update("mention")
      .values("thread_id" to primaryId)
      .where("thread_id = ?", secondaryId)
      .run()

    db.update("mms")
      .values("thread_id" to primaryId)
      .where("thread_id = ?", secondaryId)
      .run()

    db.update("pending_retry_receipts")
      .values("thread_id" to primaryId)
      .where("thread_id = ?", secondaryId)
      .run()

    // We're dealing with threads that exist, so we don't need to remap old_ids

    val count = db.update("remapped_threads")
      .values("new_id" to primaryId)
      .where("new_id = ?", secondaryId)
      .run()
    Log.w(TAG, "Remapped $count remapped_threads new_ids from $secondaryId to $primaryId", true)

    db.delete("thread")
      .where("_id = ?", secondaryId)
      .run()
  }

  private fun updateThreadTableSchema(db: SQLiteDatabase) {
    db.execSQL(
      """
        CREATE TABLE thread_tmp (
          _id INTEGER PRIMARY KEY AUTOINCREMENT, 
          date INTEGER DEFAULT 0, 
          meaningful_messages INTEGER DEFAULT 0,
          recipient_id INTEGER NOT NULL UNIQUE REFERENCES recipient (_id) ON DELETE CASCADE,
          read INTEGER DEFAULT 1, 
          type INTEGER DEFAULT 0, 
          error INTEGER DEFAULT 0,
          snippet TEXT, 
          snippet_type INTEGER DEFAULT 0, 
          snippet_uri TEXT DEFAULT NULL, 
          snippet_content_type TEXT DEFAULT NULL, 
          snippet_extras TEXT DEFAULT NULL, 
          unread_count INTEGER DEFAULT 0, 
          archived INTEGER DEFAULT 0, 
          status INTEGER DEFAULT 0, 
          delivery_receipt_count INTEGER DEFAULT 0, 
          read_receipt_count INTEGER DEFAULT 0, 
          expires_in INTEGER DEFAULT 0, 
          last_seen INTEGER DEFAULT 0, 
          has_sent INTEGER DEFAULT 0, 
          last_scrolled INTEGER DEFAULT 0, 
          pinned INTEGER DEFAULT 0, 
          unread_self_mention_count INTEGER DEFAULT 0
        )
      """.trimIndent()
    )

    db.execSQL(
      """
      INSERT INTO thread_tmp
        SELECT 
          _id,
          date,
          meaningful_messages,
          recipient_id,
          read,
          type,
          error,
          snippet,
          snippet_type,
          snippet_uri,
          snippet_content_type,
          snippet_extras,
          unread_count,
          archived,
          status,
          delivery_receipt_count,
          read_receipt_count,
          expires_in,
          last_seen,
          has_sent,
          last_scrolled,
          pinned,
          unread_self_mention_count
        FROM thread
      """.trimMargin()
    )

    db.execSQL("DROP TABLE thread")
    db.execSQL("ALTER TABLE thread_tmp RENAME TO thread")

    db.execSQL("CREATE INDEX thread_recipient_id_index ON thread (recipient_id)")
    db.execSQL("CREATE INDEX archived_count_index ON thread (archived, meaningful_messages)")
    db.execSQL("CREATE INDEX thread_pinned_index ON thread (pinned)")
    db.execSQL("CREATE INDEX thread_read ON thread (read)")
  }

  data class ThreadInfo(val id: Long, val date: Long)
}

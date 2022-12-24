package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.signal.core.util.toSingleLine
import org.signal.core.util.update

/**
 * This one's a doozy. We want to add additional foreign key constraints between the thread, recipient, and message tables. This will let us know for sure
 * that there aren't threads with invalid recipients, or messages with invalid threads, or multiple threads for the same recipient.
 */
object V166_ThreadAndMessageForeignKeys : SignalDatabaseMigration {

  private val TAG = Log.tag(V166_ThreadAndMessageForeignKeys::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Some crashes we were seeing indicated that we may have been running this migration twice on some unlucky devices, likely due
    // to some gaps that were left between some transactions during the upgrade path.
    if (!SqlUtil.columnExists(db, "thread", "thread_recipient_id")) {
      Log.w(TAG, "Migration must have already run! Skipping.", true)
      return
    }

    val stopwatch = Stopwatch("migration")

    removeDuplicateThreadEntries(db)
    stopwatch.split("thread-dupes")

    updateThreadTableSchema(db)
    stopwatch.split("thread-schema")

    fixDanglingSmsMessages(db)
    stopwatch.split("sms-dangling")

    fixDanglingMmsMessages(db)
    stopwatch.split("mms-dangling")

    updateSmsTableSchema(db)
    stopwatch.split("sms-schema")

    updateMmsTableSchema(db)
    stopwatch.split("mms-schema")

    stopwatch.stop(TAG)
  }

  private fun removeDuplicateThreadEntries(db: SQLiteDatabase) {
    db.rawQuery(
      """
      SELECT 
        thread_recipient_id, 
        COUNT(*) AS thread_count 
      FROM thread 
      GROUP BY thread_recipient_id HAVING thread_count > 1
    """.toSingleLine()
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val recipientId = cursor.requireLong("thread_recipient_id")
        val count = cursor.requireLong("thread_count")
        Log.w(TAG, "There were $count threads for RecipientId::$recipientId. Merging.", true)

        val threads: List<ThreadInfo> = getThreadsByRecipientId(db, cursor.requireLong("thread_recipient_id"))
        mergeThreads(db, threads)
      }
    }
  }

  private fun getThreadsByRecipientId(db: SQLiteDatabase, recipientId: Long): List<ThreadInfo> {
    return db.rawQuery("SELECT _id, date FROM thread WHERE thread_recipient_id = ?".trimIndent(), recipientId).readToList { cursor ->
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

    db.update("sms")
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
          message_count,
          thread_recipient_id,
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

  private fun fixDanglingSmsMessages(db: SQLiteDatabase) {
    db.delete("sms")
      .where("address IS NULL OR address NOT IN (SELECT _id FROM recipient)")
      .run()

    // Can't even attempt to "fix" these because without the threadId we don't know if it's a 1:1 or group message
    db.delete("sms")
      .where("thread_id IS NULL OR thread_id NOT IN (SELECT _id FROM thread)")
      .run()
  }

  private fun fixDanglingMmsMessages(db: SQLiteDatabase) {
    db.delete("mms")
      .where("address IS NULL OR address NOT IN (SELECT _id FROM recipient)")
      .run()

    // Can't even attempt to "fix" these because without the threadId we don't know if it's a 1:1 or group message
    db.delete("mms")
      .where("thread_id IS NULL OR thread_id NOT IN (SELECT _id FROM thread)")
      .run()
  }

  private fun updateSmsTableSchema(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE sms_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        date_sent INTEGER NOT NULL,
        date_received INTEGER NOT NULL,
        date_server INTEGER DEFAULT -1,
        thread_id INTEGER NOT NULL REFERENCES thread (_id) ON DELETE CASCADE,
        recipient_id NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        recipient_device_id INTEGER DEFAULT 1,
        type INTEGER,
        body TEXT,
        read INTEGER DEFAULT 0,
        status INTEGER DEFAULT -1,
        delivery_receipt_count INTEGER DEFAULT 0,
        mismatched_identities TEXT DEFAULT NULL,
        subscription_id INTEGER DEFAULT -1,
        expires_in INTEGER DEFAULT 0,
        expire_started INTEGER DEFAULT 0,
        notified INTEGER DEFAULT 0,
        read_receipt_count INTEGER DEFAULT 0,
        unidentified INTEGER DEFAULT 0,
        reactions_unread INTEGER DEFAULT 0,
        reactions_last_seen INTEGER DEFAULT -1,
        remote_deleted INTEGER DEFAULT 0,
        notified_timestamp INTEGER DEFAULT 0,
        server_guid TEXT DEFAULT NULL,
        receipt_timestamp INTEGER DEFAULT -1,
        export_state BLOB DEFAULT NULL,
        exported INTEGER DEFAULT 0
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      INSERT INTO sms_tmp
        SELECT
          _id,
          date_sent,
          date,
          date_server,
          thread_id,
          address,
          address_device_id,
          type,
          body,
          read,
          status,
          delivery_receipt_count,
          mismatched_identities,
          subscription_id,
          expires_in,
          expire_started,
          notified,
          read_receipt_count,
          unidentified,
          reactions_unread,
          reactions_last_seen,
          remote_deleted,
          notified_timestamp,
          server_guid,
          receipt_timestamp,
          export_state,
          exported
        FROM sms
      """.trimIndent()
    )

    db.execSQL("DROP TABLE sms")
    db.execSQL("ALTER TABLE sms_tmp RENAME TO sms")

    db.execSQL("CREATE INDEX sms_read_and_notified_and_thread_id_index ON sms(read, notified, thread_id)")
    db.execSQL("CREATE INDEX sms_type_index ON sms (type)")
    db.execSQL("CREATE INDEX sms_date_sent_index ON sms (date_sent, recipient_id, thread_id)")
    db.execSQL("CREATE INDEX sms_date_server_index ON sms (date_server)")
    db.execSQL("CREATE INDEX sms_thread_date_index ON sms (thread_id, date_received)")
    db.execSQL("CREATE INDEX sms_reactions_unread_index ON sms (reactions_unread)")
    db.execSQL("CREATE INDEX sms_exported_index ON sms (exported)")

    db.execSQL("CREATE TRIGGER sms_ai AFTER INSERT ON sms BEGIN INSERT INTO sms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END;")
    db.execSQL("CREATE TRIGGER sms_ad AFTER DELETE ON sms BEGIN INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); END;")
    db.execSQL("CREATE TRIGGER sms_au AFTER UPDATE ON sms BEGIN INSERT INTO sms_fts(sms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); INSERT INTO sms_fts(rowid, body, thread_id) VALUES(new._id, new.body, new.thread_id); END;")
    db.execSQL("CREATE TRIGGER msl_sms_delete AFTER DELETE ON sms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 0); END")
  }

  private fun updateMmsTableSchema(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE mms_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        date_sent INTEGER NOT NULL,
        date_received INTEGER NOT NULL, 
        date_server INTEGER DEFAULT -1,
        thread_id INTEGER NOT NULL REFERENCES thread (_id) ON DELETE CASCADE,
        recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        recipient_device_id INTEGER,
        type INTEGER NOT NULL,
        body TEXT,
        read INTEGER DEFAULT 0,
        ct_l TEXT,
        exp INTEGER,
        m_type INTEGER,
        m_size INTEGER,
        st INTEGER,
        tr_id TEXT,
        subscription_id INTEGER DEFAULT -1,
        receipt_timestamp INTEGER DEFAULT -1,
        delivery_receipt_count INTEGER DEFAULT 0,
        read_receipt_count INTEGER DEFAULT 0,
        viewed_receipt_count INTEGER DEFAULT 0,
        mismatched_identities TEXT DEFAULT NULL,
        network_failures TEXT DEFAULT NULL,
        expires_in INTEGER DEFAULT 0,
        expire_started INTEGER DEFAULT 0,
        notified INTEGER DEFAULT 0,
        quote_id INTEGER DEFAULT 0,
        quote_author INTEGER DEFAULT 0,
        quote_body TEXT DEFAULT NULL,
        quote_missing INTEGER DEFAULT 0,
        quote_mentions BLOB DEFAULT NULL,
        quote_type INTEGER DEFAULT 0,
        shared_contacts TEXT DEFAULT NULL,
        unidentified INTEGER DEFAULT 0,
        link_previews TEXT DEFAULT NULL,
        view_once INTEGER DEFAULT 0,
        reactions_unread INTEGER DEFAULT 0,
        reactions_last_seen INTEGER DEFAULT -1,
        remote_deleted INTEGER DEFAULT 0,
        mentions_self INTEGER DEFAULT 0,
        notified_timestamp INTEGER DEFAULT 0, 
        server_guid TEXT DEFAULT NULL,
        message_ranges BLOB DEFAULT NULL, 
        story_type INTEGER DEFAULT 0,
        parent_story_id INTEGER DEFAULT 0,
        export_state BLOB DEFAULT NULL,
        exported INTEGER DEFAULT 0
      )
      """.trimIndent()
    )

    db.execSQL(
      """
      INSERT INTO mms_tmp
        SELECT
          _id,
          date,
          date_received,
          date_server,
          thread_id,
          address,
          address_device_id,
          msg_box,
          body,
          read,
          ct_l,
          exp,
          m_type,
          m_size,
          st,
          tr_id,
          subscription_id,
          receipt_timestamp,
          delivery_receipt_count,
          read_receipt_count,
          viewed_receipt_count,
          mismatched_identities,
          network_failures,
          expires_in,
          expire_started,
          notified,
          quote_id,
          quote_author,
          quote_body,
          quote_missing,
          quote_mentions,
          quote_type,
          shared_contacts,
          unidentified,
          previews,
          reveal_duration,
          reactions_unread,
          reactions_last_seen,
          remote_deleted,
          mentions_self,
          notified_timestamp,
          server_guid,
          ranges,
          is_story,
          parent_story_id,
          export_state,
          exported
        FROM mms
      """.trimIndent()
    )

    db.execSQL("DROP TABLE mms")
    db.execSQL("ALTER TABLE mms_tmp RENAME TO mms")

    db.execSQL("CREATE INDEX mms_read_and_notified_and_thread_id_index ON mms(read, notified, thread_id)")
    db.execSQL("CREATE INDEX mms_type_index ON mms (type)")
    db.execSQL("CREATE INDEX mms_date_sent_index ON mms (date_sent, recipient_id, thread_id)")
    db.execSQL("CREATE INDEX mms_date_server_index ON mms (date_server)")
    db.execSQL("CREATE INDEX mms_thread_date_index ON mms (thread_id, date_received)")
    db.execSQL("CREATE INDEX mms_reactions_unread_index ON mms (reactions_unread)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mms_story_type_index ON mms (story_type)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mms_parent_story_id_index ON mms (parent_story_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mms_thread_story_parent_story_index ON mms (thread_id, date_received, story_type, parent_story_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mms_quote_id_quote_author_index ON mms (quote_id, quote_author)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mms_exported_index ON mms (exported)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mms_id_type_payment_transactions_index ON mms (_id, type) WHERE type & ${0x300000000L} != 0")

    db.execSQL("CREATE TRIGGER mms_ai AFTER INSERT ON mms BEGIN INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END")
    db.execSQL("CREATE TRIGGER mms_ad AFTER DELETE ON mms BEGIN INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); END")
    db.execSQL("CREATE TRIGGER mms_au AFTER UPDATE ON mms BEGIN INSERT INTO mms_fts(mms_fts, rowid, body, thread_id) VALUES('delete', old._id, old.body, old.thread_id); INSERT INTO mms_fts(rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id); END")
    db.execSQL("CREATE TRIGGER msl_mms_delete AFTER DELETE ON mms BEGIN DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE message_id = old._id AND is_mms = 1); END")
  }

  data class ThreadInfo(val id: Long, val date: Long)
}

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore

@Suppress("ClassName")
object V168_SingleMessageTableMigration : SignalDatabaseMigration {
  private val TAG = Log.tag(V168_SingleMessageTableMigration::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")

    val nextMmsId = SqlUtil.getNextAutoIncrementId(db, "mms")
    stopwatch.split("next-id")

    db.execSQL("DROP TRIGGER msl_sms_delete")
    db.execSQL("DROP TRIGGER reactions_sms_delete")
    db.execSQL("DROP TRIGGER sms_ai")
    db.execSQL("DROP TRIGGER sms_au")
    db.execSQL("DROP TRIGGER sms_ad")
    db.execSQL("DROP TABLE sms_fts") // Will drop all other related fts tables
    stopwatch.split("drop-triggers")

    // It's actually much faster to drop the indexes, copy the data, then recreate the indexes in bulk than it is to keep them and index-as-you-insert.
    // Like, at least twice as fast.
    db.execSQL("DROP INDEX mms_read_and_notified_and_thread_id_index")
    db.execSQL("DROP INDEX mms_type_index")
    db.execSQL("DROP INDEX mms_date_sent_index")
    db.execSQL("DROP INDEX mms_date_server_index")
    db.execSQL("DROP INDEX mms_thread_date_index")
    db.execSQL("DROP INDEX mms_reactions_unread_index")
    db.execSQL("DROP INDEX mms_story_type_index")
    db.execSQL("DROP INDEX mms_parent_story_id_index")
    db.execSQL("DROP INDEX mms_thread_story_parent_story_index")
    db.execSQL("DROP INDEX mms_quote_id_quote_author_index")
    db.execSQL("DROP INDEX mms_exported_index")
    db.execSQL("DROP INDEX mms_id_type_payment_transactions_index")
    db.execSQL("DROP TRIGGER mms_ai") // Note: For perf reasons, we won't actually rebuild the index here -- we'll rebuild it asynchronously in a job
    stopwatch.split("drop-mms-indexes")

    copySmsToMms(db, nextMmsId)
    stopwatch.split("copy-sms")

    db.execSQL("DROP TABLE sms")
    stopwatch.split("drop-sms")

    db.execSQL("CREATE INDEX mms_read_and_notified_and_thread_id_index ON mms(read, notified, thread_id)")
    db.execSQL("CREATE INDEX mms_type_index ON mms (type)")
    db.execSQL("CREATE INDEX mms_date_sent_index ON mms (date_sent, recipient_id, thread_id)")
    db.execSQL("CREATE INDEX mms_date_server_index ON mms (date_server)")
    db.execSQL("CREATE INDEX mms_thread_date_index ON mms (thread_id, date_received)")
    db.execSQL("CREATE INDEX mms_reactions_unread_index ON mms (reactions_unread)")
    db.execSQL("CREATE INDEX mms_story_type_index ON mms (story_type)")
    db.execSQL("CREATE INDEX mms_parent_story_id_index ON mms (parent_story_id)")
    db.execSQL("CREATE INDEX mms_thread_story_parent_story_index ON mms (thread_id, date_received, story_type, parent_story_id)")
    db.execSQL("CREATE INDEX mms_quote_id_quote_author_index ON mms (quote_id, quote_author)")
    db.execSQL("CREATE INDEX mms_exported_index ON mms (exported)")
    db.execSQL("CREATE INDEX mms_id_type_payment_transactions_index ON mms (_id, type) WHERE type & ${0x300000000L} != 0")
    db.execSQL(
      """
        CREATE TRIGGER mms_ai AFTER INSERT ON mms BEGIN
          INSERT INTO mms_fts (rowid, body, thread_id) VALUES (new._id, new.body, new.thread_id);
        END;
      """
    )
    stopwatch.split("rebuild-indexes")

    db.execSQL(
      """
      UPDATE reaction
      SET message_id = message_id + $nextMmsId
      WHERE is_mms = 0
    """
    )
    stopwatch.split("update-reactions")

    db.execSQL(
      """
      UPDATE msl_message
      SET message_id = message_id + $nextMmsId
      WHERE is_mms = 0
    """
    )
    stopwatch.split("update-msl")

    stopwatch.stop(TAG)

    SignalStore.plaintext.smsMigrationIdOffset = nextMmsId
  }

  private fun copySmsToMms(db: SQLiteDatabase, idOffset: Long) {
    val batchSize = 50_000L

    val maxId = SqlUtil.getNextAutoIncrementId(db, "sms")

    for (i in 1..maxId step batchSize) {
      db.execSQL(
        """
        INSERT INTO mms
          SELECT
            _id + $idOffset,
            date_sent,
            date_received,
            date_server,
            thread_id,
            recipient_id,
            recipient_device_id,
            type,
            body,
            read,
            null,
            0,
            0,
            0,
            status,
            null,
            subscription_id,
            receipt_timestamp,
            delivery_receipt_count,
            read_receipt_count,
            0,
            mismatched_identities,
            null,
            expires_in,
            expire_started,
            notified,
            0,
            0,
            null,
            0,
            null,
            0,
            null,
            unidentified,
            null,
            0,
            reactions_unread,
            reactions_last_seen,
            remote_deleted,
            0,
            notified_timestamp,
            server_guid,
            null,
            0,
            0,
            export_state,
            exported
          FROM 
            sms
          WHERE
            _id >= $i AND
            _id < ${i + batchSize}
      """
      )
    }
  }
}

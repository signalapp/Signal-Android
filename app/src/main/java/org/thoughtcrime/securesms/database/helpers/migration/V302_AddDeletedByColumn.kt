package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds column to messages to track who has deleted a given message. We rebuild the table
 * manually instead of using ALTER TABLE to drop the column, which previously caused OOM crashes.
 */
@Suppress("ClassName")
object V302_AddDeletedByColumn : SignalDatabaseMigration {

  private val TAG = Log.tag(V302_AddDeletedByColumn::class)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (SqlUtil.columnExists(db, "message", "deleted_by")) {
      Log.i(TAG, "Already ran migration!")
      return
    }

    val stopwatch = Stopwatch("migration", decimalPlaces = 2)

    val dependentItems: List<SqlItem> = getAllDependentItems(db, "message")
    dependentItems.forEach { item ->
      val sql = "DROP ${item.type} IF EXISTS ${item.name}"
      Log.d(TAG, "Executing: $sql")
      db.execSQL(sql)
    }
    stopwatch.split("drop-dependents")

    db.execSQL(
      """
      CREATE TABLE message_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT, 
        date_sent INTEGER NOT NULL, 
        date_received INTEGER NOT NULL,
        date_server INTEGER DEFAULT -1,
        thread_id INTEGER NOT NULL REFERENCES thread (_id) ON DELETE CASCADE,
        from_recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
        from_device_id INTEGER,
        to_recipient_id INTEGER NOT NULL REFERENCES recipient (_id) ON DELETE CASCADE,
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
        has_delivery_receipt INTEGER DEFAULT 0,
        has_read_receipt INTEGER DEFAULT 0,
        viewed INTEGER DEFAULT 0,
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
        mentions_self INTEGER DEFAULT 0,
        notified_timestamp INTEGER DEFAULT 0,
        server_guid TEXT DEFAULT NULL,
        message_ranges BLOB DEFAULT NULL,
        story_type INTEGER DEFAULT 0,
        parent_story_id INTEGER DEFAULT 0,
        export_state BLOB DEFAULT NULL,
        exported INTEGER DEFAULT 0,
        scheduled_date INTEGER DEFAULT -1,
        latest_revision_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE CASCADE,
        original_message_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE CASCADE,
        revision_number INTEGER DEFAULT 0,
        message_extras BLOB DEFAULT NULL,
        expire_timer_version INTEGER DEFAULT 1 NOT NULL,
        votes_unread INTEGER DEFAULT 0,
        votes_last_seen INTEGER DEFAULT 0,
        pinned_until INTEGER DEFAULT 0,
        pinning_message_id INTEGER DEFAULT 0,
        pinned_at INTEGER DEFAULT 0,
        deleted_by INTEGER DEFAULT NULL REFERENCES recipient (_id) ON DELETE CASCADE
      )
      """
    )
    stopwatch.split("create-table")

    db.execSQL(
      """
      INSERT INTO message_tmp
        SELECT
          _id,
          date_sent,
          date_received,
          date_server,
          thread_id,
          from_recipient_id,
          from_device_id,
          to_recipient_id,
          type,
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
          has_delivery_receipt,
          has_read_receipt,
          viewed,
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
          link_previews,
          view_once,
          reactions_unread,
          reactions_last_seen,
          mentions_self,
          notified_timestamp,
          server_guid,
          message_ranges,
          story_type,
          parent_story_id,
          export_state,
          exported,
          scheduled_date,
          latest_revision_id,
          original_message_id,
          revision_number,
          message_extras,
          expire_timer_version,
          votes_unread,
          votes_last_seen,
          pinned_until,
          pinning_message_id,
          pinned_at,
          CASE WHEN remote_deleted > 0 THEN from_recipient_id ELSE NULL END AS deleted_by
        FROM
          message
      """
    )
    stopwatch.split("copy-data")

    db.execSQL("DROP TABLE message")
    stopwatch.split("drop-old-table")

    db.execSQL("ALTER TABLE message_tmp RENAME TO message")
    stopwatch.split("rename-table")

    dependentItems.forEach { item ->
      val sql = item.createStatement
      Log.d(TAG, "Executing: $sql")
      db.execSQL(sql)
    }
    stopwatch.split("recreate-dependents")

    db.execSQL("CREATE INDEX IF NOT EXISTS message_deleted_by_index ON message (deleted_by)")
    stopwatch.split("create-index")

    val foreignKeyViolations: List<SqlUtil.ForeignKeyViolation> = SqlUtil.getForeignKeyViolations(db, "message")
    if (foreignKeyViolations.isNotEmpty()) {
      Log.w(TAG, "Foreign key violations!\n${foreignKeyViolations.joinToString(separator = "\n")}")
      throw IllegalStateException("Foreign key violations!")
    }
    stopwatch.split("fk-check")

    stopwatch.stop(TAG)
  }

  private fun getAllDependentItems(db: SQLiteDatabase, tableName: String): List<SqlItem> {
    return db.rawQuery("SELECT type, name, sql FROM sqlite_schema WHERE tbl_name='$tableName' AND type != 'table'").readToList { cursor ->
      SqlItem(
        type = cursor.requireNonNullString("type"),
        name = cursor.requireNonNullString("name"),
        createStatement = cursor.requireNonNullString("sql")
      )
    }
  }

  data class SqlItem(
    val type: String,
    val name: String,
    val createStatement: String
  )
}

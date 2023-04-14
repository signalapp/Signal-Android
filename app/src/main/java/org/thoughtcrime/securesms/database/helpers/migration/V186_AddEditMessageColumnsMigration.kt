package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString

/**
 * Changes needed for edit message. New foreign keys require recreating the table.
 */
@Suppress("ClassName")
object V186_AddEditMessageColumnsMigration : SignalDatabaseMigration {

  private val TAG = Log.tag(V186_AddEditMessageColumnsMigration::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")

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
          exported INTEGER DEFAULT 0,
          scheduled_date INTEGER DEFAULT -1,
          latest_revision_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE CASCADE,
          original_message_id INTEGER DEFAULT NULL REFERENCES message (_id) ON DELETE CASCADE,
          revision_number INTEGER DEFAULT 0
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
            link_previews,
            view_once,
            reactions_unread,
            reactions_last_seen,
            remote_deleted,
            mentions_self,
            notified_timestamp,
            server_guid,
            message_ranges,
            story_type,
            parent_story_id,
            export_state,
            exported,
            scheduled_date,
            NULL AS latest_revision_id,
            NULL AS original_message_id,
            0 as revision_number
          FROM message
      """
    )
    stopwatch.split("copy-data")

    db.execSQL("DROP TABLE message")
    stopwatch.split("drop-old")

    db.execSQL("ALTER TABLE message_tmp RENAME TO message")
    stopwatch.split("rename-table")

    dependentItems.forEach { item ->
      val sql = when (item.name) {
        "message_thread_story_parent_story_scheduled_date_index" -> "CREATE INDEX message_thread_story_parent_story_scheduled_date_latest_revision_id_index ON message (thread_id, date_received, story_type, parent_story_id, scheduled_date, latest_revision_id)"
        "message_quote_id_quote_author_scheduled_date_index" -> "CREATE INDEX message_quote_id_quote_author_scheduled_date_latest_revision_id_index ON message (quote_id, quote_author, scheduled_date, latest_revision_id)"
        else -> item.createStatement
      }
      Log.d(TAG, "Executing: $sql")
      db.execSQL(sql)
    }
    stopwatch.split("recreate-dependents")

    db.execSQL("PRAGMA foreign_key_check")
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

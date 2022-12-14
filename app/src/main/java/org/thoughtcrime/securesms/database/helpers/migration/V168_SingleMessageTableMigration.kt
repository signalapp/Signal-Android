package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.signal.core.util.SqlUtil

object V168_SingleMessageTableMigration : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val nextMmsId = SqlUtil.getNextAutoIncrementId(db, "mms")

    db.execSQL("DROP TRIGGER msl_sms_delete")
    db.execSQL("DROP TRIGGER reactions_sms_delete")
    db.execSQL("DROP TABLE sms_fts") // Will drop all other related fts tables

    db.execSQL(
      """
      INSERT INTO mms
        SELECT
          _id + $nextMmsId,
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
        FROM sms
    """
    )

    db.execSQL("DROP TABLE sms")

    db.execSQL(
      """
      UPDATE reaction
      SET message_id = message_id + $nextMmsId
      WHERE is_mms = 0
    """
    )

    db.execSQL(
      """
      UPDATE msl_message
      SET message_id = message_id + $nextMmsId
      WHERE is_mms = 0
    """
    )

    // TODO search index?
    // TODO jobs?
  }
}

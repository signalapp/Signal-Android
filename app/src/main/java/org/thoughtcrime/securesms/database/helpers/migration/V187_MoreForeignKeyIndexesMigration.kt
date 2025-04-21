package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * I found some other tables that didn't have the proper indexes setup to correspond with their foreign keys.
 */
@Suppress("ClassName")
object V187_MoreForeignKeyIndexesMigration : SignalDatabaseMigration {

  private val TAG = Log.tag(V187_MoreForeignKeyIndexesMigration::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration")

    db.execSQL("CREATE INDEX IF NOT EXISTS call_call_link_index ON call (call_link)")
    stopwatch.split("call_link")

    db.execSQL("CREATE INDEX IF NOT EXISTS call_peer_index ON call (peer)")
    stopwatch.split("call_peer")

    db.execSQL("CREATE INDEX IF NOT EXISTS distribution_list_member_recipient_id ON distribution_list_member (recipient_id)")
    stopwatch.split("dlist_member")

    db.execSQL("CREATE INDEX IF NOT EXISTS msl_message_payload_index ON msl_message (payload_id)")
    stopwatch.split("msl_payload")

    stopwatch.stop(TAG)
  }
}

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Expand quote index to included scheduled date so they can be excluded.
 */
@Suppress("ClassName")
object V176_AddScheduledDateToQuoteIndex : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP INDEX IF EXISTS mms_quote_id_quote_author_index")
    db.execSQL("CREATE INDEX IF NOT EXISTS message_quote_id_quote_author_scheduled_date_index ON message (quote_id, quote_author, scheduled_date);")
  }
}

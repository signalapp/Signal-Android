package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We added some foreign keys to the message table (particularly on original_message_id and latest_revision_id)
 * that depend on the message table itself. But there were no indices to look up messages by those fields.
 * So every time we deleted a message, SQLite had to do a linear scan of the message table to verify that no
 * original_message_id or latest_revision_id fields referenced the deleted message._id.
 *
 * And that is very slow. Like, 40 seconds to delete 100 messages slow.
 *
 * Thankfully, the solution is simple: add indices on those columns.
 *
 * While I was at it, I looked at other columns that would need indices as well.
 */
@Suppress("ClassName")
object V186_ForeignKeyIndicesMigration : SignalDatabaseMigration {

  private val TAG = Log.tag(V186_ForeignKeyIndicesMigration::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // This was added after a bug was found in V185 that resulted in it not being run for users who restored from backup.
    // In that case, this column would be missing, and the migration would fail. This is cleaned up in V188.
    if (!columnExists(db, "message", "from_recipient_id")) {
      Log.w(TAG, "V185 wasn't run successfully! Skipping the migration for now. It'll run in V188.")
      return
    }

    val stopwatch = Stopwatch("migration")

    db.execSQL("CREATE INDEX IF NOT EXISTS message_original_message_id_index ON message (original_message_id)")
    stopwatch.split("original_message_id")

    db.execSQL("CREATE INDEX IF NOT EXISTS message_latest_revision_id_index ON message (latest_revision_id)")
    stopwatch.split("latest_revision_id")

    db.execSQL("CREATE INDEX IF NOT EXISTS message_from_recipient_id_index ON message (from_recipient_id)")
    stopwatch.split("from_recipient_id")

    db.execSQL("CREATE INDEX IF NOT EXISTS message_to_recipient_id_index ON message (to_recipient_id)")
    stopwatch.split("to_recipient_id")

    db.execSQL("CREATE INDEX IF NOT EXISTS reaction_author_id_index ON reaction (author_id)")
    stopwatch.split("reaction_author")

    // Previous migration screwed up an index replacement, so we need to fix that too
    db.execSQL("DROP INDEX IF EXISTS message_quote_id_quote_author_scheduled_date_index")
    db.execSQL("CREATE INDEX IF NOT EXISTS message_quote_id_quote_author_scheduled_date_latest_revision_id_index ON message (quote_id, quote_author, scheduled_date, latest_revision_id)")
    stopwatch.split("message_fix")

    // The recipient_id indices could be considered "low quality" indices, since they have a smaller domain.
    // Running analyze will help SQLite choose the right index to use in the future.
    db.execSQL("ANALYZE message")
    stopwatch.split("analyze")

    stopwatch.stop(TAG)
  }

  private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
    return db.query("PRAGMA table_info($table)", arrayOf())
      .readToList { it.requireNonNullString("name") }
      .any { it == column }
  }
}

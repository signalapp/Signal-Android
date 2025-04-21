package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.logging.Log
import org.signal.core.util.readToSingleLong
import org.signal.core.util.select
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Previously, we weren't properly remapping quote authors when recipients were remapped. This repairs those scenarios the best we can.
 */
@Suppress("ClassName")
object V260_RemapQuoteAuthors : SignalDatabaseMigration {

  private val TAG = Log.tag(V260_RemapQuoteAuthors::class)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // The following queries are really expensive without an index. So we create a temporary one.
    db.execSQL("CREATE INDEX tmp_quote_author ON message (quote_author)")

    // Even with an index, the updates can be a little expensive, so we try to figure out if we need them at all by using a quick check.
    val invalidQuoteCount = db
      .select("count(*)")
      .from("message INDEXED BY tmp_quote_author")
      .where("quote_author != 0 AND quote_author NOT IN (SELECT _id FROM recipient)")
      .run()
      .readToSingleLong()

    if (invalidQuoteCount == 0L) {
      Log.i(TAG, "No invalid quote authors, can skip migration.")
      db.execSQL("DROP INDEX tmp_quote_author")
      return
    }

    // Remap all quote_authors using a remapped recipient
    db.execSQL(
      """
        UPDATE 
          message INDEXED BY tmp_quote_author
        SET 
          quote_author = (SELECT new_id FROM remapped_recipients WHERE old_id = message.quote_author)
        WHERE 
          quote_author IN (SELECT old_id FROM remapped_recipients)
      """
    )

    // If there are any remaining quote_authors that don't reference a real recipient, we have no choice but to clear the quote
    db.execSQL(
      """
        UPDATE 
          message INDEXED BY tmp_quote_author
        SET 
          quote_id = 0,
          quote_author = 0,
          quote_body = null,
          quote_missing = 0,
          quote_mentions = null,
          quote_type = 0
        WHERE
          quote_author != 0 AND quote_author NOT IN (SELECT _id FROM recipient)
      """
    )

    db.execSQL("DROP INDEX tmp_quote_author")
  }
}

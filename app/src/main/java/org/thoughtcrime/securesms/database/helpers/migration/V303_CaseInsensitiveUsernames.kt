package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Enforces case-insensitive uniqueness on the username column in the recipient table.
 * Cleans up any existing case-insensitive duplicates before creating the index.
 */
@Suppress("ClassName")
object V303_CaseInsensitiveUsernames : SignalDatabaseMigration {

  private val TAG = Log.tag(V303_CaseInsensitiveUsernames::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration", decimalPlaces = 2)

    // Clear the username if it doesn't have the highest _id of recipient rows with the same case-insensitive username
    db.execSQL(
      """
      UPDATE recipient
      SET username = NULL
      WHERE username IS NOT NULL
        AND _id NOT IN (
          SELECT MAX(_id)
          FROM recipient
          WHERE username IS NOT NULL
          GROUP BY LOWER(username)
        )
      """.trimIndent()
    )
    stopwatch.split("dedupe")

    db.execSQL("CREATE UNIQUE INDEX recipient_username_unique_nocase ON recipient(username COLLATE NOCASE)")
    stopwatch.split("create-index")

    stopwatch.stop(TAG)
  }
}

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * We want to add a new `rank` column to the emoji_search table, and we no longer use it as an FTS
 * table, so we can get rid of that too.
 */
object V169_EmojiSearchIndexRank : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      CREATE TABLE emoji_search_tmp (
        _id INTEGER PRIMARY KEY,
        label TEXT NOT NULL,
        emoji TEXT NOT NULL,
        rank INTEGER DEFAULT ${Int.MAX_VALUE}
      )
      """
    )
    db.execSQL("INSERT INTO emoji_search_tmp (label, emoji) SELECT label, emoji from emoji_search")
    db.execSQL("DROP TABLE emoji_search")
    db.execSQL("ALTER TABLE emoji_search_tmp RENAME TO emoji_search")
    db.execSQL("CREATE INDEX emoji_search_rank_covering ON emoji_search (rank, label, emoji)")
  }
}

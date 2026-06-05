package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

@Suppress("ClassName")
object V315_CleanupE164SenderKeyShared : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DELETE FROM sender_key_shared WHERE address LIKE '+%'")
  }
}

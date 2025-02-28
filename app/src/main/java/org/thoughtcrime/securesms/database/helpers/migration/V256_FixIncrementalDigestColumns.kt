package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Fixes a bug where sometimes incremental chunk size would be set when the attachment was not actually incremental
 * Clears out any cases where only one of the incremental_digest / incremental_size fields were previously set
 */
@Suppress("ClassName")
object V256_FixIncrementalDigestColumns : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      UPDATE attachment
      SET 
        remote_incremental_digest = NULL,
        remote_incremental_digest_chunk_size = 0 
      WHERE
        remote_incremental_digest IS NULL OR
        LENGTH(remote_incremental_digest) = 0 OR
        remote_incremental_digest_chunk_size = 0
      """
    )
  }
}

/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.SqlUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.SQLiteDatabase

object V298_DoNotBackupReleaseNotes : SignalDatabaseMigration {

  private val TAG = Log.tag(V298_DoNotBackupReleaseNotes::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val releaseNoteRecipientId = getReleaseNoteRecipientId(context) ?: return
    migrateWithRecipientId(db, releaseNoteRecipientId)
  }

  fun migrateWithRecipientId(db: SQLiteDatabase, releaseNoteRecipientId: Long) {
    db.execSQL(
      """
      UPDATE attachment
      SET archive_transfer_state = 0
      WHERE message_id IN (
        SELECT _id FROM message WHERE from_recipient_id = $releaseNoteRecipientId
      )
      """
    )
  }

  private fun getReleaseNoteRecipientId(context: Application): Long? {
    return if (KeyValueDatabase.exists(context)) {
      val keyValueDatabase = KeyValueDatabase.getInstance(context).readableDatabase
      keyValueDatabase.query("key_value", arrayOf("value"), "key = ?", SqlUtil.buildArgs("releasechannel.recipient_id"), null, null, null).use { cursor ->
        if (cursor.moveToFirst()) {
          cursor.requireLong("value")
        } else {
          Log.w(TAG, "Release note channel recipient ID not found in KV database!")
          null
        }
      }
    } else {
      Log.w(TAG, "Pre-KV database, not doing anything.")
      null
    }
  }
}

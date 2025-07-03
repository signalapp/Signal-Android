/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.core.content.contentValuesOf
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullBlob
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * For all of time, we used the revision of -1 to indicate a placeholder group (i.e., pending invite approval). With
 * backups we want to be able to export those groups which require a non-negative revision. Migrates groups with a
 * revision of -1 to a group dummy revision of 0 but with the placeholder group state flag set.
 */
object V284_SetPlaceholderGroupFlag : SignalDatabaseMigration {
  private val TAG = Log.tag(V284_SetPlaceholderGroupFlag::class)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val updates = mutableListOf<Pair<Long, ByteArray>>()

    db.query("groups", arrayOf("_id", "decrypted_group"), "revision = -1 AND decrypted_group IS NOT NULL", null, null, null, null).use { cursor ->
      while (cursor.moveToNext()) {
        val decryptedGroup = try {
          DecryptedGroup.ADAPTER.decode(cursor.requireNonNullBlob("decrypted_group"))
        } catch (e: Exception) {
          Log.w(TAG, "Unable to parse group state", e)
          continue
        }

        updates += cursor.requireLong("_id") to decryptedGroup.newBuilder().revision(0).isPlaceholderGroup(true).build().encode()
      }
    }

    updates.forEach { (id, groupState) ->
      val values = contentValuesOf("decrypted_group" to groupState, "revision" to 0)
      db.update("groups", values, "_id = $id", null)
    }
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Copies the storage id off of every gv1 recipient row into the unknown id table so the ids stay in the storage
 * service manifest, then clears the storage columns on those rows.
 *
 * A gv1 row can still be given a storage id after this runs, it is just never read, so this is not a lasting invariant.
 */
@Suppress("ClassName")
object V324_MoveGroupV1StorageIdsToUnknownIds : SignalDatabaseMigration {

  private const val RECIPIENT_TYPE_GV1 = 2
  private const val MANIFEST_TYPE_GROUPV1 = 2

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL(
      """
      INSERT OR IGNORE INTO storage_key (type, key)
      SELECT $MANIFEST_TYPE_GROUPV1, storage_service_id FROM recipient WHERE type = $RECIPIENT_TYPE_GV1 AND storage_service_id NOT NULL
      """
    )

    db.execSQL("UPDATE recipient SET storage_service_id = NULL, storage_service_proto = NULL WHERE type = $RECIPIENT_TYPE_GV1")
  }
}

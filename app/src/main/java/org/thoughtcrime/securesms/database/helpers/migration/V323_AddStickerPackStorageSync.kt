/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.readToList
import org.signal.core.util.requireLong
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Adds the columns needed to sync sticker packs via storage service: a remote position,
 * a storage id, a proto for unknown fields, and a deletion timestamp for tombstones.
 *
 * The remote `position` replaces the local `pack_order` as the canonical ordering field.
 * Packs render in ascending position order, so installed packs get their index in the old
 * display order (`pack_order` ASC, NULLs first), and pack_order is dropped.
 */
@Suppress("ClassName")
object V323_AddStickerPackStorageSync : SignalDatabaseMigration {

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("ALTER TABLE sticker_pack ADD COLUMN position INTEGER DEFAULT 0")
    db.execSQL("ALTER TABLE sticker_pack ADD COLUMN storage_service_id TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE sticker_pack ADD COLUMN storage_service_proto TEXT DEFAULT NULL")
    db.execSQL("ALTER TABLE sticker_pack ADD COLUMN deleted_timestamp_ms INTEGER DEFAULT 0")

    val installedIds = db.rawQuery("SELECT _id FROM sticker_pack WHERE installed = 1 ORDER BY pack_order ASC")
      .readToList { it.requireLong("_id") }

    installedIds.forEachIndexed { index, id ->
      db.execSQL("UPDATE sticker_pack SET position = $index WHERE _id = $id")
    }

    db.execSQL("ALTER TABLE sticker_pack DROP COLUMN pack_order")
  }
}

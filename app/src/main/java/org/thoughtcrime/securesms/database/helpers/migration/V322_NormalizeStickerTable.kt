/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * Splits the denormalized sticker table into two tables. One has pack-level details, and the other has all the packs.
 * This lets us reduce the duplication of things like pack_id/pack_key.
 */
object V322_NormalizeStickerTable : SignalDatabaseMigration {
  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Create pack table
    db.execSQL(
      """
      CREATE TABLE sticker_pack (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        pack_id TEXT NOT NULL UNIQUE,
        pack_key TEXT NOT NULL,
        pack_title TEXT NOT NULL,
        pack_author TEXT NOT NULL,
        pack_order INTEGER,
        installed INTEGER
      )
      """
    )

    // Use the cover stickers as the definitive row for the pack data
    db.execSQL(
      """
      INSERT INTO sticker_pack (pack_id, pack_key, pack_title, pack_author, pack_order, installed)
      SELECT pack_id, pack_key, pack_title, pack_author, pack_order, installed
      FROM sticker
      WHERE cover = 1
      GROUP BY pack_id
      """
    )

    // If for some reason there's a pack with no cover row, grab a representative row for that pack and create an entry for it
    db.execSQL(
      """
      INSERT INTO sticker_pack (pack_id, pack_key, pack_title, pack_author, pack_order, installed)
      SELECT pack_id, pack_key, pack_title, pack_author, pack_order, installed
      FROM sticker
      WHERE pack_id NOT IN (SELECT pack_id FROM sticker_pack)
      GROUP BY pack_id
      """
    )

    // Create the sticker table, which largely looks like the old sticker table, but with a fk reference to the pack table
    db.execSQL(
      """
      CREATE TABLE sticker_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        pack_id TEXT NOT NULL REFERENCES sticker_pack (pack_id) ON DELETE CASCADE,
        sticker_id INTEGER,
        cover INTEGER,
        emoji TEXT NOT NULL,
        content_type TEXT DEFAULT NULL,
        last_used INTEGER,
        file_path TEXT NOT NULL,
        file_length INTEGER,
        file_random BLOB,
        UNIQUE(pack_id, sticker_id, cover) ON CONFLICT IGNORE
      )
      """
    )

    // Copy over the data into the sticker table
    db.execSQL(
      """
      INSERT INTO sticker_tmp (_id, pack_id, sticker_id, cover, emoji, content_type, last_used, file_path, file_length, file_random)
      SELECT _id, pack_id, sticker_id, cover, emoji, content_type, last_used, file_path, file_length, file_random
      FROM sticker
      """
    )

    // Cleanup and rebuild indicates
    db.execSQL("DROP TABLE sticker")
    db.execSQL("ALTER TABLE sticker_tmp RENAME TO sticker")

    db.execSQL("CREATE INDEX IF NOT EXISTS sticker_pack_id_index ON sticker (pack_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS sticker_sticker_id_index ON sticker (sticker_id)")
  }
}

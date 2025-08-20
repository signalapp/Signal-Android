/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

/**
 * We want to be able to distinguish between an unset CDN (null) and CDN 0. But we default the current CDN values to zero.
 * This migration updates things so that the CDN columns default to null. We also consider all current CDN 0's to actually be unset values.
 */
object V276_AttachmentCdnDefaultValueMigration : SignalDatabaseMigration {

  private val TAG = Log.tag(V276_AttachmentCdnDefaultValueMigration::class)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("v276")

    db.execSQL("UPDATE attachment SET archive_cdn = NULL WHERE archive_cdn = 0")
    stopwatch.split("fix-old-data")

    db.execSQL(
      """
      CREATE TABLE attachment_tmp (
        _id INTEGER PRIMARY KEY AUTOINCREMENT,
        message_id INTEGER,
        content_type TEXT,
        remote_key TEXT,
        remote_location TEXT,
        remote_digest BLOB,
        remote_incremental_digest BLOB,
        remote_incremental_digest_chunk_size INTEGER DEFAULT 0,
        cdn_number INTEGER DEFAULT 0,
        transfer_state INTEGER,
        transfer_file TEXT DEFAULT NULL,
        data_file TEXT,
        data_size INTEGER,
        data_random BLOB,
        file_name TEXT,
        fast_preflight_id TEXT,
        voice_note INTEGER DEFAULT 0,
        borderless INTEGER DEFAULT 0,
        video_gif INTEGER DEFAULT 0,
        quote INTEGER DEFAULT 0,
        width INTEGER DEFAULT 0,
        height INTEGER DEFAULT 0,
        caption TEXT DEFAULT NULL,
        sticker_pack_id TEXT DEFAULT NULL,
        sticker_pack_key DEFAULT NULL,
        sticker_id INTEGER DEFAULT -1,
        sticker_emoji STRING DEFAULT NULL,
        blur_hash TEXT DEFAULT NULL,
        transform_properties TEXT DEFAULT NULL,
        display_order INTEGER DEFAULT 0,
        upload_timestamp INTEGER DEFAULT 0,
        data_hash_start TEXT DEFAULT NULL,
        data_hash_end TEXT DEFAULT NULL,
        archive_cdn INTEGER DEFAULT NULL,
        archive_transfer_file TEXT DEFAULT NULL,
        archive_transfer_state INTEGER DEFAULT 0,
        thumbnail_file TEXT DEFAULT NULL,
        thumbnail_random BLOB DEFAULT NULL,
        thumbnail_restore_state INTEGER DEFAULT 0,
        attachment_uuid TEXT DEFAULT NULL,
        remote_iv BLOB DEFAULT NULL,
        offload_restored_at INTEGER DEFAULT 0
      )
      """
    )
    stopwatch.split("create-new-table")

    db.execSQL("INSERT INTO attachment_tmp SELECT * FROM attachment")
    stopwatch.split("copy-data")

    db.execSQL("DROP TABLE attachment")
    stopwatch.split("drop-table")

    db.execSQL("ALTER TABLE attachment_tmp RENAME TO attachment")
    stopwatch.split("rename-table")

    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_message_id_index ON attachment (message_id);")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_transfer_state_index ON attachment (transfer_state);")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_sticker_pack_id_index ON attachment (sticker_pack_id);")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_data_hash_start_index ON attachment (data_hash_start);")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_data_hash_end_index ON attachment (data_hash_end);")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_data_index ON attachment (data_file);")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_archive_transfer_state ON attachment (archive_transfer_state);")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_remote_digest_index ON attachment (remote_digest);")
    stopwatch.split("create-indexes")

    db.execSQL(
      """
      CREATE TRIGGER msl_attachment_delete AFTER DELETE ON attachment 
      BEGIN
        DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE msl_message.message_id = old.message_id);
      END
      """
    )
    stopwatch.split("create-triggers")

    stopwatch.stop(TAG)
  }
}

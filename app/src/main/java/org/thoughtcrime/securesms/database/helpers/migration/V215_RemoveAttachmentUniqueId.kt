/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SQLiteDatabase

object V215_RemoveAttachmentUniqueId : SignalDatabaseMigration {

  private val TAG = Log.tag(V215_RemoveAttachmentUniqueId::class.java)

  override fun migrate(context: Application, db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val stopwatch = Stopwatch("migration", decimalPlaces = 2)

    db.execSQL(
      """
      CREATE TABLE attachment (
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
        data_hash TEXT DEFAULT NULL,
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
        upload_timestamp INTEGER DEFAULT 0
      )
      """
    )

    db.execSQL(
      """
      INSERT INTO attachment 
      SELECT 
        _id,
        mid,
        ct,
        cd,
        cl,
        digest,
        incremental_mac_digest,
        incremental_mac_chunk_size,
        cdn_number,
        pending_push,
        transfer_file,
        _data,
        data_size,
        data_random,
        data_hash,
        file_name,
        fast_preflight_id,
        voice_note,
        borderless,
        video_gif,
        quote,
        width,
        height,
        caption,
        sticker_pack_id,
        sticker_pack_key,
        sticker_id,
        sticker_emoji,
        blur_hash,
        transform_properties,
        display_order,
        upload_timestamp
      FROM part
      """
    )
    stopwatch.split("copy-data")

    db.execSQL("DROP TABLE part")
    stopwatch.split("drop-old")

    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_message_id_index ON attachment (message_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_transfer_state_index ON attachment (transfer_state)")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_sticker_pack_id_index ON attachment (sticker_pack_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_data_hash_index ON attachment (data_hash)")
    db.execSQL("CREATE INDEX IF NOT EXISTS attachment_data_index ON attachment (data_file)")
    stopwatch.split("create-indexes")

    db.execSQL(
      """
      CREATE TRIGGER msl_attachment_delete AFTER DELETE ON attachment 
      BEGIN
        DELETE FROM msl_payload WHERE _id IN (SELECT payload_id FROM msl_message WHERE msl_message.message_id = old.message_id);
      END
      """
    )

    stopwatch.stop(TAG)
  }
}

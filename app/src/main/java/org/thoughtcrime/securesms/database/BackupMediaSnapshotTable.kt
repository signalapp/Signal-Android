/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.readToList
import org.signal.core.util.requireInt
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject

/**
 * Helper table for attachment deletion sync
 */
class BackupMediaSnapshotTable(context: Context, database: SignalDatabase) : DatabaseTable(context, database) {
  companion object {

    const val TABLE_NAME = "backup_media_snapshot"

    private const val ID = "_id"

    /**
     * Generated media id matching that of the attachments table.
     */
    private const val MEDIA_ID = "media_id"

    /**
     * CDN where the data is stored
     */
    private const val CDN = "cdn"

    /**
     * Unique backup snapshot sync time. These are expected to increment in value
     * where newer backups have a greater backup id value.
     */
    @VisibleForTesting
    const val LAST_SYNC_TIME = "last_sync_time"

    /**
     * Pending sync time, set while a backup is in the process of being exported.
     */
    @VisibleForTesting
    const val PENDING_SYNC_TIME = "pending_sync_time"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MEDIA_ID TEXT UNIQUE,
        $CDN INTEGER,
        $LAST_SYNC_TIME INTEGER DEFAULT 0,
        $PENDING_SYNC_TIME INTEGER
      )
    """.trimIndent()

    private const val ON_MEDIA_ID_CONFLICT = """
      ON CONFLICT($MEDIA_ID) DO UPDATE SET
        $PENDING_SYNC_TIME = EXCLUDED.$PENDING_SYNC_TIME,
        $CDN = EXCLUDED.$CDN
    """
  }

  /**
   * Creates the temporary table if it doesn't exist, clears it, then inserts the media objects into it.
   */
  fun writePendingMediaObjects(mediaObjects: Sequence<ArchivedMediaObject>, pendingSyncTime: Long) {
    mediaObjects.chunked(999)
      .forEach { chunk ->
        writePendingMediaObjectsChunk(chunk, pendingSyncTime)
      }
  }

  private fun writePendingMediaObjectsChunk(chunk: List<ArchivedMediaObject>, pendingSyncTime: Long) {
    SqlUtil.buildBulkInsert(
      TABLE_NAME,
      arrayOf(MEDIA_ID, CDN, PENDING_SYNC_TIME),
      chunk.map {
        contentValuesOf(MEDIA_ID to it.mediaId, CDN to it.cdn, PENDING_SYNC_TIME to pendingSyncTime)
      }
    ).forEach {
      writableDatabase.execSQL("${it.where} $ON_MEDIA_ID_CONFLICT", it.whereArgs)
    }
  }

  /**
   * Copies all entries from the temporary table to the persistent table, then deletes the temporary table.
   */
  fun commitPendingRows() {
    writableDatabase.execSQL("UPDATE $TABLE_NAME SET $LAST_SYNC_TIME = $PENDING_SYNC_TIME")
  }

  fun getPageOfOldMediaObjects(currentSyncTime: Long, pageSize: Int): List<ArchivedMediaObject> {
    return readableDatabase.select(MEDIA_ID, CDN)
      .from(TABLE_NAME)
      .where("$LAST_SYNC_TIME < ? AND $LAST_SYNC_TIME = $PENDING_SYNC_TIME", currentSyncTime)
      .limit(pageSize)
      .run()
      .readToList {
        ArchivedMediaObject(mediaId = it.requireNonNullString(MEDIA_ID), cdn = it.requireInt(CDN))
      }
  }

  fun deleteMediaObjects(mediaObjects: List<ArchivedMediaObject>) {
    SqlUtil.buildCollectionQuery(MEDIA_ID, mediaObjects.map { it.mediaId }).forEach {
      writableDatabase.delete(TABLE_NAME)
        .where(it.where, it.whereArgs)
        .run()
    }
  }

  fun hasOldMediaObjects(currentSyncTime: Long): Boolean {
    return readableDatabase.exists(TABLE_NAME).where("$LAST_SYNC_TIME > ? AND $LAST_SYNC_TIME = $PENDING_SYNC_TIME", currentSyncTime).run()
  }
}

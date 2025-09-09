/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.content.Context
import android.database.Cursor
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import org.signal.core.util.SqlUtil
import org.signal.core.util.delete
import org.signal.core.util.readToList
import org.signal.core.util.readToSet
import org.signal.core.util.readToSingleLong
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireIntOrNull
import org.signal.core.util.requireNonNullBlob
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.backup.v2.ArchivedMediaObject

/**
 * When we delete attachments locally, we can't immediately delete them from the archive CDN. This is because there is still a backup that exists that
 * references that attachment -- at least until a new backup is made.
 *
 * So, this table maintains a snapshot of the media present in the last backup, so that we know what we can and can't delete from the archive CDN.
 *
 * The lifecycle is as follows:
 * - Before we make a backup, we clear any pending entries that might be left over from an aborted backup.
 * - While a backup is in progress, we write entries here for each media item, marking them with a pending flag.
 * - After a backup is fully uploaded, we commit the pending entries and update their version to MAX([SNAPSHOT_VERSION]) + 1
 *
 * The end result is that we have all the media objects referenced in backups, tagged with the most recent snapshot version they were seen at.
 *
 * This lets us know a few things:
 * 1. We know that any non-pending entries whose version < MAX([SNAPSHOT_VERSION]) must have been deleted.
 * 2. We know that any entries with MAX([SNAPSHOT_VERSION]) who aren't fully backed up yet (according to the [AttachmentTable]) need to be backed up.
 *
 * Occasionally, we'll also run a more elaborate "reconciliation" process where we fetch all of the remote CDN entries. That data, combined with this table,
 * will let us do the following:
 * 1. Any entries on the remote CDN that are not present in the table with MAX([SNAPSHOT_VERSION]) can be deleted from the remote CDN.
 * 2. Any entries present in this table with MAX([SNAPSHOT_VERSION]) that are not present on the remote CDN need to be re-uploaded. This is trickier, since the
 *   remote CDN data is too large to fit in memory. To address that, as we page through remote CDN entries, we can set the [LAST_SEEN_ON_REMOTE_SNAPSHOT_VERSION]
 *   equal to MAX([SNAPSHOT_VERSION]). After we're done, any entries whose [SNAPSHOT_VERSION] = MAX([SNAPSHOT_VERSION]), but whose
 *   [LAST_SEEN_ON_REMOTE_SNAPSHOT_VERSION] < MAX([SNAPSHOT_VERSION]) must be entries that were missing from the remote CDN.
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
    const val SNAPSHOT_VERSION = "snapshot_version"

    /**
     * Pending sync time, set while a backup is in the process of being exported.
     */
    @VisibleForTesting
    const val IS_PENDING = "is_pending"

    /**
     * Whether or not this entry is for a thumbnail.
     */
    const val IS_THUMBNAIL = "is_thumbnail"

    /**
     * Timestamp when media was last seen on archive cdn. Can be reset to default.
     */
    const val LAST_SEEN_ON_REMOTE_SNAPSHOT_VERSION = "last_seen_on_remote_snapshot_version"

    /**
     * The plaintext hash of the media object. This is used to find matching attachments in the attachment table when necessary.
     */
    const val PLAINTEXT_HASH = "plaintext_hash"

    /**
     * The remote that was used for encrypting for the media object. This is used to find matching attachments in the attachment table when necessary.
     */
    const val REMOTE_KEY = "remote_key"

    /** Constant representing a [SNAPSHOT_VERSION] version that has not yet been set. */
    const val UNKNOWN_VERSION = -1

    private const val MAX_SNAPSHOT_VERSION_INDEX = "backup_snapshot_version_index"

    /** A query that returns that max [SNAPSHOT_VERSION] presently in the table. An index exists to ensure that this is fast. */
    const val MAX_VERSION = "(SELECT MAX($SNAPSHOT_VERSION) FROM $TABLE_NAME INDEXED BY $MAX_SNAPSHOT_VERSION_INDEX WHERE $SNAPSHOT_VERSION != $UNKNOWN_VERSION)"

    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY,
        $MEDIA_ID TEXT NOT NULL UNIQUE,
        $CDN INTEGER,
        $SNAPSHOT_VERSION INTEGER NOT NULL DEFAULT $UNKNOWN_VERSION,
        $IS_PENDING INTEGER NOT NULL DEFAULT 0,
        $IS_THUMBNAIL INTEGER NOT NULL DEFAULT 0,
        $PLAINTEXT_HASH BLOB NOT NULL,
        $REMOTE_KEY BLOB NOT NULL,
        $LAST_SEEN_ON_REMOTE_SNAPSHOT_VERSION INTEGER NOT NULL DEFAULT 0
      )
    """.trimIndent()

    val CREATE_INDEXES = arrayOf(
      "CREATE INDEX IF NOT EXISTS $MAX_SNAPSHOT_VERSION_INDEX ON $TABLE_NAME ($SNAPSHOT_VERSION DESC) WHERE $SNAPSHOT_VERSION != $UNKNOWN_VERSION"
    )
  }

  /**
   * Writes the set of full-size media items that are slated to be referenced in the next backup, updating their pending sync time.
   * Will insert multiple rows per object -- one for the main item, and one for the thumbnail.
   */
  fun writeFullSizePendingMediaObjects(mediaObjects: Sequence<ArchiveMediaItem>) {
    mediaObjects
      .chunked(SqlUtil.MAX_QUERY_ARGS)
      .forEach { chunk ->
        writePendingMediaObjectsChunk(
          chunk.map { MediaEntry(it.mediaId, it.cdn, it.plaintextHash, it.remoteKey, isThumbnail = false) }
        )
      }
  }

  /**
   * Writes the set of thumbnail media items that are slated to be referenced in the next backup, updating their pending sync time.
   * Will insert multiple rows per object -- one for the main item, and one for the thumbnail.
   */
  fun writeThumbnailPendingMediaObjects(mediaObjects: Sequence<ArchiveMediaItem>) {
    mediaObjects
      .chunked(SqlUtil.MAX_QUERY_ARGS)
      .forEach { chunk ->
        writePendingMediaObjectsChunk(
          chunk.map { MediaEntry(it.thumbnailMediaId, it.cdn, it.plaintextHash, it.remoteKey, isThumbnail = true) }
        )
      }
  }

  /**
   * Commits all pending entries (written via [writePendingMediaObjects]) to have a concrete [SNAPSHOT_VERSION]. The version will be 1 higher than the previous
   * snapshot version.
   */
  fun commitPendingRows() {
    writableDatabase.withinTransaction {
      val currentSnapshotVersion = getCurrentSnapshotVersion()
      val nextSnapshotVersion = currentSnapshotVersion + 1

      writableDatabase
        .update(TABLE_NAME)
        .values(
          SNAPSHOT_VERSION to nextSnapshotVersion,
          IS_PENDING to 0
        )
        .where("$IS_PENDING != 0")
        .run()
    }
  }

  fun getCurrentSnapshotVersion(): Long {
    return readableDatabase
      .select("MAX($SNAPSHOT_VERSION)")
      .from("$TABLE_NAME INDEXED BY $MAX_SNAPSHOT_VERSION_INDEX")
      .where("$SNAPSHOT_VERSION != $UNKNOWN_VERSION")
      .run()
      .readToSingleLong(0)
  }

  fun getPageOfOldMediaObjects(pageSize: Int): Set<ArchivedMediaObject> {
    return readableDatabase.select(MEDIA_ID, CDN)
      .from(TABLE_NAME)
      .where("$SNAPSHOT_VERSION < $MAX_VERSION AND $IS_PENDING = 0")
      .limit(pageSize)
      .run()
      .readToSet {
        ArchivedMediaObject(mediaId = it.requireNonNullString(MEDIA_ID), cdn = it.requireInt(CDN))
      }
  }

  /**
   * This will remove any old snapshot entries with matching mediaId's. No pending entries or entries in the latest snapshot will be affected.
   */
  fun deleteOldMediaObjects(mediaObjects: Collection<ArchivedMediaObject>) {
    val query = SqlUtil.buildFastCollectionQuery(MEDIA_ID, mediaObjects.map { it.mediaId })

    writableDatabase.delete(TABLE_NAME)
      .where("$SNAPSHOT_VERSION < $MAX_VERSION AND $IS_PENDING = 0 AND " + query.where, query.whereArgs)
      .run()
  }

  /**
   * Given a list of media objects, find the ones that are not in the most recent backup snapshot.
   *
   * We purposely allow pending items here -- so long as they were in the most recent complete snapshot, we want to keep them.
   */
  fun getMediaObjectsThatCantBeFound(objects: List<ArchivedMediaObject>): Set<ArchivedMediaObject> {
    if (objects.isEmpty()) {
      return emptySet()
    }

    val queries: List<SqlUtil.Query> = SqlUtil.buildCollectionQuery(
      column = MEDIA_ID,
      values = objects.map { it.mediaId },
      collectionOperator = SqlUtil.CollectionOperator.IN,
      prefix = "$SNAPSHOT_VERSION = $MAX_VERSION AND "
    )

    val foundObjects: MutableSet<String> = mutableSetOf()

    for (query in queries) {
      foundObjects += readableDatabase
        .select(MEDIA_ID, CDN)
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .readToSet {
          it.requireNonNullString(MEDIA_ID)
        }
    }

    return objects.filterNot { foundObjects.contains(it.mediaId) }.toSet()
  }

  fun getMediaEntriesForObjects(objects: List<ArchivedMediaObject>): Set<MediaEntry> {
    if (objects.isEmpty()) {
      return emptySet()
    }

    val queries: List<SqlUtil.Query> = SqlUtil.buildCollectionQuery(
      column = MEDIA_ID,
      values = objects.map { it.mediaId },
      collectionOperator = SqlUtil.CollectionOperator.IN,
      prefix = "$SNAPSHOT_VERSION = $MAX_VERSION AND "
    )

    val entries: MutableSet<MediaEntry> = mutableSetOf()

    for (query in queries) {
      entries += readableDatabase
        .select(MEDIA_ID, CDN, PLAINTEXT_HASH, REMOTE_KEY, IS_THUMBNAIL)
        .from("$TABLE_NAME JOIN ${AttachmentTable.TABLE_NAME}")
        .where(query.where, query.whereArgs)
        .run()
        .readToList { MediaEntry.fromCursor(it) }
    }

    return entries.toSet()
  }

  /**
   * Given a list of media objects, find the ones that are present in the most recent snapshot, but have a different CDN than the one passed in.
   * This will ignore thumbnails, as the results are intended to be used to update CDNs, which we do not track for thumbnails.
   *
   * We purposely allow pending items here -- either way they're in the latest snapshot, and should have their CDN info updated.
   */
  fun getMediaObjectsWithNonMatchingCdn(objects: List<ArchivedMediaObject>): List<CdnMismatchResult> {
    if (objects.isEmpty()) {
      return emptyList()
    }

    val inputValues = objects.joinToString(separator = ", ") { "('${it.mediaId}', ${it.cdn})" }
    return readableDatabase.rawQuery(
      """
      WITH input_pairs($MEDIA_ID, $CDN) AS (VALUES $inputValues)
      SELECT a.$PLAINTEXT_HASH, a.$REMOTE_KEY, b.$CDN
      FROM $TABLE_NAME a
      JOIN input_pairs b ON a.$MEDIA_ID = b.$MEDIA_ID
      WHERE a.$CDN != b.$CDN AND a.$IS_THUMBNAIL = 0 AND $SNAPSHOT_VERSION = $MAX_VERSION
      """
    ).readToList { cursor ->
      CdnMismatchResult(
        plaintextHash = cursor.requireNonNullBlob(PLAINTEXT_HASH),
        remoteKey = cursor.requireNonNullBlob(REMOTE_KEY),
        cdn = cursor.requireInt(CDN)
      )
    }
  }

  /**
   * Indicate the time that the set of media objects were seen on the archive CDN. Can be used to reconcile our local state with the server state.
   */
  fun markSeenOnRemote(mediaIdBatch: Collection<String>, snapshotVersion: Long) {
    if (mediaIdBatch.isEmpty()) {
      return
    }

    val query = SqlUtil.buildFastCollectionQuery(MEDIA_ID, mediaIdBatch)
    writableDatabase
      .update(TABLE_NAME)
      .values(LAST_SEEN_ON_REMOTE_SNAPSHOT_VERSION to snapshotVersion)
      .where(query.where, query.whereArgs)
      .run()
  }

  /**
   * Get all media objects in specified snapshot who were last seen on the CDN before that snapshot.
   * This is used to find media objects that have not been seen on the CDN, even though they should be.
   *
   * The cursor contains rows that can be parsed into [MediaEntry] objects.
   *
   * We purposely allow pending items here -- either way they *should* be uploaded.
   */
  fun getMediaObjectsLastSeenOnCdnBeforeSnapshotVersion(snapshotVersion: Long): Cursor {
    return readableDatabase
      .select(MEDIA_ID, CDN, PLAINTEXT_HASH, REMOTE_KEY, IS_THUMBNAIL)
      .from(TABLE_NAME)
      .where("$LAST_SEEN_ON_REMOTE_SNAPSHOT_VERSION < $snapshotVersion AND $SNAPSHOT_VERSION = $snapshotVersion")
      .run()
  }

  private fun writePendingMediaObjectsChunk(chunk: List<MediaEntry>) {
    if (chunk.isEmpty()) {
      return
    }

    val values = chunk.map {
      contentValuesOf(
        MEDIA_ID to it.mediaId,
        CDN to it.cdn,
        PLAINTEXT_HASH to it.plaintextHash,
        REMOTE_KEY to it.remoteKey,
        IS_THUMBNAIL to it.isThumbnail.toInt(),
        SNAPSHOT_VERSION to UNKNOWN_VERSION,
        IS_PENDING to 1
      )
    }

    val query = SqlUtil.buildSingleBulkInsert(TABLE_NAME, arrayOf(MEDIA_ID, CDN, PLAINTEXT_HASH, REMOTE_KEY, IS_THUMBNAIL, SNAPSHOT_VERSION, IS_PENDING), values)

    writableDatabase.execSQL(
      query.where +
        """
        ON CONFLICT($MEDIA_ID) DO UPDATE SET
          $CDN = excluded.$CDN,
          $PLAINTEXT_HASH = excluded.$PLAINTEXT_HASH,
          $REMOTE_KEY = excluded.$REMOTE_KEY,
          $IS_THUMBNAIL = excluded.$IS_THUMBNAIL,
          $IS_PENDING = excluded.$IS_PENDING
        """,
      query.whereArgs
    )
  }

  class ArchiveMediaItem(
    val mediaId: String,
    val thumbnailMediaId: String,
    val cdn: Int?,
    val plaintextHash: ByteArray,
    val remoteKey: ByteArray,
    val quote: Boolean,
    val contentType: String?
  )

  class CdnMismatchResult(
    val plaintextHash: ByteArray,
    val remoteKey: ByteArray,
    val cdn: Int
  )

  class MediaEntry(
    val mediaId: String,
    val cdn: Int?,
    val plaintextHash: ByteArray,
    val remoteKey: ByteArray,
    val isThumbnail: Boolean
  ) {
    companion object {
      fun fromCursor(cursor: Cursor): MediaEntry {
        return MediaEntry(
          mediaId = cursor.requireNonNullString(MEDIA_ID),
          cdn = cursor.requireIntOrNull(CDN),
          plaintextHash = cursor.requireNonNullBlob(PLAINTEXT_HASH),
          remoteKey = cursor.requireNonNullBlob(REMOTE_KEY),
          isThumbnail = cursor.requireBoolean(IS_THUMBNAIL)
        )
      }
    }
  }
}

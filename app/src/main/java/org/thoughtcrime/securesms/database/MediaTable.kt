package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import androidx.compose.runtime.Immutable
import org.signal.core.database.SqlUtil
import org.signal.core.models.database.AttachmentId
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.JsonUtils
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MediaUtil.SlideType
import java.io.IOException

@SuppressLint("RecipientIdDatabaseReferenceUsage", "ThreadIdDatabaseReferenceUsage") // Not a real table, just a view
class MediaTable internal constructor(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(MediaTable::class)
    const val ALL_THREADS = -1
    private const val THREAD_RECIPIENT_ID = "THREAD_RECIPIENT_ID"
    const val MEDIA_MESSAGE_ID = "media_message_id"
    private const val THREAD_MESSAGE_ID = "thread_message_id"

    /** Projection used for most queries. Only [linkMediaQuery] rows carry link previews. */
    private const val MEDIA_QUERY_PROJECTION = """
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} AS ${AttachmentTable.ID},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CONTENT_TYPE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFER_STATE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_SIZE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FILE_NAME},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_FILE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.THUMBNAIL_FILE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CDN_NUMBER},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_LOCATION},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_KEY},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_DIGEST},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FAST_PREFLIGHT_ID},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VOICE_NOTE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.BORDERLESS},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VIDEO_GIF},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.WIDTH},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.HEIGHT},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.QUOTE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.QUOTE_TARGET_CONTENT_TYPE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_ID},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_KEY},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_ID},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_EMOJI},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.BLUR_HASH},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFORM_PROPERTIES},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CAPTION},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.UPLOAD_TIMESTAMP},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_INCREMENTAL_DIGEST},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_HASH_END},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_CDN},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.THUMBNAIL_RESTORE_STATE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_TRANSFER_STATE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_THUMBNAIL_TRANSFER_STATE},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ATTACHMENT_UUID},
        ${MessageTable.TABLE_NAME}.${MessageTable.TYPE},
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_SENT},
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED},
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_SERVER},
        ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID},
        ${MessageTable.TABLE_NAME}.${MessageTable.FROM_RECIPIENT_ID},
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} as $THREAD_RECIPIENT_ID,
        NULL AS ${MessageTable.LINK_PREVIEWS},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} as $MEDIA_MESSAGE_ID
      """

    /** Attachments joined to their message and thread, used as the FROM clause for most queries. */
    private const val MEDIA_QUERY_TABLES = """
        ${AttachmentTable.TABLE_NAME}
        LEFT JOIN ${MessageTable.TABLE_NAME} ON ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} = ${MessageTable.TABLE_NAME}.${MessageTable.ID}
        LEFT JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} = ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID}
      """

    /**
     * The same join, but driven from the message thread/date index so a single-thread query ordered by
     * date_received streams rows in index order and LIMIT/OFFSET can stop early instead of sorting the whole gallery.
     */
    private const val MEDIA_QUERY_TABLES_BY_THREAD_DATE = """
        ${MessageTable.TABLE_NAME} INDEXED BY ${MessageTable.INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID}
        CROSS JOIN ${AttachmentTable.TABLE_NAME} ON ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} = ${MessageTable.TABLE_NAME}.${MessageTable.ID}
        LEFT JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} = ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID}
      """

    /** Attachments read largest first from the size index, so an all-chats page can stop once it has its rows. */
    private const val MEDIA_QUERY_TABLES_BY_SIZE = """
        ${AttachmentTable.TABLE_NAME} INDEXED BY ${AttachmentTable.MEDIA_OVERVIEW_SIZE_INDEX}
        LEFT JOIN ${MessageTable.TABLE_NAME} ON ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} = ${MessageTable.TABLE_NAME}.${MessageTable.ID}
        LEFT JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} = ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID}
      """

    /** Conditions that are common across most queries. */
    private const val MEDIA_QUERY_CONDITIONS = """
        ${MessageTable.SCHEDULED_DATE} < 0 AND
        ${MessageTable.VIEW_ONCE} = 0 AND
        ${MessageTable.STORY_TYPE} = 0 AND
        ${MessageTable.LATEST_REVISION_ID} IS NULL AND
        ${AttachmentTable.QUOTE} = 0 AND
        ${AttachmentTable.STICKER_PACK_ID} IS NULL AND
        ${MessageTable.TABLE_NAME}.${MessageTable.FROM_RECIPIENT_ID} > 0 AND
        $THREAD_RECIPIENT_ID > 0
      """

    /** An image attachment on a message with a link preview. The media views show it only as the link's thumbnail. */
    private const val LINK_PREVIEW_THUMBNAIL_CONDITION = """
        (${MessageTable.LINK_PREVIEWS} IS NOT NULL AND ${AttachmentTable.CONTENT_TYPE} LIKE 'image/%')
      """

    /** Attachments taking up local storage. The all-chats overview is opened from storage management, so it only lists these. */
    private const val STORED_LOCALLY_CONDITION = "${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_FILE} IS NOT NULL"

    /** Attachments with local data, or offloaded ones that can be restored. */
    private const val DOWNLOADED_OR_OFFLOADED_CONDITION = """
        (${AttachmentTable.DATA_FILE} IS NOT NULL OR ${AttachmentTable.TRANSFER_STATE} = ${AttachmentTable.TRANSFER_RESTORE_OFFLOADED})
      """

    /** Gallery attachments with local data or that can be fetched, including videos that can be streamed. */
    private const val GALLERY_AVAILABLE_CONDITION = """
        (${AttachmentTable.DATA_FILE} IS NOT NULL OR (${AttachmentTable.CONTENT_TYPE} LIKE 'video/%' AND ${AttachmentTable.REMOTE_INCREMENTAL_DIGEST} IS NOT NULL) OR ${AttachmentTable.THUMBNAIL_FILE} IS NOT NULL OR ${AttachmentTable.TRANSFER_STATE} = ${AttachmentTable.TRANSFER_RESTORE_OFFLOADED})
      """

    /** Image and video attachments, excluding SVGs and link preview thumbnails. */
    private const val GALLERY_TYPE_CONDITIONS = """
        ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'image/svg%' AND
        (${AttachmentTable.CONTENT_TYPE} LIKE 'image/%' OR ${AttachmentTable.CONTENT_TYPE} LIKE 'video/%') AND
        NOT $LINK_PREVIEW_THUMBNAIL_CONDITION
      """
  }

  @JvmOverloads
  fun getGalleryMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0, offset: Int = 0): Cursor {
    val query = galleryMediaQuery(threadId, mediaSource(threadId, sorting))
      .sortedBy(sorting)
      .paged(limit, offset)

    return readableDatabase.rawQuery(query, null)
  }

  /** How many rows [getGalleryMediaForThread] would return for the thread. */
  fun getGalleryMediaCountForThread(threadId: Long): Int {
    return count(galleryMediaQuery(threadId, countSource(threadId)), null)
  }

  /**
   * Gallery media for [threadId] narrowed to the rows the media viewer can actually render, in
   * [sorting] order. [limit] and [offset] page the results.
   */
  fun getViewableGalleryMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0, offset: Int = 0): List<MediaRecord> {
    val (query, args) = if (threadId == ALL_THREADS.toLong() || sorting == Sorting.Largest) {
      viewableGalleryMediaQuery(threadId, threadSubqueryFilter(threadId)) to null
    } else {
      viewableGalleryMediaQuery(threadId, threadFilter(threadId), tables = MEDIA_QUERY_TABLES_BY_THREAD_DATE) to arrayOf(threadId.toString())
    }

    return readableDatabase
      .rawQuery(
        query
          .sortedBy(sorting)
          .paged(limit, offset),
        args
      )
      .readToList { MediaRecord.from(it) }
  }

  /** The [getViewableGalleryMediaForThread] row for [attachmentId], or null when it is absent or not viewable. */
  fun getViewableGalleryMediaForAttachment(threadId: Long, attachmentId: Long): MediaRecord? {
    val query = "${viewableGalleryMediaQuery(threadId, threadFilter(threadId))} AND ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} = ?"

    return readableDatabase
      .rawQuery(query, arrayOf(threadId.toString(), attachmentId.toString()))
      .readToSingleObject { MediaRecord.from(it) }
  }

  /** How many rows [getViewableGalleryMediaForThread] would return for the thread. */
  fun getViewableGalleryMediaCountForThread(threadId: Long): Int {
    val query = "SELECT COUNT(*) FROM (${viewableGalleryMediaQuery(threadId, threadSubqueryFilter(threadId))})"
    return readableDatabase.rawQuery(query, null).readToSingleInt(0)
  }

  /**
   * @return The zero-based position paired with the total count, batched in a single query.
   */
  fun getViewableGalleryMediaPositionAndCount(threadId: Long, sorting: Sorting, attachmentId: Long): GalleryPositionAndCount? {
    val anchor = getViewableGalleryMediaForAttachment(threadId, attachmentId) ?: return null
    val anchorAttachment = anchor.attachment ?: return null
    val before = sorting.projectedBeforePredicate(anchorAttachment, anchor.dateReceived)
    val query = """
      SELECT SUM(CASE WHEN ${before.where} THEN 1 ELSE 0 END), COUNT(*)
      FROM (${viewableGalleryMediaQuery(threadId, threadSubqueryFilter(threadId))})
    """

    return readableDatabase.rawQuery(query, before.whereArgs).readToSingleObject { cursor ->
      GalleryPositionAndCount(position = cursor.getInt(0), count = cursor.getInt(1))
    }
  }

  @JvmOverloads
  fun getDocumentMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0, offset: Int = 0): Cursor {
    val query = documentMediaQuery(threadId, mediaSource(threadId, sorting))
      .sortedBy(sorting)
      .paged(limit, offset)

    return readableDatabase.rawQuery(query, null)
  }

  /** How many rows [getDocumentMediaForThread] would return for the thread. */
  fun getDocumentMediaCountForThread(threadId: Long): Int {
    return count(documentMediaQuery(threadId, countSource(threadId)), null)
  }

  @JvmOverloads
  fun getAudioMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0, offset: Int = 0): Cursor {
    val query = audioMediaQuery(threadId, mediaSource(threadId, sorting))
      .sortedBy(sorting)
      .paged(limit, offset)

    return readableDatabase.rawQuery(query, null)
  }

  /** How many rows [getAudioMediaForThread] would return for the thread. */
  fun getAudioMediaCountForThread(threadId: Long): Int {
    return count(audioMediaQuery(threadId, countSource(threadId)), null)
  }

  @JvmOverloads
  fun getLinkMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0, offset: Int = 0): Cursor {
    val query = threadLinkMediaQuery(threadId)
      .projectedSortedBy(sorting)
      .paged(limit, offset)

    return readableDatabase.rawQuery(query, null)
  }

  /** How many rows [getLinkMediaForThread] would return for the thread. */
  fun getLinkMediaCountForThread(threadId: Long): Int {
    return count(threadLinkMediaQuery(threadId), null)
  }

  /**
   * Every attachment and link in the thread. Each half comes out of its own index already in order, so
   * SQLite merges them and can stop at the page instead of sorting both in full.
   */
  @JvmOverloads
  fun getAllMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0, offset: Int = 0): Cursor {
    val query = allMediaQuery(threadId, mediaSource(threadId, sorting))
      .projectedSortedBy(sorting)
      .paged(limit, offset)

    return readableDatabase.rawQuery(query, null)
  }

  /** How many rows [getAllMediaForThread] would return for the thread. */
  fun getAllMediaCountForThread(threadId: Long): Int {
    return count(allMediaQuery(threadId, countSource(threadId)), null)
  }

  /**
   * The rows for [attachmentIds], plus link rows for [linkMessageIds], in the same shape the per-tab
   * queries return. Ids that no longer exist are skipped.
   */
  fun getMediaRecords(attachmentIds: Collection<AttachmentId>, linkMessageIds: Collection<Long>): List<MediaRecord> {
    val attachmentRecords = SqlUtil.buildCollectionQuery("${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID}", attachmentIds.map { it.id }).flatMap { query ->
      readableDatabase
        .rawQuery("SELECT $MEDIA_QUERY_PROJECTION FROM $MEDIA_QUERY_TABLES WHERE ${query.where}", query.whereArgs)
        .readToList { MediaRecord.from(it) }
    }

    val linkRecords = SqlUtil.buildCollectionQuery("${MessageTable.TABLE_NAME}.${MessageTable.ID}", linkMessageIds).flatMap { query ->
      readableDatabase
        .rawQuery(linkMediaQuery(query.where), query.whereArgs)
        .readToList { MediaRecord.from(it) }
    }

    return attachmentRecords + linkRecords
  }

  fun getStorageBreakdown(): StorageBreakdown {
    var photoSize: Long = 0
    var videoSize: Long = 0
    var audioSize: Long = 0
    var documentSize: Long = 0

    val query = """
      SELECT
        SUM(${AttachmentTable.DATA_SIZE}) AS ${AttachmentTable.DATA_SIZE},
        ${AttachmentTable.CONTENT_TYPE}
      FROM (
        SELECT
          MAX(${AttachmentTable.DATA_SIZE}) AS ${AttachmentTable.DATA_SIZE},
          ${AttachmentTable.CONTENT_TYPE}
        FROM
          ${AttachmentTable.TABLE_NAME}
        WHERE
          ${AttachmentTable.STICKER_PACK_ID} IS NULL AND
          ${AttachmentTable.DATA_FILE} IS NOT NULL
        GROUP BY ${AttachmentTable.DATA_FILE}
      )
      GROUP BY ${AttachmentTable.CONTENT_TYPE}
    """

    readableDatabase.rawQuery(query, null).use { cursor ->
      while (cursor.moveToNext()) {
        val size: Long = cursor.requireLong(AttachmentTable.DATA_SIZE)
        val type: String? = cursor.requireString(AttachmentTable.CONTENT_TYPE)

        when (MediaUtil.getSlideTypeFromContentType(type)) {
          SlideType.GIF,
          SlideType.IMAGE,
          SlideType.MMS -> {
            photoSize += size
          }

          SlideType.VIDEO -> {
            videoSize += size
          }

          SlideType.AUDIO -> {
            audioSize += size
          }

          SlideType.LONG_TEXT,
          SlideType.DOCUMENT -> {
            documentSize += size
          }

          SlideType.VIEW_ONCE -> Unit
        }
      }
    }

    return StorageBreakdown(
      photoSize = photoSize,
      videoSize = videoSize,
      audioSize = audioSize,
      documentSize = documentSize
    )
  }

  private fun galleryMediaQuery(threadId: Long, source: String): String {
    return """
      SELECT
        $MEDIA_QUERY_PROJECTION
      FROM
        $source AND
        ${galleryConditions(threadId)} AND
        $MEDIA_QUERY_CONDITIONS
      """
  }

  private fun documentMediaQuery(threadId: Long, source: String): String {
    return """
      SELECT
        $MEDIA_QUERY_PROJECTION
      FROM
        $source AND
        ${availabilityCondition(threadId)} AND
        (
          ${AttachmentTable.CONTENT_TYPE} LIKE 'image/svg%' OR
          (
            ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'image/%' AND
            ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'video/%' AND
            ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'audio/%' AND
            ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'text/x-signal-plain'
          )
        ) AND
        $MEDIA_QUERY_CONDITIONS
      """
  }

  private fun audioMediaQuery(threadId: Long, source: String): String {
    return """
      SELECT
        $MEDIA_QUERY_PROJECTION
      FROM
        $source AND
        ${availabilityCondition(threadId)} AND
        ${AttachmentTable.CONTENT_TYPE} LIKE 'audio/%' AND
        $MEDIA_QUERY_CONDITIONS
      """
  }

  /** Every non-link attachment plus every link, unordered. */
  private fun allMediaQuery(threadId: Long, source: String): String {
    val allMediaSubquery = """
      SELECT
        $MEDIA_QUERY_PROJECTION
      FROM
        $source AND
        ${availabilityCondition(threadId)} AND
        ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'text/x-signal-plain' AND
        NOT $LINK_PREVIEW_THUMBNAIL_CONDITION AND
        $MEDIA_QUERY_CONDITIONS
      """

    return "$allMediaSubquery UNION ALL ${threadLinkMediaQuery(threadId)}"
  }

  /** Links in [threadId], in received order. Across all threads, only links whose preview image is stored locally. */
  private fun threadLinkMediaQuery(threadId: Long): String {
    val filter = if (threadId == ALL_THREADS.toLong()) {
      "${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} != $threadId AND $STORED_LOCALLY_CONDITION"
    } else {
      "${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} = $threadId"
    }

    return linkMediaQuery(
      filter = filter,
      messages = "${MessageTable.TABLE_NAME} INDEXED BY ${MessageTable.INDEX_LINK_PREVIEW_DATE}"
    )
  }

  /** What makes a non-gallery attachment listable in [threadId]. */
  private fun availabilityCondition(threadId: Long): String {
    return if (threadId == ALL_THREADS.toLong()) STORED_LOCALLY_CONDITION else DOWNLOADED_OR_OFFLOADED_CONDITION
  }

  private fun galleryConditions(threadId: Long): String {
    val availability = if (threadId == ALL_THREADS.toLong()) STORED_LOCALLY_CONDITION else GALLERY_AVAILABLE_CONDITION
    return "$availability AND $GALLERY_TYPE_CONDITIONS"
  }

  /** Generates the FROM and WHERE clause for the media page queries, using the appropriate index. */
  private fun mediaSource(threadId: Long, sorting: Sorting): String {
    return if (threadId != ALL_THREADS.toLong()) {
      "$MEDIA_QUERY_TABLES_BY_THREAD_DATE WHERE ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} = $threadId"
    } else if (sorting == Sorting.Largest) {
      "$MEDIA_QUERY_TABLES_BY_SIZE WHERE ${threadSubqueryFilter(threadId)}"
    } else {
      "$MEDIA_QUERY_TABLES WHERE ${threadSubqueryFilter(threadId)}"
    }
  }

  /**
   * Like [mediaSource], but walks the thread's message ids from the index alone and only reads the
   * message rows that have an attachment. Much cheaper for a count in a thread with mostly text, but
   * rows come out unordered, so pages keep using [mediaSource].
   */
  private fun countSource(threadId: Long): String {
    if (threadId == ALL_THREADS.toLong()) {
      return "$MEDIA_QUERY_TABLES WHERE ${threadSubqueryFilter(threadId)}"
    }

    return """
      (
        SELECT ${MessageTable.ID} AS $THREAD_MESSAGE_ID
        FROM ${MessageTable.TABLE_NAME} INDEXED BY ${MessageTable.INDEX_THREAD_STORY_SCHEDULED_DATE_LATEST_REVISION_ID}
        WHERE ${MessageTable.THREAD_ID} = $threadId
      )
      CROSS JOIN ${AttachmentTable.TABLE_NAME} ON ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} = $THREAD_MESSAGE_ID
      CROSS JOIN ${MessageTable.TABLE_NAME} ON ${MessageTable.TABLE_NAME}.${MessageTable.ID} = $THREAD_MESSAGE_ID
      LEFT JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} = ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID}
      WHERE ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} = $threadId
    """
  }

  private fun count(query: String, args: Array<String>?): Int {
    return readableDatabase.rawQuery("SELECT COUNT(*) FROM ($query)", args).readToSingleInt(0)
  }

  private fun String.sortedBy(sorting: Sorting): String = this + sorting.orderBy

  private fun String.projectedSortedBy(sorting: Sorting): String = this + sorting.projectedOrderBy

  private fun String.paged(limit: Int, offset: Int): String {
    return if (limit > 0) "$this LIMIT $limit OFFSET $offset" else this
  }

  /** Returns all media for links (like, urls sent in chats), restricted by [filter]. */
  private fun linkMediaQuery(filter: String, messages: String = MessageTable.TABLE_NAME): String {
    return """
        SELECT
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} AS ${AttachmentTable.ID},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CONTENT_TYPE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFER_STATE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_SIZE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FILE_NAME},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_FILE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.THUMBNAIL_FILE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CDN_NUMBER},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_LOCATION},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_KEY},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_DIGEST},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FAST_PREFLIGHT_ID},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VOICE_NOTE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.BORDERLESS},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VIDEO_GIF},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.WIDTH},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.HEIGHT},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.QUOTE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.QUOTE_TARGET_CONTENT_TYPE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_ID},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_KEY},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_ID},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_EMOJI},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.BLUR_HASH},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFORM_PROPERTIES},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CAPTION},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.UPLOAD_TIMESTAMP},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_INCREMENTAL_DIGEST},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_HASH_END},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_CDN},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.THUMBNAIL_RESTORE_STATE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_TRANSFER_STATE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ARCHIVE_THUMBNAIL_TRANSFER_STATE},
          ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ATTACHMENT_UUID},
          ${MessageTable.TABLE_NAME}.${MessageTable.TYPE},
          ${MessageTable.TABLE_NAME}.${MessageTable.DATE_SENT},
          ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED},
          ${MessageTable.TABLE_NAME}.${MessageTable.DATE_SERVER},
          ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID},
          ${MessageTable.TABLE_NAME}.${MessageTable.FROM_RECIPIENT_ID},
          ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} as $THREAD_RECIPIENT_ID,
          ${MessageTable.TABLE_NAME}.${MessageTable.LINK_PREVIEWS},
          ${MessageTable.TABLE_NAME}.${MessageTable.ID} as $MEDIA_MESSAGE_ID
        FROM
          $messages
          LEFT JOIN ${AttachmentTable.TABLE_NAME} ON ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} = ${MessageTable.TABLE_NAME}.${MessageTable.ID}
            AND ${AttachmentTable.TABLE_NAME}.${AttachmentTable.QUOTE} = 0
            AND ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_ID} IS NULL
            AND $LINK_PREVIEW_THUMBNAIL_CONDITION
          LEFT JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} = ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID}
        WHERE
          $filter AND
          ${MessageTable.TABLE_NAME}.${MessageTable.LINK_PREVIEWS} IS NOT NULL AND
          ${MessageTable.TABLE_NAME}.${MessageTable.VIEW_ONCE} = 0 AND
          ${MessageTable.TABLE_NAME}.${MessageTable.STORY_TYPE} = 0 AND
          ${MessageTable.TABLE_NAME}.${MessageTable.LATEST_REVISION_ID} IS NULL AND
          ${MessageTable.TABLE_NAME}.${MessageTable.FROM_RECIPIENT_ID} > 0 AND
          $THREAD_RECIPIENT_ID > 0 AND
          ${MessageTable.TABLE_NAME}.${MessageTable.SCHEDULED_DATE} < 0
        """
  }

  /** Matches [threadId] through a message id subquery, or every thread for [ALL_THREADS]. The thread id is inlined, not bound. */
  private fun threadSubqueryFilter(threadId: Long): String {
    return if (threadId == ALL_THREADS.toLong()) {
      "${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} != $threadId"
    } else {
      "${AttachmentTable.MESSAGE_ID} IN (SELECT ${MessageTable.ID} FROM ${MessageTable.TABLE_NAME} WHERE ${MessageTable.THREAD_ID} = $threadId)"
    }
  }

  /** Matches [threadId] on the joined message row, or every thread for [ALL_THREADS]. Binds the thread id. */
  private fun threadFilter(threadId: Long): String {
    val operator = if (threadId == ALL_THREADS.toLong()) "!=" else "="
    return "${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} $operator ?"
  }

  /** Gallery media narrowed to rows with something renderable on disk: full data, an in-progress download, or a restored thumbnail. */
  private fun viewableGalleryMediaQuery(threadId: Long, threadFilter: String, tables: String = MEDIA_QUERY_TABLES): String {
    return """
        SELECT
          $MEDIA_QUERY_PROJECTION
        FROM
          $tables
        WHERE
          $threadFilter AND
          ${galleryConditions(threadId)} AND
          (
            ${AttachmentTable.TRANSFER_STATE} IN (${AttachmentTable.TRANSFER_PROGRESS_DONE}, ${AttachmentTable.TRANSFER_PROGRESS_STARTED}) OR
            ${AttachmentTable.THUMBNAIL_RESTORE_STATE} = ${AttachmentTable.ThumbnailRestoreState.FINISHED.value}
          ) AND
          $MEDIA_QUERY_CONDITIONS
      """
  }

  data class MediaRecord(
    val attachment: DatabaseAttachment?,
    val recipientId: RecipientId,
    val threadRecipientId: RecipientId,
    val threadId: Long,
    val messageId: Long,
    val date: Long,
    val isOutgoing: Boolean,
    /** Set only when this row is the message's link preview rather than one of its other attachments. */
    val linkPreviewJson: String? = null,
    val dateReceived: Long = 0
  ) {

    val contentType: String?
      get() = attachment?.contentType

    /** The first entry in [linkPreviewJson], or null if there is none or it can't be read. */
    val linkPreview: LinkPreview? by lazy {
      val json = linkPreviewJson ?: return@lazy null
      try {
        JsonUtils.fromJsonArray(json, LinkPreview::class.java).firstOrNull()
      } catch (e: IOException) {
        Log.w(TAG, "Failed to parse link preview.", e)
        null
      }
    }

    companion object {
      @JvmStatic
      fun from(cursor: Cursor): MediaRecord {
        val linkPreviewIdx = cursor.getColumnIndex(MessageTable.LINK_PREVIEWS)
        val attachmentIdIdx = cursor.getColumnIndex(AttachmentTable.ID)
        val hasAttachment = attachmentIdIdx != -1 && !cursor.isNull(attachmentIdIdx)
        return MediaRecord(
          attachment = if (hasAttachment) SignalDatabase.attachments.getAttachment(cursor) else null,
          recipientId = RecipientId.from(cursor.requireLong(MessageTable.FROM_RECIPIENT_ID)),
          threadId = cursor.requireLong(MessageTable.THREAD_ID),
          threadRecipientId = RecipientId.from(cursor.requireLong(THREAD_RECIPIENT_ID)),
          messageId = cursor.requireLong(MEDIA_MESSAGE_ID),
          date = if (MessageTypes.isPushType(cursor.requireLong(MessageTable.TYPE))) {
            cursor.requireLong(MessageTable.DATE_SENT)
          } else {
            cursor.requireLong(MessageTable.DATE_RECEIVED)
          },
          isOutgoing = MessageTypes.isOutgoingMessageType(cursor.requireLong(MessageTable.TYPE)),
          linkPreviewJson = if (linkPreviewIdx != -1) cursor.getString(linkPreviewIdx) else null,
          dateReceived = cursor.requireLong(MessageTable.DATE_RECEIVED)
        )
      }
    }
  }

  enum class Sorting(order: String, projectedOrder: String) {
    Newest(
      """
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} DESC
      """,
      "${MessageTable.DATE_RECEIVED} DESC, $MEDIA_MESSAGE_ID DESC, ${AttachmentTable.DISPLAY_ORDER} DESC, ${AttachmentTable.ID} DESC"
    ),
    Oldest(
      """
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED} ASC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} ASC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} ASC
      """,
      "${MessageTable.DATE_RECEIVED} ASC, $MEDIA_MESSAGE_ID ASC, ${AttachmentTable.DISPLAY_ORDER} DESC NULLS FIRST, ${AttachmentTable.ID} ASC"
    ),
    Largest(
      """
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_SIZE} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} DESC
      """,
      "${AttachmentTable.DATA_SIZE} DESC, ${AttachmentTable.DISPLAY_ORDER} DESC, ${AttachmentTable.ID} DESC"
    );

    val orderBy: String = " ORDER BY $order"

    /** Orders by result column names rather than table columns, as a compound query requires. */
    val projectedOrderBy: String = " ORDER BY $projectedOrder"

    val isRelatedToFileSize: Boolean
      get() = this == Largest

    /**
     * A WHERE fragment against the projected column names matching rows strictly ahead of [anchor]
     * (received at [anchorDateReceived]) in this ordering. Ties are broken by attachment id.
     */
    fun projectedBeforePredicate(anchor: DatabaseAttachment, anchorDateReceived: Long): SqlUtil.Query {
      val dateReceived = anchorDateReceived.toString()
      val messageId = anchor.mmsId.toString()
      val displayOrder = anchor.displayOrder.toString()
      val id = anchor.attachmentId.id.toString()
      val dataSize = anchor.size.toString()

      return when (this) {
        Newest -> SqlUtil.Query(
          """(${MessageTable.DATE_RECEIVED} > ? OR (${MessageTable.DATE_RECEIVED} = ? AND
             (${AttachmentTable.MESSAGE_ID} > ? OR (${AttachmentTable.MESSAGE_ID} = ? AND (${AttachmentTable.DISPLAY_ORDER} > ? OR (${AttachmentTable.DISPLAY_ORDER} = ? AND ${AttachmentTable.ID} > ?))))))""",
          arrayOf(dateReceived, dateReceived, messageId, messageId, displayOrder, displayOrder, id)
        )
        Oldest -> SqlUtil.Query(
          """(${MessageTable.DATE_RECEIVED} < ? OR (${MessageTable.DATE_RECEIVED} = ? AND
             (${AttachmentTable.MESSAGE_ID} < ? OR (${AttachmentTable.MESSAGE_ID} = ? AND (${AttachmentTable.DISPLAY_ORDER} > ? OR (${AttachmentTable.DISPLAY_ORDER} = ? AND ${AttachmentTable.ID} < ?))))))""",
          arrayOf(dateReceived, dateReceived, messageId, messageId, displayOrder, displayOrder, id)
        )
        Largest -> SqlUtil.Query(
          "(${AttachmentTable.DATA_SIZE} > ? OR (${AttachmentTable.DATA_SIZE} = ? AND (${AttachmentTable.DISPLAY_ORDER} > ? OR (${AttachmentTable.DISPLAY_ORDER} = ? AND ${AttachmentTable.ID} > ?))))",
          arrayOf(dataSize, dataSize, displayOrder, displayOrder, id)
        )
      }
    }

    companion object {
      fun deserialize(code: Int): Sorting {
        return when (code) {
          0 -> Newest
          1 -> Oldest
          2 -> Largest
          else -> throw IllegalArgumentException("Unknown code: $code")
        }
      }
    }
  }

  /** An attachment's ordinal within the viewable gallery, plus the gallery's total row count. */
  data class GalleryPositionAndCount(val position: Int, val count: Int)

  @Immutable
  data class StorageBreakdown(
    val photoSize: Long,
    val videoSize: Long,
    val audioSize: Long,
    val documentSize: Long
  )
}

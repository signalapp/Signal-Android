package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import androidx.compose.runtime.Immutable
import org.signal.core.database.SqlUtil
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MediaUtil.SlideType

@SuppressLint("RecipientIdDatabaseReferenceUsage", "ThreadIdDatabaseReferenceUsage") // Not a real table, just a view
class MediaTable internal constructor(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(MediaTable::class)
    const val ALL_THREADS = -1
    private const val THREAD_RECIPIENT_ID = "THREAD_RECIPIENT_ID"
    private const val MEDIA_MESSAGE_ID = "media_message_id"

    /** Projection used for most queries */
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
        ${MessageTable.TABLE_NAME}.${MessageTable.LINK_PREVIEWS},
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

    /** Image and video attachments, excluding SVGs and link previews, that have local data or can be fetched. */
    private const val GALLERY_MEDIA_CONDITIONS = """
        (${AttachmentTable.DATA_FILE} IS NOT NULL OR (${AttachmentTable.CONTENT_TYPE} LIKE 'video/%' AND ${AttachmentTable.REMOTE_INCREMENTAL_DIGEST} IS NOT NULL) OR ${AttachmentTable.THUMBNAIL_FILE} IS NOT NULL OR ${AttachmentTable.TRANSFER_STATE} = ${AttachmentTable.TRANSFER_RESTORE_OFFLOADED}) AND
        ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'image/svg%' AND
        (${AttachmentTable.CONTENT_TYPE} LIKE 'image/%' OR ${AttachmentTable.CONTENT_TYPE} LIKE 'video/%') AND
        ${MessageTable.LINK_PREVIEWS} IS NULL
      """
  }

  @JvmOverloads
  fun getGalleryMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0): Cursor {
    var query = sorting.applyToQuery(
      """
      SELECT
        $MEDIA_QUERY_PROJECTION
      FROM
        $MEDIA_QUERY_TABLES
      WHERE
        ${threadSubqueryFilter(threadId)} AND
        $GALLERY_MEDIA_CONDITIONS AND
        $MEDIA_QUERY_CONDITIONS
      """
    )

    if (limit > 0) {
      query = "$query LIMIT $limit"
    }

    return readableDatabase.rawQuery(query, null)
  }

  /**
   * Gallery media for [threadId] narrowed to the rows the media viewer can actually render, in
   * [sorting] order. [limit] and [offset] page the results.
   */
  fun getViewableGalleryMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0, offset: Int = 0): List<MediaRecord> {
    var (query, args) = if (threadId == ALL_THREADS.toLong() || sorting == Sorting.Largest) {
      sorting.applyToQuery(viewableGalleryMediaQuery(threadSubqueryFilter(threadId))) to null
    } else {
      sorting.applyToQuery(viewableGalleryMediaQuery(threadFilter(threadId), tables = MEDIA_QUERY_TABLES_BY_THREAD_DATE)) to arrayOf(threadId.toString())
    }

    if (limit > 0) {
      query = "$query LIMIT $limit OFFSET $offset"
    }

    return readableDatabase
      .rawQuery(query, args)
      .readToList { MediaRecord.from(it) }
  }

  /** The [getViewableGalleryMediaForThread] row for [attachmentId], or null when it is absent or not viewable. */
  fun getViewableGalleryMediaForAttachment(threadId: Long, attachmentId: Long): MediaRecord? {
    val query = "${viewableGalleryMediaQuery(threadFilter(threadId))} AND ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} = ?"

    return readableDatabase
      .rawQuery(query, arrayOf(threadId.toString(), attachmentId.toString()))
      .readToSingleObject { MediaRecord.from(it) }
  }

  /** How many rows [getViewableGalleryMediaForThread] would return for the thread. */
  fun getViewableGalleryMediaCountForThread(threadId: Long): Int {
    val query = "SELECT COUNT(*) FROM (${viewableGalleryMediaQuery(threadSubqueryFilter(threadId))})"
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
      FROM (${viewableGalleryMediaQuery(threadSubqueryFilter(threadId))})
    """

    return readableDatabase.rawQuery(query, before.whereArgs).readToSingleObject { cursor ->
      GalleryPositionAndCount(position = cursor.getInt(0), count = cursor.getInt(1))
    }
  }

  @JvmOverloads
  fun getDocumentMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0): Cursor {
    var query = sorting.applyToQuery(
      """
      SELECT
        $MEDIA_QUERY_PROJECTION
      FROM
        $MEDIA_QUERY_TABLES
      WHERE
        ${threadSubqueryFilter(threadId)} AND
        (${AttachmentTable.DATA_FILE} IS NOT NULL OR ${AttachmentTable.TRANSFER_STATE} = ${AttachmentTable.TRANSFER_RESTORE_OFFLOADED}) AND
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
    )

    if (limit > 0) {
      query = "$query LIMIT $limit"
    }

    return readableDatabase.rawQuery(query, null)
  }

  @JvmOverloads
  fun getAudioMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0): Cursor {
    var query = sorting.applyToQuery(
      """
      SELECT
        $MEDIA_QUERY_PROJECTION
      FROM
        $MEDIA_QUERY_TABLES
      WHERE
        ${threadSubqueryFilter(threadId)} AND
        (${AttachmentTable.DATA_FILE} IS NOT NULL OR ${AttachmentTable.TRANSFER_STATE} = ${AttachmentTable.TRANSFER_RESTORE_OFFLOADED}) AND
        ${AttachmentTable.CONTENT_TYPE} LIKE 'audio/%' AND
        $MEDIA_QUERY_CONDITIONS
      """
    )

    if (limit > 0) {
      query = "$query LIMIT $limit"
    }

    return readableDatabase.rawQuery(query, null)
  }

  fun getLinkMediaForThread(threadId: Long, sorting: Sorting): Cursor {
    val orderBy = when (sorting) {
      Sorting.Newest -> " ORDER BY ${MessageTable.TABLE_NAME}.${MessageTable.DATE_SENT} DESC"
      Sorting.Oldest -> " ORDER BY ${MessageTable.TABLE_NAME}.${MessageTable.DATE_SENT} ASC"
      Sorting.Largest -> " ORDER BY ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_SIZE} DESC"
    }
    val query = linkMediaQuery(threadId) + orderBy
    val args = arrayOf(threadId.toString())
    return readableDatabase.rawQuery(query, args)
  }

  @JvmOverloads
  fun getAllMediaForThread(threadId: Long, sorting: Sorting, limit: Int = 0): Cursor {
    val allMediaSubquery = """
      SELECT
        $MEDIA_QUERY_PROJECTION
      FROM
        $MEDIA_QUERY_TABLES
      WHERE
        ${threadSubqueryFilter(threadId)} AND
        (${AttachmentTable.DATA_FILE} IS NOT NULL OR ${AttachmentTable.TRANSFER_STATE} = ${AttachmentTable.TRANSFER_RESTORE_OFFLOADED}) AND
        ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'text/x-signal-plain' AND
        ${MessageTable.LINK_PREVIEWS} IS NULL AND
        $MEDIA_QUERY_CONDITIONS
      """
    val linkSubquery = linkMediaQuery(threadId)

    val orderBy = when (sorting) {
      Sorting.Newest -> " ORDER BY $MEDIA_MESSAGE_ID DESC"
      Sorting.Oldest -> " ORDER BY $MEDIA_MESSAGE_ID ASC"
      Sorting.Largest -> " ORDER BY ${AttachmentTable.DATA_SIZE} DESC"
    }

    var query = "$allMediaSubquery UNION ALL $linkSubquery$orderBy"
    val args = arrayOf(threadId.toString())

    if (limit > 0) {
      query = "$query LIMIT $limit"
    }

    return readableDatabase.rawQuery(query, args)
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

  /** Returns all media for links (like, urls sent in chats). */
  private fun linkMediaQuery(threadId: Long): String {
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
          ${MessageTable.TABLE_NAME}
          LEFT JOIN ${AttachmentTable.TABLE_NAME} ON ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} = ${MessageTable.TABLE_NAME}.${MessageTable.ID}
            AND ${AttachmentTable.TABLE_NAME}.${AttachmentTable.QUOTE} = 0
            AND ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_ID} IS NULL
          LEFT JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} = ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID}
        WHERE
          ${threadFilter(threadId)} AND
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
  private fun viewableGalleryMediaQuery(threadFilter: String, tables: String = MEDIA_QUERY_TABLES): String {
    return """
        SELECT
          $MEDIA_QUERY_PROJECTION
        FROM
          $tables
        WHERE
          $threadFilter AND
          $GALLERY_MEDIA_CONDITIONS AND
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
    val linkPreviewJson: String? = null,
    val dateReceived: Long = 0
  ) {

    val contentType: String?
      get() = attachment?.contentType

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

  enum class Sorting(order: String) {
    Newest(
      """
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} DESC
      """
    ),
    Oldest(
      """
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED} ASC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MESSAGE_ID} ASC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} ASC
      """
    ),
    Largest(
      """
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA_SIZE} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER} DESC,
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ID} DESC
      """
    );

    private val postFix: String = " ORDER BY $order"

    val isRelatedToFileSize: Boolean
      get() = this == Largest

    fun applyToQuery(query: String): String {
      return query + postFix
    }

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

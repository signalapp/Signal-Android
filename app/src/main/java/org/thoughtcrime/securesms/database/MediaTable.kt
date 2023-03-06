package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.toSingleLine
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MediaUtil.SlideType

@SuppressLint("RecipientIdDatabaseReferenceUsage", "ThreadIdDatabaseReferenceUsage") // Not a real table, just a view
class MediaTable internal constructor(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper) {

  companion object {
    const val ALL_THREADS = -1
    private const val THREAD_RECIPIENT_ID = "THREAD_RECIPIENT_ID"
    private val BASE_MEDIA_QUERY = """
      SELECT 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ROW_ID} AS ${AttachmentTable.ROW_ID}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CONTENT_TYPE}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.UNIQUE_ID}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MMS_ID}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFER_STATE}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.SIZE}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FILE_NAME}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DATA}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CDN_NUMBER},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CONTENT_LOCATION},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CONTENT_DISPOSITION},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DIGEST}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.FAST_PREFLIGHT_ID},
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VOICE_NOTE}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.BORDERLESS}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VIDEO_GIF}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.WIDTH}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.HEIGHT}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.QUOTE}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_ID}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_PACK_KEY}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_ID}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.STICKER_EMOJI}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.VISUAL_HASH}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.TRANSFORM_PROPERTIES}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.CAPTION}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.NAME}, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.UPLOAD_TIMESTAMP}, 
        ${MessageTable.TABLE_NAME}.${MessageTable.TYPE}, 
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_SENT}, 
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED}, 
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_SERVER}, 
        ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID}, 
        ${MessageTable.TABLE_NAME}.${MessageTable.RECIPIENT_ID}, 
        ${ThreadTable.TABLE_NAME}.${ThreadTable.RECIPIENT_ID} as $THREAD_RECIPIENT_ID 
      FROM 
        ${AttachmentTable.TABLE_NAME} 
        LEFT JOIN ${MessageTable.TABLE_NAME} ON ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MMS_ID} = ${MessageTable.TABLE_NAME}.${MessageTable.ID} 
        LEFT JOIN ${ThreadTable.TABLE_NAME} ON ${ThreadTable.TABLE_NAME}.${ThreadTable.ID} = ${MessageTable.TABLE_NAME}.${MessageTable.THREAD_ID} 
      WHERE 
        ${AttachmentTable.MMS_ID} IN (
          SELECT ${MessageTable.ID} 
          FROM ${MessageTable.TABLE_NAME} 
          WHERE ${MessageTable.THREAD_ID} __EQUALITY__ ?
        ) AND 
        (%s) AND 
        ${MessageTable.VIEW_ONCE} = 0 AND 
        ${MessageTable.STORY_TYPE} = 0 AND 
        ${AttachmentTable.DATA} IS NOT NULL AND 
        (
          ${AttachmentTable.QUOTE} = 0 OR 
          (
            ${AttachmentTable.QUOTE} = 1 AND 
            ${AttachmentTable.DATA_HASH} IS NULL
          )
        ) AND 
        ${AttachmentTable.STICKER_PACK_ID} IS NULL AND 
        ${MessageTable.TABLE_NAME}.${MessageTable.RECIPIENT_ID} > 0 AND 
        $THREAD_RECIPIENT_ID > 0
      """.toSingleLine()

    private val UNIQUE_MEDIA_QUERY = """
        SELECT 
          MAX(${AttachmentTable.SIZE}) as ${AttachmentTable.SIZE}, 
          ${AttachmentTable.CONTENT_TYPE} 
        FROM 
          ${AttachmentTable.TABLE_NAME} 
        WHERE 
          ${AttachmentTable.STICKER_PACK_ID} IS NULL AND 
          ${AttachmentTable.TRANSFER_STATE} = ${AttachmentTable.TRANSFER_PROGRESS_DONE} 
        GROUP BY ${AttachmentTable.DATA}
      """.toSingleLine()

    private val GALLERY_MEDIA_QUERY = String.format(
      BASE_MEDIA_QUERY,
      """
        ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'image/svg%' AND 
        (${AttachmentTable.CONTENT_TYPE} LIKE 'image/%' OR ${AttachmentTable.CONTENT_TYPE} LIKE 'video/%')
      """.toSingleLine()
    )

    private val AUDIO_MEDIA_QUERY = String.format(BASE_MEDIA_QUERY, "${AttachmentTable.CONTENT_TYPE} LIKE 'audio/%'")
    private val ALL_MEDIA_QUERY = String.format(BASE_MEDIA_QUERY, "${AttachmentTable.CONTENT_TYPE} NOT LIKE 'text/x-signal-plain'")
    private val DOCUMENT_MEDIA_QUERY = String.format(
      BASE_MEDIA_QUERY,
      """
        ${AttachmentTable.CONTENT_TYPE} LIKE 'image/svg%' OR 
        (
          ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'image/%' AND 
          ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'video/%' AND 
          ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'audio/%' AND 
          ${AttachmentTable.CONTENT_TYPE} NOT LIKE 'text/x-signal-plain'
        )""".toSingleLine()
    )

    private fun applyEqualityOperator(threadId: Long, query: String): String {
      return query.replace("__EQUALITY__", if (threadId == ALL_THREADS.toLong()) "!=" else "=")
    }
  }

  fun getGalleryMediaForThread(threadId: Long, sorting: Sorting): Cursor {
    val query = sorting.applyToQuery(applyEqualityOperator(threadId, GALLERY_MEDIA_QUERY))
    val args = arrayOf(threadId.toString() + "")
    return readableDatabase.rawQuery(query, args)
  }

  fun getDocumentMediaForThread(threadId: Long, sorting: Sorting): Cursor {
    val query = sorting.applyToQuery(applyEqualityOperator(threadId, DOCUMENT_MEDIA_QUERY))
    val args = arrayOf(threadId.toString() + "")
    return readableDatabase.rawQuery(query, args)
  }

  fun getAudioMediaForThread(threadId: Long, sorting: Sorting): Cursor {
    val query = sorting.applyToQuery(applyEqualityOperator(threadId, AUDIO_MEDIA_QUERY))
    val args = arrayOf(threadId.toString() + "")
    return readableDatabase.rawQuery(query, args)
  }

  fun getAllMediaForThread(threadId: Long, sorting: Sorting): Cursor {
    val query = sorting.applyToQuery(applyEqualityOperator(threadId, ALL_MEDIA_QUERY))
    val args = arrayOf(threadId.toString() + "")
    return readableDatabase.rawQuery(query, args)
  }

  fun getStorageBreakdown(): StorageBreakdown {
    var photoSize: Long = 0
    var videoSize: Long = 0
    var audioSize: Long = 0
    var documentSize: Long = 0

    readableDatabase.rawQuery(UNIQUE_MEDIA_QUERY, null).use { cursor ->
      while (cursor.moveToNext()) {
        val size: Int = cursor.requireInt(AttachmentTable.SIZE)
        val type: String = cursor.requireNonNullString(AttachmentTable.CONTENT_TYPE)

        when (MediaUtil.getSlideTypeFromContentType(type)) {
          SlideType.GIF,
          SlideType.IMAGE,
          SlideType.MMS -> {
            photoSize += size.toLong()
          }
          SlideType.VIDEO -> {
            videoSize += size.toLong()
          }
          SlideType.AUDIO -> {
            audioSize += size.toLong()
          }
          SlideType.LONG_TEXT,
          SlideType.DOCUMENT -> {
            documentSize += size.toLong()
          }
          else -> {}
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

  data class MediaRecord constructor(
    val attachment: DatabaseAttachment?,
    val recipientId: RecipientId,
    val threadRecipientId: RecipientId,
    val threadId: Long,
    val date: Long,
    val isOutgoing: Boolean
  ) {

    val contentType: String
      get() = attachment!!.contentType

    companion object {
      @JvmStatic
      fun from(cursor: Cursor): MediaRecord {
        val attachments = SignalDatabase.attachments.getAttachments(cursor)

        return MediaRecord(
          attachment = if (attachments.isNotEmpty()) attachments[0] else null,
          recipientId = RecipientId.from(cursor.requireLong(MessageTable.RECIPIENT_ID)),
          threadId = cursor.requireLong(MessageTable.THREAD_ID),
          threadRecipientId = RecipientId.from(cursor.requireLong(THREAD_RECIPIENT_ID)),
          date = if (MessageTypes.isPushType(cursor.requireLong(MessageTable.TYPE))) {
            cursor.requireLong(MessageTable.DATE_SENT)
          } else {
            cursor.requireLong(MessageTable.DATE_RECEIVED)
          },
          isOutgoing = MessageTypes.isOutgoingMessageType(cursor.requireLong(MessageTable.TYPE))
        )
      }
    }
  }

  enum class Sorting(order: String) {
    Newest(
      """
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MMS_ID} DESC, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER} DESC, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ROW_ID} DESC
      """.toSingleLine()
    ),
    Oldest(
      """
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.MMS_ID} ASC, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER} DESC, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.ROW_ID} ASC
      """.toSingleLine()
    ),
    Largest(
      """
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.SIZE} DESC, 
        ${AttachmentTable.TABLE_NAME}.${AttachmentTable.DISPLAY_ORDER} DESC
      """.toSingleLine()
    );

    private val postFix: String

    init {
      postFix = " ORDER BY $order"
    }

    fun applyToQuery(query: String): String {
      return query + postFix
    }

    val isRelatedToFileSize: Boolean
      get() = this == Largest

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

  data class StorageBreakdown(
    val photoSize: Long,
    val videoSize: Long,
    val audioSize: Long,
    val documentSize: Long
  )
}

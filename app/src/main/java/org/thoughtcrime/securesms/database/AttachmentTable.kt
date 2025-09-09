/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaDataSource
import android.os.Parcelable
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.content.contentValuesOf
import com.bumptech.glide.Glide
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONException
import org.signal.core.util.Base64
import org.signal.core.util.SqlUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.copyTo
import org.signal.core.util.count
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
import org.signal.core.util.drain
import org.signal.core.util.exists
import org.signal.core.util.forEach
import org.signal.core.util.groupBy
import org.signal.core.util.isNull
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSet
import org.signal.core.util.readToSingleInt
import org.signal.core.util.readToSingleLong
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireIntOrNull
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullBlob
import org.signal.core.util.requireNonNullString
import org.signal.core.util.requireObject
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.toInt
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.attachments.ArchivedAttachment
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.Cdn
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.LocalStickerAttachment
import org.thoughtcrime.securesms.attachments.WallpaperAttachment
import org.thoughtcrime.securesms.audio.AudioHash
import org.thoughtcrime.securesms.backup.v2.exporters.ChatItemArchiveExporter
import org.thoughtcrime.securesms.backup.v2.proto.BackupDebugInfo
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.crypto.AttachmentSecret
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferState.COPY_PENDING
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferState.FINISHED
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferState.NONE
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferState.PERMANENT_FAILURE
import org.thoughtcrime.securesms.database.AttachmentTable.ArchiveTransferState.UPLOAD_IN_PROGRESS
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.DATA_FILE
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.DATA_HASH_END
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.PREUPLOAD_MESSAGE_ID
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.REMOTE_KEY
import org.thoughtcrime.securesms.database.AttachmentTable.Companion.TRANSFER_PROGRESS_DONE
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messages
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.threads
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob
import org.thoughtcrime.securesms.jobs.GenerateAudioWaveFormJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.DecryptableUri
import org.thoughtcrime.securesms.mms.MediaStream
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.BitmapDecodingException
import org.thoughtcrime.securesms.util.FileUtils
import org.thoughtcrime.securesms.util.ImageCompressionUtil
import org.thoughtcrime.securesms.util.JsonUtils.SaneJSONObject
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.StorageUtil
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.video.EncryptedMediaDataSource
import org.whispersystems.signalservice.api.attachment.AttachmentUploadResult
import org.whispersystems.signalservice.api.backup.MediaId
import org.whispersystems.signalservice.api.backup.MediaName
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.LinkedList
import java.util.Optional
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

class AttachmentTable(
  context: Context,
  databaseHelper: SignalDatabase,
  private val attachmentSecret: AttachmentSecret
) : DatabaseTable(context, databaseHelper) {

  companion object {
    val TAG = Log.tag(AttachmentTable::class.java)

    const val TABLE_NAME = "attachment"
    const val ID = "_id"
    const val MESSAGE_ID = "message_id"
    const val CONTENT_TYPE = "content_type"
    const val REMOTE_KEY = "remote_key"
    const val REMOTE_LOCATION = "remote_location"
    const val REMOTE_DIGEST = "remote_digest"
    const val REMOTE_INCREMENTAL_DIGEST = "remote_incremental_digest"
    const val REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE = "remote_incremental_digest_chunk_size"
    const val CDN_NUMBER = "cdn_number"
    const val TRANSFER_STATE = "transfer_state"
    const val TRANSFER_FILE = "transfer_file"
    const val DATA_FILE = "data_file"
    const val DATA_SIZE = "data_size"
    const val DATA_RANDOM = "data_random"
    const val DATA_HASH_START = "data_hash_start"
    const val DATA_HASH_END = "data_hash_end"
    const val THUMBNAIL_FILE = "thumbnail_file"
    const val THUMBNAIL_RANDOM = "thumbnail_random"
    const val FILE_NAME = "file_name"
    const val FAST_PREFLIGHT_ID = "fast_preflight_id"
    const val VOICE_NOTE = "voice_note"
    const val BORDERLESS = "borderless"
    const val VIDEO_GIF = "video_gif"
    const val QUOTE = "quote"
    const val WIDTH = "width"
    const val HEIGHT = "height"
    const val CAPTION = "caption"
    const val STICKER_PACK_ID = "sticker_pack_id"
    const val STICKER_PACK_KEY = "sticker_pack_key"
    const val STICKER_ID = "sticker_id"
    const val STICKER_EMOJI = "sticker_emoji"
    const val BLUR_HASH = "blur_hash"
    const val TRANSFORM_PROPERTIES = "transform_properties"
    const val DISPLAY_ORDER = "display_order"
    const val UPLOAD_TIMESTAMP = "upload_timestamp"
    const val ARCHIVE_CDN = "archive_cdn"
    const val ARCHIVE_TRANSFER_STATE = "archive_transfer_state"
    const val ARCHIVE_THUMBNAIL_TRANSFER_STATE = "archive_thumbnail_transfer_state"
    const val THUMBNAIL_RESTORE_STATE = "thumbnail_restore_state"
    const val ATTACHMENT_UUID = "attachment_uuid"
    const val OFFLOAD_RESTORED_AT = "offload_restored_at"
    const val QUOTE_TARGET_CONTENT_TYPE = "quote_target_content_type"

    const val ATTACHMENT_JSON_ALIAS = "attachment_json"

    private const val DIRECTORY = "parts"

    const val TRANSFER_PROGRESS_DONE = 0
    const val TRANSFER_PROGRESS_STARTED = 1
    const val TRANSFER_PROGRESS_PENDING = 2
    const val TRANSFER_PROGRESS_FAILED = 3
    const val TRANSFER_PROGRESS_PERMANENT_FAILURE = 4
    const val TRANSFER_NEEDS_RESTORE = 5
    const val TRANSFER_RESTORE_IN_PROGRESS = 6
    const val TRANSFER_RESTORE_OFFLOADED = 7
    const val PREUPLOAD_MESSAGE_ID: Long = -8675309
    const val WALLPAPER_MESSAGE_ID: Long = -8675308

    private val PROJECTION = arrayOf(
      ID,
      MESSAGE_ID,
      CONTENT_TYPE,
      REMOTE_KEY,
      REMOTE_LOCATION,
      REMOTE_DIGEST,
      REMOTE_INCREMENTAL_DIGEST,
      REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE,
      CDN_NUMBER,
      TRANSFER_STATE,
      TRANSFER_FILE,
      DATA_FILE,
      DATA_SIZE,
      DATA_RANDOM,
      FILE_NAME,
      FAST_PREFLIGHT_ID,
      VOICE_NOTE,
      BORDERLESS,
      VIDEO_GIF,
      QUOTE,
      QUOTE_TARGET_CONTENT_TYPE,
      WIDTH,
      HEIGHT,
      CAPTION,
      STICKER_PACK_ID,
      STICKER_PACK_KEY,
      STICKER_ID,
      STICKER_EMOJI,
      BLUR_HASH,
      TRANSFORM_PROPERTIES,
      DISPLAY_ORDER,
      UPLOAD_TIMESTAMP,
      DATA_HASH_START,
      DATA_HASH_END,
      ARCHIVE_CDN,
      THUMBNAIL_FILE,
      THUMBNAIL_RESTORE_STATE,
      ARCHIVE_TRANSFER_STATE,
      ATTACHMENT_UUID
    )

    @JvmField
    val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT,
        $MESSAGE_ID INTEGER,
        $CONTENT_TYPE TEXT,
        $REMOTE_KEY TEXT,
        $REMOTE_LOCATION TEXT,
        $REMOTE_DIGEST BLOB,
        $REMOTE_INCREMENTAL_DIGEST BLOB,
        $REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE INTEGER DEFAULT 0,
        $CDN_NUMBER INTEGER DEFAULT 0,
        $TRANSFER_STATE INTEGER,
        $TRANSFER_FILE TEXT DEFAULT NULL,
        $DATA_FILE TEXT,
        $DATA_SIZE INTEGER,
        $DATA_RANDOM BLOB,
        $FILE_NAME TEXT,
        $FAST_PREFLIGHT_ID TEXT,
        $VOICE_NOTE INTEGER DEFAULT 0,
        $BORDERLESS INTEGER DEFAULT 0,
        $VIDEO_GIF INTEGER DEFAULT 0,
        $QUOTE INTEGER DEFAULT 0,
        $WIDTH INTEGER DEFAULT 0,
        $HEIGHT INTEGER DEFAULT 0,
        $CAPTION TEXT DEFAULT NULL,
        $STICKER_PACK_ID TEXT DEFAULT NULL,
        $STICKER_PACK_KEY DEFAULT NULL,
        $STICKER_ID INTEGER DEFAULT -1,
        $STICKER_EMOJI STRING DEFAULT NULL,
        $BLUR_HASH TEXT DEFAULT NULL,
        $TRANSFORM_PROPERTIES TEXT DEFAULT NULL,
        $DISPLAY_ORDER INTEGER DEFAULT 0,
        $UPLOAD_TIMESTAMP INTEGER DEFAULT 0,
        $DATA_HASH_START TEXT DEFAULT NULL,
        $DATA_HASH_END TEXT DEFAULT NULL,
        $ARCHIVE_CDN INTEGER DEFAULT NULL,
        $ARCHIVE_TRANSFER_STATE INTEGER DEFAULT ${ArchiveTransferState.NONE.value},
        $THUMBNAIL_FILE TEXT DEFAULT NULL,
        $THUMBNAIL_RANDOM BLOB DEFAULT NULL,
        $THUMBNAIL_RESTORE_STATE INTEGER DEFAULT ${ThumbnailRestoreState.NONE.value},
        $ATTACHMENT_UUID TEXT DEFAULT NULL,
        $OFFLOAD_RESTORED_AT INTEGER DEFAULT 0,
        $QUOTE_TARGET_CONTENT_TYPE TEXT DEFAULT NULL,
        $ARCHIVE_THUMBNAIL_TRANSFER_STATE INTEGER DEFAULT ${ArchiveTransferState.NONE.value}
      )
      """

    private const val DATA_FILE_INDEX = "attachment_data_index"
    private const val DATA_HASH_REMOTE_KEY_INDEX = "attachment_data_hash_end_remote_key_index"

    @JvmField
    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS attachment_message_id_index ON $TABLE_NAME ($MESSAGE_ID);",
      "CREATE INDEX IF NOT EXISTS attachment_transfer_state_index ON $TABLE_NAME ($TRANSFER_STATE);",
      "CREATE INDEX IF NOT EXISTS attachment_sticker_pack_id_index ON $TABLE_NAME ($STICKER_PACK_ID);",
      "CREATE INDEX IF NOT EXISTS attachment_data_hash_start_index ON $TABLE_NAME ($DATA_HASH_START);",
      "CREATE INDEX IF NOT EXISTS $DATA_HASH_REMOTE_KEY_INDEX ON $TABLE_NAME ($DATA_HASH_END, $REMOTE_KEY);",
      "CREATE INDEX IF NOT EXISTS $DATA_FILE_INDEX ON $TABLE_NAME ($DATA_FILE);",
      "CREATE INDEX IF NOT EXISTS attachment_archive_transfer_state ON $TABLE_NAME ($ARCHIVE_TRANSFER_STATE);",
      "CREATE INDEX IF NOT EXISTS attachment_remote_digest_index ON $TABLE_NAME ($REMOTE_DIGEST);"
    )

    private val DATA_FILE_INFO_PROJECTION = arrayOf(
      ID, DATA_FILE, DATA_SIZE, DATA_RANDOM, DATA_HASH_START, DATA_HASH_END, TRANSFORM_PROPERTIES, UPLOAD_TIMESTAMP, ARCHIVE_CDN, ARCHIVE_TRANSFER_STATE, THUMBNAIL_FILE, THUMBNAIL_RESTORE_STATE, THUMBNAIL_RANDOM
    )

    private const val QUOTE_THUMBNAIL_DIMEN = 200
    private const val QUOTE_THUMBAIL_QUALITY = 50

    /** Indicates a legacy quote is pending transcoding to a new quote thumbnail. */
    const val QUOTE_PENDING_TRANSCODE = 2

    /** Indicates a quote from a free-tier backup restore is pending potential reconstruction from a parent attachment. */
    const val QUOTE_PENDING_RECONSTRUCTION = 3

    @JvmStatic
    @Throws(IOException::class)
    fun newDataFile(context: Context): File {
      val partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
      return PartFileProtector.protect { File.createTempFile("part", ".mms", partsDirectory) }
    }
  }

  @Throws(IOException::class)
  fun getAttachmentStream(attachmentId: AttachmentId, offset: Long): InputStream {
    return getDataStream(attachmentId, offset) ?: throw FileNotFoundException("No stream for: $attachmentId")
  }

  @Throws(IOException::class)
  fun getAttachmentStream(localArchivableAttachment: LocalArchivableAttachment): InputStream {
    return try {
      getDataStream(localArchivableAttachment.file, localArchivableAttachment.random, 0)
    } catch (e: FileNotFoundException) {
      throw IOException("No stream for: ${localArchivableAttachment.file}", e)
    } ?: throw IOException("No stream for: ${localArchivableAttachment.file}")
  }

  @Throws(IOException::class)
  fun getAttachmentThumbnailStream(attachmentId: AttachmentId, offset: Long): InputStream {
    return try {
      getThumbnailStream(attachmentId, offset)
    } catch (e: FileNotFoundException) {
      throw IOException("No stream for: $attachmentId", e)
    } ?: throw IOException("No stream for: $attachmentId")
  }

  /**
   * Returns a [File] for an attachment that has no [DATA_HASH_END] and is in the [TRANSFER_PROGRESS_DONE] state, if present.
   */
  fun getUnhashedDataFile(): Pair<File, AttachmentId>? {
    return readableDatabase
      .select(ID, DATA_FILE)
      .from(TABLE_NAME)
      .where("$DATA_FILE NOT NULL AND $DATA_HASH_END IS NULL AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE")
      .orderBy("$ID DESC")
      .limit(1)
      .run()
      .readToSingleObject {
        File(it.requireNonNullString(DATA_FILE)) to AttachmentId(it.requireLong(ID))
      }
  }

  /**
   * Sets the [DATA_HASH_END] for a given file. This is used to backfill the hash for attachments that were created before we started hashing them.
   * As a result, this will _not_ update the hashes on files that are not fully uploaded.
   */
  fun setHashForDataFile(file: File, hash: ByteArray) {
    writableDatabase.withinTransaction { db ->
      val hashEnd = Base64.encodeWithPadding(hash)

      val (existingFile: String?, existingSize: Long?, existingRandom: ByteArray?) = db.select(DATA_FILE, DATA_SIZE, DATA_RANDOM)
        .from(TABLE_NAME)
        .where("$DATA_HASH_END = ? AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND $DATA_FILE NOT NULL AND $DATA_FILE != ?", hashEnd, file.absolutePath)
        .limit(1)
        .run()
        .readToSingleObject {
          Triple(
            it.requireString(DATA_FILE),
            it.requireLong(DATA_SIZE),
            it.requireBlob(DATA_RANDOM)
          )
        } ?: Triple(null, null, null)

      if (existingFile != null) {
        Log.i(TAG, "[setHashForDataFile] Found that a different file has the same HASH_END. Using that one instead. Pre-existing file: $existingFile", true)

        val updateCount = writableDatabase
          .update(TABLE_NAME)
          .values(
            DATA_FILE to existingFile,
            DATA_HASH_END to hashEnd,
            DATA_SIZE to existingSize,
            DATA_RANDOM to existingRandom
          )
          .where("$DATA_FILE = ? AND $DATA_HASH_END IS NULL AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE", file.absolutePath)
          .run()

        Log.i(TAG, "[setHashForDataFile] Deduped $updateCount attachments.", true)

        val oldFileInUse = db.exists(TABLE_NAME).where("$DATA_FILE = ?", file.absolutePath).run()
        if (oldFileInUse) {
          Log.i(TAG, "[setHashForDataFile] Old file is still in use by some in-progress attachment.", true)
        } else {
          Log.i(TAG, "[setHashForDataFile] Deleting unused file: $file")
          if (!file.delete()) {
            Log.w(TAG, "Failed to delete duped file!")
          }
        }
      } else {
        val updateCount = writableDatabase
          .update(TABLE_NAME)
          .values(DATA_HASH_END to Base64.encodeWithPadding(hash))
          .where("$DATA_FILE = ? AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE", file.absolutePath)
          .run()

        Log.i(TAG, "[setHashForDataFile] Updated the HASH_END for $updateCount rows using file ${file.absolutePath}")
      }
    }
  }

  /**
   * Returns a cursor (with just the plaintextHash+remoteKey+archive_cdn) for all full-size attachments that are slated to be included in the current archive upload.
   * Used for snapshotting data in [BackupMediaSnapshotTable].
   */
  fun getFullSizeAttachmentsThatWillBeIncludedInArchive(): Cursor {
    return readableDatabase
      .select(DATA_HASH_END, REMOTE_KEY, ARCHIVE_CDN, QUOTE, CONTENT_TYPE)
      .from("$TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON $TABLE_NAME.$MESSAGE_ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID}")
      .where(buildAttachmentsThatNeedUploadQuery(transferStateFilter = "$ARCHIVE_TRANSFER_STATE != ${ArchiveTransferState.PERMANENT_FAILURE.value}"))
      .run()
  }

  /**
   * Returns a cursor (with just the plaintextHash+remoteKey+archive_cdn) for all thumbnail attachments that are slated to be included in the current archive upload.
   * Used for snapshotting data in [BackupMediaSnapshotTable].
   */
  fun getThumbnailAttachmentsThatWillBeIncludedInArchive(): Cursor {
    return readableDatabase
      .select(DATA_HASH_END, REMOTE_KEY, ARCHIVE_CDN, QUOTE, CONTENT_TYPE)
      .from("$TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON $TABLE_NAME.$MESSAGE_ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID}")
      .where(
        """
        ${buildAttachmentsThatNeedUploadQuery(transferStateFilter = "$ARCHIVE_THUMBNAIL_TRANSFER_STATE != ${ArchiveTransferState.PERMANENT_FAILURE.value}")} AND
        $QUOTE = 0 AND
        ($CONTENT_TYPE LIKE 'image/%' OR $CONTENT_TYPE LIKE 'video/%') AND
        $CONTENT_TYPE != 'image/svg+xml'
        """
      )
      .run()
  }

  fun hasData(attachmentId: AttachmentId): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ID = ? AND $DATA_FILE NOT NULL", attachmentId)
      .run()
  }

  fun getAttachment(attachmentId: AttachmentId): DatabaseAttachment? {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .run()
      .readToList { it.readAttachments() }
      .flatten()
      .firstOrNull()
  }

  fun getAttachmentIdByPlaintextHashAndRemoteKey(plaintextHash: ByteArray, remoteKey: ByteArray): AttachmentId? {
    return readableDatabase
      .select(ID)
      .from("$TABLE_NAME INDEXED BY $DATA_HASH_REMOTE_KEY_INDEX")
      .where("$DATA_HASH_END = ? AND $REMOTE_KEY = ?", Base64.encodeWithPadding(plaintextHash), Base64.encodeWithPadding(remoteKey))
      .run()
      .readToSingleObject { AttachmentId(it.requireLong(ID)) }
  }

  fun getAttachmentsForMessage(mmsId: Long): List<DatabaseAttachment> {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$MESSAGE_ID = ?", mmsId)
      .orderBy("$ID ASC")
      .run()
      .readToList { it.readAttachments() }
      .flatten()
  }

  @JvmOverloads
  fun getAttachmentsForMessages(mmsIds: Collection<Long?>, excludeTranscodingQuotes: Boolean = false): Map<Long, List<DatabaseAttachment>> {
    if (mmsIds.isEmpty()) {
      return emptyMap()
    }

    val query = SqlUtil.buildFastCollectionQuery(MESSAGE_ID, mmsIds)
    val where = if (excludeTranscodingQuotes) {
      "(${query.where}) AND $QUOTE != $QUOTE_PENDING_TRANSCODE"
    } else {
      query.where
    }

    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where(where, query.whereArgs)
      .orderBy("$ID ASC")
      .run()
      .groupBy { cursor ->
        val attachment = cursor.readAttachment()
        attachment.mmsId to attachment
      }
  }

  fun getMostRecentValidAttachmentUsingDataFile(dataFile: String): DatabaseAttachment? {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$DATA_FILE = ? AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE", dataFile)
      .orderBy("$ID DESC")
      .limit(1)
      .run()
      .readToSingleObject { it.readAttachment() }
  }

  fun hasAttachment(id: AttachmentId): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ID = ?", id.id)
      .run()
  }

  /**
   * Takes a list of attachment IDs and confirms they exist in the database.
   */
  fun hasAttachments(ids: List<AttachmentId>): Boolean {
    return ids.size == SqlUtil.buildCollectionQuery(ID, ids.map { it.id }).sumOf { query ->
      readableDatabase
        .count()
        .from(TABLE_NAME)
        .where(query.where, query.whereArgs)
        .run()
        .readToSingleInt(defaultValue = 0)
    }
  }

  fun getPendingAttachments(): List<DatabaseAttachment> {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$TRANSFER_STATE = ?", TRANSFER_PROGRESS_STARTED.toString())
      .run()
      .readToList { it.readAttachments() }
      .flatten()
  }

  fun getLocalArchivableAttachments(): List<LocalArchivableAttachment> {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$REMOTE_KEY IS NOT NULL AND $DATA_HASH_END IS NOT NULL AND $DATA_FILE IS NOT NULL")
      .orderBy("$ID DESC")
      .run()
      .readToList {
        LocalArchivableAttachment(
          file = File(it.requireNonNullString(DATA_FILE)),
          random = it.requireNonNullBlob(DATA_RANDOM),
          size = it.requireLong(DATA_SIZE),
          remoteKey = Base64.decode(it.requireNonNullString(REMOTE_KEY)),
          plaintextHash = Base64.decode(it.requireNonNullString(DATA_HASH_END))
        )
      }
  }

  /**
   * Grabs the last 30 days worth of restorable attachments with respect to the message's server timestamp,
   * up to the given batch size.
   */
  fun getLast30DaysOfRestorableAttachments(batchSize: Int): List<RestorableAttachment> {
    val thirtyDaysAgo = System.currentTimeMillis().milliseconds - 30.days
    return readableDatabase
      .select("$TABLE_NAME.$ID", MESSAGE_ID, DATA_SIZE, DATA_HASH_END, REMOTE_KEY, STICKER_PACK_ID)
      .from("$TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON ${MessageTable.TABLE_NAME}.${MessageTable.ID} = $TABLE_NAME.$MESSAGE_ID")
      .where("$TRANSFER_STATE = ? AND (${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED} >= ? OR $MESSAGE_ID = ?)", TRANSFER_NEEDS_RESTORE, thirtyDaysAgo.inWholeMilliseconds, WALLPAPER_MESSAGE_ID)
      .limit(batchSize)
      .orderBy("$TABLE_NAME.$ID DESC")
      .run()
      .readToList {
        RestorableAttachment(
          attachmentId = AttachmentId(it.requireLong(ID)),
          mmsId = it.requireLong(MESSAGE_ID),
          size = it.requireLong(DATA_SIZE),
          plaintextHash = it.requireString(DATA_HASH_END)?.let { hash -> Base64.decode(hash) },
          remoteKey = it.requireString(REMOTE_KEY)?.let { key -> Base64.decode(key) },
          stickerPackId = it.requireString(STICKER_PACK_ID)
        )
      }
  }

  /**
   * Grabs attachments outside of the last 30 days with respect to the message's server timestamp,
   * up to the given batch size.
   */
  fun getOlderRestorableAttachments(batchSize: Int): List<RestorableAttachment> {
    val thirtyDaysAgo = System.currentTimeMillis().milliseconds - 30.days
    return readableDatabase
      .select("$TABLE_NAME.$ID", MESSAGE_ID, DATA_SIZE, DATA_HASH_END, REMOTE_KEY, STICKER_PACK_ID)
      .from("$TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON ${MessageTable.TABLE_NAME}.${MessageTable.ID} = $TABLE_NAME.$MESSAGE_ID")
      .where("$TRANSFER_STATE = ? AND (${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED} < ? OR $MESSAGE_ID = ?)", TRANSFER_NEEDS_RESTORE, thirtyDaysAgo.inWholeMilliseconds, WALLPAPER_MESSAGE_ID)
      .limit(batchSize)
      .orderBy("$TABLE_NAME.$ID DESC")
      .run()
      .readToList {
        RestorableAttachment(
          attachmentId = AttachmentId(it.requireLong(ID)),
          mmsId = it.requireLong(MESSAGE_ID),
          size = it.requireLong(DATA_SIZE),
          plaintextHash = it.requireString(DATA_HASH_END)?.let { hash -> Base64.decode(hash) },
          remoteKey = it.requireString(REMOTE_KEY)?.let { key -> Base64.decode(key) },
          stickerPackId = it.requireString(STICKER_PACK_ID)
        )
      }
  }

  fun getRestorableAttachments(batchSize: Int): List<RestorableAttachment> {
    return readableDatabase
      .select(ID, MESSAGE_ID, DATA_SIZE, DATA_HASH_END, REMOTE_KEY, STICKER_PACK_ID)
      .from(TABLE_NAME)
      .where("$TRANSFER_STATE = ?", TRANSFER_NEEDS_RESTORE)
      .limit(batchSize)
      .orderBy("$ID DESC")
      .run()
      .readToList {
        RestorableAttachment(
          attachmentId = AttachmentId(it.requireLong(ID)),
          mmsId = it.requireLong(MESSAGE_ID),
          size = it.requireLong(DATA_SIZE),
          plaintextHash = it.requireString(DATA_HASH_END)?.let { hash -> Base64.decode(hash) },
          remoteKey = it.requireString(REMOTE_KEY)?.let { key -> Base64.decode(key) },
          stickerPackId = it.requireString(STICKER_PACK_ID)
        )
      }
  }

  fun getRestorableOptimizedAttachments(): List<RestorableAttachment> {
    return readableDatabase
      .select(ID, MESSAGE_ID, DATA_SIZE, DATA_HASH_END, REMOTE_KEY, STICKER_PACK_ID)
      .from(TABLE_NAME)
      .where("$TRANSFER_STATE = ? AND $DATA_HASH_END NOT NULL AND $REMOTE_KEY NOT NULL", TRANSFER_RESTORE_OFFLOADED)
      .orderBy("$ID DESC")
      .run()
      .readToList {
        RestorableAttachment(
          attachmentId = AttachmentId(it.requireLong(ID)),
          mmsId = it.requireLong(MESSAGE_ID),
          size = it.requireLong(DATA_SIZE),
          plaintextHash = it.requireString(DATA_HASH_END)?.let { hash -> Base64.decode(hash) },
          remoteKey = it.requireString(REMOTE_KEY)?.let { key -> Base64.decode(key) },
          stickerPackId = it.requireString(STICKER_PACK_ID)
        )
      }
  }

  fun getRemainingRestorableAttachmentSize(): Long {
    return readableDatabase
      .select("SUM($DATA_SIZE)")
      .from(TABLE_NAME)
      .where("$TRANSFER_STATE = ? OR $TRANSFER_STATE = ?", TRANSFER_NEEDS_RESTORE, TRANSFER_RESTORE_IN_PROGRESS)
      .run()
      .readToSingleLong()
  }

  fun getOptimizedMediaAttachmentSize(): Long {
    return readableDatabase
      .select("SUM($DATA_SIZE)")
      .from(TABLE_NAME)
      .where("$TRANSFER_STATE = ?", TRANSFER_RESTORE_OFFLOADED)
      .run()
      .readToSingleLong()
  }

  private fun getMessageDoesNotExpireWithinTimeoutClause(tablePrefix: String = MessageTable.TABLE_NAME): String {
    val messageHasExpiration = "$tablePrefix.${MessageTable.EXPIRES_IN} > 0"
    val messageExpiresInOneDayAfterViewing = "$messageHasExpiration AND  $tablePrefix.${MessageTable.EXPIRES_IN} < ${1.days.inWholeMilliseconds}"
    return "NOT $messageExpiresInOneDayAfterViewing"
  }

  /**
   * Finds all of the attachmentIds of attachments that need to be uploaded to the archive cdn.
   */
  fun getAttachmentsThatNeedArchiveUpload(): List<AttachmentId> {
    return readableDatabase
      .select("$TABLE_NAME.$ID")
      .from("$TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON $TABLE_NAME.$MESSAGE_ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID}")
      .where(buildAttachmentsThatNeedUploadQuery())
      .orderBy("$TABLE_NAME.$ID DESC")
      .run()
      .readToList { AttachmentId(it.requireLong(ID)) }
  }

  /**
   * At archive creation time, we need to ensure that all relevant attachments have populated [REMOTE_KEY]s.
   * This does that.
   */
  fun createRemoteKeyForAttachmentsThatNeedArchiveUpload(): CreateRemoteKeyResult {
    var totalCount = 0
    var notQuoteOrStickerDupeNotFoundCount = 0
    var notQuoteOrStickerDupeFoundCount = 0

    val missingKeys = readableDatabase
      .select(ID, DATA_FILE, QUOTE, STICKER_ID)
      .from(TABLE_NAME)
      .where(
        """
        $ARCHIVE_TRANSFER_STATE = ${ArchiveTransferState.NONE.value} AND
        $DATA_FILE NOT NULL AND
        $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND
        $REMOTE_KEY IS NULL
        """
      )
      .run()
      .readToList { Triple(AttachmentId(it.requireLong(ID)), it.requireBoolean(QUOTE), it.requireInt(STICKER_ID) >= 0) to it.requireNonNullString(DATA_FILE) }
      .groupBy({ (_, dataFile) -> dataFile }, { (record, _) -> record })

    missingKeys.forEach { dataFile, ids ->
      val duplicateAttachmentWithRemoteData = readableDatabase
        .select()
        .from(TABLE_NAME)
        .where("$DATA_FILE = ? AND $DATA_RANDOM NOT NULL AND $REMOTE_KEY NOT NULL AND $REMOTE_LOCATION NOT NULL AND $REMOTE_DIGEST NOT NULL", dataFile)
        .orderBy("$ID DESC")
        .limit(1)
        .run()
        .readToSingleObject { cursor ->
          val duplicateAttachment = cursor.readAttachment()
          val dataFileInfo = cursor.readDataFileInfo()!!

          duplicateAttachment to dataFileInfo
        }

      if (duplicateAttachmentWithRemoteData != null) {
        val (duplicateAttachment, duplicateAttachmentDataInfo) = duplicateAttachmentWithRemoteData

        ids.forEach { (attachmentId, isQuote, isSticker) ->
          Log.w(TAG, "[createRemoteKeyForAttachmentsThatNeedArchiveUpload][$attachmentId] Missing key but found same data file with remote data. Updating. isQuote:$isQuote isSticker:$isSticker")

          writableDatabase
            .update(TABLE_NAME)
            .values(
              REMOTE_KEY to duplicateAttachment.remoteKey,
              REMOTE_LOCATION to duplicateAttachment.remoteLocation,
              REMOTE_DIGEST to duplicateAttachment.remoteDigest,
              REMOTE_INCREMENTAL_DIGEST to duplicateAttachment.incrementalDigest?.takeIf { it.isNotEmpty() },
              REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE to duplicateAttachment.incrementalMacChunkSize,
              UPLOAD_TIMESTAMP to duplicateAttachment.uploadTimestamp,
              ARCHIVE_CDN to duplicateAttachment.archiveCdn,
              ARCHIVE_TRANSFER_STATE to duplicateAttachment.archiveTransferState.value,
              THUMBNAIL_FILE to duplicateAttachmentDataInfo.thumbnailFile,
              THUMBNAIL_RANDOM to duplicateAttachmentDataInfo.thumbnailRandom,
              THUMBNAIL_RESTORE_STATE to duplicateAttachmentDataInfo.thumbnailRestoreState
            )
            .where("$ID = ?", attachmentId.id)
            .run()

          if (!isQuote && !isSticker) {
            notQuoteOrStickerDupeFoundCount++
          }

          totalCount++
        }
      } else {
        ids.forEach { (attachmentId, isQuote, isSticker) ->
          Log.w(TAG, "[createRemoteKeyForAttachmentsThatNeedArchiveUpload][$attachmentId] Missing key. Generating. isQuote:$isQuote isSticker:$isSticker")

          val key = Util.getSecretBytes(64)

          writableDatabase.update(TABLE_NAME)
            .values(REMOTE_KEY to Base64.encodeWithPadding(key))
            .where("$ID = ?", attachmentId.id)
            .run()

          totalCount++

          if (!isQuote && !isSticker) {
            notQuoteOrStickerDupeNotFoundCount++
          }
        }
      }
    }

    return CreateRemoteKeyResult(totalCount, notQuoteOrStickerDupeNotFoundCount, notQuoteOrStickerDupeFoundCount)
  }

  /**
   * Clears incrementalMac's for any attachments that still need to be uploaded.
   * This is important because when we upload an attachment to the archive CDN, we'll be re-encrypting it, and so the incrementalMac will end up changing.
   * So we want to be sure that we don't write a potentially-invalid incrementalMac in the meantime.
   */
  fun clearIncrementalMacsForAttachmentsThatNeedArchiveUpload(): Int {
    return writableDatabase
      .update(TABLE_NAME)
      .values(
        REMOTE_INCREMENTAL_DIGEST to null,
        REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE to 0
      )
      .where(
        """
        $ARCHIVE_TRANSFER_STATE = ${ArchiveTransferState.NONE.value} AND
        $DATA_FILE NOT NULL AND
        $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND
        $REMOTE_INCREMENTAL_DIGEST NOT NULL
        """
      )
      .run()
  }

  /**
   * Similar to [getAttachmentsThatNeedArchiveUpload], but returns if the list would be non-null in a more efficient way.
   */
  fun doAnyAttachmentsNeedArchiveUpload(): Boolean {
    return readableDatabase
      .exists("$TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON $TABLE_NAME.$MESSAGE_ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID}")
      .where(buildAttachmentsThatNeedUploadQuery())
      .run()
  }

  /**
   * Returns whether or not there are thumbnails that need to be uploaded to the archive.
   */
  fun doAnyThumbnailsNeedArchiveUpload(): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where(
        """
        $ARCHIVE_TRANSFER_STATE = ${ArchiveTransferState.FINISHED.value} AND 
        $ARCHIVE_THUMBNAIL_TRANSFER_STATE = ${ArchiveTransferState.NONE.value} AND
        $QUOTE = 0 AND
        ($CONTENT_TYPE LIKE 'image/%' OR $CONTENT_TYPE LIKE 'video/%') AND
        $CONTENT_TYPE != 'image/svg+xml'
      """
      )
      .run()
  }

  /**
   * Returns whether or not there are thumbnails that need to be uploaded to the archive.
   */
  fun getThumbnailsThatNeedArchiveUpload(): List<AttachmentId> {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where(
        """
        $ARCHIVE_TRANSFER_STATE = ${ArchiveTransferState.FINISHED.value} AND 
        $ARCHIVE_THUMBNAIL_TRANSFER_STATE = ${ArchiveTransferState.NONE.value} AND
        $QUOTE = 0 AND
        ($CONTENT_TYPE LIKE 'image/%' OR $CONTENT_TYPE LIKE 'video/%') AND
        $CONTENT_TYPE != 'image/svg+xml'
      """
      )
      .run()
      .readToList { AttachmentId(it.requireLong(ID)) }
  }

  /**
   * Returns the current archive transfer state, if the attachment can be found.
   */
  fun getArchiveTransferState(id: AttachmentId): ArchiveTransferState? {
    return readableDatabase
      .select(ARCHIVE_TRANSFER_STATE)
      .from(TABLE_NAME)
      .where("$ID = ?", id.id)
      .run()
      .readToSingleObject { ArchiveTransferState.deserialize(it.requireInt(ARCHIVE_TRANSFER_STATE)) }
  }

  /**
   * Returns the current archive thumbnail transfer state, if the attachment can be found.
   */
  fun getArchiveThumbnailTransferState(id: AttachmentId): ArchiveTransferState? {
    return readableDatabase
      .select(ARCHIVE_THUMBNAIL_TRANSFER_STATE)
      .from(TABLE_NAME)
      .where("$ID = ?", id.id)
      .run()
      .readToSingleObject { ArchiveTransferState.deserialize(it.requireInt(ARCHIVE_THUMBNAIL_TRANSFER_STATE)) }
  }

  /**
   * Sets the archive transfer state for the given attachment and all other attachments that share the same data file.
   */
  fun setArchiveTransferState(id: AttachmentId, state: ArchiveTransferState) {
    writableDatabase.withinTransaction {
      val dataFile: String = readableDatabase
        .select(DATA_FILE)
        .from(TABLE_NAME)
        .where("$ID = ?", id.id)
        .run()
        .readToSingleObject { it.requireString(DATA_FILE) } ?: return@withinTransaction

      writableDatabase
        .update(TABLE_NAME)
        .values(ARCHIVE_TRANSFER_STATE to state.value)
        .where("$DATA_FILE = ?", dataFile)
        .run()
    }

    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
  }

  fun setArchiveThumbnailTransferState(id: AttachmentId, state: ArchiveTransferState) {
    check(state != ArchiveTransferState.COPY_PENDING) { "COPY_PENDING is not a valid transfer state for a thumbnail!" }

    writableDatabase.withinTransaction {
      val thumbnailFile: String? = readableDatabase
        .select(THUMBNAIL_FILE)
        .from(TABLE_NAME)
        .where("$ID = ?", id.id)
        .run()
        .readToSingleObject { it.requireString(THUMBNAIL_FILE) }

      if (thumbnailFile != null) {
        writableDatabase
          .update(TABLE_NAME)
          .values(ARCHIVE_THUMBNAIL_TRANSFER_STATE to state.value)
          .where("$THUMBNAIL_FILE = ?", thumbnailFile)
          .run()
      } else {
        writableDatabase
          .update(TABLE_NAME)
          .values(ARCHIVE_THUMBNAIL_TRANSFER_STATE to state.value)
          .where("$ID = ?", id)
          .run()
      }
    }
  }

  /**
   * Sets the archive transfer state for the given attachment and all other attachments that share the same data file iff
   * the row isn't already marked as a [ArchiveTransferState.PERMANENT_FAILURE].
   */
  fun setArchiveTransferStateFailure(id: AttachmentId, state: ArchiveTransferState) {
    writableDatabase.withinTransaction {
      val dataFile: String = readableDatabase
        .select(DATA_FILE)
        .from(TABLE_NAME)
        .where("$ID = ?", id.id)
        .run()
        .readToSingleObject { it.requireString(DATA_FILE) } ?: return@withinTransaction

      writableDatabase
        .update(TABLE_NAME)
        .values(ARCHIVE_TRANSFER_STATE to state.value)
        .where("$ARCHIVE_TRANSFER_STATE != ? AND $DATA_FILE = ?", ArchiveTransferState.PERMANENT_FAILURE.value, dataFile)
        .run()
    }

    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
  }

  /**
   * Sets the archive thumbnail transfer state for the given attachment and all other attachments that share the same thumbnail file iff
   * the row isn't already marked as a [ArchiveTransferState.PERMANENT_FAILURE].
   */
  fun setArchiveThumbnailTransferStateFailure(id: AttachmentId, state: ArchiveTransferState) {
    writableDatabase.withinTransaction {
      val thumbnailFile: String = readableDatabase
        .select(THUMBNAIL_FILE)
        .from(TABLE_NAME)
        .where("$ID = ?", id.id)
        .run()
        .readToSingleObject { it.requireString(THUMBNAIL_FILE) } ?: return@withinTransaction

      writableDatabase
        .update(TABLE_NAME)
        .values(ARCHIVE_THUMBNAIL_TRANSFER_STATE to state.value)
        .where("$ARCHIVE_THUMBNAIL_TRANSFER_STATE != ? AND $THUMBNAIL_FILE = ?", ArchiveTransferState.PERMANENT_FAILURE.value, thumbnailFile)
        .run()
    }

    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
  }

  /**
   * Resets the archive upload state by hash/key if we believe the attachment should have been uploaded already.
   */
  fun resetArchiveTransferStateByPlaintextHashAndRemoteKeyIfNecessary(plaintextHash: ByteArray, remoteKey: ByteArray): Boolean {
    return writableDatabase
      .update(TABLE_NAME)
      .values(
        ARCHIVE_TRANSFER_STATE to ArchiveTransferState.NONE.value,
        ARCHIVE_CDN to null
      )
      .where("$DATA_HASH_END = ? AND $REMOTE_KEY = ? AND $ARCHIVE_TRANSFER_STATE = ${ArchiveTransferState.FINISHED.value}", Base64.encodeWithPadding(plaintextHash), Base64.encodeWithPadding(remoteKey))
      .run() > 0
  }

  /**
   * Resets the archive thumbnail upload state by hash/key if we believe the thumbnail should have been uploaded already.
   */
  fun resetArchiveThumbnailTransferStateByPlaintextHashAndRemoteKeyIfNecessary(plaintextHash: ByteArray, remoteKey: ByteArray): Boolean {
    return writableDatabase
      .update(TABLE_NAME)
      .values(
        ARCHIVE_THUMBNAIL_TRANSFER_STATE to ArchiveTransferState.NONE.value
      )
      .where("$DATA_HASH_END = ? AND $REMOTE_KEY = ? AND $ARCHIVE_THUMBNAIL_TRANSFER_STATE = ${ArchiveTransferState.FINISHED.value}", Base64.encodeWithPadding(plaintextHash), Base64.encodeWithPadding(remoteKey))
      .run() > 0
  }

  /**
   * Sets the archive transfer state for the given attachment and all other attachments that share the same data file.
   */
  fun setArchiveTransferStateUnlessPermanentFailure(id: AttachmentId, state: ArchiveTransferState) {
    writableDatabase.withinTransaction {
      val dataFile: String = readableDatabase
        .select(DATA_FILE)
        .from(TABLE_NAME)
        .where("$ID = ?", id.id)
        .run()
        .readToSingleObject { it.requireString(DATA_FILE) } ?: return@withinTransaction

      writableDatabase
        .update(TABLE_NAME)
        .values(ARCHIVE_TRANSFER_STATE to state.value)
        .where("$DATA_FILE = ? AND $ARCHIVE_TRANSFER_STATE != ${ArchiveTransferState.PERMANENT_FAILURE.value}", dataFile)
        .run()
    }
  }

  /**
   * Resets the [ARCHIVE_TRANSFER_STATE] of any attachments that are currently in-progress of uploading.
   */
  fun clearArchiveTransferStateForInProgressItems(): Int {
    return writableDatabase
      .update(TABLE_NAME)
      .values(ARCHIVE_TRANSFER_STATE to ArchiveTransferState.NONE.value)
      .where("$ARCHIVE_TRANSFER_STATE IN (${ArchiveTransferState.UPLOAD_IN_PROGRESS.value}, ${ArchiveTransferState.COPY_PENDING.value}, ${ArchiveTransferState.TEMPORARY_FAILURE.value})")
      .run()
  }

  /**
   * Marks eligible attachments as offloaded based on their received at timestamp, their last restore time,
   * presence of thumbnail if media, and the full file being available in the archive.
   *
   * Marking offloaded only clears the strong references to the on disk file and clears other local file data like hashes.
   * Another operation must run to actually delete the data from disk. See [deleteAbandonedAttachmentFiles].
   */
  fun markEligibleAttachmentsAsOptimized() {
    val now = System.currentTimeMillis()

    val subSelect = """
      SELECT $TABLE_NAME.$ID 
      FROM $TABLE_NAME 
      INNER JOIN ${MessageTable.TABLE_NAME} ON ${MessageTable.TABLE_NAME}.${MessageTable.ID} = $TABLE_NAME.$MESSAGE_ID 
      WHERE
      (
        $TABLE_NAME.$OFFLOAD_RESTORED_AT < ${now - 7.days.inWholeMilliseconds} AND
        $TABLE_NAME.$TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND
        $TABLE_NAME.$ARCHIVE_TRANSFER_STATE = ${ArchiveTransferState.FINISHED.value} AND
        (
          $TABLE_NAME.$THUMBNAIL_FILE IS NOT NULL OR 
          NOT ($TABLE_NAME.$CONTENT_TYPE like 'image/%' OR $TABLE_NAME.$CONTENT_TYPE like 'video/%')
        ) AND
        $TABLE_NAME.$DATA_FILE IS NOT NULL
      )
      AND
      (
        ${MessageTable.TABLE_NAME}.${MessageTable.DATE_RECEIVED} < ${now - 30.days.inWholeMilliseconds}
      )
    """

    writableDatabase
      .update(TABLE_NAME)
      .values(
        TRANSFER_STATE to TRANSFER_RESTORE_OFFLOADED,
        DATA_FILE to null,
        DATA_RANDOM to null,
        TRANSFORM_PROPERTIES to null,
        DATA_HASH_START to null,
        DATA_HASH_END to null,
        OFFLOAD_RESTORED_AT to 0
      )
      .where("$ID in ($subSelect)")
      .run()
  }

  /**
   * Returns sum of the file sizes of attachments that are not fully uploaded to the archive CDN.
   *
   * Should be the same or subset of that returned by [getAttachmentsThatNeedArchiveUpload].
   */
  fun getPendingArchiveUploadBytes(): Long {
    val archiveTransferStateFilter = "$ARCHIVE_TRANSFER_STATE NOT IN (${ArchiveTransferState.FINISHED.value}, ${ArchiveTransferState.PERMANENT_FAILURE.value})"
    return readableDatabase
      .rawQuery(
        """
          SELECT SUM($DATA_SIZE)
          FROM (
            SELECT DISTINCT $DATA_HASH_END, $REMOTE_KEY, $DATA_SIZE
            FROM $TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON $TABLE_NAME.$MESSAGE_ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID}
            WHERE ${buildAttachmentsThatNeedUploadQuery(archiveTransferStateFilter)}
          )
        """.trimIndent()
      )
      .readToSingleLong()
  }

  /**
   * Clears out the incrementalMac for the specified [attachmentId], as well as any other attachments that share the same ([remoteKey], [plaintextHash]) pair (if present).
   */
  fun clearIncrementalMacsForAttachmentAndAnyDuplicates(attachmentId: AttachmentId, remoteKey: String?, plaintextHash: String?) {
    val query = if (remoteKey != null && plaintextHash != null) {
      SqlUtil.buildQuery("$ID = ? OR ($REMOTE_KEY = ?  AND $DATA_HASH_END = ?)", attachmentId, remoteKey, plaintextHash)
    } else {
      SqlUtil.buildQuery("$ID = ?", attachmentId)
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(
        REMOTE_INCREMENTAL_DIGEST to null,
        REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE to 0
      )
      .where(query.where, query.whereArgs)
      .run()
  }

  fun deleteAttachmentsForMessage(mmsId: Long): Boolean {
    Log.d(TAG, "[deleteAttachmentsForMessage] mmsId: $mmsId")

    val filePathsToDelete: MutableSet<String> = mutableSetOf()
    val contentTypesToDelete: MutableSet<String> = mutableSetOf()

    val deleteCount = writableDatabase.withinTransaction { db ->
      db.select(DATA_FILE, CONTENT_TYPE, ID)
        .from(TABLE_NAME)
        .where("$MESSAGE_ID = ?", mmsId)
        .run()
        .forEach { cursor ->
          val attachmentId = AttachmentId(cursor.requireLong(ID))

          AppDependencies.jobManager.cancelAllInQueue(AttachmentDownloadJob.constructQueueString(attachmentId))

          val filePath = cursor.requireString(DATA_FILE)
          val contentType = cursor.requireString(CONTENT_TYPE)

          if (filePath != null && isSafeToDeleteDataFile(filePath, attachmentId)) {
            filePathsToDelete += filePath
            contentType?.let { contentTypesToDelete += it }
          }
        }

      val deleteCount = db.delete(TABLE_NAME)
        .where("$MESSAGE_ID = ?", mmsId)
        .run()

      AppDependencies.databaseObserver.notifyAttachmentDeletedObservers()

      deleteCount
    }

    deleteDataFiles(filePathsToDelete, contentTypesToDelete)

    return deleteCount > 0
  }

  /**
   * Deletes all attachments with an ID of [PREUPLOAD_MESSAGE_ID]. These represent
   * attachments that were pre-uploaded and haven't been assigned to a message. This should only be
   * done when you *know* that all attachments *should* be assigned a real mmsId. For instance, when
   * the app starts. Otherwise you could delete attachments that are legitimately being
   * pre-uploaded.
   */
  fun deleteAbandonedPreuploadedAttachments(): Int {
    var count = 0

    writableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$MESSAGE_ID = ?", PREUPLOAD_MESSAGE_ID)
      .run()
      .forEach { cursor ->
        val id = AttachmentId(cursor.requireLong(ID))
        deleteAttachment(id)
        count++
      }

    return count
  }

  fun deleteAttachmentFilesForViewOnceMessage(messageId: Long) {
    Log.d(TAG, "[deleteAttachmentFilesForViewOnceMessage] messageId: $messageId")

    val filePathsToDelete: MutableSet<String> = mutableSetOf()
    val contentTypesToDelete: MutableSet<String> = mutableSetOf()

    writableDatabase.withinTransaction { db ->
      db.select(DATA_FILE, CONTENT_TYPE, ID)
        .from(TABLE_NAME)
        .where("$MESSAGE_ID = ?", messageId)
        .run()
        .forEach { cursor ->
          val filePath = cursor.requireString(DATA_FILE)
          val contentType = cursor.requireString(CONTENT_TYPE)
          val id = AttachmentId(cursor.requireLong(ID))

          if (filePath != null && isSafeToDeleteDataFile(filePath, id)) {
            filePathsToDelete += filePath
            contentType?.let { contentTypesToDelete += it }
          }
        }

      db.update(TABLE_NAME)
        .values(
          DATA_FILE to null,
          DATA_RANDOM to null,
          DATA_HASH_START to null,
          DATA_HASH_END to null,
          REMOTE_KEY to null,
          REMOTE_DIGEST to null,
          REMOTE_INCREMENTAL_DIGEST to null,
          REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE to 0,
          THUMBNAIL_FILE to null,
          THUMBNAIL_RANDOM to null,
          FILE_NAME to null,
          CAPTION to null,
          DATA_SIZE to 0,
          WIDTH to 0,
          HEIGHT to 0,
          TRANSFER_STATE to TRANSFER_PROGRESS_DONE,
          ARCHIVE_TRANSFER_STATE to ArchiveTransferState.NONE.value,
          BLUR_HASH to null,
          CONTENT_TYPE to MediaUtil.VIEW_ONCE
        )
        .where("$MESSAGE_ID = ?", messageId)
        .run()

      AppDependencies.databaseObserver.notifyAttachmentDeletedObservers()

      val threadId = messages.getThreadIdForMessage(messageId)
      if (threadId > 0) {
        notifyConversationListeners(threadId)
      }
    }

    deleteDataFiles(filePathsToDelete, contentTypesToDelete)
  }

  fun deleteAttachment(id: AttachmentId) {
    Log.d(TAG, "[deleteAttachment] attachmentId: $id")

    val filePathsToDelete = mutableSetOf<String>()
    val contentTypesToDelete = mutableSetOf<String>()

    writableDatabase.withinTransaction { db ->
      db.select(DATA_FILE, CONTENT_TYPE)
        .from(TABLE_NAME)
        .where("$ID = ?", id.id)
        .run()
        .use { cursor ->
          if (!cursor.moveToFirst()) {
            Log.w(TAG, "Tried to delete an attachment, but it didn't exist.")
            return@withinTransaction
          }

          val filePath = cursor.requireString(DATA_FILE)
          val contentType = cursor.requireString(CONTENT_TYPE)

          db.delete(TABLE_NAME)
            .where("$ID = ?", id.id)
            .run()

          if (filePath != null && isSafeToDeleteDataFile(filePath, id)) {
            filePathsToDelete += filePath
            contentType?.let { contentTypesToDelete += it }
          }

          AppDependencies.databaseObserver.notifyAttachmentDeletedObservers()
        }
    }

    deleteDataFiles(filePathsToDelete, contentTypesToDelete)
  }

  fun deleteAttachments(toDelete: List<SyncAttachmentId>): List<SyncMessageId> {
    val unhandled = mutableListOf<SyncMessageId>()
    for (syncAttachmentId in toDelete) {
      val messageId = messages.getMessageIdOrNull(syncAttachmentId.syncMessageId)
      if (messageId != null) {
        val attachments = readableDatabase
          .select(ID, ATTACHMENT_UUID, REMOTE_DIGEST, DATA_HASH_END)
          .from(TABLE_NAME)
          .where("$MESSAGE_ID = ?", messageId)
          .run()
          .readToList {
            SyncAttachment(
              id = AttachmentId(it.requireLong(ID)),
              uuid = UuidUtil.parseOrNull(it.requireString(ATTACHMENT_UUID)),
              digest = it.requireBlob(REMOTE_DIGEST),
              plaintextHash = it.requireString(DATA_HASH_END)
            )
          }

        val byUuid: SyncAttachment? by lazy { attachments.firstOrNull { it.uuid != null && it.uuid == syncAttachmentId.uuid } }
        val byDigest: SyncAttachment? by lazy { attachments.firstOrNull { it.digest != null && it.digest.contentEquals(syncAttachmentId.digest) } }
        val byPlaintext: SyncAttachment? by lazy { attachments.firstOrNull { it.plaintextHash != null && it.plaintextHash == syncAttachmentId.plaintextHash } }

        val attachmentToDelete = (byUuid ?: byDigest ?: byPlaintext)?.id
        if (attachmentToDelete != null) {
          if (attachments.size == 1) {
            messages.deleteMessage(messageId)
          } else {
            deleteAttachment(attachmentToDelete)
          }
        } else {
          Log.i(TAG, "Unable to locate sync attachment to delete for message:$messageId")
        }
      } else {
        unhandled += syncAttachmentId.syncMessageId
      }
    }

    return unhandled
  }

  fun trimAllAbandonedAttachments() {
    val deleteCount = writableDatabase
      .delete(TABLE_NAME)
      .where("$MESSAGE_ID != $PREUPLOAD_MESSAGE_ID AND $MESSAGE_ID != $WALLPAPER_MESSAGE_ID AND $MESSAGE_ID NOT IN (SELECT ${MessageTable.ID} FROM ${MessageTable.TABLE_NAME})")
      .run()

    if (deleteCount > 0) {
      Log.i(TAG, "Trimmed $deleteCount abandoned attachments.")
    }
  }

  fun deleteAbandonedAttachmentFiles(): Int {
    val diskFiles = context.getDir(DIRECTORY, Context.MODE_PRIVATE).listFiles() ?: return 0

    val filesOnDisk: Set<String> = diskFiles
      .filter { file: File -> !PartFileProtector.isProtected(file) }
      .map { file: File -> file.absolutePath }
      .toSet()

    val filesInDb: MutableSet<String> = HashSet(filesOnDisk.size)

    readableDatabase
      .select(DATA_FILE, THUMBNAIL_FILE)
      .from(TABLE_NAME)
      .run()
      .forEach { cursor ->
        cursor.requireString(DATA_FILE)?.let { filesInDb += it }
        cursor.requireString(THUMBNAIL_FILE)?.let { filesInDb += it }
      }

    filesInDb += SignalDatabase.stickers.getAllStickerFiles()

    val onDiskButNotInDatabase: Set<String> = filesOnDisk - filesInDb

    for (filePath in onDiskButNotInDatabase) {
      val success = File(filePath).delete()
      if (!success) {
        Log.w(TAG, "[deleteAbandonedAttachmentFiles] Failed to delete attachment file. $filePath")
      }
    }

    return onDiskButNotInDatabase.size
  }

  /**
   * Removes all references to the provided [DATA_FILE] from all attachments.
   * Only do this if the file is known to not exist or has some other critical problem!
   */
  fun clearUsagesOfDataFile(file: File) {
    val updateCount = writableDatabase
      .update(TABLE_NAME)
      .values(DATA_FILE to null)
      .where("$DATA_FILE = ?", file.absolutePath)
      .run()

    Log.i(TAG, "[clearUsagesOfFile] Cleared $updateCount usages of $file", true)
  }

  /**
   * Indicates that, for whatever reason, a hash could not be calculated for the file in question.
   * We put in a "bad hash" that will never match anything else so that we don't attempt to backfill it in the future.
   */
  fun markDataFileAsUnhashable(file: File) {
    val updateCount = writableDatabase
      .update(TABLE_NAME)
      .values(DATA_HASH_END to "UNHASHABLE-${UUID.randomUUID()}")
      .where("$DATA_FILE = ? AND $DATA_HASH_END IS NULL AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE", file.absolutePath)
      .run()

    Log.i(TAG, "[markDataFileAsUnhashable] Marked $updateCount attachments as unhashable with file: ${file.absolutePath}", true)
  }

  fun deleteAllAttachments() {
    Log.d(TAG, "[deleteAllAttachments]")

    writableDatabase.deleteAll(TABLE_NAME)

    FileUtils.deleteDirectoryContents(context.getDir(DIRECTORY, Context.MODE_PRIVATE))

    AppDependencies.databaseObserver.notifyAttachmentDeletedObservers()
  }

  fun setTransferState(messageId: Long, attachmentId: AttachmentId, transferState: Int) {
    writableDatabase
      .update(TABLE_NAME)
      .values(TRANSFER_STATE to transferState)
      .where("$ID = ?", attachmentId.id)
      .run()

    val threadId = messages.getThreadIdForMessage(messageId)
    notifyConversationListeners(threadId)
  }

  fun setTransferProgressFailed(attachmentId: AttachmentId, mmsId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(TRANSFER_STATE to TRANSFER_PROGRESS_FAILED)
      .where("$ID = ? AND $TRANSFER_STATE < $TRANSFER_PROGRESS_PERMANENT_FAILURE", attachmentId.id)
      .run()

    notifyConversationListeners(messages.getThreadIdForMessage(mmsId))
  }

  fun setThumbnailRestoreProgressFailed(attachmentId: AttachmentId, mmsId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(THUMBNAIL_RESTORE_STATE to ThumbnailRestoreState.PERMANENT_FAILURE.value)
      .where("$ID = ? AND $THUMBNAIL_RESTORE_STATE != ?", attachmentId.id, ThumbnailRestoreState.FINISHED)
      .run()

    notifyConversationListeners(messages.getThreadIdForMessage(mmsId))
  }

  fun setTransferProgressPermanentFailure(attachmentId: AttachmentId, mmsId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(TRANSFER_STATE to TRANSFER_PROGRESS_PERMANENT_FAILURE)
      .where("$ID = ?", attachmentId.id)
      .run()

    notifyConversationListeners(messages.getThreadIdForMessage(mmsId))
  }

  fun setThumbnailRestoreState(thumbnailAttachmentId: AttachmentId, thumbnailRestoreState: ThumbnailRestoreState) {
    setThumbnailRestoreState(listOf(thumbnailAttachmentId), thumbnailRestoreState)
  }

  fun setThumbnailRestoreState(thumbnailAttachmentIds: List<AttachmentId>, thumbnailRestoreState: ThumbnailRestoreState) {
    val prefix: String = when (thumbnailRestoreState) {
      ThumbnailRestoreState.IN_PROGRESS -> {
        "($THUMBNAIL_RESTORE_STATE = ${ThumbnailRestoreState.NEEDS_RESTORE.value} OR $THUMBNAIL_RESTORE_STATE = ${ThumbnailRestoreState.IN_PROGRESS.value}) AND"
      }

      else -> ""
    }

    val setQueries = SqlUtil.buildCollectionQuery(
      column = ID,
      values = thumbnailAttachmentIds.map { it.id },
      prefix = prefix
    )

    writableDatabase.withinTransaction {
      setQueries.forEach { query ->
        writableDatabase
          .update(TABLE_NAME)
          .values(THUMBNAIL_RESTORE_STATE to thumbnailRestoreState.value)
          .where(query.where, query.whereArgs)
          .run()
      }
    }
  }

  fun setRestoreTransferState(attachmentId: AttachmentId, state: Int) {
    setRestoreTransferState(listOf(attachmentId), state)
  }

  fun setRestoreTransferState(restorableAttachments: Collection<AttachmentId>, state: Int) {
    val prefix = when (state) {
      TRANSFER_RESTORE_OFFLOADED -> "$TRANSFER_STATE != $TRANSFER_PROGRESS_PERMANENT_FAILURE AND"
      TRANSFER_RESTORE_IN_PROGRESS -> "($TRANSFER_STATE = $TRANSFER_NEEDS_RESTORE OR $TRANSFER_STATE = $TRANSFER_RESTORE_OFFLOADED) AND"
      TRANSFER_PROGRESS_FAILED -> "$TRANSFER_STATE != $TRANSFER_PROGRESS_PERMANENT_FAILURE AND"
      else -> ""
    }

    val setQueries = SqlUtil.buildCollectionQuery(
      column = ID,
      values = restorableAttachments,
      prefix = prefix
    )

    writableDatabase.withinTransaction {
      setQueries.forEach { query ->
        writableDatabase
          .update(TABLE_NAME)
          .values(TRANSFER_STATE to state)
          .where(query.where, query.whereArgs)
          .run()
      }
    }
  }

  /**
   * Updates the attachment (and all attachments that share the same data file) with a new length.
   */
  fun updateAttachmentLength(attachmentId: AttachmentId, length: Long) {
    val dataFile = getDataFileInfo(attachmentId)
    if (dataFile == null) {
      Log.w(TAG, "[$attachmentId] Failed to find data file!")
      return
    }

    writableDatabase
      .update(TABLE_NAME)
      .values(DATA_SIZE to length)
      .where("$DATA_FILE = ?", dataFile.file.absolutePath)
      .run()
  }

  /**
   * When we find out about a new inbound attachment pointer, we insert a row for it that contains all the info we need to download it via [insertAttachmentWithData].
   * Later, we download the data for that pointer. Call this method once you have the data to associate it with the attachment. At this point, it is assumed
   * that the content of the attachment will never change.
   */
  @Throws(MmsException::class)
  fun finalizeAttachmentAfterDownload(mmsId: Long, attachmentId: AttachmentId, inputStream: InputStream, offloadRestoredAt: Duration? = null, archiveRestore: Boolean = false) {
    Log.i(TAG, "[finalizeAttachmentAfterDownload] Finalizing downloaded data for $attachmentId. (MessageId: $mmsId, $attachmentId)")

    val existingPlaceholder: DatabaseAttachment = getAttachment(attachmentId) ?: throw MmsException("No attachment found for id: $attachmentId")

    val fileWriteResult: DataFileWriteResult = writeToDataFile(newDataFile(context), inputStream, TransformProperties.empty(), closeInputStream = false)

    val foundDuplicate = writableDatabase.withinTransaction { db ->
      // We can look and see if we have any exact matches on hash_ends and dedupe the file if we see one.
      // We don't look at hash_start here because that could result in us matching on a file that got compressed down to something smaller, effectively lowering
      // the quality of the attachment we received.
      val hashMatch: DataFileInfo? = readableDatabase
        .select(*DATA_FILE_INFO_PROJECTION)
        .from(TABLE_NAME)
        .where("$DATA_HASH_END = ? AND $DATA_HASH_END NOT NULL AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND $DATA_FILE NOT NULL", fileWriteResult.hash)
        .run()
        .readToList { it.readDataFileInfo() }
        .firstOrNull()

      val values = ContentValues()

      if (hashMatch != null) {
        Log.i(TAG, "[finalizeAttachmentAfterDownload] Found that ${hashMatch.id} has the same DATA_HASH_END. Deduping. (MessageId: $mmsId, $attachmentId)")
        values.put(DATA_FILE, hashMatch.file.absolutePath)
        values.put(DATA_SIZE, hashMatch.length)
        values.put(DATA_RANDOM, hashMatch.random)
        values.put(DATA_HASH_START, hashMatch.hashEnd)
        values.put(DATA_HASH_END, hashMatch.hashEnd)
        if (archiveRestore) {
          // We aren't getting an updated remote key/mediaName when restoring, can reuse
          values.put(ARCHIVE_CDN, hashMatch.archiveCdn)
          values.put(ARCHIVE_TRANSFER_STATE, hashMatch.archiveTransferState)
        } else {
          // Clear archive cdn and transfer state so it can be re-archived with the new remote key/mediaName
          values.putNull(ARCHIVE_CDN)
          values.put(ARCHIVE_TRANSFER_STATE, ArchiveTransferState.NONE.value)
        }
      } else {
        values.put(DATA_FILE, fileWriteResult.file.absolutePath)
        values.put(DATA_SIZE, fileWriteResult.length)
        values.put(DATA_RANDOM, fileWriteResult.random)
        values.put(DATA_HASH_START, fileWriteResult.hash)
        values.put(DATA_HASH_END, fileWriteResult.hash)
      }

      val visualHashString = existingPlaceholder.getVisualHashStringOrNull()
      if (visualHashString != null) {
        values.put(BLUR_HASH, visualHashString)
      }

      values.put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE)
      values.put(TRANSFER_FILE, null as String?)
      values.put(TRANSFORM_PROPERTIES, TransformProperties.forSkipTransform().serialize())
      values.put(REMOTE_LOCATION, existingPlaceholder.remoteLocation)
      values.put(CDN_NUMBER, existingPlaceholder.cdn.serialize())
      values.put(REMOTE_KEY, existingPlaceholder.remoteKey!!)
      values.put(REMOTE_DIGEST, existingPlaceholder.remoteDigest)
      values.put(REMOTE_INCREMENTAL_DIGEST, existingPlaceholder.incrementalDigest)
      values.put(REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE, existingPlaceholder.incrementalMacChunkSize)

      if (offloadRestoredAt != null) {
        values.put(OFFLOAD_RESTORED_AT, offloadRestoredAt.inWholeMilliseconds)
      }

      val dataFilePath = hashMatch?.file?.absolutePath ?: fileWriteResult.file.absolutePath

      if (archiveRestore && existingPlaceholder.dataHash != null) {
        // Can update all rows with the same mediaName as data_file column will likely be null
        db.update(TABLE_NAME)
          .values(values)
          .where("$ID = ? OR ($REMOTE_KEY = ? AND $DATA_HASH_END = ?)", attachmentId.id, existingPlaceholder.remoteKey, existingPlaceholder.dataHash!!)
          .run()
      } else {
        db.update(TABLE_NAME)
          .values(values)
          .where("$ID = ? OR $DATA_FILE = ?", attachmentId.id, dataFilePath)
          .run()
      }

      Log.i(TAG, "[finalizeAttachmentAfterDownload] Finalized downloaded data for $attachmentId. (MessageId: $mmsId, $attachmentId)")

      hashMatch != null
    }

    val threadId = messages.getThreadIdForMessage(mmsId)

    if (!messages.isStory(mmsId)) {
      threads.updateSnippetUriSilently(threadId, snippetMessageId = mmsId, attachment = PartAuthority.getAttachmentDataUri(attachmentId))
    }

    notifyConversationListeners(threadId)
    notifyConversationListListeners()
    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()

    if (foundDuplicate) {
      if (!fileWriteResult.file.delete()) {
        Log.w(TAG, "Failed to delete unused attachment")
      }
    }

    if (MediaUtil.isAudio(existingPlaceholder)) {
      GenerateAudioWaveFormJob.enqueue(existingPlaceholder.attachmentId)
    }
  }

  @Throws(IOException::class)
  fun finalizeAttachmentThumbnailAfterDownload(attachmentId: AttachmentId, plaintextHash: String?, remoteKey: String?, inputStream: InputStream, transferFile: File) {
    Log.i(TAG, "[finalizeAttachmentThumbnailAfterDownload] Finalizing downloaded data for $attachmentId.")
    val fileWriteResult: DataFileWriteResult = writeToDataFile(newDataFile(context), inputStream, TransformProperties.empty())

    writableDatabase.withinTransaction { db ->
      val values = contentValuesOf(
        THUMBNAIL_FILE to fileWriteResult.file.absolutePath,
        THUMBNAIL_RANDOM to fileWriteResult.random,
        THUMBNAIL_RESTORE_STATE to ThumbnailRestoreState.FINISHED.value
      )

      if (plaintextHash != null && remoteKey != null) {
        db.update(TABLE_NAME)
          .values(values)
          .where("$DATA_HASH_END = ? AND $REMOTE_KEY = ?", plaintextHash, remoteKey)
          .run()
      } else {
        Log.w(TAG, "[finalizeAttachmentThumbnailAfterDownload] No plaintext hash or remote key provided for $attachmentId. Cannot update other possible thumbnails.")
      }
    }

    notifyConversationListListeners()
    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()

    if (!transferFile.delete()) {
      Log.w(TAG, "Unable to delete transfer file.")
    }
  }

  /**
   * Updates the state around archive thumbnail uploads, and ensures that all attachments sharing the same digest remain in sync.
   */
  fun finalizeAttachmentThumbnailAfterUpload(
    attachmentId: AttachmentId,
    attachmentPlaintextHash: String?,
    attachmentRemoteKey: String?,
    data: ByteArray
  ) {
    Log.i(TAG, "[finalizeAttachmentThumbnailAfterUpload] Finalizing archive data for $attachmentId thumbnail.")
    val fileWriteResult: DataFileWriteResult = writeToDataFile(newDataFile(context), ByteArrayInputStream(data), TransformProperties.empty())

    writableDatabase.withinTransaction { db ->
      val values = contentValuesOf(
        THUMBNAIL_FILE to fileWriteResult.file.absolutePath,
        THUMBNAIL_RANDOM to fileWriteResult.random,
        THUMBNAIL_RESTORE_STATE to ThumbnailRestoreState.FINISHED.value
      )

      if (attachmentPlaintextHash != null && attachmentRemoteKey != null) {
        db.update(TABLE_NAME)
          .values(values)
          .where("$DATA_HASH_END = ? AND $REMOTE_KEY = ?", attachmentPlaintextHash, attachmentRemoteKey)
          .run()
      } else {
        Log.w(TAG, "[finalizeAttachmentThumbnailAfterUpload] No plaintext hash or remote key provided for $attachmentId. Cannot update other possible thumbnails.")
      }
    }
  }

  /**
   * Needs to be called after an attachment is successfully uploaded. Writes metadata around it's final remote location, as well as calculates
   * it's ending hash, which is critical for backups.
   */
  @Throws(IOException::class)
  fun finalizeAttachmentAfterUpload(id: AttachmentId, uploadResult: AttachmentUploadResult) {
    Log.i(TAG, "[finalizeAttachmentAfterUpload] Finalizing upload for $id.")

    val dataStream = getAttachmentStream(id, 0)
    val messageDigest = MessageDigest.getInstance("SHA-256")

    DigestInputStream(dataStream, messageDigest).use {
      it.drain()
    }

    val dataHashEnd = Base64.encodeWithPadding(messageDigest.digest())

    val values = contentValuesOf(
      TRANSFER_STATE to TRANSFER_PROGRESS_DONE,
      CDN_NUMBER to uploadResult.cdnNumber,
      REMOTE_LOCATION to uploadResult.remoteId.toString(),
      REMOTE_KEY to Base64.encodeWithPadding(uploadResult.key),
      REMOTE_DIGEST to uploadResult.digest,
      REMOTE_INCREMENTAL_DIGEST to uploadResult.incrementalDigest,
      REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE to uploadResult.incrementalDigestChunkSize,
      DATA_SIZE to uploadResult.dataSize,
      DATA_HASH_END to dataHashEnd,
      UPLOAD_TIMESTAMP to uploadResult.uploadTimestamp,
      BLUR_HASH to uploadResult.blurHash
    )

    val dataFilePath = getDataFilePath(id) ?: throw IOException("No data file found for attachment!")

    val updateCount = writableDatabase
      .update(TABLE_NAME)
      .values(values)
      .where("$ID = ? OR $DATA_FILE = ?", id.id, dataFilePath)
      .run()

    if (updateCount <= 0) {
      Log.w(TAG, "[finalizeAttachmentAfterUpload] Failed to update attachment after upload! $id")
    }
  }

  @Throws(MmsException::class)
  fun copyAttachmentData(sourceId: AttachmentId, destinationId: AttachmentId) {
    val sourceAttachment = getAttachment(sourceId) ?: throw MmsException("Cannot find attachment for source!")
    val sourceDataInfo = getDataFileInfo(sourceId) ?: throw MmsException("No attachment data found for source!")

    writableDatabase
      .update(TABLE_NAME)
      .values(
        DATA_FILE to sourceDataInfo.file.absolutePath,
        DATA_HASH_START to sourceDataInfo.hashStart,
        DATA_HASH_END to sourceDataInfo.hashEnd,
        DATA_SIZE to sourceDataInfo.length,
        DATA_RANDOM to sourceDataInfo.random,
        TRANSFER_STATE to sourceAttachment.transferState,
        CDN_NUMBER to sourceAttachment.cdn.serialize(),
        REMOTE_LOCATION to sourceAttachment.remoteLocation,
        REMOTE_DIGEST to sourceAttachment.remoteDigest,
        REMOTE_INCREMENTAL_DIGEST to sourceAttachment.incrementalDigest,
        REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE to sourceAttachment.incrementalMacChunkSize,
        REMOTE_KEY to sourceAttachment.remoteKey,
        DATA_SIZE to sourceAttachment.size,
        FAST_PREFLIGHT_ID to sourceAttachment.fastPreflightId,
        WIDTH to sourceAttachment.width,
        HEIGHT to sourceAttachment.height,
        CONTENT_TYPE to sourceAttachment.contentType,
        BLUR_HASH to sourceAttachment.getVisualHashStringOrNull(),
        TRANSFORM_PROPERTIES to sourceAttachment.transformProperties?.serialize()
      )
      .where("$ID = ?", destinationId.id)
      .run()
  }

  fun updateAttachmentCaption(id: AttachmentId, caption: String?) {
    writableDatabase
      .update(TABLE_NAME)
      .values(CAPTION to caption)
      .where("$ID = ?", id.id)
      .run()
  }

  fun updateDisplayOrder(orderMap: Map<AttachmentId, Int?>) {
    writableDatabase.withinTransaction { db ->
      for ((key, value) in orderMap) {
        db.update(TABLE_NAME)
          .values(DISPLAY_ORDER to value)
          .where("$ID = ?", key.id)
          .run()
      }
    }
  }

  @Throws(MmsException::class)
  fun insertAttachmentForPreUpload(attachment: Attachment): DatabaseAttachment {
    Log.d(TAG, "Inserting attachment ${attachment.uri} for pre-upload.")
    val result = insertAttachmentsForMessage(PREUPLOAD_MESSAGE_ID, listOf(attachment), emptyList())

    if (result.values.isEmpty()) {
      throw MmsException("Bad attachment result!")
    }

    return getAttachment(result.values.iterator().next()) ?: throw MmsException("Failed to retrieve attachment we just inserted!")
  }

  fun getMessageId(attachmentId: AttachmentId): Long {
    return readableDatabase
      .select(MESSAGE_ID)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .run()
      .readToSingleLong()
  }

  fun updateMessageId(attachmentIds: Collection<AttachmentId>, mmsId: Long, isStory: Boolean) {
    writableDatabase.withinTransaction { db ->
      val values = ContentValues(2).apply {
        put(MESSAGE_ID, mmsId)
        if (!isStory) {
          putNull(CAPTION)
        }
      }

      var updatedCount = 0
      var attachmentIdSize = 0
      for (attachmentId in attachmentIds) {
        attachmentIdSize++
        updatedCount += db
          .update(TABLE_NAME)
          .values(values)
          .where("$ID = ?", attachmentId.id)
          .run()
      }

      Log.d(TAG, "[updateMessageId] Updated $updatedCount out of $attachmentIdSize ids.")
    }
  }

  fun createRemoteKeyIfNecessary(attachmentId: AttachmentId) {
    val key = Util.getSecretBytes(64)

    writableDatabase.withinTransaction {
      writableDatabase
        .update(TABLE_NAME)
        .values(REMOTE_KEY to Base64.encodeWithPadding(key))
        .where("$ID = ? AND $REMOTE_KEY IS NULL", attachmentId.id)
        .run()
    }
  }

  /**
   * A query for a specific migration. Retrieves attachments that we'd need to create a new digest for.
   * This is basically all attachments that have data and are finished downloading.
   */
  fun getAttachmentsThatNeedNewDigests(): List<AttachmentId> {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where(
        """
        $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND 
        $DATA_FILE NOT NULL
        """
      )
      .run()
      .readToList { AttachmentId(it.requireLong(ID)) }
  }

  /**
   * A query for a specific migration. Retrieves attachments that we'd need to create a new digest for.
   * This is basically all attachments that have data and are finished downloading.
   */
  fun getDataFilesWithMultipleValidAttachments(): List<String> {
    return readableDatabase
      .select("DISTINCT(a1.$DATA_FILE)")
      .from("$TABLE_NAME a1 INDEXED BY $DATA_FILE_INDEX")
      .where(
        """
        a1.$DATA_FILE NOT NULL AND
        a1.$TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND EXISTS (
          SELECT 1
          FROM $TABLE_NAME a2 INDEXED BY $DATA_FILE_INDEX
          WHERE 
            a1.$DATA_FILE = a2.$DATA_FILE AND
            a2.$TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND
            a2.$ID != a1.$ID
        )
        """
      )
      .run()
      .readToList { it.requireNonNullString(DATA_FILE) }
  }

  /**
   * As part of the digest backfill process, this updates the (key, digest) tuple for all attachments that share a data file (and are done downloading).
   */
  fun updateRemoteKeyAndDigestByDataFile(dataFile: String, key: ByteArray, digest: ByteArray) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        REMOTE_KEY to Base64.encodeWithPadding(key),
        REMOTE_DIGEST to digest
      )
      .where("$DATA_FILE = ? AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE", dataFile)
      .run()
  }

  /**
   * Inserts new attachments in the table. The [Attachment]s may or may not have data, depending on whether it's an attachment we created locally or some
   * inbound attachment that we haven't fetched yet.
   *
   * If the attachment has no data, it is assumed that you will later call [finalizeAttachmentAfterDownload].
   */
  @Throws(MmsException::class)
  fun insertAttachmentsForMessage(mmsId: Long, attachments: List<Attachment>, quoteAttachment: List<Attachment>): Map<Attachment, AttachmentId> {
    if (attachments.isEmpty() && quoteAttachment.isEmpty()) {
      return emptyMap()
    }

    Log.d(TAG, "[insertAttachmentsForMessage] insertParts(${attachments.size})")

    val insertedAttachments: MutableMap<Attachment, AttachmentId> = mutableMapOf()
    for (attachment in attachments) {
      val attachmentId = when {
        attachment is LocalStickerAttachment -> insertLocalStickerAttachment(mmsId, attachment)
        attachment.uri != null -> insertAttachmentWithData(mmsId, attachment)
        attachment is ArchivedAttachment -> insertArchivedAttachment(mmsId, attachment, quote = false, quoteTargetContentType = null)
        else -> insertUndownloadedAttachment(mmsId, attachment, quote = false)
      }

      insertedAttachments[attachment] = attachmentId
      Log.i(TAG, "[insertAttachmentsForMessage] Inserted attachment at $attachmentId")
    }

    try {
      for (attachment in quoteAttachment) {
        val attachmentId = when {
          attachment.uri != null -> insertQuoteAttachment(mmsId, attachment)
          attachment is ArchivedAttachment -> insertArchivedAttachment(mmsId, attachment, quote = true, quoteTargetContentType = attachment.quoteTargetContentType)
          else -> insertUndownloadedAttachment(mmsId, attachment, quote = true)
        }

        insertedAttachments[attachment] = attachmentId
        Log.i(TAG, "[insertAttachmentsForMessage] Inserted quoted attachment at $attachmentId")
      }
    } catch (e: MmsException) {
      Log.w(TAG, "Failed to insert quote attachment! messageId: $mmsId")
    }

    return insertedAttachments
  }

  /**
   * Updates the data stored for an existing attachment. This happens after transformations, like transcoding.
   */
  @Throws(MmsException::class, IOException::class)
  fun updateAttachmentData(
    databaseAttachment: DatabaseAttachment,
    mediaStream: MediaStream
  ) {
    val attachmentId = databaseAttachment.attachmentId
    val existingDataFileInfo: DataFileInfo = getDataFileInfo(attachmentId) ?: throw MmsException("No attachment data found!")
    val newDataFileInfo: DataFileWriteResult = writeToDataFile(existingDataFileInfo.file, mediaStream.stream, databaseAttachment.transformProperties ?: TransformProperties.empty())

    // TODO We don't dedupe here because we're assuming that we should have caught any dupe scenarios on first insert. We could consider doing dupe checks here though.

    writableDatabase.withinTransaction { db ->
      val contentValues = contentValuesOf(
        DATA_SIZE to newDataFileInfo.length,
        CONTENT_TYPE to mediaStream.mimeType,
        WIDTH to mediaStream.width,
        HEIGHT to mediaStream.height,
        DATA_FILE to newDataFileInfo.file.absolutePath,
        DATA_RANDOM to newDataFileInfo.random
      )

      val updateCount = db.update(TABLE_NAME)
        .values(contentValues)
        .where("$ID = ? OR $DATA_FILE = ?", attachmentId.id, existingDataFileInfo.file.absolutePath)
        .run()

      Log.i(TAG, "[updateAttachmentData] Updated $updateCount rows.")
    }
  }

  fun duplicateAttachmentsForMessage(destinationMessageId: Long, sourceMessageId: Long, excludedIds: Collection<Long>) {
    writableDatabase.withinTransaction { db ->
      db.execSQL("CREATE TEMPORARY TABLE tmp_part AS SELECT * FROM $TABLE_NAME WHERE $MESSAGE_ID = ?", SqlUtil.buildArgs(sourceMessageId))

      val queries = SqlUtil.buildCollectionQuery(ID, excludedIds)
      for (query in queries) {
        db.delete("tmp_part", query.where, query.whereArgs)
      }

      db.execSQL("UPDATE tmp_part SET $ID = NULL, $MESSAGE_ID = ?", SqlUtil.buildArgs(destinationMessageId))
      db.execSQL("INSERT INTO $TABLE_NAME SELECT * FROM tmp_part")
      db.execSQL("DROP TABLE tmp_part")
    }
  }

  @Throws(IOException::class)
  fun getOrCreateTransferFile(attachmentId: AttachmentId): File {
    val existing = getTransferFile(writableDatabase, attachmentId)
    if (existing != null) {
      return existing
    }

    val transferFile = newTransferFile()

    writableDatabase
      .update(TABLE_NAME)
      .values(TRANSFER_FILE to transferFile.absolutePath)
      .where("$ID = ?", attachmentId.id)
      .run()

    return transferFile
  }

  fun createArchiveThumbnailTransferFile(): File {
    return newTransferFile()
  }

  fun getDataFileInfo(attachmentId: AttachmentId): DataFileInfo? {
    return readableDatabase
      .select(*DATA_FILE_INFO_PROJECTION)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .run()
      .readToSingleObject { cursor -> cursor.readDataFileInfo() }
  }

  fun getThumbnailFileInfo(attachmentId: AttachmentId): ThumbnailFileInfo? {
    return readableDatabase
      .select(ID, THUMBNAIL_FILE, THUMBNAIL_RANDOM)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .run()
      .readToSingleObject { cursor ->
        if (cursor.isNull(THUMBNAIL_FILE)) {
          null
        } else {
          cursor.readThumbnailFileInfo()
        }
      }
  }

  fun getDataFilePath(attachmentId: AttachmentId): String? {
    return readableDatabase
      .select(DATA_FILE)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .run()
      .readToSingleObject { it.requireString(DATA_FILE) }
  }

  fun markAttachmentAsTransformed(attachmentId: AttachmentId, withFastStart: Boolean) {
    Log.i(TAG, "[markAttachmentAsTransformed] Marking $attachmentId as transformed. withFastStart: $withFastStart")
    writableDatabase.withinTransaction { db ->
      try {
        val dataInfo = getDataFileInfo(attachmentId)
        if (dataInfo == null) {
          Log.w(TAG, "[markAttachmentAsTransformed] Failed to get transformation properties, attachment no longer exists.")
          return@withinTransaction
        }

        var transformProperties = dataInfo.transformProperties.withSkipTransform()
        if (withFastStart) {
          transformProperties = transformProperties.withMp4FastStart()
        }

        val count = writableDatabase
          .update(TABLE_NAME)
          .values(TRANSFORM_PROPERTIES to transformProperties.serialize())
          .where("$ID = ? OR $DATA_FILE = ?", attachmentId.id, dataInfo.file.absolutePath)
          .run()

        Log.i(TAG, "[markAttachmentAsTransformed] Updated $count rows.")
      } catch (e: Exception) {
        Log.w(TAG, "[markAttachmentAsTransformed] Could not mark attachment as transformed.", e)
      }
    }
  }

  @WorkerThread
  fun writeAudioHash(attachmentId: AttachmentId, audioWaveForm: AudioWaveFormData?) {
    Log.i(TAG, "updating part audio wave form for $attachmentId")
    writableDatabase
      .update(TABLE_NAME)
      .values(BLUR_HASH to audioWaveForm?.let { AudioHash(it).hash })
      .where("$ID = ?", attachmentId.id)
      .run()
  }

  @RequiresApi(23)
  fun mediaDataSourceFor(attachmentId: AttachmentId, allowReadingFromTempFile: Boolean): MediaDataSource? {
    val dataInfo = getDataFileInfo(attachmentId)
    if (dataInfo != null) {
      return EncryptedMediaDataSource.createFor(attachmentSecret, dataInfo.file, dataInfo.random, dataInfo.length)
    }

    if (allowReadingFromTempFile) {
      Log.d(TAG, "Completed data file not found for video attachment, checking for in-progress files.")
      val transferFile = getTransferFile(readableDatabase, attachmentId)
      if (transferFile != null) {
        return EncryptedMediaDataSource.createForDiskBlob(attachmentSecret, transferFile)
      }
    }

    Log.w(TAG, "No data file found for video attachment!")
    return null
  }

  /**
   * @return null if we fail to find the given attachmentId
   */
  fun getTransformProperties(attachmentId: AttachmentId): TransformProperties? {
    return readableDatabase
      .select(TRANSFORM_PROPERTIES)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .run()
      .readToSingleObject {
        TransformProperties.parse(it.requireString(TRANSFORM_PROPERTIES))
      }
  }

  fun markAttachmentUploaded(messageId: Long, attachment: Attachment) {
    writableDatabase
      .update(TABLE_NAME)
      .values(TRANSFER_STATE to TRANSFER_PROGRESS_DONE)
      .where("$ID = ?", (attachment as DatabaseAttachment).attachmentId.id)
      .run()

    val threadId = messages.getThreadIdForMessage(messageId)
    notifyConversationListeners(threadId)
  }

  fun getAttachments(cursor: Cursor): List<DatabaseAttachment> {
    return try {
      if (cursor.getColumnIndex(ATTACHMENT_JSON_ALIAS) != -1) {
        if (cursor.isNull(ATTACHMENT_JSON_ALIAS)) {
          return LinkedList()
        }

        val result: MutableList<DatabaseAttachment> = mutableListOf()
        val array = JSONArray(cursor.requireString(ATTACHMENT_JSON_ALIAS))

        for (i in 0 until array.length()) {
          val jsonObject = SaneJSONObject(array.getJSONObject(i))

          if (!jsonObject.isNull(ID)) {
            val contentType = jsonObject.getString(CONTENT_TYPE)

            result += DatabaseAttachment(
              attachmentId = AttachmentId(jsonObject.getLong(ID)),
              mmsId = jsonObject.getLong(MESSAGE_ID),
              hasData = !TextUtils.isEmpty(jsonObject.getString(DATA_FILE)),
              hasThumbnail = MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType),
              contentType = contentType,
              transferProgress = jsonObject.getInt(TRANSFER_STATE),
              size = jsonObject.getLong(DATA_SIZE),
              fileName = jsonObject.getString(FILE_NAME),
              cdn = Cdn.deserialize(jsonObject.getInt(CDN_NUMBER)),
              location = jsonObject.getString(REMOTE_LOCATION),
              key = jsonObject.getString(REMOTE_KEY),
              digest = null,
              incrementalDigest = null,
              incrementalMacChunkSize = 0,
              fastPreflightId = jsonObject.getString(FAST_PREFLIGHT_ID),
              voiceNote = jsonObject.getInt(VOICE_NOTE) != 0,
              borderless = jsonObject.getInt(BORDERLESS) != 0,
              videoGif = jsonObject.getInt(VIDEO_GIF) != 0,
              width = jsonObject.getInt(WIDTH),
              height = jsonObject.getInt(HEIGHT),
              quote = jsonObject.getInt(QUOTE) != 0,
              quoteTargetContentType = if (!jsonObject.isNull(QUOTE_TARGET_CONTENT_TYPE)) jsonObject.getString(QUOTE_TARGET_CONTENT_TYPE) else null,
              caption = jsonObject.getString(CAPTION),
              stickerLocator = if (jsonObject.getInt(STICKER_ID) >= 0) {
                StickerLocator(
                  jsonObject.getString(STICKER_PACK_ID)!!,
                  jsonObject.getString(STICKER_PACK_KEY)!!,
                  jsonObject.getInt(STICKER_ID),
                  jsonObject.getString(STICKER_EMOJI)
                )
              } else {
                null
              },
              blurHash = if (MediaUtil.isAudioType(contentType)) null else BlurHash.parseOrNull(jsonObject.getString(BLUR_HASH)),
              audioHash = if (MediaUtil.isAudioType(contentType)) AudioHash.parseOrNull(jsonObject.getString(BLUR_HASH)) else null,
              transformProperties = TransformProperties.parse(jsonObject.getString(TRANSFORM_PROPERTIES)),
              displayOrder = jsonObject.getInt(DISPLAY_ORDER),
              uploadTimestamp = jsonObject.getLong(UPLOAD_TIMESTAMP),
              dataHash = jsonObject.getString(DATA_HASH_END),
              archiveCdn = if (jsonObject.isNull(ARCHIVE_CDN)) null else jsonObject.getInt(ARCHIVE_CDN),
              thumbnailRestoreState = ThumbnailRestoreState.deserialize(jsonObject.getInt(THUMBNAIL_RESTORE_STATE)),
              archiveTransferState = ArchiveTransferState.deserialize(jsonObject.getInt(ARCHIVE_TRANSFER_STATE)),
              uuid = UuidUtil.parseOrNull(jsonObject.getString(ATTACHMENT_UUID))
            )
          }
        }

        result
      } else {
        listOf(getAttachment(cursor))
      }
    } catch (e: JSONException) {
      throw AssertionError(e)
    }
  }

  fun hasStickerAttachments(): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$STICKER_PACK_ID NOT NULL")
      .run()
  }

  fun containsStickerPackId(stickerPackId: String): Boolean {
    return readableDatabase.exists(TABLE_NAME)
      .where("$STICKER_PACK_ID = ?", stickerPackId)
      .run()
  }

  fun getUnavailableStickerPacks(): Cursor {
    val query = """
      SELECT DISTINCT $STICKER_PACK_ID, $STICKER_PACK_KEY
      FROM $TABLE_NAME
      WHERE
        $STICKER_PACK_ID NOT NULL AND
        $STICKER_PACK_KEY NOT NULL AND
        $STICKER_PACK_ID NOT IN (SELECT DISTINCT ${StickerTable.PACK_ID} FROM ${StickerTable.TABLE_NAME})
    """

    return readableDatabase.rawQuery(query, null)
  }

  /**
   * Sets the archive data for the specific attachment, as well as for any attachments that have the same mediaName (plaintextHash + remoteKey).
   */
  fun setArchiveCdn(attachmentId: AttachmentId, archiveCdn: Int) {
    writableDatabase.withinTransaction { db ->
      val plaintextHashAndRemoteKey = db
        .select(DATA_HASH_END, REMOTE_KEY)
        .from(TABLE_NAME)
        .where("$ID = ?", attachmentId.id)
        .run()
        .readToSingleObject {
          it.requireNonNullString(DATA_HASH_END) to it.requireNonNullString(REMOTE_KEY)
        }

      if (plaintextHashAndRemoteKey == null) {
        Log.w(TAG, "No data file found for attachment $attachmentId. Can't set archive data.")
        return@withinTransaction
      }

      val (plaintextHash, remoteKey) = plaintextHashAndRemoteKey

      db.update(TABLE_NAME)
        .values(
          ARCHIVE_CDN to archiveCdn,
          ARCHIVE_TRANSFER_STATE to ArchiveTransferState.FINISHED.value
        )
        .where("$DATA_HASH_END = ? AND $REMOTE_KEY = ?", plaintextHash, remoteKey)
        .run()
    }
  }

  /**
   * Updates all attachments that share the same mediaName (plaintextHash + remoteKey) with the given archive CDN.
   */
  fun setArchiveCdnByPlaintextHashAndRemoteKey(plaintextHash: ByteArray, remoteKey: ByteArray, archiveCdn: Int) {
    writableDatabase
      .update(TABLE_NAME)
      .values(ARCHIVE_CDN to archiveCdn)
      .where("$DATA_HASH_END = ? AND $REMOTE_KEY = ?", Base64.encodeWithPadding(plaintextHash), Base64.encodeWithPadding(remoteKey))
      .run()
  }

  fun clearArchiveData(attachmentId: AttachmentId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        ARCHIVE_CDN to null,
        ARCHIVE_TRANSFER_STATE to ArchiveTransferState.NONE.value,
        UPLOAD_TIMESTAMP to 0
      )
      .where("$ID = ?", attachmentId)
      .run()
  }

  fun clearAllArchiveData() {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        ARCHIVE_CDN to null,
        ARCHIVE_TRANSFER_STATE to ArchiveTransferState.NONE.value,
        UPLOAD_TIMESTAMP to 0
      )
      .where("$ARCHIVE_CDN NOT NULL")
      .run()
  }

  fun debugMakeValidForArchive(attachmentId: AttachmentId) {
    writableDatabase
      .execSQL(
        """
        UPDATE $TABLE_NAME
        SET $DATA_HASH_END = $DATA_HASH_START
        WHERE $ID = ${attachmentId.id}
      """
      )

    writableDatabase
      .update(TABLE_NAME)
      .values(
        REMOTE_KEY to Base64.encodeWithPadding(Util.getSecretBytes(64)),
        REMOTE_DIGEST to Util.getSecretBytes(64)
      )
  }

  private fun deleteDataFiles(filePaths: Set<String>, contentTypes: Set<String>) {
    for (path in filePaths) {
      if (File(path).delete()) {
        Log.d(TAG, "[deleteDataFiles] Successfully deleted $path")
      } else {
        Log.w(TAG, "[deleteDataFiles] Failed to delete $path")
      }
    }

    if (contentTypes.any { MediaUtil.isImageOrVideoType(it) }) {
      Glide.get(context).clearDiskCache()
      ThreadUtil.runOnMain { Glide.get(context).clearMemory() }
    }
  }

  /**
   * Checks if it's safe to delete a specific [filePath] for an attachment with [attachmentId] that is in the process of being deleted.
   * Basically it checks if anyone else is using that file -- if so, it's not safe to delete.
   */
  private fun isSafeToDeleteDataFile(filePath: String, attachmentId: AttachmentId): Boolean {
    check(writableDatabase.inTransaction()) { "Must be in a transaction!" }

    val attachmentInUse = readableDatabase
      .exists(TABLE_NAME)
      .where("$DATA_FILE = ? AND $ID != ${attachmentId.id}", filePath)
      .run()

    if (attachmentInUse) {
      Log.i(TAG, "[deleteDataFileIfPossible] Attachment in use. Skipping deletion of $attachmentId. Path: $filePath")
      return false
    }

    return true
  }

  @Throws(FileNotFoundException::class)
  private fun getDataStream(attachmentId: AttachmentId, offset: Long): InputStream? {
    val dataInfo = getDataFileInfo(attachmentId) ?: return null
    return getDataStream(dataInfo.file, dataInfo.random, offset)
  }

  @Throws(FileNotFoundException::class)
  private fun getDataStream(file: File, random: ByteArray, offset: Long): InputStream? {
    return try {
      if (random.size == 32) {
        ModernDecryptingPartInputStream.createFor(attachmentSecret, random, file, offset)
      } else {
        val stream = ClassicDecryptingPartInputStream.createFor(attachmentSecret, file)
        val skipped = stream.skip(offset)
        if (skipped != offset) {
          Log.w(TAG, "Skip failed: $skipped vs $offset")
          return null
        }
        stream
      }
    } catch (e: FileNotFoundException) {
      Log.w(TAG, e)
      throw e
    } catch (e: IOException) {
      Log.w(TAG, e)
      null
    }
  }

  @Throws(FileNotFoundException::class)
  private fun getThumbnailStream(attachmentId: AttachmentId, offset: Long): InputStream? {
    val thumbnailInfo = getThumbnailFileInfo(attachmentId) ?: return null

    return try {
      ModernDecryptingPartInputStream.createFor(attachmentSecret, thumbnailInfo.random, thumbnailInfo.file, offset)
    } catch (e: FileNotFoundException) {
      Log.w(TAG, e)
      throw e
    } catch (e: IOException) {
      Log.w(TAG, e)
      null
    }
  }

  @Throws(IOException::class)
  private fun newTransferFile(): File {
    val partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
    return PartFileProtector.protect {
      File.createTempFile("transfer", ".mms", partsDirectory)
    }
  }

  /**
   * Reads the entire stream and saves to disk and returns a bunch of metadat about the write.
   */
  @Throws(MmsException::class, IllegalStateException::class)
  private fun writeToDataFile(destination: File, inputStream: InputStream, transformProperties: TransformProperties, closeInputStream: Boolean = true): DataFileWriteResult {
    return try {
      // Sometimes the destination is a file that's already in use, sometimes it's not.
      // To avoid writing to a file while it's in-use, we write to a temp file and then rename it to the destination file at the end.
      val tempFile = newDataFile(context)
      val messageDigest = MessageDigest.getInstance("SHA-256")
      val digestInputStream = DigestInputStream(inputStream, messageDigest)

      val encryptingStreamData = ModernEncryptingPartOutputStream.createFor(attachmentSecret, tempFile, false)
      val random = encryptingStreamData.first
      val encryptingOutputStream = encryptingStreamData.second

      val length = digestInputStream.copyTo(encryptingOutputStream, closeInputStream)
      val hash = Base64.encodeWithPadding(digestInputStream.messageDigest.digest())

      if (!tempFile.renameTo(destination)) {
        Log.w(TAG, "[writeToDataFile] Couldn't rename ${tempFile.path} to ${destination.path}")
        tempFile.delete()
        throw IllegalStateException("Couldn't rename ${tempFile.path} to ${destination.path}")
      }

      DataFileWriteResult(
        file = destination,
        length = length,
        random = random,
        hash = hash,
        transformProperties = transformProperties
      )
    } catch (e: IOException) {
      throw MmsException(e)
    } catch (e: NoSuchAlgorithmException) {
      throw MmsException(e)
    }
  }

  private fun areTransformationsCompatible(
    newProperties: TransformProperties,
    potentialMatchProperties: TransformProperties,
    newHashStart: String,
    potentialMatchHashEnd: String?,
    newIsQuote: Boolean
  ): Boolean {
    // If we're starting now where another attachment finished, then it means we're forwarding an attachment.
    if (newHashStart == potentialMatchHashEnd) {
      // Quotes don't get transcoded or anything and are just a reference to the original attachment, so as long as the hashes match we're fine
      if (newIsQuote) {
        return true
      }

      // If the new attachment is an edited video, we can't re-use the file
      if (newProperties.videoEdited) {
        return false
      }

      return true
    }

    if (newProperties.sentMediaQuality != potentialMatchProperties.sentMediaQuality) {
      return false
    }

    if (newProperties.videoEdited != potentialMatchProperties.videoEdited) {
      return false
    }

    if (newProperties.videoTrimStartTimeUs != potentialMatchProperties.videoTrimStartTimeUs) {
      return false
    }

    if (newProperties.videoTrimEndTimeUs != potentialMatchProperties.videoTrimEndTimeUs) {
      return false
    }

    return true
  }

  /**
   * Attachments need records in the database even if they haven't been downloaded yet. That allows us to store the info we need to download it, what message
   * it's associated with, etc. We treat this case separately from attachments with data (see [insertAttachmentWithData]) because it's much simpler,
   * and splitting the two use cases makes the code easier to understand.
   *
   * Callers are expected to later call [finalizeAttachmentAfterDownload] once they have downloaded the data for this attachment.
   */
  @Throws(MmsException::class)
  private fun insertUndownloadedAttachment(messageId: Long, attachment: Attachment, quote: Boolean): AttachmentId {
    Log.d(TAG, "[insertUndownloadedAttachment] Inserting attachment for messageId $messageId.")

    val attachmentId: AttachmentId = writableDatabase.withinTransaction { db ->
      val contentValues = ContentValues().apply {
        put(MESSAGE_ID, messageId)
        put(CONTENT_TYPE, attachment.contentType)
        put(TRANSFER_STATE, attachment.transferState)
        put(CDN_NUMBER, attachment.cdn.serialize())
        put(REMOTE_LOCATION, attachment.remoteLocation)
        put(REMOTE_DIGEST, attachment.remoteDigest)
        put(REMOTE_KEY, attachment.remoteKey)
        put(FILE_NAME, StorageUtil.getCleanFileName(attachment.fileName))
        put(DATA_SIZE, attachment.size)
        put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
        put(VOICE_NOTE, attachment.voiceNote.toInt())
        put(BORDERLESS, attachment.borderless.toInt())
        put(VIDEO_GIF, attachment.videoGif.toInt())
        put(WIDTH, attachment.width)
        put(HEIGHT, attachment.height)
        put(QUOTE, quote.toInt())
        put(QUOTE_TARGET_CONTENT_TYPE, attachment.quoteTargetContentType)
        put(CAPTION, attachment.caption)
        put(UPLOAD_TIMESTAMP, attachment.uploadTimestamp)
        put(BLUR_HASH, attachment.blurHash?.hash)
        put(ATTACHMENT_UUID, attachment.uuid?.toString())

        attachment.stickerLocator?.let { sticker ->
          put(STICKER_PACK_ID, sticker.packId)
          put(STICKER_PACK_KEY, sticker.packKey)
          put(STICKER_ID, sticker.stickerId)
          put(STICKER_EMOJI, sticker.emoji)
        }

        if (attachment.incrementalDigest?.isNotEmpty() == true && attachment.incrementalMacChunkSize != 0) {
          put(REMOTE_INCREMENTAL_DIGEST, attachment.incrementalDigest)
          put(REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE, attachment.incrementalMacChunkSize)
        } else {
          putNull(REMOTE_INCREMENTAL_DIGEST)
        }
      }

      val rowId = db.insert(TABLE_NAME, null, contentValues)
      AttachmentId(rowId)
    }

    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
    return attachmentId
  }

  /**
   * When inserting a quote attachment, it looks a lot like a normal attachment insert, but rather than insert the actual data pointed at by the attachment's
   * URI, we instead want to generate a thumbnail of that attachment and use that instead.
   *
   * It's important to note that it's assumed that [attachment] is the attachment that you're *quoting*. We'll use it's contentType as the quoteTargetContentType.
   */
  @Throws(MmsException::class)
  private fun insertQuoteAttachment(messageId: Long, attachment: Attachment): AttachmentId {
    Log.d(TAG, "[insertQuoteAttachment] Inserting quote attachment for messageId $messageId.")

    val thumbnail = generateQuoteThumbnail(DecryptableUri(attachment.uri!!), attachment.contentType)
    if (thumbnail != null) {
      Log.d(TAG, "[insertQuoteAttachment] Successfully generated quote thumbnail for messageId $messageId.")

      return insertAttachmentWithData(
        messageId = messageId,
        dataStream = thumbnail.data.inputStream(),
        attachment = attachment,
        quote = true,
        quoteTargetContentType = attachment.contentType
      )
    }

    Log.d(TAG, "[insertQuoteAttachment] Unable to generate quote thumbnail for messageId $messageId. Content type: ${attachment.contentType}")
    val attachmentId: AttachmentId = writableDatabase.withinTransaction { db ->
      val contentValues = ContentValues().apply {
        put(MESSAGE_ID, messageId)
        putNull(CONTENT_TYPE)
        put(VOICE_NOTE, attachment.voiceNote.toInt())
        put(BORDERLESS, attachment.borderless.toInt())
        put(VIDEO_GIF, attachment.videoGif.toInt())
        put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE)
        put(DATA_SIZE, 0)
        put(WIDTH, attachment.width)
        put(HEIGHT, attachment.height)
        put(QUOTE, 1)
        put(QUOTE_TARGET_CONTENT_TYPE, attachment.contentType)
        put(BLUR_HASH, attachment.blurHash?.hash)
        put(FILE_NAME, attachment.fileName)

        attachment.stickerLocator?.let { sticker ->
          put(STICKER_PACK_ID, sticker.packId)
          put(STICKER_PACK_KEY, sticker.packKey)
          put(STICKER_ID, sticker.stickerId)
          put(STICKER_EMOJI, sticker.emoji)
        }
      }

      val rowId = db.insert(TABLE_NAME, null, contentValues)
      AttachmentId(rowId)
    }

    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
    return attachmentId
  }

  fun generateQuoteThumbnail(uri: DecryptableUri, contentType: String?, quiet: Boolean = false): ImageCompressionUtil.Result? {
    return try {
      when {
        MediaUtil.isImageType(contentType) -> {
          val hasTransparency = MediaUtil.isPngType(contentType) || MediaUtil.isWebpType(contentType)
          val outputFormat = if (hasTransparency) MediaUtil.IMAGE_WEBP else MediaUtil.IMAGE_JPEG

          ImageCompressionUtil.compress(
            context,
            contentType,
            outputFormat,
            uri,
            QUOTE_THUMBNAIL_DIMEN,
            QUOTE_THUMBAIL_QUALITY,
            true
          )
        }
        MediaUtil.isVideoType(contentType) -> {
          val videoThumbnail = MediaUtil.getVideoThumbnail(context, uri.uri)
          if (videoThumbnail != null) {
            ImageCompressionUtil.compress(
              context,
              MediaUtil.IMAGE_JPEG,
              MediaUtil.IMAGE_JPEG,
              uri,
              QUOTE_THUMBNAIL_DIMEN,
              QUOTE_THUMBAIL_QUALITY
            )
          } else {
            Log.w(TAG, "[generateQuoteThumbnail] Failed to extract video thumbnail")
            null
          }
        }
        else -> {
          Log.w(TAG, "[generateQuoteThumbnail] Unsupported content type for thumbnail generation: $contentType")
          null
        }
      }
    } catch (e: BitmapDecodingException) {
      Log.w(TAG, "[generateQuoteThumbnail] Failed to decode image for thumbnail", e.takeUnless { quiet })
      null
    } catch (e: Exception) {
      Log.w(TAG, "[generateQuoteThumbnail] Failed to generate thumbnail", e.takeUnless { quiet })
      null
    }
  }

  /**
   * Attachments need records in the database even if they haven't been downloaded yet. That allows us to store the info we need to download it, what message
   * it's associated with, etc. We treat this case separately from attachments with data (see [insertAttachmentWithData]) because it's much simpler,
   * and splitting the two use cases makes the code easier to understand.
   *
   * Callers are expected to later call [finalizeAttachmentAfterDownload] once they have downloaded the data for this attachment.
   */
  @Throws(MmsException::class)
  private fun insertArchivedAttachment(messageId: Long, attachment: ArchivedAttachment, quote: Boolean, quoteTargetContentType: String?): AttachmentId {
    Log.d(TAG, "[insertArchivedAttachment] Inserting attachment for messageId $messageId.")

    val attachmentId: AttachmentId = writableDatabase.withinTransaction { db ->
      val plaintextHash = attachment.plaintextHash.takeIf { it.isNotEmpty() }?.let { Base64.encodeWithPadding(it) }

      val contentValues = ContentValues().apply {
        put(MESSAGE_ID, messageId)
        put(CONTENT_TYPE, attachment.contentType)
        put(TRANSFER_STATE, attachment.transferState)
        put(CDN_NUMBER, attachment.cdn.serialize())
        put(REMOTE_LOCATION, attachment.remoteLocation)
        put(REMOTE_DIGEST, attachment.remoteDigest)
        put(REMOTE_KEY, attachment.remoteKey)
        put(FILE_NAME, StorageUtil.getCleanFileName(attachment.fileName))
        put(DATA_SIZE, attachment.size)
        put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
        put(VOICE_NOTE, attachment.voiceNote.toInt())
        put(BORDERLESS, attachment.borderless.toInt())
        put(VIDEO_GIF, attachment.videoGif.toInt())
        put(WIDTH, attachment.width)
        put(HEIGHT, attachment.height)
        put(QUOTE, quote.toInt())
        put(QUOTE_TARGET_CONTENT_TYPE, quoteTargetContentType)
        put(CAPTION, attachment.caption)
        put(UPLOAD_TIMESTAMP, attachment.uploadTimestamp)
        put(ARCHIVE_CDN, attachment.archiveCdn)
        put(ARCHIVE_TRANSFER_STATE, if (attachment.archiveCdn != null) ArchiveTransferState.FINISHED.value else ArchiveTransferState.NONE.value)
        put(THUMBNAIL_RESTORE_STATE, if (attachment.archiveCdn != null) ThumbnailRestoreState.NEEDS_RESTORE.value else ThumbnailRestoreState.NONE.value)
        put(ATTACHMENT_UUID, attachment.uuid?.toString())
        put(BLUR_HASH, attachment.blurHash?.hash)

        if (plaintextHash != null) {
          put(DATA_HASH_START, plaintextHash)
          put(DATA_HASH_END, plaintextHash)
        }

        attachment.stickerLocator?.let { sticker ->
          put(STICKER_PACK_ID, sticker.packId)
          put(STICKER_PACK_KEY, sticker.packKey)
          put(STICKER_ID, sticker.stickerId)
          put(STICKER_EMOJI, sticker.emoji)
        }

        if (attachment.incrementalDigest?.isNotEmpty() == true && attachment.incrementalMacChunkSize != 0) {
          put(REMOTE_INCREMENTAL_DIGEST, attachment.incrementalDigest)
          put(REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE, attachment.incrementalMacChunkSize)
        } else {
          putNull(REMOTE_INCREMENTAL_DIGEST)
        }
      }

      val rowId = db.insert(TABLE_NAME, null, contentValues)
      AttachmentId(rowId)
    }

    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
    return attachmentId
  }

  /**
   * Inserts an incoming sticker with pre-existing local data (i.e., the sticker pack is installed).
   */
  @Throws(MmsException::class)
  private fun insertLocalStickerAttachment(messageId: Long, stickerAttachment: LocalStickerAttachment): AttachmentId {
    Log.d(TAG, "[insertLocalStickerAttachment] Inserting attachment for messageId $messageId. (MessageId: $messageId, ${stickerAttachment.uri})")

    // find sticker record and reuse
    var attachmentId: AttachmentId? = null

    writableDatabase.withinTransaction { db ->
      val match = db.select()
        .from(TABLE_NAME)
        .where("$DATA_FILE NOT NULL AND $DATA_RANDOM NOT NULL AND $STICKER_PACK_ID = ? AND $STICKER_ID = ?", stickerAttachment.packId, stickerAttachment.stickerId)
        .run()
        .readToSingleObject {
          it.readAttachment() to it.readDataFileInfo()!!
        }

      if (match != null) {
        val (attachment, dataFileInfo) = match

        Log.i(TAG, "[insertLocalStickerAttachment] Found that the sticker matches an existing sticker attachment: ${attachment.attachmentId}. Using all of it's fields. (MessageId: $messageId, ${attachment.uri})")

        val contentValues = ContentValues().apply {
          put(MESSAGE_ID, messageId)
          put(CONTENT_TYPE, attachment.contentType)
          put(REMOTE_KEY, attachment.remoteKey)
          put(REMOTE_LOCATION, attachment.remoteLocation)
          put(REMOTE_DIGEST, attachment.remoteDigest)
          put(CDN_NUMBER, attachment.cdn.serialize())
          put(TRANSFER_STATE, attachment.transferState)
          put(DATA_FILE, dataFileInfo.file.absolutePath)
          put(DATA_SIZE, attachment.size)
          put(DATA_RANDOM, dataFileInfo.random)
          put(FAST_PREFLIGHT_ID, stickerAttachment.fastPreflightId)
          put(WIDTH, attachment.width)
          put(HEIGHT, attachment.height)
          put(STICKER_PACK_ID, attachment.stickerLocator!!.packId)
          put(STICKER_PACK_KEY, attachment.stickerLocator.packKey)
          put(STICKER_ID, attachment.stickerLocator.stickerId)
          put(STICKER_EMOJI, attachment.stickerLocator.emoji)
          put(BLUR_HASH, attachment.blurHash?.hash)
          put(UPLOAD_TIMESTAMP, attachment.uploadTimestamp)
          put(DATA_HASH_START, dataFileInfo.hashStart)
          put(DATA_HASH_END, dataFileInfo.hashEnd ?: dataFileInfo.hashStart)
          put(ARCHIVE_CDN, attachment.archiveCdn)
          put(ARCHIVE_TRANSFER_STATE, attachment.archiveTransferState.value)
          put(THUMBNAIL_RESTORE_STATE, dataFileInfo.thumbnailRestoreState)
          put(THUMBNAIL_RANDOM, dataFileInfo.thumbnailRandom)
          put(THUMBNAIL_FILE, dataFileInfo.thumbnailFile)
          put(ATTACHMENT_UUID, stickerAttachment.uuid?.toString())
        }

        val rowId = db.insert(TABLE_NAME, null, contentValues)
        attachmentId = AttachmentId(rowId)
      }
    }

    if (attachmentId == null) {
      val dataStream = try {
        PartAuthority.getAttachmentStream(context, stickerAttachment.uri)
      } catch (e: IOException) {
        throw MmsException(e)
      }
      val fileWriteResult: DataFileWriteResult = writeToDataFile(newDataFile(context), dataStream, stickerAttachment.transformProperties ?: TransformProperties.empty())
      Log.d(TAG, "[insertLocalStickerAttachment] Wrote data to file: ${fileWriteResult.file.absolutePath} (MessageId: $messageId, ${stickerAttachment.uri})")
      val remoteKey = Util.getSecretBytes(64)

      val contentValues = ContentValues().apply {
        put(MESSAGE_ID, messageId)
        put(CONTENT_TYPE, stickerAttachment.contentType)
        put(REMOTE_KEY, Base64.encodeWithPadding(remoteKey))
        put(TRANSFER_STATE, stickerAttachment.transferState)
        put(DATA_FILE, fileWriteResult.file.absolutePath)
        put(DATA_SIZE, fileWriteResult.length)
        put(DATA_RANDOM, fileWriteResult.random)
        put(FAST_PREFLIGHT_ID, stickerAttachment.fastPreflightId)
        put(WIDTH, stickerAttachment.width)
        put(HEIGHT, stickerAttachment.height)
        put(STICKER_PACK_ID, stickerAttachment.stickerLocator!!.packId)
        put(STICKER_PACK_KEY, stickerAttachment.stickerLocator.packKey)
        put(STICKER_ID, stickerAttachment.stickerLocator.stickerId)
        put(STICKER_EMOJI, stickerAttachment.stickerLocator.emoji)
        put(DATA_HASH_START, fileWriteResult.hash)
        put(DATA_HASH_END, fileWriteResult.hash)
        put(ATTACHMENT_UUID, stickerAttachment.uuid?.toString())
      }

      val rowId = writableDatabase.insert(TABLE_NAME, null, contentValues)
      attachmentId = AttachmentId(rowId)
    }

    return attachmentId as AttachmentId
  }

  /**
   * Inserts an attachment with existing data. This is likely an outgoing attachment that we're in the process of sending.
   */
  @Throws(MmsException::class)
  private fun insertAttachmentWithData(messageId: Long, attachment: Attachment): AttachmentId {
    requireNotNull(attachment.uri) { "Attachment must have a uri!" }

    Log.d(TAG, "[insertAttachmentWithData] Inserting attachment for messageId $messageId. (MessageId: $messageId, ${attachment.uri})")

    val dataStream = try {
      PartAuthority.getAttachmentStream(context, attachment.uri!!)
    } catch (e: IOException) {
      throw MmsException(e)
    }

    return insertAttachmentWithData(messageId, dataStream, attachment, quote = false, quoteTargetContentType = null)
  }

  /**
   * Inserts an attachment with existing data. This is likely an outgoing attachment that we're in the process of sending.
   *
   * @param dataStream The stream to read the data from. This stream will be closed by this method.
   */
  @Throws(MmsException::class)
  private fun insertAttachmentWithData(messageId: Long, dataStream: InputStream, attachment: Attachment, quote: Boolean, quoteTargetContentType: String?): AttachmentId {
    // To avoid performing long-running operations in a transaction, we write the data to an independent file first in a way that doesn't rely on db state.
    val fileWriteResult: DataFileWriteResult = writeToDataFile(newDataFile(context), dataStream, attachment.transformProperties ?: TransformProperties.empty())
    Log.d(TAG, "[insertAttachmentWithData] Wrote data to file: ${fileWriteResult.file.absolutePath} (MessageId: $messageId, ${attachment.uri})")

    val (attachmentId: AttachmentId, foundDuplicate: Boolean) = writableDatabase.withinTransaction { db ->
      val contentValues = ContentValues()
      var transformProperties = attachment.transformProperties ?: TransformProperties.empty()

      // First we'll check if our file hash matches the starting or ending hash of any other attachments and has compatible transform properties.
      // We'll prefer the match with the most recent upload timestamp.
      val hashMatch: DataFileInfo? = readableDatabase
        .select(*DATA_FILE_INFO_PROJECTION)
        .from(TABLE_NAME)
        .where("$DATA_FILE NOT NULL AND ($DATA_HASH_START = ? OR $DATA_HASH_END = ?)", fileWriteResult.hash, fileWriteResult.hash)
        .run()
        .readToList { it.readDataFileInfo() }
        .filterNotNull()
        .sortedByDescending { it.uploadTimestamp }
        .firstOrNull { existingMatch ->
          areTransformationsCompatible(
            newProperties = transformProperties,
            potentialMatchProperties = existingMatch.transformProperties,
            newHashStart = fileWriteResult.hash,
            potentialMatchHashEnd = existingMatch.hashEnd,
            newIsQuote = quote
          )
        }

      if (hashMatch != null) {
        when (fileWriteResult.hash) {
          hashMatch.hashStart -> {
            Log.i(TAG, "[insertAttachmentWithData] Found that the new attachment hash matches the DATA_HASH_START of ${hashMatch.id}. Using all of it's fields. (MessageId: $messageId, ${attachment.uri})")
          }

          hashMatch.hashEnd -> {
            Log.i(TAG, "[insertAttachmentWithData] Found that the new attachment hash matches the DATA_HASH_END of ${hashMatch.id}. Using all of it's fields. (MessageId: $messageId, ${attachment.uri})")
          }

          else -> {
            throw IllegalStateException("Should not be possible based on query.")
          }
        }

        contentValues.put(DATA_FILE, hashMatch.file.absolutePath)
        contentValues.put(DATA_SIZE, hashMatch.length)
        contentValues.put(DATA_RANDOM, hashMatch.random)
        contentValues.put(DATA_HASH_START, fileWriteResult.hash)
        contentValues.put(DATA_HASH_END, hashMatch.hashEnd)

        if (hashMatch.transformProperties.skipTransform) {
          Log.i(TAG, "[insertAttachmentWithData] The hash match has a DATA_HASH_END and skipTransform=true, so skipping transform of the new file as well. (MessageId: $messageId, ${attachment.uri})")
          transformProperties = transformProperties.copy(skipTransform = true)
        }
      } else {
        Log.i(TAG, "[insertAttachmentWithData] No matching hash found. (MessageId: $messageId, ${attachment.uri})")
        contentValues.put(DATA_FILE, fileWriteResult.file.absolutePath)
        contentValues.put(DATA_SIZE, fileWriteResult.length)
        contentValues.put(DATA_RANDOM, fileWriteResult.random)
        contentValues.put(DATA_HASH_START, fileWriteResult.hash)
      }

      // Our hashMatch already represents a transform-compatible attachment with the most recent upload timestamp. We just need to make sure it has all of the
      // other necessary fields, and if so, we can use that to skip the upload.
      var uploadTemplate: DatabaseAttachment? = null
      if (hashMatch?.hashEnd != null && System.currentTimeMillis() - hashMatch.uploadTimestamp < AttachmentUploadJob.UPLOAD_REUSE_THRESHOLD) {
        uploadTemplate = readableDatabase
          .select(*PROJECTION)
          .from(TABLE_NAME)
          .where("$ID = ${hashMatch.id.id} AND $REMOTE_DIGEST NOT NULL AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND $DATA_HASH_END NOT NULL")
          .run()
          .readToSingleObject { it.readAttachment() }
      }

      if (uploadTemplate != null) {
        Log.i(
          TAG,
          "[insertAttachmentWithData] Found a valid template we could use to skip upload. Template: ${uploadTemplate.attachmentId}, TemplateUploadTimestamp: ${hashMatch?.uploadTimestamp}, CurrentTime: ${System.currentTimeMillis()}, InsertingAttachment: (MessageId: $messageId, ${attachment.uri})"
        )
        transformProperties = (uploadTemplate.transformProperties ?: transformProperties).copy(skipTransform = true)

        contentValues.put(ARCHIVE_CDN, hashMatch!!.archiveCdn)
        contentValues.put(ARCHIVE_TRANSFER_STATE, hashMatch.archiveTransferState)
      }

      contentValues.put(MESSAGE_ID, messageId)
      contentValues.put(CONTENT_TYPE, uploadTemplate?.contentType ?: attachment.contentType)
      contentValues.put(TRANSFER_STATE, attachment.transferState) // Even if we have a template, we let AttachmentUploadJob have the final say so it can re-check and make sure the template is still valid
      contentValues.put(CDN_NUMBER, uploadTemplate?.cdn?.serialize() ?: Cdn.CDN_0.serialize())
      contentValues.put(REMOTE_LOCATION, uploadTemplate?.remoteLocation)
      contentValues.put(REMOTE_DIGEST, uploadTemplate?.remoteDigest)
      contentValues.put(REMOTE_KEY, uploadTemplate?.remoteKey)
      contentValues.put(FILE_NAME, StorageUtil.getCleanFileName(attachment.fileName))
      contentValues.put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
      contentValues.put(VOICE_NOTE, if (attachment.voiceNote) 1 else 0)
      contentValues.put(BORDERLESS, if (attachment.borderless) 1 else 0)
      contentValues.put(VIDEO_GIF, if (attachment.videoGif) 1 else 0)
      contentValues.put(WIDTH, uploadTemplate?.width ?: attachment.width)
      contentValues.put(HEIGHT, uploadTemplate?.height ?: attachment.height)
      contentValues.put(QUOTE, quote.toInt())
      contentValues.put(QUOTE_TARGET_CONTENT_TYPE, quoteTargetContentType)
      contentValues.put(CAPTION, attachment.caption)
      contentValues.put(UPLOAD_TIMESTAMP, uploadTemplate?.uploadTimestamp ?: 0)
      contentValues.put(TRANSFORM_PROPERTIES, transformProperties.serialize())
      contentValues.put(ATTACHMENT_UUID, attachment.uuid?.toString())

      if (uploadTemplate?.incrementalDigest?.isNotEmpty() == true && uploadTemplate.incrementalMacChunkSize != 0) {
        contentValues.put(REMOTE_INCREMENTAL_DIGEST, uploadTemplate.incrementalDigest)
        contentValues.put(REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE, uploadTemplate.incrementalMacChunkSize)
      } else {
        contentValues.putNull(REMOTE_INCREMENTAL_DIGEST)
      }

      if (attachment.transformProperties?.videoTrimStartTimeUs != 0L) {
        contentValues.putNull(BLUR_HASH)
      } else {
        contentValues.put(BLUR_HASH, uploadTemplate.getVisualHashStringOrNull())
      }

      attachment.stickerLocator?.let { sticker ->
        contentValues.put(STICKER_PACK_ID, sticker.packId)
        contentValues.put(STICKER_PACK_KEY, sticker.packKey)
        contentValues.put(STICKER_ID, sticker.stickerId)
        contentValues.put(STICKER_EMOJI, sticker.emoji)
      }

      val rowId = db.insert(TABLE_NAME, null, contentValues)

      AttachmentId(rowId) to (hashMatch != null)
    }

    if (foundDuplicate) {
      if (!fileWriteResult.file.delete()) {
        Log.w(TAG, "[insertAttachmentWithData] Failed to delete duplicate file: ${fileWriteResult.file.absolutePath}")
      }
    }

    AppDependencies.databaseObserver.notifyAttachmentUpdatedObservers()
    return attachmentId
  }

  fun insertWallpaper(dataStream: InputStream): AttachmentId {
    return insertAttachmentWithData(WALLPAPER_MESSAGE_ID, dataStream, WallpaperAttachment(), quote = false, quoteTargetContentType = null).also { id ->
      createRemoteKeyIfNecessary(id)
    }
  }

  fun getAllWallpapers(): List<AttachmentId> {
    return readableDatabase
      .select(ID)
      .from(TABLE_NAME)
      .where("$MESSAGE_ID = $WALLPAPER_MESSAGE_ID")
      .run()
      .readToList { AttachmentId(it.requireLong(ID)) }
  }

  fun getEstimatedArchiveMediaSize(): Long {
    val estimatedThumbnailCount = readableDatabase
      .select("COUNT(*)")
      .from(
        """
        (
          SELECT DISTINCT $DATA_HASH_END, $REMOTE_KEY
          FROM $TABLE_NAME INNER JOIN ${MessageTable.TABLE_NAME} AS m ON $TABLE_NAME.$MESSAGE_ID = m.${MessageTable.ID}
          WHERE 
            $DATA_FILE NOT NULL AND 
            $DATA_HASH_END NOT NULL AND 
            $REMOTE_KEY NOT NULL AND
            $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND
            $ARCHIVE_TRANSFER_STATE != ${ArchiveTransferState.PERMANENT_FAILURE.value} AND 
            ($CONTENT_TYPE LIKE 'image/%' OR $CONTENT_TYPE LIKE 'video/%') AND
            $CONTENT_TYPE != 'image/svg+xml' AND
            ${getMessageDoesNotExpireWithinTimeoutClause(tablePrefix = "m")}
        )
        """
      )
      .run()
      .readToSingleLong(0L)

    val uploadedAttachmentBytes = readableDatabase
      .rawQuery(
        """
          SELECT $DATA_SIZE
          FROM (
            SELECT DISTINCT $DATA_HASH_END, $REMOTE_KEY, $DATA_SIZE
            FROM $TABLE_NAME INNER JOIN ${MessageTable.TABLE_NAME} AS m ON $TABLE_NAME.$MESSAGE_ID = m.${MessageTable.ID}
            WHERE 
              $DATA_FILE NOT NULL AND 
              $DATA_HASH_END NOT NULL AND 
              $REMOTE_KEY NOT NULL AND 
              $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND
              $ARCHIVE_TRANSFER_STATE != ${ArchiveTransferState.PERMANENT_FAILURE.value} AND
              ${getMessageDoesNotExpireWithinTimeoutClause(tablePrefix = "m")}
          )
        """
      )
      .readToList { it.requireLong(DATA_SIZE) }
      .sumOf {
        val paddedSize = PaddingInputStream.getPaddedSize(it)
        val clientEncryptedSize = AttachmentCipherStreamUtil.getCiphertextLength(paddedSize)
        val serverEncryptedSize = AttachmentCipherStreamUtil.getCiphertextLength(clientEncryptedSize)

        serverEncryptedSize
      }

    val estimatedUploadedThumbnailBytes = RemoteConfig.backupMaxThumbnailFileSize.inWholeBytes * estimatedThumbnailCount

    return uploadedAttachmentBytes + estimatedUploadedThumbnailBytes
  }

  private fun getTransferFile(db: SQLiteDatabase, attachmentId: AttachmentId): File? {
    return db
      .select(TRANSFER_FILE)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .limit(1)
      .run()
      .readToSingleObject { cursor ->
        cursor.requireString(TRANSFER_FILE)?.let { File(it) }
      }
  }

  private fun buildAttachmentsThatNeedUploadQuery(transferStateFilter: String = "$ARCHIVE_TRANSFER_STATE IN (${ArchiveTransferState.NONE.value}, ${ArchiveTransferState.TEMPORARY_FAILURE.value})"): String {
    return """
      $transferStateFilter AND
      $DATA_FILE NOT NULL AND 
      $REMOTE_KEY NOT NULL AND
      $DATA_HASH_END NOT NULL AND
      $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND 
      (${MessageTable.STORY_TYPE} = 0 OR ${MessageTable.STORY_TYPE} IS NULL) AND 
      (${MessageTable.TABLE_NAME}.${MessageTable.EXPIRES_IN} <= 0 OR ${MessageTable.TABLE_NAME}.${MessageTable.EXPIRES_IN} > ${ChatItemArchiveExporter.EXPIRATION_CUTOFF.inWholeMilliseconds}) AND
      $CONTENT_TYPE != '${MediaUtil.LONG_TEXT}' AND
      ${MessageTable.TABLE_NAME}.${MessageTable.VIEW_ONCE} = 0
    """
  }
  private fun getAttachment(cursor: Cursor): DatabaseAttachment {
    val contentType = cursor.requireString(CONTENT_TYPE)

    return DatabaseAttachment(
      attachmentId = AttachmentId(cursor.requireLong(ID)),
      mmsId = cursor.requireLong(MESSAGE_ID),
      hasData = !cursor.isNull(DATA_FILE),
      hasThumbnail = MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType),
      contentType = contentType,
      transferProgress = cursor.requireInt(TRANSFER_STATE),
      size = cursor.requireLong(DATA_SIZE),
      fileName = cursor.requireString(FILE_NAME),
      cdn = cursor.requireObject(CDN_NUMBER, Cdn),
      location = cursor.requireString(REMOTE_LOCATION),
      key = cursor.requireString(REMOTE_KEY),
      digest = cursor.requireBlob(REMOTE_DIGEST),
      incrementalDigest = cursor.requireBlob(REMOTE_INCREMENTAL_DIGEST),
      incrementalMacChunkSize = cursor.requireInt(REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE),
      fastPreflightId = cursor.requireString(FAST_PREFLIGHT_ID),
      voiceNote = cursor.requireBoolean(VOICE_NOTE),
      borderless = cursor.requireBoolean(BORDERLESS),
      videoGif = cursor.requireBoolean(VIDEO_GIF),
      width = cursor.requireInt(WIDTH),
      height = cursor.requireInt(HEIGHT),
      quote = cursor.requireBoolean(QUOTE),
      quoteTargetContentType = cursor.requireString(QUOTE_TARGET_CONTENT_TYPE),
      caption = cursor.requireString(CAPTION),
      stickerLocator = cursor.readStickerLocator(),
      blurHash = if (MediaUtil.isAudioType(contentType)) null else BlurHash.parseOrNull(cursor.requireString(BLUR_HASH)),
      audioHash = if (MediaUtil.isAudioType(contentType)) AudioHash.parseOrNull(cursor.requireString(BLUR_HASH)) else null,
      transformProperties = TransformProperties.parse(cursor.requireString(TRANSFORM_PROPERTIES)),
      displayOrder = cursor.requireInt(DISPLAY_ORDER),
      uploadTimestamp = cursor.requireLong(UPLOAD_TIMESTAMP),
      dataHash = cursor.requireString(DATA_HASH_END),
      archiveCdn = cursor.requireIntOrNull(ARCHIVE_CDN),
      thumbnailRestoreState = ThumbnailRestoreState.deserialize(cursor.requireInt(THUMBNAIL_RESTORE_STATE)),
      archiveTransferState = ArchiveTransferState.deserialize(cursor.requireInt(ARCHIVE_TRANSFER_STATE)),
      uuid = UuidUtil.parseOrNull(cursor.requireString(ATTACHMENT_UUID))
    )
  }

  private fun Cursor.readAttachments(): List<DatabaseAttachment> {
    return getAttachments(this)
  }

  private fun Cursor.readAttachment(): DatabaseAttachment {
    return getAttachment(this)
  }

  private fun Cursor.readDataFileInfo(): DataFileInfo? {
    val filePath: String = this.requireString(DATA_FILE) ?: return null
    val random: ByteArray = this.requireBlob(DATA_RANDOM) ?: return null

    return DataFileInfo(
      id = AttachmentId(this.requireLong(ID)),
      file = File(filePath),
      length = this.requireLong(DATA_SIZE),
      random = random,
      hashStart = this.requireString(DATA_HASH_START),
      hashEnd = this.requireString(DATA_HASH_END),
      transformProperties = TransformProperties.parse(this.requireString(TRANSFORM_PROPERTIES)),
      uploadTimestamp = this.requireLong(UPLOAD_TIMESTAMP),
      archiveCdn = this.requireIntOrNull(ARCHIVE_CDN),
      archiveTransferState = this.requireInt(ARCHIVE_TRANSFER_STATE),
      thumbnailFile = this.requireString(THUMBNAIL_FILE),
      thumbnailRandom = this.requireBlob(THUMBNAIL_RANDOM),
      thumbnailRestoreState = this.requireInt(THUMBNAIL_RESTORE_STATE)
    )
  }

  private fun Cursor.readThumbnailFileInfo(): ThumbnailFileInfo {
    return ThumbnailFileInfo(
      id = AttachmentId(this.requireLong(ID)),
      file = File(this.requireNonNullString(THUMBNAIL_FILE)),
      random = this.requireNonNullBlob(THUMBNAIL_RANDOM)
    )
  }

  private fun Cursor.readStickerLocator(): StickerLocator? {
    return if (this.requireInt(STICKER_ID) >= 0) {
      StickerLocator(
        packId = this.requireNonNullString(STICKER_PACK_ID),
        packKey = this.requireNonNullString(STICKER_PACK_KEY),
        stickerId = this.requireInt(STICKER_ID),
        emoji = this.requireString(STICKER_EMOJI)
      )
    } else {
      null
    }
  }

  private fun Attachment?.getVisualHashStringOrNull(): String? {
    return when {
      this == null -> null
      this.blurHash != null -> this.blurHash.hash
      this.audioHash != null -> this.audioHash.hash
      else -> null
    }
  }

  /**
   * Important: This is an expensive query that involves iterating over every row in the table. Only call this for debug stuff!
   */
  fun debugGetAttachmentsForMediaIds(mediaIds: Set<MediaId>, limit: Int): List<Pair<DatabaseAttachment, Boolean>> {
    val byteStringMediaIds: Set<ByteString> = mediaIds.map { it.value.toByteString() }.toSet()
    val found = mutableListOf<Pair<DatabaseAttachment, Boolean>>()

    readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$REMOTE_KEY NOT NULL AND $DATA_HASH_END NOT NULL")
      .run()
      .forEach { cursor ->
        val remoteKey = Base64.decode(cursor.requireNonNullString(REMOTE_KEY))
        val plaintextHash = Base64.decode(cursor.requireNonNullString(DATA_HASH_END))
        val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).value.toByteString()
        val mediaIdThumbnail = MediaName.fromPlaintextHashAndRemoteKeyForThumbnail(plaintextHash, remoteKey).toMediaId(SignalStore.backup.mediaRootBackupKey).value.toByteString()

        if (mediaId in byteStringMediaIds) {
          found.add(getAttachment(cursor) to false)
        }

        if (mediaIdThumbnail in byteStringMediaIds) {
          found.add(getAttachment(cursor) to true)
        }

        if (found.size >= limit) return@forEach
      }

    return found
  }

  fun debugGetAttachmentStats(): DebugAttachmentStats {
    val totalAttachmentRows = readableDatabase.count().from(TABLE_NAME).run().readToSingleLong(0)
    val totalEligibleForUploadRows = getFullSizeAttachmentsThatWillBeIncludedInArchive().count

    val totalUniqueDataFiles = readableDatabase.select("COUNT(DISTINCT $DATA_FILE)").from(TABLE_NAME).run().readToSingleLong(0)
    val totalUniqueMediaNames = readableDatabase.query("SELECT COUNT(*) FROM (SELECT DISTINCT $DATA_HASH_END, $REMOTE_KEY FROM $TABLE_NAME WHERE $DATA_HASH_END NOT NULL AND $REMOTE_KEY NOT NULL)").readToSingleLong(0)

    val totalUniqueMediaNamesEligibleForUpload = readableDatabase.query(
      """
        SELECT COUNT(*) FROM (
          SELECT DISTINCT $DATA_HASH_END, $REMOTE_KEY
          FROM $TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON $TABLE_NAME.$MESSAGE_ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID}
          WHERE ${buildAttachmentsThatNeedUploadQuery(transferStateFilter = "$ARCHIVE_TRANSFER_STATE != ${ArchiveTransferState.PERMANENT_FAILURE.value}")}
        )
        """
    )
      .readToSingleLong(0)

    val archiveStatusMediaNameCounts: Map<ArchiveTransferState, Long> = ArchiveTransferState.entries.associateWith { state ->
      readableDatabase.query(
        """
        SELECT COUNT(*) FROM (
          SELECT DISTINCT $DATA_HASH_END, $REMOTE_KEY
          FROM $TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON $TABLE_NAME.$MESSAGE_ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID}
          WHERE ${buildAttachmentsThatNeedUploadQuery(transferStateFilter = "$ARCHIVE_TRANSFER_STATE = ${state.value}")}
        )
        """
      )
        .readToSingleLong(0)
    }

    val uniqueEligibleMediaNamesWithThumbnailsCount = readableDatabase.query("SELECT COUNT(*) FROM (SELECT DISTINCT $DATA_HASH_END, $REMOTE_KEY FROM $TABLE_NAME WHERE $DATA_HASH_END NOT NULL AND $REMOTE_KEY NOT NULL AND $THUMBNAIL_FILE NOT NULL)").readToSingleLong(-1L)
    val archiveStatusMediaNameThumbnailCounts: Map<ArchiveTransferState, Long> = ArchiveTransferState.entries.associateWith { state ->
      readableDatabase.query(
        """
        SELECT COUNT(*) FROM (
          SELECT DISTINCT $DATA_HASH_END, $REMOTE_KEY
          FROM $TABLE_NAME LEFT JOIN ${MessageTable.TABLE_NAME} ON $TABLE_NAME.$MESSAGE_ID = ${MessageTable.TABLE_NAME}.${MessageTable.ID}
          WHERE 
            $ARCHIVE_THUMBNAIL_TRANSFER_STATE = ${state.value} AND
            $ARCHIVE_TRANSFER_STATE = ${ArchiveTransferState.FINISHED.value} AND
            $QUOTE = 0 AND
            ($CONTENT_TYPE LIKE 'image/%' OR $CONTENT_TYPE LIKE 'video/%') AND
            $CONTENT_TYPE != 'image/svg+xml'
        )
        """
      )
        .readToSingleLong(0)
    }

    val pendingAttachmentUploadBytes = getPendingArchiveUploadBytes()
    val uploadedAttachmentBytes = readableDatabase
      .rawQuery(
        """
          SELECT $DATA_SIZE
          FROM (
            SELECT DISTINCT $DATA_HASH_END, $REMOTE_KEY, $DATA_SIZE
            FROM $TABLE_NAME
            WHERE 
              $DATA_FILE NOT NULL AND 
              $DATA_HASH_END NOT NULL AND 
              $REMOTE_KEY NOT NULL AND
              $ARCHIVE_TRANSFER_STATE = ${ArchiveTransferState.FINISHED.value}
          )
        """.trimIndent()
      )
      .readToList { it.requireLong(DATA_SIZE) }
      .sumOf { AttachmentCipherStreamUtil.getCiphertextLength(AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(it))) }

    val uploadedThumbnailCount = archiveStatusMediaNameThumbnailCounts.getOrDefault(ArchiveTransferState.FINISHED, 0L)
    val uploadedThumbnailBytes = uploadedThumbnailCount * RemoteConfig.backupMaxThumbnailFileSize.inWholeBytes

    return DebugAttachmentStats(
      totalAttachmentRows = totalAttachmentRows,
      totalEligibleForUploadRows = totalEligibleForUploadRows.toLong(),
      totalUniqueMediaNamesEligibleForUpload = totalUniqueMediaNamesEligibleForUpload,
      totalUniqueDataFiles = totalUniqueDataFiles,
      totalUniqueMediaNames = totalUniqueMediaNames,
      archiveStatusMediaNameCounts = archiveStatusMediaNameCounts,
      mediaNamesWithThumbnailsCount = uniqueEligibleMediaNamesWithThumbnailsCount,
      archiveStatusMediaNameThumbnailCounts = archiveStatusMediaNameThumbnailCounts,
      pendingAttachmentUploadBytes = pendingAttachmentUploadBytes,
      uploadedAttachmentBytes = uploadedAttachmentBytes,
      uploadedThumbnailBytes = uploadedThumbnailBytes
    )
  }

  fun getDebugMediaInfoForEntries(hashes: Collection<BackupMediaSnapshotTable.MediaEntry>): Set<DebugArchiveMediaInfo> {
    val entriesByHash = hashes.associateBy { Base64.encodeWithPadding(it.plaintextHash) }

    val query = SqlUtil.buildFastCollectionQuery(DATA_HASH_END, entriesByHash.keys)

    return readableDatabase
      .select(ID, MESSAGE_ID, CONTENT_TYPE, DATA_HASH_END)
      .from(TABLE_NAME)
      .where(query.where, query.whereArgs)
      .run()
      .readToSet { cursor ->
        DebugArchiveMediaInfo(
          attachmentId = AttachmentId(cursor.requireLong(ID)),
          messageId = cursor.requireLong(MESSAGE_ID),
          contentType = cursor.requireString(CONTENT_TYPE),
          isThumbnail = entriesByHash[cursor.requireString(DATA_HASH_END)]!!.isThumbnail
        )
      }
  }

  fun debugAttachmentStatsForBackupProto(): BackupDebugInfo.AttachmentDetails {
    val archiveStateCounts = ArchiveTransferState
      .entries.associateWith {
        readableDatabase
          .count()
          .from(TABLE_NAME)
          .where("$ARCHIVE_TRANSFER_STATE = ${it.value} AND $DATA_HASH_END NOT NULL AND $REMOTE_KEY NOT NULL")
          .run()
          .readToSingleLong(-1L)
      }

    return BackupDebugInfo.AttachmentDetails(
      notStartedCount = archiveStateCounts[ArchiveTransferState.NONE]?.toInt() ?: 0,
      uploadInProgressCount = archiveStateCounts[ArchiveTransferState.UPLOAD_IN_PROGRESS]?.toInt() ?: 0,
      copyPendingCount = archiveStateCounts[ArchiveTransferState.COPY_PENDING]?.toInt() ?: 0,
      finishedCount = archiveStateCounts[ArchiveTransferState.FINISHED]?.toInt() ?: 0,
      permanentFailureCount = archiveStateCounts[ArchiveTransferState.PERMANENT_FAILURE]?.toInt() ?: 0,
      temporaryFailureCount = archiveStateCounts[ArchiveTransferState.TEMPORARY_FAILURE]?.toInt() ?: 0
    )
  }

  /**
   * After restoring from the free-tier, it's possible we'll be missing many of our quoted replies.
   * This marks quotes with a special flag to indicate that they'd be eligible for reconstruction.
   * See [QUOTE_PENDING_RECONSTRUCTION].
   */
  fun markQuotesThatNeedReconstruction() {
    writableDatabase
      .update(TABLE_NAME)
      .values(QUOTE to QUOTE_PENDING_RECONSTRUCTION)
      .where("$QUOTE != 0 AND $DATA_FILE IS NULL AND $REMOTE_LOCATION IS NULL")
      .run()
  }

  /**
   * Retrieves data for the newest quote that is pending reconstruction (see [QUOTE_PENDING_RECONSTRUCTION]), if any.
   */
  fun getNewestQuotePendingReconstruction(): DatabaseAttachment? {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$QUOTE = $QUOTE_PENDING_RECONSTRUCTION")
      .orderBy("$ID DESC")
      .limit(1)
      .run()
      .readToSingleObject { it.readAttachment() }
  }

  /**
   * After reconstructing a thumbnail, this method can be used to write the data to the quote.
   * It'll handle duplicates as well as clearing the [QUOTE_PENDING_RECONSTRUCTION] flag.
   */
  @Throws(MmsException::class)
  fun applyReconstructedQuoteData(attachmentId: AttachmentId, thumbnail: ImageCompressionUtil.Result) {
    val newDataFileInfo = writeToDataFile(newDataFile(context), thumbnail.data.inputStream(), TransformProperties.empty())

    val foundDuplicate = writableDatabase.withinTransaction { db ->
      val existingMatch: DataFileInfo? = db
        .select(*DATA_FILE_INFO_PROJECTION)
        .from(TABLE_NAME)
        .where("$DATA_HASH_END = ?", newDataFileInfo.hash)
        .run()
        .readToSingleObject { it.readDataFileInfo() }

      db.update(TABLE_NAME)
        .values(
          DATA_FILE to (existingMatch?.file?.absolutePath ?: newDataFileInfo.file.absolutePath),
          DATA_SIZE to (existingMatch?.length ?: newDataFileInfo.length),
          DATA_RANDOM to (existingMatch?.random ?: newDataFileInfo.random),
          DATA_HASH_START to (existingMatch?.hashStart ?: newDataFileInfo.hash),
          DATA_HASH_END to (existingMatch?.hashEnd ?: newDataFileInfo.hash),
          CONTENT_TYPE to thumbnail.mimeType,
          WIDTH to thumbnail.width,
          HEIGHT to thumbnail.height,
          QUOTE to 1
        )
        .where("$ID = ?", attachmentId)
        .run()

      existingMatch != null
    }

    if (foundDuplicate) {
      if (!newDataFileInfo.file.delete()) {
        Log.w(TAG, "[applyReconstructedQuoteData] Failed to delete a duplicated file!")
      }
    }
  }

  /**
   * Clears the [QUOTE_PENDING_RECONSTRUCTION] status of an attachment. Used for when an error occurs and you can't call [applyReconstructedQuoteData].
   */
  fun clearQuotePendingReconstruction(attachmentId: AttachmentId) {
    writableDatabase
      .update(TABLE_NAME)
      .values(QUOTE to 1)
      .where("$ID = ?", attachmentId)
      .run()
  }

  /**
   * Clears all [QUOTE_PENDING_RECONSTRUCTION] flags on attachments.
   */
  fun clearAllQuotesPendingReconstruction() {
    writableDatabase
      .update(TABLE_NAME)
      .values(QUOTE to 1)
      .where("$QUOTE = $QUOTE_PENDING_RECONSTRUCTION")
      .run()
  }

  /**
   * Used in an app migration that creates quote thumbnails. Updates all quote attachments that share the same
   * [previousDataFile] to use the new thumbnail.
   *
   * Handling deduping shouldn't be necessary here because we're updating by the dataFile we used to generate
   * the thumbnail. It *is* theoretically possible that generating thumbnails for two different dataFiles
   * could result in the same output thumbnail... but that's fine. That rare scenario will result in some missed
   * disk savings.
   */
  @Throws(Exception::class)
  fun migrationFinalizeQuoteWithData(previousDataFile: String, thumbnail: ImageCompressionUtil.Result, quoteTargetContentType: String?): String {
    val newDataFileInfo = writeToDataFile(newDataFile(context), thumbnail.data.inputStream(), TransformProperties.empty())

    writableDatabase
      .update(TABLE_NAME)
      .values(
        DATA_FILE to newDataFileInfo.file.absolutePath,
        DATA_SIZE to newDataFileInfo.length,
        DATA_RANDOM to newDataFileInfo.random,
        DATA_HASH_START to newDataFileInfo.hash,
        DATA_HASH_END to newDataFileInfo.hash,
        CONTENT_TYPE to thumbnail.mimeType,
        QUOTE_TARGET_CONTENT_TYPE to quoteTargetContentType,
        WIDTH to thumbnail.width,
        HEIGHT to thumbnail.height,
        QUOTE to 1
      )
      .where("$DATA_FILE = ? AND $QUOTE != 0", previousDataFile)
      .run()

    return newDataFileInfo.file.absolutePath
  }

  class DataFileWriteResult(
    val file: File,
    val length: Long,
    val random: ByteArray,
    val hash: String,
    val transformProperties: TransformProperties
  )

  @VisibleForTesting
  class DataFileInfo(
    val id: AttachmentId,
    val file: File,
    val length: Long,
    val random: ByteArray,
    val hashStart: String?,
    val hashEnd: String?,
    val transformProperties: TransformProperties,
    val uploadTimestamp: Long,
    val archiveCdn: Int?,
    val archiveTransferState: Int,
    val thumbnailFile: String?,
    val thumbnailRandom: ByteArray?,
    val thumbnailRestoreState: Int
  )

  @VisibleForTesting
  class ThumbnailFileInfo(
    val id: AttachmentId,
    val file: File,
    val random: ByteArray
  )

  @Parcelize
  data class TransformProperties(
    @JsonProperty("skipTransform")
    @JvmField
    val skipTransform: Boolean = false,

    @JsonProperty("videoTrim")
    @JvmField
    val videoTrim: Boolean = false,

    @JsonProperty("videoTrimStartTimeUs")
    @JvmField
    val videoTrimStartTimeUs: Long = 0,

    @JsonProperty("videoTrimEndTimeUs")
    @JvmField
    val videoTrimEndTimeUs: Long = 0,

    @JsonProperty("sentMediaQuality")
    @JvmField
    val sentMediaQuality: Int = SentMediaQuality.STANDARD.code,

    @JsonProperty("mp4Faststart")
    @JvmField
    val mp4FastStart: Boolean = false
  ) : Parcelable {
    fun shouldSkipTransform(): Boolean {
      return skipTransform
    }

    @IgnoredOnParcel
    @JsonProperty("videoEdited")
    val videoEdited: Boolean = videoTrim

    fun withSkipTransform(): TransformProperties {
      return this.copy(
        skipTransform = true
      )
    }

    fun withMp4FastStart(): TransformProperties {
      return this.copy(mp4FastStart = true)
    }

    fun serialize(): String {
      return JsonUtil.toJson(this)
    }

    companion object {
      private val DEFAULT_MEDIA_QUALITY = SentMediaQuality.STANDARD.code

      @JvmStatic
      fun empty(): TransformProperties {
        return TransformProperties(
          skipTransform = false,
          videoTrim = false,
          videoTrimStartTimeUs = 0,
          videoTrimEndTimeUs = 0,
          sentMediaQuality = DEFAULT_MEDIA_QUALITY,
          mp4FastStart = false
        )
      }

      fun forSkipTransform(): TransformProperties {
        return TransformProperties(
          skipTransform = true,
          videoTrim = false,
          videoTrimStartTimeUs = 0,
          videoTrimEndTimeUs = 0,
          sentMediaQuality = DEFAULT_MEDIA_QUALITY,
          mp4FastStart = false
        )
      }

      fun forVideoTrim(videoTrimStartTimeUs: Long, videoTrimEndTimeUs: Long): TransformProperties {
        return TransformProperties(
          skipTransform = false,
          videoTrim = true,
          videoTrimStartTimeUs = videoTrimStartTimeUs,
          videoTrimEndTimeUs = videoTrimEndTimeUs,
          sentMediaQuality = DEFAULT_MEDIA_QUALITY,
          mp4FastStart = false
        )
      }

      @JvmStatic
      fun forSentMediaQuality(currentProperties: Optional<TransformProperties>, sentMediaQuality: SentMediaQuality): TransformProperties {
        val existing = currentProperties.orElse(empty())
        return existing.copy(sentMediaQuality = sentMediaQuality.code)
      }

      @JvmStatic
      fun forSentMediaQuality(sentMediaQuality: Int): TransformProperties {
        return TransformProperties(sentMediaQuality = sentMediaQuality)
      }

      @JvmStatic
      fun parse(serialized: String?): TransformProperties {
        return if (serialized == null) {
          empty()
        } else {
          try {
            JsonUtil.fromJson(serialized, TransformProperties::class.java)
          } catch (e: IOException) {
            Log.w(TAG, "Failed to parse TransformProperties!", e)
            empty()
          }
        }
      }
    }
  }

  enum class ThumbnailRestoreState(val value: Int) {
    /** No thumbnail downloaded. */
    NONE(0),

    /** The thumbnail needs to be restored still. */
    NEEDS_RESTORE(1),

    /** The restore of the thumbnail is in progress */
    IN_PROGRESS(2),

    /** Completely restored the thumbnail. */
    FINISHED(3),

    /** It is impossible to restore the thumbnail. */
    PERMANENT_FAILURE(4);

    companion object {
      fun deserialize(value: Int): ThumbnailRestoreState {
        return entries.firstOrNull { it.value == value } ?: NONE
      }
    }
  }

  /**
   * This maintains two different state paths for uploading attachments to the archive.
   *
   * The first is the backfill process, which will happen after newly-enabling backups. That process will go:
   * 1. [NONE]
   * 2. [UPLOAD_IN_PROGRESS]
   * 3. [COPY_PENDING]
   * 4. [FINISHED] or [PERMANENT_FAILURE]
   *
   * The second is when newly sending/receiving an attachment after enabling backups. That process will go:
   * 1. [NONE]
   * 2. [COPY_PENDING]
   * 3. [FINISHED] or [PERMANENT_FAILURE]
   */
  enum class ArchiveTransferState(val value: Int) {
    /** Not backed up at all. */
    NONE(0),

    /** The upload to the attachment service is in progress. */
    UPLOAD_IN_PROGRESS(1),

    /** We sent/received this attachment after enabling backups, but still need to transfer the file to the archive service. */
    COPY_PENDING(2),

    /** Completely finished backing up the attachment. */
    FINISHED(3),

    /** It is impossible to upload this attachment. */
    PERMANENT_FAILURE(4),

    /** Upload failed, but in a way where it may be worth retrying later. */
    TEMPORARY_FAILURE(5);

    companion object {
      fun deserialize(value: Int): ArchiveTransferState {
        return entries.firstOrNull { it.value == value } ?: NONE
      }
    }
  }

  class SyncAttachmentId(val syncMessageId: SyncMessageId, val uuid: UUID?, val digest: ByteArray?, val plaintextHash: String?)

  class SyncAttachment(val id: AttachmentId, val uuid: UUID?, val digest: ByteArray?, val plaintextHash: String?)

  class LocalArchivableAttachment(
    val file: File,
    val random: ByteArray,
    val size: Long,
    val plaintextHash: ByteArray,
    val remoteKey: ByteArray
  )

  data class RestorableAttachment(
    val attachmentId: AttachmentId,
    val mmsId: Long,
    val size: Long,
    val plaintextHash: ByteArray?,
    val remoteKey: ByteArray?,
    val stickerPackId: String?
  ) {
    override fun equals(other: Any?): Boolean {
      return this === other || attachmentId == (other as? RestorableAttachment)?.attachmentId
    }

    override fun hashCode(): Int {
      return attachmentId.hashCode()
    }
  }

  data class DebugAttachmentStats(
    val totalAttachmentRows: Long = 0L,
    val totalEligibleForUploadRows: Long = 0L,
    val totalUniqueMediaNamesEligibleForUpload: Long = 0L,
    val totalUniqueDataFiles: Long = 0L,
    val totalUniqueMediaNames: Long = 0L,
    val archiveStatusMediaNameCounts: Map<ArchiveTransferState, Long> = emptyMap(),
    val mediaNamesWithThumbnailsCount: Long = 0L,
    val archiveStatusMediaNameThumbnailCounts: Map<ArchiveTransferState, Long> = emptyMap(),
    val pendingAttachmentUploadBytes: Long = 0L,
    val uploadedAttachmentBytes: Long = 0L,
    val uploadedThumbnailBytes: Long = 0L
  ) {
    val uploadedAttachmentCount get() = archiveStatusMediaNameCounts.getOrDefault(ArchiveTransferState.FINISHED, 0L)
    val uploadedThumbnailCount get() = archiveStatusMediaNameThumbnailCounts.getOrDefault(ArchiveTransferState.FINISHED, 0L)

    val totalUploadCount get() = uploadedAttachmentCount + uploadedThumbnailCount
    val totalUploadBytes get() = uploadedAttachmentBytes + uploadedThumbnailBytes
  }

  data class CreateRemoteKeyResult(val totalCount: Int, val notQuoteOrSickerDupeNotFoundCount: Int, val notQuoteOrSickerDupeFoundCount: Int) {
    val unexpectedKeyCreation = notQuoteOrSickerDupeFoundCount > 0 || notQuoteOrSickerDupeNotFoundCount > 0
  }

  class DebugArchiveMediaInfo(
    val attachmentId: AttachmentId,
    val messageId: Long,
    val contentType: String?,
    val isThumbnail: Boolean
  )
}

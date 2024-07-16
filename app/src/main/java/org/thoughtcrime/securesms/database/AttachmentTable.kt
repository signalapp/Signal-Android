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
import org.json.JSONArray
import org.json.JSONException
import org.signal.core.util.Base64
import org.signal.core.util.SqlUtil
import org.signal.core.util.StreamUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
import org.signal.core.util.drain
import org.signal.core.util.exists
import org.signal.core.util.forEach
import org.signal.core.util.groupBy
import org.signal.core.util.isNull
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireBlob
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
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
import org.thoughtcrime.securesms.audio.AudioHash
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.crypto.AttachmentSecret
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messages
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.stickers
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.threads
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob
import org.thoughtcrime.securesms.jobs.GenerateAudioWaveFormJob
import org.thoughtcrime.securesms.mms.MediaStream
import org.thoughtcrime.securesms.mms.MmsException
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.FileUtils
import org.thoughtcrime.securesms.util.JsonUtils.SaneJSONObject
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.StorageUtil
import org.thoughtcrime.securesms.video.EncryptedMediaDataSource
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.util.JsonUtil
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
    const val ARCHIVE_MEDIA_NAME = "archive_media_name"
    const val ARCHIVE_MEDIA_ID = "archive_media_id"
    const val ARCHIVE_THUMBNAIL_MEDIA_ID = "archive_thumbnail_media_id"
    const val ARCHIVE_THUMBNAIL_CDN = "archive_thumbnail_cdn"
    const val ARCHIVE_TRANSFER_FILE = "archive_transfer_file"
    const val ARCHIVE_TRANSFER_STATE = "archive_transfer_state"
    const val THUMBNAIL_RESTORE_STATE = "thumbnail_restore_state"
    const val ATTACHMENT_UUID = "attachment_uuid"

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
      ARCHIVE_THUMBNAIL_CDN,
      ARCHIVE_MEDIA_NAME,
      ARCHIVE_MEDIA_ID,
      ARCHIVE_TRANSFER_FILE,
      THUMBNAIL_FILE,
      THUMBNAIL_RESTORE_STATE,
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
        $ARCHIVE_CDN INTEGER DEFAULT 0,
        $ARCHIVE_MEDIA_NAME TEXT DEFAULT NULL,
        $ARCHIVE_MEDIA_ID TEXT DEFAULT NULL,
        $ARCHIVE_TRANSFER_FILE TEXT DEFAULT NULL,
        $ARCHIVE_TRANSFER_STATE INTEGER DEFAULT ${ArchiveTransferState.NONE.value},
        $ARCHIVE_THUMBNAIL_CDN INTEGER DEFAULT 0,
        $ARCHIVE_THUMBNAIL_MEDIA_ID TEXT DEFAULT NULL,
        $THUMBNAIL_FILE TEXT DEFAULT NULL,
        $THUMBNAIL_RANDOM BLOB DEFAULT NULL,
        $THUMBNAIL_RESTORE_STATE INTEGER DEFAULT ${ThumbnailRestoreState.NONE.value},
        $ATTACHMENT_UUID TEXT DEFAULT NULL
      )
      """

    @JvmField
    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS attachment_message_id_index ON $TABLE_NAME ($MESSAGE_ID);",
      "CREATE INDEX IF NOT EXISTS attachment_transfer_state_index ON $TABLE_NAME ($TRANSFER_STATE);",
      "CREATE INDEX IF NOT EXISTS attachment_sticker_pack_id_index ON $TABLE_NAME ($STICKER_PACK_ID);",
      "CREATE INDEX IF NOT EXISTS attachment_data_hash_start_index ON $TABLE_NAME ($DATA_HASH_START);",
      "CREATE INDEX IF NOT EXISTS attachment_data_hash_end_index ON $TABLE_NAME ($DATA_HASH_END);",
      "CREATE INDEX IF NOT EXISTS attachment_data_index ON $TABLE_NAME ($DATA_FILE);",
      "CREATE INDEX IF NOT EXISTS attachment_archive_media_id_index ON $TABLE_NAME ($ARCHIVE_MEDIA_ID);"
    )

    @JvmStatic
    @Throws(IOException::class)
    fun newDataFile(context: Context): File {
      val partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
      return PartFileProtector.protect { File.createTempFile("part", ".mms", partsDirectory) }
    }
  }

  @Throws(IOException::class)
  fun getAttachmentStream(attachmentId: AttachmentId, offset: Long): InputStream {
    return try {
      getDataStream(attachmentId, offset)
    } catch (e: FileNotFoundException) {
      throw IOException("No stream for: $attachmentId", e)
    } ?: throw IOException("No stream for: $attachmentId")
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

  fun getAttachmentsForMessages(mmsIds: Collection<Long?>): Map<Long, List<DatabaseAttachment>> {
    if (mmsIds.isEmpty()) {
      return emptyMap()
    }

    val query = SqlUtil.buildSingleCollectionQuery(MESSAGE_ID, mmsIds)

    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where(query.where, query.whereArgs)
      .orderBy("$ID ASC")
      .run()
      .groupBy { cursor ->
        val attachment = cursor.readAttachment()
        attachment.mmsId to attachment
      }
  }

  fun hasAttachment(id: AttachmentId): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ID = ?", id.id)
      .run()
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

  fun getArchivableAttachments(): Cursor {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$ARCHIVE_MEDIA_ID IS NULL AND $REMOTE_DIGEST IS NOT NULL AND ($TRANSFER_STATE = ? OR $TRANSFER_STATE = ?)", TRANSFER_PROGRESS_DONE.toString(), TRANSFER_NEEDS_RESTORE.toString())
      .orderBy("$ID DESC")
      .run()
  }

  fun getRestorableAttachments(batchSize: Int): List<DatabaseAttachment> {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$TRANSFER_STATE = ?", TRANSFER_NEEDS_RESTORE.toString())
      .limit(batchSize)
      .orderBy("$ID DESC")
      .run().readToList {
        it.readAttachments()
      }.flatten()
  }

  /**
   * Finds the next eligible attachment that needs to be uploaded to the archive service.
   * If it exists, it'll also atomically be marked as [ArchiveTransferState.BACKFILL_UPLOAD_IN_PROGRESS].
   */
  fun getNextAttachmentToArchiveAndMarkUploadInProgress(): DatabaseAttachment? {
    return writableDatabase.withinTransaction {
      val record: DatabaseAttachment? = readableDatabase
        .select(*PROJECTION)
        .from(TABLE_NAME)
        .where("$ARCHIVE_TRANSFER_STATE = ? AND $DATA_FILE NOT NULL AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE", ArchiveTransferState.NONE.value)
        .orderBy("$ID DESC")
        .limit(1)
        .run()
        .readToSingleObject { it.readAttachment() }

      if (record != null) {
        writableDatabase
          .update(TABLE_NAME)
          .values(ARCHIVE_TRANSFER_STATE to ArchiveTransferState.BACKFILL_UPLOAD_IN_PROGRESS.value)
          .where("$ID = ?", record.attachmentId)
          .run()
      }

      record
    }
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
  }

  /**
   * Resets any in-progress archive backfill states to [ArchiveTransferState.NONE], returning the number that had to be reset.
   * This should only be called if you believe the backfill process has finished. In this case, if this returns a value > 0,
   * it indicates that state was mis-tracked and you should try uploading again.
   */
  fun resetPendingArchiveBackfills(): Int {
    return writableDatabase
      .update(TABLE_NAME)
      .values(ARCHIVE_TRANSFER_STATE to ArchiveTransferState.NONE.value)
      .where("$ARCHIVE_TRANSFER_STATE == ${ArchiveTransferState.BACKFILL_UPLOAD_IN_PROGRESS.value} || $ARCHIVE_TRANSFER_STATE == ${ArchiveTransferState.BACKFILL_UPLOADED.value}")
      .run()
  }

  fun deleteAttachmentsForMessage(mmsId: Long): Boolean {
    Log.d(TAG, "[deleteAttachmentsForMessage] mmsId: $mmsId")

    return writableDatabase.withinTransaction { db ->
      db.select(DATA_FILE, CONTENT_TYPE, ID)
        .from(TABLE_NAME)
        .where("$MESSAGE_ID = ?", mmsId)
        .run()
        .forEach { cursor ->
          val attachmentId = AttachmentId(cursor.requireLong(ID))

          AppDependencies.jobManager.cancelAllInQueue(AttachmentDownloadJob.constructQueueString(attachmentId))

          deleteDataFileIfPossible(
            filePath = cursor.requireString(DATA_FILE),
            contentType = cursor.requireString(CONTENT_TYPE),
            attachmentId = attachmentId
          )
        }

      val deleteCount = db.delete(TABLE_NAME)
        .where("$MESSAGE_ID = ?", mmsId)
        .run()

      notifyAttachmentListeners()

      deleteCount > 0
    }
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

    writableDatabase.withinTransaction { db ->
      db.select(DATA_FILE, CONTENT_TYPE, ID)
        .from(TABLE_NAME)
        .where("$MESSAGE_ID = ?", messageId)
        .run()
        .forEach { cursor ->
          deleteDataFileIfPossible(
            filePath = cursor.requireString(DATA_FILE),
            contentType = cursor.requireString(CONTENT_TYPE),
            attachmentId = AttachmentId(cursor.requireLong(ID))
          )
        }

      db.update(TABLE_NAME)
        .values(
          DATA_FILE to null,
          DATA_RANDOM to null,
          DATA_HASH_START to null,
          DATA_HASH_END to null,
          FILE_NAME to null,
          CAPTION to null,
          DATA_SIZE to 0,
          WIDTH to 0,
          HEIGHT to 0,
          TRANSFER_STATE to TRANSFER_PROGRESS_DONE,
          BLUR_HASH to null,
          CONTENT_TYPE to MediaUtil.VIEW_ONCE
        )
        .where("$MESSAGE_ID = ?", messageId)
        .run()

      notifyAttachmentListeners()

      val threadId = messages.getThreadIdForMessage(messageId)
      if (threadId > 0) {
        notifyConversationListeners(threadId)
      }
    }
  }

  fun deleteAttachment(id: AttachmentId) {
    Log.d(TAG, "[deleteAttachment] attachmentId: $id")

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

          val data = cursor.requireString(DATA_FILE)
          val contentType = cursor.requireString(CONTENT_TYPE)

          deleteDataFileIfPossible(
            filePath = data,
            contentType = contentType,
            attachmentId = id
          )

          db.delete(TABLE_NAME)
            .where("$ID = ?", id.id)
            .run()

          deleteDataFileIfPossible(data, contentType, id)
          notifyAttachmentListeners()
        }
    }
  }

  fun deleteAttachments(toDelete: List<SyncAttachmentId>): List<SyncMessageId> {
    val unhandled = mutableListOf<SyncMessageId>()
    for (syncAttachmentId in toDelete) {
      val messageId = SignalDatabase.messages.getMessageIdOrNull(syncAttachmentId.syncMessageId)
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
            SignalDatabase.messages.deleteMessage(messageId)
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
      .where("$MESSAGE_ID != $PREUPLOAD_MESSAGE_ID AND $MESSAGE_ID NOT IN (SELECT ${MessageTable.ID} FROM ${MessageTable.TABLE_NAME})")
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

    val filesInDb: Set<String> = readableDatabase
      .select(DATA_FILE)
      .from(TABLE_NAME)
      .run()
      .readToList { it.requireString(DATA_FILE) }
      .filterNotNull()
      .toSet() + stickers.allStickerFiles

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

    notifyAttachmentListeners()
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

  fun setThumbnailTransferState(messageId: Long, attachmentId: AttachmentId, thumbnailRestoreState: ThumbnailRestoreState) {
    writableDatabase
      .update(TABLE_NAME)
      .values(THUMBNAIL_RESTORE_STATE to thumbnailRestoreState.value)
      .where("$ID = ?", attachmentId.id)
      .run()

    val threadId = messages.getThreadIdForMessage(messageId)
    notifyConversationListeners(threadId)
  }

  @Throws(MmsException::class)
  fun setTransferProgressFailed(attachmentId: AttachmentId, mmsId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(TRANSFER_STATE to TRANSFER_PROGRESS_FAILED)
      .where("$ID = ? AND $TRANSFER_STATE < $TRANSFER_PROGRESS_PERMANENT_FAILURE", attachmentId.id)
      .run()

    notifyConversationListeners(messages.getThreadIdForMessage(mmsId))
  }

  @Throws(MmsException::class)
  fun setThumbnailRestoreProgressFailed(attachmentId: AttachmentId, mmsId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(THUMBNAIL_RESTORE_STATE to ThumbnailRestoreState.PERMANENT_FAILURE.value)
      .where("$ID = ? AND $THUMBNAIL_RESTORE_STATE != ?", attachmentId.id, ThumbnailRestoreState.FINISHED)
      .run()

    notifyConversationListeners(messages.getThreadIdForMessage(mmsId))
  }

  @Throws(MmsException::class)
  fun setTransferProgressPermanentFailure(attachmentId: AttachmentId, mmsId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(TRANSFER_STATE to TRANSFER_PROGRESS_PERMANENT_FAILURE)
      .where("$ID = ?", attachmentId.id)
      .run()

    notifyConversationListeners(messages.getThreadIdForMessage(mmsId))
  }

  /**
   * When we find out about a new inbound attachment pointer, we insert a row for it that contains all the info we need to download it via [insertAttachmentWithData].
   * Later, we download the data for that pointer. Call this method once you have the data to associate it with the attachment. At this point, it is assumed
   * that the content of the attachment will never change.
   */
  @Throws(MmsException::class)
  fun finalizeAttachmentAfterDownload(mmsId: Long, attachmentId: AttachmentId, inputStream: InputStream) {
    Log.i(TAG, "[finalizeAttachmentAfterDownload] Finalizing downloaded data for $attachmentId. (MessageId: $mmsId, $attachmentId)")

    val existingPlaceholder: DatabaseAttachment = getAttachment(attachmentId) ?: throw MmsException("No attachment found for id: $attachmentId")

    val fileWriteResult: DataFileWriteResult = writeToDataFile(newDataFile(context), inputStream, TransformProperties.empty())
    val transferFile: File? = getTransferFile(databaseHelper.signalReadableDatabase, attachmentId)

    val foundDuplicate = writableDatabase.withinTransaction { db ->
      // We can look and see if we have any exact matches on hash_ends and dedupe the file if we see one.
      // We don't look at hash_start here because that could result in us matching on a file that got compressed down to something smaller, effectively lowering
      // the quality of the attachment we received.
      val hashMatch: DataFileInfo? = readableDatabase
        .select(ID, DATA_FILE, DATA_SIZE, DATA_RANDOM, DATA_HASH_START, DATA_HASH_END, TRANSFORM_PROPERTIES, UPLOAD_TIMESTAMP, ARCHIVE_CDN, ARCHIVE_MEDIA_NAME, ARCHIVE_MEDIA_ID)
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
        values.put(ARCHIVE_CDN, hashMatch.archiveCdn)
        values.put(ARCHIVE_MEDIA_NAME, hashMatch.archiveMediaName)
        values.put(ARCHIVE_MEDIA_ID, hashMatch.archiveMediaId)
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
      values.put(ARCHIVE_TRANSFER_FILE, null as String?)

      db.update(TABLE_NAME)
        .values(values)
        .where("$ID = ?", attachmentId.id)
        .run()

      hashMatch != null
    }

    val threadId = messages.getThreadIdForMessage(mmsId)

    if (!messages.isStory(mmsId)) {
      threads.updateSnippetUriSilently(threadId, PartAuthority.getAttachmentDataUri(attachmentId))
    }

    notifyConversationListeners(threadId)
    notifyConversationListListeners()
    notifyAttachmentListeners()

    if (foundDuplicate) {
      if (!fileWriteResult.file.delete()) {
        Log.w(TAG, "Failed to delete unused attachment")
      }
    }

    if (transferFile != null) {
      if (!transferFile.delete()) {
        Log.w(TAG, "Unable to delete transfer file.")
      }
    }

    if (MediaUtil.isAudio(existingPlaceholder)) {
      GenerateAudioWaveFormJob.enqueue(existingPlaceholder.attachmentId)
    }
  }

  @Throws(IOException::class)
  fun finalizeAttachmentThumbnailAfterDownload(attachmentId: AttachmentId, archiveMediaId: String, inputStream: InputStream, transferFile: File) {
    Log.i(TAG, "[finalizeAttachmentThumbnailAfterDownload] Finalizing downloaded data for $attachmentId.")
    val fileWriteResult: DataFileWriteResult = writeToDataFile(newDataFile(context), inputStream, TransformProperties.empty())

    writableDatabase.withinTransaction { db ->
      val values = contentValuesOf(
        THUMBNAIL_FILE to fileWriteResult.file.absolutePath,
        THUMBNAIL_RANDOM to fileWriteResult.random,
        THUMBNAIL_RESTORE_STATE to ThumbnailRestoreState.FINISHED.value
      )

      db.update(TABLE_NAME)
        .values(values)
        .where("$ARCHIVE_MEDIA_ID = ?", archiveMediaId)
        .run()
    }

    notifyConversationListListeners()
    notifyAttachmentListeners()

    if (!transferFile.delete()) {
      Log.w(TAG, "Unable to delete transfer file.")
    }
  }

  /**
   * Needs to be called after an attachment is successfully uploaded. Writes metadata around it's final remote location, as well as calculates
   * it's ending hash, which is critical for backups.
   */
  @Throws(IOException::class)
  fun finalizeAttachmentAfterUpload(id: AttachmentId, attachment: Attachment, uploadTimestamp: Long) {
    Log.i(TAG, "[finalizeAttachmentAfterUpload] Finalizing upload for $id.")

    val dataStream = getAttachmentStream(id, 0)
    val messageDigest = MessageDigest.getInstance("SHA-256")

    DigestInputStream(dataStream, messageDigest).use {
      it.drain()
    }

    val dataHashEnd = Base64.encodeWithPadding(messageDigest.digest())

    val values = contentValuesOf(
      TRANSFER_STATE to TRANSFER_PROGRESS_DONE,
      CDN_NUMBER to attachment.cdn.serialize(),
      REMOTE_LOCATION to attachment.remoteLocation,
      REMOTE_DIGEST to attachment.remoteDigest,
      REMOTE_INCREMENTAL_DIGEST to attachment.incrementalDigest,
      REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE to attachment.incrementalMacChunkSize,
      REMOTE_KEY to attachment.remoteKey,
      DATA_SIZE to attachment.size,
      DATA_HASH_END to dataHashEnd,
      FAST_PREFLIGHT_ID to attachment.fastPreflightId,
      BLUR_HASH to attachment.getVisualHashStringOrNull(),
      UPLOAD_TIMESTAMP to uploadTimestamp
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
    Log.d(TAG, "Inserting attachment $attachment for pre-upload.")
    val result = insertAttachmentsForMessage(PREUPLOAD_MESSAGE_ID, listOf(attachment), emptyList())

    if (result.values.isEmpty()) {
      throw MmsException("Bad attachment result!")
    }

    return getAttachment(result.values.iterator().next()) ?: throw MmsException("Failed to retrieve attachment we just inserted!")
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
      val attachmentId = if (attachment.uri != null) {
        insertAttachmentWithData(mmsId, attachment, attachment.quote)
      } else {
        if (attachment is ArchivedAttachment) {
          insertArchivedAttachment(mmsId, attachment, attachment.quote)
        } else {
          insertUndownloadedAttachment(mmsId, attachment, attachment.quote)
        }
      }

      insertedAttachments[attachment] = attachmentId
      Log.i(TAG, "[insertAttachmentsForMessage] Inserted attachment at $attachmentId")
    }

    try {
      for (attachment in quoteAttachment) {
        val attachmentId = if (attachment.uri != null) {
          insertAttachmentWithData(mmsId, attachment, true)
        } else {
          insertUndownloadedAttachment(mmsId, attachment, true)
        }

        insertedAttachments[attachment] = attachmentId
        Log.i(TAG, "[insertAttachmentsForMessage] Inserted quoted attachment at $attachmentId")
      }
    } catch (e: MmsException) {
      Log.w(TAG, "Failed to insert quote attachment! messageId: $mmsId")
    }

    return insertedAttachments
  }

  fun debugCopyAttachmentForArchiveRestore(
    mmsId: Long,
    attachment: DatabaseAttachment
  ) {
    val copy =
      """
      INSERT INTO $TABLE_NAME
        (
          $MESSAGE_ID,
          $CONTENT_TYPE,
          $TRANSFER_STATE,
          $CDN_NUMBER,
          $REMOTE_LOCATION,
          $REMOTE_DIGEST,
          $REMOTE_INCREMENTAL_DIGEST,
          $REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE,
          $REMOTE_KEY,
          $FILE_NAME,
          $DATA_SIZE,
          $VOICE_NOTE,
          $BORDERLESS,
          $VIDEO_GIF,
          $WIDTH,
          $HEIGHT,
          $CAPTION,
          $UPLOAD_TIMESTAMP,
          $BLUR_HASH,
          $DATA_SIZE,
          $DATA_RANDOM,
          $DATA_HASH_START,
          $DATA_HASH_END,
          $ARCHIVE_MEDIA_ID,
          $ARCHIVE_MEDIA_NAME,
          $ARCHIVE_CDN
        )
      SELECT
          $mmsId,
          $CONTENT_TYPE,
          $TRANSFER_PROGRESS_PENDING,
          $CDN_NUMBER,
          $REMOTE_LOCATION,
          $REMOTE_DIGEST,
          $REMOTE_INCREMENTAL_DIGEST,
          $REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE,
          $REMOTE_KEY,
          $FILE_NAME,
          $DATA_SIZE,
          $VOICE_NOTE,
          $BORDERLESS,
          $VIDEO_GIF,
          $WIDTH,
          $HEIGHT,
          $CAPTION,
          ${System.currentTimeMillis()},
          $BLUR_HASH,
          $DATA_SIZE,
          $DATA_RANDOM,
          $DATA_HASH_START,
          $DATA_HASH_END,
          "${attachment.archiveMediaId}",
          "${attachment.archiveMediaName}",
          ${attachment.archiveCdn}
        FROM $TABLE_NAME
        WHERE $ID = ${attachment.attachmentId.id}
    """

    writableDatabase.execSQL(copy)
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

  @Throws(IOException::class)
  fun getOrCreateArchiveTransferFile(attachmentId: AttachmentId): File {
    val existing = getArchiveTransferFile(writableDatabase, attachmentId)
    if (existing != null) {
      return existing
    }

    val transferFile = newTransferFile()

    writableDatabase
      .update(TABLE_NAME)
      .values(ARCHIVE_TRANSFER_FILE to transferFile.absolutePath)
      .where("$ID = ?", attachmentId.id)
      .run()

    return transferFile
  }

  fun createArchiveThumbnailTransferFile(): File {
    return newTransferFile()
  }

  fun getDataFileInfo(attachmentId: AttachmentId): DataFileInfo? {
    return readableDatabase
      .select(ID, DATA_FILE, DATA_SIZE, DATA_RANDOM, DATA_HASH_START, DATA_HASH_END, TRANSFORM_PROPERTIES, UPLOAD_TIMESTAMP, ARCHIVE_CDN, ARCHIVE_MEDIA_NAME, ARCHIVE_MEDIA_ID)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .run()
      .readToSingleObject { cursor ->
        if (cursor.isNull(DATA_FILE)) {
          null
        } else {
          cursor.readDataFileInfo()
        }
      }
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
              voiceNote = jsonObject.getInt(VOICE_NOTE) == 1,
              borderless = jsonObject.getInt(BORDERLESS) == 1,
              videoGif = jsonObject.getInt(VIDEO_GIF) == 1,
              width = jsonObject.getInt(WIDTH),
              height = jsonObject.getInt(HEIGHT),
              quote = jsonObject.getInt(QUOTE) == 1,
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
              archiveCdn = jsonObject.getInt(ARCHIVE_CDN),
              archiveThumbnailCdn = jsonObject.getInt(ARCHIVE_THUMBNAIL_CDN),
              archiveMediaName = jsonObject.getString(ARCHIVE_MEDIA_NAME),
              archiveMediaId = jsonObject.getString(ARCHIVE_MEDIA_ID),
              hasArchiveThumbnail = !TextUtils.isEmpty(jsonObject.getString(THUMBNAIL_FILE)),
              thumbnailRestoreState = ThumbnailRestoreState.deserialize(jsonObject.getInt(THUMBNAIL_RESTORE_STATE)),
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
   * Sets the archive data for the specific attachment, as well as for any attachments that use the same underlying file.
   */
  fun setArchiveData(attachmentId: AttachmentId, archiveCdn: Int, archiveMediaName: String, archiveMediaId: String, archiveThumbnailMediaId: String) {
    writableDatabase.withinTransaction { db ->
      val dataFile = db
        .select(DATA_FILE)
        .from(TABLE_NAME)
        .where("$ID = ?", attachmentId.id)
        .run()
        .readToSingleObject { it.requireString(DATA_FILE) }

      if (dataFile == null) {
        Log.w(TAG, "No data file found for attachment $attachmentId. Can't set archive data.")
        return@withinTransaction
      }

      db.update(TABLE_NAME)
        .values(
          ARCHIVE_CDN to archiveCdn,
          ARCHIVE_MEDIA_ID to archiveMediaId,
          ARCHIVE_MEDIA_NAME to archiveMediaName,
          ARCHIVE_THUMBNAIL_MEDIA_ID to archiveThumbnailMediaId,
          ARCHIVE_TRANSFER_STATE to ArchiveTransferState.FINISHED.value
        )
        .where("$DATA_FILE = ?", dataFile)
        .run()
    }
  }

  fun updateArchiveCdnByMediaId(archiveMediaId: String, archiveCdn: Int): Int {
    return writableDatabase.rawQuery(
      "UPDATE $TABLE_NAME SET " +
        "$ARCHIVE_THUMBNAIL_CDN = CASE WHEN $ARCHIVE_THUMBNAIL_MEDIA_ID = ? THEN ? ELSE $ARCHIVE_THUMBNAIL_CDN END," +
        "$ARCHIVE_CDN = CASE WHEN $ARCHIVE_MEDIA_ID = ? THEN ? ELSE $ARCHIVE_CDN END " +
        "WHERE $ARCHIVE_MEDIA_ID = ? OR $ARCHIVE_THUMBNAIL_MEDIA_ID = ? " +
        "RETURNING $ARCHIVE_CDN, $ARCHIVE_THUMBNAIL_CDN",
      SqlUtil.buildArgs(archiveMediaId, archiveCdn, archiveMediaId, archiveCdn, archiveMediaId, archiveMediaId)
    ).count
  }

  fun clearArchiveData(attachmentIds: List<AttachmentId>) {
    SqlUtil.buildCollectionQuery(ID, attachmentIds.map { it.id })
      .forEach { query ->
        writableDatabase
          .update(TABLE_NAME)
          .values(
            ARCHIVE_CDN to 0,
            ARCHIVE_MEDIA_ID to null,
            ARCHIVE_MEDIA_NAME to null
          )
          .where(query.where, query.whereArgs)
          .run()
      }
  }

  fun clearAllArchiveData() {
    writableDatabase
      .update(TABLE_NAME)
      .values(
        ARCHIVE_CDN to 0,
        ARCHIVE_MEDIA_ID to null,
        ARCHIVE_MEDIA_NAME to null
      )
      .where("$ARCHIVE_CDN > 0 OR $ARCHIVE_MEDIA_ID IS NOT NULL OR $ARCHIVE_MEDIA_NAME IS NOT NULL")
      .run()
  }

  /**
   * Deletes the data file if there's no strong references to other attachments.
   * If deleted, it will also clear all weak references (i.e. quotes) of the attachment.
   */
  private fun deleteDataFileIfPossible(
    filePath: String?,
    contentType: String?,
    attachmentId: AttachmentId
  ) {
    check(writableDatabase.inTransaction()) { "Must be in a transaction!" }

    if (filePath == null) {
      Log.w(TAG, "[deleteDataFileIfPossible] Null data file path for $attachmentId! Can't delete anything.")
      return
    }

    val strongReferenceExists = readableDatabase
      .exists(TABLE_NAME)
      .where("$DATA_FILE = ? AND QUOTE = 0 AND $ID != ${attachmentId.id}", filePath)
      .run()

    if (strongReferenceExists) {
      Log.i(TAG, "[deleteDataFileIfPossible] Attachment in use. Skipping deletion of $attachmentId. Path: $filePath")
      return
    }

    val weakReferenceCount = writableDatabase
      .update(TABLE_NAME)
      .values(
        DATA_FILE to null,
        DATA_RANDOM to null,
        DATA_HASH_START to null,
        DATA_HASH_END to null
      )
      .where("$DATA_FILE = ?", filePath)
      .run()

    Log.i(TAG, "[deleteDataFileIfPossible] Cleared $weakReferenceCount weak references for $attachmentId. Path: $filePath")

    if (!File(filePath).delete()) {
      Log.w(TAG, "[deleteDataFileIfPossible] Failed to delete $attachmentId. Path: $filePath")
    }

    if (MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType)) {
      Glide.get(context).clearDiskCache()
      ThreadUtil.runOnMain { Glide.get(context).clearMemory() }
    }
  }

  @Throws(FileNotFoundException::class)
  private fun getDataStream(attachmentId: AttachmentId, offset: Long): InputStream? {
    val dataInfo = getDataFileInfo(attachmentId) ?: return null

    return try {
      if (dataInfo.random != null && dataInfo.random.size == 32) {
        ModernDecryptingPartInputStream.createFor(attachmentSecret, dataInfo.random, dataInfo.file, offset)
      } else {
        val stream = ClassicDecryptingPartInputStream.createFor(attachmentSecret, dataInfo.file)
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
  private fun writeToDataFile(destination: File, inputStream: InputStream, transformProperties: TransformProperties): DataFileWriteResult {
    return try {
      // Sometimes the destination is a file that's already in use, sometimes it's not.
      // To avoid writing to a file while it's in-use, we write to a temp file and then rename it to the destination file at the end.
      val tempFile = newDataFile(context)
      val messageDigest = MessageDigest.getInstance("SHA-256")
      val digestInputStream = DigestInputStream(inputStream, messageDigest)

      val encryptingStreamData = ModernEncryptingPartOutputStream.createFor(attachmentSecret, tempFile, false)
      val random = encryptingStreamData.first
      val encryptingOutputStream = encryptingStreamData.second

      val length = StreamUtil.copy(digestInputStream, encryptingOutputStream)
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

    if (newProperties.mp4FastStart != potentialMatchProperties.mp4FastStart) {
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
    Log.d(TAG, "[insertAttachment] Inserting attachment for messageId $messageId.")

    val attachmentId: AttachmentId = writableDatabase.withinTransaction { db ->
      val contentValues = ContentValues().apply {
        put(MESSAGE_ID, messageId)
        put(CONTENT_TYPE, attachment.contentType)
        put(TRANSFER_STATE, attachment.transferState)
        put(CDN_NUMBER, attachment.cdn.serialize())
        put(REMOTE_LOCATION, attachment.remoteLocation)
        put(REMOTE_DIGEST, attachment.remoteDigest)
        put(REMOTE_INCREMENTAL_DIGEST, attachment.incrementalDigest)
        put(REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE, attachment.incrementalMacChunkSize)
        put(REMOTE_KEY, attachment.remoteKey)
        put(FILE_NAME, StorageUtil.getCleanFileName(attachment.fileName))
        put(DATA_SIZE, attachment.size)
        put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
        put(VOICE_NOTE, attachment.voiceNote.toInt())
        put(BORDERLESS, attachment.borderless.toInt())
        put(VIDEO_GIF, attachment.videoGif.toInt())
        put(WIDTH, attachment.width)
        put(HEIGHT, attachment.height)
        put(QUOTE, quote)
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
      }

      val rowId = db.insert(TABLE_NAME, null, contentValues)
      AttachmentId(rowId)
    }

    notifyAttachmentListeners()
    return attachmentId
  }

  /**
   * Attachments need records in the database even if they haven't been downloaded yet. That allows us to store the info we need to download it, what message
   * it's associated with, etc. We treat this case separately from attachments with data (see [insertAttachmentWithData]) because it's much simpler,
   * and splitting the two use cases makes the code easier to understand.
   *
   * Callers are expected to later call [finalizeAttachmentAfterDownload] once they have downloaded the data for this attachment.
   */
  @Throws(MmsException::class)
  private fun insertArchivedAttachment(messageId: Long, attachment: ArchivedAttachment, quote: Boolean): AttachmentId {
    Log.d(TAG, "[insertAttachment] Inserting attachment for messageId $messageId.")

    val attachmentId: AttachmentId = writableDatabase.withinTransaction { db ->
      val contentValues = ContentValues().apply {
        put(MESSAGE_ID, messageId)
        put(CONTENT_TYPE, attachment.contentType)
        put(TRANSFER_STATE, attachment.transferState)
        put(CDN_NUMBER, attachment.cdn.serialize())
        put(REMOTE_LOCATION, attachment.remoteLocation)
        put(REMOTE_DIGEST, attachment.remoteDigest)
        put(REMOTE_INCREMENTAL_DIGEST, attachment.incrementalDigest)
        put(REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE, attachment.incrementalMacChunkSize)
        put(REMOTE_KEY, attachment.remoteKey)
        put(FILE_NAME, StorageUtil.getCleanFileName(attachment.fileName))
        put(DATA_SIZE, attachment.size)
        put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
        put(VOICE_NOTE, attachment.voiceNote.toInt())
        put(BORDERLESS, attachment.borderless.toInt())
        put(VIDEO_GIF, attachment.videoGif.toInt())
        put(WIDTH, attachment.width)
        put(HEIGHT, attachment.height)
        put(QUOTE, quote)
        put(CAPTION, attachment.caption)
        put(UPLOAD_TIMESTAMP, attachment.uploadTimestamp)
        put(ARCHIVE_CDN, attachment.archiveCdn)
        put(ARCHIVE_MEDIA_NAME, attachment.archiveMediaName)
        put(ARCHIVE_MEDIA_ID, attachment.archiveMediaId)
        put(ARCHIVE_THUMBNAIL_MEDIA_ID, attachment.archiveThumbnailMediaId)
        put(THUMBNAIL_RESTORE_STATE, ThumbnailRestoreState.NEEDS_RESTORE.value)
        put(ATTACHMENT_UUID, attachment.uuid?.toString())

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

    notifyAttachmentListeners()
    return attachmentId
  }

  /**
   * Inserts an attachment with existing data. This is likely an outgoing attachment that we're in the process of sending.
   */
  @Throws(MmsException::class)
  private fun insertAttachmentWithData(messageId: Long, attachment: Attachment, quote: Boolean): AttachmentId {
    requireNotNull(attachment.uri) { "Attachment must have a uri!" }

    Log.d(TAG, "[insertAttachmentWithData] Inserting attachment for messageId $messageId. (MessageId: $messageId, ${attachment.uri})")

    val dataStream = try {
      PartAuthority.getAttachmentStream(context, attachment.uri!!)
    } catch (e: IOException) {
      throw MmsException(e)
    }

    // To avoid performing long-running operations in a transaction, we write the data to an independent file first in a way that doesn't rely on db state.
    val fileWriteResult: DataFileWriteResult = writeToDataFile(newDataFile(context), dataStream, attachment.transformProperties ?: TransformProperties.empty())
    Log.d(TAG, "[insertAttachmentWithData] Wrote data to file: ${fileWriteResult.file.absolutePath} (MessageId: $messageId, ${attachment.uri})")

    val (attachmentId: AttachmentId, foundDuplicate: Boolean) = writableDatabase.withinTransaction { db ->
      val contentValues = ContentValues()
      var transformProperties = attachment.transformProperties ?: TransformProperties.empty()

      // First we'll check if our file hash matches the starting or ending hash of any other attachments and has compatible transform properties.
      // We'll prefer the match with the most recent upload timestamp.
      val hashMatch: DataFileInfo? = readableDatabase
        .select(ID, DATA_FILE, DATA_SIZE, DATA_RANDOM, DATA_HASH_START, DATA_HASH_END, TRANSFORM_PROPERTIES, UPLOAD_TIMESTAMP, ARCHIVE_CDN, ARCHIVE_MEDIA_NAME, ARCHIVE_MEDIA_ID)
        .from(TABLE_NAME)
        .where("$DATA_FILE NOT NULL AND ($DATA_HASH_START = ? OR $DATA_HASH_END = ?)", fileWriteResult.hash, fileWriteResult.hash)
        .run()
        .readToList { it.readDataFileInfo() }
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
        if (fileWriteResult.hash == hashMatch.hashStart) {
          Log.i(TAG, "[insertAttachmentWithData] Found that the new attachment hash matches the DATA_HASH_START of ${hashMatch.id}. Using all of it's fields. (MessageId: $messageId, ${attachment.uri})")
        } else if (fileWriteResult.hash == hashMatch.hashEnd) {
          Log.i(TAG, "[insertAttachmentWithData] Found that the new attachment hash matches the DATA_HASH_END of ${hashMatch.id}. Using all of it's fields. (MessageId: $messageId, ${attachment.uri})")
        } else {
          throw IllegalStateException("Should not be possible based on query.")
        }

        contentValues.put(DATA_FILE, hashMatch.file.absolutePath)
        contentValues.put(DATA_SIZE, hashMatch.length)
        contentValues.put(DATA_RANDOM, hashMatch.random)
        contentValues.put(DATA_HASH_START, fileWriteResult.hash)
        contentValues.put(DATA_HASH_END, hashMatch.hashEnd)
        contentValues.put(ARCHIVE_CDN, hashMatch.archiveCdn)
        contentValues.put(ARCHIVE_MEDIA_NAME, hashMatch.archiveMediaName)
        contentValues.put(ARCHIVE_MEDIA_ID, hashMatch.archiveMediaId)

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
      var uploadTemplate: Attachment? = null
      if (hashMatch?.hashEnd != null && System.currentTimeMillis() - hashMatch.uploadTimestamp < AttachmentUploadJob.UPLOAD_REUSE_THRESHOLD) {
        uploadTemplate = readableDatabase
          .select(*PROJECTION)
          .from(TABLE_NAME)
          .where("$ID = ${hashMatch.id.id} AND $REMOTE_DIGEST NOT NULL AND $TRANSFER_STATE = $TRANSFER_PROGRESS_DONE AND $DATA_HASH_END NOT NULL")
          .run()
          .readToSingleObject { it.readAttachment() }
      }

      if (uploadTemplate != null) {
        Log.i(TAG, "[insertAttachmentWithData] Found a valid template we could use to skip upload. (MessageId: $messageId, ${attachment.uri})")
        transformProperties = (uploadTemplate.transformProperties ?: transformProperties).copy(skipTransform = true)
      }

      contentValues.put(MESSAGE_ID, messageId)
      contentValues.put(CONTENT_TYPE, uploadTemplate?.contentType ?: attachment.contentType)
      contentValues.put(TRANSFER_STATE, attachment.transferState) // Even if we have a template, we let AttachmentUploadJob have the final say so it can re-check and make sure the template is still valid
      contentValues.put(CDN_NUMBER, uploadTemplate?.cdn?.serialize() ?: Cdn.CDN_0.serialize())
      contentValues.put(REMOTE_LOCATION, uploadTemplate?.remoteLocation)
      contentValues.put(REMOTE_DIGEST, uploadTemplate?.remoteDigest)
      contentValues.put(REMOTE_INCREMENTAL_DIGEST, uploadTemplate?.incrementalDigest)
      contentValues.put(REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE, uploadTemplate?.incrementalMacChunkSize ?: 0)
      contentValues.put(REMOTE_KEY, uploadTemplate?.remoteKey)
      contentValues.put(FILE_NAME, StorageUtil.getCleanFileName(attachment.fileName))
      contentValues.put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
      contentValues.put(VOICE_NOTE, if (attachment.voiceNote) 1 else 0)
      contentValues.put(BORDERLESS, if (attachment.borderless) 1 else 0)
      contentValues.put(VIDEO_GIF, if (attachment.videoGif) 1 else 0)
      contentValues.put(WIDTH, uploadTemplate?.width ?: attachment.width)
      contentValues.put(HEIGHT, uploadTemplate?.height ?: attachment.height)
      contentValues.put(QUOTE, quote)
      contentValues.put(CAPTION, attachment.caption)
      contentValues.put(UPLOAD_TIMESTAMP, uploadTemplate?.uploadTimestamp ?: 0)
      contentValues.put(TRANSFORM_PROPERTIES, transformProperties.serialize())
      contentValues.put(ATTACHMENT_UUID, attachment.uuid?.toString())

      if (attachment.transformProperties?.videoEdited == true) {
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

    notifyAttachmentListeners()
    return attachmentId
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

  private fun getArchiveTransferFile(db: SQLiteDatabase, attachmentId: AttachmentId): File? {
    return db
      .select(ARCHIVE_TRANSFER_FILE)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .limit(1)
      .run()
      .readToSingleObject { cursor ->
        cursor.requireString(ARCHIVE_TRANSFER_FILE)?.let { File(it) }
      }
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
      cdn = cursor.requireObject(CDN_NUMBER, Cdn.Serializer),
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
      caption = cursor.requireString(CAPTION),
      stickerLocator = cursor.readStickerLocator(),
      blurHash = if (MediaUtil.isAudioType(contentType)) null else BlurHash.parseOrNull(cursor.requireString(BLUR_HASH)),
      audioHash = if (MediaUtil.isAudioType(contentType)) AudioHash.parseOrNull(cursor.requireString(BLUR_HASH)) else null,
      transformProperties = TransformProperties.parse(cursor.requireString(TRANSFORM_PROPERTIES)),
      displayOrder = cursor.requireInt(DISPLAY_ORDER),
      uploadTimestamp = cursor.requireLong(UPLOAD_TIMESTAMP),
      dataHash = cursor.requireString(DATA_HASH_END),
      archiveCdn = cursor.requireInt(ARCHIVE_CDN),
      archiveThumbnailCdn = cursor.requireInt(ARCHIVE_THUMBNAIL_CDN),
      archiveMediaName = cursor.requireString(ARCHIVE_MEDIA_NAME),
      archiveMediaId = cursor.requireString(ARCHIVE_MEDIA_ID),
      hasArchiveThumbnail = !cursor.isNull(THUMBNAIL_FILE),
      thumbnailRestoreState = ThumbnailRestoreState.deserialize(cursor.requireInt(THUMBNAIL_RESTORE_STATE)),
      uuid = UuidUtil.parseOrNull(cursor.requireString(ATTACHMENT_UUID))
    )
  }

  private fun Cursor.readAttachments(): List<DatabaseAttachment> {
    return getAttachments(this)
  }

  private fun Cursor.readAttachment(): DatabaseAttachment {
    return getAttachment(this)
  }

  private fun Cursor.readDataFileInfo(): DataFileInfo {
    return DataFileInfo(
      id = AttachmentId(this.requireLong(ID)),
      file = File(this.requireNonNullString(DATA_FILE)),
      length = this.requireLong(DATA_SIZE),
      random = this.requireNonNullBlob(DATA_RANDOM),
      hashStart = this.requireString(DATA_HASH_START),
      hashEnd = this.requireString(DATA_HASH_END),
      transformProperties = TransformProperties.parse(this.requireString(TRANSFORM_PROPERTIES)),
      uploadTimestamp = this.requireLong(UPLOAD_TIMESTAMP),
      archiveCdn = this.requireInt(ARCHIVE_CDN),
      archiveMediaName = this.requireString(ARCHIVE_MEDIA_NAME),
      archiveMediaId = this.requireString(ARCHIVE_MEDIA_ID)
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

  fun debugGetLatestAttachments(): List<DatabaseAttachment> {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$REMOTE_LOCATION IS NOT NULL AND $REMOTE_KEY IS NOT NULL")
      .orderBy("$ID DESC")
      .limit(30)
      .run()
      .readToList { it.readAttachments() }
      .flatten()
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
    val archiveCdn: Int,
    val archiveMediaName: String?,
    val archiveMediaId: String?
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
        return values().firstOrNull { it.value == value } ?: NONE
      }
    }
  }

  /**
   * This maintains two different state paths for uploading attachments to the archive.
   *
   * The first is the backfill process, which will happen after newly-enabling backups. That process will go:
   * 1. [NONE]
   * 2. [BACKFILL_UPLOAD_IN_PROGRESS]
   * 3. [BACKFILL_UPLOADED]
   * 4. [FINISHED] or [PERMANENT_FAILURE]
   *
   * The second is when newly sending/receiving an attachment after enabling backups. That process will go:
   * 1. [NONE]
   * 2. [ATTACHMENT_TRANSFER_PENDING]
   * 3. [FINISHED] or [PERMANENT_FAILURE]
   */
  enum class ArchiveTransferState(val value: Int) {
    /** Not backed up at all. */
    NONE(0),

    /** The upload to the attachment service is in progress. */
    BACKFILL_UPLOAD_IN_PROGRESS(1),

    /** Successfully uploaded to the attachment service during the backfill process. Still need to tell the service to move the file over to the archive service. */
    BACKFILL_UPLOADED(2),

    /** Completely finished backing up the attachment. */
    FINISHED(3),

    /** It is impossible to upload this attachment. */
    PERMANENT_FAILURE(4),

    /** We sent/received this attachment after enabling backups, but still need to transfer the file to the archive service. */
    ATTACHMENT_TRANSFER_PENDING(5);

    companion object {
      fun deserialize(value: Int): ArchiveTransferState {
        return values().firstOrNull { it.value == value } ?: NONE
      }
    }
  }

  class SyncAttachmentId(val syncMessageId: SyncMessageId, val uuid: UUID?, val digest: ByteArray?, val plaintextHash: String?)

  class SyncAttachment(val id: AttachmentId, val uuid: UUID?, val digest: ByteArray?, val plaintextHash: String?)
}

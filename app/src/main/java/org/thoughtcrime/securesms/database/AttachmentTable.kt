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
import org.signal.core.util.Base64.encodeWithPadding
import org.signal.core.util.SqlUtil.buildArgs
import org.signal.core.util.SqlUtil.buildCollectionQuery
import org.signal.core.util.SqlUtil.buildSingleCollectionQuery
import org.signal.core.util.StreamUtil
import org.signal.core.util.ThreadUtil
import org.signal.core.util.delete
import org.signal.core.util.deleteAll
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
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.audio.AudioHash
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.crypto.AttachmentSecret
import org.thoughtcrime.securesms.crypto.ClassicDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.messages
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.stickers
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.threads
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob
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
    const val DATA_HASH = "data_hash"
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

    const val ATTACHMENT_JSON_ALIAS = "attachment_json"

    private const val DIRECTORY = "parts"

    const val TRANSFER_PROGRESS_DONE = 0
    const val TRANSFER_PROGRESS_STARTED = 1
    const val TRANSFER_PROGRESS_PENDING = 2
    const val TRANSFER_PROGRESS_FAILED = 3
    const val TRANSFER_PROGRESS_PERMANENT_FAILURE = 4
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
      DATA_HASH,
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
      UPLOAD_TIMESTAMP
    )

    const val CREATE_TABLE = """
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
        $DATA_HASH TEXT DEFAULT NULL,
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
        $UPLOAD_TIMESTAMP INTEGER DEFAULT 0
      )
      """

    @JvmField
    val CREATE_INDEXS = arrayOf(
      "CREATE INDEX IF NOT EXISTS attachment_message_id_index ON $TABLE_NAME ($MESSAGE_ID);",
      "CREATE INDEX IF NOT EXISTS attachment_transfer_state_index ON $TABLE_NAME ($TRANSFER_STATE);",
      "CREATE INDEX IF NOT EXISTS attachment_sticker_pack_id_index ON $TABLE_NAME ($STICKER_PACK_ID);",
      "CREATE INDEX IF NOT EXISTS attachment_data_hash_index ON $TABLE_NAME ($DATA_HASH);",
      "CREATE INDEX IF NOT EXISTS attachment_data_index ON $TABLE_NAME ($DATA_FILE);"
    )

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun newFile(context: Context): File {
      val partsDirectory = context.getDir(DIRECTORY, Context.MODE_PRIVATE)
      return PartFileProtector.protect { File.createTempFile("part", ".mms", partsDirectory) }
    }
  }

  @Throws(IOException::class)
  fun getAttachmentStream(attachmentId: AttachmentId, offset: Long): InputStream {
    return try {
      getDataStream(attachmentId, DATA_FILE, offset)
    } catch (e: FileNotFoundException) {
      throw IOException("No stream for: $attachmentId", e)
    } ?: throw IOException("No stream for: $attachmentId")
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

    val query = buildSingleCollectionQuery(MESSAGE_ID, mmsIds)

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

  fun deleteAttachmentsForMessage(mmsId: Long): Boolean {
    Log.d(TAG, "[deleteAttachmentsForMessage] mmsId: $mmsId")

    return writableDatabase.withinTransaction { db ->
      db.select(DATA_FILE, CONTENT_TYPE, ID)
        .from(TABLE_NAME)
        .where("$MESSAGE_ID = ?", mmsId)
        .run()
        .forEach { cursor ->
          val attachmentId = AttachmentId(cursor.requireLong(ID))

          ApplicationDependencies.getJobManager().cancelAllInQueue(AttachmentDownloadJob.constructQueueString(attachmentId))

          deleteAttachmentOnDisk(
            data = cursor.requireString(DATA_FILE),
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
          deleteAttachmentOnDisk(
            data = cursor.requireString(DATA_FILE),
            contentType = cursor.requireString(CONTENT_TYPE),
            attachmentId = AttachmentId(cursor.requireLong(ID))
          )
        }

      db.update(TABLE_NAME)
        .values(
          DATA_FILE to null,
          DATA_RANDOM to null,
          DATA_HASH to null,
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

          deleteAttachmentOnDisk(
            data = data,
            contentType = contentType,
            attachmentId = id
          )

          db.delete(TABLE_NAME)
            .where("$ID = ?", id.id)
            .run()

          deleteAttachmentOnDisk(data, contentType, id)
          notifyAttachmentListeners()
        }
    }
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
  fun setTransferProgressPermanentFailure(attachmentId: AttachmentId, mmsId: Long) {
    writableDatabase
      .update(TABLE_NAME)
      .values(TRANSFER_STATE to TRANSFER_PROGRESS_PERMANENT_FAILURE)
      .where("$ID = ?", attachmentId.id)
      .run()

    notifyConversationListeners(messages.getThreadIdForMessage(mmsId))
  }

  @Throws(MmsException::class)
  fun insertAttachmentsForPlaceholder(mmsId: Long, attachmentId: AttachmentId, inputStream: InputStream) {
    val placeholder = getAttachment(attachmentId)
    val oldInfo = getAttachmentDataFileInfo(attachmentId, DATA_FILE)
    var dataInfo = storeAttachmentStream(inputStream)
    val transferFile = getTransferFile(databaseHelper.signalReadableDatabase, attachmentId)

    val updated = writableDatabase.withinTransaction { db ->
      dataInfo = deduplicateAttachment(dataInfo, attachmentId, placeholder?.transformProperties ?: TransformProperties.empty())

      if (oldInfo != null) {
        updateAttachmentDataHash(db, oldInfo.hash, dataInfo)
      }

      val values = ContentValues()
      values.put(DATA_FILE, dataInfo.file.absolutePath)
      values.put(DATA_SIZE, dataInfo.length)
      values.put(DATA_RANDOM, dataInfo.random)
      values.put(DATA_HASH, dataInfo.hash)

      val visualHashString = placeholder.getVisualHashStringOrNull()
      if (visualHashString != null) {
        values.put(BLUR_HASH, visualHashString)
      }

      values.put(TRANSFER_STATE, TRANSFER_PROGRESS_DONE)
      values.put(TRANSFER_FILE, null as String?)
      values.put(TRANSFORM_PROPERTIES, TransformProperties.forSkipTransform().serialize())

      val updateCount = db.update(TABLE_NAME)
        .values(values)
        .where("$ID = ?", attachmentId.id)
        .run()

      updateCount > 0
    }

    if (updated) {
      val threadId = messages.getThreadIdForMessage(mmsId)

      if (!messages.isStory(mmsId)) {
        threads.updateSnippetUriSilently(threadId, PartAuthority.getAttachmentDataUri(attachmentId))
      }

      notifyConversationListeners(threadId)
      notifyConversationListListeners()
      notifyAttachmentListeners()
    } else {
      if (!dataInfo.file.delete()) {
        Log.w(TAG, "Failed to delete unused attachment")
      }
    }

    if (transferFile != null) {
      if (!transferFile.delete()) {
        Log.w(TAG, "Unable to delete transfer file.")
      }
    }

    if (placeholder != null && MediaUtil.isAudio(placeholder)) {
      GenerateAudioWaveFormJob.enqueue(placeholder.attachmentId)
    }
  }

  @Throws(MmsException::class)
  fun copyAttachmentData(sourceId: AttachmentId, destinationId: AttachmentId) {
    val sourceAttachment = getAttachment(sourceId) ?: throw MmsException("Cannot find attachment for source!")
    val sourceDataInfo = getAttachmentDataFileInfo(sourceId, DATA_FILE) ?: throw MmsException("No attachment data found for source!")

    writableDatabase
      .update(TABLE_NAME)
      .values(
        DATA_FILE to sourceDataInfo.file.absolutePath,
        DATA_HASH to sourceDataInfo.hash,
        DATA_SIZE to sourceDataInfo.length,
        DATA_RANDOM to sourceDataInfo.random,
        TRANSFER_STATE to sourceAttachment.transferState,
        CDN_NUMBER to sourceAttachment.cdnNumber,
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

  fun updateAttachmentAfterUpload(id: AttachmentId, attachment: Attachment, uploadTimestamp: Long) {
    val dataInfo = getAttachmentDataFileInfo(id, DATA_FILE)
    val values = contentValuesOf(
      TRANSFER_STATE to TRANSFER_PROGRESS_DONE,
      CDN_NUMBER to attachment.cdnNumber,
      REMOTE_LOCATION to attachment.remoteLocation,
      REMOTE_DIGEST to attachment.remoteDigest,
      REMOTE_INCREMENTAL_DIGEST to attachment.incrementalDigest,
      REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE to attachment.incrementalMacChunkSize,
      REMOTE_KEY to attachment.remoteKey,
      DATA_SIZE to attachment.size,
      FAST_PREFLIGHT_ID to attachment.fastPreflightId,
      BLUR_HASH to attachment.getVisualHashStringOrNull(),
      UPLOAD_TIMESTAMP to uploadTimestamp
    )

    if (dataInfo?.hash != null) {
      updateAttachmentAndMatchingHashes(writableDatabase, id, dataInfo.hash, values)
    } else {
      writableDatabase
        .update(TABLE_NAME)
        .values(values)
        .where("$ID = ?", id.id)
        .run()
    }
  }

  @Throws(MmsException::class)
  fun insertAttachmentForPreUpload(attachment: Attachment): DatabaseAttachment {
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

  @Throws(MmsException::class)
  fun insertAttachmentsForMessage(mmsId: Long, attachments: List<Attachment>, quoteAttachment: List<Attachment>): Map<Attachment, AttachmentId> {
    if (attachments.isEmpty() && quoteAttachment.isEmpty()) {
      return emptyMap()
    }

    Log.d(TAG, "insertParts(${attachments.size})")

    val insertedAttachments: MutableMap<Attachment, AttachmentId> = mutableMapOf()
    for (attachment in attachments) {
      val attachmentId = insertAttachment(mmsId, attachment, attachment.quote)
      insertedAttachments[attachment] = attachmentId
      Log.i(TAG, "Inserted attachment at ID: $attachmentId")
    }

    try {
      for (attachment in quoteAttachment) {
        val attachmentId = insertAttachment(mmsId, attachment, true)
        insertedAttachments[attachment] = attachmentId
        Log.i(TAG, "Inserted quoted attachment at ID: $attachmentId")
      }
    } catch (e: MmsException) {
      Log.w(TAG, "Failed to insert quote attachment! messageId: $mmsId")
    }

    return insertedAttachments
  }

  /**
   * @param onlyModifyThisAttachment If false and more than one attachment shares this file and quality, they will all
   * be updated. If true, then guarantees not to affect other attachments.
   */
  @Throws(MmsException::class, IOException::class)
  fun updateAttachmentData(
    databaseAttachment: DatabaseAttachment,
    mediaStream: MediaStream,
    onlyModifyThisAttachment: Boolean
  ) {
    val attachmentId = databaseAttachment.attachmentId
    val oldDataInfo = getAttachmentDataFileInfo(attachmentId, DATA_FILE) ?: throw MmsException("No attachment data found!")
    var destination = oldDataInfo.file
    val isSingleUseOfData = onlyModifyThisAttachment || oldDataInfo.hash == null

    if (isSingleUseOfData && fileReferencedByMoreThanOneAttachment(destination)) {
      Log.i(TAG, "Creating a new file as this one is used by more than one attachment")
      destination = newFile(context)
    }

    var dataInfo: DataInfo = storeAttachmentStream(destination, mediaStream.stream)

    writableDatabase.withinTransaction { db ->
      dataInfo = deduplicateAttachment(dataInfo, attachmentId, databaseAttachment.transformProperties)

      val contentValues = contentValuesOf(
        DATA_SIZE to dataInfo.length,
        CONTENT_TYPE to mediaStream.mimeType,
        WIDTH to mediaStream.width,
        HEIGHT to mediaStream.height,
        DATA_FILE to dataInfo.file.absolutePath,
        DATA_RANDOM to dataInfo.random,
        DATA_HASH to dataInfo.hash
      )

      val updateCount = updateAttachmentAndMatchingHashes(
        db = db,
        attachmentId = attachmentId,
        dataHash = if (isSingleUseOfData) dataInfo.hash else oldDataInfo.hash,
        contentValues = contentValues
      )

      Log.i(TAG, "[updateAttachmentData] Updated $updateCount rows.")
    }
  }

  fun duplicateAttachmentsForMessage(destinationMessageId: Long, sourceMessageId: Long, excludedIds: Collection<Long>) {
    writableDatabase.withinTransaction { db ->
      db.execSQL("CREATE TEMPORARY TABLE tmp_part AS SELECT * FROM $TABLE_NAME WHERE $MESSAGE_ID = ?", buildArgs(sourceMessageId))

      val queries = buildCollectionQuery(ID, excludedIds)
      for (query in queries) {
        db.delete("tmp_part", query.where, query.whereArgs)
      }

      db.execSQL("UPDATE tmp_part SET $ID = NULL, $MESSAGE_ID = ?", buildArgs(destinationMessageId))
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

  @VisibleForTesting
  fun getAttachmentDataFileInfo(attachmentId: AttachmentId, dataType: String): DataInfo? {
    return readableDatabase
      .select(dataType, DATA_SIZE, DATA_RANDOM, DATA_HASH, TRANSFORM_PROPERTIES)
      .from(TABLE_NAME)
      .where("$ID = ?", attachmentId.id)
      .run()
      .readToSingleObject { cursor ->
        if (cursor.isNull(dataType)) {
          null
        } else {
          DataInfo(
            file = File(cursor.getString(cursor.getColumnIndexOrThrow(dataType))),
            length = cursor.requireLong(DATA_SIZE),
            random = cursor.requireNonNullBlob(DATA_RANDOM),
            hash = cursor.requireString(DATA_HASH),
            transformProperties = TransformProperties.parse(cursor.requireString(TRANSFORM_PROPERTIES))
          )
        }
      }
  }

  fun markAttachmentAsTransformed(attachmentId: AttachmentId, withFastStart: Boolean) {
    writableDatabase.withinTransaction { db ->
      try {
        var transformProperties = getTransformProperties(attachmentId)
        if (transformProperties == null) {
          Log.w(TAG, "Failed to get transformation properties, attachment no longer exists.")
          return@withinTransaction
        }

        transformProperties = transformProperties.withSkipTransform()
        if (withFastStart) {
          transformProperties = transformProperties.withMp4FastStart()
        }

        updateAttachmentTransformProperties(attachmentId, transformProperties)
      } catch (e: Exception) {
        Log.w(TAG, "Could not mark attachment as transformed.", e)
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
    val dataInfo = getAttachmentDataFileInfo(attachmentId, DATA_FILE)
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
              cdnNumber = jsonObject.getInt(CDN_NUMBER),
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
              dataHash = jsonObject.getString(DATA_HASH)
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

  private fun deleteAttachmentOnDisk(
    data: String?,
    contentType: String?,
    attachmentId: AttachmentId
  ) {
    check(writableDatabase.inTransaction()) { "Must be in a transaction!" }

    val dataUsage = getAttachmentFileUsages(data, attachmentId)
    if (dataUsage.hasStrongReference) {
      Log.i(TAG, "[deleteAttachmentOnDisk] Attachment in use. Skipping deletion. $data $attachmentId")
      return
    }

    Log.i(TAG, "[deleteAttachmentOnDisk] No other strong uses of this attachment. Safe to delete. $data $attachmentId")
    if (!data.isNullOrBlank()) {
      if (File(data).delete()) {
        Log.i(TAG, "[deleteAttachmentOnDisk] Deleted attachment file. $data $attachmentId")

        if (dataUsage.removableWeakReferences.isNotEmpty()) {
          Log.i(TAG, "[deleteAttachmentOnDisk] Deleting ${dataUsage.removableWeakReferences.size} weak references for $data")

          var deletedCount = 0
          for (weakReference in dataUsage.removableWeakReferences) {
            Log.i(TAG, "[deleteAttachmentOnDisk] Clearing weak reference for $data $weakReference")

            deletedCount += writableDatabase
              .update(TABLE_NAME)
              .values(
                DATA_FILE to null,
                DATA_RANDOM to null,
                DATA_HASH to null
              )
              .where("$ID = ?", weakReference.id)
              .run()
          }

          val logMessage = "[deleteAttachmentOnDisk] Cleared $deletedCount/${dataUsage.removableWeakReferences.size} weak references for $data"
          if (deletedCount != dataUsage.removableWeakReferences.size) {
            Log.w(TAG, logMessage)
          } else {
            Log.i(TAG, logMessage)
          }
        }
      } else {
        Log.w(TAG, "[deleteAttachmentOnDisk] Failed to delete attachment. $data $attachmentId")
      }
    }

    if (MediaUtil.isImageType(contentType) || MediaUtil.isVideoType(contentType)) {
      Glide.get(context).clearDiskCache()
      ThreadUtil.runOnMain { Glide.get(context).clearMemory() }
    }
  }

  private fun getAttachmentFileUsages(data: String?, attachmentId: AttachmentId): DataUsageResult {
    check(writableDatabase.inTransaction()) { "Must be in a transaction!" }

    if (data == null) {
      return DataUsageResult.NOT_IN_USE
    }

    val quoteRows: MutableList<AttachmentId> = mutableListOf()

    readableDatabase
      .select(ID, QUOTE)
      .from(TABLE_NAME)
      .where("$DATA_FILE = ? AND $ID != ?", data, attachmentId.id)
      .run()
      .forEach { cursor ->
        if (cursor.requireBoolean(QUOTE)) {
          quoteRows += AttachmentId(cursor.requireLong(ID))
        } else {
          return DataUsageResult.IN_USE
        }
      }

    return DataUsageResult(quoteRows)
  }

  /**
   * Check if data file is in use by another attachment row with a different hash. Rows with the same data and hash
   * will be fixed in a later call to [updateAttachmentAndMatchingHashes].
   */
  private fun isAttachmentFileUsedByOtherAttachments(attachmentId: AttachmentId?, dataInfo: DataInfo): Boolean {
    return if (attachmentId == null || dataInfo.hash == null) {
      false
    } else {
      readableDatabase
        .exists(TABLE_NAME)
        .where("$DATA_FILE = ? AND $DATA_HASH != ?", dataInfo.file.absolutePath, dataInfo.hash)
        .run()
    }
  }

  private fun updateAttachmentDataHash(
    db: SQLiteDatabase,
    oldHash: String?,
    newData: DataInfo
  ) {
    if (oldHash == null) {
      return
    }

    db.update(TABLE_NAME)
      .values(
        DATA_FILE to newData.file.absolutePath,
        DATA_RANDOM to newData.random,
        DATA_HASH to newData.hash
      )
      .where("$DATA_HASH = ?", oldHash)
      .run()
  }

  private fun updateAttachmentTransformProperties(attachmentId: AttachmentId, transformProperties: TransformProperties) {
    val dataInfo = getAttachmentDataFileInfo(attachmentId, DATA_FILE)
    if (dataInfo == null) {
      Log.w(TAG, "[updateAttachmentTransformProperties] No data info found!")
      return
    }

    val contentValues = contentValuesOf(TRANSFORM_PROPERTIES to transformProperties.serialize())
    val updateCount = updateAttachmentAndMatchingHashes(databaseHelper.signalWritableDatabase, attachmentId, dataInfo.hash, contentValues)
    Log.i(TAG, "[updateAttachmentTransformProperties] Updated $updateCount rows.")
  }

  private fun updateAttachmentAndMatchingHashes(
    db: SQLiteDatabase,
    attachmentId: AttachmentId,
    dataHash: String?,
    contentValues: ContentValues
  ): Int {
    return db
      .update(TABLE_NAME)
      .values(contentValues)
      .where("$ID = ? OR ($DATA_HASH NOT NULL AND $DATA_HASH = ?)", attachmentId.id, dataHash.toString())
      .run()
  }

  /**
   * Returns true if the file referenced by two or more attachments.
   * Returns false if the file is referenced by zero or one attachments.
   */
  private fun fileReferencedByMoreThanOneAttachment(file: File): Boolean {
    return readableDatabase
      .select("1")
      .from(TABLE_NAME)
      .where("$DATA_FILE = ?", file.absolutePath)
      .limit(2)
      .run()
      .use { cursor ->
        cursor.moveToNext() && cursor.moveToNext()
      }
  }

  @Throws(FileNotFoundException::class)
  private fun getDataStream(attachmentId: AttachmentId, dataType: String, offset: Long): InputStream? {
    val dataInfo = getAttachmentDataFileInfo(attachmentId, dataType) ?: return null

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

  @Throws(MmsException::class)
  private fun storeAttachmentStream(inputStream: InputStream): DataInfo {
    return try {
      storeAttachmentStream(newFile(context), inputStream)
    } catch (e: IOException) {
      throw MmsException(e)
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
   * Reads the entire stream and saves to disk. If you need to deduplicate attachments, call [deduplicateAttachment]
   * afterwards and use the [DataInfo] returned by it instead.
   */
  @Throws(MmsException::class, IllegalStateException::class)
  private fun storeAttachmentStream(destination: File, inputStream: InputStream): DataInfo {
    return try {
      val tempFile = newFile(context)
      val messageDigest = MessageDigest.getInstance("SHA-256")
      val digestInputStream = DigestInputStream(inputStream, messageDigest)
      val out = ModernEncryptingPartOutputStream.createFor(attachmentSecret, tempFile, false)
      val length = StreamUtil.copy(digestInputStream, out.second)
      val hash = encodeWithPadding(digestInputStream.messageDigest.digest())

      if (!tempFile.renameTo(destination)) {
        Log.w(TAG, "Couldn't rename ${tempFile.path} to ${destination.path}")
        tempFile.delete()
        throw IllegalStateException("Couldn't rename ${tempFile.path} to ${destination.path}")
      }

      DataInfo(
        file = destination,
        length = length,
        random = out.first,
        hash = hash,
        transformProperties = null
      )
    } catch (e: IOException) {
      throw MmsException(e)
    } catch (e: NoSuchAlgorithmException) {
      throw MmsException(e)
    }
  }

  private fun deduplicateAttachment(
    dataInfo: DataInfo,
    attachmentId: AttachmentId?,
    transformProperties: TransformProperties?
  ): DataInfo {
    check(writableDatabase.inTransaction()) { "Must be in a transaction!" }

    val sharedDataInfos = findDuplicateDataFileInfos(writableDatabase, dataInfo.hash, attachmentId)

    for (sharedDataInfo in sharedDataInfos) {
      if (dataInfo.file == sharedDataInfo.file) {
        continue
      }

      val isUsedElsewhere = isAttachmentFileUsedByOtherAttachments(attachmentId, dataInfo)
      val isSameQuality = (transformProperties?.sentMediaQuality ?: 0) == (sharedDataInfo.transformProperties?.sentMediaQuality ?: 0)

      Log.i(TAG, "[deduplicateAttachment] Potential duplicate data file found. usedElsewhere: " + isUsedElsewhere + " sameQuality: " + isSameQuality + " otherFile: " + sharedDataInfo.file.absolutePath)

      if (!isSameQuality) {
        continue
      }

      if (!isUsedElsewhere) {
        if (dataInfo.file.delete()) {
          Log.i(TAG, "[deduplicateAttachment] Deleted original file. ${dataInfo.file}")
        } else {
          Log.w(TAG, "[deduplicateAttachment] Original file could not be deleted.")
        }
      }

      return sharedDataInfo
    }

    Log.i(TAG, "[deduplicateAttachment] No acceptable matching attachment data found. ${dataInfo.file.absolutePath}")
    return dataInfo
  }

  private fun findDuplicateDataFileInfos(
    database: SQLiteDatabase,
    hash: String?,
    excludedAttachmentId: AttachmentId?
  ): List<DataInfo> {
    check(database.inTransaction()) { "Must be in a transaction!" }

    if (hash == null) {
      return emptyList()
    }

    val selectorArgs: Pair<String, Array<String>> = buildSharedFileSelectorArgs(hash, excludedAttachmentId)

    return database
      .select(DATA_FILE, DATA_RANDOM, DATA_SIZE, TRANSFORM_PROPERTIES)
      .from(TABLE_NAME)
      .where(selectorArgs.first, selectorArgs.second)
      .run()
      .readToList { cursor ->
        DataInfo(
          file = File(cursor.requireNonNullString(DATA_FILE)),
          length = cursor.requireLong(DATA_SIZE),
          random = cursor.requireNonNullBlob(DATA_RANDOM),
          hash = hash,
          transformProperties = TransformProperties.parse(cursor.requireString(TRANSFORM_PROPERTIES))
        )
      }
  }

  private fun buildSharedFileSelectorArgs(newHash: String, attachmentId: AttachmentId?): Pair<String, Array<String>> {
    return if (attachmentId == null) {
      "$DATA_HASH = ?" to arrayOf(newHash)
    } else {
      "$ID != ? AND $DATA_HASH = ?" to arrayOf(
        attachmentId.id.toString(),
        newHash
      )
    }
  }

  @Throws(MmsException::class)
  private fun insertAttachment(mmsId: Long, attachment: Attachment, quote: Boolean): AttachmentId {
    Log.d(TAG, "Inserting attachment for mms id: $mmsId")

    var notifyPacks = false

    val attachmentId: AttachmentId = writableDatabase.withinTransaction { db ->
      try {
        var dataInfo: DataInfo? = null

        if (attachment.uri != null) {
          val storeDataInfo = storeAttachmentStream(PartAuthority.getAttachmentStream(context, attachment.uri!!))
          Log.d(TAG, "Wrote part to file: ${storeDataInfo.file.absolutePath}")

          dataInfo = deduplicateAttachment(storeDataInfo, null, attachment.transformProperties)
        }

        var template = attachment
        var useTemplateUpload = false

        if (dataInfo != null) {
          val possibleTemplates = findTemplateAttachments(dataInfo.hash)

          for (possibleTemplate in possibleTemplates) {
            useTemplateUpload = possibleTemplate.uploadTimestamp > attachment.uploadTimestamp &&
              possibleTemplate.transferState == TRANSFER_PROGRESS_DONE &&
              possibleTemplate.transformProperties?.shouldSkipTransform() == true && possibleTemplate.remoteDigest != null &&
              attachment.transformProperties?.videoEdited == false && possibleTemplate.transformProperties.sentMediaQuality == attachment.transformProperties.sentMediaQuality

            if (useTemplateUpload) {
              Log.i(TAG, "Found a duplicate attachment upon insertion. Using it as a template.")
              template = possibleTemplate
              break
            }
          }
        }

        val contentValues = ContentValues()
        contentValues.put(MESSAGE_ID, mmsId)
        contentValues.put(CONTENT_TYPE, template.contentType)
        contentValues.put(TRANSFER_STATE, attachment.transferState)
        contentValues.put(CDN_NUMBER, if (useTemplateUpload) template.cdnNumber else attachment.cdnNumber)
        contentValues.put(REMOTE_LOCATION, if (useTemplateUpload) template.remoteLocation else attachment.remoteLocation)
        contentValues.put(REMOTE_DIGEST, if (useTemplateUpload) template.remoteDigest else attachment.remoteDigest)
        contentValues.put(REMOTE_INCREMENTAL_DIGEST, if (useTemplateUpload) template.incrementalDigest else attachment.incrementalDigest)
        contentValues.put(REMOTE_INCREMENTAL_DIGEST_CHUNK_SIZE, if (useTemplateUpload) template.incrementalMacChunkSize else attachment.incrementalMacChunkSize)
        contentValues.put(REMOTE_KEY, if (useTemplateUpload) template.remoteKey else attachment.remoteKey)
        contentValues.put(FILE_NAME, StorageUtil.getCleanFileName(attachment.fileName))
        contentValues.put(DATA_SIZE, template.size)
        contentValues.put(FAST_PREFLIGHT_ID, attachment.fastPreflightId)
        contentValues.put(VOICE_NOTE, if (attachment.voiceNote) 1 else 0)
        contentValues.put(BORDERLESS, if (attachment.borderless) 1 else 0)
        contentValues.put(VIDEO_GIF, if (attachment.videoGif) 1 else 0)
        contentValues.put(WIDTH, template.width)
        contentValues.put(HEIGHT, template.height)
        contentValues.put(QUOTE, quote)
        contentValues.put(CAPTION, attachment.caption)
        contentValues.put(UPLOAD_TIMESTAMP, if (useTemplateUpload) template.uploadTimestamp else attachment.uploadTimestamp)

        if (attachment.transformProperties?.videoEdited == true) {
          contentValues.putNull(BLUR_HASH)
          contentValues.put(TRANSFORM_PROPERTIES, attachment.transformProperties?.serialize())
        } else {
          contentValues.put(BLUR_HASH, template.getVisualHashStringOrNull())
          contentValues.put(TRANSFORM_PROPERTIES, (if (useTemplateUpload) template else attachment).transformProperties?.serialize())
        }

        attachment.stickerLocator?.let { sticker ->
          contentValues.put(STICKER_PACK_ID, sticker.packId)
          contentValues.put(STICKER_PACK_KEY, sticker.packKey)
          contentValues.put(STICKER_ID, sticker.stickerId)
          contentValues.put(STICKER_EMOJI, sticker.emoji)
        }

        if (dataInfo != null) {
          contentValues.put(DATA_FILE, dataInfo.file.absolutePath)
          contentValues.put(DATA_SIZE, dataInfo.length)
          contentValues.put(DATA_RANDOM, dataInfo.random)

          if (attachment.transformProperties?.videoEdited == true) {
            contentValues.putNull(DATA_HASH)
          } else {
            contentValues.put(DATA_HASH, dataInfo.hash)
          }
        }

        notifyPacks = attachment.isSticker && !hasStickerAttachments()

        val rowId = db.insert(TABLE_NAME, null, contentValues)
        AttachmentId(rowId)
      } catch (e: IOException) {
        throw MmsException(e)
      }
    }

    if (notifyPacks) {
      notifyStickerPackListeners()
    }

    notifyAttachmentListeners()
    return attachmentId
  }

  private fun findTemplateAttachments(dataHash: String?): List<DatabaseAttachment> {
    if (dataHash == null) {
      return emptyList()
    }

    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$DATA_HASH = ?", dataHash)
      .run()
      .readToList { it.readAttachment() }
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
      cdnNumber = cursor.requireInt(CDN_NUMBER),
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
      dataHash = cursor.requireString(DATA_HASH)
    )
  }

  private fun Cursor.readAttachments(): List<DatabaseAttachment> {
    return getAttachments(this)
  }

  private fun Cursor.readAttachment(): DatabaseAttachment {
    return getAttachment(this)
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
      this.blurHash != null -> this.blurHash!!.hash
      this.audioHash != null -> this.audioHash!!.hash
      else -> null
    }
  }

  fun debugGetLatestAttachments(): List<DatabaseAttachment> {
    return readableDatabase
      .select(*PROJECTION)
      .from(TABLE_NAME)
      .where("$TRANSFER_STATE == $TRANSFER_PROGRESS_DONE AND $REMOTE_LOCATION IS NOT NULL AND $DATA_HASH IS NOT NULL")
      .orderBy("$ID DESC")
      .limit(30)
      .run()
      .readToList { it.readAttachments() }
      .flatten()
  }

  @VisibleForTesting
  class DataInfo(
    val file: File,
    val length: Long,
    val random: ByteArray,
    val hash: String?,
    val transformProperties: TransformProperties?
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as DataInfo

      if (file != other.file) return false
      if (length != other.length) return false
      if (!random.contentEquals(other.random)) return false
      if (hash != other.hash) return false
      return transformProperties == other.transformProperties
    }

    override fun hashCode(): Int {
      var result = file.hashCode()
      result = 31 * result + length.hashCode()
      result = 31 * result + random.contentHashCode()
      result = 31 * result + (hash?.hashCode() ?: 0)
      result = 31 * result + (transformProperties?.hashCode() ?: 0)
      return result
    }
  }

  /**
   * @param removableWeakReferences Entries in here can be removed from the database. Only possible to be non-empty when [hasStrongReference] is false.
   */
  private class DataUsageResult private constructor(val hasStrongReference: Boolean, val removableWeakReferences: List<AttachmentId>) {
    constructor(removableWeakReferences: List<AttachmentId>) : this(false, removableWeakReferences)

    init {
      if (hasStrongReference && removableWeakReferences.isNotEmpty()) {
        throw IllegalStateException("There's a strong reference and removable weak references!")
      }
    }

    companion object {
      val IN_USE = DataUsageResult(true, emptyList())
      val NOT_IN_USE = DataUsageResult(false, emptyList())
    }
  }

  @Parcelize
  data class TransformProperties(
    @JsonProperty("skipTransform")
    @JvmField
    val skipTransform: Boolean,

    @JsonProperty("videoTrim")
    @JvmField
    val videoTrim: Boolean,

    @JsonProperty("videoTrimStartTimeUs")
    @JvmField
    val videoTrimStartTimeUs: Long,

    @JsonProperty("videoTrimEndTimeUs")
    @JvmField
    val videoTrimEndTimeUs: Long,

    @JsonProperty("sentMediaQuality")
    @JvmField
    val sentMediaQuality: Int,

    @JsonProperty("mp4Faststart")
    @JvmField
    val mp4FastStart: Boolean
  ) : Parcelable {
    fun shouldSkipTransform(): Boolean {
      return skipTransform
    }

    @IgnoredOnParcel
    @JsonProperty("videoEdited")
    val videoEdited: Boolean = videoTrim

    fun withSkipTransform(): TransformProperties {
      return this.copy(
        skipTransform = true,
        videoTrim = false,
        videoTrimStartTimeUs = 0,
        videoTrimEndTimeUs = 0,
        mp4FastStart = false
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
}

/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.core.content.contentValuesOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.PartAuthority
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

/**
 * Helper type to ensure we don't reuse names when downloading a large set of
 * unnamed files. Android is quite slow to update its internal data-sets so reading
 * from them as we're processing and saving attachments won't always give the most
 * up to date information.
 */
private typealias BatchOperationNameCache = HashMap<Uri, HashSet<String>>

/**
 * This is a rewrite of [SaveAttachmentTask] that does not handle displaying
 * a progress dialog and is not backed by an async task.
 */
object SaveAttachmentUtil {

  private val TAG = Log.tag(SaveAttachmentUtil::class.java)

  fun showWarningDialogIfNecessary(context: Context, count: Int, onSave: () -> Unit) {
    if (SignalStore.uiHints.hasDismissedSaveStorageWarning()) {
      onSave()
    } else {
      MaterialAlertDialogBuilder(context)
        .setView(R.layout.dialog_save_attachment)
        .setTitle(R.string.ConversationFragment__save_to_phone)
        .setCancelable(true)
        .setMessage(context.resources.getQuantityString(R.plurals.ConversationFragment__this_media_will_be_saved, count, count))
        .setPositiveButton(R.string.save) { dialog, _ ->
          val checkbox = (dialog as AlertDialog).findViewById<CheckBox>(R.id.checkbox)!!
          if (checkbox.isChecked) {
            SignalStore.uiHints.markDismissedSaveStorageWarning()
          }
          onSave()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
    }
  }

  fun getAttachmentsForRecord(record: MmsMessageRecord): Set<SaveAttachment> {
    return record.slideDeck.slides
      .filter { it.uri != null && (it.hasImage() || it.hasVideo() || it.hasAudio() || it.hasDocument()) }
      .map { SaveAttachment(it.uri!!, it.contentType, record.dateSent, it.fileName.orNull()) }
      .toSet()
  }

  suspend fun saveAttachments(attachments: Set<SaveAttachment>): SaveAttachmentsResult {
    check(attachments.isNotEmpty()) { "must pass in at least one attachment" }

    if (!StorageUtil.canWriteToMediaStore()) {
      return SaveAttachmentsResult.ErrorNoWriteAccess(errorCount = attachments.size)
    }

    val nameCache: BatchOperationNameCache = HashMap()

    val (successes, errors) = attachments
      .map { saveAttachment(it, nameCache) }
      .partition { saveResult -> saveResult is SaveAttachmentResult.Success }

    return SaveAttachmentsResult.Completed(
      successCount = successes.size,
      errorCount = errors.size
    ).also {
      Log.i(TAG, "Save attachments completed (${it.successCount} of ${attachments.size} saved successfully).")
    }
  }

  private suspend fun saveAttachment(attachment: SaveAttachment, nameCache: BatchOperationNameCache): SaveAttachmentResult = withContext(Dispatchers.IO) {
    try {
      val contentType: String = MediaUtil.getCorrectedMimeType(attachment.contentType)!!
      val fileName: String = sanitizeOutputFileName(attachment.fileName ?: generateOutputFileName(contentType, attachment.date))
      val result: CreateMediaUriResult = createMediaUri(getMediaStoreContentUriForType(contentType), contentType, fileName, nameCache)
      val updateValues = ContentValues()
      val mediaUri = result.mediaUri ?: return@withContext SaveAttachmentResult.ErrorSavingFile
      val inputStream = PartAuthority.getAttachmentStream(AppDependencies.application, attachment.uri) ?: return@withContext SaveAttachmentResult.ErrorSavingFile

      inputStream.use { inStream ->
        if (result.outputUri.scheme == ContentResolver.SCHEME_FILE) {
          FileOutputStream(mediaUri.path).use { outStream ->
            StreamUtil.copy(inStream, outStream)
            MediaScannerConnection.scanFile(AppDependencies.application, arrayOf(mediaUri.path), arrayOf(contentType), null)
          }
        } else {
          AppDependencies.application.contentResolver.openOutputStream(mediaUri, "w").use { outStream ->
            val total = StreamUtil.copy(inStream, outStream)
            if (total > 0) {
              updateValues.put(MediaStore.MediaColumns.SIZE, total)
            }
          }
        }
      }

      if (Build.VERSION.SDK_INT > 28) {
        updateValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
      }

      if (updateValues.size() > 0) {
        AppDependencies.application.contentResolver.update(mediaUri, updateValues, null, null)
      }

      return@withContext if (result.outputUri.lastPathSegment != null) {
        SaveAttachmentResult.Success
      } else {
        SaveAttachmentResult.ErrorSavingFile
      }
    } catch (e: IOException) {
      Log.w(TAG, "Failed to save attachment", e)
      return@withContext SaveAttachmentResult.ErrorSavingFile
    }
  }

  private fun getMediaStoreContentUriForType(contentType: String): Uri {
    return when {
      contentType.startsWith("video/") -> StorageUtil.getVideoUri()
      contentType.startsWith("audio/") -> StorageUtil.getAudioUri()
      contentType.startsWith("image/") -> StorageUtil.getImageUri()
      else -> StorageUtil.getDownloadUri()
    }
  }

  @SuppressLint("SimpleDateFormat")
  private fun generateOutputFileName(contentType: String, timestamp: Long): String {
    val mimeTypeMap = MimeTypeMap.getSingleton()
    val extension = mimeTypeMap.getExtensionFromMimeType(contentType) ?: "attach"
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS")
    val base = "signal-${dateFormatter.format(timestamp)}"

    return "$base.$extension"
  }

  private fun sanitizeOutputFileName(fileName: String): String {
    return File(fileName).name
  }

  @Throws(IOException::class)
  private fun createMediaUri(outputUri: Uri, contentType: String, fileName: String, nameCache: BatchOperationNameCache): CreateMediaUriResult {
    val (base, extension) = getFileNameParts(fileName)
    var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

    if (MediaUtil.isOctetStream(mimeType) && MediaUtil.isImageOrVideoType(contentType)) {
      Log.d(TAG, "MimeTypeMap returned octet stream for media, changing to provided content type [$contentType] instead.")
      mimeType = contentType
    }

    if (MediaUtil.isOctetStream(mimeType)) {
      mimeType = when {
        outputUri == StorageUtil.getAudioUri() -> "audio/*"
        outputUri == StorageUtil.getVideoUri() -> "video/*"
        outputUri == StorageUtil.getImageUri() -> "image/*"
        else -> mimeType
      }
    }

    val contentValues = contentValuesOf(
      MediaStore.MediaColumns.DISPLAY_NAME to fileName,
      MediaStore.MediaColumns.MIME_TYPE to mimeType,
      MediaStore.MediaColumns.DATE_ADDED to TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
      MediaStore.MediaColumns.DATE_MODIFIED to TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
    )

    if (Build.VERSION.SDK_INT > 28) {
      var i = 0
      var displayName = fileName

      while (nameCache.pathInCache(outputUri, displayName) || displayNameTaken(outputUri, displayName)) {
        displayName = "$base-${++i}.$extension"
      }

      contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
      contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
      nameCache.putInCache(outputUri, displayName)
    } else if (outputUri.scheme == ContentResolver.SCHEME_FILE) {
      val outputDirectory = File(outputUri.path!!)
      var outputFile = File(outputDirectory, "$base.$extension")

      var i = 0
      while (nameCache.pathInCache(outputUri, outputFile.path) || outputFile.exists()) {
        outputFile = File(outputDirectory, "$base-${++i}.$extension")
      }

      if (outputFile.isHidden) {
        throw IOException("Specified name would not be visible.")
      }

      nameCache.putInCache(outputUri, outputFile.path)
      return CreateMediaUriResult(outputUri, Uri.fromFile(outputFile))
    } else {
      val dir = getExternalPathForType(contentType) ?: throw IOException("Path for type: $contentType was not available")

      var outputFileName = fileName
      var dataPath = "$dir/$outputFileName"
      var i = 0

      while (nameCache.pathInCache(outputUri, dataPath) || pathTaken(outputUri, dataPath)) {
        Log.d(TAG, "The content exists. Rename and check again.")
        outputFileName = "$base-${++i}.$extension"
        dataPath = "$dir/$outputFileName"
      }

      nameCache.putInCache(outputUri, outputFileName)
      contentValues.put(MediaStore.MediaColumns.DATA, dataPath)
    }

    return try {
      CreateMediaUriResult(outputUri, AppDependencies.application.contentResolver.insert(outputUri, contentValues))
    } catch (e: RuntimeException) {
      if (e is IllegalArgumentException || e.cause is IllegalArgumentException) {
        Log.w(TAG, "Unable to create uri in $outputUri with mimeType [$mimeType]")
        CreateMediaUriResult(StorageUtil.getDownloadUri(), AppDependencies.application.contentResolver.insert(StorageUtil.getDownloadUri(), contentValues))
      } else {
        throw e
      }
    }
  }

  private fun getExternalPathForType(contentType: String): String? {
    val storage: File? = when {
      contentType.startsWith("video/") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
      contentType.startsWith("audio/") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
      contentType.startsWith("image/") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
      else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    }

    return storage?.let { ensureExternalPath(storage) }?.absolutePath
  }

  private fun ensureExternalPath(path: File): File? {
    return path.takeIf { path.exists() || path.mkdirs() }
  }

  private fun BatchOperationNameCache.putInCache(outputUri: Uri, dataPath: String) {
    val pathSet: HashSet<String> = this.getOrElse(outputUri) { HashSet() }
    if (!pathSet.add(dataPath)) {
      error("Path already used in data set.")
    }

    this[outputUri] = pathSet
  }

  private fun BatchOperationNameCache.pathInCache(outputUri: Uri, dataPath: String): Boolean {
    return this[outputUri]?.contains(dataPath) ?: return false
  }

  @Throws(IOException::class)
  private fun pathTaken(outputUri: Uri, dataPath: String): Boolean {
    val cursor: Cursor = AppDependencies.application.contentResolver.query(
      outputUri,
      arrayOf(MediaStore.MediaColumns.DATA),
      "${MediaStore.MediaColumns.DATA} = ?",
      arrayOf(dataPath),
      null
    ) ?: throw IOException("Something is wrong with the file name to save")

    return cursor.use { it.moveToFirst() }
  }

  @Throws(IOException::class)
  private fun displayNameTaken(outputUri: Uri, displayName: String): Boolean {
    val cursor: Cursor = AppDependencies.application.contentResolver.query(
      outputUri,
      arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
      "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
      arrayOf(displayName),
      null
    ) ?: throw IOException("Something is wrong with the displayName to save")

    return cursor.use { it.moveToFirst() }
  }

  private fun getFileNameParts(fileName: String): Pair<String, String> {
    val tokens = fileName.split(Regex("\\.(?=[^\\.]+$)"))

    return Pair(
      tokens[0],
      if (tokens.size > 1) tokens[1] else ""
    )
  }

  private sealed interface SaveAttachmentResult {
    data object Success : SaveAttachmentResult
    data object ErrorSavingFile : SaveAttachmentResult
  }

  sealed interface SaveAttachmentsResult {
    val successCount: Int
    val errorCount: Int

    fun getMessage(context: Context): CharSequence

    data class Completed(
      override val successCount: Int,
      override val errorCount: Int
    ) : SaveAttachmentsResult {

      override fun getMessage(context: Context): CharSequence {
        return when {
          errorCount == 0 -> context.resources.getQuantityText(R.plurals.SaveAttachment_saved_success, successCount)
          successCount == 0 -> context.resources.getQuantityText(R.plurals.SaveAttachment_error_while_saving_attachments_to_sd_card, errorCount)
          else -> {
            val numberFormat = NumberFormat.getInstance()
            context.resources.getQuantityString(
              R.plurals.SaveAttachment_saved_success_n_failures,
              errorCount,
              numberFormat.format(errorCount),
              numberFormat.format(errorCount + successCount)
            )
          }
        }
      }
    }

    data class ErrorNoWriteAccess(
      override val errorCount: Int
    ) : SaveAttachmentsResult {
      override val successCount: Int = 0

      override fun getMessage(context: Context): CharSequence {
        return context.getString(R.string.SaveAttachment_unable_to_write_to_sd_card_exclamation)
      }
    }
  }

  data class SaveAttachment(
    val uri: Uri,
    val contentType: String,
    val date: Long,
    val fileName: String?
  ) {
    init {
      check(date > 0L) { "Date must be greater than zero." }
    }
  }

  private data class CreateMediaUriResult(
    val outputUri: Uri,
    val mediaUri: Uri?
  )
}

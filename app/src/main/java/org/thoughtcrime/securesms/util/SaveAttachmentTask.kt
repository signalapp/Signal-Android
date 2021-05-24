package org.thoughtcrime.securesms.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface.OnClickListener
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import network.loki.messenger.R
import org.session.libsession.utilities.task.ProgressDialogAsyncTask
import org.session.libsignal.utilities.ExternalStorageUtil
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.mms.PartAuthority
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Saves attachment files to an external storage using [MediaStore] API.
 * Requires [android.Manifest.permission.WRITE_EXTERNAL_STORAGE] on API 28 and below.
 */
class SaveAttachmentTask : ProgressDialogAsyncTask<SaveAttachmentTask.Attachment, Void, Pair<Int, String?>> {

    companion object {
        @JvmStatic
        private val TAG = SaveAttachmentTask::class.simpleName

        private const val RESULT_SUCCESS = 0
        private const val RESULT_FAILURE = 1

        @JvmStatic
        @JvmOverloads
        fun showWarningDialog(context: Context, onAcceptListener: OnClickListener, count: Int = 1) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(R.string.ConversationFragment_save_to_sd_card)
            builder.setIconAttribute(R.attr.dialog_alert_icon)
            builder.setCancelable(true)
            builder.setMessage(context.resources.getQuantityString(
                    R.plurals.ConversationFragment_saving_n_media_to_storage_warning,
                    count,
                    count))
            builder.setPositiveButton(R.string.yes, onAcceptListener)
            builder.setNegativeButton(R.string.no, null)
            builder.show()
        }
    }

    private val contextReference: WeakReference<Context>
    private val attachmentCount: Int

    @JvmOverloads
    constructor(context: Context, count: Int = 1): super(context,
            context.resources.getQuantityString(R.plurals.ConversationFragment_saving_n_attachments, count, count),
            context.resources.getQuantityString(R.plurals.ConversationFragment_saving_n_attachments_to_sd_card, count, count)) {
        this.contextReference = WeakReference(context)
        this.attachmentCount = count
    }

    override fun doInBackground(vararg attachments: Attachment?): Pair<Int, String?> {
        if (attachments.isEmpty()) {
            throw IllegalArgumentException("Must pass in at least one attachment")
        }

        try {
            val context = contextReference.get()
            var directory: String? = null

            if (context == null) {
                return Pair(RESULT_FAILURE, null)
            }

            for (attachment in attachments) {
                if (attachment != null) {
                    directory = saveAttachment(context, attachment)
                    if (directory == null) return Pair(RESULT_FAILURE, null)
                }
            }

            return if (attachments.size > 1)
                Pair(RESULT_SUCCESS, null)
            else
                Pair(RESULT_SUCCESS, directory)
        } catch (e: IOException) {
            Log.w(TAG, e)
            return Pair(RESULT_FAILURE, null)
        }
    }

    @Throws(IOException::class)
    private fun saveAttachment(context: Context, attachment: Attachment): String? {
        val contentType = Objects.requireNonNull(MediaUtil.getCorrectedMimeType(attachment.contentType))!!
        var fileName = attachment.fileName
        if (fileName == null) fileName = generateOutputFileName(contentType, attachment.date)
        fileName = sanitizeOutputFileName(fileName)
        val outputUri: Uri = getMediaStoreContentUriForType(contentType)
        val mediaUri = createOutputUri(outputUri, contentType, fileName)
        val updateValues = ContentValues()
        PartAuthority.getAttachmentStream(context, attachment.uri).use { inputStream ->
            if (inputStream == null) {
                return null
            }
            if (outputUri.scheme == ContentResolver.SCHEME_FILE) {
                FileOutputStream(mediaUri!!.path).use { outputStream ->
                    StreamUtil.copy(inputStream, outputStream)
                    MediaScannerConnection.scanFile(context, arrayOf(mediaUri.path), arrayOf(contentType), null)
                }
            } else {
                context.contentResolver.openOutputStream(mediaUri!!, "w").use { outputStream ->
                    val total: Long = StreamUtil.copy(inputStream, outputStream)
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
            getContext().contentResolver.update(mediaUri!!, updateValues, null, null)
        }
        return outputUri.lastPathSegment
    }

    private fun getMediaStoreContentUriForType(contentType: String): Uri {
        return when {
            contentType.startsWith("video/") ->
                ExternalStorageUtil.getVideoUri()
            contentType.startsWith("audio/") ->
                ExternalStorageUtil.getAudioUri()
            contentType.startsWith("image/") ->
                ExternalStorageUtil.getImageUri()
            else ->
                ExternalStorageUtil.getDownloadUri()
        }
    }

    @Throws(IOException::class)
    private fun createOutputUri(outputUri: Uri, contentType: String, fileName: String): Uri? {
        val fileParts: Array<String> = getFileNameParts(fileName)
        val base = fileParts[0]
        val extension = fileParts[1]
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        contentValues.put(MediaStore.MediaColumns.DATE_ADDED, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
        contentValues.put(MediaStore.MediaColumns.DATE_MODIFIED, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
        if (Build.VERSION.SDK_INT > 28) {
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
        } else if (Objects.equals(outputUri.scheme, ContentResolver.SCHEME_FILE)) {
            val outputDirectory = File(outputUri.path)
            var outputFile = File(outputDirectory, "$base.$extension")
            var i = 0
            while (outputFile.exists()) {
                outputFile = File(outputDirectory, base + "-" + ++i + "." + extension)
            }
            if (outputFile.isHidden) {
                throw IOException("Specified name would not be visible")
            }
            return Uri.fromFile(outputFile)
        } else {
            var outputFileName = fileName
            var dataPath = String.format("%s/%s", getExternalPathToFileForType(contentType), outputFileName)
            var i = 0
            while (pathTaken(outputUri, dataPath)) {
                Log.d(TAG, "The content exists. Rename and check again.")
                outputFileName = base + "-" + ++i + "." + extension
                dataPath = String.format("%s/%s", getExternalPathToFileForType(contentType), outputFileName)
            }
            contentValues.put(MediaStore.MediaColumns.DATA, dataPath)
        }
        return context.contentResolver.insert(outputUri, contentValues)
    }

    private fun getFileNameParts(fileName: String): Array<String> {
        val tokens = fileName.split("\\.(?=[^\\.]+$)".toRegex()).toTypedArray()
        return arrayOf(tokens[0], if (tokens.size > 1) tokens[1] else "")
    }

    private fun getExternalPathToFileForType(contentType: String): String {
        val storage: File = when {
            contentType.startsWith("video/") ->
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)!!
            contentType.startsWith("audio/") ->
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)!!
            contentType.startsWith("image/") ->
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
            else ->
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)!!
        }
        return storage.absolutePath
    }

    @Throws(IOException::class)
    private fun pathTaken(outputUri: Uri, dataPath: String): Boolean {
        context.contentResolver.query(outputUri, arrayOf(MediaStore.MediaColumns.DATA),
                MediaStore.MediaColumns.DATA + " = ?", arrayOf(dataPath),
                null).use { cursor ->
            if (cursor == null) {
                throw IOException("Something is wrong with the filename to save")
            }
            return cursor.moveToFirst()
        }
    }

    private fun generateOutputFileName(contentType: String, timestamp: Long): String {
        val mimeTypeMap = MimeTypeMap.getSingleton()
        val extension = mimeTypeMap.getExtensionFromMimeType(contentType) ?: "attach"
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd-HHmmss")
        val base = "session-${dateFormatter.format(timestamp)}"

        return "${base}.${extension}";
    }

    private fun sanitizeOutputFileName(fileName: String): String {
        return File(fileName).name
    }

    override fun onPostExecute(result: Pair<Int, String?>) {
        super.onPostExecute(result)
        val context = contextReference.get() ?: return

        when (result.first) {
            RESULT_FAILURE -> {
                val message = context.resources.getQuantityText(
                        R.plurals.ConversationFragment_error_while_saving_attachments_to_sd_card,
                        attachmentCount)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }

            RESULT_SUCCESS -> {
                val message = if (!TextUtils.isEmpty(result.second)) {
                    context.resources.getString(R.string.SaveAttachmentTask_saved_to, result.second)
                } else {
                    context.resources.getString(R.string.SaveAttachmentTask_saved)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }

            else -> throw IllegalStateException("Unexpected result value: " + result.first)
        }
    }

    data class Attachment(val uri: Uri, val contentType: String, val date: Long, val fileName: String?)
}
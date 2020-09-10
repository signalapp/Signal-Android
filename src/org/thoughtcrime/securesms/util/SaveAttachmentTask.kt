package org.thoughtcrime.securesms.util

import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface.OnClickListener
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import network.loki.messenger.R
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat

/**
 * Saves attachment files to an external storage using [MediaStore] API.
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
        val resolver = context.contentResolver

        val contentType = MediaUtil.getCorrectedMimeType(attachment.contentType)!!
        val fileName = attachment.fileName
                ?: sanitizeOutputFileName(generateOutputFileName(contentType, attachment.date))

        val mediaRecord = ContentValues()
        val mediaVolume = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            MediaStore.VOLUME_EXTERNAL
        } else {
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        }
        val collectionUri: Uri

        when {
            contentType.startsWith("video/") -> {
                collectionUri = MediaStore.Video.Media.getContentUri(mediaVolume)
                mediaRecord.put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                mediaRecord.put(MediaStore.Video.Media.MIME_TYPE, contentType)
                // Add the date meta data to ensure the image is added at the front of the gallery
                mediaRecord.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis())
                mediaRecord.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())

            }
            contentType.startsWith("audio/") -> {
                collectionUri = MediaStore.Audio.Media.getContentUri(mediaVolume)
                mediaRecord.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                mediaRecord.put(MediaStore.Audio.Media.MIME_TYPE, contentType)
                mediaRecord.put(MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis())
                mediaRecord.put(MediaStore.Audio.Media.DATE_TAKEN, System.currentTimeMillis())

            }
            contentType.startsWith("image/") -> {
                collectionUri = MediaStore.Images.Media.getContentUri(mediaVolume)
                mediaRecord.put(MediaStore.Images.Media.TITLE, fileName)
                mediaRecord.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                mediaRecord.put(MediaStore.Images.Media.MIME_TYPE, contentType)
                mediaRecord.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
                mediaRecord.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())

            }
            else -> {
                mediaRecord.put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                collectionUri = MediaStore.Files.getContentUri(mediaVolume)
            }
        }

        val mediaFileUri = resolver.insert(collectionUri, mediaRecord)
        if (mediaFileUri == null) return null

        val inputStream = PartAuthority.getAttachmentStream(context, attachment.uri)
        if (inputStream == null) return null

        inputStream.use {
            resolver.openOutputStream(mediaFileUri).use {
                Util.copy(inputStream, it)
            }
        }

        return mediaFileUri.toString()
    }

    private fun generateOutputFileName(contentType: String, timestamp: Long): String {
        val mimeTypeMap = MimeTypeMap.getSingleton()
        val extension = mimeTypeMap.getExtensionFromMimeType(contentType) ?: "attach"
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd-HHmmss")
        val base = "signal-${dateFormatter.format(timestamp)}"

        return "${base}.${extension}";
    }

    private fun sanitizeOutputFileName(fileName: String): String {
        return File(fileName).name
    }

    override fun onPostExecute(result: Pair<Int, String?>) {
        super.onPostExecute(result)
        val context = contextReference.get()
        if (context == null) return

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
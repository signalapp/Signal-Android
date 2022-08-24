package org.signal.smsexporter.internal.mms

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import org.signal.core.util.Try
import org.signal.core.util.logging.Log
import org.signal.smsexporter.ExportableMessage

/**
 * Inserts the part objects for the given list of mms message insertion outputs. Returns a list
 * of attachments that can be enqueued for a disk write.
 */
internal object ExportMmsPartsUseCase {

  private val TAG = Log.tag(ExportMmsPartsUseCase::class.java)

  internal fun getContentId(part: ExportableMessage.Mms.Part): String {
    return "<signal:${part.contentId}>"
  }

  fun execute(context: Context, part: ExportableMessage.Mms.Part, output: ExportMmsMessagesUseCase.Output, checkForExistence: Boolean): Try<Output> {
    try {
      val (message, messageId) = output
      val contentId = getContentId(part)
      val mmsPartUri = Telephony.Mms.CONTENT_URI.buildUpon().appendPath(messageId.toString()).appendPath("part").build()

      if (checkForExistence) {
        Log.d(TAG, "Checking attachment that may already be present...")
        val partId: Long? = context.contentResolver.query(mmsPartUri, arrayOf(Telephony.Mms.Part._ID), "${Telephony.Mms.Part.CONTENT_ID} = ?", arrayOf(contentId), null)?.use {
          if (it.moveToFirst()) {
            it.getLong(0)
          } else {
            null
          }
        }

        if (partId != null) {
          Log.d(TAG, "Found attachment part that already exists.")
          return Try.success(
            Output(
              uri = ContentUris.withAppendedId(mmsPartUri, partId),
              part = part,
              message = message
            )
          )
        }
      }

      val mmsPartContentValues = contentValuesOf(
        Telephony.Mms.Part.MSG_ID to messageId,
        Telephony.Mms.Part.CONTENT_TYPE to part.contentType,
        Telephony.Mms.Part.CONTENT_ID to contentId,
        Telephony.Mms.Part.TEXT to if (part is ExportableMessage.Mms.Part.Text) part.text else null
      )

      val attachmentUri = context.contentResolver.insert(mmsPartUri, mmsPartContentValues)!!
      return Try.success(Output(attachmentUri, part, message))
    } catch (e: Exception) {
      return Try.failure(e)
    }
  }

  data class Output(val uri: Uri, val part: ExportableMessage.Mms.Part, val message: ExportableMessage)
}

package org.signal.smsexporter.internal.mms

import android.content.ContentUris
import android.content.Context
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import com.google.android.mms.pdu_alt.PduHeaders
import org.signal.core.util.Try
import org.signal.core.util.logging.Log
import org.signal.smsexporter.ExportableMessage

/**
 * Takes a list of messages and inserts them as a single batch. This includes
 * thread id get/create if necessary. The output is a list of (mms, message_id)
 */
internal object ExportMmsMessagesUseCase {

  private val TAG = Log.tag(ExportMmsMessagesUseCase::class.java)

  internal fun getTransactionId(mms: ExportableMessage.Mms<*>): String {
    return "signal:T${mms.id}"
  }

  fun execute(
    context: Context,
    getOrCreateThreadOutput: GetOrCreateMmsThreadIdsUseCase.Output,
    checkForExistence: Boolean
  ): Try<Output> {
    try {
      val (mms, threadId) = getOrCreateThreadOutput
      val transactionId = getTransactionId(mms)

      if (checkForExistence) {
        Log.d(TAG, "Checking if the message is already in the database.")
        val messageId = isMessageAlreadyInDatabase(context, transactionId)
        if (messageId != -1L) {
          Log.d(TAG, "Message exists in database. Returning its id.")
          return Try.success(Output(mms, messageId))
        }
      }

      val mmsContentValues = contentValuesOf(
        Telephony.Mms.THREAD_ID to threadId,
        Telephony.Mms.DATE to mms.dateReceived.inWholeSeconds,
        Telephony.Mms.DATE_SENT to mms.dateSent.inWholeSeconds,
        Telephony.Mms.MESSAGE_BOX to if (mms.isOutgoing) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_INBOX,
        Telephony.Mms.READ to if (mms.isRead) 1 else 0,
        Telephony.Mms.CONTENT_TYPE to "application/vnd.wap.multipart.related",
        Telephony.Mms.MESSAGE_TYPE to PduHeaders.MESSAGE_TYPE_SEND_REQ,
        Telephony.Mms.MMS_VERSION to PduHeaders.MMS_VERSION_1_3,
        Telephony.Mms.MESSAGE_CLASS to "personal",
        Telephony.Mms.PRIORITY to PduHeaders.PRIORITY_NORMAL,
        Telephony.Mms.TRANSACTION_ID to transactionId,
        Telephony.Mms.RESPONSE_STATUS to PduHeaders.RESPONSE_STATUS_OK,
        Telephony.Mms.SEEN to 1,
        Telephony.Mms.TEXT_ONLY to if (mms.parts.all { it is ExportableMessage.Mms.Part.Text }) 1 else 0
      )

      val uri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, mmsContentValues)
      val newMessageId = ContentUris.parseId(uri!!)

      return Try.success(Output(getOrCreateThreadOutput.mms, newMessageId))
    } catch (e: Exception) {
      return Try.failure(e)
    }
  }

  private fun isMessageAlreadyInDatabase(context: Context, transactionId: String): Long {
    return context.contentResolver.query(
      Telephony.Mms.CONTENT_URI,
      arrayOf("_id"),
      "${Telephony.Mms.TRANSACTION_ID} == ?",
      arrayOf(transactionId),
      null
    )?.use {
      if (it.moveToFirst()) {
        it.getLong(0)
      } else {
        -1L
      }
    } ?: -1L
  }

  data class Output(
    val mms: ExportableMessage.Mms<*>,
    val messageId: Long
  )
}

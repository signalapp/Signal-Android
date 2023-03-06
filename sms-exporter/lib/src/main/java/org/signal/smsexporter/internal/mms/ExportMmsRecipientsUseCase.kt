package org.signal.smsexporter.internal.mms

import android.content.Context
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.PduHeaders
import org.signal.core.util.Try
import org.signal.core.util.logging.Log
import org.signal.smsexporter.internal.sms.ExportSmsMessagesUseCase

/**
 * Inserts the recipients for each individual message in the insert mms output. Returns nothing.
 */
object ExportMmsRecipientsUseCase {

  private val TAG = Log.tag(ExportSmsMessagesUseCase::class.java)

  fun execute(context: Context, messageId: Long, recipient: String, sender: String, checkForExistence: Boolean): Try<Unit> {
    try {
      val addrUri = Telephony.Mms.CONTENT_URI.buildUpon().appendPath(messageId.toString()).appendPath("addr").build()

      if (checkForExistence) {
        Log.d(TAG, "Checking for recipient that may have already been inserted...")
        val exists = context.contentResolver.query(addrUri, arrayOf("_id"), "${Telephony.Mms.Addr.ADDRESS} == ?", arrayOf(recipient), null)?.use {
          it.moveToFirst()
        } ?: false

        if (exists) {
          Log.d(TAG, "Recipient was already inserted. Skipping.")
          return Try.success(Unit)
        }
      }

      val addrValues = contentValuesOf(
        Telephony.Mms.Addr.ADDRESS to recipient,
        Telephony.Mms.Addr.CHARSET to CharacterSets.DEFAULT_CHARSET,
        Telephony.Mms.Addr.TYPE to if (recipient == sender) PduHeaders.FROM else PduHeaders.TO
      )

      context.contentResolver.insert(addrUri, addrValues)

      return Try.success(Unit)
    } catch (e: Exception) {
      return Try.failure(e)
    }
  }
}

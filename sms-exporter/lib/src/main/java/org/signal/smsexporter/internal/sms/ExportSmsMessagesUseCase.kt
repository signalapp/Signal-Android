package org.signal.smsexporter.internal.sms

import android.content.Context
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import org.signal.core.util.Try
import org.signal.smsexporter.ExportableMessage
import java.lang.Exception

/**
 * Given a list of Sms messages, export each one to the system SMS database
 * Returns nothing.
 */
internal object ExportSmsMessagesUseCase {
  fun execute(context: Context, sms: ExportableMessage.Sms<*>, checkForExistence: Boolean): Try<Unit> {
    try {
      if (checkForExistence) {
        val exists = context.contentResolver.query(
          Telephony.Sms.CONTENT_URI,
          arrayOf("_id"),
          "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE_SENT} = ?",
          arrayOf(sms.address, sms.dateSent.inWholeMilliseconds.toString()),
          null
        )?.use {
          it.count > 0
        } ?: false

        if (exists) {
          return Try.success(Unit)
        }
      }

      val contentValues = contentValuesOf(
        Telephony.Sms.ADDRESS to sms.address,
        Telephony.Sms.BODY to sms.body,
        Telephony.Sms.DATE to sms.dateReceived.inWholeMilliseconds,
        Telephony.Sms.DATE_SENT to sms.dateSent.inWholeMilliseconds,
        Telephony.Sms.READ to if (sms.isRead) 1 else 0,
        Telephony.Sms.TYPE to if (sms.isOutgoing) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX
      )

      context.contentResolver.insert(Telephony.Sms.CONTENT_URI, contentValues)

      return Try.success(Unit)
    } catch (e: Exception) {
      return Try.failure(e)
    }
  }
}

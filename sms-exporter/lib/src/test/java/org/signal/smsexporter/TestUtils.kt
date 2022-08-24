package org.signal.smsexporter

import android.provider.Telephony
import org.robolectric.shadows.ShadowContentResolver
import java.util.UUID

object TestUtils {
  fun generateSmsMessage(
    id: String = UUID.randomUUID().toString(),
    address: String = "+15555060177",
    dateReceived: Long = 2,
    dateSent: Long = 1,
    isRead: Boolean = false,
    isOutgoing: Boolean = false,
    body: String = "Hello, $id"
  ): ExportableMessage.Sms {
    return ExportableMessage.Sms(id, address, dateReceived, dateSent, isRead, isOutgoing, body)
  }

  fun generateMmsMessage(
    id: String = UUID.randomUUID().toString(),
    addresses: Set<String> = setOf("+15555060177"),
    dateReceived: Long = 2,
    dateSent: Long = 1,
    isRead: Boolean = false,
    isOutgoing: Boolean = false,
    parts: List<ExportableMessage.Mms.Part> = listOf(ExportableMessage.Mms.Part.Text("Hello, $id")),
    sender: CharSequence = "+15555060177"
  ): ExportableMessage.Mms {
    return ExportableMessage.Mms(id, addresses, dateReceived, dateSent, isRead, isOutgoing, parts, sender)
  }

  fun setUpSmsContentProviderAndResolver() {
    ShadowContentResolver.registerProviderInternal(
      Telephony.Sms.CONTENT_URI.authority,
      InMemoryContentProvider()
    )
  }

  fun setUpMmsContentProviderAndResolver() {
    ShadowContentResolver.registerProviderInternal(
      Telephony.Mms.CONTENT_URI.authority,
      InMemoryContentProvider()
    )
  }
}

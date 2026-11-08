package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import org.signal.appsettings.usernamelinks.QrScanProblem
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Result of taking data from the QR scanner and trying to resolve it to a recipient.
 */
sealed class QrScanResult {
  class Success(val recipient: Recipient) : QrScanResult()

  class NotFound(val username: String?) : QrScanResult()

  object InvalidData : QrScanResult()

  object NetworkError : QrScanResult()

  object QrNotFound : QrScanResult()
}

/** Why this result couldn't be turned into a chat, or null when it found someone. */
fun QrScanResult.toProblem(): QrScanProblem? {
  return when (this) {
    is QrScanResult.Success -> null
    is QrScanResult.NotFound -> QrScanProblem.NotFound(username)
    QrScanResult.InvalidData -> QrScanProblem.InvalidData
    QrScanResult.NetworkError -> QrScanProblem.NetworkError
    QrScanResult.QrNotFound -> QrScanProblem.QrNotFound
  }
}

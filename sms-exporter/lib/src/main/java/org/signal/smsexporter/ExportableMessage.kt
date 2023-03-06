package org.signal.smsexporter

import kotlin.time.Duration

/**
 * Represents an exportable MMS or SMS message
 */
sealed interface ExportableMessage {

  /**
   * This represents the initial exportState of the message, and it is *not* updated as
   * the message moves through processing.
   */
  val exportState: SmsExportState

  /**
   * An exportable SMS message
   */
  data class Sms<out ID : Any>(
    val id: ID,
    override val exportState: SmsExportState,
    val address: String,
    val dateReceived: Duration,
    val dateSent: Duration,
    val isRead: Boolean,
    val isOutgoing: Boolean,
    val body: String
  ) : ExportableMessage

  /**
   * An exportable MMS message
   */
  data class Mms<out ID : Any>(
    val id: ID,
    override val exportState: SmsExportState,
    val addresses: Set<String>,
    val dateReceived: Duration,
    val dateSent: Duration,
    val isRead: Boolean,
    val isOutgoing: Boolean,
    val parts: List<Part>,
    val sender: CharSequence
  ) : ExportableMessage {
    /**
     * An attachment, attached to an MMS message
     */
    sealed interface Part {

      val contentType: String
      val contentId: String

      data class Text(val text: String) : Part {
        override val contentType: String = "text/plain"
        override val contentId: String = "text"
      }
      data class Stream(
        val id: String,
        override val contentType: String
      ) : Part {
        override val contentId: String = id
      }
    }
  }

  data class Skip<out ID : Any>(
    val id: ID,
    override val exportState: SmsExportState = SmsExportState()
  ) : ExportableMessage
}

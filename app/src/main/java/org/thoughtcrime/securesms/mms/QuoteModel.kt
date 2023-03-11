package org.thoughtcrime.securesms.mms

import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage

class QuoteModel(
  val id: Long,
  val author: RecipientId,
  val text: String,
  val isOriginalMissing: Boolean,
  val attachments: List<Attachment>,
  mentions: List<Mention>?,
  val type: Type,
  val bodyRanges: BodyRangeList?
) {
  val mentions: List<Mention>

  init {
    this.mentions = mentions ?: emptyList()
  }

  enum class Type(val code: Int, val dataMessageType: SignalServiceDataMessage.Quote.Type) {

    NORMAL(0, SignalServiceDataMessage.Quote.Type.NORMAL),
    GIFT_BADGE(1, SignalServiceDataMessage.Quote.Type.GIFT_BADGE);

    companion object {
      @JvmStatic
      fun fromCode(code: Int): Type {
        for (value in values()) {
          if (value.code == code) {
            return value
          }
        }
        throw IllegalArgumentException("Invalid code: $code")
      }

      @JvmStatic
      fun fromDataMessageType(dataMessageType: SignalServiceDataMessage.Quote.Type): Type {
        for (value in values()) {
          if (value.dataMessageType === dataMessageType) {
            return value
          }
        }
        return NORMAL
      }
    }
  }
}

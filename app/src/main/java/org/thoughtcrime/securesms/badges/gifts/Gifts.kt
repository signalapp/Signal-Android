package org.thoughtcrime.securesms.badges.gifts

import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.Base64

/**
 * Helper object for Gift badges
 */
object Gifts {

  /**
   * Request Code for getting token from Google Pay
   */
  const val GOOGLE_PAY_REQUEST_CODE = 3000

  /**
   * Creates an OutgoingSecureMediaMessage which contains the given gift badge.
   */
  fun createOutgoingGiftMessage(
    recipient: Recipient,
    giftBadge: GiftBadge,
    sentTimestamp: Long,
    expiresIn: Long
  ): OutgoingMediaMessage {
    return OutgoingSecureMediaMessage(
      recipient,
      Base64.encodeBytes(giftBadge.toByteArray()),
      listOf(),
      sentTimestamp,
      ThreadDatabase.DistributionTypes.CONVERSATION,
      expiresIn,
      false,
      StoryType.NONE,
      null,
      false,
      null,
      listOf(),
      listOf(),
      listOf(),
      giftBadge
    )
  }
}

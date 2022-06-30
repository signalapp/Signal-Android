package org.thoughtcrime.securesms.database

import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.mms.IncomingMediaMessage
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.Optional

/**
 * Helper methods for inserting an MMS message into the MMS table.
 */
object MmsHelper {

  fun insert(
    recipient: Recipient = Recipient.UNKNOWN,
    body: String = "body",
    sentTimeMillis: Long = System.currentTimeMillis(),
    subscriptionId: Int = -1,
    expiresIn: Long = 0,
    viewOnce: Boolean = false,
    distributionType: Int = ThreadDatabase.DistributionTypes.DEFAULT,
    threadId: Long = 1,
    storyType: StoryType = StoryType.NONE,
    giftBadge: GiftBadge? = null
  ): Long {
    val message = OutgoingMediaMessage(
      recipient,
      body,
      emptyList(),
      sentTimeMillis,
      subscriptionId,
      expiresIn,
      viewOnce,
      distributionType,
      storyType,
      null,
      false,
      null,
      emptyList(),
      emptyList(),
      emptyList(),
      emptySet(),
      emptySet(),
      giftBadge
    )

    return insert(
      message = message,
      threadId = threadId
    )
  }

  fun insert(
    message: OutgoingMediaMessage,
    threadId: Long
  ): Long {
    return SignalDatabase.mms.insertMessageOutbox(message, threadId, false, GroupReceiptDatabase.STATUS_UNKNOWN, null)
  }

  fun insert(
    message: IncomingMediaMessage,
    threadId: Long
  ): Optional<MessageDatabase.InsertResult> {
    return SignalDatabase.mms.insertSecureDecryptedMessageInbox(message, threadId)
  }
}

package org.thoughtcrime.securesms.database

import org.thoughtcrime.securesms.database.model.ParentStoryId
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
    distributionType: Int = ThreadTable.DistributionTypes.DEFAULT,
    threadId: Long = SignalDatabase.threads.getOrCreateThreadIdFor(recipient, distributionType),
    storyType: StoryType = StoryType.NONE,
    parentStoryId: ParentStoryId? = null,
    isStoryReaction: Boolean = false,
    giftBadge: GiftBadge? = null,
    secure: Boolean = true
  ): Long {
    val message = OutgoingMediaMessage(
      recipient = recipient,
      body = body,
      timestamp = sentTimeMillis,
      subscriptionId = subscriptionId,
      expiresIn = expiresIn,
      viewOnce = viewOnce,
      distributionType = distributionType,
      storyType = storyType,
      parentStoryId = parentStoryId,
      isStoryReaction = isStoryReaction,
      giftBadge = giftBadge,
      isSecure = secure
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
    return SignalDatabase.mms.insertMessageOutbox(message, threadId, false, GroupReceiptTable.STATUS_UNKNOWN, null)
  }

  fun insert(
    message: IncomingMediaMessage,
    threadId: Long
  ): Optional<MessageTable.InsertResult> {
    return SignalDatabase.mms.insertSecureDecryptedMessageInbox(message, threadId)
  }
}

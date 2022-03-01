package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.android.mms.pdu_alt.PduHeaders
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Helper methods for inserting an MMS message into the MMS table.
 */
object TestMms {

  fun insert(
    db: SQLiteDatabase,
    recipient: Recipient = Recipient.UNKNOWN,
    body: String = "body",
    sentTimeMillis: Long = System.currentTimeMillis(),
    receivedTimestampMillis: Long = System.currentTimeMillis(),
    subscriptionId: Int = -1,
    expiresIn: Long = 0,
    viewOnce: Boolean = false,
    distributionType: Int = ThreadDatabase.DistributionTypes.DEFAULT,
    type: Long = MmsSmsColumns.Types.BASE_INBOX_TYPE,
    unread: Boolean = false,
    viewed: Boolean = false,
    threadId: Long = 1,
    storyType: StoryType = StoryType.NONE
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
      null,
      emptyList(),
      emptyList(),
      emptyList(),
      emptySet(),
      emptySet()
    )

    return insert(
      db = db,
      message = message,
      body = body,
      type = type,
      unread = unread,
      viewed = viewed,
      threadId = threadId,
      receivedTimestampMillis = receivedTimestampMillis
    )
  }

  fun insert(
    db: SQLiteDatabase,
    message: OutgoingMediaMessage,
    body: String = message.body,
    type: Long = MmsSmsColumns.Types.BASE_INBOX_TYPE,
    unread: Boolean = false,
    viewed: Boolean = false,
    threadId: Long = 1,
    receivedTimestampMillis: Long = System.currentTimeMillis(),
  ): Long {
    val contentValues = ContentValues().apply {
      put(MmsDatabase.DATE_SENT, message.sentTimeMillis)
      put(MmsDatabase.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ)

      put(MmsDatabase.MESSAGE_BOX, type)
      put(MmsSmsColumns.THREAD_ID, threadId)
      put(MmsSmsColumns.READ, if (unread) 0 else 1)
      put(MmsDatabase.DATE_RECEIVED, receivedTimestampMillis)
      put(MmsSmsColumns.SUBSCRIPTION_ID, message.subscriptionId)
      put(MmsSmsColumns.EXPIRES_IN, message.expiresIn)
      put(MmsDatabase.VIEW_ONCE, message.isViewOnce)
      put(MmsSmsColumns.RECIPIENT_ID, message.recipient.id.serialize())
      put(MmsSmsColumns.DELIVERY_RECEIPT_COUNT, 0)
      put(MmsSmsColumns.RECEIPT_TIMESTAMP, 0)
      put(MmsSmsColumns.VIEWED_RECEIPT_COUNT, if (viewed) 1 else 0)
      put(MmsDatabase.STORY_TYPE, message.storyType.code)

      put(MmsSmsColumns.BODY, body)
      put(MmsDatabase.PART_COUNT, 0)
      put(MmsDatabase.MENTIONS_SELF, 0)
    }

    return db.insert(MmsDatabase.TABLE_NAME, null, contentValues)
  }
}

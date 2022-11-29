package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.android.mms.pdu_alt.PduHeaders
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Helper methods for inserting an MMS message into the MMS table.
 */
object TestMms {

  fun insert(
    db: SQLiteDatabase,
    recipient: Recipient = Recipient.UNKNOWN,
    recipientId: RecipientId = Recipient.UNKNOWN.id,
    body: String = "body",
    sentTimeMillis: Long = System.currentTimeMillis(),
    receivedTimestampMillis: Long = System.currentTimeMillis(),
    subscriptionId: Int = -1,
    expiresIn: Long = 0,
    viewOnce: Boolean = false,
    distributionType: Int = ThreadTable.DistributionTypes.DEFAULT,
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
      false,
      null,
      emptyList(),
      emptyList(),
      emptyList(),
      emptySet(),
      emptySet(),
      null
    )

    return insert(
      db = db,
      message = message,
      recipientId = recipientId,
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
    recipientId: RecipientId = message.recipient.id,
    body: String = message.body,
    type: Long = MmsSmsColumns.Types.BASE_INBOX_TYPE,
    unread: Boolean = false,
    viewed: Boolean = false,
    threadId: Long = 1,
    receivedTimestampMillis: Long = System.currentTimeMillis(),
  ): Long {
    val contentValues = ContentValues().apply {
      put(MmsTable.DATE_SENT, message.sentTimeMillis)
      put(MmsTable.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ)

      put(MmsTable.MESSAGE_BOX, type)
      put(MmsSmsColumns.THREAD_ID, threadId)
      put(MmsSmsColumns.READ, if (unread) 0 else 1)
      put(MmsTable.DATE_RECEIVED, receivedTimestampMillis)
      put(MmsSmsColumns.SUBSCRIPTION_ID, message.subscriptionId)
      put(MmsSmsColumns.EXPIRES_IN, message.expiresIn)
      put(MmsTable.VIEW_ONCE, message.isViewOnce)
      put(MmsSmsColumns.RECIPIENT_ID, recipientId.serialize())
      put(MmsSmsColumns.DELIVERY_RECEIPT_COUNT, 0)
      put(MmsSmsColumns.RECEIPT_TIMESTAMP, 0)
      put(MmsSmsColumns.VIEWED_RECEIPT_COUNT, if (viewed) 1 else 0)
      put(MmsTable.STORY_TYPE, message.storyType.code)

      put(MmsSmsColumns.BODY, body)
      put(MmsTable.PART_COUNT, 0)
      put(MmsTable.MENTIONS_SELF, 0)
    }

    return db.insert(MmsTable.TABLE_NAME, null, contentValues)
  }

  fun markAsRemoteDelete(db: SQLiteDatabase, messageId: Long) {
    val values = ContentValues()
    values.put(MmsSmsColumns.REMOTE_DELETED, 1)
    values.putNull(MmsSmsColumns.BODY)
    values.putNull(MmsTable.QUOTE_BODY)
    values.putNull(MmsTable.QUOTE_AUTHOR)
    values.putNull(MmsTable.QUOTE_ATTACHMENT)
    values.put(MmsTable.QUOTE_TYPE, -1)
    values.putNull(MmsTable.QUOTE_ID)
    values.putNull(MmsTable.LINK_PREVIEWS)
    values.putNull(MmsTable.SHARED_CONTACTS)
    db.update(MmsTable.TABLE_NAME, values, DatabaseTable.ID_WHERE, arrayOf(messageId.toString()))
  }
}

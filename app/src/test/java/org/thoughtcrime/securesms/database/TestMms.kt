package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.android.mms.pdu_alt.PduHeaders
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Helper methods for inserting an MMS message into the MMS table.
 */
object TestMms {

  fun insertMmsMessage(
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
    threadId: Long = 1
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
      null,
      emptyList(),
      emptyList(),
      emptyList(),
      emptySet(),
      emptySet()
    )

    return insertMmsMessage(
      db = db,
      message = message,
      body = body,
      type = type,
      unread = unread,
      threadId = threadId,
      receivedTimestampMillis = receivedTimestampMillis
    )
  }

  fun insertMmsMessage(
    db: SQLiteDatabase,
    message: OutgoingMediaMessage,
    body: String = message.body,
    type: Long = MmsSmsColumns.Types.BASE_INBOX_TYPE,
    unread: Boolean = false,
    threadId: Long = 1,
    receivedTimestampMillis: Long = System.currentTimeMillis(),
  ): Long {
    val contentValues = ContentValues()
    contentValues.put(MmsDatabase.DATE_SENT, message.sentTimeMillis)
    contentValues.put(MmsDatabase.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ)

    contentValues.put(MmsDatabase.MESSAGE_BOX, type)
    contentValues.put(MmsSmsColumns.THREAD_ID, threadId)
    contentValues.put(MmsSmsColumns.READ, if (unread) 0 else 1)
    contentValues.put(MmsDatabase.DATE_RECEIVED, receivedTimestampMillis)
    contentValues.put(MmsSmsColumns.SUBSCRIPTION_ID, message.subscriptionId)
    contentValues.put(MmsSmsColumns.EXPIRES_IN, message.expiresIn)
    contentValues.put(MmsDatabase.VIEW_ONCE, message.isViewOnce)
    contentValues.put(MmsSmsColumns.RECIPIENT_ID, message.recipient.id.serialize())
    contentValues.put(MmsSmsColumns.DELIVERY_RECEIPT_COUNT, 0)
    contentValues.put(MmsSmsColumns.RECEIPT_TIMESTAMP, 0)

    contentValues.put(MmsSmsColumns.BODY, body)
    contentValues.put(MmsDatabase.PART_COUNT, 0)
    contentValues.put(MmsDatabase.MENTIONS_SELF, 0)

    return db.insert(MmsDatabase.TABLE_NAME, null, contentValues)
  }
}

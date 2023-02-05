package org.thoughtcrime.securesms.database

import android.content.ContentValues
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import java.util.Optional
import java.util.UUID
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase

/**
 * Helper methods for inserting SMS messages into the SMS table.
 */
object TestSms {

  fun insert(
    db: AndroidSQLiteDatabase,
    sender: RecipientId = RecipientId.from(1),
    senderDeviceId: Int = 1,
    sentTimestampMillis: Long = System.currentTimeMillis(),
    serverTimestampMillis: Long = System.currentTimeMillis(),
    receivedTimestampMillis: Long = System.currentTimeMillis(),
    encodedBody: String = "encodedBody",
    groupId: Optional<GroupId> = Optional.empty(),
    expiresInMillis: Long = 0,
    unidentified: Boolean = false,
    serverGuid: String = UUID.randomUUID().toString(),
    type: Long = MessageTypes.BASE_INBOX_TYPE,
    unread: Boolean = false,
    threadId: Long = 1
  ): Long {
    val message = IncomingTextMessage(
      sender,
      senderDeviceId,
      sentTimestampMillis,
      serverTimestampMillis,
      receivedTimestampMillis,
      encodedBody,
      groupId,
      expiresInMillis,
      unidentified,
      serverGuid
    )

    return insert(
      db = db,
      message = message,
      type = type,
      unread = unread,
      threadId = threadId
    )
  }

  fun insert(
    db: AndroidSQLiteDatabase,
    message: IncomingTextMessage,
    type: Long = MessageTypes.BASE_INBOX_TYPE,
    unread: Boolean = false,
    threadId: Long = 1
  ): Long {
    val values = ContentValues().apply {
      put(MessageTable.RECIPIENT_ID, message.sender.serialize())
      put(MessageTable.RECIPIENT_DEVICE_ID, message.senderDeviceId)
      put(MessageTable.DATE_RECEIVED, message.receivedTimestampMillis)
      put(MessageTable.DATE_SENT, message.sentTimestampMillis)
      put(MessageTable.DATE_SERVER, message.serverTimestampMillis)
      put(MessageTable.READ, if (unread) 0 else 1)
      put(MessageTable.SMS_SUBSCRIPTION_ID, message.subscriptionId)
      put(MessageTable.EXPIRES_IN, message.expiresIn)
      put(MessageTable.UNIDENTIFIED, message.isUnidentified)
      put(MessageTable.BODY, message.messageBody)
      put(MessageTable.TYPE, type)
      put(MessageTable.THREAD_ID, threadId)
      put(MessageTable.SERVER_GUID, message.serverGuid)
    }

    return db.insert(MessageTable.TABLE_NAME, null, values)
  }
}

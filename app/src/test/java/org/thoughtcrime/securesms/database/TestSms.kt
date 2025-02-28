package org.thoughtcrime.securesms.database

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Optional
import java.util.UUID

/**
 * Helper methods for inserting SMS messages into the SMS table.
 */
object TestSms {

  private var startSentTimestamp = System.currentTimeMillis()

  fun insert(
    db: SupportSQLiteDatabase,
    sender: RecipientId = RecipientId.from(1),
    senderDeviceId: Int = 1,
    sentTimestampMillis: Long = startSentTimestamp++,
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
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = sender,
      sentTimeMillis = sentTimestampMillis,
      serverTimeMillis = serverTimestampMillis,
      receivedTimeMillis = receivedTimestampMillis,
      body = encodedBody,
      groupId = groupId.orNull(),
      expiresIn = expiresInMillis,
      isUnidentified = unidentified,
      serverGuid = serverGuid
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
    db: SupportSQLiteDatabase,
    message: IncomingMessage,
    type: Long = MessageTypes.BASE_INBOX_TYPE,
    unread: Boolean = false,
    threadId: Long = 1
  ): Long {
    val values = ContentValues().apply {
      put(MessageTable.FROM_RECIPIENT_ID, message.from.serialize())
      put(MessageTable.TO_RECIPIENT_ID, message.from.serialize())
      put(MessageTable.DATE_RECEIVED, message.receivedTimeMillis)
      put(MessageTable.DATE_SENT, message.sentTimeMillis)
      put(MessageTable.DATE_SERVER, message.serverTimeMillis)
      put(MessageTable.READ, if (unread) 0 else 1)
      put(MessageTable.SMS_SUBSCRIPTION_ID, message.subscriptionId)
      put(MessageTable.EXPIRES_IN, message.expiresIn)
      put(MessageTable.UNIDENTIFIED, message.isUnidentified)
      put(MessageTable.BODY, message.body)
      put(MessageTable.TYPE, type)
      put(MessageTable.THREAD_ID, threadId)
      put(MessageTable.SERVER_GUID, message.serverGuid)
    }

    return db.insert(MessageTable.TABLE_NAME, 0, values)
  }
}

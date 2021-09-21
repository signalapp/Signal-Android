package org.thoughtcrime.securesms.database

import android.content.ContentValues
import android.text.TextUtils
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import org.whispersystems.libsignal.util.guava.Optional
import java.util.UUID
import android.database.sqlite.SQLiteDatabase as AndroidSQLiteDatabase

/**
 * Helper methods for inserting SMS messages into the SMS table.
 */
object TestSms {

  fun insertSmsMessage(
    db: AndroidSQLiteDatabase,
    sender: RecipientId = RecipientId.from(1),
    senderDeviceId: Int = 1,
    sentTimestampMillis: Long = System.currentTimeMillis(),
    serverTimestampMillis: Long = System.currentTimeMillis(),
    receivedTimestampMillis: Long = System.currentTimeMillis(),
    encodedBody: String = "encodedBody",
    groupId: Optional<GroupId> = Optional.absent(),
    expiresInMillis: Long = 0,
    unidentified: Boolean = false,
    serverGuid: String = UUID.randomUUID().toString(),
    type: Long = MmsSmsColumns.Types.BASE_INBOX_TYPE,
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

    return insertSmsMessage(
      db = db,
      message = message,
      type = type,
      unread = unread,
      threadId = threadId
    )
  }

  fun insertSmsMessage(
    db: AndroidSQLiteDatabase,
    message: IncomingTextMessage,
    type: Long = MmsSmsColumns.Types.BASE_INBOX_TYPE,
    unread: Boolean = false,
    threadId: Long = 1
  ): Long {
    val values = ContentValues()
    values.put(MmsSmsColumns.RECIPIENT_ID, message.sender.serialize())
    values.put(MmsSmsColumns.ADDRESS_DEVICE_ID, message.senderDeviceId)
    values.put(SmsDatabase.DATE_RECEIVED, message.receivedTimestampMillis)
    values.put(SmsDatabase.DATE_SENT, message.sentTimestampMillis)
    values.put(MmsSmsColumns.DATE_SERVER, message.serverTimestampMillis)
    values.put(SmsDatabase.PROTOCOL, message.protocol)
    values.put(MmsSmsColumns.READ, if (unread) 0 else 1)
    values.put(MmsSmsColumns.SUBSCRIPTION_ID, message.subscriptionId)
    values.put(MmsSmsColumns.EXPIRES_IN, message.expiresIn)
    values.put(MmsSmsColumns.UNIDENTIFIED, message.isUnidentified)

    if (!TextUtils.isEmpty(message.pseudoSubject)) values.put(SmsDatabase.SUBJECT, message.pseudoSubject)

    values.put(SmsDatabase.REPLY_PATH_PRESENT, message.isReplyPathPresent)
    values.put(SmsDatabase.SERVICE_CENTER, message.serviceCenterAddress)
    values.put(MmsSmsColumns.BODY, message.messageBody)
    values.put(SmsDatabase.TYPE, type)
    values.put(MmsSmsColumns.THREAD_ID, threadId)
    values.put(MmsSmsColumns.SERVER_GUID, message.serverGuid)

    return db.insert(SmsDatabase.TABLE_NAME, null, values)
  }
}

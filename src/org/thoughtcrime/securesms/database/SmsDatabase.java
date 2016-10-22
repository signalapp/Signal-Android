/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchList;
import org.thoughtcrime.securesms.database.model.DisplayRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.jobs.TrimThreadJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingGroupMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.thoughtcrime.securesms.util.Util.canonicalizeNumber;

/**
 * Database for storage of SMS messages.
 *
 * @author Moxie Marlinspike
 */

public class SmsDatabase extends MessagingDatabase {

  private static final String TAG = SmsDatabase.class.getSimpleName();

  public  static final String TABLE_NAME         = "sms";
  public  static final String PERSON             = "person";
          static final String DATE_RECEIVED      = "date";
          static final String DATE_SENT          = "date_sent";
  public  static final String PROTOCOL           = "protocol";
  public  static final String STATUS             = "status";
  public  static final String TYPE               = "type";
  public  static final String REPLY_PATH_PRESENT = "reply_path_present";
  public  static final String SUBJECT            = "subject";
  public  static final String SERVICE_CENTER     = "service_center";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " integer PRIMARY KEY, "                +
    THREAD_ID + " INTEGER, " + ADDRESS + " TEXT, " + ADDRESS_DEVICE_ID + " INTEGER DEFAULT 1, " + PERSON + " INTEGER, " +
    DATE_RECEIVED  + " INTEGER, " + DATE_SENT + " INTEGER, " + PROTOCOL + " INTEGER, " + READ + " INTEGER DEFAULT 0, " +
    STATUS + " INTEGER DEFAULT -1," + TYPE + " INTEGER, " + REPLY_PATH_PRESENT + " INTEGER, " +
    RECEIPT_COUNT + " INTEGER DEFAULT 0," + SUBJECT + " TEXT, " + BODY + " TEXT, " +
    MISMATCHED_IDENTITIES + " TEXT DEFAULT NULL, " + SERVICE_CENTER + " TEXT, " + SUBSCRIPTION_ID + " INTEGER DEFAULT -1, " +
    EXPIRES_IN + " INTEGER DEFAULT 0, " + EXPIRE_STARTED + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS sms_thread_id_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS sms_read_index ON " + TABLE_NAME + " (" + READ + ");",
    "CREATE INDEX IF NOT EXISTS sms_read_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS sms_type_index ON " + TABLE_NAME + " (" + TYPE + ");",
    "CREATE INDEX IF NOT EXISTS sms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ");",
    "CREATE INDEX IF NOT EXISTS sms_thread_date_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");"
  };

  private static final String[] MESSAGE_PROJECTION = new String[] {
      ID, THREAD_ID, ADDRESS, ADDRESS_DEVICE_ID, PERSON,
      DATE_RECEIVED + " AS " + NORMALIZED_DATE_RECEIVED,
      DATE_SENT + " AS " + NORMALIZED_DATE_SENT,
      PROTOCOL, READ, STATUS, TYPE,
      REPLY_PATH_PRESENT, SUBJECT, BODY, SERVICE_CENTER, RECEIPT_COUNT,
      MISMATCHED_IDENTITIES, SUBSCRIPTION_ID, EXPIRES_IN, EXPIRE_STARTED
  };

  private static final EarlyReceiptCache earlyReceiptCache = new EarlyReceiptCache();
  private final JobManager jobManager;

  public SmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
    this.jobManager = ApplicationContext.getInstance(context).getJobManager();
  }

  protected String getTableName() {
    return TABLE_NAME;
  }

  private void updateTypeBitmask(long id, long maskOff, long maskOn) {
    Log.w("MessageDatabase", "Updating ID: " + id + " to base type: " + maskOn);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME +
               " SET " + TYPE + " = (" + TYPE + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
               " WHERE " + ID + " = ?", new String[] {id+""});

    long threadId = getThreadIdForMessage(id);

    DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
  }

  public long getThreadIdForMessage(long id) {
    String sql        = "SELECT " + THREAD_ID + " FROM " + TABLE_NAME + " WHERE " + ID + " = ?";
    String[] sqlArgs  = new String[] {id+""};
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    Cursor cursor = null;

    try {
      cursor = db.rawQuery(sql, sqlArgs);
      if (cursor != null && cursor.moveToFirst())
        return cursor.getLong(0);
      else
        return -1;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public int getMessageCount() {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, new String[] {"COUNT(*)"}, null, null, null, null, null);

      if (cursor != null && cursor.moveToFirst()) return cursor.getInt(0);
      else                                        return 0;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public int getMessageCountForThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, new String[] {"COUNT(*)"}, THREAD_ID + " = ?",
                        new String[] {threadId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst())
        return cursor.getInt(0);
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return 0;
  }

  public void markAsEndSession(long id) {
    updateTypeBitmask(id, Types.KEY_EXCHANGE_MASK, Types.END_SESSION_BIT);
  }

  public void markAsPreKeyBundle(long id) {
    updateTypeBitmask(id, Types.KEY_EXCHANGE_MASK, Types.KEY_EXCHANGE_BIT | Types.KEY_EXCHANGE_BUNDLE_BIT);
  }

  public void markAsInvalidVersionKeyExchange(long id) {
    updateTypeBitmask(id, 0, Types.KEY_EXCHANGE_INVALID_VERSION_BIT);
  }

  public void markAsSecure(long id) {
    updateTypeBitmask(id, 0, Types.SECURE_MESSAGE_BIT);
  }

  public void markAsInsecure(long id) {
    updateTypeBitmask(id, Types.SECURE_MESSAGE_BIT, 0);
  }

  public void markAsPush(long id) {
    updateTypeBitmask(id, 0, Types.PUSH_MESSAGE_BIT);
  }

  public void markAsForcedSms(long id) {
    updateTypeBitmask(id, Types.PUSH_MESSAGE_BIT, Types.MESSAGE_FORCE_SMS_BIT);
  }

  public void markAsDecryptFailed(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_FAILED_BIT);
  }

  public void markAsDecryptDuplicate(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_DUPLICATE_BIT);
  }

  public void markAsNoSession(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_NO_SESSION_BIT);
  }

  public void markAsLegacyVersion(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_LEGACY_BIT);
  }

  public void markAsOutbox(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_OUTBOX_TYPE);
  }

  public void markAsPendingInsecureSmsFallback(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_PENDING_INSECURE_SMS_FALLBACK);
  }

  public void markAsSending(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_SENDING_TYPE);
  }

  public void markAsSent(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_SENT_TYPE);
  }

  public void markExpireStarted(long id) {
    markExpireStarted(id, System.currentTimeMillis());
  }

  public void markExpireStarted(long id, long startedAtTimestamp) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(EXPIRE_STARTED, startedAtTimestamp);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(id)});

    long threadId = getThreadIdForMessage(id);

    DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
  }

  public void markStatus(long id, int status) {
    Log.w("MessageDatabase", "Updating ID: " + id + " to status: " + status);
    ContentValues contentValues = new ContentValues();
    contentValues.put(STATUS, status);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {id+""});

    long threadId = getThreadIdForMessage(id);
    DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
  }

  public void markAsSentFailed(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_SENT_FAILED_TYPE);
  }

  public void incrementDeliveryReceiptCount(SyncMessageId messageId) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    Cursor         cursor       = null;
    boolean        foundMessage = false;

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, ADDRESS, TYPE},
                              DATE_SENT + " = ?", new String[] {String.valueOf(messageId.getTimetamp())},
                              null, null, null, null);

      while (cursor.moveToNext()) {
        if (Types.isOutgoingMessageType(cursor.getLong(cursor.getColumnIndexOrThrow(TYPE)))) {
          try {
            String theirAddress = canonicalizeNumber(context, messageId.getAddress());
            String ourAddress   = canonicalizeNumber(context, cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS)));

            if (ourAddress.equals(theirAddress)) {
              long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));

              database.execSQL("UPDATE " + TABLE_NAME +
                               " SET " + RECEIPT_COUNT + " = " + RECEIPT_COUNT + " + 1 WHERE " +
                               ID + " = ?",
                               new String[] {String.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(ID)))});

              DatabaseFactory.getThreadDatabase(context).update(threadId, false);
              notifyConversationListeners(threadId);
              foundMessage = true;
            }
          } catch (InvalidNumberException e) {
            Log.w(TAG, e);
          }
        }
      }

      if (!foundMessage) {
        try {
          earlyReceiptCache.increment(messageId.getTimetamp(), canonicalizeNumber(context, messageId.getAddress()));
        } catch (InvalidNumberException e) {
          Log.w(TAG, e);
        }
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public List<MarkedMessageInfo> setMessagesRead(long threadId) {
    SQLiteDatabase          database  = databaseHelper.getWritableDatabase();
    String                  where     = THREAD_ID + " = ? AND " + READ + " = 0";
    String[]                selection = new String[]{String.valueOf(threadId)};
    List<MarkedMessageInfo> results   = new LinkedList<>();
    Cursor                  cursor    = null;

    database.beginTransaction();
    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, ADDRESS, DATE_SENT, TYPE, EXPIRES_IN, EXPIRE_STARTED}, where, selection, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        if (Types.isSecureType(cursor.getLong(3))) {
          SyncMessageId  syncMessageId  = new SyncMessageId(cursor.getString(1), cursor.getLong(2));
          ExpirationInfo expirationInfo = new ExpirationInfo(cursor.getLong(0), cursor.getLong(4), cursor.getLong(5), false);

          results.add(new MarkedMessageInfo(syncMessageId, expirationInfo));
        }
      }

      ContentValues contentValues = new ContentValues();
      contentValues.put(READ, 1);

      database.update(TABLE_NAME, contentValues, where, selection);
      database.setTransactionSuccessful();
    } finally {
      if (cursor != null) cursor.close();
      database.endTransaction();
    }

    return results;
  }

  public List<Pair<Long, Long>> setTimestampRead(SyncMessageId messageId, long expireStarted) {
    SQLiteDatabase         database = databaseHelper.getWritableDatabase();
    List<Pair<Long, Long>> expiring = new LinkedList<>();
    Cursor                 cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, ADDRESS, TYPE, EXPIRES_IN},
                              DATE_SENT + " = ?", new String[] {String.valueOf(messageId.getTimetamp())},
                              null, null, null, null);

      while (cursor.moveToNext()) {
        try {
          String theirAddress = canonicalizeNumber(context, messageId.getAddress());
          String ourAddress   = canonicalizeNumber(context, cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS)));

          if (ourAddress.equals(theirAddress)) {
            long id        = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
            long threadId  = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
            long expiresIn = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN));

            ContentValues contentValues = new ContentValues();
            contentValues.put(READ, 1);

            if (expiresIn > 0) {
              contentValues.put(EXPIRE_STARTED, expireStarted);
              expiring.add(new Pair<>(id, expiresIn));
            }

            database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {cursor.getLong(cursor.getColumnIndexOrThrow(ID)) + ""});

            DatabaseFactory.getThreadDatabase(context).updateReadState(threadId);
            notifyConversationListeners(threadId);
          }
        } catch (InvalidNumberException e) {
          Log.w(TAG, e);
        }
      }
    } finally {
      if (cursor != null) cursor.close();
    }

    return expiring;
  }

  public void setAllMessagesRead() {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, 1);

    database.update(TABLE_NAME, contentValues, null, null);
  }

  protected Pair<Long, Long> updateMessageBodyAndType(long messageId, String body, long maskOff, long maskOn) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + BODY + " = ?, " +
                   TYPE + " = (" + TYPE + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + ") " +
                   "WHERE " + ID + " = ?",
               new String[] {body, messageId + ""});

    long threadId = getThreadIdForMessage(messageId);

    DatabaseFactory.getThreadDatabase(context).update(threadId, true);
    notifyConversationListeners(threadId);
    notifyConversationListListeners();

    return new Pair<>(messageId, threadId);
  }

  public Pair<Long, Long> copyMessageInbox(long messageId) {
    Reader           reader = readerFor(getMessage(messageId));
    SmsMessageRecord record = reader.getNext();

    ContentValues contentValues = new ContentValues();
    contentValues.put(TYPE, (record.getType() & ~Types.BASE_TYPE_MASK) | Types.BASE_INBOX_TYPE);
    contentValues.put(ADDRESS, record.getIndividualRecipient().getNumber());
    contentValues.put(ADDRESS_DEVICE_ID, record.getRecipientDeviceId());
    contentValues.put(DATE_RECEIVED, System.currentTimeMillis());
    contentValues.put(DATE_SENT, record.getDateSent());
    contentValues.put(PROTOCOL, 31337);
    contentValues.put(READ, 0);
    contentValues.put(BODY, record.getBody().getBody());
    contentValues.put(THREAD_ID, record.getThreadId());
    contentValues.put(EXPIRES_IN, record.getExpiresIn());

    SQLiteDatabase db           = databaseHelper.getWritableDatabase();
    long           newMessageId = db.insert(TABLE_NAME, null, contentValues);

    DatabaseFactory.getThreadDatabase(context).update(record.getThreadId(), true);
    notifyConversationListeners(record.getThreadId());

    jobManager.add(new TrimThreadJob(context, record.getThreadId()));
    reader.close();
    
    return new Pair<>(newMessageId, record.getThreadId());
  }

  public @NonNull Pair<Long, Long> insertReceivedCall(@NonNull String number) {
    return insertCallLog(number, Types.INCOMING_CALL_TYPE, false);
  }

  public @NonNull Pair<Long, Long> insertOutgoingCall(@NonNull String number) {
    return insertCallLog(number, Types.OUTGOING_CALL_TYPE, false);
  }

  public @NonNull Pair<Long, Long> insertMissedCall(@NonNull String number) {
    return insertCallLog(number, Types.MISSED_CALL_TYPE, true);
  }

  private @NonNull Pair<Long, Long> insertCallLog(@NonNull String number, long type, boolean unread) {
    Recipients recipients = RecipientFactory.getRecipientsFromString(context, number, true);
    long       threadId   = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

    ContentValues values = new ContentValues(6);
    values.put(ADDRESS, number);
    values.put(ADDRESS_DEVICE_ID,  1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, unread ? 0 : 1);
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    long messageId    = db.insert(TABLE_NAME, null, values);

    DatabaseFactory.getThreadDatabase(context).update(threadId, true);
    notifyConversationListeners(threadId);
    jobManager.add(new TrimThreadJob(context, threadId));

    if (unread) {
      DatabaseFactory.getThreadDatabase(context).setUnread(threadId);
    }

    return new Pair<>(messageId, threadId);
  }

  protected Pair<Long, Long> insertMessageInbox(IncomingTextMessage message, long type) {
    if (message.isJoined()) {
      type = (type & (Types.TOTAL_MASK - Types.BASE_TYPE_MASK)) | Types.JOINED_TYPE;
    } else if (message.isPreKeyBundle()) {
      type |= Types.KEY_EXCHANGE_BIT | Types.KEY_EXCHANGE_BUNDLE_BIT;
    } else if (message.isSecureMessage()) {
      type |= Types.SECURE_MESSAGE_BIT;
    } else if (message.isGroup()) {
      type |= Types.SECURE_MESSAGE_BIT;
      if      (((IncomingGroupMessage)message).isUpdate()) type |= Types.GROUP_UPDATE_BIT;
      else if (((IncomingGroupMessage)message).isQuit())   type |= Types.GROUP_QUIT_BIT;
    } else if (message.isEndSession()) {
      type |= Types.SECURE_MESSAGE_BIT;
      type |= Types.END_SESSION_BIT;
    }

    if (message.isPush())           type |= Types.PUSH_MESSAGE_BIT;
    if (message.isIdentityUpdate()) type |= Types.KEY_EXCHANGE_IDENTITY_UPDATE_BIT;

    Recipients recipients;

    if (message.getSender() != null) {
      recipients = RecipientFactory.getRecipientsFromString(context, message.getSender(), true);
    } else {
      Log.w(TAG, "Sender is null, returning unknown recipient");
      recipients = RecipientFactory.getRecipientsFor(context, Recipient.getUnknownRecipient(), false);
    }

    Recipients groupRecipients;

    if (message.getGroupId() == null) {
      groupRecipients = null;
    } else {
      groupRecipients = RecipientFactory.getRecipientsFromString(context, message.getGroupId(), true);
    }

    boolean    unread     = (org.thoughtcrime.securesms.util.Util.isDefaultSmsProvider(context) ||
                            message.isSecureMessage() || message.isGroup() || message.isPreKeyBundle()) &&
                            !message.isIdentityUpdate();

    long       threadId;

    if (groupRecipients == null) threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    else                         threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipients);

    ContentValues values = new ContentValues(6);
    values.put(ADDRESS, message.getSender());
    values.put(ADDRESS_DEVICE_ID,  message.getSenderDeviceId());
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, message.getSentTimestampMillis());
    values.put(PROTOCOL, message.getProtocol());
    values.put(READ, unread ? 0 : 1);
    values.put(SUBSCRIPTION_ID, message.getSubscriptionId());
    values.put(EXPIRES_IN, message.getExpiresIn());

    if (!TextUtils.isEmpty(message.getPseudoSubject()))
      values.put(SUBJECT, message.getPseudoSubject());

    values.put(REPLY_PATH_PRESENT, message.isReplyPathPresent());
    values.put(SERVICE_CENTER, message.getServiceCenterAddress());
    values.put(BODY, message.getMessageBody());
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    long messageId    = db.insert(TABLE_NAME, null, values);

    if (unread) {
      DatabaseFactory.getThreadDatabase(context).setUnread(threadId);
    }

    if (!message.isIdentityUpdate()) {
      DatabaseFactory.getThreadDatabase(context).update(threadId, true);
    }

    notifyConversationListeners(threadId);
    jobManager.add(new TrimThreadJob(context, threadId));

    return new Pair<>(messageId, threadId);
  }

  public Pair<Long, Long> insertMessageInbox(IncomingTextMessage message) {
    return insertMessageInbox(message, Types.BASE_INBOX_TYPE);
  }

  protected long insertMessageOutbox(long threadId, OutgoingTextMessage message,
                                     long type, boolean forceSms, long date)
  {
    if      (message.isKeyExchange())   type |= Types.KEY_EXCHANGE_BIT;
    else if (message.isSecureMessage()) type |= Types.SECURE_MESSAGE_BIT;
    else if (message.isEndSession())    type |= Types.END_SESSION_BIT;
    if      (forceSms)                  type |= Types.MESSAGE_FORCE_SMS_BIT;

    String address = message.getRecipients().getPrimaryRecipient().getNumber();

    ContentValues contentValues = new ContentValues(6);
    contentValues.put(ADDRESS, PhoneNumberUtils.formatNumber(address));
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(BODY, message.getMessageBody());
    contentValues.put(DATE_RECEIVED, System.currentTimeMillis());
    contentValues.put(DATE_SENT, date);
    contentValues.put(READ, 1);
    contentValues.put(TYPE, type);
    contentValues.put(SUBSCRIPTION_ID, message.getSubscriptionId());
    contentValues.put(EXPIRES_IN, message.getExpiresIn());

    try {
      contentValues.put(RECEIPT_COUNT, earlyReceiptCache.remove(date, canonicalizeNumber(context, address)));
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
    }

    SQLiteDatabase db        = databaseHelper.getWritableDatabase();
    long           messageId = db.insert(TABLE_NAME, ADDRESS, contentValues);

    DatabaseFactory.getThreadDatabase(context).update(threadId, true);
    notifyConversationListeners(threadId);
    jobManager.add(new TrimThreadJob(context, threadId));

    return messageId;
  }

  Cursor getMessages(int skip, int limit) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, null, null, null, null, ID, skip + "," + limit);
  }

  Cursor getOutgoingMessages() {
    String outgoingSelection = TYPE + " & "  + Types.BASE_TYPE_MASK + " = " + Types.BASE_OUTBOX_TYPE;
    SQLiteDatabase db        = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, outgoingSelection, null, null, null, null);
  }

  public Cursor getDecryptInProgressMessages() {
    String where       = TYPE + " & " + (Types.ENCRYPTION_ASYMMETRIC_BIT) + " != 0";
    SQLiteDatabase db  = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, where, null, null, null, null);
  }

  public Cursor getEncryptedRogueMessages(Recipient recipient) {
    String selection  = TYPE + " & " + Types.ENCRYPTION_REMOTE_NO_SESSION_BIT + " != 0" +
                        " AND PHONE_NUMBERS_EQUAL(" + ADDRESS + ", ?)";
    String[] args     = {recipient.getNumber()};
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, selection, args, null, null, null);
  }

  public Cursor getExpirationStartedMessages() {
    String         where = EXPIRE_STARTED + " > 0";
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, where, null, null, null, null);
  }

  public Cursor getMessage(long messageId) {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    Cursor         cursor = db.query(TABLE_NAME, MESSAGE_PROJECTION, ID_WHERE, new String[]{messageId + ""},
                                     null, null, null);
    setNotifyConverationListeners(cursor, getThreadIdForMessage(messageId));
    return cursor;
  }

  public boolean deleteMessage(long messageId) {
    Log.w("MessageDatabase", "Deleting: " + messageId);
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    long threadId     = getThreadIdForMessage(messageId);
    db.delete(TABLE_NAME, ID_WHERE, new String[] {messageId+""});
    boolean threadDeleted = DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
    return threadDeleted;
  }

  /*package */void deleteThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, THREAD_ID + " = ?", new String[] {threadId+""});
  }

  /*package*/void deleteMessagesInThreadBeforeDate(long threadId, long date) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    String where      = THREAD_ID + " = ? AND (CASE " + TYPE;

    for (long outgoingType : Types.OUTGOING_MESSAGE_TYPES) {
      where += " WHEN " + outgoingType + " THEN " + DATE_SENT + " < " + date;
    }

    where += (" ELSE " + DATE_RECEIVED + " < " + date + " END)");

    db.delete(TABLE_NAME, where, new String[] {threadId + ""});
  }

  /*package*/ void deleteThreads(Set<Long> threadIds) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    String where      = "";

    for (long threadId : threadIds) {
      where += THREAD_ID + " = '" + threadId + "' OR ";
    }

    where = where.substring(0, where.length() - 4);

    db.delete(TABLE_NAME, where, null);
  }

  /*package */ void deleteAllThreads() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  /*package*/ SQLiteDatabase beginTransaction() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();
    return database;
  }

  /*package*/ void endTransaction(SQLiteDatabase database) {
    database.setTransactionSuccessful();
    database.endTransaction();
  }

  /*package*/ SQLiteStatement createInsertStatement(SQLiteDatabase database) {
    return database.compileStatement("INSERT INTO " + TABLE_NAME + " (" + ADDRESS + ", " +
                                                                      PERSON + ", " +
                                                                      DATE_SENT + ", " +
                                                                      DATE_RECEIVED  + ", " +
                                                                      PROTOCOL + ", " +
                                                                      READ + ", " +
                                                                      STATUS + ", " +
                                                                      TYPE + ", " +
                                                                      REPLY_PATH_PRESENT + ", " +
                                                                      SUBJECT + ", " +
                                                                      BODY + ", " +
                                                                      SERVICE_CENTER +
                                                                      ", " + THREAD_ID + ") " +
                                     " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
  }

  public static class Status {
    public static final int STATUS_NONE     = -1;
    public static final int STATUS_COMPLETE  = 0;
    public static final int STATUS_PENDING   = 0x20;
    public static final int STATUS_FAILED    = 0x40;
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public class Reader {

    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public SmsMessageRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public int getCount() {
      if (cursor == null) return 0;
      else                return cursor.getCount();
    }

    public SmsMessageRecord getCurrent() {
      long messageId          = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
      String address          = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
      int addressDeviceId     = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS_DEVICE_ID));
      long type               = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
      long dateReceived       = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_RECEIVED));
      long dateSent           = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_SENT));
      long threadId           = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.THREAD_ID));
      int status              = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.STATUS));
      int receiptCount        = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.RECEIPT_COUNT));
      String mismatchDocument = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.MISMATCHED_IDENTITIES));
      int subscriptionId      = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.SUBSCRIPTION_ID));
      long expiresIn          = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.EXPIRES_IN));
      long expireStarted      = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.EXPIRE_STARTED));

      List<IdentityKeyMismatch> mismatches = getMismatches(mismatchDocument);
      Recipients                recipients = getRecipientsFor(address);
      DisplayRecord.Body        body       = getBody(cursor);

      return new SmsMessageRecord(context, messageId, body, recipients,
                                  recipients.getPrimaryRecipient(),
                                  addressDeviceId,
                                  dateSent, dateReceived, receiptCount, type,
                                  threadId, status, mismatches, subscriptionId,
                                  expiresIn, expireStarted);
    }

    private Recipients getRecipientsFor(String address) {
      if (address != null) {
        Recipients recipients = RecipientFactory.getRecipientsFromString(context, address, true);

        if (recipients == null || recipients.isEmpty()) {
          return RecipientFactory.getRecipientsFor(context, Recipient.getUnknownRecipient(), true);
        }

        return recipients;
      } else {
        Log.w(TAG, "getRecipientsFor() address is null");
        return RecipientFactory.getRecipientsFor(context, Recipient.getUnknownRecipient(), true);
      }
    }

    private List<IdentityKeyMismatch> getMismatches(String document) {
      try {
        if (!TextUtils.isEmpty(document)) {
          return JsonUtils.fromJson(document, IdentityKeyMismatchList.class).getList();
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      return new LinkedList<>();
    }

    protected DisplayRecord.Body getBody(Cursor cursor) {
      long type   = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
      String body = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));

      if (Types.isSymmetricEncryption(type)) {
        return new DisplayRecord.Body(body, false);
      } else {
        return new DisplayRecord.Body(body, true);
      }
    }

    public void close() {
      cursor.close();
    }
  }
}

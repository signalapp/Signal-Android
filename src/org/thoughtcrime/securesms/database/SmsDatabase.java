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
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.model.DisplayRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingKeyExchangeMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.Trimmer;
import org.thoughtcrime.securesms.util.Util;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Database for storage of SMS messages.
 *
 * @author Moxie Marlinspike
 */

public class SmsDatabase extends Database implements MmsSmsColumns {

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
    THREAD_ID + " INTEGER, " + ADDRESS + " TEXT, " + PERSON + " INTEGER, " + DATE_RECEIVED  + " INTEGER, " +
    DATE_SENT + " INTEGER, " + PROTOCOL + " INTEGER, " + READ + " INTEGER DEFAULT 0, " +
    STATUS + " INTEGER DEFAULT -1," + TYPE + " INTEGER, " + REPLY_PATH_PRESENT + " INTEGER, " +
    SUBJECT + " TEXT, " + BODY + " TEXT, " + SERVICE_CENTER + " TEXT);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS sms_thread_id_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS sms_read_index ON " + TABLE_NAME + " (" + READ + ");",
    "CREATE INDEX IF NOT EXISTS sms_read_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS sms_type_index ON " + TABLE_NAME + " (" + TYPE + ");"
  };

  private static final String[] MESSAGE_PROJECTION = new String[] {
      ID, THREAD_ID, ADDRESS, PERSON,
      DATE_RECEIVED + " AS " + NORMALIZED_DATE_RECEIVED,
      DATE_SENT + " AS " + NORMALIZED_DATE_SENT,
      PROTOCOL, READ, STATUS, TYPE,
      REPLY_PATH_PRESENT, SUBJECT, BODY, SERVICE_CENTER
  };

  public SmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  private void updateTypeBitmask(long id, long maskOff, long maskOn) {
    Log.w("MessageDatabase", "Updating ID: " + id + " to base type: " + maskOn);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME +
               " SET " + TYPE + " = (" + TYPE + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
               " WHERE " + ID + " = ?", new String[] {id+""});

    long threadId = getThreadIdForMessage(id);

    DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
    notifyConversationListListeners();
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

  public void markAsDecryptFailed(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_FAILED_BIT);
  }

  public void markAsNoSession(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_NO_SESSION_BIT);
  }

  public void markAsDecrypting(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_BIT);
  }

  public void markAsSent(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_SENT_TYPE);
  }

  public void markStatus(long id, int status) {
    Log.w("MessageDatabase", "Updating ID: " + id + " to status: " + status);
    ContentValues contentValues = new ContentValues();
    contentValues.put(STATUS, status);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {id+""});
    notifyConversationListeners(getThreadIdForMessage(id));
  }

  public void markAsSentFailed(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_SENT_FAILED_TYPE);
  }

  public void setMessagesRead(long threadId) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, 1);

    long start = System.currentTimeMillis();
    database.update(TABLE_NAME, contentValues, THREAD_ID + " = ? AND " + READ + " = 0", new String[] {threadId+""});
    long end = System.currentTimeMillis();

    Log.w("SmsDatabase", "setMessagesRead time: " + (end - start));
  }

  protected void updateMessageBodyAndType(long messageId, String body, long maskOff, long maskOn) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + BODY + " = ?, " +
               TYPE + " = (" + TYPE + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + ") " +
               "WHERE " + ID + " = ?",
               new String[] {body, messageId+""});

    long threadId = getThreadIdForMessage(messageId);

    DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
    notifyConversationListListeners();
  }

  protected Pair<Long, Long> insertMessageInbox(IncomingTextMessage message, long type) {
    if (message.isKeyExchange()) {
      type |= Types.KEY_EXCHANGE_BIT;
      if      (((IncomingKeyExchangeMessage)message).isStale())     type |= Types.KEY_EXCHANGE_STALE_BIT;
      else if (((IncomingKeyExchangeMessage)message).isProcessed()) {Log.w("SmsDatabase", "Setting processed bit..."); type |= Types.KEY_EXCHANGE_PROCESSED_BIT;}
    } else if (message.isSecureMessage()) {
      type |= Types.SECURE_MESSAGE_BIT;
      type |= Types.ENCRYPTION_REMOTE_BIT;
    }

    Recipient recipient   = new Recipient(null, message.getSender(), null, null);
    Recipients recipients = new Recipients(recipient);
    long threadId         = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

    ContentValues values = new ContentValues(6);
    values.put(ADDRESS, message.getSender());
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, message.getSentTimestampMillis());
    values.put(PROTOCOL, message.getProtocol());
    values.put(READ, 0);

    if (!Util.isEmpty(message.getPseudoSubject()))
      values.put(SUBJECT, message.getPseudoSubject());

    values.put(REPLY_PATH_PRESENT, message.isReplyPathPresent());
    values.put(SERVICE_CENTER, message.getServiceCenterAddress());
    values.put(BODY, message.getMessageBody());
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    long messageId    = db.insert(TABLE_NAME, null, values);

    DatabaseFactory.getThreadDatabase(context).setUnread(threadId);
    DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
    Trimmer.trimThread(context, threadId);

    return new Pair<Long, Long>(messageId, threadId);
  }

  public Pair<Long, Long> insertMessageInbox(IncomingTextMessage message) {
    return insertMessageInbox(message, Types.BASE_INBOX_TYPE);
  }

  protected List<Long> insertMessageOutbox(long threadId, OutgoingTextMessage message, long type) {
    if      (message.isKeyExchange())   type |= Types.KEY_EXCHANGE_BIT;
    else if (message.isSecureMessage()) type |= Types.SECURE_MESSAGE_BIT;

    long date             = System.currentTimeMillis();
    List<Long> messageIds = new LinkedList<Long>();

    for (Recipient recipient : message.getRecipients().getRecipientsList()) {
      ContentValues contentValues = new ContentValues(6);
      contentValues.put(ADDRESS, PhoneNumberUtils.formatNumber(recipient.getNumber()));
      contentValues.put(THREAD_ID, threadId);
      contentValues.put(BODY, message.getMessageBody());
      contentValues.put(DATE_RECEIVED, date);
      contentValues.put(DATE_SENT, date);
      contentValues.put(READ, 1);
      contentValues.put(TYPE, type);

      SQLiteDatabase db = databaseHelper.getWritableDatabase();
      messageIds.add(db.insert(TABLE_NAME, ADDRESS, contentValues));

      DatabaseFactory.getThreadDatabase(context).update(threadId);
      notifyConversationListeners(threadId);
      Trimmer.trimThread(context, threadId);
    }

    return messageIds;
  }

  Cursor getOutgoingMessages() {
    String outgoingSelection = TYPE + " & "  + Types.BASE_TYPE_MASK + " = " + Types.BASE_OUTBOX_TYPE;
    SQLiteDatabase db        = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, outgoingSelection, null, null, null, null);
  }

  public Cursor getDecryptInProgressMessages() {
    String where       = TYPE + " & " + (Types.ENCRYPTION_REMOTE_BIT | Types.ENCRYPTION_ASYMMETRIC_BIT) + " != 0";
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

  public Cursor getMessage(long messageId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, ID_WHERE, new String[] {messageId+""},
                    null, null, null);
  }

  public void deleteMessage(long messageId) {
    Log.w("MessageDatabase", "Deleting: " + messageId);
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    long threadId     = getThreadIdForMessage(messageId);
    db.delete(TABLE_NAME, ID_WHERE, new String[] {messageId+""});
    DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
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

    db.delete(TABLE_NAME, where, new String[] {threadId+""});
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
    public static final int STATUS_COMPLETE = 0;
    public static final int STATUS_PENDING  = 32;
    public static final int STATUS_FAILED   = 64;
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

    public SmsMessageRecord getCurrent() {
      long messageId          = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
      String address          = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
      long type               = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
      long dateReceived       = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_RECEIVED));
      long dateSent           = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_SENT));
      long threadId           = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.THREAD_ID));
      int status              = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.STATUS));
      Recipients recipients   = getRecipientsFor(address);
      DisplayRecord.Body body = getBody(cursor);

      return  new SmsMessageRecord(context, messageId, body, recipients,
                                   recipients.getPrimaryRecipient(),
                                   dateSent, dateReceived, type,
                                   threadId, status);
    }

    private Recipients getRecipientsFor(String address) {
      try {
        return RecipientFactory.getRecipientsFromString(context, address, false);
      } catch (RecipientFormattingException e) {
        Log.w("EncryptingSmsDatabase", e);
        return new Recipients(new Recipient("Unknown", "Unknown", null,
                                            ContactPhotoFactory.getDefaultContactPhoto(context)));
      }
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

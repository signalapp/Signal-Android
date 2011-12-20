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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * Database for storage of SMS messages.
 * 
 * @author Moxie Marlinspike
 */

public class SmsDatabase extends Database {
  public  static final String TRANSPORT          = "transport_type";
	
  public  static final String TABLE_NAME         = "sms";
  public  static final String ID                 = "_id";
  public  static final String THREAD_ID          = "thread_id";
  public  static final String ADDRESS            = "address";
  public  static final String PERSON             = "person";
  public  static final String DATE               = "date";
  public  static final String PROTOCOL           = "protocol";
  public  static final String READ               = "read";
  public  static final String STATUS             = "status";
  public  static final String TYPE               = "type";
  public  static final String REPLY_PATH_PRESENT = "reply_path_present";
  public  static final String SUBJECT            = "subject";
  public  static final String BODY               = "body";
  public  static final String SERVICE_CENTER     = "service_center";
	
  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " integer PRIMARY KEY, "                + 
    THREAD_ID + " INTEGER, " + ADDRESS + " TEXT, " + PERSON + " INTEGER, " + DATE  + " INTEGER, "    + 
    PROTOCOL + " INTEGER, " + READ + " INTEGER DEFAULT 0, " + STATUS + " INTEGER DEFAULT -1,"        + 
    TYPE + " INTEGER, " + REPLY_PATH_PRESENT + " INTEGER, " + SUBJECT + " TEXT, " + BODY + " TEXT, " + 
    SERVICE_CENTER + " TEXT);";
	
  public SmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }
	
  private void updateType(long id, long type) {
    Log.w("MessageDatabase", "Updating ID: " + id + " to type: " + type);
    ContentValues contentValues = new ContentValues();
    contentValues.put(TYPE, type);
		
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {id+""});
    notifyConversationListeners(getThreadIdForMessage(id));
  }
	
  private long insertMessageReceived(SmsMessage message, String body, long type) {
    List<Recipient> recipientList = new ArrayList<Recipient>(1);			
    recipientList.add(new Recipient(null, message.getDisplayOriginatingAddress(), null));
    Recipients recipients         = new Recipients(recipientList);
		
    long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
		
    ContentValues values = new ContentValues(6);
    values.put(ADDRESS, message.getDisplayOriginatingAddress());
    values.put(DATE, new Long(System.currentTimeMillis()));
    values.put(PROTOCOL, message.getProtocolIdentifier());
    values.put(READ, Integer.valueOf(0));
		
    if (message.getPseudoSubject().length() > 0)
      values.put(SUBJECT, message.getPseudoSubject());
		
    values.put(REPLY_PATH_PRESENT, message.isReplyPathPresent() ? 1 : 0);
    values.put(SERVICE_CENTER, message.getServiceCenterAddress());
    values.put(BODY, body);
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);
		
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    long messageId    = db.insert(TABLE_NAME, null, values);
		
    DatabaseFactory.getThreadDatabase(context).setUnread(threadId);
    DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
    return messageId;
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
      cursor = db.query(TABLE_NAME, new String[] {"COUNT(*)"}, THREAD_ID + " = ?", new String[] {threadId+""}, null, null, null);
			
      if (cursor != null && cursor.moveToFirst())
	return cursor.getInt(0);
    } finally {
      if (cursor != null)
	cursor.close();
    }

    return 0;
  }
	
  public void markAsDecryptFailed(long id) {
    updateType(id, Types.FAILED_DECRYPT_TYPE);
  }
	
  public void markAsNoSession(long id) {
    updateType(id, Types.NO_SESSION_TYPE);
  }
	
  public void markAsDecrypting(long id) {
    updateType(id, Types.DECRYPT_IN_PROGRESS_TYPE);
  }
	
  public void markAsSent(long id, long type) {
    if (type == Types.ENCRYPTING_TYPE)
      updateType(id, Types.SECURE_SENT_TYPE);
    else
      updateType(id, Types.SENT_TYPE);
  }
	
  public void markAsSentFailed(long id) {
    updateType(id, Types.FAILED_TYPE);
  }
	
  public void setMessagesRead(long threadId) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, 1);
		
    long start = System.currentTimeMillis();
    database.update(TABLE_NAME, contentValues, THREAD_ID + " = ? AND " + READ + " = 0", new String[] {threadId+""});
    long end = System.currentTimeMillis();
		
    Log.w("SmsDatabase", "setMessagesRead time: " + (end-start));
  }
	
  public void updateMessageBodyAndType(long messageId, String body, long type) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(BODY, body);
    contentValues.put(TYPE, type);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId+""});
		
    DatabaseFactory.getThreadDatabase(context).update(getThreadIdForMessage(messageId));
    notifyConversationListeners(getThreadIdForMessage(messageId));
    notifyConversationListListeners();
  }
	
  public long insertSecureMessageReceived(SmsMessage message, String body) {
    return insertMessageReceived(message, body, Types.DECRYPT_IN_PROGRESS_TYPE);
  }
	
  public long insertMessageReceived(SmsMessage message, String body) {
    return insertMessageReceived(message, body, Types.INBOX_TYPE);
  }
	
  public long insertMessageSent(String address, long threadId, String body, long date, long type) {
    ContentValues contentValues = new ContentValues(6);
    //		contentValues.put(ADDRESS, NumberUtil.filterNumber(address));
    contentValues.put(ADDRESS, address);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(BODY, body);
    contentValues.put(DATE, date);
    contentValues.put(READ, 1);
    contentValues.put(TYPE, type);
		
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    long messageId    = db.insert(TABLE_NAME, ADDRESS, contentValues);

    DatabaseFactory.getThreadDatabase(context).setRead(threadId);
    DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
    return messageId;
  }

  public Cursor getOutgoingMessages() {
    String outgoingSelection = "(" + TYPE + " = " + Types.ENCRYPTING_TYPE + " OR " + TYPE + " = " + Types.ENCRYPTED_OUTBOX_TYPE + ")";
    SQLiteDatabase db        = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, null, outgoingSelection, null, null, null, null);
  }
	
  public Cursor getDecryptInProgressMessages() {
    String where      = TYPE + " = " + Types.DECRYPT_IN_PROGRESS_TYPE;
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, null, where, null, null, null, null);
  }
	
  public Cursor getEncryptedRogueMessages(Recipient recipient) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    String selection  = TYPE + " = " + Types.NO_SESSION_TYPE + " AND PHONE_NUMBERS_EQUAL(" + ADDRESS + ", ?)";	
    String[] args     = {recipient.getNumber()};
    return db.query(TABLE_NAME, null, selection, args, null, null, null);		
  }
	
  public Cursor getMessage(long messageId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, null, ID_WHERE, new String[] {messageId+""}, null, null, null);
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
	
  /*package*/ void insertRaw(SQLiteDatabase database, ContentValues contentValues) {
    database.insert(TABLE_NAME, null, contentValues);
  }
	
  /*package*/ SQLiteStatement createInsertStatement(SQLiteDatabase database) {
    return database.compileStatement("INSERT INTO " + TABLE_NAME + " (" + ADDRESS + ", " + PERSON + ", " + DATE + ", " + PROTOCOL + ", " + READ + ", " + STATUS + ", " + TYPE + ", " + REPLY_PATH_PRESENT + ", " + SUBJECT + ", " + BODY + ", " + SERVICE_CENTER + ", THREAD_ID) " +
				     " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
  }
	
  public static class Types {
    public static final int INBOX_TYPE          = 1;
    public static final int SENT_TYPE           = 2;
    public static final int SENT_PENDING        = 4;
    public static final int FAILED_TYPE         = 5;
		
    public static final int ENCRYPTING_TYPE          = 42;  // Messages are stored local encrypted and need async encryption and delivery.
    public static final int ENCRYPTED_OUTBOX_TYPE    = 43;  // Messages are stored local encrypted and need delivery.
    public static final int SECURE_SENT_TYPE         = 44;  // Messages were sent with async encryption.
    public static final int SECURE_RECEIVED_TYPE     = 45;  // Messages were received with async decryption.
    public static final int FAILED_DECRYPT_TYPE      = 46;  // Messages were received with async encryption and failed to decrypt.
    public static final int DECRYPT_IN_PROGRESS_TYPE = 47;  // Messages are in the process of being asymmetricaly decrypted.
    public static final int NO_SESSION_TYPE          = 48;  // Messages were received with async encryption but there is no session yet.
		
    public static boolean isFailedMessageType(long type) {
      return type == FAILED_TYPE;
    }
		
    public static boolean isOutgoingMessageType(long type) {
      return type == SENT_TYPE || type == SENT_PENDING || type == ENCRYPTING_TYPE || type == ENCRYPTED_OUTBOX_TYPE || type == SECURE_SENT_TYPE || type == FAILED_TYPE;
    }
		
    public static boolean isPendingMessageType(long type) {
      return type == SENT_PENDING || type == ENCRYPTING_TYPE || type == ENCRYPTED_OUTBOX_TYPE;
    }
		
    public static boolean isSecureType(long type) {
      return type == SECURE_SENT_TYPE || type == ENCRYPTING_TYPE || type == SECURE_RECEIVED_TYPE;
    }
	
  }
	
}

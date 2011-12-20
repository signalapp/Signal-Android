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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ThreadDatabase extends Database {

  private static final String TABLE_NAME          = "thread";
  public  static final String ID                  = "_id";
  public  static final String DATE                = "date";
  public  static final String MESSAGE_COUNT       = "message_count";
  public  static final String RECIPIENT_IDS       = "recipient_ids";
  public  static final String SNIPPET             = "snippet";
  private static final String SNIPPET_CHARSET     = "snippet_cs";
  public  static final String READ                = "read";
  private static final String TYPE                = "type";
  private static final String ERROR               = "error";
  private static final String HAS_ATTACHMENT      = "has_attachment";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, "                             +
    DATE + " INTEGER DEFAULT 0, " + MESSAGE_COUNT + " INTEGER DEFAULT 0, "                         +
    RECIPIENT_IDS + " TEXT, " + SNIPPET + " TEXT, " + SNIPPET_CHARSET + " INTEGER DEFAULT 0, "     +
    READ + " INTEGER DEFAULT 1, " + TYPE + " INTEGER DEFAULT 0, " + ERROR + " INTEGER DEFAULT 0, " +
    HAS_ATTACHMENT + " INTEGER DEFAULT 0);";				

  public ThreadDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }


  private long[] getRecipientIds(Recipients recipients) {
    Set<Long> recipientSet = new HashSet<Long>();
    List<Recipient> recipientList = recipients.getRecipientsList();

    for (Recipient recipient : recipientList) {
      //			String number = NumberUtil.filterNumber(recipient.getNumber());
      String number = recipient.getNumber();
      recipientSet.add(new Long(DatabaseFactory.getAddressDatabase(context).getCanonicalAddress(number)));
    }
		
    long[] recipientArray = new long[recipientSet.size()];
    int i                 = 0;
		
    for (Long recipientId : recipientSet) {
      recipientArray[i++] = recipientId;
    }
		
    Arrays.sort(recipientArray);
		
    return recipientArray;
  }
	
  private String getRecipientsAsString(long[] recipientIds) {
    StringBuilder sb = new StringBuilder();
    for (int i=0;i<recipientIds.length;i++) {
      if (i != 0) sb.append(' ');
      sb.append(recipientIds[i]);
    }
		
    return sb.toString();
  }
	
  private long createThreadForRecipients(String recipients, int recipientCount) {
    ContentValues contentValues = new ContentValues(4);
    long date                   = System.currentTimeMillis();
        
    contentValues.put(DATE, date - date % 1000);
    contentValues.put(RECIPIENT_IDS, recipients);
        
    if (recipientCount > 1)
      contentValues.put(TYPE, 1);
        
    contentValues.put(MESSAGE_COUNT, 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    return db.insert(TABLE_NAME, null, contentValues);
  }

  private void updateThread(long threadId, long count, String body, long date) {
    ContentValues contentValues = new ContentValues(3);
    contentValues.put(DATE, date - date % 1000);
    contentValues.put(MESSAGE_COUNT, count);
    contentValues.put(SNIPPET, body);
		
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] {threadId+""});
    notifyConversationListListeners();
  }
	
  private void deleteThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, ID_WHERE, new String[] {threadId+""});			
    notifyConversationListListeners();
  }
	
  private void deleteThreads(Set<Long> threadIds) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    String where      = "";
		
    for (long threadId : threadIds) {
      where += ID + " = '" + threadId + "' OR ";
    }
		
    where = where.substring(0, where.length() - 4);
		
    db.delete(TABLE_NAME, where, null);
    notifyConversationListListeners();
  }
	
  private void deleteAllThreads() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);			
    notifyConversationListListeners();		
  }
	
  public void setRead(long threadId) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, 1);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId+""});
		
    DatabaseFactory.getSmsDatabase(context).setMessagesRead(threadId);
    DatabaseFactory.getMmsDatabase(context).setMessagesRead(threadId);
    notifyConversationListListeners();
  }
	
  public void setUnread(long threadId) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put("read", 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId+""});
    notifyConversationListListeners();		
  }
	
  public Cursor getFilteredConversationList(List<String> filter) {
    if (filter == null || filter.size() == 0)
      return null;
		
    List<Long> recipientIds = DatabaseFactory.getAddressDatabase(context).getCanonicalAddresses(filter);
		
    if (recipientIds == null || recipientIds.size() == 0)
      return null;
		
    String selection       = RECIPIENT_IDS + " = ?";
    String[] selectionArgs = new String[recipientIds.size()];
		
    for (int i=0;i<recipientIds.size()-1;i++)
      selection += (" OR " + RECIPIENT_IDS + " = ?");
		
    int i= 0;		
    for (long id : recipientIds) {
      selectionArgs[i++] = id+"";
    }
		
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = db.query(TABLE_NAME, null, selection, selectionArgs, null, null, DATE + " DESC");
    setNotifyConverationListListeners(cursor);		
    return cursor;
  }
	
  public Cursor getConversationList() {		
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     =  db.query(TABLE_NAME, null, null, null, null, null, DATE + " DESC");
    setNotifyConverationListListeners(cursor);
    return cursor;
  }
	
  public void deleteConversation(long threadId) {
    DatabaseFactory.getSmsDatabase(context).deleteThread(threadId);
    DatabaseFactory.getMmsDatabase(context).deleteThread(threadId);
    deleteThread(threadId);
    notifyConversationListeners(threadId);
    notifyConversationListListeners();
  }		
	

  public void deleteConversations(Set<Long> selectedConversations) {
    DatabaseFactory.getSmsDatabase(context).deleteThreads(selectedConversations);
    DatabaseFactory.getMmsDatabase(context).deleteThreads(selectedConversations);
    deleteThreads(selectedConversations);
    notifyConversationListeners(selectedConversations);
    notifyConversationListListeners();
  }
	
  public void deleteAllConversations() {
    DatabaseFactory.getSmsDatabase(context).deleteAllThreads();
    DatabaseFactory.getMmsDatabase(context).deleteAllThreads();
    deleteAllThreads();		
  }

  public long getThreadIdIfExistsFor(Recipients recipients) {
    long[] recipientIds    = getRecipientIds(recipients);
    String recipientsList  = getRecipientsAsString(recipientIds);
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    String where           = RECIPIENT_IDS + " = ?";
    String[] recipientsArg = new String[] {recipientsList};
    Cursor cursor          = null;
		
    try {
      cursor = db.query(TABLE_NAME, new String[]{ID}, where, recipientsArg, null, null, null);
			
      if (cursor != null && cursor.moveToFirst())
	return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      else
	return -1L;
    } finally {
      if (cursor != null)
	cursor.close();
    }
  }
	
  public long getThreadIdFor(Recipients recipients) {
    long[] recipientIds    = getRecipientIds(recipients);
    String recipientsList  = getRecipientsAsString(recipientIds);
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    String where           = RECIPIENT_IDS + " = ?";
    String[] recipientsArg = new String[] {recipientsList};
    Cursor cursor          = null;
		
    try {
      cursor = db.query(TABLE_NAME, new String[]{ID}, where, recipientsArg, null, null, null);
			
      if (cursor != null && cursor.moveToFirst())
	return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      else
	return createThreadForRecipients(recipientsList, recipientIds.length);
    } finally {
      if (cursor != null)
	cursor.close();
    }
  }
	
  public void update(long threadId) {
    MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
    long count                    = mmsSmsDatabase.getConversationCount(threadId);
		
    if (count == 0) {
      deleteThread(threadId);
      notifyConversationListListeners();
      return;
    }
		
    Cursor cursor = null;
		
    try {
      cursor = mmsSmsDatabase.getConversationSnippet(threadId);
      if (cursor != null && cursor.moveToFirst())
	updateThread(threadId, count, 
		     cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY)), 
		     cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.DATE)));
      else
	deleteThread(threadId);
    } finally {
      if (cursor != null)
	cursor.close();
    }
		
    notifyConversationListListeners();
  }





}

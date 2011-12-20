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

import java.util.StringTokenizer;

import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class SmsMigrator {

  public static final int PROGRESS_UPDATE           = 1;
  public static final int SECONDARY_PROGRESS_UPDATE = 2;
  public static final int COMPLETE                  = 3;
	
  private static void addEncryptedStringToStatement(Context context, SQLiteStatement statement, Cursor cursor, MasterSecret masterSecret, int index, String key) {
    int columnIndex = cursor.getColumnIndexOrThrow(key);
    if (cursor.isNull(columnIndex))
      statement.bindNull(index);
    else
      statement.bindString(index, encryptIfNecessary(context, masterSecret, cursor.getString(columnIndex)));
		
  }
	
  private static void addStringToStatement(SQLiteStatement statement, Cursor cursor, int index, String key) {
    int columnIndex = cursor.getColumnIndexOrThrow(key);
    if (cursor.isNull(columnIndex))
      statement.bindNull(index);
    else
      statement.bindString(index, cursor.getString(columnIndex));
  }

  private static void addIntToStatement(SQLiteStatement statement, Cursor cursor, int index, String key) {
    int columnIndex = cursor.getColumnIndexOrThrow(key);
    if (cursor.isNull(columnIndex))
      statement.bindNull(index);
    else
      statement.bindLong(index, cursor.getLong(columnIndex));		
  }
	
  private static void getContentValuesForRow(Context context, MasterSecret masterSecret, Cursor cursor, long threadId, SQLiteStatement statement) {
    addStringToStatement(statement, cursor, 1, SmsDatabase.ADDRESS);
    addIntToStatement(statement, cursor, 2, SmsDatabase.PERSON);
    addIntToStatement(statement, cursor, 3, SmsDatabase.DATE);
    addIntToStatement(statement, cursor, 4, SmsDatabase.PROTOCOL);
    addIntToStatement(statement, cursor, 5, SmsDatabase.READ);
    addIntToStatement(statement, cursor, 6, SmsDatabase.STATUS);
    addIntToStatement(statement, cursor, 7, SmsDatabase.TYPE);
    addIntToStatement(statement, cursor, 8, SmsDatabase.REPLY_PATH_PRESENT);
    addStringToStatement(statement, cursor, 9, SmsDatabase.SUBJECT);
    addEncryptedStringToStatement(context, statement, cursor, masterSecret, 10, SmsDatabase.BODY);
    addStringToStatement(statement, cursor, 11, SmsDatabase.SERVICE_CENTER);
		
    statement.bindLong(12, threadId);
  }
	
  private static String getTheirCanonicalAddress(Context context, String theirRecipientId) {
    Uri uri  = Uri.parse("content://mms-sms/canonical-address/" + theirRecipientId);
    Cursor cursor = null;
		
    try {
      cursor = context.getContentResolver().query(uri, null, null, null, null);
      if (cursor != null && cursor.moveToFirst())
	return cursor.getString(0);
      else
	return null;
    } finally {
      if (cursor != null)
	cursor.close();
    }
  }
	
  private static Recipients getOurRecipients(Context context, String theirRecipients) {
    StringTokenizer tokenizer = new StringTokenizer(theirRecipients.trim(), " ");
    StringBuilder sb          = new StringBuilder();
		
    while (tokenizer.hasMoreTokens()) {
      String theirRecipientId = tokenizer.nextToken();
      String address          = getTheirCanonicalAddress(context, theirRecipientId);
			
      if (address == null)
	continue;
			
      if (sb.length() != 0)
	sb.append(',');
			
      sb.append(address);
    }
		
    try {
      if (sb.length() == 0) return null;
      else                  return RecipientFactory.getRecipientsFromString(context, sb.toString());
    } catch (RecipientFormattingException rfe) {
      Log.w("SmsMigrator", rfe);
      return null;
    }
  }
	
  private static String encryptIfNecessary(Context context, MasterSecret masterSecret, String body) {
    if (!body.startsWith(Prefix.SYMMETRIC_ENCRYPT) && !body.startsWith(Prefix.ASYMMETRIC_ENCRYPT)) {
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      return Prefix.SYMMETRIC_ENCRYPT + masterCipher.encryptBody(body);
    }
		
    return body;
  }
	
  private static void migrateConversation(Context context, MasterSecret masterSecret, Handler handler, long theirThreadId, long ourThreadId) {
    SmsDatabase ourSmsDatabase = DatabaseFactory.getSmsDatabase(context);
    Cursor cursor              = null;
		
    try {
      Uri uri                    = Uri.parse("content://sms/conversations/" + theirThreadId);
      cursor                     = context.getContentResolver().query(uri, null, null, null, null);
      SQLiteDatabase transaction = ourSmsDatabase.beginTransaction();
      SQLiteStatement statement  = ourSmsDatabase.createInsertStatement(transaction);
			
      while (cursor != null && cursor.moveToNext()) {
	getContentValuesForRow(context, masterSecret, cursor, ourThreadId, statement);
	statement.execute();
				
	Message msg = handler.obtainMessage(SECONDARY_PROGRESS_UPDATE, 10000/cursor.getCount(), 0);
	handler.sendMessage(msg);				
      }
			
      ourSmsDatabase.endTransaction(transaction);
      DatabaseFactory.getThreadDatabase(context).update(ourThreadId);
      DatabaseFactory.getThreadDatabase(context).notifyConversationListeners(ourThreadId);

    } finally {
      if (cursor != null)
	cursor.close();
    }
  }
	
  public static void migrateDatabase(Context context, MasterSecret masterSecret, Handler handler) {
    if (context.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE).getBoolean("migrated", false))
      return;
		
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
		
    Cursor cursor = null;
		
    try {
      Uri threadListUri = Uri.parse("content://mms-sms/conversations?simple=true");
      cursor            = context.getContentResolver().query(threadListUri, null, null, null, "date ASC");

      while (cursor != null && cursor.moveToNext()) {
	long   theirThreadId     = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
	String theirRecipients   = cursor.getString(cursor.getColumnIndexOrThrow("recipient_ids"));
	Recipients ourRecipients = getOurRecipients(context, theirRecipients);
	    		
	if (ourRecipients != null) {
	  long ourThreadId = threadDatabase.getThreadIdFor(ourRecipients);
	  migrateConversation(context, masterSecret, handler, theirThreadId, ourThreadId);
	}
	    		
	Message msg = handler.obtainMessage(PROGRESS_UPDATE, 10000/cursor.getCount(), 0);
	handler.sendMessage(msg);
      }
    } finally {
      if (cursor != null)
	cursor.close();
    }
		
    context.getSharedPreferences("SecureSMS", Context.MODE_PRIVATE).edit().putBoolean("migrated", true).commit();
    handler.sendEmptyMessage(COMPLETE);
  }
}

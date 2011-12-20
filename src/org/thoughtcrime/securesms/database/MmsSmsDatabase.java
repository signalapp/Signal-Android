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

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;

public class MmsSmsDatabase extends Database {
	
  public static final String TRANSPORT = "transport_type";

  public MmsSmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }
	
  public Cursor getConversation(long threadId) {
    String[] projection    = {"_id", "body", "type", "address", "subject", "normalized_date AS date", "m_type", "msg_box", "transport_type"};
    String order           = "normalized_date ASC";
    String selection       = "thread_id = " + threadId;
		
    Cursor cursor = queryTables(projection, selection, order, null);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }
	
  public Cursor getConversationSnippet(long threadId) {
    String[] projection    = {"_id", "body", "type", "address", "subject", "normalized_date AS date", "m_type", "msg_box", "transport_type"};
    String order           = "normalized_date DESC";
    String selection       = "thread_id = " + threadId;
		
    Cursor cursor = queryTables(projection, selection, order, "1");
    return cursor;
  }
	
  public Cursor getUnread() {
    String[] projection    = {"_id", "body", "read", "type", "address", "subject", "thread_id", "normalized_date AS date", "m_type", "msg_box", "transport_type"};
    String order           = "normalized_date ASC";
    String selection       = "read = 0";
		
    Cursor cursor = queryTables(projection, selection, order, null);
    return cursor;		
  }
	
  public int getConversationCount(long threadId) {
    int count = DatabaseFactory.getSmsDatabase(context).getMessageCountForThread(threadId);
    count    += DatabaseFactory.getMmsDatabase(context).getMessageCountForThread(threadId);
		
    return count;
  }

  private Cursor queryTables(String[] projection, String selection, String order, String limit) {
    String[] mmsProjection = {"date * 1000 AS normalized_date", "_id", "body", "read", "thread_id", "type", "address", "subject", "date", "m_type", "msg_box", "transport_type"};
    String[] smsProjection = {"date * 1 AS normalized_date", "_id", "body", "read", "thread_id", "type", "address", "subject", "date", "m_type", "msg_box", "transport_type"};
		
    SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
    SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

    mmsQueryBuilder.setDistinct(true);
    smsQueryBuilder.setDistinct(true);

    mmsQueryBuilder.setTables(MmsDatabase.TABLE_NAME);
    smsQueryBuilder.setTables(SmsDatabase.TABLE_NAME);
        
    Set<String> mmsColumnsPresent = new HashSet<String>();
    mmsColumnsPresent.add("_id");
    mmsColumnsPresent.add("m_type");
    mmsColumnsPresent.add("msg_box");
    mmsColumnsPresent.add("date");
    mmsColumnsPresent.add("read");
    mmsColumnsPresent.add("thread_id");
        
    Set<String> smsColumnsPresent = new HashSet<String>();
    smsColumnsPresent.add("_id");
    smsColumnsPresent.add("body");
    smsColumnsPresent.add("type");
    smsColumnsPresent.add("address");
    smsColumnsPresent.add("subject");
    smsColumnsPresent.add("date");
    smsColumnsPresent.add("read");
    smsColumnsPresent.add("thread_id");

    String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery("transport_type", mmsProjection, mmsColumnsPresent, 0, "mms", selection, null, null, null);
    String smsSubQuery = smsQueryBuilder.buildUnionSubQuery("transport_type", smsProjection, smsColumnsPresent, 0, "sms", selection, null, null, null);

    SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();       
    String unionQuery = unionQueryBuilder.buildUnionQuery(new String[] {smsSubQuery, mmsSubQuery}, order, null);

    SQLiteQueryBuilder outerQueryBuilder = new SQLiteQueryBuilder();
    outerQueryBuilder.setTables("(" + unionQuery + ")");
        
    String query      = outerQueryBuilder.buildQuery(projection, null, null, null, null, null, limit);		
        
    Log.w("MmsSmsDatabase", "Executing query: " + query);
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = db.rawQuery(query, null);
    return cursor;
		
  }
	
}

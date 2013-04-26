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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.HashSet;
import java.util.Set;

public class MmsSmsDatabase extends Database {

  public static final String TRANSPORT     = "transport_type";
  public static final String MMS_TRANSPORT = "mms";
  public static final String SMS_TRANSPORT = "sms";

  public MmsSmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

//  public Cursor getCollatedGroupConversation(long threadId) {
//    String smsCaseSecurity = "CASE " + SmsDatabase.TYPE + " & " + SmsDatabase.Types.SECURE_MESSAGE_BIT + " " +
//                             "WHEN " + SmsDatabase.Types.SECURE_MESSAGE_BIT    + " THEN 1 " +
//                             "ELSE 0 END";
//
//    String mmsCaseSecurity = "CASE " + MmsDatabase.MESSAGE_BOX + " & " + SmsDatabase.Types.SECURE_MESSAGE_BIT + " " +
//                             "WHEN " + MmsDatabase.Types.SECURE_MESSAGE_BIT + " THEN 'secure' " +
//                             "ELSE 'insecure' END";
//
//    String mmsGroupSentCount = "SUM(CASE " + MmsDatabase.MESSAGE_BOX + " & " + MmsDatabase.Types.BASE_TYPE_MASK + " " +
//                               "WHEN " + MmsDatabase.Types.BASE_SENT_TYPE + " THEN 1 " +
//                               "ELSE 0 END)";
//
//
//    String smsGroupSentCount = "SUM(CASE " + SmsDatabase.TYPE + " & " + SmsDatabase.Types.BASE_TYPE_MASK + " " +
//                               "WHEN " + SmsDatabase.Types.BASE_SENT_TYPE + " THEN 1 " +
//                               "ELSE 0 END)";
//
//    String mmsGroupSentFailedCount = "SUM(CASE " + MmsDatabase.MESSAGE_BOX + " & " + MmsDatabase.Types.BASE_TYPE_MASK + " " +
//                                     "WHEN " + MmsDatabase.Types.BASE_SENT_FAILED_TYPE + " THEN 1 " +
//                                     "ELSE 0 END)";
//
//    String smsGroupSentFailedCount = "SUM(CASE " + SmsDatabase.TYPE + " & " + SmsDatabase.Types.BASE_TYPE_MASK +  " " +
//                                     "WHEN " + SmsDatabase.Types.BASE_SENT_FAILED_TYPE + " THEN 1 " +
//                                     "ELSE 0 END)";
//
//    String[] projection = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.TYPE,
//                           MmsSmsColumns.THREAD_ID,
//                           SmsDatabase.ADDRESS, SmsDatabase.SUBJECT, SmsDatabase.STATUS,
//                           MmsSmsColumns.NORMALIZED_DATE_SENT, MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
//                           MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX, TRANSPORT,
//                           "COUNT(" + MmsSmsColumns.ID + ") AS " + GROUP_SIZE,
//                           mmsGroupSentCount + " AS " + MMS_GROUP_SENT_COUNT,
//                           mmsGroupSentFailedCount + " AS " + MMS_GROUP_SEND_FAILED_COUNT,
//                           smsGroupSentCount + " AS " + SMS_GROUP_SENT_COUNT,
//                           smsGroupSentFailedCount + " AS " + SMS_GROUP_SEND_FAILED_COUNT,
//                           smsCaseSecurity + " AS sms_collate", mmsCaseSecurity + " AS mms_collate"};
//
//    String order        = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";
//    String selection    = MmsSmsColumns.THREAD_ID + " = " + threadId;
//    String groupBy      = MmsSmsColumns.NORMALIZED_DATE_SENT + " / 1000, sms_collate, mms_collate";
//
//    Cursor cursor = queryTables(projection, selection, order, groupBy, null);
//    setNotifyConverationListeners(cursor, threadId);
//
//    return cursor;
//  }

  public Cursor getConversation(long threadId) {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.TYPE,
                              MmsSmsColumns.THREAD_ID,
                              SmsDatabase.ADDRESS, SmsDatabase.SUBJECT,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              SmsDatabase.STATUS, MmsDatabase.PART_COUNT, TRANSPORT};

    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";

    String selection       = MmsSmsColumns.THREAD_ID + " = " + threadId;

    Cursor cursor = queryTables(projection, selection, order, null, null);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }

  public Cursor getConversationSnippet(long threadId) {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.TYPE,
                              MmsSmsColumns.THREAD_ID,
                              SmsDatabase.ADDRESS, SmsDatabase.SUBJECT,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              SmsDatabase.STATUS, MmsDatabase.PART_COUNT, TRANSPORT};
    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
    String selection       = MmsSmsColumns.THREAD_ID + " = " + threadId;

    return  queryTables(projection, selection, order, null, "1");
  }

  public Cursor getUnread() {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.READ, SmsDatabase.TYPE,
                              SmsDatabase.ADDRESS, SmsDatabase.SUBJECT, MmsSmsColumns.THREAD_ID,
                              SmsDatabase.STATUS,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              MmsDatabase.PART_COUNT, TRANSPORT};
    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";
    String selection       = MmsSmsColumns.READ + " = 0";

    return queryTables(projection, selection, order, null, null);
  }

  public int getConversationCount(long threadId) {
    int count = DatabaseFactory.getSmsDatabase(context).getMessageCountForThread(threadId);
    count    += DatabaseFactory.getMmsDatabase(context).getMessageCountForThread(threadId);

    return count;
  }

  private Cursor queryTables(String[] projection, String selection, String order, String groupBy, String limit) {
    String[] mmsProjection = {MmsDatabase.DATE_SENT + " * 1000 AS " + MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsDatabase.DATE_RECEIVED + " * 1000 AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsSmsColumns.ID, SmsDatabase.BODY, MmsSmsColumns.READ, MmsSmsColumns.THREAD_ID,
                              SmsDatabase.TYPE, SmsDatabase.ADDRESS, SmsDatabase.SUBJECT, MmsDatabase.MESSAGE_TYPE,
                              MmsDatabase.MESSAGE_BOX, SmsDatabase.STATUS, MmsDatabase.PART_COUNT, TRANSPORT};

    String[] smsProjection = {SmsDatabase.DATE_SENT + " * 1 AS " + MmsSmsColumns.NORMALIZED_DATE_SENT,
                              SmsDatabase.DATE_RECEIVED + " * 1 AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsSmsColumns.ID, SmsDatabase.BODY, MmsSmsColumns.READ, MmsSmsColumns.THREAD_ID,
                              SmsDatabase.TYPE, SmsDatabase.ADDRESS, SmsDatabase.SUBJECT, MmsDatabase.MESSAGE_TYPE,
                              MmsDatabase.MESSAGE_BOX, SmsDatabase.STATUS, MmsDatabase.PART_COUNT, TRANSPORT};


    SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
    SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

    mmsQueryBuilder.setDistinct(true);
    smsQueryBuilder.setDistinct(true);

    mmsQueryBuilder.setTables(MmsDatabase.TABLE_NAME);
    smsQueryBuilder.setTables(SmsDatabase.TABLE_NAME);

    Set<String> mmsColumnsPresent = new HashSet<String>();
    mmsColumnsPresent.add(MmsSmsColumns.ID);
    mmsColumnsPresent.add(MmsDatabase.MESSAGE_TYPE);
    mmsColumnsPresent.add(MmsDatabase.MESSAGE_BOX);
    mmsColumnsPresent.add(MmsDatabase.DATE_SENT);
    mmsColumnsPresent.add(MmsDatabase.DATE_RECEIVED);
    mmsColumnsPresent.add(MmsSmsColumns.READ);
    mmsColumnsPresent.add(MmsSmsColumns.THREAD_ID);
    mmsColumnsPresent.add(MmsSmsColumns.BODY);
    mmsColumnsPresent.add(MmsDatabase.PART_COUNT);

    Set<String> smsColumnsPresent = new HashSet<String>();
    smsColumnsPresent.add(MmsSmsColumns.ID);
    smsColumnsPresent.add(MmsSmsColumns.BODY);
    smsColumnsPresent.add(SmsDatabase.TYPE);
    smsColumnsPresent.add(SmsDatabase.ADDRESS);
    smsColumnsPresent.add(SmsDatabase.SUBJECT);
    smsColumnsPresent.add(SmsDatabase.DATE_SENT);
    smsColumnsPresent.add(SmsDatabase.DATE_RECEIVED);
    smsColumnsPresent.add(MmsSmsColumns.READ);
    smsColumnsPresent.add(MmsSmsColumns.THREAD_ID);
    smsColumnsPresent.add(SmsDatabase.STATUS);

    String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery(TRANSPORT, mmsProjection, mmsColumnsPresent, 2, MMS_TRANSPORT, selection, null, null, null);
    String smsSubQuery = smsQueryBuilder.buildUnionSubQuery(TRANSPORT, smsProjection, smsColumnsPresent, 2, SMS_TRANSPORT, selection, null, null, null);

    SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();
    String unionQuery = unionQueryBuilder.buildUnionQuery(new String[] {smsSubQuery, mmsSubQuery}, order, null);

    SQLiteQueryBuilder outerQueryBuilder = new SQLiteQueryBuilder();
    outerQueryBuilder.setTables("(" + unionQuery + ")");

    String query      = outerQueryBuilder.buildQuery(projection, null, null, groupBy, null, null, limit);

    Log.w("MmsSmsDatabase", "Executing query: " + query);
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.rawQuery(query, null);
  }

  public Reader readerFor(Cursor cursor, MasterSecret masterSecret) {
    return new Reader(cursor, masterSecret);
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public class Reader {

    private final Cursor cursor;
    private final EncryptingSmsDatabase.Reader smsReader;
    private final MmsDatabase.Reader mmsReader;

    public Reader(Cursor cursor, MasterSecret masterSecret) {
      this.cursor       = cursor;
      this.smsReader    = DatabaseFactory.getEncryptingSmsDatabase(context).readerFor(masterSecret, cursor);
      this.mmsReader    = DatabaseFactory.getMmsDatabase(context).readerFor(masterSecret, cursor);
    }

    public Reader(Cursor cursor) {
      this.cursor = cursor;
      this.smsReader = DatabaseFactory.getSmsDatabase(context).readerFor(cursor);
      this.mmsReader = DatabaseFactory.getMmsDatabase(context).readerFor(null, cursor);
    }

    public MessageRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public MessageRecord getCurrent() {
      String type = cursor.getString(cursor.getColumnIndexOrThrow(TRANSPORT));

      if (type.equals(MmsSmsDatabase.MMS_TRANSPORT)) {
        return mmsReader.getCurrent();
      } else {
        return smsReader.getCurrent();
      }
    }

    public void close() {
      cursor.close();
    }
  }
}

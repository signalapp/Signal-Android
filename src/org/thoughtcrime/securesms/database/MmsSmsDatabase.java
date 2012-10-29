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

import java.util.HashSet;
import java.util.Set;

public class MmsSmsDatabase extends Database {

  public static final String TRANSPORT                   = "transport_type";
  public static final String GROUP_SIZE                  = "group_size";
  public static final String SMS_GROUP_SENT_COUNT        = "sms_group_sent_count";
  public static final String SMS_GROUP_SEND_FAILED_COUNT = "sms_group_sent_failed_count";
  public static final String MMS_GROUP_SENT_COUNT        = "mms_group_sent_count";
  public static final String MMS_GROUP_SEND_FAILED_COUNT = "mms_group_sent_failed_count";

  public MmsSmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getCollatedGroupConversation(long threadId) {
    String smsCaseSecurity = "CASE " + SmsDatabase.TYPE + " " +
                             "WHEN " + SmsDatabase.Types.SENT_TYPE             + " THEN 1 " +
                             "WHEN " + SmsDatabase.Types.SENT_PENDING          + " THEN 1 " +
                             "WHEN " + SmsDatabase.Types.ENCRYPTED_OUTBOX_TYPE + " THEN 1 " +
                             "WHEN " + SmsDatabase.Types.FAILED_TYPE           + " THEN 1 " +
                             "WHEN " + SmsDatabase.Types.ENCRYPTING_TYPE       + " THEN 2 " +
                             "WHEN " + SmsDatabase.Types.SECURE_SENT_TYPE      + " THEN 2 " +
                             "ELSE 0 END";

    String mmsCaseSecurity = "CASE " + MmsDatabase.MESSAGE_BOX + " " +
                             "WHEN " + MmsDatabase.Types.MESSAGE_BOX_OUTBOX        + " THEN 'insecure' " +
                             "WHEN " + MmsDatabase.Types.MESSAGE_BOX_SENT          + " THEN 'insecure' " +
                             "WHEN " + MmsDatabase.Types.MESSAGE_BOX_SENT_FAILED   + " THEN 'insecure' " +
                             "WHEN " + MmsDatabase.Types.MESSAGE_BOX_SECURE_OUTBOX + " THEN 'secure' " +
                             "WHEN " + MmsDatabase.Types.MESSAGE_BOX_SECURE_SENT   + " THEN 'secure' " +
                             "ELSE 0 END";

    String mmsGroupSentCount = "SUM(CASE " + MmsDatabase.MESSAGE_BOX + " " +
                               "WHEN " + MmsDatabase.Types.MESSAGE_BOX_SENT        + " THEN 1 " +
                               "WHEN " + MmsDatabase.Types.MESSAGE_BOX_SECURE_SENT + " THEN 1 " +
                               "ELSE 0 END)";

    String smsGroupSentCount = "SUM(CASE " + SmsDatabase.TYPE + " " +
                               "WHEN " + SmsDatabase.Types.SENT_TYPE        + " THEN 1 " +
                               "WHEN " + SmsDatabase.Types.SECURE_SENT_TYPE + " THEN 1 " +
                               "ELSE 0 END)";

    String mmsGroupSentFailedCount = "SUM(CASE " + MmsDatabase.MESSAGE_BOX + " " +
                                     "WHEN " + MmsDatabase.Types.MESSAGE_BOX_SENT_FAILED + " THEN 1 " +
                                     "ELSE 0 END)";

    String smsGroupSentFailedCount = "SUM(CASE " + SmsDatabase.TYPE + " " +
                                     "WHEN " + SmsDatabase.Types.FAILED_TYPE + " THEN 1 " +
                                     "ELSE 0 END)";

    String[] projection = {"_id", "body", "type", "address", "subject", "normalized_date AS date", "m_type", "msg_box", "transport_type", "COUNT(_id) AS group_size", mmsGroupSentCount + " AS mms_group_sent_count", mmsGroupSentFailedCount + " AS mms_group_sent_failed_count", smsGroupSentCount + " AS sms_group_sent_count", smsGroupSentFailedCount + " AS sms_group_sent_failed_count", smsCaseSecurity + " AS sms_collate", mmsCaseSecurity + " AS mms_collate"};
    String order        = "normalized_date ASC";
    String selection    = "thread_id = " + threadId;
    String groupBy      = "normalized_date / 1000, sms_collate, mms_collate";

    Cursor cursor = queryTables(projection, selection, order, groupBy, null);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }

  public Cursor getConversation(long threadId) {
    String[] projection    = {"_id", "body", "type", "address", "subject", "normalized_date AS date", "m_type", "msg_box", "transport_type"};
    String order           = "normalized_date ASC";
    String selection       = "thread_id = " + threadId;

    Cursor cursor = queryTables(projection, selection, order, null, null);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }

  public Cursor getConversationSnippet(long threadId) {
    String[] projection    = {"_id", "body", "type", "address", "subject", "normalized_date AS date", "m_type", "msg_box", "transport_type"};
    String order           = "normalized_date DESC";
    String selection       = "thread_id = " + threadId;

    Cursor cursor = queryTables(projection, selection, order, null, "1");
    return cursor;
  }

  public Cursor getUnread() {
    String[] projection    = {"_id", "body", "read", "type", "address", "subject", "thread_id", "normalized_date AS date", "m_type", "msg_box", "transport_type"};
    String order           = "normalized_date ASC";
    String selection       = "read = 0";

    Cursor cursor = queryTables(projection, selection, order, null, null);
    return cursor;
  }

  public int getConversationCount(long threadId) {
    int count = DatabaseFactory.getSmsDatabase(context).getMessageCountForThread(threadId);
    count    += DatabaseFactory.getMmsDatabase(context).getMessageCountForThread(threadId);

    return count;
  }

  private Cursor queryTables(String[] projection, String selection, String order, String groupBy, String limit) {
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

    String query      = outerQueryBuilder.buildQuery(projection, null, null, groupBy, null, null, limit);

    Log.w("MmsSmsDatabase", "Executing query: " + query);
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = db.rawQuery(query, null);
    return cursor;

  }

}

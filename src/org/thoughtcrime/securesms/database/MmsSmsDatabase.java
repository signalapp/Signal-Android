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

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.util.LRUCache;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MmsSmsDatabase extends Database {

  public static final String TRANSPORT     = "transport_type";
  public static final String MMS_TRANSPORT = "mms";
  public static final String SMS_TRANSPORT = "sms";

  private final Map<Long, String> queryCache = new LRUCache<>(100);

  private static final String[] CONVERSATION_PROJECTION = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.TYPE,
                                                           MmsSmsColumns.THREAD_ID,
                                                           SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT,
                                                           MmsSmsColumns.NORMALIZED_DATE_SENT,
                                                           MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                                                           MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                                                           SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                                                           MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                                                           MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY,
                                                           MmsDatabase.STATUS, MmsSmsColumns.RECEIPT_COUNT,
                                                           MmsSmsColumns.MISMATCHED_IDENTITIES,
                                                           MmsDatabase.NETWORK_FAILURE, TRANSPORT};
  private static final String[] MMS_PROJECTION = {MmsDatabase.DATE_SENT + " AS " + MmsSmsColumns.NORMALIZED_DATE_SENT,
                                                  MmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                                                  MmsSmsColumns.ID, SmsDatabase.BODY, MmsSmsColumns.READ, MmsSmsColumns.THREAD_ID,
                                                  SmsDatabase.TYPE, SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT, MmsDatabase.MESSAGE_TYPE,
                                                  MmsDatabase.MESSAGE_BOX, SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                                                  MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                                                  MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY, MmsDatabase.STATUS,
                                                  MmsSmsColumns.RECEIPT_COUNT, MmsSmsColumns.MISMATCHED_IDENTITIES,
                                                  MmsDatabase.NETWORK_FAILURE, TRANSPORT};
  private static final String[] SMS_PROJECTION = {SmsDatabase.DATE_SENT + " AS " + MmsSmsColumns.NORMALIZED_DATE_SENT,
                                                  SmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                                                  MmsSmsColumns.ID, SmsDatabase.BODY, MmsSmsColumns.READ, MmsSmsColumns.THREAD_ID,
                                                  SmsDatabase.TYPE, SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT, MmsDatabase.MESSAGE_TYPE,
                                                  MmsDatabase.MESSAGE_BOX, SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                                                  MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                                                  MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY, MmsDatabase.STATUS,
                                                  MmsSmsColumns.RECEIPT_COUNT, MmsSmsColumns.MISMATCHED_IDENTITIES,
                                                  MmsDatabase.NETWORK_FAILURE, TRANSPORT};
  private static final String CONVERSATION_ORDER = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";
  private static final Set<String> MMS_COLUMNS_PRESENT = new HashSet<String>() {{
    add(MmsSmsColumns.ID);
    add(MmsSmsColumns.READ);
    add(MmsSmsColumns.THREAD_ID);
    add(MmsSmsColumns.BODY);
    add(MmsSmsColumns.ADDRESS);
    add(MmsSmsColumns.ADDRESS_DEVICE_ID);
    add(MmsSmsColumns.RECEIPT_COUNT);
    add(MmsSmsColumns.MISMATCHED_IDENTITIES);
    add(MmsDatabase.MESSAGE_TYPE);
    add(MmsDatabase.MESSAGE_BOX);
    add(MmsDatabase.DATE_SENT);
    add(MmsDatabase.DATE_RECEIVED);
    add(MmsDatabase.PART_COUNT);
    add(MmsDatabase.CONTENT_LOCATION);
    add(MmsDatabase.TRANSACTION_ID);
    add(MmsDatabase.MESSAGE_SIZE);
    add(MmsDatabase.EXPIRY);
    add(MmsDatabase.STATUS);
    add(MmsDatabase.NETWORK_FAILURE);
  }};
  private static final Set<String> SMS_COLUMNS_PRESENT = new HashSet<String>() {{
    add(MmsSmsColumns.ID);
    add(MmsSmsColumns.BODY);
    add(MmsSmsColumns.ADDRESS);
    add(MmsSmsColumns.ADDRESS_DEVICE_ID);
    add(MmsSmsColumns.READ);
    add(MmsSmsColumns.THREAD_ID);
    add(MmsSmsColumns.RECEIPT_COUNT);
    add(MmsSmsColumns.MISMATCHED_IDENTITIES);
    add(SmsDatabase.TYPE);
    add(SmsDatabase.SUBJECT);
    add(SmsDatabase.DATE_SENT);
    add(SmsDatabase.DATE_RECEIVED);
    add(SmsDatabase.STATUS);
  }};

  public MmsSmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  public Cursor getConversation(long threadId) {
    final String selection = MmsSmsColumns.THREAD_ID + " = " + threadId;
    final String query;
    if (queryCache.containsKey(threadId)) {
      query = queryCache.get(threadId);
    } else {
      query = getQuery(CONVERSATION_PROJECTION, selection, selection, CONVERSATION_ORDER, null, null);
      queryCache.put(threadId, query);
    }
    final Cursor cursor = rawQuery(query);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }

  public Cursor getIdentityConflictMessagesForThread(long threadId) {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.TYPE,
                              MmsSmsColumns.THREAD_ID,
                              SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY,
                              MmsDatabase.STATUS, MmsSmsColumns.RECEIPT_COUNT,
                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT};

    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";

    String selection       = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MmsSmsColumns.MISMATCHED_IDENTITIES + " IS NOT NULL";

    Cursor cursor = queryTables(projection, selection, selection, order, null, null);
    setNotifyConverationListeners(cursor, threadId);

    return cursor;
  }

  public Cursor getConversationSnippet(long threadId) {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.TYPE,
                              MmsSmsColumns.THREAD_ID,
                              SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY,
                              MmsDatabase.STATUS, MmsSmsColumns.RECEIPT_COUNT,
                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT};

    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
    String selection       = MmsSmsColumns.THREAD_ID + " = " + threadId;

    return  queryTables(projection, selection, selection, order, null, "1");
  }

  public Cursor getUnread() {
    String[] projection    = {MmsSmsColumns.ID, SmsDatabase.BODY, SmsDatabase.READ, SmsDatabase.TYPE,
                              SmsDatabase.ADDRESS, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT, MmsSmsColumns.THREAD_ID,
                              SmsDatabase.STATUS,
                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.MESSAGE_TYPE, MmsDatabase.MESSAGE_BOX,
                              MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY,
                              MmsDatabase.STATUS, MmsSmsColumns.RECEIPT_COUNT,
                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT};

    String order           = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";
    String selection       = MmsSmsColumns.READ + " = 0";

    return queryTables(projection, selection, selection, order, null, null);
  }

  public boolean isConversationEmpty(long threadId) {
    return !DatabaseFactory.getSmsDatabase(context).hasMessagesForThread(threadId) &&
           !DatabaseFactory.getMmsDatabase(context).hasMessagesForThread(threadId);
  }

  public void incrementDeliveryReceiptCount(String address, long timestamp) {
    DatabaseFactory.getSmsDatabase(context).incrementDeliveryReceiptCount(address, timestamp);
    DatabaseFactory.getMmsDatabase(context).incrementDeliveryReceiptCount(address, timestamp);
  }

  private Cursor rawQuery(final String query) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.rawQuery(query, null);
  }

  private Cursor queryTables(String[] projection, String smsSelection, String mmsSelection, String order, String groupBy, String limit) {
    return rawQuery(getQuery(projection, smsSelection, mmsSelection, order, groupBy, limit));
  }

  private String getQuery(String[] projection, String smsSelection, String mmsSelection, String order, String groupBy, String limit) {
    SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
    SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

    mmsQueryBuilder.setDistinct(false);
    smsQueryBuilder.setDistinct(false);

    mmsQueryBuilder.setTables(MmsDatabase.TABLE_NAME);
    smsQueryBuilder.setTables(SmsDatabase.TABLE_NAME);

    String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery(TRANSPORT, MMS_PROJECTION, MMS_COLUMNS_PRESENT, 2, MMS_TRANSPORT, mmsSelection, null, null, null);
    String smsSubQuery = smsQueryBuilder.buildUnionSubQuery(TRANSPORT, SMS_PROJECTION, SMS_COLUMNS_PRESENT, 2, SMS_TRANSPORT, smsSelection, null, null, null);

    SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();
    String unionQuery = unionQueryBuilder.buildUnionQuery(new String[] {smsSubQuery, mmsSubQuery}, order, null);

    SQLiteQueryBuilder outerQueryBuilder = new SQLiteQueryBuilder();
    outerQueryBuilder.setTables("(" + unionQuery + ")");

    return outerQueryBuilder.buildQuery(projection, null, null, groupBy, null, null, limit);
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

      if (MmsSmsDatabase.MMS_TRANSPORT.equals(type)) {
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

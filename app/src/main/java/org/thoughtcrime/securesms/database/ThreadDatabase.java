/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013-2017 Open Whisper Systems
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

import static org.session.libsession.utilities.GroupUtil.CLOSED_GROUP_PREFIX;
import static org.session.libsession.utilities.GroupUtil.OPEN_GROUP_PREFIX;
import static org.thoughtcrime.securesms.database.GroupDatabase.GROUP_ID;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import net.sqlcipher.database.SQLiteDatabase;

import org.jetbrains.annotations.NotNull;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.Contact;
import org.session.libsession.utilities.DelimiterUtil;
import org.session.libsession.utilities.DistributionTypes;
import org.session.libsession.utilities.GroupRecord;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.recipients.Recipient.RecipientSettings;
import org.session.libsignal.utilities.IdPrefix;
import org.session.libsignal.utilities.Log;
import org.session.libsignal.utilities.Pair;
import org.session.libsignal.utilities.guava.Optional;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.groups.OpenGroupMigrator;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.util.SessionMetaProtocol;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ThreadDatabase extends Database {

  private static final String TAG = ThreadDatabase.class.getSimpleName();

  private final Map<Long, Address> addressCache = new HashMap<>();

  public  static final String TABLE_NAME             = "thread";
  public  static final String ID                     = "_id";
  public  static final String DATE                   = "date";
  public  static final String MESSAGE_COUNT          = "message_count";
  public  static final String ADDRESS                = "recipient_ids";
  public  static final String SNIPPET                = "snippet";
  private static final String SNIPPET_CHARSET        = "snippet_cs";
  public  static final String READ                   = "read";
  public  static final String UNREAD_COUNT           = "unread_count";
  public  static final String TYPE                   = "type";
  private static final String ERROR                  = "error";
  public  static final String SNIPPET_TYPE           = "snippet_type";
  public  static final String SNIPPET_URI            = "snippet_uri";
  public  static final String ARCHIVED               = "archived";
  public  static final String STATUS                 = "status";
  public  static final String DELIVERY_RECEIPT_COUNT = "delivery_receipt_count";
  public  static final String READ_RECEIPT_COUNT     = "read_receipt_count";
  public  static final String EXPIRES_IN             = "expires_in";
  public  static final String LAST_SEEN              = "last_seen";
  public static final String HAS_SENT               = "has_sent";
  public  static final String IS_PINNED              = "is_pinned";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("                    +
    ID + " INTEGER PRIMARY KEY, " + DATE + " INTEGER DEFAULT 0, "                                  +
    MESSAGE_COUNT + " INTEGER DEFAULT 0, " + ADDRESS + " TEXT, " + SNIPPET + " TEXT, "             +
    SNIPPET_CHARSET + " INTEGER DEFAULT 0, " + READ + " INTEGER DEFAULT 1, "                       +
    TYPE + " INTEGER DEFAULT 0, " + ERROR + " INTEGER DEFAULT 0, "                                 +
    SNIPPET_TYPE + " INTEGER DEFAULT 0, " + SNIPPET_URI + " TEXT DEFAULT NULL, "                   +
    ARCHIVED + " INTEGER DEFAULT 0, " + STATUS + " INTEGER DEFAULT 0, "                            +
    DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + EXPIRES_IN + " INTEGER DEFAULT 0, "          +
    LAST_SEEN + " INTEGER DEFAULT 0, " + HAS_SENT + " INTEGER DEFAULT 0, "                         +
    READ_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + UNREAD_COUNT + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON " + TABLE_NAME + " (" + ADDRESS + ");",
    "CREATE INDEX IF NOT EXISTS archived_count_index ON " + TABLE_NAME + " (" + ARCHIVED + ", " + MESSAGE_COUNT + ");",
  };

  private static final String[] THREAD_PROJECTION = {
      ID, DATE, MESSAGE_COUNT, ADDRESS, SNIPPET, SNIPPET_CHARSET, READ, UNREAD_COUNT, TYPE, ERROR, SNIPPET_TYPE,
      SNIPPET_URI, ARCHIVED, STATUS, DELIVERY_RECEIPT_COUNT, EXPIRES_IN, LAST_SEEN, READ_RECEIPT_COUNT, IS_PINNED
  };

  private static final List<String> TYPED_THREAD_PROJECTION = Stream.of(THREAD_PROJECTION)
                                                                    .map(columnName -> TABLE_NAME + "." + columnName)
                                                                    .toList();

  private static final List<String> COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION = Stream.concat(Stream.concat(Stream.of(TYPED_THREAD_PROJECTION),
                                                                                                             Stream.of(RecipientDatabase.TYPED_RECIPIENT_PROJECTION)),
                                                                                               Stream.of(GroupDatabase.TYPED_GROUP_PROJECTION))
                                                                                       .toList();

  public static String getCreatePinnedCommand() {
    return "ALTER TABLE "+ TABLE_NAME + " " +
            "ADD COLUMN " + IS_PINNED + " INTEGER DEFAULT 0;";
  }

  public ThreadDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  private long createThreadForRecipient(Address address, boolean group, int distributionType) {
    ContentValues contentValues = new ContentValues(4);
    long date                   = System.currentTimeMillis();

    contentValues.put(DATE, date - date % 1000);
    contentValues.put(ADDRESS, address.serialize());

    if (group)
      contentValues.put(TYPE, distributionType);

    contentValues.put(MESSAGE_COUNT, 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    return db.insert(TABLE_NAME, null, contentValues);
  }

  private void updateThread(long threadId, long count, String body, @Nullable Uri attachment,
                            long date, int status, int deliveryReceiptCount, long type, boolean unarchive,
                            long expiresIn, int readReceiptCount)
  {
    ContentValues contentValues = new ContentValues(7);
    contentValues.put(DATE, date - date % 1000);
    contentValues.put(MESSAGE_COUNT, count);
    if (!body.isEmpty()) {
      contentValues.put(SNIPPET, body);
    }
    contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());
    contentValues.put(SNIPPET_TYPE, type);
    contentValues.put(STATUS, status);
    contentValues.put(DELIVERY_RECEIPT_COUNT, deliveryReceiptCount);
    contentValues.put(READ_RECEIPT_COUNT, readReceiptCount);
    contentValues.put(EXPIRES_IN, expiresIn);

    if (unarchive) {
      contentValues.put(ARCHIVED, 0);
    }

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public void updateSnippet(long threadId, String snippet, @Nullable Uri attachment, long date, long type, boolean unarchive) {
    ContentValues contentValues = new ContentValues(4);

    contentValues.put(DATE, date - date % 1000);
    if (!snippet.isEmpty()) {
      contentValues.put(SNIPPET, snippet);
    }
    contentValues.put(SNIPPET_TYPE, type);
    contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());

    if (unarchive) {
      contentValues.put(ARCHIVED, 0);
    }

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  private void deleteThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, ID_WHERE, new String[] {threadId + ""});
    addressCache.remove(threadId);
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
    for (long threadId: threadIds) {
      addressCache.remove(threadId);
    }
    notifyConversationListListeners();
  }

  private void deleteAllThreads() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
    addressCache.clear();
    notifyConversationListListeners();
  }

  public void trimAllThreads(int length, ProgressListener listener) {
    Cursor cursor   = null;
    int threadCount = 0;
    int complete    = 0;

    try {
      cursor = this.getConversationList();

      if (cursor != null)
        threadCount = cursor.getCount();

      while (cursor != null && cursor.moveToNext()) {
        long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        trimThread(threadId, length);

        listener.onProgress(++complete, threadCount);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void trimThread(long threadId, int length) {
    Log.i("ThreadDatabase", "Trimming thread: " + threadId + " to: " + length);
    Cursor cursor = null;

    try {
      cursor = DatabaseComponent.get(context).mmsSmsDatabase().getConversation(threadId, true);

      if (cursor != null && length > 0 && cursor.getCount() > length) {
        Log.w("ThreadDatabase", "Cursor count is greater than length!");
        cursor.moveToPosition(length - 1);

        long lastTweetDate = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.NORMALIZED_DATE_RECEIVED));

        Log.i("ThreadDatabase", "Cut off tweet date: " + lastTweetDate);

        DatabaseComponent.get(context).smsDatabase().deleteMessagesInThreadBeforeDate(threadId, lastTweetDate);
        DatabaseComponent.get(context).mmsDatabase().deleteMessagesInThreadBeforeDate(threadId, lastTweetDate);

        update(threadId, false);
        notifyConversationListeners(threadId);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public void trimThreadBefore(long threadId, long timestamp) {
    Log.i("ThreadDatabase", "Trimming thread: " + threadId + " before :"+timestamp);
    DatabaseComponent.get(context).smsDatabase().deleteMessagesInThreadBeforeDate(threadId, timestamp);
    DatabaseComponent.get(context).mmsDatabase().deleteMessagesInThreadBeforeDate(threadId, timestamp);
    update(threadId, false);
    notifyConversationListeners(threadId);
  }

  public List<MarkedMessageInfo> setRead(long threadId, boolean lastSeen) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, 1);
    contentValues.put(UNREAD_COUNT, 0);

    if (lastSeen) {
      contentValues.put(LAST_SEEN, System.currentTimeMillis());
    }

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId+""});

    final List<MarkedMessageInfo> smsRecords = DatabaseComponent.get(context).smsDatabase().setMessagesRead(threadId);
    final List<MarkedMessageInfo> mmsRecords = DatabaseComponent.get(context).mmsDatabase().setMessagesRead(threadId);

    notifyConversationListListeners();

    return new LinkedList<MarkedMessageInfo>() {{
      addAll(smsRecords);
      addAll(mmsRecords);
    }};
  }

  public void incrementUnread(long threadId, int amount) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + READ + " = 0, " +
                   UNREAD_COUNT + " = " + UNREAD_COUNT + " + ? WHERE " + ID + " = ?",
               new String[] {String.valueOf(amount),
                             String.valueOf(threadId)});
  }

  public void decrementUnread(long threadId, int amount) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + READ + " = 0, " +
                    UNREAD_COUNT + " = " + UNREAD_COUNT + " - ? WHERE " + ID + " = ? AND " + UNREAD_COUNT + " > 0",
            new String[] {String.valueOf(amount),
                    String.valueOf(threadId)});
  }

  public void setDistributionType(long threadId, int distributionType) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(TYPE, distributionType);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public int getDistributionType(long threadId) {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    Cursor         cursor = db.query(TABLE_NAME, new String[]{TYPE}, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null);

    try {
      if (cursor != null && cursor.moveToNext()) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
      }

      return DistributionTypes.DEFAULT;
    } finally {
      if (cursor != null) cursor.close();
    }

  }

  public Cursor searchConversationAddresses(String addressQuery) {
    if (addressQuery == null || addressQuery.isEmpty()) {
      return null;
    }

    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    String   selection      = TABLE_NAME + "." + ADDRESS + " LIKE ? AND " + TABLE_NAME + "." + MESSAGE_COUNT + " != 0";
    String[] selectionArgs  = new String[]{addressQuery+"%"};
    String query = createQuery(selection, 0);
    Cursor cursor = db.rawQuery(query, selectionArgs);
    return cursor;
  }

  public Cursor getFilteredConversationList(@Nullable List<Address> filter) {
    if (filter == null || filter.size() == 0)
      return null;

    SQLiteDatabase      db                   = databaseHelper.getReadableDatabase();
    List<List<Address>> partitionedAddresses = Util.partition(filter, 900);
    List<Cursor>        cursors              = new LinkedList<>();

    for (List<Address> addresses : partitionedAddresses) {
      String   selection      = TABLE_NAME + "." + ADDRESS + " = ?";
      String[] selectionArgs  = new String[addresses.size()];

      for (int i=0;i<addresses.size()-1;i++)
        selection += (" OR " + TABLE_NAME + "." + ADDRESS + " = ?");

      int i= 0;
      for (Address address : addresses) {
        selectionArgs[i++] = DelimiterUtil.escape(address.serialize(), ' ');
      }

      String query = createQuery(selection, 0);
      cursors.add(db.rawQuery(query, selectionArgs));
    }

    Cursor cursor = cursors.size() > 1 ? new MergeCursor(cursors.toArray(new Cursor[cursors.size()])) : cursors.get(0);
    setNotifyConverationListListeners(cursor);
    return cursor;
  }

  public Cursor getRecentConversationList(int limit) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = createQuery(MESSAGE_COUNT + " != 0", limit);

    return db.rawQuery(query, null);
  }

  public int getUnapprovedConversationCount() {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      String query    = "SELECT COUNT (*) FROM " + TABLE_NAME +
              " LEFT OUTER JOIN " + RecipientDatabase.TABLE_NAME +
              " ON " + TABLE_NAME + "." + ADDRESS + " = " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.ADDRESS +
              " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME +
              " ON " + TABLE_NAME + "." + ADDRESS + " = " + GroupDatabase.TABLE_NAME + "." + GROUP_ID +
              " WHERE " + MESSAGE_COUNT + " != 0 AND " + ARCHIVED + " = 0 AND " + HAS_SENT + " = 0 AND " + MESSAGE_COUNT + " = " + UNREAD_COUNT + " AND " +
              RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.BLOCK + " = 0 AND " +
              RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.APPROVED + " = 0 AND " +
              GroupDatabase.TABLE_NAME + "." + GROUP_ID + " IS NULL";
      cursor          = db.rawQuery(query, null);

      if (cursor != null && cursor.moveToFirst())
        return cursor.getInt(0);
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return 0;
  }

  public long getLatestUnapprovedConversationTimestamp() {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      String where    = "SELECT " + DATE + " FROM " + TABLE_NAME +
              " LEFT OUTER JOIN " + RecipientDatabase.TABLE_NAME +
              " ON " + TABLE_NAME + "." + ADDRESS + " = " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.ADDRESS +
              " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME +
              " ON " + TABLE_NAME + "." + ADDRESS + " = " + GroupDatabase.TABLE_NAME + "." + GROUP_ID +
              " WHERE " + MESSAGE_COUNT + " != 0 AND " + ARCHIVED + " = 0 AND " + HAS_SENT + " = 0 AND " +
              RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.BLOCK + " = 0 AND " +
              RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.APPROVED + " = 0 AND " +
              GroupDatabase.TABLE_NAME + "." + GROUP_ID + " IS NULL ORDER BY " + DATE + " DESC LIMIT 1";
      cursor          = db.rawQuery(where, null);

      if (cursor != null && cursor.moveToFirst())
        return cursor.getLong(0);
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return 0;
  }

  public Cursor getConversationList() {
    String where  = "(" + MESSAGE_COUNT + " != 0 OR " + GroupDatabase.TABLE_NAME + "." + GROUP_ID + " LIKE '" + OPEN_GROUP_PREFIX + "%') " +
            "AND " + ARCHIVED + " = 0 ";
    return getConversationList(where);
  }

  public Cursor getBlindedConversationList() {
    String where  = TABLE_NAME + "." + ADDRESS + " LIKE '" + IdPrefix.BLINDED.getValue() + "%' ";
    return getConversationList(where);
  }

  public Cursor getApprovedConversationList() {
    String where  = "((" + MESSAGE_COUNT + " != 0 AND (" + HAS_SENT + " = 1 OR " + RecipientDatabase.APPROVED + " = 1 OR "+ GroupDatabase.TABLE_NAME +"."+GROUP_ID+" LIKE '"+CLOSED_GROUP_PREFIX+"%')) OR " + GroupDatabase.TABLE_NAME + "." + GROUP_ID + " LIKE '" + OPEN_GROUP_PREFIX + "%') " +
            "AND " + ARCHIVED + " = 0 ";
    return getConversationList(where);
  }

  public Cursor getUnapprovedConversationList() {
    String where  = MESSAGE_COUNT + " != 0 AND " + ARCHIVED + " = 0 AND " + HAS_SENT + " = 0 AND " +
            RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.APPROVED + " = 0 AND " +
            RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.BLOCK + " = 0 AND " +
            GroupDatabase.TABLE_NAME + "." + GROUP_ID + " IS NULL";
    return getConversationList(where);
  }

  public Cursor getArchivedConversationList() {
    String where  = "(" + MESSAGE_COUNT + " != 0 OR " + GroupDatabase.TABLE_NAME + "." + GROUP_ID + " LIKE '" + OPEN_GROUP_PREFIX + "%') " +
            "AND " + ARCHIVED + " = 1 ";
    return getConversationList(where);
  }

  private Cursor getConversationList(String where) {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    String         query  = createQuery(where, 0);
    Cursor         cursor = db.rawQuery(query, null);

    setNotifyConverationListListeners(cursor);

    return cursor;
  }

  public Cursor getDirectShareList() {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = createQuery(MESSAGE_COUNT + " != 0", 0);

    return db.rawQuery(query, null);
  }

  public void setLastSeen(long threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(LAST_SEEN, System.currentTimeMillis());

    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(threadId)});
    notifyConversationListListeners();
  }

  public Pair<Long, Boolean> getLastSeenAndHasSent(long threadId) {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    Cursor         cursor = db.query(TABLE_NAME, new String[]{LAST_SEEN, HAS_SENT}, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        return new Pair<>(cursor.getLong(0), cursor.getLong(1) == 1);
      }

      return new Pair<>(-1L, false);
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  public Long getLastUpdated(long threadId) {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    Cursor         cursor = db.query(TABLE_NAME, new String[]{DATE}, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(0);
      }

      return -1L;
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  public int getMessageCount(long threadId) {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    String[]       columns = new String[]{MESSAGE_COUNT};
    String[]       args    = new String[]{String.valueOf(threadId)};
    try (Cursor cursor = db.query(TABLE_NAME, columns, ID_WHERE, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }

      return 0;
    }
  }

  public void deleteConversation(long threadId) {
    DatabaseComponent.get(context).smsDatabase().deleteThread(threadId);
    DatabaseComponent.get(context).mmsDatabase().deleteThread(threadId);
    DatabaseComponent.get(context).draftDatabase().clearDrafts(threadId);
    DatabaseComponent.get(context).lokiMessageDatabase().deleteThread(threadId);
    deleteThread(threadId);
    notifyConversationListeners(threadId);
    notifyConversationListListeners();
    SessionMetaProtocol.clearReceivedMessages();
  }

  public long getThreadIdIfExistsFor(String address) {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    String where           = ADDRESS + " = ?";
    String[] recipientsArg = new String[] {address};
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

  public long getThreadIdIfExistsFor(Recipient recipient) {
    return getThreadIdIfExistsFor(recipient.getAddress().serialize());
  }

  public long getOrCreateThreadIdFor(Recipient recipient) {
    return getOrCreateThreadIdFor(recipient, DistributionTypes.DEFAULT);
  }

  public void setThreadArchived(long threadId) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(ARCHIVED, 1);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE,
            new String[] {String.valueOf(threadId)});

    notifyConversationListListeners();
    notifyConversationListeners(threadId);
  }

  public long getOrCreateThreadIdFor(Recipient recipient, int distributionType) {
    SQLiteDatabase db            = databaseHelper.getReadableDatabase();
    String         where         = ADDRESS + " = ?";
    String[]       recipientsArg = new String[]{recipient.getAddress().serialize()};
    Cursor         cursor        = null;

    try {
      cursor = db.query(TABLE_NAME, new String[]{ID}, where, recipientsArg, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      } else {
        DatabaseComponent.get(context).recipientDatabase().setProfileSharing(recipient, true);
        return createThreadForRecipient(recipient.getAddress(), recipient.isGroupRecipient(), distributionType);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public @Nullable Recipient getRecipientForThreadId(long threadId) {
    if (addressCache.containsKey(threadId) && addressCache.get(threadId) != null) {
      return Recipient.from(context, addressCache.get(threadId), false);
    }

    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, null, ID + " = ?", new String[] {threadId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        Address address = Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS)));
        addressCache.put(threadId, address);
        return Recipient.from(context, address, false);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  public void setHasSent(long threadId, boolean hasSent) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(HAS_SENT, hasSent ? 1 : 0);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE,
                                                new String[] {String.valueOf(threadId)});

    notifyConversationListeners(threadId);
  }

  public boolean update(long threadId, boolean unarchive) {
    MmsSmsDatabase mmsSmsDatabase = DatabaseComponent.get(context).mmsSmsDatabase();
    long count                    = mmsSmsDatabase.getConversationCount(threadId);

    boolean shouldDeleteEmptyThread = deleteThreadOnEmpty(threadId);

    if (count == 0 && shouldDeleteEmptyThread) {
      deleteThread(threadId);
      notifyConversationListListeners();
      return true;
    }

    MmsSmsDatabase.Reader reader = null;

    try {
      reader = mmsSmsDatabase.readerFor(mmsSmsDatabase.getConversationSnippet(threadId));
      MessageRecord record = null;
      if (reader != null) {
        record = reader.getNext();
        while (record != null && record.isDeleted()) {
          record = reader.getNext();
        }
      }
      if (record != null && !record.isDeleted()) {
        updateThread(threadId, count, getFormattedBodyFor(record), getAttachmentUriFor(record),
                     record.getTimestamp(), record.getDeliveryStatus(), record.getDeliveryReceiptCount(),
                     record.getType(), unarchive, record.getExpiresIn(), record.getReadReceiptCount());
        notifyConversationListListeners();
        return false;
      } else {
        if (shouldDeleteEmptyThread) {
          deleteThread(threadId);
          notifyConversationListListeners();
          return true;
        }
        return false;
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  public void setPinned(long threadId, boolean pinned) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(IS_PINNED, pinned ? 1 : 0);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE,
            new String[] {String.valueOf(threadId)});

    notifyConversationListeners(threadId);
  }

  public void markAllAsRead(long threadId, boolean isGroupRecipient) {
    List<MarkedMessageInfo> messages = setRead(threadId, true);
    if (isGroupRecipient) {
      for (MarkedMessageInfo message: messages) {
        MarkReadReceiver.scheduleDeletion(context, message.getExpirationInfo());
      }
    } else {
      MarkReadReceiver.process(context, messages);
    }
    ApplicationContext.getInstance(context).messageNotifier.updateNotification(context, false, 0);
  }

  private boolean deleteThreadOnEmpty(long threadId) {
    Recipient threadRecipient = getRecipientForThreadId(threadId);
    return threadRecipient != null && !threadRecipient.isOpenGroupRecipient();
  }

  private @NonNull String getFormattedBodyFor(@NonNull MessageRecord messageRecord) {
    if (messageRecord.isMms()) {
      MmsMessageRecord record = (MmsMessageRecord) messageRecord;
      if (record.getSharedContacts().size() > 0) {
        Contact contact = ((MmsMessageRecord) messageRecord).getSharedContacts().get(0);
        return ContactUtil.getStringSummary(context, contact).toString();
      }
      String attachmentString = record.getSlideDeck().getBody();
      if (!attachmentString.isEmpty()) {
        if (!messageRecord.getBody().isEmpty()) {
          attachmentString = attachmentString + ": " + messageRecord.getBody();
        }
        return attachmentString;
      }
    }
    return messageRecord.getBody();
  }

  private @Nullable Uri getAttachmentUriFor(MessageRecord record) {
    if (!record.isMms() || record.isMmsNotification()) return null;

    SlideDeck slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
    Slide     thumbnail = slideDeck.getThumbnailSlide();

    if (thumbnail != null) {
      return thumbnail.getThumbnailUri();
    }

    return null;
  }

  private @NonNull String createQuery(@NonNull String where, int limit) {
    String projection = Util.join(COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION, ",");
    String query =
    "SELECT " + projection + " FROM " + TABLE_NAME +
           " LEFT OUTER JOIN " + RecipientDatabase.TABLE_NAME +
           " ON " + TABLE_NAME + "." + ADDRESS + " = " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.ADDRESS +
           " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME +
           " ON " + TABLE_NAME + "." + ADDRESS + " = " + GroupDatabase.TABLE_NAME + "." + GROUP_ID +
           " WHERE " + where +
           " ORDER BY " + TABLE_NAME + "." + IS_PINNED + " DESC, " + TABLE_NAME + "." + DATE + " DESC";

    if (limit >  0) {
      query += " LIMIT " + limit;
    }

    return query;
  }

  @NotNull
  public List<ThreadRecord> getHttpOxenOpenGroups() {
    String where = TABLE_NAME+"."+ADDRESS+" LIKE ?";
    String selection = OpenGroupMigrator.HTTP_PREFIX+OpenGroupMigrator.OPEN_GET_SESSION_TRAILING_DOT_ENCODED +"%";
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    String         query  = createQuery(where, 0);
    Cursor         cursor = db.rawQuery(query, new String[]{selection});

    if (cursor == null) {
      return Collections.emptyList();
    }
    List<ThreadRecord> threads = new ArrayList<>();
    try {
      Reader reader = readerFor(cursor);
      ThreadRecord record;
      while ((record = reader.getNext()) != null) {
        threads.add(record);
      }
    } finally {
      cursor.close();
    }
    return threads;
  }

  @NotNull
  public List<ThreadRecord> getLegacyOxenOpenGroups() {
    String where = TABLE_NAME+"."+ADDRESS+" LIKE ?";
    String selection = OpenGroupMigrator.LEGACY_GROUP_ENCODED_ID+"%";
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    String         query  = createQuery(where, 0);
    Cursor         cursor = db.rawQuery(query, new String[]{selection});

    if (cursor == null) {
      return Collections.emptyList();
    }
    List<ThreadRecord> threads = new ArrayList<>();
    try {
      Reader reader = readerFor(cursor);
      ThreadRecord record;
      while ((record = reader.getNext()) != null) {
        threads.add(record);
      }
    } finally {
      cursor.close();
    }
    return threads;
  }

  @NotNull
  public List<ThreadRecord> getHttpsOxenOpenGroups() {
    String where = TABLE_NAME+"."+ADDRESS+" LIKE ?";
    String selection = OpenGroupMigrator.NEW_GROUP_ENCODED_ID+"%";
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    String         query  = createQuery(where, 0);
    Cursor         cursor = db.rawQuery(query, new String[]{selection});
    if (cursor == null) {
      return Collections.emptyList();
    }
    List<ThreadRecord> threads = new ArrayList<>();
    try {
      Reader reader = readerFor(cursor);
      ThreadRecord record;
      while ((record = reader.getNext()) != null) {
        threads.add(record);
      }
    } finally {
      cursor.close();
    }
    return threads;
  }

  public void migrateEncodedGroup(long threadId, @NotNull String newEncodedGroupId) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(ADDRESS, newEncodedGroupId);
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId+""});
  }

  public void notifyThreadUpdated(long threadId) {
    notifyConversationListeners(threadId);
  }

  public interface ProgressListener {
    void onProgress(int complete, int total);
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public class Reader implements Closeable {

    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public ThreadRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public ThreadRecord getCurrent() {
      long    threadId         = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
      int     distributionType = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE));
      Address address          = Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.ADDRESS)));

      Optional<RecipientSettings> settings;
      Optional<GroupRecord>       groupRecord;

      if (distributionType != DistributionTypes.ARCHIVE && distributionType != DistributionTypes.INBOX_ZERO) {
        settings    = DatabaseComponent.get(context).recipientDatabase().getRecipientSettings(cursor);
        groupRecord = DatabaseComponent.get(context).groupDatabase().getGroup(cursor);
      } else {
        settings    = Optional.absent();
        groupRecord = Optional.absent();
      }

      Recipient          recipient            = Recipient.from(context, address, settings, groupRecord, true);
      String             body                 = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET));
      long               date                 = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE));
      long               count                = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT));
      int                unreadCount          = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.UNREAD_COUNT));
      long               type                 = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
      boolean            archived             = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.ARCHIVED)) != 0;
      int                status               = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.STATUS));
      int                deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.DELIVERY_RECEIPT_COUNT));
      int                readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.READ_RECEIPT_COUNT));
      long               expiresIn            = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.EXPIRES_IN));
      long               lastSeen             = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.LAST_SEEN));
      Uri                snippetUri           = getSnippetUri(cursor);
      boolean            pinned              = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.IS_PINNED)) != 0;

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount = 0;
      }

      return new ThreadRecord(body, snippetUri, recipient, date, count,
                              unreadCount, threadId, deliveryReceiptCount, status, type,
                              distributionType, archived, expiresIn, lastSeen, readReceiptCount, pinned);
    }

    private @Nullable Uri getSnippetUri(Cursor cursor) {
      if (cursor.isNull(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_URI))) {
        return null;
      }

      try {
        return Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_URI)));
      } catch (IllegalArgumentException e) {
        Log.w(TAG, e);
        return null;
      }
    }

    @Override
    public void close() {
      if (cursor != null) {
        cursor.close();
      }
    }
  }
}

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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.fasterxml.jackson.annotation.JsonProperty;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ThreadDatabase extends Database {

  private static final String TAG = ThreadDatabase.class.getSimpleName();

  public  static final String TABLE_NAME             = "thread";
  public  static final String ID                     = "_id";
  public  static final String DATE                   = "date";
  public  static final String MESSAGE_COUNT          = "message_count";
  public  static final String RECIPIENT_ID           = "recipient_ids";
  public  static final String SNIPPET                = "snippet";
  private static final String SNIPPET_CHARSET        = "snippet_cs";
  public  static final String READ                   = "read";
  public  static final String UNREAD_COUNT           = "unread_count";
  public  static final String TYPE                   = "type";
  private static final String ERROR                  = "error";
  public  static final String SNIPPET_TYPE           = "snippet_type";
  public  static final String SNIPPET_URI            = "snippet_uri";
  public  static final String SNIPPET_CONTENT_TYPE   = "snippet_content_type";
  public  static final String SNIPPET_EXTRAS         = "snippet_extras";
  public  static final String ARCHIVED               = "archived";
  public  static final String STATUS                 = "status";
  public  static final String DELIVERY_RECEIPT_COUNT = "delivery_receipt_count";
  public  static final String READ_RECEIPT_COUNT     = "read_receipt_count";
  public  static final String EXPIRES_IN             = "expires_in";
  public  static final String LAST_SEEN              = "last_seen";
  public  static final String HAS_SENT               = "has_sent";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("                    +
    ID + " INTEGER PRIMARY KEY, " + DATE + " INTEGER DEFAULT 0, "                                  +
    MESSAGE_COUNT + " INTEGER DEFAULT 0, " + RECIPIENT_ID + " INTEGER, " + SNIPPET + " TEXT, "             +
    SNIPPET_CHARSET + " INTEGER DEFAULT 0, " + READ + " INTEGER DEFAULT 1, "                       +
    TYPE + " INTEGER DEFAULT 0, " + ERROR + " INTEGER DEFAULT 0, "                                 +
    SNIPPET_TYPE + " INTEGER DEFAULT 0, " + SNIPPET_URI + " TEXT DEFAULT NULL, "                   +
    SNIPPET_CONTENT_TYPE + " TEXT DEFAULT NULL, " + SNIPPET_EXTRAS + " TEXT DEFAULT NULL, "      +
    ARCHIVED + " INTEGER DEFAULT 0, " + STATUS + " INTEGER DEFAULT 0, "                            +
    DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + EXPIRES_IN + " INTEGER DEFAULT 0, "          +
    LAST_SEEN + " INTEGER DEFAULT 0, " + HAS_SENT + " INTEGER DEFAULT 0, "                         +
    READ_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + UNREAD_COUNT + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON " + TABLE_NAME + " (" + RECIPIENT_ID + ");",
    "CREATE INDEX IF NOT EXISTS archived_count_index ON " + TABLE_NAME + " (" + ARCHIVED + ", " + MESSAGE_COUNT + ");",
  };

  private static final String[] THREAD_PROJECTION = {
      ID, DATE, MESSAGE_COUNT, RECIPIENT_ID, SNIPPET, SNIPPET_CHARSET, READ, UNREAD_COUNT, TYPE, ERROR, SNIPPET_TYPE,
      SNIPPET_URI, SNIPPET_CONTENT_TYPE, SNIPPET_EXTRAS, ARCHIVED, STATUS, DELIVERY_RECEIPT_COUNT, EXPIRES_IN, LAST_SEEN, READ_RECEIPT_COUNT
  };

  private static final List<String> TYPED_THREAD_PROJECTION = Stream.of(THREAD_PROJECTION)
                                                                    .map(columnName -> TABLE_NAME + "." + columnName)
                                                                    .toList();

  private static final List<String> COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION = Stream.concat(Stream.concat(Stream.of(TYPED_THREAD_PROJECTION),
                                                                                                             Stream.of(RecipientDatabase.TYPED_RECIPIENT_PROJECTION)),
                                                                                               Stream.of(GroupDatabase.TYPED_GROUP_PROJECTION))
                                                                                       .toList();

  public ThreadDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  private long createThreadForRecipient(@NonNull RecipientId recipientId, boolean group, int distributionType) {
    if (recipientId.isUnknown()) {
      throw new AssertionError("Cannot create a thread for an unknown recipient!");
    }

    ContentValues contentValues = new ContentValues(4);
    long date                   = System.currentTimeMillis();

    contentValues.put(DATE, date - date % 1000);
    contentValues.put(RECIPIENT_ID, recipientId.serialize());

    if (group)
      contentValues.put(TYPE, distributionType);

    contentValues.put(MESSAGE_COUNT, 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    return db.insert(TABLE_NAME, null, contentValues);
  }

  private void updateThread(long threadId, long count, String body, @Nullable Uri attachment,
                            @Nullable String contentType, @Nullable Extra extra,
                            long date, int status, int deliveryReceiptCount, long type, boolean unarchive,
                            long expiresIn, int readReceiptCount)
  {
    String extraSerialized = null;

    if (extra != null) {
      try {
        extraSerialized = JsonUtils.toJson(extra);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    ContentValues contentValues = new ContentValues(7);
    contentValues.put(DATE, date - date % 1000);
    contentValues.put(MESSAGE_COUNT, count);
    contentValues.put(SNIPPET, body);
    contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());
    contentValues.put(SNIPPET_TYPE, type);
    contentValues.put(SNIPPET_CONTENT_TYPE, contentType);
    contentValues.put(SNIPPET_EXTRAS, extraSerialized);
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
    contentValues.put(SNIPPET, snippet);
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
      cursor = DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId);

      if (cursor != null && length > 0 && cursor.getCount() > length) {
        Log.w("ThreadDatabase", "Cursor count is greater than length!");
        cursor.moveToPosition(length - 1);

        long lastTweetDate = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.NORMALIZED_DATE_RECEIVED));

        Log.i("ThreadDatabase", "Cut off tweet date: " + lastTweetDate);

        DatabaseFactory.getSmsDatabase(context).deleteMessagesInThreadBeforeDate(threadId, lastTweetDate);
        DatabaseFactory.getMmsDatabase(context).deleteMessagesInThreadBeforeDate(threadId, lastTweetDate);

        update(threadId, false);
        notifyConversationListeners(threadId);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public List<MarkedMessageInfo> setAllThreadsRead() {
    SQLiteDatabase db           = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, 1);
    contentValues.put(UNREAD_COUNT, 0);

    db.update(TABLE_NAME, contentValues, null, null);

    final List<MarkedMessageInfo> smsRecords = DatabaseFactory.getSmsDatabase(context).setAllMessagesRead();
    final List<MarkedMessageInfo> mmsRecords = DatabaseFactory.getMmsDatabase(context).setAllMessagesRead();

    DatabaseFactory.getSmsDatabase(context).setAllReactionsSeen();
    DatabaseFactory.getMmsDatabase(context).setAllReactionsSeen();

    notifyConversationListListeners();

    return Util.concatenatedList(smsRecords, mmsRecords);
  }

  public boolean hasCalledSince(@NonNull Recipient recipient, long timestamp) {
    return DatabaseFactory.getMmsSmsDatabase(context).hasReceivedAnyCallsSince(getThreadIdFor(recipient), timestamp);
  }

  public List<MarkedMessageInfo> setEntireThreadRead(long threadId) {
    setRead(threadId, false);

    final List<MarkedMessageInfo> smsRecords = DatabaseFactory.getSmsDatabase(context).setEntireThreadRead(threadId);
    final List<MarkedMessageInfo> mmsRecords = DatabaseFactory.getMmsDatabase(context).setEntireThreadRead(threadId);

    return Util.concatenatedList(smsRecords, mmsRecords);
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

    final List<MarkedMessageInfo> smsRecords = DatabaseFactory.getSmsDatabase(context).setMessagesRead(threadId);
    final List<MarkedMessageInfo> mmsRecords = DatabaseFactory.getMmsDatabase(context).setMessagesRead(threadId);

    DatabaseFactory.getSmsDatabase(context).setReactionsSeen(threadId);
    DatabaseFactory.getMmsDatabase(context).setReactionsSeen(threadId);

    notifyConversationListListeners();

    return Util.concatenatedList(smsRecords, mmsRecords);
  }

  public void incrementUnread(long threadId, int amount) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + READ + " = 0, " +
                   UNREAD_COUNT + " = " + UNREAD_COUNT + " + ? WHERE " + ID + " = ?",
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

  public Cursor getFilteredConversationList(@Nullable List<RecipientId> filter) {
    if (filter == null || filter.size() == 0)
      return null;

    SQLiteDatabase          db                = databaseHelper.getReadableDatabase();
    List<List<RecipientId>> splitRecipientIds = Util.partition(filter, 900);
    List<Cursor>            cursors           = new LinkedList<>();

    for (List<RecipientId> recipientIds : splitRecipientIds) {
      String   selection      = TABLE_NAME + "." + RECIPIENT_ID + " = ?";
      String[] selectionArgs  = new String[recipientIds.size()];

      for (int i=0;i<recipientIds.size()-1;i++)
        selection += (" OR " + TABLE_NAME + "." + RECIPIENT_ID + " = ?");

      int i= 0;
      for (RecipientId recipientId : recipientIds) {
        selectionArgs[i++] = recipientId.serialize();
      }

      String query = createQuery(selection, 0);
      cursors.add(db.rawQuery(query, selectionArgs));
    }

    Cursor cursor = cursors.size() > 1 ? new MergeCursor(cursors.toArray(new Cursor[cursors.size()])) : cursors.get(0);
    setNotifyConverationListListeners(cursor);
    return cursor;
  }

  public Cursor getRecentConversationList(int limit, boolean includeInactiveGroups) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = !includeInactiveGroups ? MESSAGE_COUNT + " != 0 AND (" + GroupDatabase.TABLE_NAME + "." + GroupDatabase.ACTIVE + " IS NULL OR " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.ACTIVE + " = 1)"
                                                  : MESSAGE_COUNT + " != 0";
    return db.rawQuery(createQuery(query, limit), null);
  }

  public Cursor getRecentPushConversationList(int limit, boolean includeInactiveGroups) {
    SQLiteDatabase db               = databaseHelper.getReadableDatabase();
    String         activeGroupQuery = !includeInactiveGroups ? " AND " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.ACTIVE + " = 1" : "";
    String         where            = MESSAGE_COUNT + " != 0 AND " +
                                      "(" +
                                        RecipientDatabase.REGISTERED + " = " + RecipientDatabase.RegisteredState.REGISTERED.getId() + " OR " +
                                        "(" +
                                          GroupDatabase.TABLE_NAME + "." + GroupDatabase.GROUP_ID + " NOT NULL AND " +
                                          GroupDatabase.TABLE_NAME + "." + GroupDatabase.MMS + " = 0" +
                                          activeGroupQuery +
                                        ")" +
                                      ")";
    String         query = createQuery(where, limit);

    return db.rawQuery(query, null);
  }

  public Cursor getConversationList() {
    return getConversationList("0");
  }

  public Cursor getArchivedConversationList() {
    return getConversationList("1");
  }

  public boolean isArchived(@NonNull RecipientId recipientId) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = RECIPIENT_ID + " = ?";
    String[]       args  = new String[]{ recipientId.serialize() };

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { ARCHIVED }, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(ARCHIVED)) == 1;
      }
    }

    return false;
  }

  public void setArchived(@NonNull RecipientId recipientId, boolean status) {
    setArchived(Collections.singletonMap(recipientId, status));
  }

  public void setArchived(@NonNull Map<RecipientId, Boolean> status) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();

    db.beginTransaction();
    try {
      String query = RECIPIENT_ID + " = ?";

      for (Map.Entry<RecipientId, Boolean> entry : status.entrySet()) {
        ContentValues values = new ContentValues(1);
        values.put(ARCHIVED, entry.getValue() ? "1" : "0");
        db.update(TABLE_NAME, values, query, new String[] { entry.getKey().serialize() });
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      notifyConversationListListeners();
    }
  }

  public @NonNull Set<RecipientId> getArchivedRecipients() {
    Set<RecipientId> archived = new HashSet<>();

    try (Cursor cursor = getArchivedConversationList()) {
      while (cursor != null && cursor.moveToNext()) {
        archived.add(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_ID))));
      }
    }

    return archived;
  }

  public @NonNull Map<RecipientId, Integer> getInboxPositions() {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    String         query  = createQuery(MESSAGE_COUNT + " != ?", 0);

    Map<RecipientId, Integer> positions = new HashMap<>();

    try (Cursor cursor = db.rawQuery(query, new String[] { "0" })) {
      int i = 0;
      while (cursor != null && cursor.moveToNext()) {
        RecipientId recipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_ID)));
        positions.put(recipientId, i);
        i++;
      }
    }

    return positions;
  }

  private Cursor getConversationList(String archived) {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    String         query  = createQuery(ARCHIVED + " = ? AND " + MESSAGE_COUNT + " != 0", 0);
    Cursor         cursor = db.rawQuery(query, new String[]{archived});

    setNotifyConverationListListeners(cursor);

    return cursor;
  }

  public int getArchivedConversationListCount() {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, new String[] {"COUNT(*)"}, ARCHIVED + " = ?",
                        new String[] {"1"}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }

    } finally {
      if (cursor != null) cursor.close();
    }

    return 0;
  }

  public void archiveConversation(long threadId) {
    SQLiteDatabase db            = databaseHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues(1);
    contentValues.put(ARCHIVED, 1);

    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();

    Recipient recipient = getRecipientForThreadId(threadId);
    if (recipient != null) {
      DatabaseFactory.getRecipientDatabase(context).markNeedsSync(recipient.getId());
      StorageSyncHelper.scheduleSyncForDataChange();
    }
  }

  public void unarchiveConversation(long threadId) {
    SQLiteDatabase db            = databaseHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues(1);
    contentValues.put(ARCHIVED, 0);

    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();

    Recipient recipient = getRecipientForThreadId(threadId);
    if (recipient != null) {
      DatabaseFactory.getRecipientDatabase(context).markNeedsSync(recipient.getId());
      StorageSyncHelper.scheduleSyncForDataChange();
    }
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

  public void deleteConversation(long threadId) {
    DatabaseFactory.getSmsDatabase(context).deleteThread(threadId);
    DatabaseFactory.getMmsDatabase(context).deleteThread(threadId);
    DatabaseFactory.getDraftDatabase(context).clearDrafts(threadId);
    deleteThread(threadId);
    notifyConversationListeners(threadId);
    notifyConversationListListeners();
  }

  public void deleteConversations(Set<Long> selectedConversations) {
    DatabaseFactory.getSmsDatabase(context).deleteThreads(selectedConversations);
    DatabaseFactory.getMmsDatabase(context).deleteThreads(selectedConversations);
    DatabaseFactory.getDraftDatabase(context).clearDrafts(selectedConversations);
    deleteThreads(selectedConversations);
    notifyConversationListeners(selectedConversations);
    notifyConversationListListeners();
  }

  public void deleteAllConversations() {
    DatabaseFactory.getSmsDatabase(context).deleteAllThreads();
    DatabaseFactory.getMmsDatabase(context).deleteAllThreads();
    DatabaseFactory.getDraftDatabase(context).clearAllDrafts();
    deleteAllThreads();
  }

  public long getThreadIdIfExistsFor(Recipient recipient) {
    SQLiteDatabase db            = databaseHelper.getReadableDatabase();
    String         where         = RECIPIENT_ID + " = ?";
    String[]       recipientsArg = new String[] {recipient.getId().serialize()};
    Cursor         cursor        = null;

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

  public long getThreadIdFor(@NonNull Recipient recipient) {
    return getThreadIdFor(recipient, DistributionTypes.DEFAULT);
  }

  public long getThreadIdFor(@NonNull Recipient recipient, int distributionType) {
    Long threadId = getThreadIdFor(recipient.getId());
    if (threadId != null) {
      return threadId;
    } else {
      return createThreadForRecipient(recipient.getId(), recipient.isGroup(), distributionType);
    }
  }

  public Long getThreadIdFor(@NonNull RecipientId recipientId) {
    SQLiteDatabase db            = databaseHelper.getReadableDatabase();
    String         where         = RECIPIENT_ID + " = ?";
    String[]       recipientsArg = new String[]{recipientId.serialize()};

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{ ID }, where, recipientsArg, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      } else {
        return null;
      }
    }
  }

  public @Nullable RecipientId getRecipientIdForThreadId(long threadId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, null, ID + " = ?", new String[]{ threadId + "" }, null, null, null)) {

      if (cursor != null && cursor.moveToFirst()) {
        return RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)));
      }
    }

    return null;
  }

  public @Nullable Recipient getRecipientForThreadId(long threadId) {
    RecipientId id = getRecipientIdForThreadId(threadId);
    if (id == null) return null;
    return Recipient.resolved(id);
  }

  public void setHasSent(long threadId, boolean hasSent) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(HAS_SENT, hasSent ? 1 : 0);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE,
                                                new String[] {String.valueOf(threadId)});

    notifyConversationListeners(threadId);
  }

  void updateReadState(long threadId) {
    int unreadCount = DatabaseFactory.getMmsSmsDatabase(context).getUnreadCount(threadId);

    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, unreadCount == 0);
    contentValues.put(UNREAD_COUNT, unreadCount);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues,ID_WHERE,
                                                new String[] {String.valueOf(threadId)});

    notifyConversationListListeners();
  }

  public boolean update(long threadId, boolean unarchive) {
    MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
    long count                    = mmsSmsDatabase.getConversationCount(threadId);

    if (count == 0) {
      deleteThread(threadId);
      notifyConversationListListeners();
      return true;
    }

    MmsSmsDatabase.Reader reader = null;

    try {
      reader = mmsSmsDatabase.readerFor(mmsSmsDatabase.getConversationSnippet(threadId));
      MessageRecord record;

      if (reader != null && (record = reader.getNext()) != null) {
        updateThread(threadId, count, getFormattedBodyFor(record), getAttachmentUriFor(record),
                     getContentTypeFor(record), getExtrasFor(record),
                     record.getTimestamp(), record.getDeliveryStatus(), record.getDeliveryReceiptCount(),
                     record.getType(), unarchive, record.getExpiresIn(), record.getReadReceiptCount());
        notifyConversationListListeners();
        return false;
      } else {
        deleteThread(threadId);
        notifyConversationListListeners();
        return true;
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private @NonNull String getFormattedBodyFor(@NonNull MessageRecord messageRecord) {
    if (messageRecord.isMms() && ((MmsMessageRecord) messageRecord).getSharedContacts().size() > 0) {
      Contact contact = ((MmsMessageRecord) messageRecord).getSharedContacts().get(0);
      return ContactUtil.getStringSummary(context, contact).toString();
    }

    return messageRecord.getBody();
  }

  private @Nullable Uri getAttachmentUriFor(MessageRecord record) {
    if (!record.isMms() || record.isMmsNotification() || record.isGroupAction()) return null;

    SlideDeck slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
    Slide     thumbnail = Optional.fromNullable(slideDeck.getThumbnailSlide()).or(Optional.fromNullable(slideDeck.getStickerSlide())).orNull();

    if (thumbnail != null && !((MmsMessageRecord) record).isViewOnce()) {
      return thumbnail.getThumbnailUri();
    }

    return null;
  }

  private @Nullable String getContentTypeFor(MessageRecord record) {
    if (record.isMms()) {
      SlideDeck slideDeck = ((MmsMessageRecord) record).getSlideDeck();

      if (slideDeck.getSlides().size() > 0) {
        return slideDeck.getSlides().get(0).getContentType();
      }
    }

    return null;
  }

  private @Nullable Extra getExtrasFor(MessageRecord record) {
    boolean     messageRequestAccepted = RecipientUtil.isMessageRequestAccepted(context, record.getThreadId());
    RecipientId threadRecipientId      = getRecipientIdForThreadId(record.getThreadId());

    if (!messageRequestAccepted && threadRecipientId != null) {
      boolean isPushGroup = Recipient.resolved(threadRecipientId).isPushGroup();
      if (isPushGroup) {
        RecipientId recipientId = DatabaseFactory.getMmsSmsDatabase(context).getGroupAddedBy(record.getThreadId());

        if (recipientId != null) {
          return Extra.forGroupMessageRequest(recipientId);
        }
      }

      return Extra.forMessageRequest();
    }

    if (record.isViewOnce()) {
      return Extra.forViewOnce();
    } else if (record.isRemoteDelete()) {
      return Extra.forRemoteDelete();
    } else if (record.isMms() && ((MmsMessageRecord) record).getSlideDeck().getStickerSlide() != null) {
      return Extra.forSticker();
    } else if (record.isMms() && ((MmsMessageRecord) record).getSlideDeck().getSlides().size() > 1) {
      return Extra.forAlbum();
    }

    return null;
  }

  private @NonNull String createQuery(@NonNull String where, int limit) {
    String projection = Util.join(COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION, ",");
    String query =
    "SELECT " + projection + " FROM " + TABLE_NAME +
           " LEFT OUTER JOIN " + RecipientDatabase.TABLE_NAME +
           " ON " + TABLE_NAME + "." + RECIPIENT_ID + " = " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.ID +
           " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME +
           " ON " + TABLE_NAME + "." + RECIPIENT_ID + " = " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.RECIPIENT_ID +
           " WHERE " + where +
           " ORDER BY " + TABLE_NAME + "." + DATE + " DESC";

    if (limit >  0) {
      query += " LIMIT " + limit;
    }

    return query;
  }

  public interface ProgressListener {
    void onProgress(int complete, int total);
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public static class DistributionTypes {
    public static final int DEFAULT      = 2;
    public static final int BROADCAST    = 1;
    public static final int CONVERSATION = 2;
    public static final int ARCHIVE      = 3;
    public static final int INBOX_ZERO   = 4;
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
      long        threadId         = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
      int         distributionType = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE));
      RecipientId recipientId      = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_ID)));

      Recipient          recipient            = Recipient.live(recipientId).get();
      String             body                 = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET));
      long               date                 = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE));
      long               count                = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT));
      int                unreadCount          = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.UNREAD_COUNT));
      long               type                 = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
      boolean            archived             = cursor.getInt(cursor.getColumnIndex(ThreadDatabase.ARCHIVED)) != 0;
      int                status               = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.STATUS));
      int                deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.DELIVERY_RECEIPT_COUNT));
      int                readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.READ_RECEIPT_COUNT));
      long               expiresIn            = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.EXPIRES_IN));
      long               lastSeen             = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.LAST_SEEN));
      Uri                snippetUri           = getSnippetUri(cursor);
      String             contentType          = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_CONTENT_TYPE));
      String             extraString          = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_EXTRAS));

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount = 0;
      }

      Extra extra = null;

      if (extraString != null) {
        try {
          extra = JsonUtils.fromJson(extraString, Extra.class);
        } catch (IOException e) {
          Log.w(TAG, "Failed to decode extras!");
        }
      }

      return new ThreadRecord(body, snippetUri, contentType, extra, recipient, date, count,
                              unreadCount, threadId, deliveryReceiptCount, status, type,
                              distributionType, archived, expiresIn, lastSeen, readReceiptCount);
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

  public static final class Extra {

    @JsonProperty private final boolean isRevealable;
    @JsonProperty private final boolean isSticker;
    @JsonProperty private final boolean isAlbum;
    @JsonProperty private final boolean isRemoteDelete;
    @JsonProperty private final boolean isMessageRequestAccepted;
    @JsonProperty private final String  groupAddedBy;

    public Extra(@JsonProperty("isRevealable") boolean isRevealable,
                 @JsonProperty("isSticker") boolean isSticker,
                 @JsonProperty("isAlbum") boolean isAlbum,
                 @JsonProperty("isRemoteDelete") boolean isRemoteDelete,
                 @JsonProperty("isMessageRequestAccepted") boolean isMessageRequestAccepted,
                 @JsonProperty("groupAddedBy") String groupAddedBy)
    {
      this.isRevealable             = isRevealable;
      this.isSticker                = isSticker;
      this.isAlbum                  = isAlbum;
      this.isRemoteDelete           = isRemoteDelete;
      this.isMessageRequestAccepted = isMessageRequestAccepted;
      this.groupAddedBy             = groupAddedBy;
    }

    public static @NonNull Extra forViewOnce() {
      return new Extra(true, false, false, false, true, null);
    }

    public static @NonNull Extra forSticker() {
      return new Extra(false, true, false, false, true, null);
    }

    public static @NonNull Extra forAlbum() {
      return new Extra(false, false, true, false, true, null);
    }

    public static @NonNull Extra forRemoteDelete() {
      return new Extra(false, false, false, true, true, null);
    }

    public static @NonNull Extra forMessageRequest() {
      return new Extra(false, false, false, false, false, null);
    }

    public static @NonNull Extra forGroupMessageRequest(RecipientId recipientId) {
      return new Extra(false, false, false, false, false, recipientId.serialize());
    }

    public boolean isViewOnce() {
      return isRevealable;
    }

    public boolean isSticker() {
      return isSticker;
    }

    public boolean isAlbum() {
      return isAlbum;
    }

    public boolean isRemoteDelete() {
      return isRemoteDelete;
    }

    public boolean isMessageRequestAccepted() {
      return isMessageRequestAccepted;
    }

    public @Nullable String getGroupAddedBy() {
      return groupAddedBy;
    }
  }
}

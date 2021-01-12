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

import org.jsoup.helper.StringUtil;
import org.signal.core.util.logging.Log;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.database.MessageDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientDetails;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ThreadDatabase extends Database {

  private static final String TAG = ThreadDatabase.class.getSimpleName();

  public static final long NO_TRIM_BEFORE_DATE_SET   = 0;
  public static final int  NO_TRIM_MESSAGE_COUNT_SET = Integer.MAX_VALUE;

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
  private static final String LAST_SCROLLED          = "last_scrolled";
          static final String PINNED                 = "pinned";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                     + " INTEGER PRIMARY KEY, " +
                                                                                  DATE                   + " INTEGER DEFAULT 0, " +
                                                                                  MESSAGE_COUNT          + " INTEGER DEFAULT 0, " +
                                                                                  RECIPIENT_ID           + " INTEGER, " +
                                                                                  SNIPPET                + " TEXT, " +
                                                                                  SNIPPET_CHARSET        + " INTEGER DEFAULT 0, " +
                                                                                  READ                   + " INTEGER DEFAULT " + ReadStatus.READ.serialize() + ", " +
                                                                                  TYPE                   + " INTEGER DEFAULT 0, " +
                                                                                  ERROR                  + " INTEGER DEFAULT 0, " +
                                                                                  SNIPPET_TYPE           + " INTEGER DEFAULT 0, " +
                                                                                  SNIPPET_URI            + " TEXT DEFAULT NULL, " +
                                                                                  SNIPPET_CONTENT_TYPE   + " TEXT DEFAULT NULL, " +
                                                                                  SNIPPET_EXTRAS         + " TEXT DEFAULT NULL, " +
                                                                                  ARCHIVED               + " INTEGER DEFAULT 0, " +
                                                                                  STATUS                 + " INTEGER DEFAULT 0, " +
                                                                                  DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " +
                                                                                  EXPIRES_IN             + " INTEGER DEFAULT 0, " +
                                                                                  LAST_SEEN              + " INTEGER DEFAULT 0, " +
                                                                                  HAS_SENT               + " INTEGER DEFAULT 0, " +
                                                                                  READ_RECEIPT_COUNT     + " INTEGER DEFAULT 0, " +
                                                                                  UNREAD_COUNT           + " INTEGER DEFAULT 0, " +
                                                                                  LAST_SCROLLED          + " INTEGER DEFAULT 0, " +
                                                                                  PINNED                 + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON " + TABLE_NAME + " (" + RECIPIENT_ID + ");",
    "CREATE INDEX IF NOT EXISTS archived_count_index ON " + TABLE_NAME + " (" + ARCHIVED + ", " + MESSAGE_COUNT + ");",
    "CREATE INDEX IF NOT EXISTS thread_pinned_index ON " + TABLE_NAME + " (" + PINNED + ");",
  };

  private static final String[] THREAD_PROJECTION = {
      ID, DATE, MESSAGE_COUNT, RECIPIENT_ID, SNIPPET, SNIPPET_CHARSET, READ, UNREAD_COUNT, TYPE, ERROR, SNIPPET_TYPE,
      SNIPPET_URI, SNIPPET_CONTENT_TYPE, SNIPPET_EXTRAS, ARCHIVED, STATUS, DELIVERY_RECEIPT_COUNT, EXPIRES_IN, LAST_SEEN, READ_RECEIPT_COUNT, LAST_SCROLLED, PINNED
  };

  private static final List<String> TYPED_THREAD_PROJECTION = Stream.of(THREAD_PROJECTION)
                                                                    .map(columnName -> TABLE_NAME + "." + columnName)
                                                                    .toList();

  private static final List<String> COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION = Stream.concat(Stream.concat(Stream.of(TYPED_THREAD_PROJECTION),
                                                                                                             Stream.of(RecipientDatabase.TYPED_RECIPIENT_PROJECTION_NO_ID)),
                                                                                               Stream.of(GroupDatabase.TYPED_GROUP_PROJECTION))
                                                                                       .toList();

  private static final String ORDER_BY_DEFAULT = TABLE_NAME + "." + DATE + " DESC";

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

    ContentValues contentValues = new ContentValues();
    contentValues.put(DATE, date - date % 1000);
    contentValues.put(SNIPPET, body);
    contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());
    contentValues.put(SNIPPET_TYPE, type);
    contentValues.put(SNIPPET_CONTENT_TYPE, contentType);
    contentValues.put(SNIPPET_EXTRAS, extraSerialized);
    contentValues.put(MESSAGE_COUNT, count);
    contentValues.put(STATUS, status);
    contentValues.put(DELIVERY_RECEIPT_COUNT, deliveryReceiptCount);
    contentValues.put(READ_RECEIPT_COUNT, readReceiptCount);
    contentValues.put(EXPIRES_IN, expiresIn);

    if (unarchive) {
      contentValues.put(ARCHIVED, 0);
    }

    if (count != getConversationMessageCount(threadId)) {
      contentValues.put(LAST_SCROLLED, 0);
    }

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public void updateSnippet(long threadId, String snippet, @Nullable Uri attachment, long date, long type, boolean unarchive) {
    if (isSilentType(type)) {
      return;
    }

    ContentValues contentValues = new ContentValues();
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
    ConversationUtil.clearShortcuts(context, Collections.singleton(threadId));
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
    ConversationUtil.clearShortcuts(context, threadIds);
  }

  private void deleteAllThreads() {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
    notifyConversationListListeners();
    ConversationUtil.clearAllShortcuts(context);
  }

  public void trimAllThreads(int length, long trimBeforeDate) {
    if (length == NO_TRIM_MESSAGE_COUNT_SET && trimBeforeDate == NO_TRIM_BEFORE_DATE_SET) {
      return;
    }

    SQLiteDatabase       db                   = databaseHelper.getWritableDatabase();
    AttachmentDatabase   attachmentDatabase   = DatabaseFactory.getAttachmentDatabase(context);
    GroupReceiptDatabase groupReceiptDatabase = DatabaseFactory.getGroupReceiptDatabase(context);
    MmsSmsDatabase       mmsSmsDatabase       = DatabaseFactory.getMmsSmsDatabase(context);
    MentionDatabase      mentionDatabase      = DatabaseFactory.getMentionDatabase(context);

    try (Cursor cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[] { ID }, null, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        trimThreadInternal(CursorUtil.requireLong(cursor, ID), length, trimBeforeDate);
      }
    }

    db.beginTransaction();

    try {
      mmsSmsDatabase.deleteAbandonedMessages();
      attachmentDatabase.trimAllAbandonedAttachments();
      groupReceiptDatabase.deleteAbandonedRows();
      mentionDatabase.deleteAbandonedMentions();
      attachmentDatabase.deleteAbandonedAttachmentFiles();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }


    notifyAttachmentListeners();
    notifyStickerListeners();
    notifyStickerPackListeners();
  }

  public void trimThread(long threadId, int length, long trimBeforeDate) {
    if (length == NO_TRIM_MESSAGE_COUNT_SET && trimBeforeDate == NO_TRIM_BEFORE_DATE_SET) {
      return;
    }

    SQLiteDatabase       db                   = databaseHelper.getWritableDatabase();
    AttachmentDatabase   attachmentDatabase   = DatabaseFactory.getAttachmentDatabase(context);
    GroupReceiptDatabase groupReceiptDatabase = DatabaseFactory.getGroupReceiptDatabase(context);
    MmsSmsDatabase       mmsSmsDatabase       = DatabaseFactory.getMmsSmsDatabase(context);
    MentionDatabase      mentionDatabase      = DatabaseFactory.getMentionDatabase(context);

    db.beginTransaction();

    try {
      trimThreadInternal(threadId, length, trimBeforeDate);
      mmsSmsDatabase.deleteAbandonedMessages();
      attachmentDatabase.trimAllAbandonedAttachments();
      groupReceiptDatabase.deleteAbandonedRows();
      mentionDatabase.deleteAbandonedMentions();
      attachmentDatabase.deleteAbandonedAttachmentFiles();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }


    notifyAttachmentListeners();
    notifyStickerListeners();
    notifyStickerPackListeners();
  }

  private void trimThreadInternal(long threadId, int length, long trimBeforeDate) {
    if (length == NO_TRIM_MESSAGE_COUNT_SET && trimBeforeDate == NO_TRIM_BEFORE_DATE_SET) {
      return;
    }

    if (length != NO_TRIM_MESSAGE_COUNT_SET) {
      try (Cursor cursor = DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId)) {
        if (cursor != null && length > 0 && cursor.getCount() > length) {
          cursor.moveToPosition(length - 1);
          trimBeforeDate = Math.max(trimBeforeDate, cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.NORMALIZED_DATE_RECEIVED)));
        }
      }
    }

    if (trimBeforeDate != NO_TRIM_BEFORE_DATE_SET) {
      Log.i(TAG, "Trimming thread: " + threadId + " before: " + trimBeforeDate);

      DatabaseFactory.getMmsSmsDatabase(context).deleteMessagesInThreadBeforeDate(threadId, trimBeforeDate);

      update(threadId, false);
      notifyConversationListeners(threadId);
    }
  }

  public List<MarkedMessageInfo> setAllThreadsRead() {
    SQLiteDatabase db           = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, ReadStatus.READ.serialize());
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
    return hasReceivedAnyCallsSince(getThreadIdFor(recipient), timestamp);
  }

  public boolean hasReceivedAnyCallsSince(long threadId, long timestamp) {
    return DatabaseFactory.getMmsSmsDatabase(context).hasReceivedAnyCallsSince(threadId, timestamp);
  }

  public List<MarkedMessageInfo> setEntireThreadRead(long threadId) {
    setRead(threadId, false);

    final List<MarkedMessageInfo> smsRecords = DatabaseFactory.getSmsDatabase(context).setEntireThreadRead(threadId);
    final List<MarkedMessageInfo> mmsRecords = DatabaseFactory.getMmsDatabase(context).setEntireThreadRead(threadId);

    return Util.concatenatedList(smsRecords, mmsRecords);
  }

  public List<MarkedMessageInfo> setRead(long threadId, boolean lastSeen) {
    return setReadInternal(Collections.singletonList(threadId), lastSeen, -1);
  }

  public List<MarkedMessageInfo> setReadSince(long threadId, boolean lastSeen, long sinceTimestamp) {
    return setReadInternal(Collections.singletonList(threadId), lastSeen, sinceTimestamp);
  }

  public List<MarkedMessageInfo> setRead(Collection<Long> threadIds, boolean lastSeen) {
    return setReadInternal(threadIds, lastSeen, -1);
  }

  private List<MarkedMessageInfo> setReadInternal(Collection<Long> threadIds, boolean lastSeen, long sinceTimestamp) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    List<MarkedMessageInfo> smsRecords = new LinkedList<>();
    List<MarkedMessageInfo> mmsRecords = new LinkedList<>();
    boolean                 needsSync  = false;

    db.beginTransaction();

    try {
      ContentValues contentValues = new ContentValues(2);
      contentValues.put(READ, ReadStatus.READ.serialize());

      if (lastSeen) {
        contentValues.put(LAST_SEEN, sinceTimestamp == -1 ? System.currentTimeMillis() : sinceTimestamp);
      }

      for (long threadId : threadIds) {
        ThreadRecord previous = getThreadRecord(threadId);

        smsRecords.addAll(DatabaseFactory.getSmsDatabase(context).setMessagesReadSince(threadId, sinceTimestamp));
        mmsRecords.addAll(DatabaseFactory.getMmsDatabase(context).setMessagesReadSince(threadId, sinceTimestamp));

        DatabaseFactory.getSmsDatabase(context).setReactionsSeen(threadId, sinceTimestamp);
        DatabaseFactory.getMmsDatabase(context).setReactionsSeen(threadId, sinceTimestamp);

        int unreadCount = DatabaseFactory.getMmsSmsDatabase(context).getUnreadCount(threadId);

        contentValues.put(UNREAD_COUNT, unreadCount);

        db.update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(threadId));

        if (previous != null && previous.isForcedUnread()) {
          DatabaseFactory.getRecipientDatabase(context).markNeedsSync(previous.getRecipient().getId());
          needsSync = true;
        }
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(new HashSet<>(threadIds));
    notifyConversationListListeners();

    if (needsSync) {
      StorageSyncHelper.scheduleSyncForDataChange();
    }

    return Util.concatenatedList(smsRecords, mmsRecords);
  }

  public void setForcedUnread(@NonNull Collection<Long> threadIds) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      List<RecipientId> recipientIds  = getRecipientIdsForThreadIds(threadIds);
      SqlUtil.Query     query         = SqlUtil.buildCollectionQuery(ID, threadIds);
      ContentValues     contentValues = new ContentValues();

      contentValues.put(READ, ReadStatus.FORCED_UNREAD.serialize());

      db.update(TABLE_NAME, contentValues, query.getWhere(), query.getWhereArgs());
      DatabaseFactory.getRecipientDatabase(context).markNeedsSync(recipientIds);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();

      StorageSyncHelper.scheduleSyncForDataChange();
      notifyConversationListListeners();
    }
  }


  public void incrementUnread(long threadId, int amount) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + READ + " = " + ReadStatus.UNREAD.serialize() + ", " +
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
    setNotifyConversationListListeners(cursor);
    return cursor;
  }

  public Cursor getRecentConversationList(int limit, boolean includeInactiveGroups, boolean hideV1Groups) {
    return getRecentConversationList(limit, includeInactiveGroups, false, hideV1Groups);
  }

  public Cursor getRecentConversationList(int limit, boolean includeInactiveGroups, boolean groupsOnly, boolean hideV1Groups) {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = !includeInactiveGroups ? MESSAGE_COUNT + " != 0 AND (" + GroupDatabase.TABLE_NAME + "." + GroupDatabase.ACTIVE + " IS NULL OR " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.ACTIVE + " = 1)"
                                                  : MESSAGE_COUNT + " != 0";

    if (groupsOnly) {
      query += " AND " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.GROUP_ID + " NOT NULL";
    }

    if (hideV1Groups) {
      query += " AND " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.GROUP_TYPE + " != " + RecipientDatabase.GroupType.SIGNAL_V1.getId();
    }

    query += " AND " + ARCHIVED + " = 0";

    return db.rawQuery(createQuery(query, 0, limit, true), null);
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
    String         query = createQuery(where, 0, limit, true);

    return db.rawQuery(query, null);
  }

  public @NonNull List<ThreadRecord> getRecentV1Groups(int limit) {
    SQLiteDatabase db               = databaseHelper.getReadableDatabase();
    String         where            = MESSAGE_COUNT + " != 0 AND " +
                                      "(" +
                                        GroupDatabase.TABLE_NAME + "." + GroupDatabase.ACTIVE + " = 1 AND " +
                                        GroupDatabase.TABLE_NAME + "." + GroupDatabase.V2_MASTER_KEY + " IS NULL AND " +
                                        GroupDatabase.TABLE_NAME + "." + GroupDatabase.MMS + " = 0" +
                                      ")";
    String         query = createQuery(where, 0, limit, true);

    List<ThreadRecord> threadRecords = new ArrayList<>();

    try (Reader reader = readerFor(db.rawQuery(query, null))) {
      ThreadRecord record;

      while ((record = reader.getNext()) != null) {
        threadRecords.add(record);
      }
    }
    return threadRecords;
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
        ContentValues values   = new ContentValues(2);

        if (entry.getValue()) {
          values.put(PINNED, "0");
        }

        values.put(ARCHIVED, entry.getValue() ? "1" : "0");
        db.update(TABLE_NAME, values, query, new String[] { entry.getKey().serialize() });
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      notifyConversationListListeners();
    }
  }

  public void setArchived(Set<Long> threadIds, boolean archive) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    db.beginTransaction();
    try {
      for (long threadId : threadIds) {
        ContentValues values = new ContentValues(2);

        if (archive) {
          values.put(PINNED, "0");
        }

        values.put(ARCHIVED, archive ? "1" : "0");
        db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(threadId));
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

  public Cursor getArchivedConversationList(long offset, long limit) {
    return getConversationList("1", offset, limit);
  }

  private Cursor getConversationList(String archived) {
    return getConversationList(archived, 0, 0);
  }

  public Cursor getUnarchivedConversationList(boolean pinned, long offset, long limit) {
    SQLiteDatabase db          = databaseHelper.getReadableDatabase();
    String         pinnedWhere = PINNED + (pinned ? " != 0" : " = 0");
    String         where       = ARCHIVED + " = 0 AND " + MESSAGE_COUNT + " != 0 AND " + pinnedWhere;

    final String query;

    if (pinned) {
      query = createQuery(where, PINNED + " ASC", offset, limit);
    } else {
      query = createQuery(where, offset, limit, false);
    }

    Cursor cursor = db.rawQuery(query, new String[]{});

    setNotifyConversationListListeners(cursor);

    return cursor;
  }

  private Cursor getConversationList(@NonNull String archived, long offset, long limit) {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    String         query  = createQuery(ARCHIVED + " = ? AND " + MESSAGE_COUNT + " != 0", offset, limit, false);
    Cursor         cursor = db.rawQuery(query, new String[]{archived});

    setNotifyConversationListListeners(cursor);

    return cursor;
  }

  public int getArchivedConversationListCount() {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    String[]       columns = new String[] { "COUNT(*)" };
    String         query   = ARCHIVED + " = ? AND " + MESSAGE_COUNT + " != 0";
    String[]       args    = new String[] {"1"};

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public int getPinnedConversationListCount() {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    String[]       columns = new String[] { "COUNT(*)" };
    String         query   = ARCHIVED + " = 0 AND " + PINNED + " != 0 AND " + MESSAGE_COUNT + " != 0";

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public int getUnarchivedConversationListCount() {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    String[]       columns = new String[] { "COUNT(*)" };
    String         query   = ARCHIVED + " = 0 AND " + MESSAGE_COUNT + " != 0";

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  /**
   * @return Pinned recipients, in order from top to bottom.
   */
  public @NonNull List<RecipientId> getPinnedRecipientIds() {
    SQLiteDatabase    db         = databaseHelper.getReadableDatabase();
    String[]          projection = new String[]{RECIPIENT_ID};
    String            query      = PINNED + " > ?";
    String[]          args       = SqlUtil.buildArgs(0);
    List<RecipientId> pinned     = new LinkedList<>();

    try (Cursor cursor = db.query(TABLE_NAME, projection, query, args, null, null, PINNED + " ASC")) {
      while (cursor.moveToNext()) {
        pinned.add(RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID)));
      }
    }

    return pinned;
  }

  public void pinConversations(@NonNull Set<Long> threadIds) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    try {
      db.beginTransaction();

      int pinnedCount = getPinnedConversationListCount();

      for (long threadId : threadIds) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(PINNED, ++pinnedCount);

        db.update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(threadId));

      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      notifyConversationListListeners();
    }

    notifyConversationListListeners();

    DatabaseFactory.getRecipientDatabase(context).markNeedsSync(Recipient.self().getId());
    StorageSyncHelper.scheduleSyncForDataChange();
  }

  public void unpinConversations(@NonNull Set<Long> threadIds) {
    SQLiteDatabase db            = databaseHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues(1);
    String         placeholders  = StringUtil.join(Stream.of(threadIds).map(unused -> "?").toList(), ",");
    String         selection     = ID + " IN (" + placeholders + ")";

    contentValues.put(PINNED, 0);

    db.update(TABLE_NAME, contentValues, selection, SqlUtil.buildArgs(Stream.of(threadIds).toArray()));
    notifyConversationListListeners();

    DatabaseFactory.getRecipientDatabase(context).markNeedsSync(Recipient.self().getId());
    StorageSyncHelper.scheduleSyncForDataChange();
  }

  public void archiveConversation(long threadId) {
    SQLiteDatabase db            = databaseHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues(1);
    contentValues.put(PINNED, 0);
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

  public void setLastScrolled(long threadId, long lastScrolledTimestamp) {
    SQLiteDatabase db            = databaseHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues(1);

    contentValues.put(LAST_SCROLLED, lastScrolledTimestamp);

    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(threadId)});
  }

  public ConversationMetadata getConversationMetadata(long threadId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{LAST_SEEN, HAS_SENT, LAST_SCROLLED}, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return new ConversationMetadata(cursor.getLong(cursor.getColumnIndexOrThrow(LAST_SEEN)),
                                        cursor.getLong(cursor.getColumnIndexOrThrow(HAS_SENT)) == 1,
                                        cursor.getLong(cursor.getColumnIndexOrThrow(LAST_SCROLLED)));
      }

      return new ConversationMetadata(-1L, false, -1);
    }
  }

  public int getConversationMessageCount(long threadId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{MESSAGE_COUNT}, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return CursorUtil.requireInt(cursor, MESSAGE_COUNT);
      }
    }
    return 0;
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

  public long getThreadIdIfExistsFor(@NonNull RecipientId recipientId) {
    SQLiteDatabase db            = databaseHelper.getReadableDatabase();
    String         where         = RECIPIENT_ID + " = ?";
    String[]       recipientsArg = new String[] {recipientId.serialize()};

    try (Cursor cursor = db.query(TABLE_NAME, new String[]{ ID }, where, recipientsArg, null, null, null, "1")) {
      if (cursor != null && cursor.moveToFirst()) {
        return CursorUtil.requireLong(cursor, ID);
      } else {
        return -1;
      }
    }
  }

  public long getOrCreateValidThreadId(@NonNull Recipient recipient, long candidateId) {
    return getOrCreateValidThreadId(recipient, candidateId, DistributionTypes.DEFAULT);
  }

  public long getOrCreateValidThreadId(@NonNull Recipient recipient, long candidateId, int distributionType) {
    if (candidateId != -1) {
      Optional<Long> remapped = RemappedRecords.getInstance().getThread(context, candidateId);
      return remapped.isPresent() ? remapped.get() : candidateId;
    } else {
      return getThreadIdFor(recipient, distributionType);
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

  public @Nullable Long getThreadIdFor(@NonNull RecipientId recipientId) {
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

  public @NonNull List<RecipientId> getRecipientIdsForThreadIds(Collection<Long> threadIds) {
    SQLiteDatabase    db    = databaseHelper.getReadableDatabase();
    SqlUtil.Query     query = SqlUtil.buildCollectionQuery(ID, threadIds);
    List<RecipientId> ids   = new ArrayList<>(threadIds.size());

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { RECIPIENT_ID }, query.getWhere(), query.getWhereArgs(), null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID)));
      }
    }

    return ids;
  }

  public boolean hasThread(@NonNull RecipientId recipientId) {
    return getThreadIdIfExistsFor(recipientId) > -1;
  }

  public void setHasSent(long threadId, boolean hasSent) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(HAS_SENT, hasSent ? 1 : 0);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE,
                                                new String[] {String.valueOf(threadId)});

    notifyConversationListeners(threadId);
  }

  void updateReadState(long threadId) {
    ThreadRecord previous    = getThreadRecord(threadId);
    int          unreadCount = DatabaseFactory.getMmsSmsDatabase(context).getUnreadCount(threadId);

    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, unreadCount == 0 ? ReadStatus.READ.serialize() : ReadStatus.UNREAD.serialize());
    contentValues.put(UNREAD_COUNT, unreadCount);

    databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(threadId));

    notifyConversationListListeners();

    if (previous != null && previous.isForcedUnread()) {
      DatabaseFactory.getRecipientDatabase(context).markNeedsSync(previous.getRecipient().getId());
      StorageSyncHelper.scheduleSyncForDataChange();
    }
  }

  public void applyStorageSyncUpdate(@NonNull RecipientId recipientId, @NonNull SignalContactRecord record) {
    applyStorageSyncUpdate(recipientId, record.isArchived(), record.isForcedUnread());
  }

  public void applyStorageSyncUpdate(@NonNull RecipientId recipientId, @NonNull SignalGroupV1Record record) {
    applyStorageSyncUpdate(recipientId, record.isArchived(), record.isForcedUnread());
  }

  public void applyStorageSyncUpdate(@NonNull RecipientId recipientId, @NonNull SignalGroupV2Record record) {
    applyStorageSyncUpdate(recipientId, record.isArchived(), record.isForcedUnread());
  }

  public void applyStorageSyncUpdate(@NonNull RecipientId recipientId, @NonNull SignalAccountRecord record) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();
    try {
      applyStorageSyncUpdate(recipientId, record.isNoteToSelfArchived(), record.isNoteToSelfForcedUnread());

      ContentValues clearPinnedValues = new ContentValues();
      clearPinnedValues.put(PINNED, 0);
      db.update(TABLE_NAME, clearPinnedValues, null, null);

      int pinnedPosition = 1;
      for (SignalAccountRecord.PinnedConversation pinned : record.getPinnedConversations()) {
        ContentValues pinnedValues = new ContentValues();
        pinnedValues.put(PINNED, pinnedPosition);

        Recipient pinnedRecipient;

        if (pinned.getContact().isPresent()) {
          pinnedRecipient = Recipient.externalPush(context, pinned.getContact().get());
        } else if (pinned.getGroupV1Id().isPresent()) {
          try {
            pinnedRecipient = Recipient.externalGroupExact(context, GroupId.v1(pinned.getGroupV1Id().get()));
          } catch (BadGroupIdException e) {
            Log.w(TAG, "Failed to parse pinned groupV1 ID!", e);
            pinnedRecipient = null;
          }
        } else if (pinned.getGroupV2MasterKey().isPresent()) {
          try {
            pinnedRecipient = Recipient.externalGroupExact(context, GroupId.v2(new GroupMasterKey(pinned.getGroupV2MasterKey().get())));
          } catch (InvalidInputException e) {
            Log.w(TAG, "Failed to parse pinned groupV2 master key!", e);
            pinnedRecipient = null;
          }
        } else {
          Log.w(TAG, "Empty pinned conversation on the AccountRecord?");
          pinnedRecipient = null;
        }

        if (pinnedRecipient != null) {
          db.update(TABLE_NAME, pinnedValues, RECIPIENT_ID + " = ?", SqlUtil.buildArgs(pinnedRecipient.getId()));
        }

        pinnedPosition++;
      }
      
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListListeners();
  }

  private void applyStorageSyncUpdate(@NonNull RecipientId recipientId, boolean archived, boolean forcedUnread) {
    ContentValues values = new ContentValues();
    values.put(ARCHIVED, archived);

    if (forcedUnread) {
      values.put(READ, ReadStatus.FORCED_UNREAD.serialize());
    } else {
      Long threadId = getThreadIdFor(recipientId);
      if (threadId != null) {
        int unreadCount = DatabaseFactory.getMmsSmsDatabase(context).getUnreadCount(threadId);

        values.put(READ, unreadCount == 0 ? ReadStatus.READ.serialize() : ReadStatus.UNREAD.serialize());
        values.put(UNREAD_COUNT, unreadCount);
      }
    }

    databaseHelper.getWritableDatabase().update(TABLE_NAME, values, RECIPIENT_ID + " = ?", SqlUtil.buildArgs(recipientId));
  }

  public boolean update(long threadId, boolean unarchive) {
    return update(threadId, unarchive, true);
  }

  public boolean update(long threadId, boolean unarchive, boolean allowDeletion) {
    MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
    long count                    = mmsSmsDatabase.getConversationCountForThreadSummary(threadId);

    if (count == 0) {
      if (allowDeletion) {
        deleteThread(threadId);
        notifyConversationListListeners();
      }
      return true;
    }

    MmsSmsDatabase.Reader reader = null;

    try {
      reader = mmsSmsDatabase.readerFor(mmsSmsDatabase.getConversationSnippet(threadId));
      MessageRecord record;

      if (reader != null && (record = reader.getNext()) != null) {
        updateThread(threadId, count, ThreadBodyUtil.getFormattedBodyFor(context, record), getAttachmentUriFor(record),
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

  public @NonNull ThreadRecord getThreadRecordFor(@NonNull Recipient recipient) {
    return Objects.requireNonNull(getThreadRecord(getThreadIdFor(recipient)));
  }

  public @NonNull Set<RecipientId> getAllThreadRecipients() {
    SQLiteDatabase   db  = databaseHelper.getReadableDatabase();
    Set<RecipientId> ids = new HashSet<>();


    try (Cursor cursor = db.query(TABLE_NAME, new String[] { RECIPIENT_ID }, null, null, null, null, null)) {
      while (cursor.moveToNext()) {
        ids.add(RecipientId.from(CursorUtil.requireString(cursor, RECIPIENT_ID)));
      }
    }

    return ids;
  }


  @NonNull MergeResult merge(@NonNull RecipientId primaryRecipientId, @NonNull RecipientId secondaryRecipientId) {
    if (!databaseHelper.getWritableDatabase().inTransaction()) {
      throw new IllegalStateException("Must be in a transaction!");
    }

    Log.w(TAG, "Merging threads. Primary: " + primaryRecipientId + ", Secondary: " + secondaryRecipientId);

    ThreadRecord primary   = getThreadRecord(getThreadIdFor(primaryRecipientId));
    ThreadRecord secondary = getThreadRecord(getThreadIdFor(secondaryRecipientId));

    if (primary != null && secondary == null) {
      Log.w(TAG, "[merge] Only had a thread for primary. Returning that.");
      return new MergeResult(primary.getThreadId(), -1, false);
    } else if (primary == null && secondary != null) {
      Log.w(TAG, "[merge] Only had a thread for secondary. Updating it to have the recipientId of the primary.");

      ContentValues values = new ContentValues();
      values.put(RECIPIENT_ID, primaryRecipientId.serialize());

      databaseHelper.getWritableDatabase().update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(secondary.getThreadId()));
      return new MergeResult(secondary.getThreadId(), -1, false);
    } else if (primary == null && secondary == null) {
      Log.w(TAG, "[merge] No thread for either.");
      return new MergeResult(-1, -1, false);
    } else {
      Log.w(TAG, "[merge] Had a thread for both. Deleting the secondary and merging the attributes together.");

      SQLiteDatabase db = databaseHelper.getWritableDatabase();

      db.delete(TABLE_NAME, ID_WHERE, SqlUtil.buildArgs(secondary.getThreadId()));

      if (primary.getExpiresIn() != secondary.getExpiresIn()) {
        ContentValues values = new ContentValues();
        if (primary.getExpiresIn() == 0) {
          values.put(EXPIRES_IN, secondary.getExpiresIn());
        } else if (secondary.getExpiresIn() == 0) {
          values.put(EXPIRES_IN, primary.getExpiresIn());
        } else {
          values.put(EXPIRES_IN, Math.min(primary.getExpiresIn(), secondary.getExpiresIn()));
        }

        db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(primary.getThreadId()));
      }

      ContentValues draftValues = new ContentValues();
      draftValues.put(DraftDatabase.THREAD_ID, primary.getThreadId());
      db.update(DraftDatabase.TABLE_NAME, draftValues, DraftDatabase.THREAD_ID + " = ?", SqlUtil.buildArgs(secondary.getThreadId()));

      ContentValues searchValues = new ContentValues();
      searchValues.put(SearchDatabase.THREAD_ID, primary.getThreadId());
      db.update(SearchDatabase.SMS_FTS_TABLE_NAME, searchValues, SearchDatabase.THREAD_ID + " = ?", SqlUtil.buildArgs(secondary.getThreadId()));
      db.update(SearchDatabase.MMS_FTS_TABLE_NAME, searchValues, SearchDatabase.THREAD_ID + " = ?", SqlUtil.buildArgs(secondary.getThreadId()));

      RemappedRecords.getInstance().addThread(context, secondary.getThreadId(), primary.getThreadId());

      return new MergeResult(primary.getThreadId(), secondary.getThreadId(), true);
    }
  }

  private @Nullable ThreadRecord getThreadRecord(@Nullable Long threadId) {
    if (threadId == null) {
      return null;
    }

    String query = createQuery(TABLE_NAME + "." + ID + " = ?", 1);

    try (Cursor cursor = databaseHelper.getReadableDatabase().rawQuery(query, SqlUtil.buildArgs(threadId))) {
      if (cursor != null && cursor.moveToFirst()) {
        return readerFor(cursor).getCurrent();
      }
    }

    return null;
  }

  private @Nullable Uri getAttachmentUriFor(MessageRecord record) {
    if (!record.isMms() || record.isMmsNotification() || record.isGroupAction()) return null;

    SlideDeck slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
    Slide     thumbnail = Optional.fromNullable(slideDeck.getThumbnailSlide()).or(Optional.fromNullable(slideDeck.getStickerSlide())).orNull();

    if (thumbnail != null && !((MmsMessageRecord) record).isViewOnce()) {
      return thumbnail.getUri();
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
      Recipient resolved = Recipient.resolved(threadRecipientId);
      if (resolved.isPushGroup()) {
        if (resolved.isPushV2Group()) {
          MessageRecord.InviteAddState inviteAddState = record.getGv2AddInviteState();
          if (inviteAddState != null) {
            RecipientId from = RecipientId.from(inviteAddState.getAddedOrInvitedBy(), null);
            if (inviteAddState.isInvited()) {
              Log.i(TAG, "GV2 invite message request from " + from);
              return Extra.forGroupV2invite(from);
            } else {
              Log.i(TAG, "GV2 message request from " + from);
              return Extra.forGroupMessageRequest(from);
            }
          }
          Log.w(TAG, "Falling back to unknown message request state for GV2 message");
          return Extra.forMessageRequest();
        } else {
          RecipientId recipientId = DatabaseFactory.getMmsSmsDatabase(context).getGroupAddedBy(record.getThreadId());

          if (recipientId != null) {
            return Extra.forGroupMessageRequest(recipientId);
          }
        }
      }

      return Extra.forMessageRequest();
    }

    if (record.isRemoteDelete()) {
      return Extra.forRemoteDelete();
    } else if (record.isViewOnce()) {
      return Extra.forViewOnce();
    } else if (record.isMms() && ((MmsMessageRecord) record).getSlideDeck().getStickerSlide() != null) {
      StickerSlide slide = Objects.requireNonNull(((MmsMessageRecord) record).getSlideDeck().getStickerSlide());
      return Extra.forSticker(slide.getEmoji());
    } else if (record.isMms() && ((MmsMessageRecord) record).getSlideDeck().getSlides().size() > 1) {
      return Extra.forAlbum();
    }

    return null;
  }

  private @NonNull String createQuery(@NonNull String where, long limit) {
    return createQuery(where, 0, limit, false);
  }

  private @NonNull String createQuery(@NonNull String where, long offset, long limit, boolean preferPinned) {
    String orderBy    = (preferPinned ? TABLE_NAME + "." + PINNED + " DESC, " : "") + TABLE_NAME + "." + DATE + " DESC";

    return createQuery(where, orderBy, offset, limit);
  }

  private @NonNull String createQuery(@NonNull String where, @NonNull String orderBy, long offset, long limit) {
    String projection = Util.join(COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION, ",");

    String query =
    "SELECT " + projection + " FROM " + TABLE_NAME +
           " LEFT OUTER JOIN " + RecipientDatabase.TABLE_NAME +
           " ON " + TABLE_NAME + "." + RECIPIENT_ID + " = " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.ID +
           " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME +
           " ON " + TABLE_NAME + "." + RECIPIENT_ID + " = " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.RECIPIENT_ID +
           " WHERE " + where +
           " ORDER BY " + orderBy;

    if (limit >  0) {
      query += " LIMIT " + limit;
    }

    if (offset > 0) {
      query += " OFFSET " + offset;
    }

    return query;
  }

  private boolean isSilentType(long type) {
    return MmsSmsColumns.Types.isProfileChange(type) ||
           MmsSmsColumns.Types.isGroupV1MigrationEvent(type);
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

  public class Reader extends StaticReader {
    public Reader(Cursor cursor) {
      super(cursor, context);
    }
  }

  public static class StaticReader implements Closeable {

    private final Cursor  cursor;
    private final Context context;

    public StaticReader(Cursor cursor, Context context) {
      this.cursor  = cursor;
      this.context = context;
    }

    public ThreadRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public ThreadRecord getCurrent() {
      RecipientId       recipientId       = RecipientId.from(CursorUtil.requireLong(cursor, ThreadDatabase.RECIPIENT_ID));
      RecipientSettings recipientSettings = RecipientDatabase.getRecipientSettings(context, cursor, ThreadDatabase.RECIPIENT_ID);

      Recipient recipient;

      if (recipientSettings.getGroupId() != null) {
        GroupDatabase.GroupRecord group = new GroupDatabase.Reader(cursor).getCurrent();

        if (group != null) {
          RecipientDetails details = new RecipientDetails(group.getTitle(),
                                                          group.hasAvatar() ? Optional.of(group.getAvatarId()) : Optional.absent(),
                                                          false,
                                                          false,
                                                          recipientSettings,
                                                          null);
          recipient = new Recipient(recipientId, details, false);
        } else {
          recipient = Recipient.live(recipientId).get();
        }
      } else {
        RecipientDetails details = RecipientDetails.forIndividual(context, recipientSettings);
        recipient = new Recipient(recipientId, details, true);
      }

      int readReceiptCount = TextSecurePreferences.isReadReceiptsEnabled(context) ? cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.READ_RECEIPT_COUNT))
                                                                                  : 0;

      String extraString = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_EXTRAS));
      Extra  extra       = null;

      if (extraString != null) {
        try {
          extra = JsonUtils.fromJson(extraString, Extra.class);
        } catch (IOException e) {
          Log.w(TAG, "Failed to decode extras!");
        }
      }

      return new ThreadRecord.Builder(cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID)))
                             .setRecipient(recipient)
                             .setType(cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE)))
                             .setDistributionType(cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE)))
                             .setBody(Util.emptyIfNull(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET))))
                             .setDate(cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE)))
                             .setArchived(cursor.getInt(cursor.getColumnIndex(ThreadDatabase.ARCHIVED)) != 0)
                             .setDeliveryStatus(cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.STATUS)))
                             .setDeliveryReceiptCount(cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.DELIVERY_RECEIPT_COUNT)))
                             .setReadReceiptCount(readReceiptCount)
                             .setExpiresIn(cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.EXPIRES_IN)))
                             .setLastSeen(cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.LAST_SEEN)))
                             .setSnippetUri(getSnippetUri(cursor))
                             .setContentType(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_CONTENT_TYPE)))
                             .setCount(cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT)))
                             .setUnreadCount(cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.UNREAD_COUNT)))
                             .setForcedUnread(cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.READ)) == ReadStatus.FORCED_UNREAD.serialize())
                             .setPinned(CursorUtil.requireBoolean(cursor, ThreadDatabase.PINNED))
                             .setExtra(extra)
                             .build();
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
    @JsonProperty private final String  stickerEmoji;
    @JsonProperty private final boolean isAlbum;
    @JsonProperty private final boolean isRemoteDelete;
    @JsonProperty private final boolean isMessageRequestAccepted;
    @JsonProperty private final boolean isGv2Invite;
    @JsonProperty private final String  groupAddedBy;

    public Extra(@JsonProperty("isRevealable") boolean isRevealable,
                 @JsonProperty("isSticker") boolean isSticker,
                 @JsonProperty("stickerEmoji") String stickerEmoji,
                 @JsonProperty("isAlbum") boolean isAlbum,
                 @JsonProperty("isRemoteDelete") boolean isRemoteDelete,
                 @JsonProperty("isMessageRequestAccepted") boolean isMessageRequestAccepted,
                 @JsonProperty("isGv2Invite") boolean isGv2Invite,
                 @JsonProperty("groupAddedBy") String groupAddedBy)
    {
      this.isRevealable             = isRevealable;
      this.isSticker                = isSticker;
      this.stickerEmoji             = stickerEmoji;
      this.isAlbum                  = isAlbum;
      this.isRemoteDelete           = isRemoteDelete;
      this.isMessageRequestAccepted = isMessageRequestAccepted;
      this.isGv2Invite              = isGv2Invite;
      this.groupAddedBy             = groupAddedBy;
    }

    public static @NonNull Extra forViewOnce() {
      return new Extra(true, false, null, false, false, true, false, null);
    }

    public static @NonNull Extra forSticker(@Nullable String emoji) {
      return new Extra(false, true, emoji, false, false, true, false, null);
    }

    public static @NonNull Extra forAlbum() {
      return new Extra(false, false, null, true, false, true, false, null);
    }

    public static @NonNull Extra forRemoteDelete() {
      return new Extra(false, false, null, false, true, true, false, null);
    }

    public static @NonNull Extra forMessageRequest() {
      return new Extra(false, false, null, false, false, false, false, null);
    }

    public static @NonNull Extra forGroupMessageRequest(RecipientId recipientId) {
      return new Extra(false, false, null, false, false, false, false, recipientId.serialize());
    }

    public static @NonNull Extra forGroupV2invite(RecipientId recipientId) {
      return new Extra(false, false, null, false, false, false, true, recipientId.serialize());
    }

    public boolean isViewOnce() {
      return isRevealable;
    }

    public boolean isSticker() {
      return isSticker;
    }

    public @Nullable String getStickerEmoji() {
      return stickerEmoji;
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

    public boolean isGv2Invite() {
      return isGv2Invite;
    }

    public @Nullable String getGroupAddedBy() {
      return groupAddedBy;
    }
  }

  enum ReadStatus {
    READ(1), UNREAD(0), FORCED_UNREAD(2);

    private final int value;

    ReadStatus(int value) {
      this.value = value;
    }

    public static ReadStatus deserialize(int value) {
      for (ReadStatus status : ReadStatus.values()) {
        if (status.value == value) {
          return status;
        }
      }
      throw new IllegalArgumentException("No matching status for value " + value);
    }

    public int serialize() {
      return value;
    }
  }

  public static class ConversationMetadata {
    private final long    lastSeen;
    private final boolean hasSent;
    private final long    lastScrolled;

    public ConversationMetadata(long lastSeen, boolean hasSent, long lastScrolled) {
      this.lastSeen     = lastSeen;
      this.hasSent      = hasSent;
      this.lastScrolled = lastScrolled;
    }

    public long getLastSeen() {
      return lastSeen;
    }

    public boolean hasSent() {
      return hasSent;
    }

    public long getLastScrolled() {
      return lastScrolled;
    }
  }

  static final class MergeResult {
    final long    threadId;
    final long    previousThreadId;
    final boolean neededMerge;

    private MergeResult(long threadId, long previousThreadId, boolean neededMerge) {
      this.threadId         = threadId;
      this.previousThreadId = previousThreadId;
      this.neededMerge      = neededMerge;
    }
  }
}

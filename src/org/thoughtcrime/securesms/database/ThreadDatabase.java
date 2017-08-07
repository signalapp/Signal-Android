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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.RecipientPreferenceDatabase.RecipientsPreferences;
import org.thoughtcrime.securesms.database.model.DisplayRecord;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.util.DelimiterUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ThreadDatabase extends Database {

  private static final String TAG = ThreadDatabase.class.getSimpleName();

          static final String TABLE_NAME      = "thread";
  public  static final String ID              = "_id";
  public  static final String DATE            = "date";
  public  static final String MESSAGE_COUNT   = "message_count";
  public  static final String ADDRESS         = "recipient_ids";
  public  static final String SNIPPET         = "snippet";
  private static final String SNIPPET_CHARSET = "snippet_cs";
  public  static final String READ            = "read";
  public  static final String TYPE            = "type";
  private static final String ERROR           = "error";
  public  static final String SNIPPET_TYPE    = "snippet_type";
  public  static final String SNIPPET_URI     = "snippet_uri";
  public  static final String ARCHIVED        = "archived";
  public  static final String STATUS          = "status";
  public  static final String RECEIPT_COUNT   = "delivery_receipt_count";
  public  static final String EXPIRES_IN      = "expires_in";
  public  static final String LAST_SEEN       = "last_seen";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("                    +
    ID + " INTEGER PRIMARY KEY, " + DATE + " INTEGER DEFAULT 0, "                                  +
    MESSAGE_COUNT + " INTEGER DEFAULT 0, " + ADDRESS + " TEXT, " + SNIPPET + " TEXT, "       +
    SNIPPET_CHARSET + " INTEGER DEFAULT 0, " + READ + " INTEGER DEFAULT 1, "                       +
    TYPE + " INTEGER DEFAULT 0, " + ERROR + " INTEGER DEFAULT 0, "                                 +
    SNIPPET_TYPE + " INTEGER DEFAULT 0, " + SNIPPET_URI + " TEXT DEFAULT NULL, "                   +
    ARCHIVED + " INTEGER DEFAULT 0, " + STATUS + " INTEGER DEFAULT 0, "                            +
    RECEIPT_COUNT + " INTEGER DEFAULT 0, " + EXPIRES_IN + " INTEGER DEFAULT 0, "                   +
    LAST_SEEN + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON " + TABLE_NAME + " (" + ADDRESS + ");",
    "CREATE INDEX IF NOT EXISTS archived_count_index ON " + TABLE_NAME + " (" + ARCHIVED + ", " + MESSAGE_COUNT + ");",
  };

  private static final String[] THREAD_PROJECTION = {
      ID, DATE, MESSAGE_COUNT, ADDRESS, SNIPPET, SNIPPET_CHARSET, READ, TYPE, ERROR, SNIPPET_TYPE,
      SNIPPET_URI, ARCHIVED, STATUS, RECEIPT_COUNT, EXPIRES_IN, LAST_SEEN
  };

  private static final List<String> TYPED_THREAD_PROJECTION = Stream.of(THREAD_PROJECTION)
                                                                    .map(columnName -> TABLE_NAME + "." + columnName)
                                                                    .toList();

  private static final List<String> COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION = Stream.concat(Stream.concat(Stream.of(TYPED_THREAD_PROJECTION),
                                                                                                             Stream.of(RecipientPreferenceDatabase.TYPED_RECIPIENT_PROJECTION)),
                                                                                               Stream.of(GroupDatabase.TYPED_GROUP_PROJECTION))
                                                                                       .toList();

  public ThreadDatabase(Context context, SQLiteOpenHelper databaseHelper) {
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
                            long date, int status, int receiptCount, long type, boolean unarchive,
                            long expiresIn)
  {
    ContentValues contentValues = new ContentValues(7);
    contentValues.put(DATE, date - date % 1000);
    contentValues.put(MESSAGE_COUNT, count);
    contentValues.put(SNIPPET, body);
    contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());
    contentValues.put(SNIPPET_TYPE, type);
    contentValues.put(STATUS, status);
    contentValues.put(RECEIPT_COUNT, receiptCount);
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
    Log.w("ThreadDatabase", "Trimming thread: " + threadId + " to: " + length);
    Cursor cursor = null;

    try {
      cursor = DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadId);

      if (cursor != null && length > 0 && cursor.getCount() > length) {
        Log.w("ThreadDatabase", "Cursor count is greater than length!");
        cursor.moveToPosition(length - 1);

        long lastTweetDate = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.NORMALIZED_DATE_RECEIVED));

        Log.w("ThreadDatabase", "Cut off tweet date: " + lastTweetDate);

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

  public void setAllThreadsRead() {
    SQLiteDatabase db           = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, 1);

    db.update(TABLE_NAME, contentValues, null, null);

    DatabaseFactory.getSmsDatabase(context).setAllMessagesRead();
    DatabaseFactory.getMmsDatabase(context).setAllMessagesRead();
    notifyConversationListListeners();
  }

  public List<MarkedMessageInfo> setRead(long threadId, boolean lastSeen) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, 1);

    if (lastSeen) {
      contentValues.put(LAST_SEEN, System.currentTimeMillis());
    }

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId+""});

    final List<MarkedMessageInfo> smsRecords = DatabaseFactory.getSmsDatabase(context).setMessagesRead(threadId);
    final List<MarkedMessageInfo> mmsRecords = DatabaseFactory.getMmsDatabase(context).setMessagesRead(threadId);

    notifyConversationListListeners();

    return new LinkedList<MarkedMessageInfo>() {{
      addAll(smsRecords);
      addAll(mmsRecords);
    }};
  }

  public void setUnread(long threadId) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(READ, 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();
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

  public Cursor getFilteredConversationList(List<Address> filter) {
    if (filter == null || filter.size() == 0)
      return null;

    SQLiteDatabase      db                   = databaseHelper.getReadableDatabase();
    List<List<Address>> partitionedAddresses = Util.partition(filter, 900);
    List<Cursor>        cursors              = new LinkedList<>();

    for (List<Address> addresses : partitionedAddresses) {
      String   selection      = ADDRESS + " = ?";
      String[] selectionArgs  = new String[addresses.size()];

      for (int i=0;i<addresses.size()-1;i++)
        selection += (" OR " + ADDRESS + " = ?");

      int i= 0;
      for (Address address : addresses) {
        selectionArgs[i++] = DelimiterUtil.escape(address.serialize(), ' ');
      }

      cursors.add(db.query(TABLE_NAME, null, selection, selectionArgs, null, null, DATE + " DESC"));
    }

    Cursor cursor = cursors.size() > 1 ? new MergeCursor(cursors.toArray(new Cursor[cursors.size()])) : cursors.get(0);
    setNotifyConverationListListeners(cursor);
    return cursor;
  }

  public Cursor getConversationList() {
    return getConversationList("0");
  }

  public Cursor getArchivedConversationList() {
    return getConversationList("1");
  }

  private Cursor getConversationList(String archived) {
    String         projection = Util.join(COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION, ",");
    SQLiteDatabase db         = databaseHelper.getReadableDatabase();
    Cursor         cursor     = db.rawQuery("SELECT " + projection + " FROM " + TABLE_NAME +
                                            " LEFT OUTER JOIN " + RecipientPreferenceDatabase.TABLE_NAME +
                                            " ON " + TABLE_NAME + "." + ADDRESS + " = " + RecipientPreferenceDatabase.TABLE_NAME + "." + RecipientPreferenceDatabase.ADDRESS +
                                            " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME +
                                            " ON " + TABLE_NAME + "." + ADDRESS + " = " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.GROUP_ID +
                                            " WHERE " + ARCHIVED + " = ? AND " + MESSAGE_COUNT + " != 0" +
                                            " ORDER BY " + TABLE_NAME + "." + DATE + " DESC",
                                            new String[] {archived});

    setNotifyConverationListListeners(cursor);

    return cursor;
  }

  public Cursor getDirectShareList() {
    SQLiteDatabase db         = databaseHelper.getReadableDatabase();
    String         projection = Util.join(COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION, ",");

    return db.rawQuery("SELECT " + projection + " FROM " + TABLE_NAME +
                       " LEFT OUTER JOIN " + RecipientPreferenceDatabase.TABLE_NAME +
                       " ON " + TABLE_NAME + "." + ADDRESS + " = " + RecipientPreferenceDatabase.TABLE_NAME + "." + ADDRESS +
                       " ORDER BY " + TABLE_NAME + "." + DATE + " DESC",
                       null);
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
  }

  public void unarchiveConversation(long threadId) {
    SQLiteDatabase db            = databaseHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues(1);
    contentValues.put(ARCHIVED, 0);

    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public void setLastSeen(long threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(LAST_SEEN, System.currentTimeMillis());

    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(threadId)});
    notifyConversationListListeners();
  }

  public long getLastSeen(long threadId) {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    Cursor         cursor = db.query(TABLE_NAME, new String[]{LAST_SEEN}, ID_WHERE, new String[]{String.valueOf(threadId)}, null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(0);
      }

      return -1;
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
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    String where           = ADDRESS + " = ?";
    String[] recipientsArg = new String[] {recipient.getAddress().serialize()};
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

  public long getThreadIdFor(Recipient recipient) {
    return getThreadIdFor(recipient, DistributionTypes.DEFAULT);
  }

  public long getThreadIdFor(Recipient recipient, int distributionType) {
    SQLiteDatabase db            = databaseHelper.getReadableDatabase();
    String         where         = ADDRESS + " = ?";
    String[]       recipientsArg = new String[]{recipient.getAddress().serialize()};
    Cursor         cursor        = null;

    try {
      cursor = db.query(TABLE_NAME, new String[]{ID}, where, recipientsArg, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      } else {
        return createThreadForRecipient(recipient.getAddress(), recipient.isGroupRecipient(), distributionType);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public @Nullable Recipient getRecipientForThreadId(long threadId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, null, ID + " = ?", new String[] {threadId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        Address address = Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ADDRESS)));
        return RecipientFactory.getRecipientFor(context, address, false);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  public void updateReadState(long threadId) {
    int unreadCount = DatabaseFactory.getMmsSmsDatabase(context).getUnreadCount(threadId);

    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, unreadCount == 0);

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
        updateThread(threadId, count, record.getBody().getBody(), getAttachmentUriFor(record),
                     record.getTimestamp(), record.getDeliveryStatus(), record.getReceiptCount(),
                     record.getType(), unarchive, record.getExpiresIn());
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

  private @Nullable Uri getAttachmentUriFor(MessageRecord record) {
    if (!record.isMms() || record.isMmsNotification() || record.isGroupAction()) return null;

    SlideDeck slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
    Slide     thumbnail = slideDeck.getThumbnailSlide();

    return thumbnail != null ? thumbnail.getThumbnailUri() : null;
  }

  public static interface ProgressListener {
    public void onProgress(int complete, int total);
  }

  public Reader readerFor(Cursor cursor, MasterCipher masterCipher) {
    return new Reader(cursor, masterCipher);
  }

  public static class DistributionTypes {
    public static final int DEFAULT      = 2;
    public static final int BROADCAST    = 1;
    public static final int CONVERSATION = 2;
    public static final int ARCHIVE      = 3;
  }

  public class Reader {

    private final Cursor       cursor;
    private final MasterCipher masterCipher;

    public Reader(Cursor cursor, MasterCipher masterCipher) {
      this.cursor       = cursor;
      this.masterCipher = masterCipher;
    }

    public ThreadRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public ThreadRecord getCurrent() {
      long                            threadId    = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
      Address                         address     = Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.ADDRESS)));
      Optional<RecipientsPreferences> preferences = DatabaseFactory.getRecipientPreferenceDatabase(context).getRecipientPreferences(cursor);
      Optional<GroupRecord>           groupRecord = DatabaseFactory.getGroupDatabase(context).getGroup(cursor);
      Recipient                       recipient   = RecipientFactory.getRecipientFor(context, address, preferences, groupRecord, true);

      DisplayRecord.Body body             = getPlaintextBody(cursor);
      long               date             = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE));
      long               count            = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT));
      long               read             = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.READ));
      long               type             = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
      int                distributionType = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE));
      boolean            archived         = cursor.getInt(cursor.getColumnIndex(ThreadDatabase.ARCHIVED)) != 0;
      int                status           = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.STATUS));
      int                receiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.RECEIPT_COUNT));
      long               expiresIn        = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.EXPIRES_IN));
      long               lastSeen         = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.LAST_SEEN));
      Uri                snippetUri       = getSnippetUri(cursor);

      return new ThreadRecord(context, body, snippetUri, recipient, date, count, read == 1,
                              threadId, receiptCount, status, type, distributionType, archived,
                              expiresIn, lastSeen);
    }

    private DisplayRecord.Body getPlaintextBody(Cursor cursor) {
      try {
        long type   = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
        String body = cursor.getString(cursor.getColumnIndexOrThrow(SNIPPET));

        if (!TextUtils.isEmpty(body) && masterCipher != null && MmsSmsColumns.Types.isSymmetricEncryption(type)) {
          return new DisplayRecord.Body(masterCipher.decryptBody(body), true);
        } else if (!TextUtils.isEmpty(body) && masterCipher == null && MmsSmsColumns.Types.isSymmetricEncryption(type)) {
          return new DisplayRecord.Body(body, false);
        } else {
          return new DisplayRecord.Body(body, true);
        }
      } catch (InvalidMessageException e) {
        Log.w("ThreadDatabase", e);
        return new DisplayRecord.Body(context.getString(R.string.ThreadDatabase_error_decrypting_message), true);
      }
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

    public void close() {
      cursor.close();
    }
  }
}

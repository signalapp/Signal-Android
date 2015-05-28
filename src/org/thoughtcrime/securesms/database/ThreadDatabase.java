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
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.database.model.DisplayRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libaxolotl.InvalidMessageException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import de.gdata.messaging.util.PrivacyBridge;

public class ThreadDatabase extends Database {

          static final String TABLE_NAME      = "thread";
  public  static final String ID              = "_id";
  public  static final String DATE            = "date";
  public  static final String MESSAGE_COUNT   = "message_count";
  public  static final String RECIPIENT_IDS   = "recipient_ids";
  public  static final String SNIPPET         = "snippet";
  private static final String SNIPPET_CHARSET = "snippet_cs";
  public  static final String READ            = "read";
  private static final String TYPE            = "type";
  private static final String ERROR           = "error";
  private static final String HAS_ATTACHMENT  = "has_attachment";
  public  static final String SNIPPET_TYPE    = "snippet_type";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, "                             +
    DATE + " INTEGER DEFAULT 0, " + MESSAGE_COUNT + " INTEGER DEFAULT 0, "                         +
    RECIPIENT_IDS + " TEXT, " + SNIPPET + " TEXT, " + SNIPPET_CHARSET + " INTEGER DEFAULT 0, "     +
    READ + " INTEGER DEFAULT 1, " + TYPE + " INTEGER DEFAULT 0, " + ERROR + " INTEGER DEFAULT 0, " +
    SNIPPET_TYPE + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON " + TABLE_NAME + " (" + RECIPIENT_IDS + ");",
  };

  public ThreadDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  private long[] getRecipientIds(Recipients recipients) {
    Set<Long>       recipientSet  = new HashSet<Long>();
    List<Recipient> recipientList = recipients.getRecipientsList();

    for (Recipient recipient : recipientList) {
      recipientSet.add(recipient.getRecipientId());
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

  private long createThreadForRecipients(String recipients, int recipientCount, int distributionType) {
    ContentValues contentValues = new ContentValues(4);
    long date                   = System.currentTimeMillis();

    contentValues.put(DATE, date - date % 1000);
    contentValues.put(RECIPIENT_IDS, recipients);

    if (recipientCount > 1)
      contentValues.put(TYPE, distributionType);

    contentValues.put(MESSAGE_COUNT, 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    return db.insert(TABLE_NAME, null, contentValues);
  }

  private void updateThread(long threadId, long count, String body, long date, long type)
  {
    ContentValues contentValues = new ContentValues(4);
    contentValues.put(DATE, date - date % 1000);
    contentValues.put(MESSAGE_COUNT, count);
    contentValues.put(SNIPPET, body);
    contentValues.put(SNIPPET_TYPE, type);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public void updateSnippet(long threadId, String snippet, long date, long type) {
    ContentValues contentValues = new ContentValues(3);

    contentValues.put(DATE, date - date % 1000);
    contentValues.put(SNIPPET, snippet);
    contentValues.put(SNIPPET_TYPE, type);
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] {threadId + ""});
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

      if (cursor != null && cursor.getCount() > length) {
        Log.w("ThreadDatabase", "Cursor count is greater than length!");
        cursor.moveToPosition(cursor.getCount() - length);

        long lastTweetDate = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.NORMALIZED_DATE_RECEIVED));

        Log.w("ThreadDatabase", "Cut off tweet date: " + lastTweetDate);

        DatabaseFactory.getSmsDatabase(context).deleteMessagesInThreadBeforeDate(threadId, lastTweetDate);
        DatabaseFactory.getMmsDatabase(context).deleteMessagesInThreadBeforeDate(threadId, lastTweetDate);

        update(threadId);
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
    contentValues.put(READ, 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId+""});
    notifyConversationListListeners();
  }

  public void setDistributionType(long threadId, int distributionType) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(TYPE, distributionType);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {threadId+""});
    notifyConversationListListeners();
  }

  public Cursor getFilteredConversationList(List<String> filter) {
    if (filter == null || filter.size() == 0)
      return null;

    List<Long> rawRecipientIds = DatabaseFactory.getAddressDatabase(context).getCanonicalAddressIds(filter);

    if (rawRecipientIds == null || rawRecipientIds.size() == 0)
      return null;

    SQLiteDatabase   db                      = databaseHelper.getReadableDatabase();
    List<List<Long>> partitionedRecipientIds = Util.partition(rawRecipientIds, 900);
    List<Cursor>     cursors                 = new LinkedList<>();

    for (List<Long> recipientIds : partitionedRecipientIds) {
      String   selection      = RECIPIENT_IDS + " = ?";
      String[] selectionArgs  = new String[recipientIds.size()];

      for (int i=0;i<recipientIds.size()-1;i++)
        selection += (" OR " + RECIPIENT_IDS + " = ?");

      int i= 0;
      for (long id : recipientIds) {
        selectionArgs[i++] = String.valueOf(id);
      }

      cursors.add(db.query(TABLE_NAME, null, selection, selectionArgs, null, null, DATE + " DESC"));
    }

    Cursor cursor = cursors.size() > 1 ? new MergeCursor(cursors.toArray(new Cursor[cursors.size()])) : cursors.get(0);
    setNotifyConverationListListeners(cursor);
    return cursor;
  }

  public Cursor getConversationList() {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     =  db.query(TABLE_NAME, null,  PrivacyBridge.getConversationSelection(context),
        PrivacyBridge.getConversationSelectionArgs(context), null, null, DATE + " DESC");
    setNotifyConverationListListeners(cursor);
    return cursor;
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
    return getThreadIdFor(recipients, DistributionTypes.DEFAULT);
  }

  public long getThreadIdFor(Recipients recipients, int distributionType) {
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
        return createThreadForRecipients(recipientsList, recipientIds.length, distributionType);
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public @Nullable Recipients getRecipientsForThreadId(long threadId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, null, ID + " = ?", new String[] {threadId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        String recipientIds = cursor.getString(cursor.getColumnIndexOrThrow(RECIPIENT_IDS));
        return RecipientFactory.getRecipientsForIds(context, recipientIds, false);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return null;
  }

  public boolean update(long threadId) {
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
        final long timestamp;

        if (record.isPush()) timestamp = record.getDateSent();
        else                 timestamp = record.getDateReceived();

        updateThread(threadId, count, record.getBody().getBody(), timestamp, record.getType());
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
      long threadId         = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
      String recipientId    = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_IDS));
      Recipients recipients = RecipientFactory.getRecipientsForIds(context, recipientId, true);

      DisplayRecord.Body body = getPlaintextBody(cursor);
      long date               = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE));
      long count              = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT));
      long read               = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.READ));
      long type               = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
      int distributionType    = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE));

      return new ThreadRecord(context, body, recipients, date, count,
                              read == 1, threadId, type, distributionType);
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

    public void close() {
      cursor.close();
    }
  }
}

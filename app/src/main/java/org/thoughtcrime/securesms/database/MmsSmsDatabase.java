/*
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Stream;
import com.google.protobuf.InvalidProtocolBufferException;

import net.zetetic.database.sqlcipher.SQLiteQueryBuilder;

import org.signal.core.util.CursorUtil;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.database.MessageDatabase.MessageUpdate;
import org.thoughtcrime.securesms.database.MessageDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.model.MessageExportStatus;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.notifications.v2.DefaultMessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.thoughtcrime.securesms.database.MmsSmsColumns.Types.GROUP_V2_LEAVE_BITS;

public class MmsSmsDatabase extends Database {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(MmsSmsDatabase.class);

  public static final String TRANSPORT     = "transport_type";
  public static final String MMS_TRANSPORT = "mms";
  public static final String SMS_TRANSPORT = "sms";

  private static final String[] PROJECTION = {MmsSmsColumns.ID,
                                              MmsSmsColumns.UNIQUE_ROW_ID,
                                              SmsDatabase.BODY,
                                              SmsDatabase.TYPE,
                                              MmsSmsColumns.THREAD_ID,
                                              SmsDatabase.RECIPIENT_ID,
                                              SmsDatabase.ADDRESS_DEVICE_ID,
                                              SmsDatabase.SUBJECT,
                                              MmsSmsColumns.NORMALIZED_DATE_SENT,
                                              MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                                              MmsSmsColumns.DATE_SERVER,
                                              MmsDatabase.MESSAGE_TYPE,
                                              MmsDatabase.MESSAGE_BOX,
                                              SmsDatabase.STATUS,
                                              MmsSmsColumns.UNIDENTIFIED,
                                              MmsDatabase.PART_COUNT,
                                              MmsDatabase.CONTENT_LOCATION,
                                              MmsDatabase.TRANSACTION_ID,
                                              MmsDatabase.MESSAGE_SIZE,
                                              MmsDatabase.EXPIRY,
                                              MmsDatabase.STATUS,
                                              MmsSmsColumns.DELIVERY_RECEIPT_COUNT,
                                              MmsSmsColumns.READ_RECEIPT_COUNT,
                                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                                              MmsDatabase.NETWORK_FAILURE,
                                              MmsSmsColumns.SUBSCRIPTION_ID,
                                              MmsSmsColumns.EXPIRES_IN,
                                              MmsSmsColumns.EXPIRE_STARTED,
                                              MmsSmsColumns.NOTIFIED,
                                              TRANSPORT,
                                              AttachmentDatabase.ATTACHMENT_JSON_ALIAS,
                                              MmsDatabase.QUOTE_ID,
                                              MmsDatabase.QUOTE_AUTHOR,
                                              MmsDatabase.QUOTE_BODY,
                                              MmsDatabase.QUOTE_MISSING,
                                              MmsDatabase.QUOTE_ATTACHMENT,
                                              MmsDatabase.QUOTE_TYPE,
                                              MmsDatabase.QUOTE_MENTIONS,
                                              MmsDatabase.SHARED_CONTACTS,
                                              MmsDatabase.LINK_PREVIEWS,
                                              MmsDatabase.VIEW_ONCE,
                                              MmsSmsColumns.READ,
                                              MmsSmsColumns.REACTIONS_UNREAD,
                                              MmsSmsColumns.REACTIONS_LAST_SEEN,
                                              MmsSmsColumns.REMOTE_DELETED,
                                              MmsDatabase.MENTIONS_SELF,
                                              MmsSmsColumns.NOTIFIED_TIMESTAMP,
                                              MmsSmsColumns.VIEWED_RECEIPT_COUNT,
                                              MmsSmsColumns.RECEIPT_TIMESTAMP,
                                              MmsDatabase.MESSAGE_RANGES,
                                              MmsDatabase.STORY_TYPE,
                                              MmsDatabase.PARENT_STORY_ID};

  private static final String SNIPPET_QUERY = "SELECT " + MmsSmsColumns.ID + ", 0 AS " + TRANSPORT + ", " + SmsDatabase.TYPE + " AS " + MmsSmsColumns.NORMALIZED_TYPE + ", " + SmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " FROM " + SmsDatabase.TABLE_NAME + " " +
                                              "WHERE " + MmsSmsColumns.THREAD_ID + " = ? AND " + SmsDatabase.TYPE + " NOT IN (" + SmsDatabase.Types.PROFILE_CHANGE_TYPE + ", " + SmsDatabase.Types.GV1_MIGRATION_TYPE + ", " + SmsDatabase.Types.CHANGE_NUMBER_TYPE + ", " + SmsDatabase.Types.BOOST_REQUEST_TYPE + ", " + SmsDatabase.Types.SMS_EXPORT_TYPE + ") AND " + SmsDatabase.TYPE + " & " + GROUP_V2_LEAVE_BITS + " != " + GROUP_V2_LEAVE_BITS + " " +
                                              "UNION ALL " +
                                              "SELECT " + MmsSmsColumns.ID + ", 1 AS " + TRANSPORT + ", " + MmsDatabase.MESSAGE_BOX + " AS " + MmsSmsColumns.NORMALIZED_TYPE + ", " + MmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " FROM " + MmsDatabase.TABLE_NAME + " " +
                                              "WHERE " + MmsSmsColumns.THREAD_ID + " = ? AND " + MmsDatabase.MESSAGE_BOX + " & " + GROUP_V2_LEAVE_BITS + " != " + GROUP_V2_LEAVE_BITS + " AND " + MmsDatabase.STORY_TYPE + " = 0 AND " + MmsDatabase.PARENT_STORY_ID + " <= 0 " +
                                              "ORDER BY " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC " +
                                              "LIMIT 1";

  public MmsSmsDatabase(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  /**
   * @return The user that added you to the group, otherwise null.
   */
  public @Nullable RecipientId getGroupAddedBy(long threadId) {
    long lastQuitChecked = System.currentTimeMillis();
    Pair<RecipientId, Long> pair;

    do {
      pair = getGroupAddedBy(threadId, lastQuitChecked);
      if (pair.first() != null) {
        return pair.first();
      } else {
        lastQuitChecked = pair.second();
      }

    } while (pair.second() != -1);

    return null;
  }

  private @NonNull Pair<RecipientId, Long> getGroupAddedBy(long threadId, long lastQuitChecked) {
    MessageDatabase mmsDatabase = SignalDatabase.mms();
    MessageDatabase smsDatabase = SignalDatabase.sms();
    long            latestQuit  = mmsDatabase.getLatestGroupQuitTimestamp(threadId, lastQuitChecked);
    RecipientId     id          = smsDatabase.getOldestGroupUpdateSender(threadId, latestQuit);

    return new Pair<>(id, latestQuit);
  }

  public int getMessagePositionOnOrAfterTimestamp(long threadId, long timestamp) {
    String[] projection = new String[] { "COUNT(*)" };
    String   selection  = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " +
                          MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " >= " + timestamp + " AND " +
                          MmsDatabase.STORY_TYPE + " = 0 AND " + MmsDatabase.PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = queryTables(projection, selection, null, null, false)) {
      if (cursor != null && cursor.moveToNext()) {
        return cursor.getInt(0);
      }
    }
    return 0;
  }

  public @Nullable MessageRecord getMessageFor(long timestamp, RecipientId authorId) {
    Recipient author = Recipient.resolved(authorId);

    try (Cursor cursor = queryTables(PROJECTION, MmsSmsColumns.NORMALIZED_DATE_SENT + " = " + timestamp, null, null, true)) {
      MmsSmsDatabase.Reader reader = readerFor(cursor);

      MessageRecord messageRecord;

      while ((messageRecord = reader.getNext()) != null) {
        if ((author.isSelf() && messageRecord.isOutgoing()) ||
            (!author.isSelf() && messageRecord.getIndividualRecipient().getId().equals(authorId)))
        {
          return messageRecord;
        }
      }
    }

    return null;
  }

  public @NonNull List<MessageRecord> getMessagesAfterVoiceNoteInclusive(long messageId, long limit) throws NoSuchMessageException {
    MessageRecord       origin = SignalDatabase.mms().getMessageRecord(messageId);
    List<MessageRecord> mms    = SignalDatabase.mms().getMessagesInThreadAfterInclusive(origin.getThreadId(), origin.getDateReceived(), limit);
    List<MessageRecord> sms    = SignalDatabase.sms().getMessagesInThreadAfterInclusive(origin.getThreadId(), origin.getDateReceived(), limit);

    mms.addAll(sms);
    Collections.sort(mms, (a, b) -> Long.compare(a.getDateReceived(), b.getDateReceived()));

    return Stream.of(mms).limit(limit).toList();
  }


  public Cursor getConversation(long threadId, long offset, long limit) {
    SQLiteDatabase db        = databaseHelper.getSignalReadableDatabase();
    String         order     = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
    String         selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MmsDatabase.STORY_TYPE + " = 0 AND " + MmsDatabase.PARENT_STORY_ID + " <= 0";
    String         limitStr  = limit > 0 || offset > 0 ? offset + ", " + limit : null;
    String         query     = buildQuery(PROJECTION, selection, order, limitStr, false);

    return db.rawQuery(query, null);
  }

  public Cursor getConversation(long threadId) {
    return getConversation(threadId, 0, 0);
  }

  public @NonNull MessageRecord getConversationSnippet(long threadId) throws NoSuchMessageException {
    try (Cursor cursor = getConversationSnippetCursor(threadId)) {
      if (cursor.moveToFirst()) {
        boolean isMms = CursorUtil.requireBoolean(cursor, TRANSPORT);
        long    id    = CursorUtil.requireLong(cursor, MmsSmsColumns.ID);

        if (isMms) {
          return SignalDatabase.mms().getMessageRecord(id);
        } else {
          return SignalDatabase.sms().getMessageRecord(id);
        }
      } else {
        throw new NoSuchMessageException("no message");
      }
    }
  }

  @VisibleForTesting
  @NonNull Cursor getConversationSnippetCursor(long threadId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    return db.rawQuery(SNIPPET_QUERY, SqlUtil.buildArgs(threadId, threadId));
  }

  public long getConversationSnippetType(long threadId) throws NoSuchMessageException {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    try (Cursor cursor = db.rawQuery(SNIPPET_QUERY, SqlUtil.buildArgs(threadId, threadId))) {
      if (cursor.moveToFirst()) {
        return CursorUtil.requireLong(cursor, MmsSmsColumns.NORMALIZED_TYPE);
      } else {
        throw new NoSuchMessageException("no message");
      }
    }
  }

  public Cursor getMessagesForNotificationState(Collection<DefaultMessageNotifier.StickyThread> stickyThreads) {
    StringBuilder stickyQuery = new StringBuilder();
    for (DefaultMessageNotifier.StickyThread stickyThread : stickyThreads) {
      if (stickyQuery.length() > 0) {
        stickyQuery.append(" OR ");
      }
      stickyQuery.append("(")
                 .append(MmsSmsColumns.THREAD_ID + " = ")
                 .append(stickyThread.getConversationId().getThreadId())
                 .append(" AND ")
                 .append(MmsSmsColumns.NORMALIZED_DATE_RECEIVED)
                 .append(" >= ")
                 .append(stickyThread.getEarliestTimestamp())
                 .append(getStickyWherePartForParentStoryId(stickyThread.getConversationId().getGroupStoryId()))
                 .append(")");
    }

    String order     = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";
    String selection = MmsSmsColumns.NOTIFIED + " = 0 AND " + MmsDatabase.STORY_TYPE + " = 0 AND (" + MmsSmsColumns.READ + " = 0 OR " + MmsSmsColumns.REACTIONS_UNREAD + " = 1" + (stickyQuery.length() > 0 ? " OR (" + stickyQuery + ")" : "") + ")";

    return queryTables(PROJECTION, selection, order, null, true);
  }

  /**
   * Whether or not the message has been quoted by another message.
   */
  public boolean isQuoted(@NonNull MessageRecord messageRecord) {
    RecipientId author    = messageRecord.isOutgoing() ? Recipient.self().getId() : messageRecord.getRecipient().getId();
    long        timestamp = messageRecord.getDateSent();

    String   where      = MmsDatabase.QUOTE_ID +  " = ?  AND " + MmsDatabase.QUOTE_AUTHOR + " = ?";
    String[] whereArgs  = SqlUtil.buildArgs(timestamp, author);

    try (Cursor cursor = getReadableDatabase().query(MmsDatabase.TABLE_NAME, new String[]{ "1" }, where, whereArgs, null, null, null, "1")) {
      return cursor.moveToFirst();
    }
  }

  public List<MessageRecord> getAllMessagesThatQuote(@NonNull MessageId id) {
    MessageRecord targetMessage;
    try {
      targetMessage = id.isMms() ? SignalDatabase.mms().getMessageRecord(id.getId()) : SignalDatabase.sms().getMessageRecord(id.getId());
    } catch (NoSuchMessageException e) {
      throw new IllegalArgumentException("Invalid message ID!");
    }

    RecipientId author = targetMessage.isOutgoing() ? Recipient.self().getId() : targetMessage.getRecipient().getId();
    String      query  = MmsDatabase.QUOTE_ID + " = " + targetMessage.getDateSent() + " AND " + MmsDatabase.QUOTE_AUTHOR + " = " + author.serialize();
    String      order  = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";

    List<MessageRecord> records = new ArrayList<>();

    try (Reader reader = new Reader(queryTables(PROJECTION, query, order, null, true))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null) {
        records.add(record);
        records.addAll(getAllMessagesThatQuote(new MessageId(record.getId(), record.isMms())));
      }
    }

    Collections.sort(records, (lhs, rhs) -> {
      if (lhs.getDateReceived() > rhs.getDateReceived()) {
        return -1;
      } else if (lhs.getDateReceived() < rhs.getDateReceived()) {
        return 1;
      } else {
        return 0;
      }
    });

    return records;
  }

  private @NonNull String getStickyWherePartForParentStoryId(@Nullable Long parentStoryId) {
    if (parentStoryId == null) {
      return " AND " + MmsDatabase.PARENT_STORY_ID + " <= 0";
    }

    return " AND " + MmsDatabase.PARENT_STORY_ID + " = " + parentStoryId;
  }

  public int getUnreadCount(long threadId) {
    String selection = MmsSmsColumns.READ + " = 0 AND " + MmsDatabase.STORY_TYPE + " = 0 AND " + MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MmsDatabase.PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = queryTables(PROJECTION, selection, null, null, false)) {
      return cursor != null ? cursor.getCount() : 0;
    }
  }

  public boolean checkMessageExists(@NonNull MessageRecord messageRecord) {
    MessageDatabase db = messageRecord.isMms() ? SignalDatabase.mms()
                                               : SignalDatabase.sms();

    try (Cursor cursor = db.getMessageCursor(messageRecord.getId())) {
      return cursor != null && cursor.getCount() > 0;
    }
  }

  public int getSecureConversationCount(long threadId) {
    if (threadId == -1) {
      return 0;
    }

    int count = SignalDatabase.sms().getSecureMessageCount(threadId);
    count    += SignalDatabase.mms().getSecureMessageCount(threadId);

    return count;
  }

  public int getOutgoingSecureConversationCount(long threadId) {
    if (threadId == -1L) {
      return 0;
    }

    int count = SignalDatabase.sms().getOutgoingSecureMessageCount(threadId);
    count    += SignalDatabase.mms().getOutgoingSecureMessageCount(threadId);

    return count;
  }

  public int getConversationCount(long threadId) {
    int count = SignalDatabase.sms().getMessageCountForThread(threadId);
    count    += SignalDatabase.mms().getMessageCountForThread(threadId);

    return count;
  }

  public int getConversationCount(long threadId, long beforeTime) {
    return SignalDatabase.sms().getMessageCountForThread(threadId, beforeTime) +
           SignalDatabase.mms().getMessageCountForThread(threadId, beforeTime);
  }

  public int getInsecureSentCount(long threadId) {
    int count  = SignalDatabase.sms().getInsecureMessagesSentForThread(threadId);
    count     += SignalDatabase.mms().getInsecureMessagesSentForThread(threadId);

    return count;
  }

  public int getInsecureMessageCountForInsights() {
    int count = SignalDatabase.sms().getInsecureMessageCountForInsights();
    count    += SignalDatabase.mms().getInsecureMessageCountForInsights();

    return count;
  }

  public int getUnexportedInsecureMessagesCount() {
    return getUnexportedInsecureMessagesCount(-1);
  }

  public int getUnexportedInsecureMessagesCount(long threadId) {
    int count = SignalDatabase.sms().getUnexportedInsecureMessagesCount(threadId);
    count    += SignalDatabase.mms().getUnexportedInsecureMessagesCount(threadId);

    return count;
  }

  public int getIncomingMeaningfulMessageCountSince(long threadId, long afterTime) {
    int count = SignalDatabase.sms().getIncomingMeaningfulMessageCountSince(threadId, afterTime);
    count    += SignalDatabase.mms().getIncomingMeaningfulMessageCountSince(threadId, afterTime);

    return count;
  }

  public int getMessageCountBeforeDate(long date) {
    String selection = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " < " + date;

    try (Cursor cursor = queryTables(new String[] { "COUNT(*)" }, selection, null, null, false)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public int getSecureMessageCountForInsights() {
    int count = SignalDatabase.sms().getSecureMessageCountForInsights();
    count    += SignalDatabase.mms().getSecureMessageCountForInsights();

    return count;
  }

  public boolean hasMeaningfulMessage(long threadId) {
    if (threadId == -1) {
      return false;
    }

    return SignalDatabase.sms().hasMeaningfulMessage(threadId) ||
           SignalDatabase.mms().hasMeaningfulMessage(threadId);
  }

  public long getThreadId(MessageId messageId) {
    if (messageId.isMms()) {
      return SignalDatabase.mms().getThreadIdForMessage(messageId.getId());
    } else {
      return SignalDatabase.sms().getThreadIdForMessage(messageId.getId());
    }
  }

  /**
   * This is currently only used in an old migration and shouldn't be used by anyone else, just because it flat-out isn't correct.
   */
  @Deprecated
  public long getThreadForMessageId(long messageId) {
    long id = SignalDatabase.sms().getThreadIdForMessage(messageId);

    if (id == -1) return SignalDatabase.mms().getThreadIdForMessage(messageId);
    else          return id;
  }

  public Collection<SyncMessageId> incrementDeliveryReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, MessageDatabase.ReceiptType.DELIVERY);
  }

  public boolean incrementDeliveryReceiptCount(SyncMessageId syncMessageId, long timestamp) {
    return incrementReceiptCount(syncMessageId, timestamp, MessageDatabase.ReceiptType.DELIVERY);
  }

  /**
   * @return A list of ID's that were not updated.
   */
  public @NonNull Collection<SyncMessageId> incrementReadReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, MessageDatabase.ReceiptType.READ);
  }

  public boolean incrementReadReceiptCount(SyncMessageId syncMessageId, long timestamp) {
    return incrementReceiptCount(syncMessageId, timestamp, MessageDatabase.ReceiptType.READ);
  }

  /**
   * @return A list of ID's that were not updated.
   */
  public @NonNull Collection<SyncMessageId> incrementViewedReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, MessageDatabase.ReceiptType.VIEWED);
  }

  public @NonNull Collection<SyncMessageId> incrementViewedNonStoryReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, MessageDatabase.ReceiptType.VIEWED, MessageDatabase.MessageQualifier.NORMAL);
  }

  public boolean incrementViewedReceiptCount(SyncMessageId syncMessageId, long timestamp) {
    return incrementReceiptCount(syncMessageId, timestamp, MessageDatabase.ReceiptType.VIEWED);
  }

  public @NonNull Collection<SyncMessageId> incrementViewedStoryReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    SQLiteDatabase            db             = databaseHelper.getSignalWritableDatabase();
    Set<MessageUpdate>        messageUpdates = new HashSet<>();
    Collection<SyncMessageId> unhandled      = new HashSet<>();

    db.beginTransaction();
    try {
      for (SyncMessageId id : syncMessageIds) {
        Set<MessageUpdate> updates = incrementReceiptCountInternal(id, timestamp, MessageDatabase.ReceiptType.VIEWED, MessageDatabase.MessageQualifier.STORY);

        if (updates.size() > 0) {
          messageUpdates.addAll(updates);
        } else {
          unhandled.add(id);
        }
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();

      for (MessageUpdate update : messageUpdates) {
        ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(update.getMessageId());
        ApplicationDependencies.getDatabaseObserver().notifyVerboseConversationListeners(Collections.singleton(update.getThreadId()));
      }

      if (messageUpdates.size() > 0) {
        notifyConversationListListeners();
      }
    }

    return unhandled;
  }

  /**
   * Wraps a single receipt update in a transaction and triggers the proper updates.
   *
   * @return Whether or not some thread was updated.
   */
  private boolean incrementReceiptCount(SyncMessageId syncMessageId, long timestamp, @NonNull MessageDatabase.ReceiptType receiptType) {
    return incrementReceiptCount(syncMessageId, timestamp, receiptType, MessageDatabase.MessageQualifier.ALL);
  }

  private boolean incrementReceiptCount(SyncMessageId syncMessageId, long timestamp, @NonNull MessageDatabase.ReceiptType receiptType, @NonNull MessageDatabase.MessageQualifier messageQualifier) {
    SQLiteDatabase     db             = databaseHelper.getSignalWritableDatabase();
    ThreadDatabase     threadDatabase = SignalDatabase.threads();
    Set<MessageUpdate> messageUpdates = new HashSet<>();

    db.beginTransaction();
    try {
      messageUpdates = incrementReceiptCountInternal(syncMessageId, timestamp, receiptType, messageQualifier);

      for (MessageUpdate messageUpdate : messageUpdates) {
        threadDatabase.update(messageUpdate.getThreadId(), false);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();

      for (MessageUpdate threadUpdate : messageUpdates) {
        ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(threadUpdate.getMessageId());
      }
    }

    return messageUpdates.size() > 0;
  }

  /**
   * Wraps multiple receipt updates in a transaction and triggers the proper updates.
   *
   * @return All of the messages that didn't result in updates.
   */
  private @NonNull Collection<SyncMessageId> incrementReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp, @NonNull MessageDatabase.ReceiptType receiptType) {
    return incrementReceiptCounts(syncMessageIds, timestamp, receiptType, MessageDatabase.MessageQualifier.ALL);
  }

  private @NonNull Collection<SyncMessageId> incrementReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp, @NonNull MessageDatabase.ReceiptType receiptType, @NonNull MessageDatabase.MessageQualifier messageQualifier) {
    SQLiteDatabase            db             = databaseHelper.getSignalWritableDatabase();
    ThreadDatabase            threadDatabase = SignalDatabase.threads();
    Set<MessageUpdate>        messageUpdates = new HashSet<>();
    Collection<SyncMessageId> unhandled      = new HashSet<>();

    db.beginTransaction();
    try {
      for (SyncMessageId id : syncMessageIds) {
        Set<MessageUpdate> updates = incrementReceiptCountInternal(id, timestamp, receiptType, messageQualifier);

        if (updates.size() > 0) {
          messageUpdates.addAll(updates);
        } else {
          unhandled.add(id);
        }
      }

      for (MessageUpdate update : messageUpdates) {
        threadDatabase.updateSilently(update.getThreadId(), false);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();

      for (MessageUpdate update : messageUpdates) {
        ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(update.getMessageId());
        ApplicationDependencies.getDatabaseObserver().notifyVerboseConversationListeners(Collections.singleton(update.getThreadId()));
      }

      if (messageUpdates.size() > 0) {
        notifyConversationListListeners();
      }
    }

    return unhandled;
  }


  /**
   * Doesn't do any transactions or updates, so we can re-use the method safely.
   */
  private @NonNull Set<MessageUpdate> incrementReceiptCountInternal(SyncMessageId syncMessageId, long timestamp, MessageDatabase.ReceiptType receiptType, @NonNull MessageDatabase.MessageQualifier messageQualifier) {
    Set<MessageUpdate> messageUpdates = new HashSet<>();

    messageUpdates.addAll(SignalDatabase.sms().incrementReceiptCount(syncMessageId, timestamp, receiptType, messageQualifier));
    messageUpdates.addAll(SignalDatabase.mms().incrementReceiptCount(syncMessageId, timestamp, receiptType, messageQualifier));

    return messageUpdates;
  }

  public void updateViewedStories(@NonNull Set<SyncMessageId> syncMessageIds) {
    SignalDatabase.mms().updateViewedStories(syncMessageIds);
  }

  private @NonNull MessageExportState getMessageExportState(@NonNull MessageId messageId) throws NoSuchMessageException {
    String   table      = messageId.isMms() ? MmsDatabase.TABLE_NAME : SmsDatabase.TABLE_NAME;
    String[] projection = SqlUtil.buildArgs(MmsSmsColumns.EXPORT_STATE);
    String[] args       = SqlUtil.buildArgs(messageId.getId());

    try (Cursor cursor = getReadableDatabase().query(table, projection, ID_WHERE, args, null, null, null, null)) {
      if (cursor.moveToFirst()) {
        byte[] bytes = CursorUtil.requireBlob(cursor,  MmsSmsColumns.EXPORT_STATE);
        if (bytes == null) {
          return MessageExportState.getDefaultInstance();
        } else {
          try {
            return MessageExportState.parseFrom(bytes);
          } catch (InvalidProtocolBufferException e) {
            return MessageExportState.getDefaultInstance();
          }
        }
      } else {
        throw new NoSuchMessageException("The requested message does not exist.");
      }
    }
  }

  public void updateMessageExportState(@NonNull MessageId messageId, @NonNull Function<MessageExportState, MessageExportState> transform) throws NoSuchMessageException {
    SQLiteDatabase database = getWritableDatabase();

    database.beginTransaction();
    try {
      MessageExportState oldState = getMessageExportState(messageId);
      MessageExportState newState = transform.apply(oldState);

      setMessageExportState(messageId, newState);

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
  }

  public void markMessageExported(@NonNull MessageId messageId) {
    String        table         = messageId.isMms() ? MmsDatabase.TABLE_NAME : SmsDatabase.TABLE_NAME;
    ContentValues contentValues = new ContentValues(1);

    contentValues.put(MmsSmsColumns.EXPORTED, MessageExportStatus.EXPORTED.getCode());

    getWritableDatabase().update(table, contentValues, ID_WHERE, SqlUtil.buildArgs(messageId.getId()));
  }

  public void markMessageExportFailed(@NonNull MessageId messageId) {
    String        table         = messageId.isMms() ? MmsDatabase.TABLE_NAME : SmsDatabase.TABLE_NAME;
    ContentValues contentValues = new ContentValues(1);

    contentValues.put(MmsSmsColumns.EXPORTED, MessageExportStatus.ERROR.getCode());

    getWritableDatabase().update(table, contentValues, ID_WHERE, SqlUtil.buildArgs(messageId.getId()));
  }

  private void setMessageExportState(@NonNull MessageId messageId, @NonNull MessageExportState messageExportState) {
    String        table         = messageId.isMms() ? MmsDatabase.TABLE_NAME : SmsDatabase.TABLE_NAME;
    ContentValues contentValues = new ContentValues(1);

    contentValues.put(MmsSmsColumns.EXPORT_STATE, messageExportState.toByteArray());

    getWritableDatabase().update(table, contentValues, ID_WHERE, SqlUtil.buildArgs(messageId.getId()));
  }

  /**
   * @return Unhandled ids
   */
  public Collection<SyncMessageId> setTimestampReadFromSyncMessage(@NonNull List<ReadMessage> readMessages, long proposedExpireStarted, @NonNull Map<Long, Long> threadToLatestRead) {
    SQLiteDatabase db = getWritableDatabase();

    List<Pair<Long, Long>>    expiringText   = new LinkedList<>();
    List<Pair<Long, Long>>    expiringMedia  = new LinkedList<>();
    Set<Long>                 updatedThreads = new HashSet<>();
    Collection<SyncMessageId> unhandled      = new LinkedList<>();

    db.beginTransaction();
    try {
      for (ReadMessage readMessage : readMessages) {
        RecipientId         authorId    = Recipient.externalPush(readMessage.getSender()).getId();
        TimestampReadResult textResult  = SignalDatabase.sms().setTimestampReadFromSyncMessage(new SyncMessageId(authorId, readMessage.getTimestamp()),
                                                                                               proposedExpireStarted,
                                                                                               threadToLatestRead);
        TimestampReadResult mediaResult = SignalDatabase.mms().setTimestampReadFromSyncMessage(new SyncMessageId(authorId, readMessage.getTimestamp()),
                                                                                               proposedExpireStarted,
                                                                                               threadToLatestRead);

        expiringText.addAll(textResult.expiring);
        expiringMedia.addAll(mediaResult.expiring);

        updatedThreads.addAll(textResult.threads);
        updatedThreads.addAll(mediaResult.threads);

        if (textResult.threads.isEmpty() && mediaResult.threads.isEmpty()) {
          unhandled.add(new SyncMessageId(authorId, readMessage.getTimestamp()));
        }
      }

      for (long threadId : updatedThreads) {
        SignalDatabase.threads().updateReadState(threadId);
        SignalDatabase.threads().setLastSeen(threadId);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    for (Pair<Long, Long> expiringMessage : expiringText) {
      ApplicationDependencies.getExpiringMessageManager()
                             .scheduleDeletion(expiringMessage.first(), false, proposedExpireStarted, expiringMessage.second());
    }

    for (Pair<Long, Long> expiringMessage : expiringMedia) {
      ApplicationDependencies.getExpiringMessageManager()
                             .scheduleDeletion(expiringMessage.first(), true, proposedExpireStarted, expiringMessage.second());
    }

    for (long threadId : updatedThreads) {
      notifyConversationListeners(threadId);
    }

    return unhandled;
  }

  public int getQuotedMessagePosition(long threadId, long quoteId, @NonNull RecipientId recipientId) {
    String order     = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
    String selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MmsDatabase.STORY_TYPE + " = 0" + " AND " + MmsDatabase.PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = queryTables(new String[]{ MmsSmsColumns.NORMALIZED_DATE_SENT, MmsSmsColumns.RECIPIENT_ID, MmsSmsColumns.REMOTE_DELETED}, selection, order, null, false)) {
      boolean isOwnNumber = Recipient.resolved(recipientId).isSelf();

      while (cursor != null && cursor.moveToNext()) {
        boolean quoteIdMatches     = cursor.getLong(0) == quoteId;
        boolean recipientIdMatches = recipientId.equals(RecipientId.from(CursorUtil.requireLong(cursor, MmsSmsColumns.RECIPIENT_ID)));

        if (quoteIdMatches && (recipientIdMatches || isOwnNumber)) {
          if (CursorUtil.requireBoolean(cursor, MmsSmsColumns.REMOTE_DELETED)) {
            return -1;
          } else {
            return cursor.getPosition();
          }
        }
      }
    }
    return -1;
  }

  public int getMessagePositionInConversation(long threadId, long receivedTimestamp, @NonNull RecipientId recipientId) {
    String order     = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
    String selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MmsDatabase.STORY_TYPE + " = 0" + " AND " + MmsDatabase.PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = queryTables(new String[]{ MmsSmsColumns.NORMALIZED_DATE_RECEIVED, MmsSmsColumns.RECIPIENT_ID, MmsSmsColumns.REMOTE_DELETED}, selection, order, null, false)) {
      boolean isOwnNumber = Recipient.resolved(recipientId).isSelf();

      while (cursor != null && cursor.moveToNext()) {
        boolean timestampMatches   = cursor.getLong(0) == receivedTimestamp;
        boolean recipientIdMatches = recipientId.equals(RecipientId.from(cursor.getLong(1)));


        if (timestampMatches && (recipientIdMatches || isOwnNumber)) {
          if (CursorUtil.requireBoolean(cursor, MmsSmsColumns.REMOTE_DELETED)) {
            return -1;
          } else {
            return cursor.getPosition();
          }
        }
      }
    }
    return -1;
  }

  boolean hasReceivedAnyCallsSince(long threadId, long timestamp) {
    return SignalDatabase.sms().hasReceivedAnyCallsSince(threadId, timestamp);
  }


  public int getMessagePositionInConversation(long threadId, long receivedTimestamp) {
    return getMessagePositionInConversation(threadId, 0, receivedTimestamp);
  }

  /**
   * Retrieves the position of the message with the provided timestamp in the query results you'd
   * get from calling {@link #getConversation(long)}.
   *
   * Note: This could give back incorrect results in the situation where multiple messages have the
   * same received timestamp. However, because this was designed to determine where to scroll to,
   * you'll still wind up in about the right spot.
   *
   * @param groupStoryId Ignored if passed value is <= 0
   */
  public int getMessagePositionInConversation(long threadId, long groupStoryId, long receivedTimestamp) {
    final String order;
    final String selection;

    if (groupStoryId > 0) {
      order     = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";
      selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " +
                  MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " < " + receivedTimestamp + " AND " +
                  MmsDatabase.STORY_TYPE + " = 0 AND " + MmsDatabase.PARENT_STORY_ID + " = " + groupStoryId;
    } else {
      order     = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " DESC";
      selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " +
                  MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " > " + receivedTimestamp + " AND " +
                  MmsDatabase.STORY_TYPE + " = 0 AND " + MmsDatabase.PARENT_STORY_ID + " <= 0";
    }

    try (Cursor cursor = queryTables(new String[]{ "COUNT(*)" }, selection, order, null, false)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }
    return -1;
  }

  public long getTimestampForFirstMessageAfterDate(long date) {
    String order     = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " ASC";
    String selection = MmsSmsColumns.NORMALIZED_DATE_RECEIVED + " > " + date;

    try (Cursor cursor = queryTables(new String[] { MmsSmsColumns.NORMALIZED_DATE_RECEIVED }, selection, order, "1", false)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(0);
      }
    }

    return 0;
  }

  public void setNotifiedTimestamp(long timestamp, @NonNull List<Long> smsIds, @NonNull List<Long> mmsIds) {
    SignalDatabase.sms().setNotifiedTimestamp(timestamp, smsIds);
    SignalDatabase.mms().setNotifiedTimestamp(timestamp, mmsIds);
  }

  public int deleteMessagesInThreadBeforeDate(long threadId, long trimBeforeDate) {
    Log.d(TAG, "deleteMessagesInThreadBeforeData(" + threadId + ", " + trimBeforeDate + ")");
    int deletes = SignalDatabase.sms().deleteMessagesInThreadBeforeDate(threadId, trimBeforeDate);
    deletes += SignalDatabase.mms().deleteMessagesInThreadBeforeDate(threadId, trimBeforeDate);
    return deletes;
  }

  public void deleteAbandonedMessages() {
    Log.d(TAG, "deleteAbandonedMessages()");
    SignalDatabase.sms().deleteAbandonedMessages();
    SignalDatabase.mms().deleteAbandonedMessages();
  }

  public @NonNull List<MessageDatabase.ReportSpamData> getReportSpamMessageServerData(long threadId, long timestamp, int limit) {
    List<MessageDatabase.ReportSpamData> data = new ArrayList<>();
    data.addAll(SignalDatabase.sms().getReportSpamMessageServerGuids(threadId, timestamp));
    data.addAll(SignalDatabase.mms().getReportSpamMessageServerGuids(threadId, timestamp));
    return data.stream()
               .sorted((l, r) -> -Long.compare(l.getDateReceived(), r.getDateReceived()))
               .limit(limit)
               .collect(Collectors.toList());
  }

  private static @NonNull String buildQuery(String[] projection, String selection, String order, String limit, boolean includeAttachments) {
    String attachmentJsonJoin;
    if (includeAttachments) {
      attachmentJsonJoin = "json_group_array(json_object(" + "'" + AttachmentDatabase.ROW_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + ", " +
                                                             "'" + AttachmentDatabase.UNIQUE_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UNIQUE_ID + ", " +
                                                             "'" + AttachmentDatabase.MMS_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + "," +
                                                             "'" + AttachmentDatabase.SIZE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE + ", " +
                                                             "'" + AttachmentDatabase.FILE_NAME + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FILE_NAME + ", " +
                                                             "'" + AttachmentDatabase.DATA + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DATA + ", " +
                                                             "'" + AttachmentDatabase.CONTENT_TYPE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_TYPE + ", " +
                                                             "'" + AttachmentDatabase.CDN_NUMBER + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CDN_NUMBER + ", " +
                                                             "'" + AttachmentDatabase.CONTENT_LOCATION + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_LOCATION + ", " +
                                                             "'" + AttachmentDatabase.FAST_PREFLIGHT_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FAST_PREFLIGHT_ID + ", " +
                                                             "'" + AttachmentDatabase.VOICE_NOTE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VOICE_NOTE + ", " +
                                                             "'" + AttachmentDatabase.BORDERLESS + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.BORDERLESS + ", " +
                                                             "'" + AttachmentDatabase.VIDEO_GIF + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VIDEO_GIF + ", " +
                                                             "'" + AttachmentDatabase.WIDTH + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.WIDTH + ", " +
                                                             "'" + AttachmentDatabase.HEIGHT + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.HEIGHT + ", " +
                                                             "'" + AttachmentDatabase.QUOTE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.QUOTE + ", " +
                                                             "'" + AttachmentDatabase.CONTENT_DISPOSITION + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_DISPOSITION + ", " +
                                                             "'" + AttachmentDatabase.NAME + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.NAME + ", " +
                                                             "'" + AttachmentDatabase.TRANSFER_STATE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFER_STATE + ", " +
                                                             "'" + AttachmentDatabase.CAPTION + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CAPTION + ", " +
                                                             "'" + AttachmentDatabase.STICKER_PACK_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_ID + ", " +
                                                             "'" + AttachmentDatabase.STICKER_PACK_KEY + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_KEY + ", " +
                                                             "'" + AttachmentDatabase.STICKER_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_ID + ", " +
                                                             "'" + AttachmentDatabase.STICKER_EMOJI + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_EMOJI + ", " +
                                                             "'" + AttachmentDatabase.VISUAL_HASH + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VISUAL_HASH + ", " +
                                                             "'" + AttachmentDatabase.TRANSFORM_PROPERTIES + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFORM_PROPERTIES + ", " +
                                                             "'" + AttachmentDatabase.DISPLAY_ORDER + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DISPLAY_ORDER + ", " +
                                                             "'" + AttachmentDatabase.UPLOAD_TIMESTAMP + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UPLOAD_TIMESTAMP + "))";
    } else {
      attachmentJsonJoin = "NULL";
    }

    String[] mmsProjection = {MmsDatabase.DATE_SENT + " AS " + MmsSmsColumns.NORMALIZED_DATE_SENT,
                              MmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " AS " + MmsSmsColumns.ID,
                              "'MMS::' || " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " || '::' || " + MmsDatabase.DATE_SENT + " AS " + MmsSmsColumns.UNIQUE_ROW_ID,
                              attachmentJsonJoin + " AS " + AttachmentDatabase.ATTACHMENT_JSON_ALIAS,
                              SmsDatabase.BODY,
                              MmsSmsColumns.READ,
                              MmsSmsColumns.THREAD_ID,
                              SmsDatabase.TYPE,
                              SmsDatabase.RECIPIENT_ID,
                              SmsDatabase.ADDRESS_DEVICE_ID,
                              SmsDatabase.SUBJECT,
                              MmsDatabase.MESSAGE_TYPE,
                              MmsDatabase.MESSAGE_BOX,
                              SmsDatabase.STATUS,
                              MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION,
                              MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE,
                              MmsDatabase.EXPIRY,
                              MmsDatabase.STATUS,
                              MmsDatabase.UNIDENTIFIED,
                              MmsSmsColumns.DELIVERY_RECEIPT_COUNT,
                              MmsSmsColumns.READ_RECEIPT_COUNT,
                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsSmsColumns.SUBSCRIPTION_ID,
                              MmsSmsColumns.EXPIRES_IN,
                              MmsSmsColumns.EXPIRE_STARTED,
                              MmsSmsColumns.NOTIFIED,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT,
                              MmsDatabase.QUOTE_ID,
                              MmsDatabase.QUOTE_AUTHOR,
                              MmsDatabase.QUOTE_BODY,
                              MmsDatabase.QUOTE_MISSING,
                              MmsDatabase.QUOTE_ATTACHMENT,
                              MmsDatabase.QUOTE_TYPE,
                              MmsDatabase.QUOTE_MENTIONS,
                              MmsDatabase.SHARED_CONTACTS,
                              MmsDatabase.LINK_PREVIEWS,
                              MmsDatabase.VIEW_ONCE,
                              MmsSmsColumns.REACTIONS_UNREAD,
                              MmsSmsColumns.REACTIONS_LAST_SEEN,
                              MmsSmsColumns.DATE_SERVER,
                              MmsSmsColumns.REMOTE_DELETED,
                              MmsDatabase.MENTIONS_SELF,
                              MmsSmsColumns.NOTIFIED_TIMESTAMP,
                              MmsSmsColumns.VIEWED_RECEIPT_COUNT,
                              MmsSmsColumns.RECEIPT_TIMESTAMP,
                              MmsDatabase.MESSAGE_RANGES,
                              MmsDatabase.STORY_TYPE,
                              MmsDatabase.PARENT_STORY_ID};

    String[] smsProjection = {SmsDatabase.DATE_SENT + " AS " + MmsSmsColumns.NORMALIZED_DATE_SENT,
                              SmsDatabase.DATE_RECEIVED + " AS " + MmsSmsColumns.NORMALIZED_DATE_RECEIVED,
                              MmsSmsColumns.ID, "'SMS::' || " + MmsSmsColumns.ID + " || '::' || " + SmsDatabase.DATE_SENT + " AS " + MmsSmsColumns.UNIQUE_ROW_ID,
                              "NULL AS " + AttachmentDatabase.ATTACHMENT_JSON_ALIAS,
                              SmsDatabase.BODY, MmsSmsColumns.READ, MmsSmsColumns.THREAD_ID,
                              SmsDatabase.TYPE, SmsDatabase.RECIPIENT_ID, SmsDatabase.ADDRESS_DEVICE_ID, SmsDatabase.SUBJECT, MmsDatabase.MESSAGE_TYPE,
                              MmsDatabase.MESSAGE_BOX, SmsDatabase.STATUS, MmsDatabase.PART_COUNT,
                              MmsDatabase.CONTENT_LOCATION, MmsDatabase.TRANSACTION_ID,
                              MmsDatabase.MESSAGE_SIZE, MmsDatabase.EXPIRY, MmsDatabase.STATUS,
                              MmsDatabase.UNIDENTIFIED,
                              MmsSmsColumns.DELIVERY_RECEIPT_COUNT, MmsSmsColumns.READ_RECEIPT_COUNT,
                              MmsSmsColumns.MISMATCHED_IDENTITIES,
                              MmsSmsColumns.SUBSCRIPTION_ID, MmsSmsColumns.EXPIRES_IN, MmsSmsColumns.EXPIRE_STARTED,
                              MmsSmsColumns.NOTIFIED,
                              MmsDatabase.NETWORK_FAILURE, TRANSPORT,
                              MmsDatabase.QUOTE_ID,
                              MmsDatabase.QUOTE_AUTHOR,
                              MmsDatabase.QUOTE_BODY,
                              MmsDatabase.QUOTE_MISSING,
                              MmsDatabase.QUOTE_ATTACHMENT,
                              MmsDatabase.QUOTE_TYPE,
                              MmsDatabase.QUOTE_MENTIONS,
                              MmsDatabase.SHARED_CONTACTS,
                              MmsDatabase.LINK_PREVIEWS,
                              MmsDatabase.VIEW_ONCE,
                              MmsSmsColumns.REACTIONS_UNREAD,
                              MmsSmsColumns.REACTIONS_LAST_SEEN,
                              MmsSmsColumns.DATE_SERVER,
                              MmsSmsColumns.REMOTE_DELETED,
                              MmsDatabase.MENTIONS_SELF,
                              MmsSmsColumns.NOTIFIED_TIMESTAMP,
                              MmsSmsColumns.VIEWED_RECEIPT_COUNT,
                              MmsSmsColumns.RECEIPT_TIMESTAMP,
                              MmsDatabase.MESSAGE_RANGES,
                              "0 AS " + MmsDatabase.STORY_TYPE,
                              "0 AS " + MmsDatabase.PARENT_STORY_ID};

    SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
    SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

    if (includeAttachments) {
      mmsQueryBuilder.setDistinct(true);
      smsQueryBuilder.setDistinct(true);
    }

    smsQueryBuilder.setTables(SmsDatabase.TABLE_NAME);

    if (includeAttachments) {
      mmsQueryBuilder.setTables(MmsDatabase.TABLE_NAME + " LEFT OUTER JOIN " + AttachmentDatabase.TABLE_NAME +
                                " ON " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + " = " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID);
    } else {
      mmsQueryBuilder.setTables(MmsDatabase.TABLE_NAME);
    }

    Set<String> mmsColumnsPresent = new HashSet<>();
    mmsColumnsPresent.add(MmsSmsColumns.ID);
    mmsColumnsPresent.add(MmsSmsColumns.READ);
    mmsColumnsPresent.add(MmsSmsColumns.THREAD_ID);
    mmsColumnsPresent.add(MmsSmsColumns.BODY);
    mmsColumnsPresent.add(MmsSmsColumns.RECIPIENT_ID);
    mmsColumnsPresent.add(MmsSmsColumns.ADDRESS_DEVICE_ID);
    mmsColumnsPresent.add(MmsSmsColumns.DELIVERY_RECEIPT_COUNT);
    mmsColumnsPresent.add(MmsSmsColumns.READ_RECEIPT_COUNT);
    mmsColumnsPresent.add(MmsSmsColumns.MISMATCHED_IDENTITIES);
    mmsColumnsPresent.add(MmsSmsColumns.SUBSCRIPTION_ID);
    mmsColumnsPresent.add(MmsSmsColumns.EXPIRES_IN);
    mmsColumnsPresent.add(MmsSmsColumns.EXPIRE_STARTED);
    mmsColumnsPresent.add(MmsDatabase.MESSAGE_TYPE);
    mmsColumnsPresent.add(MmsDatabase.MESSAGE_BOX);
    mmsColumnsPresent.add(MmsDatabase.DATE_SENT);
    mmsColumnsPresent.add(MmsDatabase.DATE_RECEIVED);
    mmsColumnsPresent.add(MmsDatabase.DATE_SERVER);
    mmsColumnsPresent.add(MmsDatabase.PART_COUNT);
    mmsColumnsPresent.add(MmsDatabase.CONTENT_LOCATION);
    mmsColumnsPresent.add(MmsDatabase.TRANSACTION_ID);
    mmsColumnsPresent.add(MmsDatabase.MESSAGE_SIZE);
    mmsColumnsPresent.add(MmsDatabase.EXPIRY);
    mmsColumnsPresent.add(MmsDatabase.NOTIFIED);
    mmsColumnsPresent.add(MmsDatabase.STATUS);
    mmsColumnsPresent.add(MmsDatabase.UNIDENTIFIED);
    mmsColumnsPresent.add(MmsDatabase.NETWORK_FAILURE);
    mmsColumnsPresent.add(MmsDatabase.QUOTE_ID);
    mmsColumnsPresent.add(MmsDatabase.QUOTE_AUTHOR);
    mmsColumnsPresent.add(MmsDatabase.QUOTE_BODY);
    mmsColumnsPresent.add(MmsDatabase.QUOTE_MISSING);
    mmsColumnsPresent.add(MmsDatabase.QUOTE_ATTACHMENT);
    mmsColumnsPresent.add(MmsDatabase.QUOTE_TYPE);
    mmsColumnsPresent.add(MmsDatabase.QUOTE_MENTIONS);
    mmsColumnsPresent.add(MmsDatabase.SHARED_CONTACTS);
    mmsColumnsPresent.add(MmsDatabase.LINK_PREVIEWS);
    mmsColumnsPresent.add(MmsDatabase.VIEW_ONCE);
    mmsColumnsPresent.add(MmsDatabase.REACTIONS_UNREAD);
    mmsColumnsPresent.add(MmsDatabase.REACTIONS_LAST_SEEN);
    mmsColumnsPresent.add(MmsDatabase.REMOTE_DELETED);
    mmsColumnsPresent.add(MmsDatabase.MENTIONS_SELF);
    mmsColumnsPresent.add(MmsSmsColumns.NOTIFIED_TIMESTAMP);
    mmsColumnsPresent.add(MmsSmsColumns.VIEWED_RECEIPT_COUNT);
    mmsColumnsPresent.add(MmsSmsColumns.RECEIPT_TIMESTAMP);
    mmsColumnsPresent.add(MmsDatabase.MESSAGE_RANGES);
    mmsColumnsPresent.add(MmsDatabase.STORY_TYPE);
    mmsColumnsPresent.add(MmsDatabase.PARENT_STORY_ID);

    Set<String> smsColumnsPresent = new HashSet<>();
    smsColumnsPresent.add(MmsSmsColumns.ID);
    smsColumnsPresent.add(MmsSmsColumns.BODY);
    smsColumnsPresent.add(MmsSmsColumns.RECIPIENT_ID);
    smsColumnsPresent.add(MmsSmsColumns.ADDRESS_DEVICE_ID);
    smsColumnsPresent.add(MmsSmsColumns.READ);
    smsColumnsPresent.add(MmsSmsColumns.THREAD_ID);
    smsColumnsPresent.add(MmsSmsColumns.DELIVERY_RECEIPT_COUNT);
    smsColumnsPresent.add(MmsSmsColumns.READ_RECEIPT_COUNT);
    smsColumnsPresent.add(MmsSmsColumns.MISMATCHED_IDENTITIES);
    smsColumnsPresent.add(MmsSmsColumns.SUBSCRIPTION_ID);
    smsColumnsPresent.add(MmsSmsColumns.EXPIRES_IN);
    smsColumnsPresent.add(MmsSmsColumns.EXPIRE_STARTED);
    smsColumnsPresent.add(MmsSmsColumns.NOTIFIED);
    smsColumnsPresent.add(SmsDatabase.TYPE);
    smsColumnsPresent.add(SmsDatabase.SUBJECT);
    smsColumnsPresent.add(SmsDatabase.DATE_SENT);
    smsColumnsPresent.add(SmsDatabase.DATE_RECEIVED);
    smsColumnsPresent.add(SmsDatabase.DATE_SERVER);
    smsColumnsPresent.add(SmsDatabase.STATUS);
    smsColumnsPresent.add(SmsDatabase.UNIDENTIFIED);
    smsColumnsPresent.add(SmsDatabase.REACTIONS_UNREAD);
    smsColumnsPresent.add(SmsDatabase.REACTIONS_LAST_SEEN);
    smsColumnsPresent.add(MmsSmsColumns.REMOTE_DELETED);
    smsColumnsPresent.add(MmsSmsColumns.NOTIFIED_TIMESTAMP);
    smsColumnsPresent.add(MmsSmsColumns.RECEIPT_TIMESTAMP);
    smsColumnsPresent.add("0 AS " + MmsDatabase.STORY_TYPE);
    smsColumnsPresent.add("0 AS " + MmsDatabase.PARENT_STORY_ID);

    String mmsGroupBy = includeAttachments ? MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID : null;

    String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery(TRANSPORT, mmsProjection, mmsColumnsPresent, 4, MMS_TRANSPORT, selection, null, mmsGroupBy, null);
    String smsSubQuery = smsQueryBuilder.buildUnionSubQuery(TRANSPORT, smsProjection, smsColumnsPresent, 4, SMS_TRANSPORT, selection, null, null, null);

    SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();
    String             unionQuery        = unionQueryBuilder.buildUnionQuery(new String[] { smsSubQuery, mmsSubQuery }, order, limit);

    SQLiteQueryBuilder outerQueryBuilder = new SQLiteQueryBuilder();
    outerQueryBuilder.setTables("(" + unionQuery + ")");

    return outerQueryBuilder.buildQuery(projection, null, null, null, null, null, null);
  }

  private Cursor queryTables(String[] projection, String selection, String order, String limit, boolean includeAttachments) {
    String query = buildQuery(projection, selection, order, limit, includeAttachments);

    return databaseHelper.getSignalReadableDatabase().rawQuery(query, null);
  }

  public static Reader readerFor(@NonNull Cursor cursor) {
    return new Reader(cursor);
  }

  public static class Reader implements Closeable {

    private final Cursor                 cursor;
    private       SmsDatabase.Reader     smsReader;
    private       MmsDatabase.Reader     mmsReader;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    private SmsDatabase.Reader getSmsReader() {
      if (smsReader == null) {
        smsReader = SmsDatabase.readerFor(cursor);
      }

      return smsReader;
    }

    private MmsDatabase.Reader getMmsReader() {
      if (mmsReader == null) {
        mmsReader = MmsDatabase.readerFor(cursor);
      }

      return mmsReader;
    }

    public MessageRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public MessageRecord getCurrent() {
      String type = cursor.getString(cursor.getColumnIndexOrThrow(TRANSPORT));

      if      (MmsSmsDatabase.MMS_TRANSPORT.equals(type)) return getMmsReader().getCurrent();
      else if (MmsSmsDatabase.SMS_TRANSPORT.equals(type)) return getSmsReader().getCurrent();
      else                                                throw new AssertionError("Bad type: " + type);
    }

    @Override
    public void close() {
      cursor.close();
    }
  }

  static final class TimestampReadResult {
    final List<Pair<Long, Long>> expiring;
    final List<Long> threads;

    TimestampReadResult(@NonNull List<Pair<Long, Long>> expiring, @NonNull List<Long> threads) {
      this.expiring = expiring;
      this.threads  = threads;
    }
  }
}

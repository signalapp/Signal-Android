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
import org.thoughtcrime.securesms.database.MessageTable.MessageUpdate;
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId;
import org.thoughtcrime.securesms.database.model.DisplayRecord;
import org.thoughtcrime.securesms.database.model.MessageExportStatus;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.thoughtcrime.securesms.database.MmsSmsColumns.Types.GROUP_V2_LEAVE_BITS;

public class MmsSmsTable extends DatabaseTable {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(MmsSmsTable.class);

  private static final String[] PROJECTION = { MessageTable.TABLE_NAME + "." + MessageTable.ID + " AS " + MmsSmsColumns.ID,
                                               MmsSmsColumns.BODY,
                                               MmsSmsColumns.TYPE,
                                               MmsSmsColumns.THREAD_ID,
                                               MmsSmsColumns.RECIPIENT_ID,
                                               MmsSmsColumns.RECIPIENT_DEVICE_ID,
                                               MmsSmsColumns.DATE_SENT,
                                               MmsSmsColumns.DATE_RECEIVED,
                                               MmsSmsColumns.DATE_SERVER,
                                               MessageTable.MMS_MESSAGE_TYPE,
                                               MmsSmsColumns.UNIDENTIFIED,
                                               MessageTable.MMS_CONTENT_LOCATION,
                                               MessageTable.MMS_TRANSACTION_ID,
                                               MessageTable.MMS_MESSAGE_SIZE,
                                               MessageTable.MMS_EXPIRY,
                                               MessageTable.MMS_STATUS,
                                               MmsSmsColumns.DELIVERY_RECEIPT_COUNT,
                                               MmsSmsColumns.READ_RECEIPT_COUNT,
                                               MmsSmsColumns.MISMATCHED_IDENTITIES,
                                               MessageTable.NETWORK_FAILURES,
                                               MmsSmsColumns.SMS_SUBSCRIPTION_ID,
                                               MmsSmsColumns.EXPIRES_IN,
                                               MmsSmsColumns.EXPIRE_STARTED,
                                               MmsSmsColumns.NOTIFIED,
                                               MessageTable.QUOTE_ID,
                                               MessageTable.QUOTE_AUTHOR,
                                               MessageTable.QUOTE_BODY,
                                               MessageTable.QUOTE_MISSING,
                                               MessageTable.QUOTE_TYPE,
                                               MessageTable.QUOTE_MENTIONS,
                                               MessageTable.SHARED_CONTACTS,
                                               MessageTable.LINK_PREVIEWS,
                                               MessageTable.VIEW_ONCE,
                                               MmsSmsColumns.READ,
                                               MmsSmsColumns.REACTIONS_UNREAD,
                                               MmsSmsColumns.REACTIONS_LAST_SEEN,
                                               MmsSmsColumns.REMOTE_DELETED,
                                               MessageTable.MENTIONS_SELF,
                                               MmsSmsColumns.NOTIFIED_TIMESTAMP,
                                               MmsSmsColumns.VIEWED_RECEIPT_COUNT,
                                               MmsSmsColumns.RECEIPT_TIMESTAMP,
                                               MessageTable.MESSAGE_RANGES,
                                               MessageTable.STORY_TYPE,
                                               MessageTable.PARENT_STORY_ID};

  private static final String SNIPPET_QUERY = "SELECT " + MmsSmsColumns.ID + ", " + MmsSmsColumns.TYPE + ", " + MessageTable.DATE_RECEIVED + " FROM " + MessageTable.TABLE_NAME + " " +
                                              "WHERE " + MmsSmsColumns.THREAD_ID + " = ? AND " + MessageTable.TYPE + " & " + GROUP_V2_LEAVE_BITS + " != " + GROUP_V2_LEAVE_BITS + " AND " + MessageTable.STORY_TYPE + " = 0 AND " + MessageTable.PARENT_STORY_ID + " <= 0 " +
                                              "ORDER BY " + MmsSmsColumns.DATE_RECEIVED + " DESC " +
                                              "LIMIT 1";

  public MmsSmsTable(Context context, SignalDatabase databaseHelper) {
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
    MessageTable mmsDatabase = SignalDatabase.messages();
    MessageTable smsDatabase = SignalDatabase.messages();
    long         latestQuit  = mmsDatabase.getLatestGroupQuitTimestamp(threadId, lastQuitChecked);
    RecipientId     id          = smsDatabase.getOldestGroupUpdateSender(threadId, latestQuit);

    return new Pair<>(id, latestQuit);
  }

  public int getMessagePositionOnOrAfterTimestamp(long threadId, long timestamp) {
    String[] projection = new String[] { "COUNT(*)" };
    String   selection  = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " +
                          MmsSmsColumns.DATE_RECEIVED + " >= " + timestamp + " AND " +
                          MessageTable.STORY_TYPE + " = 0 AND " + MessageTable.PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = queryTables(projection, selection, null, null, false)) {
      if (cursor != null && cursor.moveToNext()) {
        return cursor.getInt(0);
      }
    }
    return 0;
  }

  public @Nullable MessageRecord getMessageFor(long timestamp, RecipientId authorId) {
    Recipient author = Recipient.resolved(authorId);

    try (Cursor cursor = queryTables(PROJECTION, MmsSmsColumns.DATE_SENT + " = " + timestamp, null, null, true)) {
      MmsSmsTable.Reader reader = readerFor(cursor);

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
    MessageRecord       origin = SignalDatabase.messages().getMessageRecord(messageId);
    List<MessageRecord> mms    = SignalDatabase.messages().getMessagesInThreadAfterInclusive(origin.getThreadId(), origin.getDateReceived(), limit);
    List<MessageRecord> sms    = SignalDatabase.messages().getMessagesInThreadAfterInclusive(origin.getThreadId(), origin.getDateReceived(), limit);

    mms.addAll(sms);
    Collections.sort(mms, Comparator.comparingLong(DisplayRecord::getDateReceived));

    return Stream.of(mms).limit(limit).toList();
  }


  public Cursor getConversation(long threadId, long offset, long limit) {
    SQLiteDatabase db        = databaseHelper.getSignalReadableDatabase();
    String         order     = MmsSmsColumns.DATE_RECEIVED + " DESC";
    String         selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MessageTable.STORY_TYPE + " = 0 AND " + MessageTable.PARENT_STORY_ID + " <= 0";
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
        long id = CursorUtil.requireLong(cursor, MmsSmsColumns.ID);
        return SignalDatabase.messages().getMessageRecord(id);
      } else {
        throw new NoSuchMessageException("no message");
      }
    }
  }

  @VisibleForTesting
  @NonNull Cursor getConversationSnippetCursor(long threadId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    return db.rawQuery(SNIPPET_QUERY, SqlUtil.buildArgs(threadId));
  }

  public long getConversationSnippetType(long threadId) throws NoSuchMessageException {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    try (Cursor cursor = db.rawQuery(SNIPPET_QUERY, SqlUtil.buildArgs(threadId))) {
      if (cursor.moveToFirst()) {
        return CursorUtil.requireLong(cursor, MmsSmsColumns.TYPE);
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
                 .append(MmsSmsColumns.DATE_RECEIVED)
                 .append(" >= ")
                 .append(stickyThread.getEarliestTimestamp())
                 .append(getStickyWherePartForParentStoryId(stickyThread.getConversationId().getGroupStoryId()))
                 .append(")");
    }

    String order     = MmsSmsColumns.DATE_RECEIVED + " ASC";
    String selection = MmsSmsColumns.NOTIFIED + " = 0 AND " + MessageTable.STORY_TYPE + " = 0 AND (" + MmsSmsColumns.READ + " = 0 OR " + MmsSmsColumns.REACTIONS_UNREAD + " = 1" + (stickyQuery.length() > 0 ? " OR (" + stickyQuery + ")" : "") + ")";

    return queryTables(PROJECTION, selection, order, null, true);
  }

  /**
   * Whether or not the message has been quoted by another message.
   */
  public boolean isQuoted(@NonNull MessageRecord messageRecord) {
    RecipientId author    = messageRecord.isOutgoing() ? Recipient.self().getId() : messageRecord.getRecipient().getId();
    long        timestamp = messageRecord.getDateSent();

    String   where      = MessageTable.QUOTE_ID + " = ?  AND " + MessageTable.QUOTE_AUTHOR + " = ?";
    String[] whereArgs  = SqlUtil.buildArgs(timestamp, author);

    try (Cursor cursor = getReadableDatabase().query(MessageTable.TABLE_NAME, new String[]{ "1" }, where, whereArgs, null, null, null, "1")) {
      return cursor.moveToFirst();
    }
  }

  public MessageId getRootOfQuoteChain(@NonNull MessageId id) {
    MmsMessageRecord targetMessage;
    try {
      targetMessage = (MmsMessageRecord) SignalDatabase.messages().getMessageRecord(id.getId());
    } catch (NoSuchMessageException e) {
      throw new IllegalArgumentException("Invalid message ID!");
    }

    if (targetMessage.getQuote() == null) {
      return id;
    }

    String query;
    if (targetMessage.getQuote().getAuthor().equals(Recipient.self().getId())) {
      query = MessageTable.DATE_SENT + " = " + targetMessage.getQuote().getId() + " AND (" + MmsSmsColumns.TYPE + " & " + MmsSmsColumns.Types.BASE_TYPE_MASK + ") = " + MmsSmsColumns.Types.BASE_SENT_TYPE;
    } else {
      query = MessageTable.DATE_SENT + " = " + targetMessage.getQuote().getId() + " AND " + MessageTable.RECIPIENT_ID + " = '" + targetMessage.getQuote().getAuthor().serialize() + "'";
    }

    try (Reader reader = new Reader(queryTables(PROJECTION, query, null, "1", false))) {
      MessageRecord record;
      if ((record = reader.getNext()) != null) {
        return getRootOfQuoteChain(new MessageId(record.getId()));
      }
    }

    return id;
  }

  public List<MessageRecord> getAllMessagesThatQuote(@NonNull MessageId id) {
    MessageRecord targetMessage;
    try {
      targetMessage = SignalDatabase.messages().getMessageRecord(id.getId());
    } catch (NoSuchMessageException e) {
      throw new IllegalArgumentException("Invalid message ID!");
    }

    RecipientId author = targetMessage.isOutgoing() ? Recipient.self().getId() : targetMessage.getRecipient().getId();
    String      query  = MessageTable.QUOTE_ID + " = " + targetMessage.getDateSent() + " AND " + MessageTable.QUOTE_AUTHOR + " = " + author.serialize();
    String      order  = MmsSmsColumns.DATE_RECEIVED + " DESC";

    List<MessageRecord> records = new ArrayList<>();

    try (Reader reader = new Reader(queryTables(PROJECTION, query, order, null, true))) {
      MessageRecord record;
      while ((record = reader.getNext()) != null) {
        records.add(record);
        records.addAll(getAllMessagesThatQuote(new MessageId(record.getId())));
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
      return " AND " + MessageTable.PARENT_STORY_ID + " <= 0";
    }

    return " AND " + MessageTable.PARENT_STORY_ID + " = " + parentStoryId;
  }

  public int getUnreadCount(long threadId) {
    String selection = MmsSmsColumns.READ + " = 0 AND " + MessageTable.STORY_TYPE + " = 0 AND " + MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MessageTable.PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = queryTables(PROJECTION, selection, null, null, false)) {
      return cursor != null ? cursor.getCount() : 0;
    }
  }

  public boolean checkMessageExists(@NonNull MessageRecord messageRecord) {
    MessageTable db = messageRecord.isMms() ? SignalDatabase.messages()
                                            : SignalDatabase.messages();

    try (Cursor cursor = db.getMessageCursor(messageRecord.getId())) {
      return cursor != null && cursor.getCount() > 0;
    }
  }

  public int getSecureConversationCount(long threadId) {
    if (threadId == -1) {
      return 0;
    }

    return SignalDatabase.messages().getSecureMessageCount(threadId);
  }

  public int getOutgoingSecureConversationCount(long threadId) {
    if (threadId == -1L) {
      return 0;
    }

    return SignalDatabase.messages().getOutgoingSecureMessageCount(threadId);
  }

  public int getConversationCount(long threadId) {
    return SignalDatabase.messages().getMessageCountForThread(threadId);
  }

  public int getConversationCount(long threadId, long beforeTime) {
    return SignalDatabase.messages().getMessageCountForThread(threadId, beforeTime);
  }

  public int getInsecureSentCount(long threadId) {
    return SignalDatabase.messages().getInsecureMessagesSentForThread(threadId);
  }

  public int getInsecureMessageCountForInsights() {
    return SignalDatabase.messages().getInsecureMessageCountForInsights();
  }

  public int getUnexportedInsecureMessagesCount() {
    return getUnexportedInsecureMessagesCount(-1);
  }

  public int getUnexportedInsecureMessagesCount(long threadId) {
    return SignalDatabase.messages().getUnexportedInsecureMessagesCount(threadId);
  }

  public int getIncomingMeaningfulMessageCountSince(long threadId, long afterTime) {
    return SignalDatabase.messages().getIncomingMeaningfulMessageCountSince(threadId, afterTime);
  }

  public int getMessageCountBeforeDate(long date) {
    String selection = MmsSmsColumns.DATE_RECEIVED + " < " + date;

    try (Cursor cursor = queryTables(new String[] { "COUNT(*)" }, selection, null, null, false)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public int getSecureMessageCountForInsights() {
    int count = SignalDatabase.messages().getSecureMessageCountForInsights();
    count    += SignalDatabase.messages().getSecureMessageCountForInsights();

    return count;
  }

  public boolean hasMeaningfulMessage(long threadId) {
    if (threadId == -1) {
      return false;
    }

    return SignalDatabase.messages().hasMeaningfulMessage(threadId) ||
           SignalDatabase.messages().hasMeaningfulMessage(threadId);
  }

  public long getThreadId(MessageId messageId) {
    return SignalDatabase.messages().getThreadIdForMessage(messageId.getId());
  }

  /**
   * This is currently only used in an old migration and shouldn't be used by anyone else, just because it flat-out isn't correct.
   */
  @Deprecated
  public long getThreadForMessageId(long messageId) {
    long id = SignalDatabase.messages().getThreadIdForMessage(messageId);

    if (id == -1) return SignalDatabase.messages().getThreadIdForMessage(messageId);
    else          return id;
  }

  public Collection<SyncMessageId> incrementDeliveryReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, MessageTable.ReceiptType.DELIVERY);
  }

  public boolean incrementDeliveryReceiptCount(SyncMessageId syncMessageId, long timestamp) {
    return incrementReceiptCount(syncMessageId, timestamp, MessageTable.ReceiptType.DELIVERY);
  }

  /**
   * @return A list of ID's that were not updated.
   */
  public @NonNull Collection<SyncMessageId> incrementReadReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, MessageTable.ReceiptType.READ);
  }

  public boolean incrementReadReceiptCount(SyncMessageId syncMessageId, long timestamp) {
    return incrementReceiptCount(syncMessageId, timestamp, MessageTable.ReceiptType.READ);
  }

  /**
   * @return A list of ID's that were not updated.
   */
  public @NonNull Collection<SyncMessageId> incrementViewedReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, MessageTable.ReceiptType.VIEWED);
  }

  public @NonNull Collection<SyncMessageId> incrementViewedNonStoryReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, MessageTable.ReceiptType.VIEWED, MessageTable.MessageQualifier.NORMAL);
  }

  public boolean incrementViewedReceiptCount(SyncMessageId syncMessageId, long timestamp) {
    return incrementReceiptCount(syncMessageId, timestamp, MessageTable.ReceiptType.VIEWED);
  }

  public @NonNull Collection<SyncMessageId> incrementViewedStoryReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    SQLiteDatabase            db             = databaseHelper.getSignalWritableDatabase();
    Set<MessageUpdate>        messageUpdates = new HashSet<>();
    Collection<SyncMessageId> unhandled      = new HashSet<>();

    db.beginTransaction();
    try {
      for (SyncMessageId id : syncMessageIds) {
        Set<MessageUpdate> updates = incrementReceiptCountInternal(id, timestamp, MessageTable.ReceiptType.VIEWED, MessageTable.MessageQualifier.STORY);

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
  private boolean incrementReceiptCount(SyncMessageId syncMessageId, long timestamp, @NonNull MessageTable.ReceiptType receiptType) {
    return incrementReceiptCount(syncMessageId, timestamp, receiptType, MessageTable.MessageQualifier.ALL);
  }

  private boolean incrementReceiptCount(SyncMessageId syncMessageId, long timestamp, @NonNull MessageTable.ReceiptType receiptType, @NonNull MessageTable.MessageQualifier messageQualifier) {
    SQLiteDatabase     db             = databaseHelper.getSignalWritableDatabase();
    ThreadTable        threadTable    = SignalDatabase.threads();
    Set<MessageUpdate> messageUpdates = new HashSet<>();

    db.beginTransaction();
    try {
      messageUpdates = incrementReceiptCountInternal(syncMessageId, timestamp, receiptType, messageQualifier);

      for (MessageUpdate messageUpdate : messageUpdates) {
        threadTable.update(messageUpdate.getThreadId(), false);
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
  private @NonNull Collection<SyncMessageId> incrementReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp, @NonNull MessageTable.ReceiptType receiptType) {
    return incrementReceiptCounts(syncMessageIds, timestamp, receiptType, MessageTable.MessageQualifier.ALL);
  }

  private @NonNull Collection<SyncMessageId> incrementReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp, @NonNull MessageTable.ReceiptType receiptType, @NonNull MessageTable.MessageQualifier messageQualifier) {
    SQLiteDatabase     db             = databaseHelper.getSignalWritableDatabase();
    ThreadTable        threadTable    = SignalDatabase.threads();
    Set<MessageUpdate> messageUpdates = new HashSet<>();
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
        threadTable.updateSilently(update.getThreadId(), false);
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
  private @NonNull Set<MessageUpdate> incrementReceiptCountInternal(SyncMessageId syncMessageId, long timestamp, MessageTable.ReceiptType receiptType, @NonNull MessageTable.MessageQualifier messageQualifier) {
    Set<MessageUpdate> messageUpdates = new HashSet<>();

    messageUpdates.addAll(SignalDatabase.messages().incrementReceiptCount(syncMessageId, timestamp, receiptType, messageQualifier));
    messageUpdates.addAll(SignalDatabase.messages().incrementReceiptCount(syncMessageId, timestamp, receiptType, messageQualifier));

    return messageUpdates;
  }

  public void updateViewedStories(@NonNull Set<SyncMessageId> syncMessageIds) {
    SignalDatabase.messages().updateViewedStories(syncMessageIds);
  }

  private @NonNull MessageExportState getMessageExportState(@NonNull MessageId messageId) throws NoSuchMessageException {
    String   table      = MessageTable.TABLE_NAME;
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
    String        table         = MessageTable.TABLE_NAME;
    ContentValues contentValues = new ContentValues(1);

    contentValues.put(MmsSmsColumns.EXPORTED, MessageExportStatus.EXPORTED.getCode());

    getWritableDatabase().update(table, contentValues, ID_WHERE, SqlUtil.buildArgs(messageId.getId()));
  }

  public void markMessageExportFailed(@NonNull MessageId messageId) {
    String        table         = MessageTable.TABLE_NAME;
    ContentValues contentValues = new ContentValues(1);

    contentValues.put(MmsSmsColumns.EXPORTED, MessageExportStatus.ERROR.getCode());

    getWritableDatabase().update(table, contentValues, ID_WHERE, SqlUtil.buildArgs(messageId.getId()));
  }

  private void setMessageExportState(@NonNull MessageId messageId, @NonNull MessageExportState messageExportState) {
    String        table         = MessageTable.TABLE_NAME;
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
        TimestampReadResult textResult  = SignalDatabase.messages().setTimestampReadFromSyncMessage(new SyncMessageId(authorId, readMessage.getTimestamp()),
                                                                                               proposedExpireStarted,
                                                                                               threadToLatestRead);
        TimestampReadResult mediaResult = SignalDatabase.messages().setTimestampReadFromSyncMessage(new SyncMessageId(authorId, readMessage.getTimestamp()),
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
    String order     = MmsSmsColumns.DATE_RECEIVED + " DESC";
    String selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MessageTable.STORY_TYPE + " = 0" + " AND " + MessageTable.PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = queryTables(new String[]{ MmsSmsColumns.DATE_SENT, MmsSmsColumns.RECIPIENT_ID, MmsSmsColumns.REMOTE_DELETED}, selection, order, null, false)) {
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
    String order     = MmsSmsColumns.DATE_RECEIVED + " DESC";
    String selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " + MessageTable.STORY_TYPE + " = 0" + " AND " + MessageTable.PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = queryTables(new String[]{ MmsSmsColumns.DATE_RECEIVED, MmsSmsColumns.RECIPIENT_ID, MmsSmsColumns.REMOTE_DELETED}, selection, order, null, false)) {
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
    return SignalDatabase.messages().hasReceivedAnyCallsSince(threadId, timestamp);
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
      order     = MmsSmsColumns.DATE_RECEIVED + " ASC";
      selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " +
                  MmsSmsColumns.DATE_RECEIVED + " < " + receivedTimestamp + " AND " +
                  MessageTable.STORY_TYPE + " = 0 AND " + MessageTable.PARENT_STORY_ID + " = " + groupStoryId;
    } else {
      order     = MmsSmsColumns.DATE_RECEIVED + " DESC";
      selection = MmsSmsColumns.THREAD_ID + " = " + threadId + " AND " +
                  MmsSmsColumns.DATE_RECEIVED + " > " + receivedTimestamp + " AND " +
                  MessageTable.STORY_TYPE + " = 0 AND " + MessageTable.PARENT_STORY_ID + " <= 0";
    }

    try (Cursor cursor = queryTables(new String[]{ "COUNT(*)" }, selection, order, null, false)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }
    return -1;
  }

  public long getTimestampForFirstMessageAfterDate(long date) {
    String order     = MmsSmsColumns.DATE_RECEIVED + " ASC";
    String selection = MmsSmsColumns.DATE_RECEIVED + " > " + date;

    try (Cursor cursor = queryTables(new String[] { MmsSmsColumns.DATE_RECEIVED }, selection, order, "1", false)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(0);
      }
    }

    return 0;
  }

  public void setNotifiedTimestamp(long timestamp, @NonNull List<Long> smsIds, @NonNull List<Long> mmsIds) {
    SignalDatabase.messages().setNotifiedTimestamp(timestamp, smsIds);
    SignalDatabase.messages().setNotifiedTimestamp(timestamp, mmsIds);
  }

  public int deleteMessagesInThreadBeforeDate(long threadId, long trimBeforeDate) {
    Log.d(TAG, "deleteMessagesInThreadBeforeData(" + threadId + ", " + trimBeforeDate + ")");
    int deletes = SignalDatabase.messages().deleteMessagesInThreadBeforeDate(threadId, trimBeforeDate);
    deletes += SignalDatabase.messages().deleteMessagesInThreadBeforeDate(threadId, trimBeforeDate);
    return deletes;
  }

  public void deleteAbandonedMessages() {
    Log.d(TAG, "deleteAbandonedMessages()");
    SignalDatabase.messages().deleteAbandonedMessages();
    SignalDatabase.messages().deleteAbandonedMessages();
  }

  public @NonNull List<MessageTable.ReportSpamData> getReportSpamMessageServerData(long threadId, long timestamp, int limit) {
    List<MessageTable.ReportSpamData> data = new ArrayList<>();
    data.addAll(SignalDatabase.messages().getReportSpamMessageServerGuids(threadId, timestamp));
    data.addAll(SignalDatabase.messages().getReportSpamMessageServerGuids(threadId, timestamp));
    return data.stream()
               .sorted((l, r) -> -Long.compare(l.getDateReceived(), r.getDateReceived()))
               .limit(limit)
               .collect(Collectors.toList());
  }

  private static @NonNull String buildQuery(String[] projection, String selection, String order, String limit, boolean includeAttachments) {
    String attachmentJsonJoin;
    if (includeAttachments) {
      attachmentJsonJoin = "json_group_array(json_object(" + "'" + AttachmentTable.ROW_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.ROW_ID + ", " +
                           "'" + AttachmentTable.UNIQUE_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.UNIQUE_ID + ", " +
                           "'" + AttachmentTable.MMS_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + "," +
                           "'" + AttachmentTable.SIZE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.SIZE + ", " +
                           "'" + AttachmentTable.FILE_NAME + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.FILE_NAME + ", " +
                           "'" + AttachmentTable.DATA + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DATA + ", " +
                           "'" + AttachmentTable.CONTENT_TYPE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_TYPE + ", " +
                           "'" + AttachmentTable.CDN_NUMBER + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CDN_NUMBER + ", " +
                           "'" + AttachmentTable.CONTENT_LOCATION + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_LOCATION + ", " +
                           "'" + AttachmentTable.FAST_PREFLIGHT_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.FAST_PREFLIGHT_ID + ", " +
                           "'" + AttachmentTable.VOICE_NOTE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VOICE_NOTE + ", " +
                           "'" + AttachmentTable.BORDERLESS + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.BORDERLESS + ", " +
                           "'" + AttachmentTable.VIDEO_GIF + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VIDEO_GIF + ", " +
                           "'" + AttachmentTable.WIDTH + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.WIDTH + ", " +
                           "'" + AttachmentTable.HEIGHT + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.HEIGHT + ", " +
                           "'" + AttachmentTable.QUOTE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.QUOTE + ", " +
                           "'" + AttachmentTable.CONTENT_DISPOSITION + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_DISPOSITION + ", " +
                           "'" + AttachmentTable.NAME + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.NAME + ", " +
                           "'" + AttachmentTable.TRANSFER_STATE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.TRANSFER_STATE + ", " +
                           "'" + AttachmentTable.CAPTION + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CAPTION + ", " +
                           "'" + AttachmentTable.STICKER_PACK_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_PACK_ID + ", " +
                           "'" + AttachmentTable.STICKER_PACK_KEY + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_PACK_KEY + ", " +
                           "'" + AttachmentTable.STICKER_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_ID + ", " +
                           "'" + AttachmentTable.STICKER_EMOJI + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.STICKER_EMOJI + ", " +
                           "'" + AttachmentTable.VISUAL_HASH + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VISUAL_HASH + ", " +
                           "'" + AttachmentTable.TRANSFORM_PROPERTIES + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.TRANSFORM_PROPERTIES + ", " +
                           "'" + AttachmentTable.DISPLAY_ORDER + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DISPLAY_ORDER + ", " +
                           "'" + AttachmentTable.UPLOAD_TIMESTAMP + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.UPLOAD_TIMESTAMP + "))";
    } else {
      attachmentJsonJoin = "NULL";
    }

    projection = SqlUtil.appendArg(projection, attachmentJsonJoin + " AS " + AttachmentTable.ATTACHMENT_JSON_ALIAS);

    SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();

    if (includeAttachments) {
      mmsQueryBuilder.setDistinct(true);
    }

    if (includeAttachments) {
      mmsQueryBuilder.setTables(MessageTable.TABLE_NAME + " LEFT OUTER JOIN " + AttachmentTable.TABLE_NAME +
                                " ON " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + " = " + MessageTable.TABLE_NAME + "." + MessageTable.ID);
    } else {
      mmsQueryBuilder.setTables(MessageTable.TABLE_NAME);
    }

    String mmsGroupBy = includeAttachments ? MessageTable.TABLE_NAME + "." + MessageTable.ID : null;

    return mmsQueryBuilder.buildQuery(projection, selection, null, mmsGroupBy, null, order, limit);
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
    private       MessageTable.MmsReader mmsReader;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    private MessageTable.MmsReader getMmsReader() {
      if (mmsReader == null) {
        mmsReader = MessageTable.mmsReaderFor(cursor);
      }

      return mmsReader;
    }

    public MessageRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public MessageRecord getCurrent() {
      return getMmsReader().getCurrent();
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

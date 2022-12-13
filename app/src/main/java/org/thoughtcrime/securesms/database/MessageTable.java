package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.mms.pdu_alt.NotificationInd;

import net.zetetic.database.sqlcipher.SQLiteStatement;

import org.signal.core.util.CursorExtensionsKt;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SQLiteDatabaseExtensionsKt;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.database.documents.Document;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.MessageExportStatus;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ParentStoryId;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.database.model.StoryResult;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.database.model.StoryViewState;
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState;
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.insights.InsightsConstants;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.revealable.ViewOnceExpirationInfo;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.Util;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class MessageTable extends DatabaseTable implements MmsSmsColumns, RecipientIdDatabaseReference, ThreadIdDatabaseReference  {

  private static final String TAG = Log.tag(MessageTable.class);

  protected static final String   THREAD_ID_WHERE      = THREAD_ID + " = ?";
  protected static final String[] THREAD_ID_PROJECTION = new String[] { THREAD_ID };

  public MessageTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  protected abstract String getTableName();
  protected abstract String getTypeField();
  protected abstract String getDateSentColumnName();
  protected abstract String getDateReceivedColumnName();

  public abstract @Nullable RecipientId getOldestGroupUpdateSender(long threadId, long minimumDateReceived);
  public abstract long getLatestGroupQuitTimestamp(long threadId, long quitTimeBarrier);
  public abstract boolean isGroupQuitMessage(long messageId);
  public abstract @Nullable Pair<RecipientId, Long> getOldestUnreadMentionDetails(long threadId);
  public abstract int getUnreadMentionCount(long threadId);
  public abstract long getThreadIdForMessage(long id);
  public abstract int getMessageCountForThread(long threadId);
  public abstract int getMessageCountForThread(long threadId, long beforeTime);
  public abstract boolean hasMeaningfulMessage(long threadId);
  public abstract int getIncomingMeaningfulMessageCountSince(long threadId, long afterTime);
  public abstract Optional<MmsNotificationInfo> getNotification(long messageId);

  public abstract Cursor getExpirationStartedMessages();
  public abstract SmsMessageRecord getSmsMessage(long messageId) throws NoSuchMessageException;
  public abstract Reader getMessages(Collection<Long> messageIds);
  public abstract Cursor getMessageCursor(long messageId);
  public abstract OutgoingMediaMessage getOutgoingMessage(long messageId) throws MmsException, NoSuchMessageException;
  public abstract MessageRecord getMessageRecord(long messageId) throws NoSuchMessageException;
  public abstract @Nullable MessageRecord getMessageRecordOrNull(long messageId);
  public abstract boolean hasReceivedAnyCallsSince(long threadId, long timestamp);
  public abstract @Nullable ViewOnceExpirationInfo getNearestExpiringViewOnceMessage();
  public abstract boolean isSent(long messageId);
  public abstract List<MessageRecord> getProfileChangeDetailsRecords(long threadId, long afterTimestamp);
  public abstract Set<Long> getAllRateLimitedMessageIds();
  public abstract Cursor getUnexportedInsecureMessages(int limit);
  public abstract long getUnexportedInsecureMessagesEstimatedSize();
  public abstract void deleteExportedMessages();

  public abstract void markExpireStarted(long messageId);
  public abstract void markExpireStarted(long messageId, long startTime);
  public abstract void markExpireStarted(Collection<Long> messageId, long startTime);

  public abstract void markAsEndSession(long id);
  public abstract void markAsInvalidVersionKeyExchange(long id);
  public abstract void markAsSecure(long id);
  public abstract void markAsInsecure(long id);
  public abstract void markAsPush(long id);
  public abstract void markAsForcedSms(long id);
  public abstract void markAsRateLimited(long id);
  public abstract void clearRateLimitStatus(Collection<Long> ids);
  public abstract void markAsDecryptFailed(long id);
  public abstract void markAsNoSession(long id);
  public abstract void markAsUnsupportedProtocolVersion(long id);
  public abstract void markAsInvalidMessage(long id);
  public abstract void markAsLegacyVersion(long id);
  public abstract void markAsOutbox(long id);
  public abstract void markAsPendingInsecureSmsFallback(long id);
  public abstract void markAsSent(long messageId, boolean secure);
  public abstract void markUnidentified(long messageId, boolean unidentified);
  public abstract void markAsSentFailed(long id);
  public abstract void markAsSending(long messageId);
  public abstract void markAsRemoteDelete(long messageId);
  public abstract void markAsMissedCall(long id, boolean isVideoOffer);
  public abstract void markAsNotified(long id);
  public abstract void markSmsStatus(long id, int status);
  public abstract void markDownloadState(long messageId, long state);
  public abstract void markIncomingNotificationReceived(long threadId);
  public abstract void markGiftRedemptionCompleted(long messageId);
  public abstract void markGiftRedemptionStarted(long messageId);
  public abstract void markGiftRedemptionFailed(long messageId);

  public abstract Set<MessageUpdate> incrementReceiptCount(SyncMessageId messageId, long timestamp, @NonNull ReceiptType receiptType, @NonNull MessageQualifier messageType);

  public abstract List<MarkedMessageInfo> setEntireThreadRead(long threadId);
  public abstract List<MarkedMessageInfo> setMessagesReadSince(long threadId, long timestamp);
  public abstract List<MarkedMessageInfo> setAllMessagesRead();
  public abstract InsertResult updateBundleMessageBody(long messageId, String body);
  public abstract @NonNull List<MarkedMessageInfo> getViewedIncomingMessages(long threadId);
  public abstract @Nullable MarkedMessageInfo setIncomingMessageViewed(long messageId);
  public abstract @NonNull List<MarkedMessageInfo> setIncomingMessagesViewed(@NonNull List<Long> messageIds);
  public abstract @NonNull List<MarkedMessageInfo> setOutgoingGiftsRevealed(@NonNull List<Long> messageIds);

  public abstract void addFailures(long messageId, List<NetworkFailure> failure);
  public abstract void setNetworkFailures(long messageId, Set<NetworkFailure> failures);

  public abstract @NonNull Pair<Long, Long> insertReceivedCall(@NonNull RecipientId address, boolean isVideoOffer);
  public abstract @NonNull Pair<Long, Long> insertOutgoingCall(@NonNull RecipientId address, boolean isVideoOffer);
  public abstract @NonNull Pair<Long, Long> insertMissedCall(@NonNull RecipientId address, long timestamp, boolean isVideoOffer);
  public abstract void insertOrUpdateGroupCall(@NonNull RecipientId groupRecipientId,
                                               @NonNull RecipientId sender,
                                               long timestamp,
                                               @Nullable String peekGroupCallEraId,
                                               @NonNull Collection<UUID> peekJoinedUuids,
                                               boolean isCallFull);
  public abstract void insertOrUpdateGroupCall(@NonNull RecipientId groupRecipientId,
                                               @NonNull RecipientId sender,
                                               long timestamp,
                                               @Nullable String messageGroupCallEraId);
  public abstract boolean updatePreviousGroupCall(long threadId, @Nullable String peekGroupCallEraId, @NonNull Collection<UUID> peekJoinedUuids, boolean isCallFull);

  public abstract Optional<InsertResult> insertMessageInbox(IncomingTextMessage message, long type);
  public abstract Optional<InsertResult> insertMessageInbox(IncomingTextMessage message);
  public abstract Optional<InsertResult> insertMessageInbox(IncomingMediaMessage retrieved, String contentLocation, long threadId) throws MmsException;
  public abstract Pair<Long, Long> insertMessageInbox(@NonNull NotificationInd notification, int subscriptionId);
  public abstract Optional<InsertResult> insertSecureDecryptedMessageInbox(IncomingMediaMessage retrieved, long threadId) throws MmsException;
  public abstract @NonNull InsertResult insertChatSessionRefreshedMessage(@NonNull RecipientId recipientId, long senderDeviceId, long sentTimestamp);
  public abstract void insertBadDecryptMessage(@NonNull RecipientId recipientId, int senderDevice, long sentTimestamp, long receivedTimestamp, long threadId);
  public abstract long insertMessageOutbox(long threadId, OutgoingTextMessage message, boolean forceSms, long date, InsertListener insertListener);
  public abstract long insertMessageOutbox(@NonNull OutgoingMediaMessage message, long threadId, boolean forceSms, @Nullable SmsTable.InsertListener insertListener) throws MmsException;
  public abstract long insertMessageOutbox(@NonNull OutgoingMediaMessage message, long threadId, boolean forceSms, int defaultReceiptStatus, @Nullable SmsTable.InsertListener insertListener) throws MmsException;
  public abstract void insertProfileNameChangeMessages(@NonNull Recipient recipient, @NonNull String newProfileName, @NonNull String previousProfileName);
  public abstract void insertGroupV1MigrationEvents(@NonNull RecipientId recipientId, long threadId, @NonNull GroupMigrationMembershipChange membershipChange);
  public abstract void insertNumberChangeMessages(@NonNull RecipientId recipientId);
  public abstract void insertBoostRequestMessage(@NonNull RecipientId recipientId, long threadId);
  public abstract void insertThreadMergeEvent(@NonNull RecipientId recipientId, long threadId, @NonNull ThreadMergeEvent event);
  public abstract void insertSmsExportMessage(@NonNull RecipientId recipientId, long threadId);

  public abstract boolean deleteMessage(long messageId);
  abstract void deleteThread(long threadId);
  abstract int deleteMessagesInThreadBeforeDate(long threadId, long date);
  abstract void deleteThreads(@NonNull Set<Long> threadIds);
  abstract void deleteAllThreads();
  abstract void deleteAbandonedMessages();
  public abstract void deleteRemotelyDeletedStory(long messageId);

  public abstract List<MessageRecord> getMessagesInThreadAfterInclusive(long threadId, long timestamp, long limit);

  public abstract SQLiteDatabase beginTransaction();
  public abstract void endTransaction(SQLiteDatabase database);
  public abstract void setTransactionSuccessful();
  public abstract void endTransaction();

  public abstract void ensureMigration();

  public abstract boolean isStory(long messageId);
  public abstract @NonNull Reader getOutgoingStoriesTo(@NonNull RecipientId recipientId);
  public abstract @NonNull Reader getAllOutgoingStories(boolean reverse, int limit);
  public abstract @NonNull Reader getAllOutgoingStoriesAt(long sentTimestamp);
  public abstract @NonNull List<MarkedMessageInfo> markAllIncomingStoriesRead();
  public abstract @NonNull List<StoryResult> getOrderedStoryRecipientsAndIds(boolean isOutgoingOnly);

  public abstract void markOnboardingStoryRead();

  public abstract @NonNull Reader getAllStoriesFor(@NonNull RecipientId recipientId, int limit);
  public abstract @NonNull MessageId getStoryId(@NonNull RecipientId authorId, long sentTimestamp) throws NoSuchMessageException;
  public abstract int getNumberOfStoryReplies(long parentStoryId);
  public abstract @NonNull List<RecipientId>  getUnreadStoryThreadRecipientIds();
  public abstract boolean containsStories(long threadId);
  public abstract boolean hasSelfReplyInStory(long parentStoryId);
  public abstract boolean hasGroupReplyOrReactionInStory(long parentStoryId);
  public abstract @NonNull Cursor getStoryReplies(long parentStoryId);
  public abstract @Nullable Long getOldestStorySendTimestamp(boolean hasSeenReleaseChannelStories);
  public abstract int deleteStoriesOlderThan(long timestamp, boolean hasSeenReleaseChannelStories);
  public abstract @NonNull MessageTable.Reader getUnreadStories(@NonNull RecipientId recipientId, int limit);
  public abstract @Nullable ParentStoryId.GroupReply getParentStoryIdForGroupReply(long messageId);
  public abstract void deleteGroupStoryReplies(long parentStoryId);
  public abstract boolean isOutgoingStoryAlreadyInDatabase(@NonNull RecipientId recipientId, long sentTimestamp);
  public abstract @NonNull List<MarkedMessageInfo> setGroupStoryMessagesReadSince(long threadId, long groupStoryId, long sinceTimestamp);
  public abstract @NonNull List<StoryType> getStoryTypes(@NonNull List<MessageId> messageIds);

  public abstract @NonNull StoryViewState getStoryViewState(@NonNull RecipientId recipientId);
  public abstract void updateViewedStories(@NonNull Set<SyncMessageId> syncMessageIds);

  final @NonNull String getOutgoingTypeClause() {
    List<String> segments = new ArrayList<>(Types.OUTGOING_MESSAGE_TYPES.length);
    for (long outgoingMessageType : Types.OUTGOING_MESSAGE_TYPES) {
      segments.add("(" + getTableName() + "." + getTypeField() + " & " + Types.BASE_TYPE_MASK + " = " + outgoingMessageType + ")");
    }

    return Util.join(segments, " OR ");
  }

  final int getInsecureMessagesSentForThread(long threadId) {
    SQLiteDatabase db         = databaseHelper.getSignalReadableDatabase();
    String[]       projection = new String[]{"COUNT(*)"};
    String         query      = THREAD_ID + " = ? AND " + getOutgoingInsecureMessageClause() + " AND " + getDateSentColumnName() + " > ?";
    String[]       args       = new String[]{String.valueOf(threadId), String.valueOf(System.currentTimeMillis() - InsightsConstants.PERIOD_IN_MILLIS)};

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  final int getInsecureMessageCountForInsights() {
    return getMessageCountForRecipientsAndType(getOutgoingInsecureMessageClause());
  }

  public int getInsecureMessageCount() {
    try (Cursor cursor = getReadableDatabase().query(getTableName(), SqlUtil.COUNT, getInsecureMessageClause(), null, null, null, null)) {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public boolean hasSmsExportMessage(long threadId) {
    return SQLiteDatabaseExtensionsKt.exists(getReadableDatabase(), getTableName())
        .where(THREAD_ID_WHERE + " AND " + getTypeField() + " = ?", threadId, Types.SMS_EXPORT_TYPE)
        .run();
  }

  final int getSecureMessageCountForInsights() {
    return getMessageCountForRecipientsAndType(getOutgoingSecureMessageClause());
  }

  final int getSecureMessageCount(long threadId) {
    SQLiteDatabase db           = databaseHelper.getSignalReadableDatabase();
    String[]       projection   = new String[] {"COUNT(*)"};
    String         query        = getSecureMessageClause() + "AND " + MmsSmsColumns.THREAD_ID + " = ?";
    String[]       args         = new String[]{String.valueOf(threadId)};

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  final int getOutgoingSecureMessageCount(long threadId) {
    SQLiteDatabase db           = databaseHelper.getSignalReadableDatabase();
    String[]       projection   = new String[] {"COUNT(*)"};
    String         query        = getOutgoingSecureMessageClause() +
                                  "AND " + MmsSmsColumns.THREAD_ID + " = ? " +
                                  "AND (" + getTypeField() + " & " + Types.GROUP_LEAVE_BIT + " = 0 OR " + getTypeField() + " & " + Types.GROUP_V2_BIT + " = " + Types.GROUP_V2_BIT + ")";
    String[]       args         = new String[]{String.valueOf(threadId)};

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  /**
   * Handles a synchronized read message.
   * @param messageId An id representing the author-timestamp pair of the message that was read on a linked device. Note that the author could be self when
   *                  syncing read receipts for reactions.
   */
  final @NonNull MmsSmsTable.TimestampReadResult setTimestampReadFromSyncMessage(SyncMessageId messageId, long proposedExpireStarted, @NonNull Map<Long, Long> threadToLatestRead) {
    SQLiteDatabase         database   = databaseHelper.getSignalWritableDatabase();
    List<Pair<Long, Long>> expiring   = new LinkedList<>();
    String[]               projection = new String[] { ID, THREAD_ID, EXPIRES_IN, EXPIRE_STARTED };
    String                 query      = getDateSentColumnName() + " = ? AND (" + RECIPIENT_ID + " = ? OR (" + RECIPIENT_ID + " = ? AND " + getOutgoingTypeClause() + "))";
    String[]               args       = SqlUtil.buildArgs(messageId.getTimetamp(), messageId.getRecipientId(), Recipient.self().getId());
    List<Long>             threads    = new LinkedList<>();

    try (Cursor cursor = database.query(getTableName(), projection, query, args, null, null, null)) {
      while (cursor.moveToNext()) {
        long id            = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        long threadId      = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
        long expiresIn     = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN));
        long expireStarted = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRE_STARTED));

        expireStarted = expireStarted > 0 ? Math.min(proposedExpireStarted, expireStarted) : proposedExpireStarted;

        ContentValues values = new ContentValues();
        values.put(READ, 1);
        values.put(REACTIONS_UNREAD, 0);
        values.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

        if (expiresIn > 0) {
          values.put(EXPIRE_STARTED, expireStarted);
          expiring.add(new Pair<>(id, expiresIn));
        }

        database.update(getTableName(), values, ID_WHERE, SqlUtil.buildArgs(id));

        threads.add(threadId);

        Long latest = threadToLatestRead.get(threadId);
        threadToLatestRead.put(threadId, (latest != null) ? Math.max(latest, messageId.getTimetamp()) : messageId.getTimetamp());
      }
    }

    return new MmsSmsTable.TimestampReadResult(expiring, threads);
  }

  private int getMessageCountForRecipientsAndType(String typeClause) {

    SQLiteDatabase db           = databaseHelper.getSignalReadableDatabase();
    String[]       projection   = new String[] {"COUNT(*)"};
    String         query        = typeClause + " AND " + getDateSentColumnName() + " > ?";
    String[]       args         = new String[]{String.valueOf(System.currentTimeMillis() - InsightsConstants.PERIOD_IN_MILLIS)};

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  private String getOutgoingInsecureMessageClause() {
    return "(" + getTypeField() + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND NOT (" + getTypeField() + " & " + Types.SECURE_MESSAGE_BIT + ")";
  }

  private String getOutgoingSecureMessageClause() {
    return "(" + getTypeField() + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND (" + getTypeField() + " & " + (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT) + ")";
  }

  private String getSecureMessageClause() {
    String isSent     = "(" + getTypeField() + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE;
    String isReceived = "(" + getTypeField() + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_INBOX_TYPE;
    String isSecure   = "(" + getTypeField() + " & " + (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT) + ")";

    return String.format(Locale.ENGLISH, "(%s OR %s) AND %s", isSent, isReceived, isSecure);
  }

  protected String getInsecureMessageClause() {
    return getInsecureMessageClause(-1);
  }

  protected String getInsecureMessageClause(long threadId) {
    String isSent      = "(" + getTableName() + "." + getTypeField() + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE;
    String isReceived  = "(" + getTableName() + "." + getTypeField() + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_INBOX_TYPE;
    String isSecure    = "(" + getTableName() + "." + getTypeField() + " & " + (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT) + ")";
    String isNotSecure = "(" + getTableName() + "." + getTypeField() + " <= " + (Types.BASE_TYPE_MASK | Types.MESSAGE_ATTRIBUTE_MASK) + ")";

    String whereClause = String.format(Locale.ENGLISH, "(%s OR %s) AND NOT %s AND %s", isSent, isReceived, isSecure, isNotSecure);

    if (threadId != -1) {
      whereClause += " AND " + getTableName() + "." +  THREAD_ID + " = " + threadId;
    }

    return whereClause;
  }

  public int getUnexportedInsecureMessagesCount() {
    return getUnexportedInsecureMessagesCount(-1);
  }

  public int getUnexportedInsecureMessagesCount(long threadId) {
    try (Cursor cursor = getWritableDatabase().query(getTableName(), SqlUtil.COUNT, getInsecureMessageClause(threadId) + " AND " + EXPORTED + " < ?", SqlUtil.buildArgs(MessageExportStatus.EXPORTED), null, null, null)) {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  /**
   * Resets the exported state and exported flag so messages can be re-exported.
   */
  public void clearExportState() {
    ContentValues values = new ContentValues(2);
    values.putNull(EXPORT_STATE);
    values.put(EXPORTED, MessageExportStatus.UNEXPORTED.serialize());

    SQLiteDatabaseExtensionsKt.update(getWritableDatabase(), getTableName())
                              .values(values)
                              .where(EXPORT_STATE + " IS NOT NULL OR " + EXPORTED + " != ?", MessageExportStatus.UNEXPORTED)
                              .run();
  }

  /**
   * Reset the exported status (not state) to the default for clearing errors.
   */
  public void clearInsecureMessageExportedErrorStatus() {
    ContentValues values = new ContentValues(1);
    values.put(EXPORTED, MessageExportStatus.UNEXPORTED.getCode());

    SQLiteDatabaseExtensionsKt.update(getWritableDatabase(), getTableName())
                              .values(values)
                              .where(EXPORTED + " < ?", MessageExportStatus.UNEXPORTED)
                              .run();
  }

  public void setReactionsSeen(long threadId, long sinceTimestamp) {
    SQLiteDatabase db          = databaseHelper.getSignalWritableDatabase();
    ContentValues  values      = new ContentValues();
    String         whereClause = THREAD_ID + " = ? AND " + REACTIONS_UNREAD + " = ?";
    String[]       whereArgs   = new String[]{String.valueOf(threadId), "1"};

    if (sinceTimestamp > -1) {
      whereClause +=  " AND " + getDateReceivedColumnName() + " <= " + sinceTimestamp;
    }

    values.put(REACTIONS_UNREAD, 0);
    values.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    db.update(getTableName(), values, whereClause, whereArgs);
  }

  public void setAllReactionsSeen() {
    SQLiteDatabase db     = databaseHelper.getSignalWritableDatabase();
    ContentValues  values = new ContentValues();
    String         query  = REACTIONS_UNREAD + " != ?";
    String[]       args   = new String[] { "0" };

    values.put(REACTIONS_UNREAD, 0);
    values.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    db.update(getTableName(), values, query, args);
  }

  public void setNotifiedTimestamp(long timestamp, @NonNull List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }

    SQLiteDatabase db     = databaseHelper.getSignalWritableDatabase();
    SqlUtil.Query  where  = SqlUtil.buildSingleCollectionQuery(ID, ids);
    ContentValues  values = new ContentValues();

    values.put(NOTIFIED_TIMESTAMP, timestamp);

    db.update(getTableName(), values, where.getWhere(), where.getWhereArgs());
  }

  public void addMismatchedIdentity(long messageId, @NonNull RecipientId recipientId, IdentityKey identityKey) {
    try {
      addToDocument(messageId, MISMATCHED_IDENTITIES,
                    new IdentityKeyMismatch(recipientId, identityKey),
                    IdentityKeyMismatchSet.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void removeMismatchedIdentity(long messageId, @NonNull RecipientId recipientId, IdentityKey identityKey) {
    try {
      removeFromDocument(messageId, MISMATCHED_IDENTITIES,
                         new IdentityKeyMismatch(recipientId, identityKey),
                         IdentityKeyMismatchSet.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void setMismatchedIdentities(long messageId, @NonNull Set<IdentityKeyMismatch> mismatches) {
    try {
      setDocument(databaseHelper.getSignalWritableDatabase(), messageId, MISMATCHED_IDENTITIES, new IdentityKeyMismatchSet(mismatches));
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public @NonNull List<ReportSpamData> getReportSpamMessageServerGuids(long threadId, long timestamp) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = THREAD_ID + " = ? AND " + getDateReceivedColumnName() + " <= ?";
    String[]       args  = SqlUtil.buildArgs(threadId, timestamp);

    List<ReportSpamData> data = new ArrayList<>();
    try (Cursor cursor = db.query(getTableName(), new String[] { RECIPIENT_ID, SERVER_GUID, getDateReceivedColumnName() }, query, args, null, null, getDateReceivedColumnName() + " DESC", "3")) {
      while (cursor.moveToNext()) {
        RecipientId id         = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
        String      serverGuid = CursorUtil.requireString(cursor, SERVER_GUID);
        long        dateReceived = CursorUtil.requireLong(cursor, getDateReceivedColumnName());
        if (!Util.isEmpty(serverGuid)) {
          data.add(new ReportSpamData(id, serverGuid, dateReceived));
        }
      }
    }
    return data;
  }

  public List<Long> getIncomingPaymentRequestThreads() {
    Cursor cursor = SQLiteDatabaseExtensionsKt.select(getReadableDatabase(), "DISTINCT " + THREAD_ID)
        .from(getTableName())
        .where("(" + getTypeField() + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_INBOX_TYPE + " AND (" + getTypeField() + " & ?) != 0", Types.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST)
        .run();

    return CursorExtensionsKt.readToList(cursor, c -> CursorUtil.requireLong(c, THREAD_ID));
  }

  public @Nullable MessageId getPaymentMessage(@NonNull UUID paymentUuid) {
    Cursor cursor = SQLiteDatabaseExtensionsKt.select(getReadableDatabase(), ID)
                                              .from(getTableName())
                                              .where(getTypeField() + " & ? != 0 AND body = ?", Types.SPECIAL_TYPE_PAYMENTS_NOTIFICATION, paymentUuid)
                                              .run();

    long id = CursorExtensionsKt.readToSingleLong(cursor, -1);
    if (id != -1) {
      return new MessageId(id, getTableName().equals(MmsTable.TABLE_NAME));
    } else {
      return null;
    }
  }

  @Override
  public void remapRecipient(@NonNull RecipientId fromId, @NonNull RecipientId toId) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, toId.serialize());
    getWritableDatabase().update(getTableName(), values, RECIPIENT_ID + " = ?", SqlUtil.buildArgs(fromId));
  }

  @Override
  public void remapThread(long fromId, long toId) {
    ContentValues values = new ContentValues();
    values.put(SmsTable.THREAD_ID, toId);
    getWritableDatabase().update(getTableName(), values, THREAD_ID + " = ?", SqlUtil.buildArgs(fromId));
  }

  void updateReactionsUnread(SQLiteDatabase db, long messageId, boolean hasReactions, boolean isRemoval) {
    try {
      boolean       isOutgoing = getMessageRecord(messageId).isOutgoing();
      ContentValues values     = new ContentValues();

      if (!hasReactions) {
        values.put(REACTIONS_UNREAD, 0);
      } else if (!isRemoval) {
        values.put(REACTIONS_UNREAD, 1);
      }

      if (isOutgoing && hasReactions) {
        values.put(NOTIFIED, 0);
      }

      if (values.size() > 0) {
        db.update(getTableName(), values, ID_WHERE, SqlUtil.buildArgs(messageId));
      }
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "Failed to find message " + messageId);
    }
  }

  protected <D extends Document<I>, I> void removeFromDocument(long messageId, String column, I object, Class<D> clazz) throws IOException {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    database.beginTransaction();

    try {
      D           document = getDocument(database, messageId, column, clazz);
      Iterator<I> iterator = document.getItems().iterator();

      while (iterator.hasNext()) {
        I item = iterator.next();

        if (item.equals(object)) {
          iterator.remove();
          break;
        }
      }

      setDocument(database, messageId, column, document);
      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
  }

  protected <T extends Document<I>, I> void addToDocument(long messageId, String column, final I object, Class<T> clazz) throws IOException {
    List<I> list = new ArrayList<I>() {{
      add(object);
    }};

    addToDocument(messageId, column, list, clazz);
  }

  protected <T extends Document<I>, I> void addToDocument(long messageId, String column, List<I> objects, Class<T> clazz) throws IOException {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    database.beginTransaction();

    try {
      T document = getDocument(database, messageId, column, clazz);
      document.getItems().addAll(objects);
      setDocument(database, messageId, column, document);

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
  }

  protected void setDocument(SQLiteDatabase database, long messageId, String column, Document document) throws IOException {
    ContentValues contentValues = new ContentValues();

    if (document == null || document.size() == 0) {
      contentValues.put(column, (String)null);
    } else {
      contentValues.put(column, JsonUtils.toJson(document));
    }

    database.update(getTableName(), contentValues, ID_WHERE, new String[] {String.valueOf(messageId)});
  }

  private <D extends Document> D getDocument(SQLiteDatabase database, long messageId,
                                             String column, Class<D> clazz)
  {
    Cursor cursor = null;

    try {
      cursor = database.query(getTableName(), new String[] {column},
                              ID_WHERE, new String[] {String.valueOf(messageId)},
                              null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        String document = cursor.getString(cursor.getColumnIndexOrThrow(column));

        try {
          if (!TextUtils.isEmpty(document)) {
            return JsonUtils.fromJson(document, clazz);
          }
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      try {
        return clazz.newInstance();
      } catch (InstantiationException e) {
        throw new AssertionError(e);
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private long getThreadId(@NonNull SQLiteDatabase db, long messageId) {
    String[] projection = new String[]{ THREAD_ID };
    String   query      = ID + " = ?";
    String[] args       = new String[]{ String.valueOf(messageId) };

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
      }
    }

    return -1;
  }

  protected enum ReceiptType {
    READ(READ_RECEIPT_COUNT, GroupReceiptTable.STATUS_READ),
    DELIVERY(DELIVERY_RECEIPT_COUNT, GroupReceiptTable.STATUS_DELIVERED),
    VIEWED(VIEWED_RECEIPT_COUNT, GroupReceiptTable.STATUS_VIEWED);

    private final String columnName;
    private final int    groupStatus;

    ReceiptType(String columnName, int groupStatus) {
      this.columnName  = columnName;
      this.groupStatus = groupStatus;
    }

    public String getColumnName() {
      return columnName;
    }

    public int getGroupStatus() {
      return groupStatus;
    }
  }

  public static class SyncMessageId {

    private final RecipientId recipientId;
    private final long        timetamp;

    public SyncMessageId(@NonNull RecipientId recipientId, long timetamp) {
      this.recipientId = recipientId;
      this.timetamp    = timetamp;
    }

    public RecipientId getRecipientId() {
      return recipientId;
    }

    public long getTimetamp() {
      return timetamp;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final SyncMessageId that = (SyncMessageId) o;
      return timetamp == that.timetamp && Objects.equals(recipientId, that.recipientId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(recipientId, timetamp);
    }
  }

  public static class ExpirationInfo {

    private final long    id;
    private final long    expiresIn;
    private final long    expireStarted;
    private final boolean mms;

    public ExpirationInfo(long id, long expiresIn, long expireStarted, boolean mms) {
      this.id            = id;
      this.expiresIn     = expiresIn;
      this.expireStarted = expireStarted;
      this.mms           = mms;
    }

    public long getId() {
      return id;
    }

    public long getExpiresIn() {
      return expiresIn;
    }

    public long getExpireStarted() {
      return expireStarted;
    }

    public boolean isMms() {
      return mms;
    }
  }

  public static class MarkedMessageInfo {

    private final long           threadId;
    private final SyncMessageId  syncMessageId;
    private final MessageId      messageId;
    private final ExpirationInfo expirationInfo;

    public MarkedMessageInfo(long threadId, @NonNull SyncMessageId syncMessageId, @NonNull MessageId messageId, @Nullable ExpirationInfo expirationInfo) {
      this.threadId       = threadId;
      this.syncMessageId  = syncMessageId;
      this.messageId      = messageId;
      this.expirationInfo = expirationInfo;
    }

    public long getThreadId() {
      return threadId;
    }

    public @NonNull SyncMessageId getSyncMessageId() {
      return syncMessageId;
    }

    public @NonNull MessageId getMessageId() {
      return messageId;
    }

    public @Nullable ExpirationInfo getExpirationInfo() {
      return expirationInfo;
    }
  }

  public static class InsertResult {
    private final long messageId;
    private final long threadId;

    public InsertResult(long messageId, long threadId) {
      this.messageId = messageId;
      this.threadId = threadId;
    }

    public long getMessageId() {
      return messageId;
    }

    public long getThreadId() {
      return threadId;
    }
  }

  public static class MmsNotificationInfo {
    private final RecipientId from;
    private final String      contentLocation;
    private final String      transactionId;
    private final int         subscriptionId;

    MmsNotificationInfo(@NonNull RecipientId from, String contentLocation, String transactionId, int subscriptionId) {
      this.from            = from;
      this.contentLocation = contentLocation;
      this.transactionId   = transactionId;
      this.subscriptionId  = subscriptionId;
    }

    public String getContentLocation() {
      return contentLocation;
    }

    public String getTransactionId() {
      return transactionId;
    }

    public int getSubscriptionId() {
      return subscriptionId;
    }

    public @NonNull RecipientId getFrom() {
      return from;
    }
  }

  static class MessageUpdate {
    private final long      threadId;
    private final MessageId messageId;

    MessageUpdate(long threadId, @NonNull MessageId messageId) {
      this.threadId  = threadId;
      this.messageId = messageId;
    }

    public long getThreadId() {
      return threadId;
    }

    public @NonNull MessageId getMessageId() {
      return messageId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final MessageUpdate that = (MessageUpdate) o;
      return threadId == that.threadId && messageId.equals(that.messageId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(threadId, messageId);
    }
  }


  public interface InsertListener {
    void onComplete();
  }

  /**
   * Allows the developer to safely iterate over and close a cursor containing
   * data for MessageRecord objects. Supports for-each loops as well as try-with-resources
   * blocks.
   *
   * Readers are considered "one-shot" and it's on the caller to decide what needs
   * to be done with the data. Once read, a reader cannot be read from again. This
   * is by design, since reading data out of a cursor involves object creations and
   * lookups, so it is in the best interest of app performance to only read out the
   * data once. If you need to parse the list multiple times, it is recommended that
   * you copy the iterable out into a normal List, or use extension methods such as
   * partition.
   *
   * This reader does not support removal, since this would be considered a destructive
   * database call.
   */
  public interface Reader extends Closeable, Iterable<MessageRecord> {
    /**
     * @deprecated Use the Iterable interface instead.
     */
    @Deprecated
    MessageRecord getNext();

    /**
     * @deprecated Use the Iterable interface instead.
     */
    @Deprecated
    MessageRecord getCurrent();

    /**
     * Pulls the export state out of the query, if it is present.
     */
    @NonNull MessageExportState getMessageExportStateForCurrentRecord();

    /**
     * From the {@link Closeable} interface, removing the IOException requirement.
     */
    void close();
  }

  public static class ReportSpamData {
    private final RecipientId recipientId;
    private final String      serverGuid;
    private final long        dateReceived;

    public ReportSpamData(RecipientId recipientId, String serverGuid, long dateReceived) {
      this.recipientId  = recipientId;
      this.serverGuid   = serverGuid;
      this.dateReceived = dateReceived;
    }

    public @NonNull RecipientId getRecipientId() {
      return recipientId;
    }

    public @NonNull String getServerGuid() {
      return serverGuid;
    }

    public long getDateReceived() {
      return dateReceived;
    }
  }

  /**
   * Describes which messages to act on. This is used when incrementing receipts.
   * Specifically, this was added to support stories having separate viewed receipt settings.
   */
  public enum MessageQualifier {
    /**
     * A normal database message (i.e. not a story)
     */
    NORMAL,
    /**
     * A story message
     */
    STORY,
    /**
     * Both normal and story message
     */
    ALL
  }
}

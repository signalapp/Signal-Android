package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.google.android.mms.pdu_alt.NotificationInd;
import com.google.protobuf.InvalidProtocolBufferException;

import net.sqlcipher.database.SQLiteStatement;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.documents.Document;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchList;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.ReactionList;
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
import org.thoughtcrime.securesms.util.SqlUtil;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public abstract class MessageDatabase extends Database implements MmsSmsColumns {

  private static final String TAG = MessageDatabase.class.getSimpleName();

  public MessageDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
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
  abstract int getMessageCountForThreadSummary(long threadId);
  public abstract Optional<MmsNotificationInfo> getNotification(long messageId);

  public abstract Cursor getExpirationStartedMessages();
  public abstract SmsMessageRecord getSmsMessage(long messageId) throws NoSuchMessageException;
  public abstract Reader getMessages(Collection<Long> messageIds);
  public abstract Cursor getMessageCursor(long messageId);
  public abstract OutgoingMediaMessage getOutgoingMessage(long messageId) throws MmsException, NoSuchMessageException;
  public abstract MessageRecord getMessageRecord(long messageId) throws NoSuchMessageException;
  public abstract Cursor getVerboseMessageCursor(long messageId);
  public abstract boolean hasReceivedAnyCallsSince(long threadId, long timestamp);
  public abstract @Nullable ViewOnceExpirationInfo getNearestExpiringViewOnceMessage();
  public abstract boolean isSent(long messageId);
  public abstract List<MessageRecord> getProfileChangeDetailsRecords(long threadId, long afterTimestamp);

  public abstract void markExpireStarted(long messageId);
  public abstract void markExpireStarted(long messageId, long startTime);
  public abstract void markExpireStarted(Collection<Long> messageId, long startTime);

  public abstract void markAsEndSession(long id);
  public abstract void markAsPreKeyBundle(long id);
  public abstract void markAsInvalidVersionKeyExchange(long id);
  public abstract void markAsSecure(long id);
  public abstract void markAsInsecure(long id);
  public abstract void markAsPush(long id);
  public abstract void markAsForcedSms(long id);
  public abstract void markAsDecryptFailed(long id);
  public abstract void markAsDecryptDuplicate(long id);
  public abstract void markAsNoSession(long id);
  public abstract void markAsUnsupportedProtocolVersion(long id);
  public abstract void markAsInvalidMessage(long id);
  public abstract void markAsLegacyVersion(long id);
  public abstract void markAsOutbox(long id);
  public abstract void markAsPendingInsecureSmsFallback(long id);
  public abstract void markAsSent(long messageId, boolean secure);
  public abstract void markAsSentFailed(long id);
  public abstract void markUnidentified(long messageId, boolean unidentified);
  public abstract void markAsSending(long messageId);
  public abstract void markAsRemoteDelete(long messageId);
  public abstract void markAsMissedCall(long id, boolean isVideoOffer);
  public abstract void markAsNotified(long id);
  public abstract void markSmsStatus(long id, int status);
  public abstract void markDownloadState(long messageId, long state);
  public abstract void markIncomingNotificationReceived(long threadId);

  public abstract boolean incrementReceiptCount(SyncMessageId messageId, long timestamp, @NonNull ReceiptType receiptType);
  public abstract List<Pair<Long, Long>> setTimestampRead(SyncMessageId messageId, long proposedExpireStarted);
  public abstract List<MarkedMessageInfo> setEntireThreadRead(long threadId);
  public abstract List<MarkedMessageInfo> setMessagesReadSince(long threadId, long timestamp);
  public abstract List<MarkedMessageInfo> setAllMessagesRead();
  public abstract Pair<Long, Long> updateBundleMessageBody(long messageId, String body);
  public abstract @NonNull List<MarkedMessageInfo> getViewedIncomingMessages(long threadId);
  public abstract @Nullable MarkedMessageInfo setIncomingMessageViewed(long messageId);

  public abstract void addFailures(long messageId, List<NetworkFailure> failure);
  public abstract void removeFailure(long messageId, NetworkFailure failure);

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
  public abstract @NonNull InsertResult insertDecryptionFailedMessage(@NonNull RecipientId recipientId, long senderDeviceId, long sentTimestamp);
  public abstract long insertMessageOutbox(long threadId, OutgoingTextMessage message, boolean forceSms, long date, InsertListener insertListener);
  public abstract long insertMessageOutbox(@NonNull OutgoingMediaMessage message, long threadId, boolean forceSms, @Nullable SmsDatabase.InsertListener insertListener) throws MmsException;
  public abstract long insertMessageOutbox(@NonNull OutgoingMediaMessage message, long threadId, boolean forceSms, int defaultReceiptStatus, @Nullable SmsDatabase.InsertListener insertListener) throws MmsException;
  public abstract void insertProfileNameChangeMessages(@NonNull Recipient recipient, @NonNull String newProfileName, @NonNull String previousProfileName);
  public abstract void insertGroupV1MigrationEvents(@NonNull RecipientId recipientId, long threadId, @NonNull GroupMigrationMembershipChange membershipChange);

  public abstract boolean deleteMessage(long messageId);
  abstract void deleteThread(long threadId);
  abstract void deleteMessagesInThreadBeforeDate(long threadId, long date);
  abstract void deleteThreads(@NonNull Set<Long> threadIds);
  abstract void deleteAllThreads();
  abstract void deleteAbandonedMessages();

  public abstract List<MessageRecord> getMessagesInThreadAfterInclusive(long threadId, long timestamp, long limit);

  public abstract SQLiteDatabase beginTransaction();
  public abstract void endTransaction(SQLiteDatabase database);
  public abstract void setTransactionSuccessful();
  public abstract void endTransaction();
  public abstract SQLiteStatement createInsertStatement(SQLiteDatabase database);

  public abstract void ensureMigration();


  final int getInsecureMessagesSentForThread(long threadId) {
    SQLiteDatabase db         = databaseHelper.getReadableDatabase();
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

  final int getSecureMessageCountForInsights() {
    return getMessageCountForRecipientsAndType(getOutgoingSecureMessageClause());
  }

  final int getSecureMessageCount(long threadId) {
    SQLiteDatabase db           = databaseHelper.getReadableDatabase();
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
    SQLiteDatabase db           = databaseHelper.getReadableDatabase();
    String[]       projection   = new String[] {"COUNT(*)"};
    String         query        = getOutgoingSecureMessageClause() + "AND " + MmsSmsColumns.THREAD_ID + " = ? AND" + "(" + getTypeField() + " & " + Types.GROUP_QUIT_BIT + " = 0)";
    String[]       args         = new String[]{String.valueOf(threadId)};

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  private int getMessageCountForRecipientsAndType(String typeClause) {

    SQLiteDatabase db           = databaseHelper.getReadableDatabase();
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

  public void setReactionsSeen(long threadId, long sinceTimestamp) {
    SQLiteDatabase db          = databaseHelper.getWritableDatabase();
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
    SQLiteDatabase db     = databaseHelper.getWritableDatabase();
    ContentValues  values = new ContentValues();
    String         query  = REACTIONS_UNREAD + " != ?";
    String[]       args   = new String[] { "0" };

    values.put(REACTIONS_UNREAD, 0);
    values.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    db.update(getTableName(), values, query, args);
  }

  public void addReaction(long messageId, @NonNull ReactionRecord reaction) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();

    try {
      ReactionList          reactions   = getReactions(db, messageId).or(ReactionList.getDefaultInstance());
      ReactionList.Reaction newReaction = ReactionList.Reaction.newBuilder()
                                                               .setEmoji(reaction.getEmoji())
                                                               .setAuthor(reaction.getAuthor().toLong())
                                                               .setSentTime(reaction.getDateSent())
                                                               .setReceivedTime(reaction.getDateReceived())
                                                               .build();

      ReactionList updatedList = pruneByAuthor(reactions, reaction.getAuthor()).toBuilder()
                                                                               .addReactions(newReaction)
                                                                               .build();

      setReactions(db, messageId, updatedList);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(getThreadId(db, messageId));
  }

  public void deleteReaction(long messageId, @NonNull RecipientId author) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();

    try {
      ReactionList reactions   = getReactions(db, messageId).or(ReactionList.getDefaultInstance());
      ReactionList updatedList = pruneByAuthor(reactions, author);

      setReactions(db, messageId, updatedList);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(getThreadId(db, messageId));
  }

  public boolean hasReaction(long messageId, @NonNull ReactionRecord reactionRecord) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    ReactionList reactions = getReactions(db, messageId).or(ReactionList.getDefaultInstance());

    for (ReactionList.Reaction reaction : reactions.getReactionsList()) {
      if (reactionRecord.getAuthor().toLong() == reaction.getAuthor() &&
          reactionRecord.getEmoji().equals(reaction.getEmoji()))
      {
        return true;
      }
    }

    return false;
  }

  public void setNotifiedTimestamp(long timestamp, @NonNull List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }

    SQLiteDatabase db     = databaseHelper.getWritableDatabase();
    SqlUtil.Query  where  = SqlUtil.buildCollectionQuery(ID, ids);
    ContentValues  values = new ContentValues();

    values.put(NOTIFIED_TIMESTAMP, timestamp);

    db.update(getTableName(), values, where.getWhere(), where.getWhereArgs());
  }

  public void addMismatchedIdentity(long messageId, @NonNull RecipientId recipientId, IdentityKey identityKey) {
    try {
      addToDocument(messageId, MISMATCHED_IDENTITIES,
                    new IdentityKeyMismatch(recipientId, identityKey),
                    IdentityKeyMismatchList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void removeMismatchedIdentity(long messageId, @NonNull RecipientId recipientId, IdentityKey identityKey) {
    try {
      removeFromDocument(messageId, MISMATCHED_IDENTITIES,
                         new IdentityKeyMismatch(recipientId, identityKey),
                         IdentityKeyMismatchList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  protected static List<ReactionRecord> parseReactions(@NonNull Cursor cursor) {
    byte[] raw = cursor.getBlob(cursor.getColumnIndexOrThrow(REACTIONS));

    if (raw != null) {
      try {
        return Stream.of(ReactionList.parseFrom(raw).getReactionsList())
                     .map(r -> {
                       return new ReactionRecord(r.getEmoji(),
                                                 RecipientId.from(r.getAuthor()),
                                                 r.getSentTime(),
                                                 r.getReceivedTime());
                     })
                     .toList();
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "[parseReactions] Failed to parse reaction list!", e);
        return Collections.emptyList();
      }
    } else {
      return Collections.emptyList();
    }
  }

  protected <D extends Document<I>, I> void removeFromDocument(long messageId, String column, I object, Class<D> clazz) throws IOException {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    try {
      D           document = getDocument(database, messageId, column, clazz);
      Iterator<I> iterator = document.getList().iterator();

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
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    try {
      T document = getDocument(database, messageId, column, clazz);
      document.getList().addAll(objects);
      setDocument(database, messageId, column, document);

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
  }

  private void setDocument(SQLiteDatabase database, long messageId, String column, Document document) throws IOException {
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

  private static @NonNull ReactionList pruneByAuthor(@NonNull ReactionList reactionList, @NonNull RecipientId recipientId) {
    List<ReactionList.Reaction> pruned = Stream.of(reactionList.getReactionsList())
                                               .filterNot(r -> r.getAuthor() == recipientId.toLong())
                                               .toList();

    return reactionList.toBuilder()
                       .clearReactions()
                       .addAllReactions(pruned)
                       .build();
  }

  private @NonNull Optional<ReactionList> getReactions(SQLiteDatabase db, long messageId) {
    String[] projection = new String[]{ REACTIONS };
    String   query      = ID + " = ?";
    String[] args       = new String[]{String.valueOf(messageId)};

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        byte[] raw = cursor.getBlob(cursor.getColumnIndexOrThrow(REACTIONS));

        if (raw != null) {
          return Optional.of(ReactionList.parseFrom(raw));
        }
      }
    } catch (InvalidProtocolBufferException e) {
      Log.w(TAG, "[getRecipients] Failed to parse reaction list!", e);
    }

    return Optional.absent();
  }

  private void setReactions(@NonNull SQLiteDatabase db, long messageId, @NonNull ReactionList reactionList) {
    ContentValues values       = new ContentValues(1);
    boolean       hasReactions = reactionList.getReactionsCount() != 0;

    values.put(REACTIONS, reactionList.getReactionsList().isEmpty() ? null : reactionList.toByteArray());
    values.put(REACTIONS_UNREAD, hasReactions ? 1 : 0);

    if (hasReactions) {
      values.put(NOTIFIED, 0);
    }

    String   query = ID + " = ?";
    String[] args  = new String[] { String.valueOf(messageId) };

    db.update(getTableName(), values, query, args);
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
    READ(READ_RECEIPT_COUNT, GroupReceiptDatabase.STATUS_READ),
    DELIVERY(DELIVERY_RECEIPT_COUNT, GroupReceiptDatabase.STATUS_DELIVERED),
    VIEWED(VIEWED_RECEIPT_COUNT, GroupReceiptDatabase.STATUS_VIEWED);

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
    private final ExpirationInfo expirationInfo;

    public MarkedMessageInfo(long threadId, SyncMessageId syncMessageId, ExpirationInfo expirationInfo) {
      this.threadId       = threadId;
      this.syncMessageId  = syncMessageId;
      this.expirationInfo = expirationInfo;
    }

    public long getThreadId() {
      return threadId;
    }

    public SyncMessageId getSyncMessageId() {
      return syncMessageId;
    }

    public ExpirationInfo getExpirationInfo() {
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

  public interface InsertListener {
    void onComplete();
  }

  public interface Reader extends Closeable {
    MessageRecord getNext();
    MessageRecord getCurrent();
    void close();
  }
}

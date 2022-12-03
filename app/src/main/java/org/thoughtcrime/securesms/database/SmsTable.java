/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 - 2017 Open Whisper Systems
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
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Stream;
import com.google.android.mms.pdu_alt.NotificationInd;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.core.util.CursorExtensionsKt;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SQLiteDatabaseExtensionsKt;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil;
import org.thoughtcrime.securesms.database.model.MessageExportStatus;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ParentStoryId;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.database.model.StoryResult;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.database.model.StoryViewState;
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState;
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.jobs.TrimThreadJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.revealable.ViewOnceExpirationInfo;
import org.thoughtcrime.securesms.sms.IncomingGroupUpdateMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.thoughtcrime.securesms.database.MmsSmsColumns.Types.CHANGE_NUMBER_TYPE;
import static org.thoughtcrime.securesms.database.MmsSmsColumns.Types.GROUP_V2_LEAVE_BITS;

/**
 * Database for storage of SMS messages.
 *
 * @author Moxie Marlinspike
 */
public class SmsTable extends MessageTable {

  private static final String TAG = Log.tag(SmsTable.class);

  public  static final String TABLE_NAME = "sms";
  public  static final String SMS_STATUS = "status";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                     + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                  DATE_SENT              + " INTEGER NOT NULL, " +
                                                                                  DATE_RECEIVED          + " INTEGER NOT NULL, " +
                                                                                  DATE_SERVER            + " INTEGER DEFAULT -1, " +
                                                                                  THREAD_ID              + " INTEGER NOT NULL REFERENCES " + ThreadTable.TABLE_NAME + " (" + ThreadTable.ID + ") ON DELETE CASCADE, " +
                                                                                  RECIPIENT_ID           + " INTEGER NOT NULL REFERENCES " + RecipientTable.TABLE_NAME + " (" + RecipientTable.ID + ") ON DELETE CASCADE, " +
                                                                                  RECIPIENT_DEVICE_ID    + " INTEGER DEFAULT 1, " +
                                                                                  TYPE                   + " INTEGER, " +
                                                                                  BODY                   + " TEXT, " +
                                                                                  READ                   + " INTEGER DEFAULT 0, " +
                                                                                  SMS_STATUS             + " INTEGER DEFAULT -1," +
                                                                                  DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0," +
                                                                                  MISMATCHED_IDENTITIES  + " TEXT DEFAULT NULL, " +
                                                                                  SMS_SUBSCRIPTION_ID    + " INTEGER DEFAULT -1, " +
                                                                                  EXPIRES_IN             + " INTEGER DEFAULT 0, " +
                                                                                  EXPIRE_STARTED         + " INTEGER DEFAULT 0, " +
                                                                                  NOTIFIED               + " DEFAULT 0, " +
                                                                                  READ_RECEIPT_COUNT     + " INTEGER DEFAULT 0, " +
                                                                                  UNIDENTIFIED           + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS_UNREAD       + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS_LAST_SEEN    + " INTEGER DEFAULT -1, " +
                                                                                  REMOTE_DELETED         + " INTEGER DEFAULT 0, " +
                                                                                  NOTIFIED_TIMESTAMP     + " INTEGER DEFAULT 0, " +
                                                                                  SERVER_GUID            + " TEXT DEFAULT NULL, " +
                                                                                  RECEIPT_TIMESTAMP      + " INTEGER DEFAULT -1, " +
                                                                                  EXPORT_STATE           + " BLOB DEFAULT NULL, " +
                                                                                  EXPORTED               + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS sms_read_and_notified_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + NOTIFIED + ","  + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS sms_type_index ON " + TABLE_NAME + " (" + TYPE + ");",
    "CREATE INDEX IF NOT EXISTS sms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ", " + RECIPIENT_ID + ", " + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS sms_date_server_index ON " + TABLE_NAME + " (" + DATE_SERVER + ");",
    "CREATE INDEX IF NOT EXISTS sms_thread_date_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");",
    "CREATE INDEX IF NOT EXISTS sms_reactions_unread_index ON " + TABLE_NAME + " (" + REACTIONS_UNREAD + ");",
    "CREATE INDEX IF NOT EXISTS sms_exported_index ON " + TABLE_NAME + " (" + EXPORTED + ");"
  };

  private static final String[] MESSAGE_PROJECTION = new String[] {
      ID,
      THREAD_ID,
      RECIPIENT_ID,
      RECIPIENT_DEVICE_ID,
      DATE_RECEIVED,
      DATE_SENT,
      DATE_SERVER,
      READ,
      SMS_STATUS,
      TYPE,
      BODY,
      DELIVERY_RECEIPT_COUNT,
      MISMATCHED_IDENTITIES,
      SMS_SUBSCRIPTION_ID,
      EXPIRES_IN,
      EXPIRE_STARTED,
      NOTIFIED,
      READ_RECEIPT_COUNT,
      UNIDENTIFIED,
      REACTIONS_UNREAD,
      REACTIONS_LAST_SEEN,
      REMOTE_DELETED,
      NOTIFIED_TIMESTAMP,
      RECEIPT_TIMESTAMP
  };

  @VisibleForTesting
  static final long IGNORABLE_TYPESMASK_WHEN_COUNTING = Types.END_SESSION_BIT | Types.KEY_EXCHANGE_IDENTITY_UPDATE_BIT | Types.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT;

  private static final EarlyReceiptCache earlyDeliveryReceiptCache = new EarlyReceiptCache("SmsDelivery");

  public SmsTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  @Override
  protected String getTableName() {
    return TABLE_NAME;
  }

  @Override
  protected String getDateSentColumnName() {
    return DATE_SENT;
  }

  @Override
  protected String getDateReceivedColumnName() {
    return DATE_RECEIVED;
  }

  @Override
  protected String getTypeField() {
    return TYPE;
  }

  private void updateTypeBitmask(long id, long maskOff, long maskOn) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    long threadId;

    db.beginTransaction();
    try {
      db.execSQL("UPDATE " + TABLE_NAME +
                 " SET " + TYPE + " = (" + TYPE + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
                 " WHERE " + ID + " = ?", SqlUtil.buildArgs(id));

      threadId = getThreadIdForMessage(id);

      SignalDatabase.threads().updateSnippetTypeSilently(threadId);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(id, false));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

  @Override
  public @Nullable RecipientId getOldestGroupUpdateSender(long threadId, long minimumDateReceived) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String[] columns = new String[]{RECIPIENT_ID};
    String   query   = THREAD_ID + " = ? AND " + TYPE + " & ? AND " + DATE_RECEIVED + " >= ?";
    long     type    = Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT | Types.GROUP_UPDATE_BIT | Types.BASE_INBOX_TYPE;
    String[] args    = new String[]{String.valueOf(threadId), String.valueOf(type), String.valueOf(minimumDateReceived)};
    String   limit   = "1";

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, args, null, null, limit)) {
      if (cursor.moveToFirst()) {
        return RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
      }
    }

    return null;
  }

  @Override
  public long getThreadIdForMessage(long id) {
    try (Cursor cursor = databaseHelper.getSignalReadableDatabase().query(TABLE_NAME, THREAD_ID_PROJECTION, ID_WHERE, SqlUtil.buildArgs(id), null, null, null)) {
      if (cursor.moveToFirst()) {
        return CursorUtil.requireLong(cursor, THREAD_ID);
      }
    }
    return -1;
  }

  @Override
  public int getMessageCountForThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, COUNT, THREAD_ID_WHERE, SqlUtil.buildArgs(threadId), null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  @Override
  public int getMessageCountForThread(long threadId, long beforeTime) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String[] cols  = new String[] {"COUNT(*)"};
    String   query = THREAD_ID + " = ? AND " + DATE_RECEIVED + " < ?";
    String[] args  = new String[]{String.valueOf(threadId), String.valueOf(beforeTime)};

    try (Cursor cursor = db.query(TABLE_NAME, cols, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  @Override
  public boolean hasMeaningfulMessage(long threadId) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    SqlUtil.Query  query = buildMeaningfulMessagesQuery(threadId);

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { "1" }, query.getWhere(), query.getWhereArgs(), null, null, null, "1")) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  @Override
  public int getIncomingMeaningfulMessageCountSince(long threadId, long afterTime) {
    SQLiteDatabase db                      = databaseHelper.getSignalReadableDatabase();
    String[]       projection              = SqlUtil.COUNT;
    SqlUtil.Query  meaningfulMessagesQuery = buildMeaningfulMessagesQuery(threadId);
    String         where                   = meaningfulMessagesQuery.getWhere() + " AND " + DATE_RECEIVED + " >= ?";
    String[]       whereArgs               = SqlUtil.appendArg(meaningfulMessagesQuery.getWhereArgs(), String.valueOf(afterTime));

    try (Cursor cursor = db.query(TABLE_NAME, projection, where, whereArgs, null, null, null, "1")) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  private @NonNull SqlUtil.Query buildMeaningfulMessagesQuery(long threadId) {
    String query = THREAD_ID + " = ? AND (NOT " + TYPE + " & ? AND " + TYPE + " != ? AND " + TYPE + " != ? AND " + TYPE + " != ? AND " + TYPE + " != ? AND " + TYPE + " & " + GROUP_V2_LEAVE_BITS + " != " + GROUP_V2_LEAVE_BITS + ")";
    return SqlUtil.buildQuery(query, threadId, IGNORABLE_TYPESMASK_WHEN_COUNTING, Types.PROFILE_CHANGE_TYPE, Types.CHANGE_NUMBER_TYPE, Types.SMS_EXPORT_TYPE, Types.BOOST_REQUEST_TYPE);
  }

  @Override
  public void markAsEndSession(long id) {
    updateTypeBitmask(id, Types.KEY_EXCHANGE_MASK, Types.END_SESSION_BIT);
  }

  @Override
  public void markAsInvalidVersionKeyExchange(long id) {
    updateTypeBitmask(id, 0, Types.KEY_EXCHANGE_INVALID_VERSION_BIT);
  }

  @Override
  public void markAsSecure(long id) {
    updateTypeBitmask(id, 0, Types.SECURE_MESSAGE_BIT);
  }

  @Override
  public void markAsInsecure(long id) {
    updateTypeBitmask(id, Types.SECURE_MESSAGE_BIT, 0);
  }

  @Override
  public void markAsPush(long id) {
    updateTypeBitmask(id, 0, Types.PUSH_MESSAGE_BIT);
  }

  @Override
  public void markAsForcedSms(long id) {
    updateTypeBitmask(id, Types.PUSH_MESSAGE_BIT, Types.MESSAGE_FORCE_SMS_BIT);
  }

  @Override
  public void markAsRateLimited(long id) {
    updateTypeBitmask(id, 0, Types.MESSAGE_RATE_LIMITED_BIT);
  }

  @Override
  public void clearRateLimitStatus(@NonNull Collection<Long> ids) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      for (long id : ids) {
        updateTypeBitmask(id, Types.MESSAGE_RATE_LIMITED_BIT, 0);
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  public void markAsDecryptFailed(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_FAILED_BIT);
  }

  @Override
  public void markAsNoSession(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_NO_SESSION_BIT);
  }

  @Override
  public void markAsUnsupportedProtocolVersion(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.UNSUPPORTED_MESSAGE_TYPE);
  }

  @Override
  public void markAsInvalidMessage(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.INVALID_MESSAGE_TYPE);
  }

  @Override
  public void markAsLegacyVersion(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_LEGACY_BIT);
  }

  @Override
  public void markAsOutbox(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_OUTBOX_TYPE);
  }

  @Override
  public void markAsPendingInsecureSmsFallback(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_PENDING_INSECURE_SMS_FALLBACK);
  }

  @Override
  public void markAsSent(long id, boolean isSecure) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_SENT_TYPE | (isSecure ? Types.PUSH_MESSAGE_BIT | Types.SECURE_MESSAGE_BIT : 0));
  }

  @Override
  public void markAsSending(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_SENDING_TYPE);
  }

  @Override
  public void markAsMissedCall(long id, boolean isVideoOffer) {
    updateTypeBitmask(id, Types.TOTAL_MASK, isVideoOffer ? Types.MISSED_VIDEO_CALL_TYPE : Types.MISSED_AUDIO_CALL_TYPE);
  }

  @Override
  public void markAsRemoteDelete(long id) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    long threadId;

    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put(REMOTE_DELETED, 1);
      values.putNull(BODY);
      db.update(TABLE_NAME, values, ID_WHERE, new String[] { String.valueOf(id) });

      threadId = getThreadIdForMessage(id);

      SignalDatabase.reactions().deleteReactions(new MessageId(id, false));
      SignalDatabase.threads().update(threadId, false);
      SignalDatabase.messageLog().deleteAllRelatedToMessage(id, false);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    notifyConversationListeners(threadId);
  }

  @Override
  public void markUnidentified(long id, boolean unidentified) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(UNIDENTIFIED, unidentified ? 1 : 0);

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(id)});
  }

  @Override
  public void markExpireStarted(long id) {
    markExpireStarted(id, System.currentTimeMillis());
  }

  @Override
  public void markExpireStarted(long id, long startedAtTimestamp) {
    markExpireStarted(Collections.singleton(id), startedAtTimestamp);
  }

  @Override
  public void markExpireStarted(Collection<Long> ids, long startedAtTimestamp) {
    SQLiteDatabase db       = databaseHelper.getSignalWritableDatabase();
    long           threadId = -1;

    db.beginTransaction();
    try {
      String query = ID + " = ? AND (" + EXPIRE_STARTED + " = 0 OR " + EXPIRE_STARTED + " > ?)";

      for (long id : ids) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(EXPIRE_STARTED, startedAtTimestamp);

        db.update(TABLE_NAME, contentValues, query, new String[]{String.valueOf(id), String.valueOf(startedAtTimestamp)});

        if (threadId < 0) {
          threadId = getThreadIdForMessage(id);
        }
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    SignalDatabase.threads().update(threadId, false);
    notifyConversationListeners(threadId);
  }

  @Override
  public void markSmsStatus(long id, int status) {
    Log.i(TAG, "Updating ID: " + id + " to status: " + status);
    ContentValues contentValues = new ContentValues();
    contentValues.put(SMS_STATUS, status);

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {id+""});

    long threadId = getThreadIdForMessage(id);
    SignalDatabase.threads().update(threadId, false);
    notifyConversationListeners(threadId);
  }

  @Override
  public void markAsSentFailed(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_SENT_FAILED_TYPE);
  }

  @Override
  public void markAsNotified(long id) {
    SQLiteDatabase database      = databaseHelper.getSignalWritableDatabase();
    ContentValues  contentValues = new ContentValues();

    contentValues.put(NOTIFIED, 1);
    contentValues.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(id)});
  }

  @Override
  public @NonNull Set<MessageUpdate> incrementReceiptCount(SyncMessageId messageId, long timestamp, @NonNull ReceiptType receiptType, @NonNull MessageQualifier messageQualifier) {
    if (messageQualifier == MessageQualifier.STORY) {
      return Collections.emptySet();
    }

    if (receiptType == ReceiptType.VIEWED) {
      return Collections.emptySet();
    }

    SQLiteDatabase     database       = databaseHelper.getSignalWritableDatabase();
    Set<MessageUpdate> messageUpdates = new HashSet<>();

    try (Cursor cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, RECIPIENT_ID, TYPE, DELIVERY_RECEIPT_COUNT, READ_RECEIPT_COUNT, RECEIPT_TIMESTAMP},
                                        DATE_SENT + " = ?", new String[] {String.valueOf(messageId.getTimetamp())},
                                        null, null, null, null))
    {
      while (cursor.moveToNext()) {
        if (Types.isOutgoingMessageType(CursorUtil.requireLong(cursor, TYPE))) {
          RecipientId theirRecipientId = messageId.getRecipientId();
          RecipientId outRecipientId   = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));

          if (outRecipientId.equals(theirRecipientId)) {
            long    id               = CursorUtil.requireLong(cursor, ID);
            long    threadId         = CursorUtil.requireLong(cursor, THREAD_ID);
            String  columnName       = receiptType.getColumnName();
            boolean isFirstIncrement = CursorUtil.requireLong(cursor, columnName) == 0;
            long    savedTimestamp   = CursorUtil.requireLong(cursor, RECEIPT_TIMESTAMP);
            long    updatedTimestamp = isFirstIncrement ? Math.max(savedTimestamp, timestamp) : savedTimestamp;

            database.execSQL("UPDATE " + TABLE_NAME +
                             " SET " + columnName + " = " + columnName + " + 1, " +
                             RECEIPT_TIMESTAMP + " = ? WHERE " +
                             ID + " = ?",
                             SqlUtil.buildArgs(updatedTimestamp, id));

            messageUpdates.add(new MessageUpdate(threadId, new MessageId(id, false)));
          }
        }
      }

      if (messageUpdates.isEmpty() && receiptType == ReceiptType.DELIVERY) {
        earlyDeliveryReceiptCache.increment(messageId.getTimetamp(), messageId.getRecipientId(), timestamp);
      }

      return messageUpdates;
    }
  }

  @Override
  public List<MarkedMessageInfo> setEntireThreadRead(long threadId) {
    return setMessagesRead(THREAD_ID + " = ?", new String[] {String.valueOf(threadId)});
  }

  @Override
  public List<MarkedMessageInfo> setMessagesReadSince(long threadId, long sinceTimestamp) {
    if (sinceTimestamp == -1) {
      return setMessagesRead(THREAD_ID + " = ? AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND (" + getOutgoingTypeClause() + ")))", new String[] {String.valueOf(threadId)});
    } else {
      return setMessagesRead(THREAD_ID + " = ? AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND ( " + getOutgoingTypeClause() + " ))) AND " + DATE_RECEIVED + " <= ?", new String[] {String.valueOf(threadId),String.valueOf(sinceTimestamp)});
    }
  }

  @Override
  public List<MarkedMessageInfo> setAllMessagesRead() {
    return setMessagesRead(READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND (" + getOutgoingTypeClause() + "))", null);
  }

  private List<MarkedMessageInfo> setMessagesRead(String where, String[] arguments) {
    SQLiteDatabase          database         = databaseHelper.getSignalWritableDatabase();
    List<MarkedMessageInfo> results          = new LinkedList<>();
    Cursor                  cursor           = null;
    RecipientId             releaseChannelId = SignalStore.releaseChannelValues().getReleaseChannelRecipientId();

    database.beginTransaction();
    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, RECIPIENT_ID, DATE_SENT, TYPE, EXPIRES_IN, EXPIRE_STARTED, THREAD_ID}, where, arguments, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        if (Types.isSecureType(CursorUtil.requireLong(cursor, TYPE))) {
          long           threadId       = CursorUtil.requireLong(cursor, THREAD_ID);
          RecipientId    recipientId    = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
          long           dateSent       = CursorUtil.requireLong(cursor, DATE_SENT);
          long           messageId      = CursorUtil.requireLong(cursor, ID);
          long           expiresIn      = CursorUtil.requireLong(cursor, EXPIRES_IN);
          long           expireStarted  = CursorUtil.requireLong(cursor, EXPIRE_STARTED);
          SyncMessageId  syncMessageId  = new SyncMessageId(recipientId, dateSent);
          ExpirationInfo expirationInfo = new ExpirationInfo(messageId, expiresIn, expireStarted, false);

          if (!recipientId.equals(releaseChannelId)) {
            results.add(new MarkedMessageInfo(threadId, syncMessageId, new MessageId(messageId, false), expirationInfo));
          }
        }
      }

      ContentValues contentValues = new ContentValues();
      contentValues.put(READ, 1);
      contentValues.put(REACTIONS_UNREAD, 0);
      contentValues.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

      database.update(TABLE_NAME, contentValues, where, arguments);
      database.setTransactionSuccessful();
    } finally {
      if (cursor != null) cursor.close();
      database.endTransaction();
    }

    return results;
  }

  @Override
  public InsertResult updateBundleMessageBody(long messageId, String body) {
    long type = Types.BASE_INBOX_TYPE | Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT;
    return updateMessageBodyAndType(messageId, body, Types.TOTAL_MASK, type);
  }

  @Override
  public @NonNull List<MarkedMessageInfo> getViewedIncomingMessages(long threadId) {
    return Collections.emptyList();
  }

  @Override
  public @Nullable MarkedMessageInfo setIncomingMessageViewed(long messageId) {
    return null;
  }

  @Override
  public @NonNull List<MarkedMessageInfo> setIncomingMessagesViewed(@NonNull List<Long> messageIds) {
    return Collections.emptyList();
  }

  @Override
  public @NonNull List<MarkedMessageInfo> setOutgoingGiftsRevealed(@NonNull List<Long> messageIds) {
    throw new UnsupportedOperationException();
  }

  private InsertResult updateMessageBodyAndType(long messageId, String body, long maskOff, long maskOn) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + BODY + " = ?, " +
                   TYPE + " = (" + TYPE + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + ") " +
                   "WHERE " + ID + " = ?",
               new String[] {body, messageId + ""});

    long threadId = getThreadIdForMessage(messageId);

    SignalDatabase.threads().update(threadId, true);
    notifyConversationListeners(threadId);

    return new InsertResult(messageId, threadId);
  }

  @Override
  public boolean hasReceivedAnyCallsSince(long threadId, long timestamp) {
    SQLiteDatabase db            = databaseHelper.getSignalReadableDatabase();
    String[]       projection    = SqlUtil.buildArgs(SmsTable.TYPE);
    String         selection     = THREAD_ID + " = ? AND " + DATE_RECEIVED  + " > ? AND (" + TYPE + " = ? OR " + TYPE + " = ? OR " + TYPE + " = ? OR " + TYPE + " =?)";
    String[]       selectionArgs = SqlUtil.buildArgs(threadId,
                                                     timestamp,
                                                     Types.INCOMING_AUDIO_CALL_TYPE,
                                                     Types.INCOMING_VIDEO_CALL_TYPE,
                                                     Types.MISSED_AUDIO_CALL_TYPE,
                                                     Types.MISSED_VIDEO_CALL_TYPE);

    try (Cursor cursor = db.query(TABLE_NAME, projection, selection, selectionArgs, null, null, null)) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  @Override
  public @NonNull Pair<Long, Long> insertReceivedCall(@NonNull RecipientId address, boolean isVideoOffer) {
    return insertCallLog(address, isVideoOffer ? Types.INCOMING_VIDEO_CALL_TYPE : Types.INCOMING_AUDIO_CALL_TYPE, false, System.currentTimeMillis());
  }

  @Override
  public @NonNull Pair<Long, Long> insertOutgoingCall(@NonNull RecipientId address, boolean isVideoOffer) {
    return insertCallLog(address, isVideoOffer ? Types.OUTGOING_VIDEO_CALL_TYPE : Types.OUTGOING_AUDIO_CALL_TYPE, false, System.currentTimeMillis());
  }

  @Override
  public @NonNull Pair<Long, Long> insertMissedCall(@NonNull RecipientId address, long timestamp, boolean isVideoOffer) {
    return insertCallLog(address, isVideoOffer ? Types.MISSED_VIDEO_CALL_TYPE : Types.MISSED_AUDIO_CALL_TYPE, true, timestamp);
  }

  @Override
  public void insertOrUpdateGroupCall(@NonNull RecipientId groupRecipientId,
                                      @NonNull RecipientId sender,
                                      long timestamp,
                                      @Nullable String peekGroupCallEraId,
                                      @NonNull Collection<UUID> peekJoinedUuids,
                                      boolean isCallFull)
  {
    SQLiteDatabase db                      = databaseHelper.getSignalWritableDatabase();
    Recipient      recipient               = Recipient.resolved(groupRecipientId);
    long           threadId                = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);
    boolean        peerEraIdSameAsPrevious = updatePreviousGroupCall(threadId, peekGroupCallEraId, peekJoinedUuids, isCallFull);

    try {
      db.beginTransaction();

      if (!peerEraIdSameAsPrevious && !Util.isEmpty(peekGroupCallEraId)) {
        Recipient self     = Recipient.self();
        boolean   markRead = peekJoinedUuids.contains(self.requireServiceId().uuid()) || self.getId().equals(sender);

        byte[] updateDetails = GroupCallUpdateDetails.newBuilder()
                                                     .setEraId(Util.emptyIfNull(peekGroupCallEraId))
                                                     .setStartedCallUuid(Recipient.resolved(sender).requireServiceId().toString())
                                                     .setStartedCallTimestamp(timestamp)
                                                     .addAllInCallUuids(Stream.of(peekJoinedUuids).map(UUID::toString).toList())
                                                     .setIsCallFull(isCallFull)
                                                     .build()
                                                     .toByteArray();

        String body = Base64.encodeBytes(updateDetails);

        ContentValues values = new ContentValues();
        values.put(RECIPIENT_ID, sender.serialize());
        values.put(RECIPIENT_DEVICE_ID, 1);
        values.put(DATE_RECEIVED, timestamp);
        values.put(DATE_SENT, timestamp);
        values.put(READ, markRead ? 1 : 0);
        values.put(BODY, body);
        values.put(TYPE, Types.GROUP_CALL_TYPE);
        values.put(THREAD_ID, threadId);

        db.insert(TABLE_NAME, null, values);

        SignalDatabase.threads().incrementUnread(threadId, 1, 0);
      }
      boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && recipient.isMuted();
      SignalDatabase.threads().update(threadId, !keepThreadArchived);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(threadId);
    TrimThreadJob.enqueueAsync(threadId);
  }

  @Override
  public void insertOrUpdateGroupCall(@NonNull RecipientId groupRecipientId,
                                      @NonNull RecipientId sender,
                                      long timestamp,
                                      @Nullable String messageGroupCallEraId)
  {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    long threadId;

    try {
      db.beginTransaction();

      Recipient recipient = Recipient.resolved(groupRecipientId);

      threadId = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);

      String   where     = TYPE + " = ? AND " + THREAD_ID + " = ?";
      String[] args      = SqlUtil.buildArgs(Types.GROUP_CALL_TYPE, threadId);
      boolean  sameEraId = false;

      try (Reader reader = new Reader(db.query(TABLE_NAME, MESSAGE_PROJECTION, where, args, null, null, DATE_RECEIVED + " DESC", "1"))) {
        MessageRecord record = reader.getNext();
        if (record != null) {
          GroupCallUpdateDetails groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(record.getBody());

          sameEraId = groupCallUpdateDetails.getEraId().equals(messageGroupCallEraId) && !Util.isEmpty(messageGroupCallEraId);

          if (!sameEraId) {
            String body = GroupCallUpdateDetailsUtil.createUpdatedBody(groupCallUpdateDetails, Collections.emptyList(), false);

            ContentValues contentValues = new ContentValues();
            contentValues.put(BODY, body);

            db.update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(record.getId()));
          }
        }
      }

      if (!sameEraId && !Util.isEmpty(messageGroupCallEraId)) {
        byte[] updateDetails = GroupCallUpdateDetails.newBuilder()
                                                     .setEraId(Util.emptyIfNull(messageGroupCallEraId))
                                                     .setStartedCallUuid(Recipient.resolved(sender).requireServiceId().toString())
                                                     .setStartedCallTimestamp(timestamp)
                                                     .addAllInCallUuids(Collections.emptyList())
                                                     .setIsCallFull(false)
                                                     .build()
                                                     .toByteArray();

        String body = Base64.encodeBytes(updateDetails);

        ContentValues values = new ContentValues();
        values.put(RECIPIENT_ID, sender.serialize());
        values.put(RECIPIENT_DEVICE_ID, 1);
        values.put(DATE_RECEIVED, timestamp);
        values.put(DATE_SENT, timestamp);
        values.put(READ, 0);
        values.put(BODY, body);
        values.put(TYPE, Types.GROUP_CALL_TYPE);
        values.put(THREAD_ID, threadId);

        db.insert(TABLE_NAME, null, values);

        SignalDatabase.threads().incrementUnread(threadId, 1, 0);
      }

      final boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && recipient.isMuted();
      SignalDatabase.threads().update(threadId, !keepThreadArchived);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(threadId);
    TrimThreadJob.enqueueAsync(threadId);
  }

  @Override
  public boolean updatePreviousGroupCall(long threadId, @Nullable String peekGroupCallEraId, @NonNull Collection<UUID> peekJoinedUuids, boolean isCallFull) {
    SQLiteDatabase db        = databaseHelper.getSignalWritableDatabase();
    String         where     = TYPE + " = ? AND " + THREAD_ID + " = ?";
    String[]       args      = SqlUtil.buildArgs(Types.GROUP_CALL_TYPE, threadId);
    boolean        sameEraId = false;

    try (Reader reader = new Reader(db.query(TABLE_NAME, MESSAGE_PROJECTION, where, args, null, null, DATE_RECEIVED + " DESC", "1"))) {
      MessageRecord record = reader.getNext();
      if (record == null) {
        return false;
      }

      GroupCallUpdateDetails groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(record.getBody());
      boolean                containsSelf           = peekJoinedUuids.contains(SignalStore.account().requireAci().uuid());

      sameEraId = groupCallUpdateDetails.getEraId().equals(peekGroupCallEraId) && !Util.isEmpty(peekGroupCallEraId);

      List<String> inCallUuids = sameEraId ? Stream.of(peekJoinedUuids).map(UUID::toString).toList()
                                           : Collections.emptyList();

      String body = GroupCallUpdateDetailsUtil.createUpdatedBody(groupCallUpdateDetails, inCallUuids, isCallFull);

      ContentValues contentValues = new ContentValues();
      contentValues.put(BODY, body);

      if (sameEraId && containsSelf) {
        contentValues.put(READ, 1);
      }

      SqlUtil.Query query   = SqlUtil.buildTrueUpdateQuery(ID_WHERE, SqlUtil.buildArgs(record.getId()), contentValues);
      boolean       updated = db.update(TABLE_NAME, contentValues, query.getWhere(), query.getWhereArgs()) > 0;

      if (updated) {
        notifyConversationListeners(threadId);
      }
    }

    return sameEraId;
  }

  private @NonNull Pair<Long, Long> insertCallLog(@NonNull RecipientId recipientId, long type, boolean unread, long timestamp) {
    Recipient recipient = Recipient.resolved(recipientId);
    long      threadId  = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);

    ContentValues values = new ContentValues(6);
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, timestamp);
    values.put(READ, unread ? 0 : 1);
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);

    SQLiteDatabase db        = databaseHelper.getSignalWritableDatabase();
    long           messageId = db.insert(TABLE_NAME, null, values);

    if (unread) {
      SignalDatabase.threads().incrementUnread(threadId, 1, 0);
    }
    boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && Recipient.resolved(recipientId).isMuted();
    SignalDatabase.threads().update(threadId, !keepThreadArchived);

    notifyConversationListeners(threadId);
    TrimThreadJob.enqueueAsync(threadId);

    return new Pair<>(messageId, threadId);
  }

  @Override
  public Set<Long> getAllRateLimitedMessageIds() {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         where = "(" + TYPE + " & " + Types.TOTAL_MASK + " & " + Types.MESSAGE_RATE_LIMITED_BIT + ") > 0";

    Set<Long> ids = new HashSet<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { ID }, where, null, null, null, null)) {
      while (cursor.moveToNext()) {
        ids.add(CursorUtil.requireLong(cursor, ID));
      }
    }

    return ids;
  }

  @Override
  public Cursor getUnexportedInsecureMessages(int limit) {
    return queryMessages(
        SqlUtil.appendArg(MESSAGE_PROJECTION, EXPORT_STATE),
        getInsecureMessageClause() + " AND NOT " + EXPORTED,
        null,
        false,
        limit
    );
  }

  @Override
  public long getUnexportedInsecureMessagesEstimatedSize() {
    Cursor cursor = SQLiteDatabaseExtensionsKt.select(getReadableDatabase(), "SUM(LENGTH(" + BODY + "))")
                                              .from(TABLE_NAME)
                                              .where(getInsecureMessageClause() + " AND " + EXPORTED + " < ?", MessageExportStatus.EXPORTED)
                                              .run();

    return CursorExtensionsKt.readToSingleLong(cursor);
  }

  @Override
  public void deleteExportedMessages() {
    beginTransaction();
    try {
      List<Long> threadsToUpdate = new LinkedList<>();
      try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, THREAD_ID_PROJECTION, EXPORTED + " = ?", SqlUtil.buildArgs(MessageExportStatus.EXPORTED), THREAD_ID, null, null, null)) {
        while (cursor.moveToNext()) {
          threadsToUpdate.add(CursorUtil.requireLong(cursor, THREAD_ID));
        }
      }

      getWritableDatabase().delete(TABLE_NAME, EXPORTED + " = ?", SqlUtil.buildArgs(MessageExportStatus.EXPORTED));

      for (final long threadId : threadsToUpdate) {
        SignalDatabase.threads().update(threadId, false);
      }

      setTransactionSuccessful();
    } finally {
      endTransaction();
    }
  }

  @Override
  public List<MessageRecord> getProfileChangeDetailsRecords(long threadId, long afterTimestamp) {
    String   where = THREAD_ID + " = ? AND " + DATE_RECEIVED + " >= ? AND " + TYPE + " = ?";
    String[] args  = SqlUtil.buildArgs(threadId, afterTimestamp, Types.PROFILE_CHANGE_TYPE);

    try (Reader reader = readerFor(queryMessages(where, args, true, -1))) {
      List<MessageRecord> results = new ArrayList<>(reader.getCount());
      while (reader.getNext() != null) {
        results.add(reader.getCurrent());
      }

      return results;
    }
  }

  @Override
  public void insertProfileNameChangeMessages(@NonNull Recipient recipient, @NonNull String newProfileName, @NonNull String previousProfileName) {
    ThreadTable                  threadTable  = SignalDatabase.threads();
    List<GroupTable.GroupRecord> groupRecords = SignalDatabase.groups().getGroupsContainingMember(recipient.getId(), false);
    List<Long>                   threadIdsToUpdate = new LinkedList<>();

    byte[] profileChangeDetails = ProfileChangeDetails.newBuilder()
                                                      .setProfileNameChange(ProfileChangeDetails.StringChange.newBuilder()
                                                                                                             .setNew(newProfileName)
                                                                                                             .setPrevious(previousProfileName))
                                                      .build()
                                                      .toByteArray();

    String body = Base64.encodeBytes(profileChangeDetails);

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.beginTransaction();

    try {
      threadIdsToUpdate.add(threadTable.getThreadIdFor(recipient.getId()));
      for (GroupTable.GroupRecord groupRecord : groupRecords) {
        if (groupRecord.isActive()) {
          threadIdsToUpdate.add(threadTable.getThreadIdFor(groupRecord.getRecipientId()));
        }
      }

      Stream.of(threadIdsToUpdate)
            .withoutNulls()
            .forEach(threadId -> {
              ContentValues values = new ContentValues();
              values.put(RECIPIENT_ID, recipient.getId().serialize());
              values.put(RECIPIENT_DEVICE_ID, 1);
              values.put(DATE_RECEIVED, System.currentTimeMillis());
              values.put(DATE_SENT, System.currentTimeMillis());
              values.put(READ, 1);
              values.put(TYPE, Types.PROFILE_CHANGE_TYPE);
              values.put(THREAD_ID, threadId);
              values.put(BODY, body);

              db.insert(TABLE_NAME, null, values);

              notifyConversationListeners(threadId);
            });

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    Stream.of(threadIdsToUpdate)
          .withoutNulls()
          .forEach(TrimThreadJob::enqueueAsync);
  }

  @Override
  public void insertGroupV1MigrationEvents(@NonNull RecipientId recipientId,
                                           long threadId,
                                           @NonNull GroupMigrationMembershipChange membershipChange)
  {
    insertGroupV1MigrationNotification(recipientId, threadId);

    if (!membershipChange.isEmpty()) {
      insertGroupV1MigrationMembershipChanges(recipientId, threadId, membershipChange);
    }

    notifyConversationListeners(threadId);
    TrimThreadJob.enqueueAsync(threadId);
  }

  private void insertGroupV1MigrationNotification(@NonNull RecipientId recipientId, long threadId) {
    insertGroupV1MigrationMembershipChanges(recipientId, threadId, GroupMigrationMembershipChange.empty());
  }

  private void insertGroupV1MigrationMembershipChanges(@NonNull RecipientId recipientId,
                                                       long threadId,
                                                       @NonNull GroupMigrationMembershipChange membershipChange)
  {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, 1);
    values.put(TYPE, Types.GV1_MIGRATION_TYPE);
    values.put(THREAD_ID, threadId);

    if (!membershipChange.isEmpty()) {
      values.put(BODY, membershipChange.serialize());
    }

    databaseHelper.getSignalWritableDatabase().insert(TABLE_NAME, null, values);
  }

  @Override
  public void insertNumberChangeMessages(@NonNull RecipientId recipientId) {
    ThreadTable                  threadTable  = SignalDatabase.threads();
    List<GroupTable.GroupRecord> groupRecords = SignalDatabase.groups().getGroupsContainingMember(recipientId, false);
    List<Long>                   threadIdsToUpdate = new LinkedList<>();

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.beginTransaction();

    try {
      threadIdsToUpdate.add(threadTable.getThreadIdFor(recipientId));
      for (GroupTable.GroupRecord groupRecord : groupRecords) {
        if (groupRecord.isActive()) {
          threadIdsToUpdate.add(threadTable.getThreadIdFor(groupRecord.getRecipientId()));
        }
      }

      threadIdsToUpdate.stream()
                       .filter(Objects::nonNull)
                       .forEach(threadId -> {
                         ContentValues values = new ContentValues();
                         values.put(RECIPIENT_ID, recipientId.serialize());
                         values.put(RECIPIENT_DEVICE_ID, 1);
                         values.put(DATE_RECEIVED, System.currentTimeMillis());
                         values.put(DATE_SENT, System.currentTimeMillis());
                         values.put(READ, 1);
                         values.put(TYPE, Types.CHANGE_NUMBER_TYPE);
                         values.put(THREAD_ID, threadId);
                         values.putNull(BODY);

                         db.insert(TABLE_NAME, null, values);
                       });

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    threadIdsToUpdate.stream()
                     .filter(Objects::nonNull)
                     .forEach(threadId -> {
                       TrimThreadJob.enqueueAsync(threadId);
                       SignalDatabase.threads().update(threadId, true);
                       notifyConversationListeners(threadId);
                     });
  }

  @Override
  public void insertBoostRequestMessage(@NonNull RecipientId recipientId, long threadId) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, 1);
    values.put(TYPE, Types.BOOST_REQUEST_TYPE);
    values.put(THREAD_ID, threadId);
    values.putNull(BODY);

    getWritableDatabase().insert(TABLE_NAME, null, values);
  }

  @Override
  public void insertThreadMergeEvent(@NonNull RecipientId recipientId, long threadId, @NonNull ThreadMergeEvent event) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, 1);
    values.put(TYPE, Types.THREAD_MERGE_TYPE);
    values.put(THREAD_ID, threadId);
    values.put(BODY, Base64.encodeBytes(event.toByteArray()));

    getWritableDatabase().insert(TABLE_NAME, null, values);

    ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId);
  }

  @Override
  public void insertSmsExportMessage(@NonNull RecipientId recipientId, long threadId) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, 1);
    values.put(TYPE, Types.SMS_EXPORT_TYPE);
    values.put(THREAD_ID, threadId);
    values.putNull(BODY);

    boolean updated = SQLiteDatabaseExtensionsKt.withinTransaction(getWritableDatabase(), db -> {
      if (SignalDatabase.sms().hasSmsExportMessage(threadId)) {
        return false;
      } else {
        db.insert(TABLE_NAME, null, values);
        return true;
      }
    });

    if (updated) {
      ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId);
    }
  }

  @Override
  public Optional<InsertResult> insertMessageInbox(IncomingTextMessage message, long type) {
    boolean tryToCollapseJoinRequestEvents = false;

    if (message.isJoined()) {
      type = (type & (Types.TOTAL_MASK - Types.BASE_TYPE_MASK)) | Types.JOINED_TYPE;
    } else if (message.isPreKeyBundle()) {
      type |= Types.KEY_EXCHANGE_BIT | Types.KEY_EXCHANGE_BUNDLE_BIT;
    } else if (message.isSecureMessage()) {
      type |= Types.SECURE_MESSAGE_BIT;
    } else if (message.isGroup()) {
      IncomingGroupUpdateMessage incomingGroupUpdateMessage = (IncomingGroupUpdateMessage) message;

      type |= Types.SECURE_MESSAGE_BIT;

      if (incomingGroupUpdateMessage.isGroupV2()) {
        type |= Types.GROUP_V2_BIT | Types.GROUP_UPDATE_BIT;
        if (incomingGroupUpdateMessage.isJustAGroupLeave()) {
          type |= Types.GROUP_LEAVE_BIT;
        } else if (incomingGroupUpdateMessage.isCancelJoinRequest()) {
          tryToCollapseJoinRequestEvents = true;
        }
      } else if (incomingGroupUpdateMessage.isUpdate()) {
        type |= Types.GROUP_UPDATE_BIT;
      } else if (incomingGroupUpdateMessage.isQuit()) {
        type |= Types.GROUP_LEAVE_BIT;
      }

    } else if (message.isEndSession()) {
      type |= Types.SECURE_MESSAGE_BIT;
      type |= Types.END_SESSION_BIT;
    }

    if (message.isPush())                type |= Types.PUSH_MESSAGE_BIT;
    if (message.isIdentityUpdate())      type |= Types.KEY_EXCHANGE_IDENTITY_UPDATE_BIT;
    if (message.isContentPreKeyBundle()) type |= Types.KEY_EXCHANGE_CONTENT_FORMAT;

    if      (message.isIdentityVerified())    type |= Types.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT;
    else if (message.isIdentityDefault())     type |= Types.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT;

    Recipient recipient = Recipient.resolved(message.getSender());

    Recipient groupRecipient;

    if (message.getGroupId() == null) {
      groupRecipient = null;
    } else {
      RecipientId id = SignalDatabase.recipients().getOrInsertFromPossiblyMigratedGroupId(message.getGroupId());
      groupRecipient = Recipient.resolved(id);
    }

    boolean silent = message.isIdentityUpdate()   ||
                     message.isIdentityVerified() ||
                     message.isIdentityDefault()  ||
                     message.isJustAGroupLeave()  ||
                     (type & Types.GROUP_UPDATE_BIT) > 0;

    boolean unread = !silent && (Util.isDefaultSmsProvider(context) ||
                                 message.isSecureMessage()          ||
                                 message.isGroup()                  ||
                                 message.isPreKeyBundle());

    long       threadId;

    if (groupRecipient == null) threadId = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);
    else                        threadId = SignalDatabase.threads().getOrCreateThreadIdFor(groupRecipient);

    if (tryToCollapseJoinRequestEvents) {
        final Optional<InsertResult> result = collapseJoinRequestEventsIfPossible(threadId, (IncomingGroupUpdateMessage) message);
        if (result.isPresent()) {
          return result;
        }
    }

    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, message.getSender().serialize());
    values.put(RECIPIENT_DEVICE_ID, message.getSenderDeviceId());
    values.put(DATE_RECEIVED, message.getReceivedTimestampMillis());
    values.put(DATE_SENT, message.getSentTimestampMillis());
    values.put(DATE_SERVER, message.getServerTimestampMillis());
    values.put(READ, unread ? 0 : 1);
    values.put(SMS_SUBSCRIPTION_ID, message.getSubscriptionId());
    values.put(EXPIRES_IN, message.getExpiresIn());
    values.put(UNIDENTIFIED, message.isUnidentified());
    values.put(BODY, message.getMessageBody());
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);
    values.put(SERVER_GUID, message.getServerGuid());

    if (message.isPush() && isDuplicate(message, threadId)) {
      Log.w(TAG, "Duplicate message (" + message.getSentTimestampMillis() + "), ignoring...");
      return Optional.empty();
    } else {
      SQLiteDatabase db        = databaseHelper.getSignalWritableDatabase();
      long           messageId = db.insert(TABLE_NAME, null, values);

      if (unread) {
        SignalDatabase.threads().incrementUnread(threadId, 1, 0);
      }

      if (!silent) {
        final boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && recipient.isMuted();
        SignalDatabase.threads().update(threadId, !keepThreadArchived);
      }

      if (message.getSubscriptionId() != -1) {
        SignalDatabase.recipients().setDefaultSubscriptionId(recipient.getId(), message.getSubscriptionId());
      }

      notifyConversationListeners(threadId);

      if (!silent) {
        TrimThreadJob.enqueueAsync(threadId);
      }

      return Optional.of(new InsertResult(messageId, threadId));
    }
  }

  @Override
  public Optional<InsertResult> insertMessageInbox(IncomingTextMessage message) {
    return insertMessageInbox(message, Types.BASE_INBOX_TYPE);
  }

  @Override
  public @NonNull InsertResult insertChatSessionRefreshedMessage(@NonNull RecipientId recipientId, long senderDeviceId, long sentTimestamp) {
    SQLiteDatabase db       = databaseHelper.getSignalWritableDatabase();
    long           threadId = SignalDatabase.threads().getOrCreateThreadIdFor(Recipient.resolved(recipientId));
    long           type     = Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT;

    type = type & (Types.TOTAL_MASK - Types.ENCRYPTION_MASK) | Types.ENCRYPTION_REMOTE_FAILED_BIT;

    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, senderDeviceId);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, sentTimestamp);
    values.put(DATE_SERVER, -1);
    values.put(READ, 0);
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);

    long messageId = db.insert(TABLE_NAME, null, values);

    SignalDatabase.threads().incrementUnread(threadId, 1, 0);
    boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && Recipient.resolved(recipientId).isMuted();
    SignalDatabase.threads().update(threadId, !keepThreadArchived);

    notifyConversationListeners(threadId);

    TrimThreadJob.enqueueAsync(threadId);

    return new InsertResult(messageId, threadId);
  }

  @Override
  public void insertBadDecryptMessage(@NonNull RecipientId recipientId, int senderDevice, long sentTimestamp, long receivedTimestamp, long threadId) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, senderDevice);
    values.put(DATE_SENT, sentTimestamp);
    values.put(DATE_RECEIVED, receivedTimestamp);
    values.put(DATE_SERVER, -1);
    values.put(READ, 0);
    values.put(TYPE, Types.BAD_DECRYPT_TYPE);
    values.put(THREAD_ID, threadId);

    databaseHelper.getSignalWritableDatabase().insert(TABLE_NAME, null, values);

    SignalDatabase.threads().incrementUnread(threadId, 1, 0);
    boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && Recipient.resolved(recipientId).isMuted();
    SignalDatabase.threads().update(threadId, !keepThreadArchived);

    notifyConversationListeners(threadId);

    TrimThreadJob.enqueueAsync(threadId);
  }

  @Override
  public long insertMessageOutbox(long threadId, OutgoingTextMessage message,
                                  boolean forceSms, long date, InsertListener insertListener)
  {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    long type = Types.BASE_SENDING_TYPE;

    if      (message.isKeyExchange())   type |= Types.KEY_EXCHANGE_BIT;
    else if (message.isSecureMessage()) type |= (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT);
    else if (message.isEndSession())    type |= Types.END_SESSION_BIT;
    if      (forceSms)                  type |= Types.MESSAGE_FORCE_SMS_BIT;

    if      (message.isIdentityVerified()) type |= Types.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT;
    else if (message.isIdentityDefault())  type |= Types.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT;

    RecipientId                                 recipientId           = message.getRecipient().getId();
    Map<RecipientId, EarlyReceiptCache.Receipt> earlyDeliveryReceipts = earlyDeliveryReceiptCache.remove(date);

    ContentValues contentValues = new ContentValues(6);
    contentValues.put(RECIPIENT_ID, recipientId.serialize());
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(BODY, message.getMessageBody());
    contentValues.put(DATE_RECEIVED, System.currentTimeMillis());
    contentValues.put(DATE_SENT, date);
    contentValues.put(READ, 1);
    contentValues.put(TYPE, type);
    contentValues.put(SMS_SUBSCRIPTION_ID, message.getSubscriptionId());
    contentValues.put(EXPIRES_IN, message.getExpiresIn());
    contentValues.put(DELIVERY_RECEIPT_COUNT, Stream.of(earlyDeliveryReceipts.values()).mapToLong(EarlyReceiptCache.Receipt::getCount).sum());
    contentValues.put(RECEIPT_TIMESTAMP, Stream.of(earlyDeliveryReceipts.values()).mapToLong(EarlyReceiptCache.Receipt::getTimestamp).max().orElse(-1));

    long messageId = db.insert(TABLE_NAME, null, contentValues);

    if (insertListener != null) {
      insertListener.onComplete();
    }

    if (!message.isIdentityVerified() && !message.isIdentityDefault()) {
      SignalDatabase.threads().setLastScrolled(threadId, 0);
      SignalDatabase.threads().setLastSeenSilently(threadId);
    }

    SignalDatabase.threads().setHasSentSilently(threadId, true);

    ApplicationDependencies.getDatabaseObserver().notifyMessageInsertObservers(threadId, new MessageId(messageId, false));

    if (!message.isIdentityVerified() && !message.isIdentityDefault()) {
      TrimThreadJob.enqueueAsync(threadId);
    }

    return messageId;
  }

  @Override
  public Cursor getExpirationStartedMessages() {
    String         where = EXPIRE_STARTED + " > 0";
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, where, null, null, null, null);
  }

  @Override
  public SmsMessageRecord getSmsMessage(long messageId) throws NoSuchMessageException {
    SQLiteDatabase db     = databaseHelper.getSignalReadableDatabase();
    Cursor         cursor = db.query(TABLE_NAME, MESSAGE_PROJECTION, ID_WHERE, new String[]{messageId + ""}, null, null, null);
    Reader         reader = new Reader(cursor);
    SmsMessageRecord record = reader.getNext();

    reader.close();

    if (record == null) throw new NoSuchMessageException("No message for ID: " + messageId);
    else                return record;
  }

  @Override
  public Cursor getMessageCursor(long messageId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, ID_WHERE, new String[] {messageId + ""}, null, null, null);
  }

  @Override
  public boolean deleteMessage(long messageId) {
    Log.d(TAG, "deleteMessage(" + messageId + ")");

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    long    threadId;
    boolean threadDeleted;

    db.beginTransaction();
    try {
      threadId = getThreadIdForMessage(messageId);

      db.delete(TABLE_NAME, ID_WHERE, new String[] { messageId + "" });

      SignalDatabase.threads().setLastScrolled(threadId, 0);
      threadDeleted = SignalDatabase.threads().update(threadId, false, true);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(threadId);
    return threadDeleted;
  }

  @Override
  public void ensureMigration() {
    databaseHelper.getSignalWritableDatabase();
  }

  @Override
  public boolean isStory(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull MessageTable.Reader getOutgoingStoriesTo(@NonNull RecipientId recipientId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull MessageTable.Reader getAllOutgoingStories(boolean reverse, int limit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull MessageTable.Reader getAllOutgoingStoriesAt(long sentTimestamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull List<MarkedMessageInfo> markAllIncomingStoriesRead() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull List<StoryResult> getOrderedStoryRecipientsAndIds(boolean isOutgoingOnly) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markOnboardingStoryRead() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull MessageTable.Reader getAllStoriesFor(@NonNull RecipientId recipientId, int limit) {
    throw new UnsupportedOperationException();
  }

  public boolean isOutgoingStoryAlreadyInDatabase(@NonNull RecipientId recipientId, long sentTimestamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull MessageId getStoryId(@NonNull RecipientId authorId, long sentTimestamp) throws NoSuchMessageException {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getNumberOfStoryReplies(long parentStoryId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull List<RecipientId>  getUnreadStoryThreadRecipientIds() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsStories(long threadId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasSelfReplyInStory(long parentStoryId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasGroupReplyOrReactionInStory(long parentStoryId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull Cursor getStoryReplies(long parentStoryId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable Long getOldestStorySendTimestamp(boolean hasSeenReleaseChannelStories) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull StoryViewState getStoryViewState(@NonNull RecipientId recipientId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateViewedStories(@NonNull Set<SyncMessageId> syncMessageIds) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int deleteStoriesOlderThan(long timestamp, boolean hasSeenReleaseChannelStories) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull MessageTable.Reader getUnreadStories(@NonNull RecipientId recipientId, int limit) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable ParentStoryId.GroupReply getParentStoryIdForGroupReply(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull List<MarkedMessageInfo> setGroupStoryMessagesReadSince(long threadId, long groupStoryId, long sinceTimestamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull List<StoryType> getStoryTypes(@NonNull List<MessageId> messageIds) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteGroupStoryReplies(long parentStoryId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MessageRecord getMessageRecord(long messageId) throws NoSuchMessageException {
    return getSmsMessage(messageId);
  }

  @Override
  public @Nullable MessageRecord getMessageRecordOrNull(long messageId) {
    try {
      return getSmsMessage(messageId);
    } catch (NoSuchMessageException e) {
      return null;
    }
  }

  private boolean isDuplicate(IncomingTextMessage message, long threadId) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = DATE_SENT + " = ? AND " + RECIPIENT_ID + " = ? AND " + THREAD_ID + " = ?";
    String[]       args  = SqlUtil.buildArgs(message.getSentTimestampMillis(), message.getSender().serialize(), threadId);

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { "1" }, query, args, null, null, null, "1")) {
      return cursor.moveToFirst();
    }
  }

  @Override
  void deleteThread(long threadId) {
    Log.d(TAG, "deleteThread(" + threadId + ")");
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.delete(TABLE_NAME, THREAD_ID + " = ?", new String[] {threadId+""});
  }

  @Override
  int deleteMessagesInThreadBeforeDate(long threadId, long date) {
    SQLiteDatabase db    = databaseHelper.getSignalWritableDatabase();
    String         where = THREAD_ID + " = ? AND " + DATE_RECEIVED + " < " + date;

    return db.delete(TABLE_NAME, where, SqlUtil.buildArgs(threadId));
  }

  @Override
  void deleteAbandonedMessages() {
    SQLiteDatabase db    = databaseHelper.getSignalWritableDatabase();
    String         where = THREAD_ID + " NOT IN (SELECT _id FROM " + ThreadTable.TABLE_NAME + ")";

    int deletes = db.delete(TABLE_NAME, where, null);
    if (deletes > 0) {
      Log.i(TAG, "Deleted " + deletes + " abandoned messages");
    }
  }

  @Override
  public void deleteRemotelyDeletedStory(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<MessageRecord> getMessagesInThreadAfterInclusive(long threadId, long timestamp, long limit) {
    String   where = TABLE_NAME + "." + MmsSmsColumns.THREAD_ID + " = ? AND " +
                     TABLE_NAME + "." + getDateReceivedColumnName() + " >= ?";
    String[] args  = SqlUtil.buildArgs(threadId, timestamp);

    try (Reader reader = readerFor(queryMessages(where, args, false, limit))) {
      List<MessageRecord> results = new ArrayList<>(reader.cursor.getCount());

      while (reader.getNext() != null) {
        results.add(reader.getCurrent());
      }

      return results;
    }
  }

  private Cursor queryMessages(@NonNull String where, @Nullable String[] args, boolean reverse, long limit) {
    return queryMessages(MESSAGE_PROJECTION, where, args, reverse, limit);
  }

  private Cursor queryMessages(@NonNull String[] projection, @NonNull String where, @Nullable String[] args, boolean reverse, long limit) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    return db.query(TABLE_NAME,
                    projection,
                    where,
                    args,
                    null,
                    null,
                    reverse ? ID + " DESC" : null,
                    limit > 0 ? String.valueOf(limit) : null);
  }

  @Override
  void deleteThreads(@NonNull Set<Long> threadIds) {
    Log.d(TAG, "deleteThreads(count: " + threadIds.size() + ")");

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    String where      = "";

    for (long threadId : threadIds) {
      where += THREAD_ID + " = '" + threadId + "' OR ";
    }

    where = where.substring(0, where.length() - 4);

    db.delete(TABLE_NAME, where, null);
  }

  @Override
  void deleteAllThreads() {
    Log.d(TAG, "deleteAllThreads()");
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  @Override
  public SQLiteDatabase beginTransaction() {
    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    database.beginTransaction();
    return database;
  }

  @Override
  public void setTransactionSuccessful() {
    databaseHelper.getSignalWritableDatabase().setTransactionSuccessful();
  }

  @Override
  public void endTransaction(SQLiteDatabase database) {
    database.setTransactionSuccessful();
    database.endTransaction();
  }

  @Override
  public void endTransaction() {
    databaseHelper.getSignalWritableDatabase().endTransaction();
  }

  @Override
  public @Nullable ViewOnceExpirationInfo getNearestExpiringViewOnceMessage() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSent(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLatestGroupQuitTimestamp(long threadId, long quitTimeBarrier) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isGroupQuitMessage(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable Pair<RecipientId, Long> getOldestUnreadMentionDetails(long threadId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getUnreadMentionCount(long threadId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addFailures(long messageId, List<NetworkFailure> failure) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setNetworkFailures(long messageId, Set<NetworkFailure> failures) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markDownloadState(long messageId, long state) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<MmsNotificationInfo> getNotification(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public OutgoingMediaMessage getOutgoingMessage(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<InsertResult> insertMessageInbox(IncomingMediaMessage retrieved, String contentLocation, long threadId) throws MmsException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Pair<Long, Long> insertMessageInbox(@NonNull NotificationInd notification, int subscriptionId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<InsertResult> insertSecureDecryptedMessageInbox(IncomingMediaMessage retrieved, long threadId) throws MmsException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long insertMessageOutbox(@NonNull OutgoingMediaMessage message, long threadId, boolean forceSms, @Nullable InsertListener insertListener) throws MmsException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long insertMessageOutbox(@NonNull OutgoingMediaMessage message, long threadId, boolean forceSms, int defaultReceiptStatus, @Nullable InsertListener insertListener) throws MmsException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markIncomingNotificationReceived(long threadId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markGiftRedemptionCompleted(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markGiftRedemptionStarted(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markGiftRedemptionFailed(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MessageTable.Reader getMessages(Collection<Long> messageIds) {
    throw new UnsupportedOperationException();
  }


  public static class Status {
    public static final int STATUS_NONE     = -1;
    public static final int STATUS_COMPLETE  = 0;
    public static final int STATUS_PENDING   = 0x20;
    public static final int STATUS_FAILED    = 0x40;
  }

  public static Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public static OutgoingMessageReader readerFor(OutgoingTextMessage message, long threadId, long messageId) {
    return new OutgoingMessageReader(message, threadId, messageId);
  }

  public static class OutgoingMessageReader {

    private final OutgoingTextMessage message;
    private final long                id;
    private final long                threadId;

    public OutgoingMessageReader(OutgoingTextMessage message, long threadId, long messageId) {
      this.message  = message;
      this.threadId = threadId;
      this.id       = messageId;
    }

    public MessageRecord getCurrent() {
      return new SmsMessageRecord(id,
                                  message.getMessageBody(),
                                  message.getRecipient(),
                                  message.getRecipient(),
                                  1,
                                  System.currentTimeMillis(),
                                  System.currentTimeMillis(),
                                  -1,
                                  0,
                                  message.isSecureMessage() ? MmsSmsColumns.Types.getOutgoingEncryptedMessageType() : MmsSmsColumns.Types.getOutgoingSmsMessageType(),
                                  threadId,
                                  0,
                                  new HashSet<>(),
                                  message.getSubscriptionId(),
                                  message.getExpiresIn(),
                                  System.currentTimeMillis(),
                                  0,
                                  false,
                                  Collections.emptyList(),
                                  false,
                                  0,
                                  -1);
    }
  }

  public static class Reader implements MessageTable.Reader {

    private final Cursor  cursor;
    private final Context context;

    public Reader(Cursor cursor) {
      this.cursor  = cursor;
      this.context = ApplicationDependencies.getApplication();
    }

    public SmsMessageRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public int getCount() {
      if (cursor == null) return 0;
      else                return cursor.getCount();
    }

    @Override
    public @NonNull MessageExportState getMessageExportStateForCurrentRecord() {
      byte[] messageExportState = CursorUtil.requireBlob(cursor, SmsTable.EXPORT_STATE);
      if (messageExportState == null) {
        return MessageExportState.getDefaultInstance();
      }

      try {
        return MessageExportState.parseFrom(messageExportState);
      } catch (InvalidProtocolBufferException e) {
        return MessageExportState.getDefaultInstance();
      }
    }

    public SmsMessageRecord getCurrent() {
      long                 messageId            = cursor.getLong(cursor.getColumnIndexOrThrow(SmsTable.ID));
      long                 recipientId          = cursor.getLong(cursor.getColumnIndexOrThrow(SmsTable.RECIPIENT_ID));
      int                  addressDeviceId      = cursor.getInt(cursor.getColumnIndexOrThrow(SmsTable.RECIPIENT_DEVICE_ID));
      long                 type                 = cursor.getLong(cursor.getColumnIndexOrThrow(SmsTable.TYPE));
      long                 dateReceived         = cursor.getLong(cursor.getColumnIndexOrThrow(SmsTable.DATE_RECEIVED));
      long                 dateSent             = cursor.getLong(cursor.getColumnIndexOrThrow(SmsTable.DATE_SENT));
      long                 dateServer           = cursor.getLong(cursor.getColumnIndexOrThrow(SmsTable.DATE_SERVER));
      long                 threadId             = cursor.getLong(cursor.getColumnIndexOrThrow(SmsTable.THREAD_ID));
      int                  status               = cursor.getInt(cursor.getColumnIndexOrThrow(SmsTable.SMS_STATUS));
      int                  deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(SmsTable.DELIVERY_RECEIPT_COUNT));
      int                  readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(SmsTable.READ_RECEIPT_COUNT));
      String               mismatchDocument     = cursor.getString(cursor.getColumnIndexOrThrow(SmsTable.MISMATCHED_IDENTITIES));
      int                  subscriptionId       = cursor.getInt(cursor.getColumnIndexOrThrow(SmsTable.SMS_SUBSCRIPTION_ID));
      long                 expiresIn            = cursor.getLong(cursor.getColumnIndexOrThrow(SmsTable.EXPIRES_IN));
      long                 expireStarted        = cursor.getLong(cursor.getColumnIndexOrThrow(SmsTable.EXPIRE_STARTED));
      String               body                 = cursor.getString(cursor.getColumnIndexOrThrow(SmsTable.BODY));
      boolean              unidentified         = cursor.getInt(cursor.getColumnIndexOrThrow(SmsTable.UNIDENTIFIED)) == 1;
      boolean              remoteDelete         = cursor.getInt(cursor.getColumnIndexOrThrow(SmsTable.REMOTE_DELETED)) == 1;
      long                 notifiedTimestamp    = CursorUtil.requireLong(cursor, NOTIFIED_TIMESTAMP);
      long                 receiptTimestamp     = CursorUtil.requireLong(cursor, RECEIPT_TIMESTAMP);

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount = 0;
      }

      Set<IdentityKeyMismatch> mismatches = getMismatches(mismatchDocument);
      Recipient                recipient  = Recipient.live(RecipientId.from(recipientId)).get();

      return new SmsMessageRecord(messageId, body, recipient,
                                  recipient,
                                  addressDeviceId,
                                  dateSent, dateReceived, dateServer, deliveryReceiptCount, type,
                                  threadId, status, mismatches, subscriptionId,
                                  expiresIn, expireStarted,
                                  readReceiptCount, unidentified, Collections.emptyList(), remoteDelete,
                                  notifiedTimestamp, receiptTimestamp);
    }

    private Set<IdentityKeyMismatch> getMismatches(String document) {
      try {
        if (!TextUtils.isEmpty(document)) {
          return JsonUtils.fromJson(document, IdentityKeyMismatchSet.class).getItems();
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      return Collections.emptySet();
    }

    @Override
    public void close() {
      cursor.close();
    }

    @Override
    public @NonNull Iterator<MessageRecord> iterator() {
      return new ReaderIterator();
    }

    private class ReaderIterator implements Iterator<MessageRecord> {
      @Override
      public boolean hasNext() {
        return cursor != null && cursor.getCount() != 0 && !cursor.isLast();
      }

      @Override
      public MessageRecord next() {
        MessageRecord record = getNext();
        if (record == null) {
          throw new NoSuchElementException();
        }

        return record;
      }
    }
  }

  /**
   * The number of change number messages in the thread.
   * Currently only used for tests.
   */
  @VisibleForTesting
  int getChangeNumberMessageCount(@NonNull RecipientId recipientId) {
    try (Cursor cursor = SQLiteDatabaseExtensionsKt
        .select(getReadableDatabase(), "COUNT(*)")
        .from(TABLE_NAME)
        .where(RECIPIENT_ID + " = ? AND " + TYPE + " = ?", recipientId, CHANGE_NUMBER_TYPE)
        .run())
    {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  @VisibleForTesting
  Optional<InsertResult> collapseJoinRequestEventsIfPossible(long threadId, IncomingGroupUpdateMessage message) {
    InsertResult result = null;


    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();

    try {
      try (MmsSmsTable.Reader reader = MmsSmsTable.readerFor(SignalDatabase.mmsSms().getConversation(threadId, 0, 2))) {
        MessageRecord latestMessage = reader.getNext();
        if (latestMessage != null && latestMessage.isGroupV2()) {
          Optional<ByteString> changeEditor = message.getChangeEditor();
          if (changeEditor.isPresent() && latestMessage.isGroupV2JoinRequest(changeEditor.get())) {
            String encodedBody;
            long   id;

            MessageRecord secondLatestMessage = reader.getNext();
            if (secondLatestMessage != null && secondLatestMessage.isGroupV2JoinRequest(changeEditor.get())) {
              id          = secondLatestMessage.getId();
              encodedBody = MessageRecord.createNewContextWithAppendedDeleteJoinRequest(secondLatestMessage, message.getChangeRevision(), changeEditor.get());
              deleteMessage(latestMessage.getId());
            } else {
              id          = latestMessage.getId();
              encodedBody = MessageRecord.createNewContextWithAppendedDeleteJoinRequest(latestMessage, message.getChangeRevision(), changeEditor.get());
            }

            ContentValues values = new ContentValues(1);
            values.put(BODY, encodedBody);
            getWritableDatabase().update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(id));
            result = new InsertResult(id, threadId);
          }
        }
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    return Optional.ofNullable(result);
  }
}

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

import com.annimon.stream.Stream;
import com.google.android.mms.pdu_alt.NotificationInd;

import net.sqlcipher.database.SQLiteStatement;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchList;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.jobs.TrimThreadJob;
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
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.Closeable;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Database for storage of SMS messages.
 *
 * @author Moxie Marlinspike
 */
public class SmsDatabase extends MessageDatabase {

  private static final String TAG = SmsDatabase.class.getSimpleName();

  public  static final String TABLE_NAME         = "sms";
  public  static final String PERSON             = "person";
          static final String DATE_RECEIVED      = "date";
          static final String DATE_SENT          = "date_sent";
  public  static final String PROTOCOL           = "protocol";
  public  static final String STATUS             = "status";
  public  static final String TYPE               = "type";
  public  static final String REPLY_PATH_PRESENT = "reply_path_present";
  public  static final String SUBJECT            = "subject";
  public  static final String SERVICE_CENTER     = "service_center";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                     + " INTEGER PRIMARY KEY, " +
                                                                                  THREAD_ID              + " INTEGER, " +
                                                                                  RECIPIENT_ID           + " INTEGER, " +
                                                                                  ADDRESS_DEVICE_ID      + " INTEGER DEFAULT 1, " +
                                                                                  PERSON                 + " INTEGER, " +
                                                                                  DATE_RECEIVED          + " INTEGER, " +
                                                                                  DATE_SENT              + " INTEGER, " +
                                                                                  DATE_SERVER            + " INTEGER DEFAULT -1, " +
                                                                                  PROTOCOL               + " INTEGER, " +
                                                                                  READ                   + " INTEGER DEFAULT 0, " +
                                                                                  STATUS                 + " INTEGER DEFAULT -1," +
                                                                                  TYPE                   + " INTEGER, " +
                                                                                  REPLY_PATH_PRESENT     + " INTEGER, " +
                                                                                  DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0," +
                                                                                  SUBJECT                + " TEXT, " +
                                                                                  BODY                   + " TEXT, " +
                                                                                  MISMATCHED_IDENTITIES  + " TEXT DEFAULT NULL, " +
                                                                                  SERVICE_CENTER         + " TEXT, " +
                                                                                  SUBSCRIPTION_ID        + " INTEGER DEFAULT -1, " +
                                                                                  EXPIRES_IN             + " INTEGER DEFAULT 0, " +
                                                                                  EXPIRE_STARTED         + " INTEGER DEFAULT 0, " +
                                                                                  NOTIFIED               + " DEFAULT 0, " +
                                                                                  READ_RECEIPT_COUNT     + " INTEGER DEFAULT 0, " +
                                                                                  UNIDENTIFIED           + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS              + " BLOB DEFAULT NULL, " +
                                                                                  REACTIONS_UNREAD       + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS_LAST_SEEN    + " INTEGER DEFAULT -1, " +
                                                                                  REMOTE_DELETED         + " INTEGER DEFAULT 0, " +
                                                                                  NOTIFIED_TIMESTAMP     + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS sms_thread_id_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS sms_read_index ON " + TABLE_NAME + " (" + READ + ");",
    "CREATE INDEX IF NOT EXISTS sms_read_and_notified_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + NOTIFIED + ","  + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS sms_type_index ON " + TABLE_NAME + " (" + TYPE + ");",
    "CREATE INDEX IF NOT EXISTS sms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ");",
    "CREATE INDEX IF NOT EXISTS sms_date_server_index ON " + TABLE_NAME + " (" + DATE_SERVER + ");",
    "CREATE INDEX IF NOT EXISTS sms_thread_date_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");",
    "CREATE INDEX IF NOT EXISTS sms_reactions_unread_index ON " + TABLE_NAME + " (" + REACTIONS_UNREAD + ");"
  };

  private static final String[] MESSAGE_PROJECTION = new String[] {
      ID, THREAD_ID, RECIPIENT_ID, ADDRESS_DEVICE_ID, PERSON,
      DATE_RECEIVED + " AS " + NORMALIZED_DATE_RECEIVED,
      DATE_SENT + " AS " + NORMALIZED_DATE_SENT,
      DATE_SERVER,
      PROTOCOL, READ, STATUS, TYPE,
      REPLY_PATH_PRESENT, SUBJECT, BODY, SERVICE_CENTER, DELIVERY_RECEIPT_COUNT,
      MISMATCHED_IDENTITIES, SUBSCRIPTION_ID, EXPIRES_IN, EXPIRE_STARTED,
      NOTIFIED, READ_RECEIPT_COUNT, UNIDENTIFIED, REACTIONS, REACTIONS_UNREAD, REACTIONS_LAST_SEEN,
      REMOTE_DELETED, NOTIFIED_TIMESTAMP
  };

  private final String OUTGOING_INSECURE_MESSAGE_CLAUSE = "(" + TYPE + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND NOT (" + TYPE + " & " + Types.SECURE_MESSAGE_BIT + ")";
  private final String OUTGOING_SECURE_MESSAGE_CLAUSE   = "(" + TYPE + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND (" + TYPE + " & " + (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT) + ")";

  private static final EarlyReceiptCache earlyDeliveryReceiptCache = new EarlyReceiptCache("SmsDelivery");

  public SmsDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
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
    Log.i(TAG, "Updating ID: " + id + " to base type: " + maskOn);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME +
               " SET " + TYPE + " = (" + TYPE + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
               " WHERE " + ID + " = ?", new String[] {id+""});

    long threadId = getThreadIdForMessage(id);

    DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
  }

  @Override
  public @Nullable RecipientId getOldestGroupUpdateSender(long threadId, long minimumDateReceived) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    String[] columns = new String[]{RECIPIENT_ID};
    String   query   = THREAD_ID + " = ? AND " + TYPE + " & ? AND " + DATE_RECEIVED + " >= ?";
    long     type    = Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT | Types.GROUP_UPDATE_BIT | Types.BASE_INBOX_TYPE;
    String[] args    = new String[]{String.valueOf(threadId), String.valueOf(type), String.valueOf(minimumDateReceived)};
    String   limit   = "1";

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, args, null, null, limit)) {
      if (cursor.moveToFirst()) {
        return RecipientId.from(cursor.getLong(cursor.getColumnIndex(RECIPIENT_ID)));
      }
    }

    return null;
  }

  @Override
  public long getThreadIdForMessage(long id) {
    String sql        = "SELECT " + THREAD_ID + " FROM " + TABLE_NAME + " WHERE " + ID + " = ?";
    String[] sqlArgs  = new String[] {id+""};
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    Cursor cursor = null;

    try {
      cursor = db.rawQuery(sql, sqlArgs);
      if (cursor != null && cursor.moveToFirst())
        return cursor.getLong(0);
      else
        return -1;
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  @Override
  public int getMessageCountForThreadSummary(long threadId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    String[] cols  = { "COUNT(*)" };
    String   query = THREAD_ID + " = ? AND (NOT " + TYPE + " & ? AND TYPE != ?)";
    long     type  = Types.END_SESSION_BIT | Types.KEY_EXCHANGE_IDENTITY_UPDATE_BIT | Types.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT;
    String[] args  = SqlUtil.buildArgs(threadId, type, Types.PROFILE_CHANGE_TYPE);

    try (Cursor cursor = db.query(TABLE_NAME, cols, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int count = cursor.getInt(0);
        if (count > 0) {
          return getMessageCountForThread(threadId);
        }
      }
    }

    return 0;
  }

  @Override
  public int getMessageCountForThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    String[] cols  = new String[] {"COUNT(*)"};
    String   query = THREAD_ID + " = ?";
    String[] args  = new String[]{String.valueOf(threadId)};

    try (Cursor cursor = db.query(TABLE_NAME, cols, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  @Override
  public int getMessageCountForThread(long threadId, long beforeTime) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

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
  public void markAsEndSession(long id) {
    updateTypeBitmask(id, Types.KEY_EXCHANGE_MASK, Types.END_SESSION_BIT);
  }

  @Override
  public void markAsPreKeyBundle(long id) {
    updateTypeBitmask(id, Types.KEY_EXCHANGE_MASK, Types.KEY_EXCHANGE_BIT | Types.KEY_EXCHANGE_BUNDLE_BIT);
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
  public void markAsDecryptFailed(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_FAILED_BIT);
  }

  @Override
  public void markAsDecryptDuplicate(long id) {
    updateTypeBitmask(id, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_DUPLICATE_BIT);
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
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    ContentValues values = new ContentValues();
    values.put(REMOTE_DELETED, 1);
    values.putNull(BODY);
    values.putNull(REACTIONS);
    db.update(TABLE_NAME, values, ID_WHERE, new String[] { String.valueOf(id) });

    long threadId = getThreadIdForMessage(id);

    DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
  }

  @Override
  public void markUnidentified(long id, boolean unidentified) {
    ContentValues contentValues = new ContentValues(1);
    contentValues.put(UNIDENTIFIED, unidentified ? 1 : 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
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
    SQLiteDatabase db       = databaseHelper.getWritableDatabase();
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

    DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
  }

  @Override
  public void markSmsStatus(long id, int status) {
    Log.i(TAG, "Updating ID: " + id + " to status: " + status);
    ContentValues contentValues = new ContentValues();
    contentValues.put(STATUS, status);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {id+""});

    long threadId = getThreadIdForMessage(id);
    DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
  }

  @Override
  public void markAsSentFailed(long id) {
    updateTypeBitmask(id, Types.BASE_TYPE_MASK, Types.BASE_SENT_FAILED_TYPE);
  }

  @Override
  public void markAsNotified(long id) {
    SQLiteDatabase database      = databaseHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues();

    contentValues.put(NOTIFIED, 1);

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(id)});
  }

  @Override
  public boolean incrementReceiptCount(SyncMessageId messageId, long timestamp, @NonNull ReceiptType receiptType) {
    if (receiptType == ReceiptType.VIEWED) {
      return false;
    }

    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    boolean        foundMessage = false;

    try (Cursor cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, RECIPIENT_ID, TYPE, DELIVERY_RECEIPT_COUNT, READ_RECEIPT_COUNT},
                              DATE_SENT + " = ?", new String[] {String.valueOf(messageId.getTimetamp())},
                              null, null, null, null)) {

      while (cursor.moveToNext()) {
        if (Types.isOutgoingMessageType(cursor.getLong(cursor.getColumnIndexOrThrow(TYPE)))) {
          RecipientId theirRecipientId = messageId.getRecipientId();
          RecipientId outRecipientId   = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)));
          String      columnName       = receiptType.getColumnName();
          boolean     isFirstIncrement = cursor.getLong(cursor.getColumnIndexOrThrow(columnName)) == 0;

          if (outRecipientId.equals(theirRecipientId)) {
            long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));

            database.execSQL("UPDATE " + TABLE_NAME +
                             " SET " + columnName + " = " + columnName + " + 1 WHERE " +
                             ID + " = ?",
                             new String[] {String.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(ID)))});

            DatabaseFactory.getThreadDatabase(context).update(threadId, false);

            if (isFirstIncrement) {
              notifyConversationListeners(threadId);
            } else {
              notifyVerboseConversationListeners(threadId);
            }

            foundMessage = true;
          }
        }
      }

      if (!foundMessage && receiptType == ReceiptType.DELIVERY) {
        earlyDeliveryReceiptCache.increment(messageId.getTimetamp(), messageId.getRecipientId());
        return true;
      }

      return foundMessage;
    }
  }

  @Override
  public List<Pair<Long, Long>> setTimestampRead(SyncMessageId messageId, long proposedExpireStarted) {
    SQLiteDatabase         database = databaseHelper.getWritableDatabase();
    List<Pair<Long, Long>> expiring = new LinkedList<>();
    Cursor                 cursor   = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, RECIPIENT_ID, TYPE, EXPIRES_IN, EXPIRE_STARTED},
                              DATE_SENT + " = ?", new String[] {String.valueOf(messageId.getTimetamp())},
                              null, null, null, null);

      while (cursor.moveToNext()) {
        RecipientId theirRecipientId = messageId.getRecipientId();
        RecipientId outRecipientId   = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)));

        if (outRecipientId.equals(theirRecipientId)) {
          long id            = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
          long threadId      = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
          long expiresIn     = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN));
          long expireStarted = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRE_STARTED));

          expireStarted = expireStarted > 0 ? Math.min(proposedExpireStarted, expireStarted) : proposedExpireStarted;

          ContentValues contentValues = new ContentValues();
          contentValues.put(READ, 1);

          if (expiresIn > 0) {
            contentValues.put(EXPIRE_STARTED, expireStarted);
            expiring.add(new Pair<>(id, expiresIn));
          }

          database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {cursor.getLong(cursor.getColumnIndexOrThrow(ID)) + ""});

          DatabaseFactory.getThreadDatabase(context).updateReadState(threadId);
          DatabaseFactory.getThreadDatabase(context).setLastSeen(threadId);
          notifyConversationListeners(threadId);
        }
      }
    } finally {
      if (cursor != null) cursor.close();
    }

    return expiring;
  }

  @Override
  public List<MarkedMessageInfo> setEntireThreadRead(long threadId) {
    return setMessagesRead(THREAD_ID + " = ?", new String[] {String.valueOf(threadId)});
  }

  @Override
  public List<MarkedMessageInfo> setMessagesReadSince(long threadId, long sinceTimestamp) {
    if (sinceTimestamp == -1) {
      return setMessagesRead(THREAD_ID + " = ? AND " + READ + " = 0", new String[] {String.valueOf(threadId)});
    } else {
      return setMessagesRead(THREAD_ID + " = ? AND " + READ + " = 0 AND " + DATE_RECEIVED + " <= ?", new String[] {String.valueOf(threadId),String.valueOf(sinceTimestamp)});
    }
  }

  @Override
  public List<MarkedMessageInfo> setAllMessagesRead() {
    return setMessagesRead(READ + " = 0", null);
  }

  private List<MarkedMessageInfo> setMessagesRead(String where, String[] arguments) {
    SQLiteDatabase          database  = databaseHelper.getWritableDatabase();
    List<MarkedMessageInfo> results   = new LinkedList<>();
    Cursor                  cursor    = null;

    database.beginTransaction();
    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, RECIPIENT_ID, DATE_SENT, TYPE, EXPIRES_IN, EXPIRE_STARTED, THREAD_ID}, where, arguments, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        if (Types.isSecureType(cursor.getLong(cursor.getColumnIndex(TYPE)))) {
          long           threadId       = cursor.getLong(cursor.getColumnIndex(THREAD_ID));
          RecipientId    recipientId    = RecipientId.from(cursor.getLong(cursor.getColumnIndex(RECIPIENT_ID)));
          long           dateSent       = cursor.getLong(cursor.getColumnIndex(DATE_SENT));
          long           messageId      = cursor.getLong(cursor.getColumnIndex(ID));
          long           expiresIn      = cursor.getLong(cursor.getColumnIndex(EXPIRES_IN));
          long           expireStarted  = cursor.getLong(cursor.getColumnIndex(EXPIRE_STARTED));
          SyncMessageId  syncMessageId  = new SyncMessageId(recipientId, dateSent);
          ExpirationInfo expirationInfo = new ExpirationInfo(messageId, expiresIn, expireStarted, false);

          results.add(new MarkedMessageInfo(threadId, syncMessageId, expirationInfo));
        }
      }

      ContentValues contentValues = new ContentValues();
      contentValues.put(READ, 1);

      database.update(TABLE_NAME, contentValues, where, arguments);
      database.setTransactionSuccessful();
    } finally {
      if (cursor != null) cursor.close();
      database.endTransaction();
    }

    return results;
  }

  @Override
  public Pair<Long, Long> updateBundleMessageBody(long messageId, String body) {
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

  private Pair<Long, Long> updateMessageBodyAndType(long messageId, String body, long maskOff, long maskOn) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + BODY + " = ?, " +
                   TYPE + " = (" + TYPE + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + ") " +
                   "WHERE " + ID + " = ?",
               new String[] {body, messageId + ""});

    long threadId = getThreadIdForMessage(messageId);

    DatabaseFactory.getThreadDatabase(context).update(threadId, true);
    notifyConversationListeners(threadId);
    notifyConversationListListeners();

    return new Pair<>(messageId, threadId);
  }

  @Override
  public boolean hasReceivedAnyCallsSince(long threadId, long timestamp) {
    SQLiteDatabase db            = databaseHelper.getReadableDatabase();
    String[]       projection    = SqlUtil.buildArgs(SmsDatabase.TYPE);
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
    SQLiteDatabase db                      = databaseHelper.getWritableDatabase();
    Recipient      recipient               = Recipient.resolved(groupRecipientId);
    long           threadId                = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    boolean        peerEraIdSameAsPrevious = updatePreviousGroupCall(threadId, peekGroupCallEraId, peekJoinedUuids, isCallFull);

    try {
      db.beginTransaction();

      if (!peerEraIdSameAsPrevious && !Util.isEmpty(peekGroupCallEraId)) {
        Recipient self     = Recipient.self();
        boolean   markRead = peekJoinedUuids.contains(self.requireUuid()) || self.getId().equals(sender);

        byte[] updateDetails = GroupCallUpdateDetails.newBuilder()
                                                     .setEraId(Util.emptyIfNull(peekGroupCallEraId))
                                                     .setStartedCallUuid(Recipient.resolved(sender).requireUuid().toString())
                                                     .setStartedCallTimestamp(timestamp)
                                                     .addAllInCallUuids(Stream.of(peekJoinedUuids).map(UUID::toString).toList())
                                                     .setIsCallFull(isCallFull)
                                                     .build()
                                                     .toByteArray();

        String body = Base64.encodeBytes(updateDetails);

        ContentValues values = new ContentValues();
        values.put(RECIPIENT_ID, sender.serialize());
        values.put(ADDRESS_DEVICE_ID, 1);
        values.put(DATE_RECEIVED, timestamp);
        values.put(DATE_SENT, timestamp);
        values.put(READ, markRead ? 1 : 0);
        values.put(BODY, body);
        values.put(TYPE, Types.GROUP_CALL_TYPE);
        values.put(THREAD_ID, threadId);

        db.insert(TABLE_NAME, null, values);

        DatabaseFactory.getThreadDatabase(context).incrementUnread(threadId, 1);
      }

      DatabaseFactory.getThreadDatabase(context).update(threadId, true);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(threadId);
    ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId));
  }

  @Override
  public void insertOrUpdateGroupCall(@NonNull RecipientId groupRecipientId,
                                      @NonNull RecipientId sender,
                                      long timestamp,
                                      @Nullable String messageGroupCallEraId)
  {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    long threadId;

    try {
      db.beginTransaction();

      Recipient recipient = Recipient.resolved(groupRecipientId);

      threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

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
                                                     .setStartedCallUuid(Recipient.resolved(sender).requireUuid().toString())
                                                     .setStartedCallTimestamp(timestamp)
                                                     .addAllInCallUuids(Collections.emptyList())
                                                     .setIsCallFull(false)
                                                     .build()
                                                     .toByteArray();

        String body = Base64.encodeBytes(updateDetails);

        ContentValues values = new ContentValues();
        values.put(RECIPIENT_ID, sender.serialize());
        values.put(ADDRESS_DEVICE_ID, 1);
        values.put(DATE_RECEIVED, timestamp);
        values.put(DATE_SENT, timestamp);
        values.put(READ, 0);
        values.put(BODY, body);
        values.put(TYPE, Types.GROUP_CALL_TYPE);
        values.put(THREAD_ID, threadId);

        db.insert(TABLE_NAME, null, values);

        DatabaseFactory.getThreadDatabase(context).incrementUnread(threadId, 1);
      }

      DatabaseFactory.getThreadDatabase(context).update(threadId, true);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(threadId);
    ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId));
  }

  @Override
  public boolean updatePreviousGroupCall(long threadId, @Nullable String peekGroupCallEraId, @NonNull Collection<UUID> peekJoinedUuids, boolean isCallFull) {
    SQLiteDatabase db        = databaseHelper.getWritableDatabase();
    String         where     = TYPE + " = ? AND " + THREAD_ID + " = ?";
    String[]       args      = SqlUtil.buildArgs(Types.GROUP_CALL_TYPE, threadId);
    boolean        sameEraId = false;

    try (Reader reader = new Reader(db.query(TABLE_NAME, MESSAGE_PROJECTION, where, args, null, null, DATE_RECEIVED + " DESC", "1"))) {
      MessageRecord record = reader.getNext();
      if (record == null) {
        return false;
      }

      GroupCallUpdateDetails groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(record.getBody());
      boolean                containsSelf           = peekJoinedUuids.contains(Recipient.self().requireUuid());

      sameEraId = groupCallUpdateDetails.getEraId().equals(peekGroupCallEraId) && !Util.isEmpty(peekGroupCallEraId);

      List<String> inCallUuids = sameEraId ? Stream.of(peekJoinedUuids).map(UUID::toString).toList()
                                           : Collections.emptyList();

      String body = GroupCallUpdateDetailsUtil.createUpdatedBody(groupCallUpdateDetails, inCallUuids, isCallFull);

      ContentValues contentValues = new ContentValues();
      contentValues.put(BODY, body);

      if (sameEraId && containsSelf) {
        contentValues.put(READ, 1);
      }

      db.update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(record.getId()));
    }

    notifyConversationListeners(threadId);

    return sameEraId;
  }

  private @NonNull Pair<Long, Long> insertCallLog(@NonNull RecipientId recipientId, long type, boolean unread, long timestamp) {
    Recipient recipient = Recipient.resolved(recipientId);
    long      threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);

    ContentValues values = new ContentValues(6);
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(ADDRESS_DEVICE_ID,  1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, timestamp);
    values.put(READ, unread ? 0 : 1);
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    long messageId    = db.insert(TABLE_NAME, null, values);

    DatabaseFactory.getThreadDatabase(context).update(threadId, true);
    if (unread) {
      DatabaseFactory.getThreadDatabase(context).incrementUnread(threadId, 1);
    }

    notifyConversationListeners(threadId);
    ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId));

    return new Pair<>(messageId, threadId);
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
    ThreadDatabase                  threadDatabase    = DatabaseFactory.getThreadDatabase(context);
    List<GroupDatabase.GroupRecord> groupRecords      = DatabaseFactory.getGroupDatabase(context).getGroupsContainingMember(recipient.getId(), false);
    List<Long>                      threadIdsToUpdate = new LinkedList<>();

    byte[] profileChangeDetails = ProfileChangeDetails.newBuilder()
                                                      .setProfileNameChange(ProfileChangeDetails.StringChange.newBuilder()
                                                                                                             .setNew(newProfileName)
                                                                                                             .setPrevious(previousProfileName))
                                                      .build()
                                                      .toByteArray();

    String body = Base64.encodeBytes(profileChangeDetails);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.beginTransaction();

    try {
      threadIdsToUpdate.add(threadDatabase.getThreadIdFor(recipient.getId()));
      for (GroupDatabase.GroupRecord groupRecord : groupRecords) {
        if (groupRecord.isActive()) {
          threadIdsToUpdate.add(threadDatabase.getThreadIdFor(groupRecord.getRecipientId()));
        }
      }

      Stream.of(threadIdsToUpdate)
            .withoutNulls()
            .forEach(threadId -> {
              ContentValues values = new ContentValues();
              values.put(RECIPIENT_ID, recipient.getId().serialize());
              values.put(ADDRESS_DEVICE_ID, 1);
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
          .forEach(threadId -> ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId)));
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
    ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId));
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
    values.put(ADDRESS_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, 1);
    values.put(TYPE, Types.GV1_MIGRATION_TYPE);
    values.put(THREAD_ID, threadId);

    if (!membershipChange.isEmpty()) {
      values.put(BODY, membershipChange.serialize());
    }

    databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, values);
  }

  @Override
  public Optional<InsertResult> insertMessageInbox(IncomingTextMessage message, long type) {
    if (message.isJoined()) {
      type = (type & (Types.TOTAL_MASK - Types.BASE_TYPE_MASK)) | Types.JOINED_TYPE;
    } else if (message.isPreKeyBundle()) {
      type |= Types.KEY_EXCHANGE_BIT | Types.KEY_EXCHANGE_BUNDLE_BIT;
    } else if (message.isSecureMessage()) {
      type |= Types.SECURE_MESSAGE_BIT;
    } else if (message.isGroup()) {
      IncomingGroupUpdateMessage incomingGroupUpdateMessage = (IncomingGroupUpdateMessage) message;

      type |= Types.SECURE_MESSAGE_BIT;

      if      (incomingGroupUpdateMessage.isGroupV2()) type |= Types.GROUP_V2_BIT | Types.GROUP_UPDATE_BIT;
      else if (incomingGroupUpdateMessage.isUpdate())  type |= Types.GROUP_UPDATE_BIT;
      else if (incomingGroupUpdateMessage.isQuit())    type |= Types.GROUP_QUIT_BIT;

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
      RecipientId id = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromPossiblyMigratedGroupId(message.getGroupId());
      groupRecipient = Recipient.resolved(id);
    }

    boolean    unread     = (org.thoughtcrime.securesms.util.Util.isDefaultSmsProvider(context) ||
                            message.isSecureMessage() || message.isGroup() || message.isPreKeyBundle()) &&
                            !message.isIdentityUpdate() && !message.isIdentityDefault() && !message.isIdentityVerified();

    long       threadId;

    if (groupRecipient == null) threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    else                        threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);

    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, message.getSender().serialize());
    values.put(ADDRESS_DEVICE_ID,  message.getSenderDeviceId());
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, message.getSentTimestampMillis());
    values.put(DATE_SERVER, message.getServerTimestampMillis());
    values.put(PROTOCOL, message.getProtocol());
    values.put(READ, unread ? 0 : 1);
    values.put(SUBSCRIPTION_ID, message.getSubscriptionId());
    values.put(EXPIRES_IN, message.getExpiresIn());
    values.put(UNIDENTIFIED, message.isUnidentified());

    if (!TextUtils.isEmpty(message.getPseudoSubject()))
      values.put(SUBJECT, message.getPseudoSubject());

    values.put(REPLY_PATH_PRESENT, message.isReplyPathPresent());
    values.put(SERVICE_CENTER, message.getServiceCenterAddress());
    values.put(BODY, message.getMessageBody());
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);

    if (message.isPush() && isDuplicate(message, threadId)) {
      Log.w(TAG, "Duplicate message (" + message.getSentTimestampMillis() + "), ignoring...");
      return Optional.absent();
    } else {
      SQLiteDatabase db        = databaseHelper.getWritableDatabase();
      long           messageId = db.insert(TABLE_NAME, null, values);

      if (unread) {
        DatabaseFactory.getThreadDatabase(context).incrementUnread(threadId, 1);
      }

      if (!message.isIdentityUpdate() && !message.isIdentityVerified() && !message.isIdentityDefault()) {
        DatabaseFactory.getThreadDatabase(context).update(threadId, true);
      }

      if (message.getSubscriptionId() != -1) {
        DatabaseFactory.getRecipientDatabase(context).setDefaultSubscriptionId(recipient.getId(), message.getSubscriptionId());
      }

      notifyConversationListeners(threadId);

      if (!message.isIdentityUpdate() && !message.isIdentityVerified() && !message.isIdentityDefault()) {
        ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId));
      }

      return Optional.of(new InsertResult(messageId, threadId));
    }
  }

  @Override
  public Optional<InsertResult> insertMessageInbox(IncomingTextMessage message) {
    return insertMessageInbox(message, Types.BASE_INBOX_TYPE);
  }

  @Override
  public @NonNull InsertResult insertDecryptionFailedMessage(@NonNull RecipientId recipientId, long senderDeviceId, long sentTimestamp) {
    SQLiteDatabase db       = databaseHelper.getWritableDatabase();
    long           threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(Recipient.resolved(recipientId));
    long           type     = Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT;

    type = type & (Types.TOTAL_MASK - Types.ENCRYPTION_MASK) | Types.ENCRYPTION_REMOTE_FAILED_BIT;

    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(ADDRESS_DEVICE_ID,  senderDeviceId);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, sentTimestamp);
    values.put(DATE_SERVER, -1);
    values.put(READ, 0);
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);

    long messageId = db.insert(TABLE_NAME, null, values);

    DatabaseFactory.getThreadDatabase(context).incrementUnread(threadId, 1);
    DatabaseFactory.getThreadDatabase(context).update(threadId, true);

    notifyConversationListeners(threadId);

    ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId));

    return new InsertResult(messageId, threadId);
  }

  @Override
  public long insertMessageOutbox(long threadId, OutgoingTextMessage message,
                                  boolean forceSms, long date, InsertListener insertListener)
  {
    long type = Types.BASE_SENDING_TYPE;

    if      (message.isKeyExchange())   type |= Types.KEY_EXCHANGE_BIT;
    else if (message.isSecureMessage()) type |= (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT);
    else if (message.isEndSession())    type |= Types.END_SESSION_BIT;
    if      (forceSms)                  type |= Types.MESSAGE_FORCE_SMS_BIT;

    if      (message.isIdentityVerified()) type |= Types.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT;
    else if (message.isIdentityDefault())  type |= Types.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT;

    RecipientId            recipientId           = message.getRecipient().getId();
    Map<RecipientId, Long> earlyDeliveryReceipts = earlyDeliveryReceiptCache.remove(date);

    ContentValues contentValues = new ContentValues(6);
    contentValues.put(RECIPIENT_ID, recipientId.serialize());
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(BODY, message.getMessageBody());
    contentValues.put(DATE_RECEIVED, System.currentTimeMillis());
    contentValues.put(DATE_SENT, date);
    contentValues.put(READ, 1);
    contentValues.put(TYPE, type);
    contentValues.put(SUBSCRIPTION_ID, message.getSubscriptionId());
    contentValues.put(EXPIRES_IN, message.getExpiresIn());
    contentValues.put(DELIVERY_RECEIPT_COUNT, Stream.of(earlyDeliveryReceipts.values()).mapToLong(Long::longValue).sum());

    SQLiteDatabase db        = databaseHelper.getWritableDatabase();
    long           messageId = db.insert(TABLE_NAME, null, contentValues);

    if (insertListener != null) {
      insertListener.onComplete();
    }

    if (!message.isIdentityVerified() && !message.isIdentityDefault()) {
      DatabaseFactory.getThreadDatabase(context).update(threadId, true);
      DatabaseFactory.getThreadDatabase(context).setLastSeen(threadId);
    }

    DatabaseFactory.getThreadDatabase(context).setHasSent(threadId, true);

    notifyConversationListeners(threadId);

    if (!message.isIdentityVerified() && !message.isIdentityDefault()) {
      ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId));
    }

    return messageId;
  }

  @Override
  public Cursor getExpirationStartedMessages() {
    String         where = EXPIRE_STARTED + " > 0";
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, where, null, null, null, null);
  }

  @Override
  public SmsMessageRecord getSmsMessage(long messageId) throws NoSuchMessageException {
    SQLiteDatabase db     = databaseHelper.getReadableDatabase();
    Cursor         cursor = db.query(TABLE_NAME, MESSAGE_PROJECTION, ID_WHERE, new String[]{messageId + ""}, null, null, null);
    Reader         reader = new Reader(cursor);
    SmsMessageRecord record = reader.getNext();

    reader.close();

    if (record == null) throw new NoSuchMessageException("No message for ID: " + messageId);
    else                return record;
  }

  @Override
  public Cursor getVerboseMessageCursor(long messageId) {
    Cursor cursor = getMessageCursor(messageId);
    setNotifyVerboseConversationListeners(cursor, getThreadIdForMessage(messageId));
    return cursor;
  }

  @Override
  public Cursor getMessageCursor(long messageId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    return db.query(TABLE_NAME, MESSAGE_PROJECTION, ID_WHERE, new String[] {messageId + ""}, null, null, null);
  }

  @Override
  public boolean deleteMessage(long messageId) {
    Log.d(TAG, "deleteMessage(" + messageId + ")");

    SQLiteDatabase db       = databaseHelper.getWritableDatabase();
    long           threadId = getThreadIdForMessage(messageId);

    db.delete(TABLE_NAME, ID_WHERE, new String[] {messageId+""});

    boolean threadDeleted = DatabaseFactory.getThreadDatabase(context).update(threadId, false, true);

    notifyConversationListeners(threadId);
    return threadDeleted;
  }

  @Override
  public void ensureMigration() {
    databaseHelper.getWritableDatabase();
  }

  @Override
  public MessageRecord getMessageRecord(long messageId) throws NoSuchMessageException {
    return getSmsMessage(messageId);
  }

  private boolean isDuplicate(IncomingTextMessage message, long threadId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = database.query(TABLE_NAME, null, DATE_SENT + " = ? AND " + RECIPIENT_ID + " = ? AND " + THREAD_ID + " = ?",
                                             new String[]{String.valueOf(message.getSentTimestampMillis()), message.getSender().serialize(), String.valueOf(threadId)},
                                             null, null, null, "1");

    try {
      return cursor != null && cursor.moveToFirst();
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  @Override
  void deleteThread(long threadId) {
    Log.d(TAG, "deleteThread(" + threadId + ")");
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, THREAD_ID + " = ?", new String[] {threadId+""});
  }

  @Override
  void deleteMessagesInThreadBeforeDate(long threadId, long date) {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();
    String         where = THREAD_ID + " = ? AND " + DATE_RECEIVED + " < " + date;

    db.delete(TABLE_NAME, where, SqlUtil.buildArgs(threadId));
  }

  @Override
  void deleteAbandonedMessages() {
    SQLiteDatabase db    = databaseHelper.getWritableDatabase();
    String         where = THREAD_ID + " NOT IN (SELECT _id FROM " + ThreadDatabase.TABLE_NAME + ")";

    db.delete(TABLE_NAME, where, null);
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

  private Cursor queryMessages(@NonNull String where, @NonNull String[] args, boolean reverse, long limit) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    return db.query(TABLE_NAME,
                    MESSAGE_PROJECTION,
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

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
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
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, null, null);
  }

  @Override
  public SQLiteDatabase beginTransaction() {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();
    return database;
  }

  @Override
  public void setTransactionSuccessful() {
    databaseHelper.getWritableDatabase().setTransactionSuccessful();
  }

  @Override
  public void endTransaction(SQLiteDatabase database) {
    database.setTransactionSuccessful();
    database.endTransaction();
  }

  @Override
  public void endTransaction() {
    databaseHelper.getWritableDatabase().endTransaction();
  }

  @Override
  public SQLiteStatement createInsertStatement(SQLiteDatabase database) {
    return database.compileStatement("INSERT INTO " + TABLE_NAME + " (" + RECIPIENT_ID + ", " +
                                                                          PERSON + ", " +
                                                                          DATE_SENT + ", " +
                                                                          DATE_RECEIVED  + ", " +
                                                                          PROTOCOL + ", " +
                                                                          READ + ", " +
                                                                          STATUS + ", " +
                                                                          TYPE + ", " +
                                                                          REPLY_PATH_PRESENT + ", " +
                                                                          SUBJECT + ", " +
                                                                          BODY + ", " +
                                                                          SERVICE_CENTER +
                                                                          ", " + THREAD_ID + ") " +
                                     " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
  public void removeFailure(long messageId, NetworkFailure failure) {
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
  public MessageDatabase.Reader getMessages(Collection<Long> messageIds) {
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

  public static OutgoingMessageReader readerFor(OutgoingTextMessage message, long threadId) {
    return new OutgoingMessageReader(message, threadId);
  }

  public static class OutgoingMessageReader {

    private final OutgoingTextMessage message;
    private final long                id;
    private final long                threadId;

    public OutgoingMessageReader(OutgoingTextMessage message, long threadId) {
      this.message  = message;
      this.threadId = threadId;
      this.id       = new SecureRandom().nextLong();
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
                                  new LinkedList<>(),
                                  message.getSubscriptionId(),
                                  message.getExpiresIn(),
                                  System.currentTimeMillis(),
                                  0,
                                  false,
                                  Collections.emptyList(),
                                  false,
                                  0);
    }
  }

  public static class Reader implements Closeable {

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

    public SmsMessageRecord getCurrent() {
      long                 messageId            = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
      long                 recipientId          = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.RECIPIENT_ID));
      int                  addressDeviceId      = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS_DEVICE_ID));
      long                 type                 = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.TYPE));
      long                 dateReceived         = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_RECEIVED));
      long                 dateSent             = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.NORMALIZED_DATE_SENT));
      long                 dateServer           = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.DATE_SERVER));
      long                 threadId             = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.THREAD_ID));
      int                  status               = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.STATUS));
      int                  deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.DELIVERY_RECEIPT_COUNT));
      int                  readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.READ_RECEIPT_COUNT));
      String               mismatchDocument     = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.MISMATCHED_IDENTITIES));
      int                  subscriptionId       = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.SUBSCRIPTION_ID));
      long                 expiresIn            = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.EXPIRES_IN));
      long                 expireStarted        = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.EXPIRE_STARTED));
      String               body                 = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));
      boolean              unidentified         = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.UNIDENTIFIED)) == 1;
      boolean              remoteDelete         = cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabase.REMOTE_DELETED)) == 1;
      List<ReactionRecord> reactions            = parseReactions(cursor);
      long                 notifiedTimestamp    = CursorUtil.requireLong(cursor, NOTIFIED_TIMESTAMP);

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount = 0;
      }

      List<IdentityKeyMismatch> mismatches = getMismatches(mismatchDocument);
      Recipient                 recipient  = Recipient.live(RecipientId.from(recipientId)).get();

      return new SmsMessageRecord(messageId, body, recipient,
                                  recipient,
                                  addressDeviceId,
                                  dateSent, dateReceived, dateServer, deliveryReceiptCount, type,
                                  threadId, status, mismatches, subscriptionId,
                                  expiresIn, expireStarted,
                                  readReceiptCount, unidentified, reactions, remoteDelete,
                                  notifiedTimestamp);
    }

    private List<IdentityKeyMismatch> getMismatches(String document) {
      try {
        if (!TextUtils.isEmpty(document)) {
          return JsonUtils.fromJson(document, IdentityKeyMismatchList.class).getList();
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      return new LinkedList<>();
    }

    @Override
    public void close() {
      cursor.close();
    }
  }

}

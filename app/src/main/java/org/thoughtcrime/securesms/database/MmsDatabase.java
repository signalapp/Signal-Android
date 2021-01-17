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
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.android.mms.pdu_alt.NotificationInd;
import com.google.android.mms.pdu_alt.PduHeaders;

import net.sqlcipher.database.SQLiteStatement;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.MmsNotificationAttachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchList;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.documents.NetworkFailureList;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Quote;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.jobs.TrimThreadJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingGroupUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.revealable.ViewOnceExpirationInfo;
import org.thoughtcrime.securesms.revealable.ViewOnceUtil;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.CursorUtil;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.SqlUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.thoughtcrime.securesms.contactshare.Contact.Avatar;

public class MmsDatabase extends MessageDatabase {

  private static final String TAG = MmsDatabase.class.getSimpleName();

  public  static final String TABLE_NAME         = "mms";
          static final String DATE_SENT          = "date";
          static final String DATE_RECEIVED      = "date_received";
  public  static final String MESSAGE_BOX        = "msg_box";
          static final String CONTENT_LOCATION   = "ct_l";
          static final String EXPIRY             = "exp";
  public  static final String MESSAGE_TYPE       = "m_type";
          static final String MESSAGE_SIZE       = "m_size";
          static final String STATUS             = "st";
          static final String TRANSACTION_ID     = "tr_id";
          static final String PART_COUNT         = "part_count";
          static final String NETWORK_FAILURE    = "network_failures";

          static final String QUOTE_ID         = "quote_id";
          static final String QUOTE_AUTHOR     = "quote_author";
          static final String QUOTE_BODY       = "quote_body";
          static final String QUOTE_ATTACHMENT = "quote_attachment";
          static final String QUOTE_MISSING    = "quote_missing";
          static final String QUOTE_MENTIONS   = "quote_mentions";

          static final String SHARED_CONTACTS = "shared_contacts";
          static final String LINK_PREVIEWS   = "previews";
          static final String MENTIONS_SELF   = "mentions_self";

  public  static final String VIEW_ONCE       = "reveal_duration";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                     + " INTEGER PRIMARY KEY, " +
                                                                                  THREAD_ID              + " INTEGER, " +
                                                                                  DATE_SENT              + " INTEGER, " +
                                                                                  DATE_RECEIVED          + " INTEGER, " +
                                                                                  DATE_SERVER            + " INTEGER DEFAULT -1, " +
                                                                                  MESSAGE_BOX            + " INTEGER, " +
                                                                                  READ                   + " INTEGER DEFAULT 0, " +
                                                                                  "m_id"                 + " TEXT, " +
                                                                                  "sub"                  + " TEXT, " +
                                                                                  "sub_cs"               + " INTEGER, " +
                                                                                  BODY                   + " TEXT, " +
                                                                                  PART_COUNT             + " INTEGER, " +
                                                                                  "ct_t"                 + " TEXT, " +
                                                                                  CONTENT_LOCATION       + " TEXT, " +
                                                                                  RECIPIENT_ID           + " INTEGER, " +
                                                                                  ADDRESS_DEVICE_ID      + " INTEGER, " +
                                                                                  EXPIRY                 + " INTEGER, " +
                                                                                  "m_cls"                + " TEXT, " +
                                                                                  MESSAGE_TYPE           + " INTEGER, " +
                                                                                  "v"                    + " INTEGER, " +
                                                                                  MESSAGE_SIZE           + " INTEGER, " +
                                                                                  "pri"                  + " INTEGER, " +
                                                                                  "rr"                   + " INTEGER, " +
                                                                                  "rpt_a"                + " INTEGER, " +
                                                                                  "resp_st"              + " INTEGER, " +
                                                                                  STATUS                 + " INTEGER, " +
                                                                                  TRANSACTION_ID         + " TEXT, " +
                                                                                  "retr_st"              + " INTEGER, " +
                                                                                  "retr_txt"             + " TEXT, " +
                                                                                  "retr_txt_cs"          + " INTEGER, " +
                                                                                  "read_status"          + " INTEGER, " +
                                                                                  "ct_cls"               + " INTEGER, " +
                                                                                  "resp_txt"             + " TEXT, " +
                                                                                  "d_tm"                 + " INTEGER, " +
                                                                                  DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " +
                                                                                  MISMATCHED_IDENTITIES  + " TEXT DEFAULT NULL, " +
                                                                                  NETWORK_FAILURE        + " TEXT DEFAULT NULL," +
                                                                                  "d_rpt"                + " INTEGER, " +
                                                                                  SUBSCRIPTION_ID        + " INTEGER DEFAULT -1, " +
                                                                                  EXPIRES_IN             + " INTEGER DEFAULT 0, " +
                                                                                  EXPIRE_STARTED         + " INTEGER DEFAULT 0, " +
                                                                                  NOTIFIED               + " INTEGER DEFAULT 0, " +
                                                                                  READ_RECEIPT_COUNT     + " INTEGER DEFAULT 0, " +
                                                                                  QUOTE_ID               + " INTEGER DEFAULT 0, " +
                                                                                  QUOTE_AUTHOR           + " TEXT, " +
                                                                                  QUOTE_BODY             + " TEXT, " +
                                                                                  QUOTE_ATTACHMENT       + " INTEGER DEFAULT -1, " +
                                                                                  QUOTE_MISSING          + " INTEGER DEFAULT 0, " +
                                                                                  QUOTE_MENTIONS         + " BLOB DEFAULT NULL," +
                                                                                  SHARED_CONTACTS        + " TEXT, " +
                                                                                  UNIDENTIFIED           + " INTEGER DEFAULT 0, " +
                                                                                  LINK_PREVIEWS          + " TEXT, " +
                                                                                  VIEW_ONCE              + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS              + " BLOB DEFAULT NULL, " +
                                                                                  REACTIONS_UNREAD       + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS_LAST_SEEN    + " INTEGER DEFAULT -1, " +
                                                                                  REMOTE_DELETED         + " INTEGER DEFAULT 0, " +
                                                                                  MENTIONS_SELF          + " INTEGER DEFAULT 0, " +
                                                                                  NOTIFIED_TIMESTAMP     + " INTEGER DEFAULT 0, " +
                                                                                  VIEWED_RECEIPT_COUNT   + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS mms_thread_id_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_read_index ON " + TABLE_NAME + " (" + READ + ");",
    "CREATE INDEX IF NOT EXISTS mms_read_and_notified_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + NOTIFIED + "," + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_message_box_index ON " + TABLE_NAME + " (" + MESSAGE_BOX + ");",
    "CREATE INDEX IF NOT EXISTS mms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ");",
    "CREATE INDEX IF NOT EXISTS mms_date_server_index ON " + TABLE_NAME + " (" + DATE_SERVER + ");",
    "CREATE INDEX IF NOT EXISTS mms_thread_date_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");",
    "CREATE INDEX IF NOT EXISTS mms_reactions_unread_index ON " + TABLE_NAME + " (" + REACTIONS_UNREAD + ");"
  };

  private static final String[] MMS_PROJECTION = new String[] {
      MmsDatabase.TABLE_NAME + "." + ID + " AS " + ID,
      THREAD_ID, DATE_SENT + " AS " + NORMALIZED_DATE_SENT,
      DATE_RECEIVED + " AS " + NORMALIZED_DATE_RECEIVED,
      DATE_SERVER,
      MESSAGE_BOX, READ,
      CONTENT_LOCATION, EXPIRY, MESSAGE_TYPE,
      MESSAGE_SIZE, STATUS, TRANSACTION_ID,
      BODY, PART_COUNT, RECIPIENT_ID, ADDRESS_DEVICE_ID,
      DELIVERY_RECEIPT_COUNT, READ_RECEIPT_COUNT, MISMATCHED_IDENTITIES, NETWORK_FAILURE, SUBSCRIPTION_ID,
      EXPIRES_IN, EXPIRE_STARTED, NOTIFIED, QUOTE_ID, QUOTE_AUTHOR, QUOTE_BODY, QUOTE_ATTACHMENT, QUOTE_MISSING, QUOTE_MENTIONS,
      SHARED_CONTACTS, LINK_PREVIEWS, UNIDENTIFIED, VIEW_ONCE, REACTIONS, REACTIONS_UNREAD, REACTIONS_LAST_SEEN,
      REMOTE_DELETED, MENTIONS_SELF, NOTIFIED_TIMESTAMP, VIEWED_RECEIPT_COUNT,
      "json_group_array(json_object(" +
          "'" + AttachmentDatabase.ROW_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.ROW_ID + ", " +
          "'" + AttachmentDatabase.UNIQUE_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UNIQUE_ID + ", " +
          "'" + AttachmentDatabase.MMS_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + ", " +
          "'" + AttachmentDatabase.SIZE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE + ", " +
          "'" + AttachmentDatabase.FILE_NAME + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FILE_NAME + ", " +
          "'" + AttachmentDatabase.DATA + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DATA + ", " +
          "'" + AttachmentDatabase.CONTENT_TYPE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_TYPE + ", " +
          "'" + AttachmentDatabase.CDN_NUMBER + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CDN_NUMBER + ", " +
          "'" + AttachmentDatabase.CONTENT_LOCATION + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_LOCATION + ", " +
          "'" + AttachmentDatabase.FAST_PREFLIGHT_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.FAST_PREFLIGHT_ID + "," +
          "'" + AttachmentDatabase.VOICE_NOTE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VOICE_NOTE + "," +
          "'" + AttachmentDatabase.BORDERLESS + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.BORDERLESS + "," +
          "'" + AttachmentDatabase.WIDTH + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.WIDTH + "," +
          "'" + AttachmentDatabase.HEIGHT + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.HEIGHT + "," +
          "'" + AttachmentDatabase.QUOTE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.QUOTE + ", " +
          "'" + AttachmentDatabase.CONTENT_DISPOSITION + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CONTENT_DISPOSITION + ", " +
          "'" + AttachmentDatabase.NAME + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.NAME + ", " +
          "'" + AttachmentDatabase.TRANSFER_STATE + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFER_STATE + ", " +
          "'" + AttachmentDatabase.CAPTION + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.CAPTION + ", " +
          "'" + AttachmentDatabase.STICKER_PACK_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_ID+ ", " +
          "'" + AttachmentDatabase.STICKER_PACK_KEY + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_PACK_KEY + ", " +
          "'" + AttachmentDatabase.STICKER_ID + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_ID + ", " +
          "'" + AttachmentDatabase.STICKER_EMOJI + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.STICKER_EMOJI + ", " +
          "'" + AttachmentDatabase.VISUAL_HASH + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VISUAL_HASH + ", " +
          "'" + AttachmentDatabase.TRANSFORM_PROPERTIES + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.TRANSFORM_PROPERTIES + ", " +
          "'" + AttachmentDatabase.DISPLAY_ORDER + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.DISPLAY_ORDER + ", " +
          "'" + AttachmentDatabase.UPLOAD_TIMESTAMP + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UPLOAD_TIMESTAMP +
          ")) AS " + AttachmentDatabase.ATTACHMENT_JSON_ALIAS,
  };

  private static final String RAW_ID_WHERE = TABLE_NAME + "._id = ?";

  private static final String OUTGOING_INSECURE_MESSAGES_CLAUSE = "(" + MESSAGE_BOX + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND NOT (" + MESSAGE_BOX + " & " + Types.SECURE_MESSAGE_BIT + ")";
  private static final String OUTGOING_SECURE_MESSAGES_CLAUSE   = "(" + MESSAGE_BOX + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND (" + MESSAGE_BOX + " & " + (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT) + ")";

  private final EarlyReceiptCache earlyDeliveryReceiptCache = new EarlyReceiptCache("MmsDelivery");

  public MmsDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
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
    return MESSAGE_BOX;
  }

  @Override
  public @Nullable RecipientId getOldestGroupUpdateSender(long threadId, long minimumDateReceived) {
    throw new UnsupportedOperationException();
  }

  @Override
  int getMessageCountForThreadSummary(long threadId) {
    return getMessageCountForThread(threadId);
  }

  @Override
  public Cursor getExpirationStartedMessages() {
    String where = EXPIRE_STARTED + " > 0";
    return rawQuery(where, null);
  }

  @Override
  public SmsMessageRecord getSmsMessage(long messageId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Cursor getMessageCursor(long messageId) {
    Cursor cursor = internalGetMessage(messageId);
    setNotifyConversationListeners(cursor, getThreadIdForMessage(messageId));
    return cursor;
  }

  @Override
  public Cursor getVerboseMessageCursor(long messageId) {
    Cursor cursor = internalGetMessage(messageId);
    setNotifyVerboseConversationListeners(cursor, getThreadIdForMessage(messageId));
    return cursor;
  }

  @Override
  public boolean hasReceivedAnyCallsSince(long threadId, long timestamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsEndSession(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsPreKeyBundle(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsInvalidVersionKeyExchange(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsSecure(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsPush(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsDecryptFailed(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsDecryptDuplicate(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsNoSession(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsUnsupportedProtocolVersion(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsInvalidMessage(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsLegacyVersion(long id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markAsMissedCall(long id, boolean isVideoOffer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markSmsStatus(long id, int status) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Pair<Long, Long> updateBundleMessageBody(long messageId, String body) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull List<MarkedMessageInfo> getViewedIncomingMessages(long threadId) {
    SQLiteDatabase db      = databaseHelper.getReadableDatabase();
    String[]       columns = new String[]{ID, RECIPIENT_ID, DATE_SENT, MESSAGE_BOX, THREAD_ID};
    String         where   = THREAD_ID + " = ? AND " + VIEWED_RECEIPT_COUNT + " > 0 AND " + MESSAGE_BOX + " & " + Types.BASE_INBOX_TYPE + " = " + Types.BASE_INBOX_TYPE;
    String[]       args    = SqlUtil.buildArgs(threadId);


    try (Cursor cursor = db.query(getTableName(), columns, where, args, null, null, null, null)) {
      if (cursor == null) {
        return Collections.emptyList();
      }

      List<MarkedMessageInfo> results = new ArrayList<>(cursor.getCount());
      while (cursor.moveToNext()) {
        RecipientId    recipientId   = RecipientId.from(cursor.getLong(cursor.getColumnIndex(RECIPIENT_ID)));
        long           dateSent      = cursor.getLong(cursor.getColumnIndex(DATE_SENT));
        SyncMessageId  syncMessageId = new SyncMessageId(recipientId, dateSent);

        results.add(new MarkedMessageInfo(threadId, syncMessageId, null));
      }

      return results;
    }
  }

  @Override
  public @Nullable MarkedMessageInfo setIncomingMessageViewed(long messageId) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    String[]       columns  = new String[]{ID, RECIPIENT_ID, DATE_SENT, MESSAGE_BOX, THREAD_ID};
    String         where    = ID_WHERE + " AND " + VIEWED_RECEIPT_COUNT + " = 0";
    String[]       args     = SqlUtil.buildArgs(messageId);

    database.beginTransaction();
    try (Cursor cursor = database.query(TABLE_NAME, columns, where, args, null, null, null)) {
      if (cursor == null || !cursor.moveToFirst()) {
        return null;
      }

      long type = CursorUtil.requireLong(cursor, MESSAGE_BOX);
      if (Types.isSecureType(type) && Types.isInboxType(type)) {
        long           threadId       = cursor.getLong(cursor.getColumnIndex(THREAD_ID));
        RecipientId    recipientId    = RecipientId.from(cursor.getLong(cursor.getColumnIndex(RECIPIENT_ID)));
        long           dateSent       = cursor.getLong(cursor.getColumnIndex(DATE_SENT));
        SyncMessageId  syncMessageId  = new SyncMessageId(recipientId, dateSent);

        MarkedMessageInfo result = new MarkedMessageInfo(threadId, syncMessageId, null);

        ContentValues contentValues = new ContentValues();
        contentValues.put(VIEWED_RECEIPT_COUNT, 1);

        database.update(TABLE_NAME, contentValues, where, args);
        database.setTransactionSuccessful();

        return result;
      } else {
        return null;
      }
    } finally {
      database.endTransaction();
    }
  }

  @Override
  public @NonNull Pair<Long, Long> insertReceivedCall(@NonNull RecipientId address, boolean isVideoOffer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull Pair<Long, Long> insertOutgoingCall(@NonNull RecipientId address, boolean isVideoOffer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull Pair<Long, Long> insertMissedCall(@NonNull RecipientId address, long timestamp, boolean isVideoOffer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insertOrUpdateGroupCall(@NonNull RecipientId groupRecipientId,
                                      @NonNull RecipientId sender,
                                      long timestamp,
                                      @Nullable String peekGroupCallEraId,
                                      @NonNull Collection<UUID> peekJoinedUuids,
                                      boolean isCallFull)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insertOrUpdateGroupCall(@NonNull RecipientId groupRecipientId,
                                      @NonNull RecipientId sender,
                                      long timestamp,
                                      @Nullable String messageGroupCallEraId)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean updatePreviousGroupCall(long threadId, @Nullable String peekGroupCallEraId, @NonNull Collection<UUID> peekJoinedUuids, boolean isCallFull) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<InsertResult> insertMessageInbox(IncomingTextMessage message, long type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<InsertResult> insertMessageInbox(IncomingTextMessage message) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long insertMessageOutbox(long threadId, OutgoingTextMessage message, boolean forceSms, long date, InsertListener insertListener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insertProfileNameChangeMessages(@NonNull Recipient recipient, @NonNull String newProfileName, @NonNull String previousProfileName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insertGroupV1MigrationEvents(@NonNull RecipientId recipientId,
                                           long threadId,
                                           @NonNull GroupMigrationMembershipChange membershipChange)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void endTransaction(SQLiteDatabase database) {
    database.endTransaction();
  }

  @Override
  public SQLiteStatement createInsertStatement(SQLiteDatabase database) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void ensureMigration() {
    databaseHelper.getWritableDatabase();
  }

  @Override
  public boolean isGroupQuitMessage(long messageId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    String[] columns = new String[]{ID};
    String   query   = ID + " = ? AND " + MESSAGE_BOX + " & ?";
    long     type    = Types.getOutgoingEncryptedMessageType() | Types.GROUP_QUIT_BIT;
    String[] args    = new String[]{String.valueOf(messageId), String.valueOf(type)};

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, args, null, null, null, null)) {
      if (cursor.getCount() == 1) {
        return true;
      }
    }

    return false;
  }

  @Override
  public long getLatestGroupQuitTimestamp(long threadId, long quitTimeBarrier) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    String[] columns = new String[]{DATE_SENT};
    String   query   = THREAD_ID + " = ? AND " + MESSAGE_BOX + " & ? AND " + DATE_SENT + " < ?";
    long     type    = Types.getOutgoingEncryptedMessageType() | Types.GROUP_QUIT_BIT;
    String[] args    = new String[]{String.valueOf(threadId), String.valueOf(type), String.valueOf(quitTimeBarrier)};
    String   orderBy = DATE_SENT + " DESC";
    String   limit   = "1";

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, args, null, null, orderBy, limit)) {
      if (cursor.moveToFirst()) {
        return cursor.getLong(cursor.getColumnIndex(DATE_SENT));
      }
    }

    return -1;
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
  public void addFailures(long messageId, List<NetworkFailure> failure) {
    try {
      addToDocument(messageId, NETWORK_FAILURE, failure, NetworkFailureList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public void removeFailure(long messageId, NetworkFailure failure) {
    try {
      removeFromDocument(messageId, NETWORK_FAILURE, failure, NetworkFailureList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public boolean incrementReceiptCount(SyncMessageId messageId, long timestamp, @NonNull ReceiptType receiptType) {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    boolean        found    = false;

    try (Cursor cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, MESSAGE_BOX, RECIPIENT_ID, receiptType.getColumnName()},
                                        DATE_SENT + " = ?", new String[] {String.valueOf(messageId.getTimetamp())},
                                        null, null, null, null)) {
      while (cursor.moveToNext()) {
        if (Types.isOutgoingMessageType(cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX)))) {
          RecipientId theirRecipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)));
          RecipientId ourRecipientId   = messageId.getRecipientId();
          String      columnName       = receiptType.getColumnName();

          if (ourRecipientId.equals(theirRecipientId) || Recipient.resolved(theirRecipientId).isGroup()) {
            long    id               = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
            long    threadId         = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
            int     status           = receiptType.getGroupStatus();
            boolean isFirstIncrement = cursor.getLong(cursor.getColumnIndexOrThrow(columnName)) == 0;

            found = true;

            database.execSQL("UPDATE " + TABLE_NAME + " SET " +
                             columnName + " = " + columnName + " + 1 WHERE " + ID + " = ?",
                             new String[] {String.valueOf(id)});

            DatabaseFactory.getGroupReceiptDatabase(context).update(ourRecipientId, id, status, timestamp);
            DatabaseFactory.getThreadDatabase(context).update(threadId, false);

            if (isFirstIncrement) {
              notifyConversationListeners(threadId);
            } else {
              notifyVerboseConversationListeners(threadId);
            }
          }
        }
      }

      if (!found && receiptType == ReceiptType.DELIVERY) {
        earlyDeliveryReceiptCache.increment(messageId.getTimetamp(), messageId.getRecipientId());
        return true;
      }

      return found;
    }
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

  private long getThreadIdFor(@NonNull IncomingMediaMessage retrieved) {
    if (retrieved.getGroupId() != null) {
      RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromPossiblyMigratedGroupId(retrieved.getGroupId());
      Recipient   groupRecipients  = Recipient.resolved(groupRecipientId);
      return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipients);
    } else {
      Recipient sender = Recipient.resolved(retrieved.getFrom());
      return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(sender);
    }
  }

  private long getThreadIdFor(@NonNull NotificationInd notification) {
    String fromString = notification.getFrom() != null && notification.getFrom().getTextString() != null
                      ? Util.toIsoString(notification.getFrom().getTextString())
                      : "";
    Recipient recipient = Recipient.external(context, fromString);
    return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
  }

  private Cursor rawQuery(@NonNull String where, @Nullable String[] arguments) {
    return rawQuery(where, arguments, false, 0);
  }

  private Cursor rawQuery(@NonNull String where, @Nullable String[] arguments, boolean reverse, long limit) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    String rawQueryString   = "SELECT " + Util.join(MMS_PROJECTION, ",") +
                              " FROM " + MmsDatabase.TABLE_NAME +  " LEFT OUTER JOIN " + AttachmentDatabase.TABLE_NAME +
                              " ON (" + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " = " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + ")" +
                              " WHERE " + where + " GROUP BY " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID;

    if (reverse) {
      rawQueryString += " ORDER BY " + MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " DESC";
    }

    if (limit > 0) {
      rawQueryString += " LIMIT " + limit;
    }

    return database.rawQuery(rawQueryString, arguments);
  }

  private Cursor internalGetMessage(long messageId) {
    return rawQuery(RAW_ID_WHERE, new String[] {messageId + ""});
  }

  @Override
  public MessageRecord getMessageRecord(long messageId) throws NoSuchMessageException {
    try (Cursor cursor = rawQuery(RAW_ID_WHERE, new String[] {messageId + ""})) {
      MessageRecord record = new Reader(cursor).getNext();

      if (record == null) {
        throw new NoSuchMessageException("No message for ID: " + messageId);
      }

      return record;
    }
  }

  @Override
  public Reader getMessages(Collection<Long> messageIds) {
    String ids = TextUtils.join(",", messageIds);
    return readerFor(rawQuery(MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " IN (" + ids + ")", null));
  }

  private void updateMailboxBitmask(long id, long maskOff, long maskOn, Optional<Long> threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME +
                   " SET " + MESSAGE_BOX + " = (" + MESSAGE_BOX + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
                   " WHERE " + ID + " = ?", new String[] {id + ""});

    if (threadId.isPresent()) {
      DatabaseFactory.getThreadDatabase(context).update(threadId.get(), false);
    }
  }

  @Override
  public void markAsOutbox(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_OUTBOX_TYPE, Optional.of(threadId));
  }

  @Override
  public void markAsForcedSms(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.PUSH_MESSAGE_BIT, Types.MESSAGE_FORCE_SMS_BIT, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  @Override
  public void markAsPendingInsecureSmsFallback(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_PENDING_INSECURE_SMS_FALLBACK, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  @Override
  public void markAsSending(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENDING_TYPE, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  @Override
  public void markAsSentFailed(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENT_FAILED_TYPE, Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  @Override
  public void markAsSent(long messageId, boolean secure) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENT_TYPE | (secure ? Types.PUSH_MESSAGE_BIT | Types.SECURE_MESSAGE_BIT : 0), Optional.of(threadId));
    notifyConversationListeners(threadId);
  }

  @Override
  public void markAsRemoteDelete(long messageId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    ContentValues values = new ContentValues();
    values.put(REMOTE_DELETED, 1);
    values.putNull(BODY);
    values.putNull(QUOTE_BODY);
    values.putNull(QUOTE_AUTHOR);
    values.putNull(QUOTE_ATTACHMENT);
    values.putNull(QUOTE_ID);
    values.putNull(LINK_PREVIEWS);
    values.putNull(SHARED_CONTACTS);
    values.putNull(REACTIONS);
    db.update(TABLE_NAME, values, ID_WHERE, new String[] { String.valueOf(messageId) });

    DatabaseFactory.getAttachmentDatabase(context).deleteAttachmentsForMessage(messageId);
    DatabaseFactory.getMentionDatabase(context).deleteMentionsForMessage(messageId);

    long threadId = getThreadIdForMessage(messageId);
    DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
  }

  @Override
  public void markDownloadState(long messageId, long state) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(STATUS, state);

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId + ""});
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }

  @Override
  public void markAsInsecure(long messageId) {
    updateMailboxBitmask(messageId, Types.SECURE_MESSAGE_BIT, 0, Optional.<Long>absent());
  }

  @Override
  public void markUnidentified(long messageId, boolean unidentified) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(UNIDENTIFIED, unidentified ? 1 : 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(messageId)});
  }

  @Override
  public void markExpireStarted(long id) {
    markExpireStarted(id, System.currentTimeMillis());
  }

  @Override
  public void markExpireStarted(long id, long startedTimestamp) {
    markExpireStarted(Collections.singleton(id), startedTimestamp);
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
  public void markAsNotified(long id) {
    SQLiteDatabase database      = databaseHelper.getWritableDatabase();
    ContentValues  contentValues = new ContentValues();

    contentValues.put(NOTIFIED, 1);

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(id)});
  }

  @Override
  public List<MarkedMessageInfo> setMessagesReadSince(long threadId, long sinceTimestamp) {
    if (sinceTimestamp == -1) {
      return setMessagesRead(THREAD_ID + " = ? AND " + READ + " = 0", new String[] {String.valueOf(threadId)});
    } else {
      return setMessagesRead(THREAD_ID + " = ? AND " + READ + " = 0 AND " + DATE_RECEIVED + " <= ?", new String[]{String.valueOf(threadId), String.valueOf(sinceTimestamp)});
    }
  }

  @Override
  public List<MarkedMessageInfo> setEntireThreadRead(long threadId) {
    return setMessagesRead(THREAD_ID + " = ?", new String[] {String.valueOf(threadId)});
  }

  @Override
  public List<MarkedMessageInfo> setAllMessagesRead() {
    return setMessagesRead(READ + " = 0", null);
  }

  private List<MarkedMessageInfo> setMessagesRead(String where, String[] arguments) {
    SQLiteDatabase          database = databaseHelper.getWritableDatabase();
    List<MarkedMessageInfo> result   = new LinkedList<>();
    Cursor                  cursor   = null;

    database.beginTransaction();

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, RECIPIENT_ID, DATE_SENT, MESSAGE_BOX, EXPIRES_IN, EXPIRE_STARTED, THREAD_ID}, where, arguments, null, null, null);

      while(cursor != null && cursor.moveToNext()) {
        if (Types.isSecureType(cursor.getLong(cursor.getColumnIndex(MESSAGE_BOX)))) {
          long           threadId       = cursor.getLong(cursor.getColumnIndex(THREAD_ID));
          RecipientId    recipientId    = RecipientId.from(cursor.getLong(cursor.getColumnIndex(RECIPIENT_ID)));
          long           dateSent       = cursor.getLong(cursor.getColumnIndex(DATE_SENT));
          long           messageId      = cursor.getLong(cursor.getColumnIndex(ID));
          long           expiresIn      = cursor.getLong(cursor.getColumnIndex(EXPIRES_IN));
          long           expireStarted  = cursor.getLong(cursor.getColumnIndex(EXPIRE_STARTED));
          SyncMessageId  syncMessageId  = new SyncMessageId(recipientId, dateSent);
          ExpirationInfo expirationInfo = new ExpirationInfo(messageId, expiresIn, expireStarted, true);

          result.add(new MarkedMessageInfo(threadId, syncMessageId, expirationInfo));
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

    return result;
  }

  @Override
  public List<Pair<Long, Long>> setTimestampRead(SyncMessageId messageId, long proposedExpireStarted) {
    SQLiteDatabase         database        = databaseHelper.getWritableDatabase();
    List<Pair<Long, Long>> expiring        = new LinkedList<>();
    Cursor                 cursor          = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, MESSAGE_BOX, EXPIRES_IN, EXPIRE_STARTED, RECIPIENT_ID}, DATE_SENT + " = ?", new String[] {String.valueOf(messageId.getTimetamp())}, null, null, null, null);

      while (cursor.moveToNext()) {
        RecipientId theirRecipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)));
        RecipientId ourRecipientId   = messageId.getRecipientId();

        if (ourRecipientId.equals(theirRecipientId) || Recipient.resolved(theirRecipientId).isGroup()) {
          long id            = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
          long threadId      = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
          long expiresIn     = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN));
          long expireStarted = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRE_STARTED));

          expireStarted = expireStarted > 0 ? Math.min(proposedExpireStarted, expireStarted) : proposedExpireStarted;

          ContentValues values = new ContentValues();
          values.put(READ, 1);

          if (expiresIn > 0) {
            values.put(EXPIRE_STARTED, expireStarted);
            expiring.add(new Pair<>(id, expiresIn));
          }

          database.update(TABLE_NAME, values, ID_WHERE, new String[]{String.valueOf(id)});

          DatabaseFactory.getThreadDatabase(context).updateReadState(threadId);
          DatabaseFactory.getThreadDatabase(context).setLastSeen(threadId);
          notifyConversationListeners(threadId);
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return expiring;
  }

  @Override
  public @Nullable Pair<RecipientId, Long> getOldestUnreadMentionDetails(long threadId) {
    SQLiteDatabase database   = databaseHelper.getReadableDatabase();
    String[]       projection = new String[]{RECIPIENT_ID,DATE_RECEIVED};
    String         selection  = THREAD_ID + " = ? AND " + READ + " = 0 AND " + MENTIONS_SELF + " = 1";
    String[]       args       = SqlUtil.buildArgs(threadId);

    try (Cursor cursor = database.query(TABLE_NAME, projection, selection, args, null, null, DATE_RECEIVED + " ASC", "1")) {
      if (cursor != null && cursor.moveToFirst()) {
        return new Pair<>(RecipientId.from(CursorUtil.requireString(cursor, RECIPIENT_ID)), CursorUtil.requireLong(cursor, DATE_RECEIVED));
      }
    }

    return null;
  }

  @Override
  public int getUnreadMentionCount(long threadId) {
    SQLiteDatabase database   = databaseHelper.getReadableDatabase();
    String[]       projection = new String[]{"COUNT(*)"};
    String         selection  = THREAD_ID + " = ? AND " + READ + " = 0 AND " + MENTIONS_SELF + " = 1";
    String[]       args       = SqlUtil.buildArgs(threadId);

    try (Cursor cursor = database.query(TABLE_NAME, projection, selection, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  /**
   * Trims data related to expired messages. Only intended to be run after a backup restore.
   */
  void trimEntriesForExpiredMessages() {
    SQLiteDatabase database         = databaseHelper.getWritableDatabase();
    String         trimmedCondition = " NOT IN (SELECT " + MmsDatabase.ID + " FROM " + MmsDatabase.TABLE_NAME + ")";

    database.delete(GroupReceiptDatabase.TABLE_NAME, GroupReceiptDatabase.MMS_ID + trimmedCondition, null);

    String[] columns = new String[] { AttachmentDatabase.ROW_ID, AttachmentDatabase.UNIQUE_ID };
    String   where   = AttachmentDatabase.MMS_ID + trimmedCondition;

    try (Cursor cursor = database.query(AttachmentDatabase.TABLE_NAME, columns, where, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        DatabaseFactory.getAttachmentDatabase(context).deleteAttachment(new AttachmentId(cursor.getLong(0), cursor.getLong(1)));
      }
    }

    DatabaseFactory.getMentionDatabase(context).deleteAbandonedMentions();

    try (Cursor cursor = database.query(ThreadDatabase.TABLE_NAME, new String[] { ThreadDatabase.ID }, ThreadDatabase.EXPIRES_IN + " > 0", null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        DatabaseFactory.getThreadDatabase(context).update(cursor.getLong(0), false);
      }
    }
  }

  @Override
  public Optional<MmsNotificationInfo> getNotification(long messageId) {
    Cursor cursor = null;

    try {
      cursor = rawQuery(RAW_ID_WHERE, new String[] {String.valueOf(messageId)});

      if (cursor != null && cursor.moveToNext()) {
        return Optional.of(new MmsNotificationInfo(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID))),
                                                   cursor.getString(cursor.getColumnIndexOrThrow(CONTENT_LOCATION)),
                                                   cursor.getString(cursor.getColumnIndexOrThrow(TRANSACTION_ID)),
                                                   cursor.getInt(cursor.getColumnIndexOrThrow(SUBSCRIPTION_ID))));
      } else {
        return Optional.absent();
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  @Override
  public OutgoingMediaMessage getOutgoingMessage(long messageId)
      throws MmsException, NoSuchMessageException
  {
    AttachmentDatabase attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
    MentionDatabase    mentionDatabase    = DatabaseFactory.getMentionDatabase(context);
    Cursor             cursor             = null;

    try {
      cursor = rawQuery(RAW_ID_WHERE, new String[] {String.valueOf(messageId)});

      if (cursor != null && cursor.moveToNext()) {
        List<DatabaseAttachment> associatedAttachments = attachmentDatabase.getAttachmentsForMessage(messageId);
        List<Mention>            mentions              = mentionDatabase.getMentionsForMessage(messageId);

        long             outboxType         = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX));
        String           body               = cursor.getString(cursor.getColumnIndexOrThrow(BODY));
        long             timestamp          = cursor.getLong(cursor.getColumnIndexOrThrow(NORMALIZED_DATE_SENT));
        int              subscriptionId     = cursor.getInt(cursor.getColumnIndexOrThrow(SUBSCRIPTION_ID));
        long             expiresIn          = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN));
        boolean          viewOnce           = cursor.getLong(cursor.getColumnIndexOrThrow(VIEW_ONCE)) == 1;
        long             recipientId        = cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID));
        long             threadId           = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
        int              distributionType   = DatabaseFactory.getThreadDatabase(context).getDistributionType(threadId);
        String           mismatchDocument   = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.MISMATCHED_IDENTITIES));
        String           networkDocument    = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.NETWORK_FAILURE));

        long              quoteId            = cursor.getLong(cursor.getColumnIndexOrThrow(QUOTE_ID));
        long              quoteAuthor        = cursor.getLong(cursor.getColumnIndexOrThrow(QUOTE_AUTHOR));
        String            quoteText          = cursor.getString(cursor.getColumnIndexOrThrow(QUOTE_BODY));
        boolean           quoteMissing       = cursor.getInt(cursor.getColumnIndexOrThrow(QUOTE_MISSING)) == 1;
        List<Attachment>  quoteAttachments   = Stream.of(associatedAttachments).filter(Attachment::isQuote).map(a -> (Attachment)a).toList();
        List<Mention>     quoteMentions      = parseQuoteMentions(context, cursor);
        List<Contact>     contacts           = getSharedContacts(cursor, associatedAttachments);
        Set<Attachment>   contactAttachments = new HashSet<>(Stream.of(contacts).map(Contact::getAvatarAttachment).filter(a -> a != null).toList());
        List<LinkPreview> previews           = getLinkPreviews(cursor, associatedAttachments);
        Set<Attachment>   previewAttachments = Stream.of(previews).filter(lp -> lp.getThumbnail().isPresent()).map(lp -> lp.getThumbnail().get()).collect(Collectors.toSet());
        List<Attachment>  attachments        = Stream.of(associatedAttachments).filterNot(Attachment::isQuote)
                                                                               .filterNot(contactAttachments::contains)
                                                                               .filterNot(previewAttachments::contains)
                                                                               .sorted(new DatabaseAttachment.DisplayOrderComparator())
                                                                               .map(a -> (Attachment)a).toList();

        Recipient                 recipient       = Recipient.resolved(RecipientId.from(recipientId));
        List<NetworkFailure>      networkFailures = new LinkedList<>();
        List<IdentityKeyMismatch> mismatches      = new LinkedList<>();
        QuoteModel                quote           = null;

        if (quoteId > 0 && quoteAuthor > 0 && (!TextUtils.isEmpty(quoteText) || !quoteAttachments.isEmpty())) {
          quote = new QuoteModel(quoteId, RecipientId.from(quoteAuthor), quoteText, quoteMissing, quoteAttachments, quoteMentions);
        }

        if (!TextUtils.isEmpty(mismatchDocument)) {
          try {
            mismatches = JsonUtils.fromJson(mismatchDocument, IdentityKeyMismatchList.class).getList();
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        if (!TextUtils.isEmpty(networkDocument)) {
          try {
            networkFailures = JsonUtils.fromJson(networkDocument, NetworkFailureList.class).getList();
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        if (body != null && (Types.isGroupQuit(outboxType) || Types.isGroupUpdate(outboxType))) {
          return new OutgoingGroupUpdateMessage(recipient, new MessageGroupContext(body, Types.isGroupV2(outboxType)), attachments, timestamp, 0, false, quote, contacts, previews, mentions);
        } else if (Types.isExpirationTimerUpdate(outboxType)) {
          return new OutgoingExpirationUpdateMessage(recipient, timestamp, expiresIn);
        }

        OutgoingMediaMessage message = new OutgoingMediaMessage(recipient, body, attachments, timestamp, subscriptionId, expiresIn, viewOnce, distributionType, quote, contacts, previews, mentions, networkFailures, mismatches);

        if (Types.isSecureType(outboxType)) {
          return new OutgoingSecureMediaMessage(message);
        }

        return message;
      }

      throw new NoSuchMessageException("No record found for id: " + messageId);
    } catch (IOException e) {
      throw new MmsException(e);
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private static List<Contact> getSharedContacts(@NonNull Cursor cursor, @NonNull List<DatabaseAttachment> attachments) {
    String serializedContacts = cursor.getString(cursor.getColumnIndexOrThrow(SHARED_CONTACTS));

    if (TextUtils.isEmpty(serializedContacts)) {
      return Collections.emptyList();
    }

    Map<AttachmentId, DatabaseAttachment> attachmentIdMap = new HashMap<>();
    for (DatabaseAttachment attachment : attachments) {
      attachmentIdMap.put(attachment.getAttachmentId(), attachment);
    }

    try {
      List<Contact> contacts     = new LinkedList<>();
      JSONArray     jsonContacts = new JSONArray(serializedContacts);

      for (int i = 0; i < jsonContacts.length(); i++) {
        Contact contact = Contact.deserialize(jsonContacts.getJSONObject(i).toString());

        if (contact.getAvatar() != null && contact.getAvatar().getAttachmentId() != null) {
          DatabaseAttachment attachment    = attachmentIdMap.get(contact.getAvatar().getAttachmentId());
          Avatar             updatedAvatar = new Avatar(contact.getAvatar().getAttachmentId(),
                                                        attachment,
                                                        contact.getAvatar().isProfile());
          contacts.add(new Contact(contact, updatedAvatar));
        } else {
          contacts.add(contact);
        }
      }

      return contacts;
    } catch (JSONException | IOException e) {
      Log.w(TAG, "Failed to parse shared contacts.", e);
    }

    return Collections.emptyList();
  }

  private static List<LinkPreview> getLinkPreviews(@NonNull Cursor cursor, @NonNull List<DatabaseAttachment> attachments) {
    String serializedPreviews = cursor.getString(cursor.getColumnIndexOrThrow(LINK_PREVIEWS));

    if (TextUtils.isEmpty(serializedPreviews)) {
      return Collections.emptyList();
    }

    Map<AttachmentId, DatabaseAttachment> attachmentIdMap = new HashMap<>();
    for (DatabaseAttachment attachment : attachments) {
      attachmentIdMap.put(attachment.getAttachmentId(), attachment);
    }

    try {
      List<LinkPreview> previews     = new LinkedList<>();
      JSONArray         jsonPreviews = new JSONArray(serializedPreviews);

      for (int i = 0; i < jsonPreviews.length(); i++) {
        LinkPreview preview = LinkPreview.deserialize(jsonPreviews.getJSONObject(i).toString());

        if (preview.getAttachmentId() != null) {
          DatabaseAttachment attachment = attachmentIdMap.get(preview.getAttachmentId());
          if (attachment != null) {
            previews.add(new LinkPreview(preview.getUrl(), preview.getTitle(), preview.getDescription(), preview.getDate(), attachment));
          }
        } else {
          previews.add(preview);
        }
      }

      return previews;
    } catch (JSONException | IOException e) {
      Log.w(TAG, "Failed to parse shared contacts.", e);
    }

    return Collections.emptyList();
  }

  private Optional<InsertResult> insertMessageInbox(IncomingMediaMessage retrieved,
                                                    String contentLocation,
                                                    long threadId, long mailbox)
      throws MmsException
  {
    if (threadId == -1 || retrieved.isGroupMessage()) {
      threadId = getThreadIdFor(retrieved);
    }

    ContentValues contentValues = new ContentValues();

    contentValues.put(DATE_SENT, retrieved.getSentTimeMillis());
    contentValues.put(DATE_SERVER, retrieved.getServerTimeMillis());
    contentValues.put(RECIPIENT_ID, retrieved.getFrom().serialize());

    contentValues.put(MESSAGE_BOX, mailbox);
    contentValues.put(MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(CONTENT_LOCATION, contentLocation);
    contentValues.put(STATUS, Status.DOWNLOAD_INITIALIZED);
    contentValues.put(DATE_RECEIVED, retrieved.isPushMessage() ? System.currentTimeMillis() : generatePduCompatTimestamp());
    contentValues.put(PART_COUNT, retrieved.getAttachments().size());
    contentValues.put(SUBSCRIPTION_ID, retrieved.getSubscriptionId());
    contentValues.put(EXPIRES_IN, retrieved.getExpiresIn());
    contentValues.put(VIEW_ONCE, retrieved.isViewOnce() ? 1 : 0);
    contentValues.put(READ, retrieved.isExpirationUpdate() ? 1 : 0);
    contentValues.put(UNIDENTIFIED, retrieved.isUnidentified());

    if (!contentValues.containsKey(DATE_SENT)) {
      contentValues.put(DATE_SENT, contentValues.getAsLong(DATE_RECEIVED));
    }

    List<Attachment> quoteAttachments = new LinkedList<>();

    if (retrieved.getQuote() != null) {
      contentValues.put(QUOTE_ID, retrieved.getQuote().getId());
      contentValues.put(QUOTE_BODY, retrieved.getQuote().getText().toString());
      contentValues.put(QUOTE_AUTHOR, retrieved.getQuote().getAuthor().serialize());
      contentValues.put(QUOTE_MISSING, retrieved.getQuote().isOriginalMissing() ? 1 : 0);

      BodyRangeList mentionsList = MentionUtil.mentionsToBodyRangeList(retrieved.getQuote().getMentions());
      if (mentionsList != null) {
        contentValues.put(QUOTE_MENTIONS, mentionsList.toByteArray());
      }

      quoteAttachments = retrieved.getQuote().getAttachments();
    }

    if (retrieved.isPushMessage() && isDuplicate(retrieved, threadId)) {
      Log.w(TAG, "Ignoring duplicate media message (" + retrieved.getSentTimeMillis() + ")");
      return Optional.absent();
    }

    long messageId = insertMediaMessage(threadId, retrieved.getBody(), retrieved.getAttachments(), quoteAttachments, retrieved.getSharedContacts(), retrieved.getLinkPreviews(), retrieved.getMentions(), contentValues, null);

    if (!Types.isExpirationTimerUpdate(mailbox)) {
      DatabaseFactory.getThreadDatabase(context).incrementUnread(threadId, 1);
      DatabaseFactory.getThreadDatabase(context).update(threadId, true);
    }

    notifyConversationListeners(threadId);

    return Optional.of(new InsertResult(messageId, threadId));
  }

  @Override
  public Optional<InsertResult> insertMessageInbox(IncomingMediaMessage retrieved,
                                                   String contentLocation, long threadId)
      throws MmsException
  {
    long type = Types.BASE_INBOX_TYPE;

    if (retrieved.isPushMessage()) {
      type |= Types.PUSH_MESSAGE_BIT;
    }

    if (retrieved.isExpirationUpdate()) {
      type |= Types.EXPIRATION_TIMER_UPDATE_BIT;
    }

    return insertMessageInbox(retrieved, contentLocation, threadId, type);
  }

  @Override
  public Optional<InsertResult> insertSecureDecryptedMessageInbox(IncomingMediaMessage retrieved, long threadId)
      throws MmsException
  {
    long type = Types.BASE_INBOX_TYPE | Types.SECURE_MESSAGE_BIT;

    if (retrieved.isPushMessage()) {
      type |= Types.PUSH_MESSAGE_BIT;
    }

    if (retrieved.isExpirationUpdate()) {
      type |= Types.EXPIRATION_TIMER_UPDATE_BIT;
    }

    return insertMessageInbox(retrieved, "", threadId, type);
  }

  public Pair<Long, Long> insertMessageInbox(@NonNull NotificationInd notification, int subscriptionId) {
    SQLiteDatabase       db             = databaseHelper.getWritableDatabase();
    long                 threadId       = getThreadIdFor(notification);
    ContentValues        contentValues  = new ContentValues();
    ContentValuesBuilder contentBuilder = new ContentValuesBuilder(contentValues);

    Log.i(TAG, "Message received type: " + notification.getMessageType());

    contentBuilder.add(CONTENT_LOCATION, notification.getContentLocation());
    contentBuilder.add(DATE_SENT, System.currentTimeMillis());
    contentBuilder.add(EXPIRY, notification.getExpiry());
    contentBuilder.add(MESSAGE_SIZE, notification.getMessageSize());
    contentBuilder.add(TRANSACTION_ID, notification.getTransactionId());
    contentBuilder.add(MESSAGE_TYPE, notification.getMessageType());

    if (notification.getFrom() != null) {
      Recipient recipient = Recipient.external(context, Util.toIsoString(notification.getFrom().getTextString()));
      contentValues.put(RECIPIENT_ID, recipient.getId().serialize());
    } else {
      contentValues.put(RECIPIENT_ID, RecipientId.UNKNOWN.serialize());
    }

    contentValues.put(MESSAGE_BOX, Types.BASE_INBOX_TYPE);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(STATUS, Status.DOWNLOAD_INITIALIZED);
    contentValues.put(DATE_RECEIVED, generatePduCompatTimestamp());
    contentValues.put(READ, Util.isDefaultSmsProvider(context) ? 0 : 1);
    contentValues.put(SUBSCRIPTION_ID, subscriptionId);

    if (!contentValues.containsKey(DATE_SENT))
      contentValues.put(DATE_SENT, contentValues.getAsLong(DATE_RECEIVED));

    long messageId = db.insert(TABLE_NAME, null, contentValues);

    return new Pair<>(messageId, threadId);
  }

  @Override
  public @NonNull InsertResult insertDecryptionFailedMessage(@NonNull RecipientId recipientId, long senderDeviceId, long sentTimestamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markIncomingNotificationReceived(long threadId) {
    notifyConversationListeners(threadId);
    DatabaseFactory.getThreadDatabase(context).update(threadId, true);

    if (org.thoughtcrime.securesms.util.Util.isDefaultSmsProvider(context)) {
      DatabaseFactory.getThreadDatabase(context).incrementUnread(threadId, 1);
    }

    ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId));
  }

  @Override
  public long insertMessageOutbox(@NonNull OutgoingMediaMessage message,
                                  long threadId,
                                  boolean forceSms,
                                  @Nullable SmsDatabase.InsertListener insertListener)
      throws MmsException
  {
    return insertMessageOutbox(message, threadId, forceSms, GroupReceiptDatabase.STATUS_UNDELIVERED, insertListener);
  }

  @Override
  public long insertMessageOutbox(@NonNull OutgoingMediaMessage message,
                                  long threadId, boolean forceSms, int defaultReceiptStatus,
                                  @Nullable SmsDatabase.InsertListener insertListener)
      throws MmsException
  {
    long type = Types.BASE_SENDING_TYPE;

    if (message.isSecure()) type |= (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT);
    if (forceSms)           type |= Types.MESSAGE_FORCE_SMS_BIT;

    if (message.isGroup()) {
      OutgoingGroupUpdateMessage outgoingGroupUpdateMessage = (OutgoingGroupUpdateMessage) message;
      if (outgoingGroupUpdateMessage.isV2Group()) {
        type |= Types.GROUP_V2_BIT | Types.GROUP_UPDATE_BIT;
      } else {
        MessageGroupContext.GroupV1Properties properties = outgoingGroupUpdateMessage.requireGroupV1Properties();
        if      (properties.isUpdate()) type |= Types.GROUP_UPDATE_BIT;
        else if (properties.isQuit())   type |= Types.GROUP_QUIT_BIT;
      }
    }

    if (message.isExpirationUpdate()) {
      type |= Types.EXPIRATION_TIMER_UPDATE_BIT;
    }

    Map<RecipientId, Long> earlyDeliveryReceipts = earlyDeliveryReceiptCache.remove(message.getSentTimeMillis());

    ContentValues contentValues = new ContentValues();
    contentValues.put(DATE_SENT, message.getSentTimeMillis());
    contentValues.put(MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ);

    contentValues.put(MESSAGE_BOX, type);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(READ, 1);
    contentValues.put(DATE_RECEIVED, System.currentTimeMillis());
    contentValues.put(SUBSCRIPTION_ID, message.getSubscriptionId());
    contentValues.put(EXPIRES_IN, message.getExpiresIn());
    contentValues.put(VIEW_ONCE, message.isViewOnce());
    contentValues.put(RECIPIENT_ID, message.getRecipient().getId().serialize());
    contentValues.put(DELIVERY_RECEIPT_COUNT, Stream.of(earlyDeliveryReceipts.values()).mapToLong(Long::longValue).sum());

    List<Attachment> quoteAttachments = new LinkedList<>();

    if (message.getOutgoingQuote() != null) {
      MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyAndMentionsWithPlaceholders(message.getOutgoingQuote().getText(), message.getOutgoingQuote().getMentions());

      contentValues.put(QUOTE_ID, message.getOutgoingQuote().getId());
      contentValues.put(QUOTE_AUTHOR, message.getOutgoingQuote().getAuthor().serialize());
      contentValues.put(QUOTE_BODY, updated.getBodyAsString());
      contentValues.put(QUOTE_MISSING, message.getOutgoingQuote().isOriginalMissing() ? 1 : 0);

      BodyRangeList mentionsList = MentionUtil.mentionsToBodyRangeList(updated.getMentions());
      if (mentionsList != null) {
        contentValues.put(QUOTE_MENTIONS, mentionsList.toByteArray());
      }

      quoteAttachments.addAll(message.getOutgoingQuote().getAttachments());
    }

    MentionUtil.UpdatedBodyAndMentions updatedBodyAndMentions = MentionUtil.updateBodyAndMentionsWithPlaceholders(message.getBody(), message.getMentions());
    long messageId = insertMediaMessage(threadId, updatedBodyAndMentions.getBodyAsString(), message.getAttachments(), quoteAttachments, message.getSharedContacts(), message.getLinkPreviews(), updatedBodyAndMentions.getMentions(), contentValues, insertListener);

    if (message.getRecipient().isGroup()) {
      OutgoingGroupUpdateMessage outgoingGroupUpdateMessage = (message instanceof OutgoingGroupUpdateMessage) ? (OutgoingGroupUpdateMessage) message : null;

      GroupReceiptDatabase receiptDatabase = DatabaseFactory.getGroupReceiptDatabase(context);
      Set<RecipientId>     members         = new HashSet<>();

      if (outgoingGroupUpdateMessage != null && outgoingGroupUpdateMessage.isV2Group()) {
        MessageGroupContext.GroupV2Properties groupV2Properties = outgoingGroupUpdateMessage.requireGroupV2Properties();
        members.addAll(Stream.of(groupV2Properties.getAllActivePendingAndRemovedMembers())
                             .distinct()
                             .map(uuid -> RecipientId.from(uuid, null))
                             .toList());
        members.remove(Recipient.self().getId());
      } else {
        members.addAll(Stream.of(DatabaseFactory.getGroupDatabase(context).getGroupMembers(message.getRecipient().requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF)).map(Recipient::getId).toList());
      }

      receiptDatabase.insert(members, messageId, defaultReceiptStatus, message.getSentTimeMillis());

      for (RecipientId recipientId : earlyDeliveryReceipts.keySet()) receiptDatabase.update(recipientId, messageId, GroupReceiptDatabase.STATUS_DELIVERED, -1);
    }

    DatabaseFactory.getThreadDatabase(context).setLastSeen(threadId);
    DatabaseFactory.getThreadDatabase(context).setHasSent(threadId, true);
    ApplicationDependencies.getJobManager().add(new TrimThreadJob(threadId));

    return messageId;
  }

  private long insertMediaMessage(long threadId,
                                  @Nullable String body,
                                  @NonNull List<Attachment> attachments,
                                  @NonNull List<Attachment> quoteAttachments,
                                  @NonNull List<Contact> sharedContacts,
                                  @NonNull List<LinkPreview> linkPreviews,
                                  @NonNull List<Mention> mentions,
                                  @NonNull ContentValues contentValues,
                                  @Nullable SmsDatabase.InsertListener insertListener)
      throws MmsException
  {
    SQLiteDatabase     db              = databaseHelper.getWritableDatabase();
    AttachmentDatabase partsDatabase   = DatabaseFactory.getAttachmentDatabase(context);
    MentionDatabase    mentionDatabase = DatabaseFactory.getMentionDatabase(context);

    boolean mentionsSelf = Stream.of(mentions).filter(m -> Recipient.resolved(m.getRecipientId()).isSelf()).findFirst().isPresent();

    List<Attachment> allAttachments     = new LinkedList<>();
    List<Attachment> contactAttachments = Stream.of(sharedContacts).map(Contact::getAvatarAttachment).filter(a -> a != null).toList();
    List<Attachment> previewAttachments = Stream.of(linkPreviews).filter(lp -> lp.getThumbnail().isPresent()).map(lp -> lp.getThumbnail().get()).toList();

    allAttachments.addAll(attachments);
    allAttachments.addAll(contactAttachments);
    allAttachments.addAll(previewAttachments);

    contentValues.put(BODY, body);
    contentValues.put(PART_COUNT, allAttachments.size());
    contentValues.put(MENTIONS_SELF, mentionsSelf ? 1 : 0);

    db.beginTransaction();
    try {
      long messageId = db.insert(TABLE_NAME, null, contentValues);

      mentionDatabase.insert(threadId, messageId, mentions);

      Map<Attachment, AttachmentId> insertedAttachments = partsDatabase.insertAttachmentsForMessage(messageId, allAttachments, quoteAttachments);
      String                        serializedContacts  = getSerializedSharedContacts(insertedAttachments, sharedContacts);
      String                        serializedPreviews  = getSerializedLinkPreviews(insertedAttachments, linkPreviews);

      if (!TextUtils.isEmpty(serializedContacts)) {
        ContentValues contactValues = new ContentValues();
        contactValues.put(SHARED_CONTACTS, serializedContacts);

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        int rows = database.update(TABLE_NAME, contactValues, ID + " = ?", new String[]{ String.valueOf(messageId) });

        if (rows <= 0) {
          Log.w(TAG, "Failed to update message with shared contact data.");
        }
      }

      if (!TextUtils.isEmpty(serializedPreviews)) {
        ContentValues contactValues = new ContentValues();
        contactValues.put(LINK_PREVIEWS, serializedPreviews);

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        int rows = database.update(TABLE_NAME, contactValues, ID + " = ?", new String[]{ String.valueOf(messageId) });

        if (rows <= 0) {
          Log.w(TAG, "Failed to update message with link preview data.");
        }
      }

      db.setTransactionSuccessful();
      return messageId;
    } finally {
      db.endTransaction();

      if (insertListener != null) {
        insertListener.onComplete();
      }

      notifyConversationListeners(contentValues.getAsLong(THREAD_ID));
      DatabaseFactory.getThreadDatabase(context).update(contentValues.getAsLong(THREAD_ID), true);
    }
  }

  @Override
  public boolean deleteMessage(long messageId) {
    Log.d(TAG, "deleteMessage(" + messageId + ")");

    long               threadId           = getThreadIdForMessage(messageId);
    AttachmentDatabase attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context);
    attachmentDatabase.deleteAttachmentsForMessage(messageId);

    GroupReceiptDatabase groupReceiptDatabase = DatabaseFactory.getGroupReceiptDatabase(context);
    groupReceiptDatabase.deleteRowsForMessage(messageId);

    MentionDatabase mentionDatabase = DatabaseFactory.getMentionDatabase(context);
    mentionDatabase.deleteMentionsForMessage(messageId);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, ID_WHERE, new String[] {messageId+""});
    boolean threadDeleted = DatabaseFactory.getThreadDatabase(context).update(threadId, false);
    notifyConversationListeners(threadId);
    notifyStickerListeners();
    notifyStickerPackListeners();
    return threadDeleted;
  }

  @Override
  public void deleteThread(long threadId) {
    Log.d(TAG, "deleteThread(" + threadId + ")");
    Set<Long> singleThreadSet = new HashSet<>();
    singleThreadSet.add(threadId);
    deleteThreads(singleThreadSet);
  }

  private @Nullable String getSerializedSharedContacts(@NonNull Map<Attachment, AttachmentId> insertedAttachmentIds, @NonNull List<Contact> contacts) {
    if (contacts.isEmpty()) return null;

    JSONArray sharedContactJson = new JSONArray();

    for (Contact contact : contacts) {
      try {
        AttachmentId attachmentId = null;

        if (contact.getAvatarAttachment() != null) {
          attachmentId = insertedAttachmentIds.get(contact.getAvatarAttachment());
        }

        Avatar  updatedAvatar  = new Avatar(attachmentId,
                                            contact.getAvatarAttachment(),
                                            contact.getAvatar() != null && contact.getAvatar().isProfile());
        Contact updatedContact = new Contact(contact, updatedAvatar);

        sharedContactJson.put(new JSONObject(updatedContact.serialize()));
      } catch (JSONException | IOException e) {
        Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e);
      }
    }
    return sharedContactJson.toString();
  }

  private @Nullable String getSerializedLinkPreviews(@NonNull Map<Attachment, AttachmentId> insertedAttachmentIds, @NonNull List<LinkPreview> previews) {
    if (previews.isEmpty()) return null;

    JSONArray linkPreviewJson = new JSONArray();

    for (LinkPreview preview : previews) {
      try {
        AttachmentId attachmentId = null;

        if (preview.getThumbnail().isPresent()) {
          attachmentId = insertedAttachmentIds.get(preview.getThumbnail().get());
        }

        LinkPreview updatedPreview = new LinkPreview(preview.getUrl(), preview.getTitle(), preview.getDescription(), preview.getDate(), attachmentId);
        linkPreviewJson.put(new JSONObject(updatedPreview.serialize()));
      } catch (JSONException | IOException e) {
        Log.w(TAG, "Failed to serialize shared contact. Skipping it.", e);
      }
    }
    return linkPreviewJson.toString();
  }

  private boolean isDuplicate(IncomingMediaMessage message, long threadId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor         cursor   = database.query(TABLE_NAME, null, DATE_SENT + " = ? AND " + RECIPIENT_ID + " = ? AND " + THREAD_ID + " = ?",
                                             new String[]{String.valueOf(message.getSentTimeMillis()), message.getFrom().serialize(), String.valueOf(threadId)},
                                             null, null, null, "1");

    try {
      return cursor != null && cursor.moveToFirst();
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  @Override
  public boolean isSent(long messageId) {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    try (Cursor cursor = database.query(TABLE_NAME, new String[] {  MESSAGE_BOX }, ID + " = ?", new String[] { String.valueOf(messageId)}, null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        long type = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX));
        return Types.isSentType(type);
      }
    }
    return false;
  }

  @Override
  public List<MessageRecord> getProfileChangeDetailsRecords(long threadId, long afterTimestamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  void deleteThreads(@NonNull Set<Long> threadIds) {
    Log.d(TAG, "deleteThreads(count: " + threadIds.size() + ")");

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    String where      = "";
    Cursor cursor     = null;

    for (long threadId : threadIds) {
      where += THREAD_ID + " = '" + threadId + "' OR ";
    }

    where = where.substring(0, where.length() - 4);

    try {
      cursor = db.query(TABLE_NAME, new String[] {ID}, where, null, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        deleteMessage(cursor.getLong(0));
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
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

    try (Reader reader = readerFor(rawQuery(where, args, false, limit))) {
      List<MessageRecord> results = new ArrayList<>(reader.cursor.getCount());

      while (reader.getNext() != null) {
        results.add(reader.getCurrent());
      }

      return results;
    }
  }

  @Override
  public void deleteAllThreads() {
    Log.d(TAG, "deleteAllThreads()");
    DatabaseFactory.getAttachmentDatabase(context).deleteAllAttachments();
    DatabaseFactory.getGroupReceiptDatabase(context).deleteAllRows();
    DatabaseFactory.getMentionDatabase(context).deleteAllMentions();

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);
  }

  @Override
  public @Nullable ViewOnceExpirationInfo getNearestExpiringViewOnceMessage() {
    SQLiteDatabase       db                = databaseHelper.getReadableDatabase();
    ViewOnceExpirationInfo info              = null;
    long                 nearestExpiration = Long.MAX_VALUE;

    String   query = "SELECT " +
                         TABLE_NAME + "." + ID + ", " +
                         VIEW_ONCE + ", " +
                         DATE_RECEIVED + " " +
                     "FROM " + TABLE_NAME + " INNER JOIN " + AttachmentDatabase.TABLE_NAME + " " +
                         "ON " + TABLE_NAME + "." + ID + " = " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID + " " +
                     "WHERE " +
                         VIEW_ONCE + " > 0 AND " +
                         "(" + AttachmentDatabase.DATA + " NOT NULL OR " + AttachmentDatabase.TRANSFER_STATE + " != ?)";
    String[] args = new String[] { String.valueOf(AttachmentDatabase.TRANSFER_PROGRESS_DONE) };

    try (Cursor cursor = db.rawQuery(query, args)) {
      while (cursor != null && cursor.moveToNext()) {
        long id              = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        long dateReceived    = cursor.getLong(cursor.getColumnIndexOrThrow(DATE_RECEIVED));
        long expiresAt       = dateReceived + ViewOnceUtil.MAX_LIFESPAN;

        if (info == null || expiresAt < nearestExpiration) {
          info              = new ViewOnceExpirationInfo(id, dateReceived);
          nearestExpiration = expiresAt;
        }
      }
    }

    return info;
  }

  private static @NonNull List<Mention> parseQuoteMentions(@NonNull Context context, Cursor cursor) {
    byte[] raw = cursor.getBlob(cursor.getColumnIndexOrThrow(QUOTE_MENTIONS));

    return MentionUtil.bodyRangeListToMentions(context, raw);
  }

  @Override
  public SQLiteDatabase beginTransaction() {
    databaseHelper.getWritableDatabase().beginTransaction();
    return databaseHelper.getWritableDatabase();
  }

  @Override
  public void setTransactionSuccessful() {
    databaseHelper.getWritableDatabase().setTransactionSuccessful();
  }

  @Override
  public void endTransaction() {
    databaseHelper.getWritableDatabase().endTransaction();
  }

  public static Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public static OutgoingMessageReader readerFor(OutgoingMediaMessage message, long threadId) {
    return new OutgoingMessageReader(message, threadId);
  }

  public static class Status {
    public static final int DOWNLOAD_INITIALIZED     = 1;
    public static final int DOWNLOAD_NO_CONNECTIVITY = 2;
    public static final int DOWNLOAD_CONNECTING      = 3;
    public static final int DOWNLOAD_SOFT_FAILURE    = 4;
    public static final int DOWNLOAD_HARD_FAILURE    = 5;
    public static final int DOWNLOAD_APN_UNAVAILABLE = 6;
  }

  public static class OutgoingMessageReader {

    private final Context              context;
    private final OutgoingMediaMessage message;
    private final long                 id;
    private final long                 threadId;

    public OutgoingMessageReader(OutgoingMediaMessage message, long threadId) {
      this.context  = ApplicationDependencies.getApplication();
      this.message  = message;
      this.id       = new SecureRandom().nextLong();
      this.threadId = threadId;
    }

    public MessageRecord getCurrent() {
      SlideDeck slideDeck = new SlideDeck(context, message.getAttachments());

      CharSequence  quoteText     = message.getOutgoingQuote() != null ? message.getOutgoingQuote().getText() : null;
      List<Mention> quoteMentions = message.getOutgoingQuote() != null ? message.getOutgoingQuote().getMentions() : Collections.emptyList();

      if (quoteText != null && !quoteMentions.isEmpty()) {
        MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, quoteText, quoteMentions);

        quoteText     = updated.getBody();
        quoteMentions = updated.getMentions();
      }

      return new MediaMmsMessageRecord(id,
                                       message.getRecipient(),
                                       message.getRecipient(),
                                       1,
                                       System.currentTimeMillis(),
                                       System.currentTimeMillis(),
                                       -1,
                                       0,
                                       threadId, message.getBody(),
                                       slideDeck,
                                       slideDeck.getSlides().size(),
                                       message.isSecure() ? MmsSmsColumns.Types.getOutgoingEncryptedMessageType() : MmsSmsColumns.Types.getOutgoingSmsMessageType(),
                                       new LinkedList<>(),
                                       new LinkedList<>(),
                                       message.getSubscriptionId(),
                                       message.getExpiresIn(),
                                       System.currentTimeMillis(),
                                       message.isViewOnce(),
                                       0,
                                       message.getOutgoingQuote() != null ?
                                           new Quote(message.getOutgoingQuote().getId(),
                                                     message.getOutgoingQuote().getAuthor(),
                                                     quoteText,
                                                     message.getOutgoingQuote().isOriginalMissing(),
                                                     new SlideDeck(context, message.getOutgoingQuote().getAttachments()),
                                                     quoteMentions) :
                                           null,
                                       message.getSharedContacts(),
                                       message.getLinkPreviews(),
                                       false,
                                       Collections.emptyList(),
                                       false,
                                       false,
                                       0,
                                       0);
    }
  }

  public static class Reader implements MessageDatabase.Reader {

    private final Cursor  cursor;
    private final Context context;

    public Reader(Cursor cursor) {
      this.cursor  = cursor;
      this.context = ApplicationDependencies.getApplication();
    }

    @Override
    public MessageRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    @Override
    public MessageRecord getCurrent() {
      long mmsType = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_TYPE));

      if (mmsType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
        return getNotificationMmsMessageRecord(cursor);
      } else {
        return getMediaMmsMessageRecord(cursor);
      }
    }

    private NotificationMmsMessageRecord getNotificationMmsMessageRecord(Cursor cursor) {
      long      id                   = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.ID));
      long      dateSent             = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_SENT));
      long      dateReceived         = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_RECEIVED));
      long      threadId             = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.THREAD_ID));
      long      mailbox              = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));
      long      recipientId          = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.RECIPIENT_ID));
      int       addressDeviceId      = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS_DEVICE_ID));
      Recipient recipient            = Recipient.live(RecipientId.from(recipientId)).get();

      String    contentLocation      = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.CONTENT_LOCATION));
      String    transactionId        = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.TRANSACTION_ID));
      long      messageSize          = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_SIZE));
      long      expiry               = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.EXPIRY));
      int       status               = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.STATUS));
      int       deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.DELIVERY_RECEIPT_COUNT));
      int       readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.READ_RECEIPT_COUNT));
      int       subscriptionId       = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.SUBSCRIPTION_ID));
      int       viewedReceiptCount   = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsColumns.VIEWED_RECEIPT_COUNT));

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount = 0;
      }

      byte[]contentLocationBytes = null;
      byte[]transactionIdBytes   = null;

      if (!TextUtils.isEmpty(contentLocation))
        contentLocationBytes = org.thoughtcrime.securesms.util.Util.toIsoBytes(contentLocation);

      if (!TextUtils.isEmpty(transactionId))
        transactionIdBytes = org.thoughtcrime.securesms.util.Util.toIsoBytes(transactionId);

      SlideDeck slideDeck = new SlideDeck(context, new MmsNotificationAttachment(status, messageSize));


      return new NotificationMmsMessageRecord(id, recipient, recipient,
                                              addressDeviceId, dateSent, dateReceived, deliveryReceiptCount, threadId,
                                              contentLocationBytes, messageSize, expiry, status,
                                              transactionIdBytes, mailbox, subscriptionId, slideDeck,
                                              readReceiptCount, viewedReceiptCount);
    }

    private MediaMmsMessageRecord getMediaMmsMessageRecord(Cursor cursor) {
      long                 id                   = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.ID));
      long                 dateSent             = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_SENT));
      long                 dateReceived         = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_RECEIVED));
      long                 dateServer           = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.DATE_SERVER));
      long                 box                  = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));
      long                 threadId             = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.THREAD_ID));
      long                 recipientId          = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.RECIPIENT_ID));
      int                  addressDeviceId      = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS_DEVICE_ID));
      int                  deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.DELIVERY_RECEIPT_COUNT));
      int                  readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.READ_RECEIPT_COUNT));
      String               body                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.BODY));
      int                  partCount            = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.PART_COUNT));
      String               mismatchDocument     = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.MISMATCHED_IDENTITIES));
      String               networkDocument      = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.NETWORK_FAILURE));
      int                  subscriptionId       = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.SUBSCRIPTION_ID));
      long                 expiresIn            = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.EXPIRES_IN));
      long                 expireStarted        = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.EXPIRE_STARTED));
      boolean              unidentified         = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.UNIDENTIFIED)) == 1;
      boolean              isViewOnce           = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.VIEW_ONCE))   == 1;
      boolean              remoteDelete         = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.REMOTE_DELETED))   == 1;
      List<ReactionRecord> reactions            = parseReactions(cursor);
      boolean              mentionsSelf         = CursorUtil.requireBoolean(cursor, MENTIONS_SELF);
      long                 notifiedTimestamp    = CursorUtil.requireLong(cursor, NOTIFIED_TIMESTAMP);
      int                  viewedReceiptCount   = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsColumns.VIEWED_RECEIPT_COUNT));

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount   = 0;
        viewedReceiptCount = 0;
      }

      Recipient                 recipient          = Recipient.live(RecipientId.from(recipientId)).get();
      List<IdentityKeyMismatch> mismatches         = getMismatchedIdentities(mismatchDocument);
      List<NetworkFailure>      networkFailures    = getFailures(networkDocument);
      List<DatabaseAttachment>  attachments        = DatabaseFactory.getAttachmentDatabase(context).getAttachment(cursor);
      List<Contact>             contacts           = getSharedContacts(cursor, attachments);
      Set<Attachment>           contactAttachments = Stream.of(contacts).map(Contact::getAvatarAttachment).withoutNulls().collect(Collectors.toSet());
      List<LinkPreview>         previews           = getLinkPreviews(cursor, attachments);
      Set<Attachment>           previewAttachments = Stream.of(previews).filter(lp -> lp.getThumbnail().isPresent()).map(lp -> lp.getThumbnail().get()).collect(Collectors.toSet());
      SlideDeck                 slideDeck          = getSlideDeck(Stream.of(attachments).filterNot(contactAttachments::contains).filterNot(previewAttachments::contains).toList());
      Quote                     quote              = getQuote(cursor);

      return new MediaMmsMessageRecord(id, recipient, recipient,
                                       addressDeviceId, dateSent, dateReceived, dateServer, deliveryReceiptCount,
                                       threadId, body, slideDeck, partCount, box, mismatches,
                                       networkFailures, subscriptionId, expiresIn, expireStarted,
                                       isViewOnce, readReceiptCount, quote, contacts, previews, unidentified, reactions,
                                       remoteDelete, mentionsSelf, notifiedTimestamp, viewedReceiptCount);
    }

    private List<IdentityKeyMismatch> getMismatchedIdentities(String document) {
      if (!TextUtils.isEmpty(document)) {
        try {
          return JsonUtils.fromJson(document, IdentityKeyMismatchList.class).getList();
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      return new LinkedList<>();
    }

    private List<NetworkFailure> getFailures(String document) {
      if (!TextUtils.isEmpty(document)) {
        try {
          return JsonUtils.fromJson(document, NetworkFailureList.class).getList();
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
        }
      }

      return new LinkedList<>();
    }

    private SlideDeck getSlideDeck(@NonNull List<DatabaseAttachment> attachments) {
      List<DatabaseAttachment> messageAttachments = Stream.of(attachments)
                                                          .filterNot(Attachment::isQuote)
                                                          .sorted(new DatabaseAttachment.DisplayOrderComparator())
                                                          .toList();
      return new SlideDeck(context, messageAttachments);
    }

    private @Nullable Quote getQuote(@NonNull Cursor cursor) {
      long                       quoteId          = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.QUOTE_ID));
      long                       quoteAuthor      = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.QUOTE_AUTHOR));
      CharSequence               quoteText        = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.QUOTE_BODY));
      boolean                    quoteMissing     = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.QUOTE_MISSING)) == 1;
      List<Mention>              quoteMentions    = parseQuoteMentions(context, cursor);
      List<DatabaseAttachment>   attachments      = DatabaseFactory.getAttachmentDatabase(context).getAttachment(cursor);
      List<? extends Attachment> quoteAttachments = Stream.of(attachments).filter(Attachment::isQuote).toList();
      SlideDeck                  quoteDeck        = new SlideDeck(context, quoteAttachments);

      if (quoteId > 0 && quoteAuthor > 0) {
        if (quoteText != null && !quoteMentions.isEmpty()) {
          MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, quoteText, quoteMentions);

          quoteText     = updated.getBody();
          quoteMentions = updated.getMentions();
        }

        return new Quote(quoteId, RecipientId.from(quoteAuthor), quoteText, quoteMissing, quoteDeck, quoteMentions);
      } else {
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

  private long generatePduCompatTimestamp() {
    final long time = System.currentTimeMillis();
    return time - (time % 1000);
  }
}

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
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.android.mms.pdu_alt.NotificationInd;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.protobuf.InvalidProtocolBufferException;

import net.zetetic.database.sqlcipher.SQLiteStatement;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.signal.core.util.CursorExtensionsKt;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SQLiteDatabaseExtensionsKt;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.MmsNotificationAttachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.documents.NetworkFailureSet;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageExportStatus;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ParentStoryId;
import org.thoughtcrime.securesms.database.model.Quote;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.database.model.StoryResult;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.database.model.StoryViewState;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge;
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState;
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.jobs.TrimThreadJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingGroupUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingPaymentsActivatedMessages;
import org.thoughtcrime.securesms.mms.OutgoingPaymentsNotificationMessage;
import org.thoughtcrime.securesms.mms.OutgoingRequestToActivatePaymentMessages;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.revealable.ViewOnceExpirationInfo;
import org.thoughtcrime.securesms.revealable.ViewOnceUtil;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stories.Stories;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

import static org.thoughtcrime.securesms.contactshare.Contact.Avatar;

public class MmsDatabase extends MessageDatabase {

  private static final String TAG = Log.tag(MmsDatabase.class);

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
          static final String QUOTE_TYPE       = "quote_type";

          static final String SHARED_CONTACTS = "shared_contacts";
          static final String LINK_PREVIEWS   = "previews";
          static final String MENTIONS_SELF   = "mentions_self";
          static final String MESSAGE_RANGES  = "ranges";

  public  static final String VIEW_ONCE       = "reveal_duration";
  public  static final String STORY_TYPE      = "is_story";
          static final String PARENT_STORY_ID = "parent_story_id";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                     + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                  THREAD_ID              + " INTEGER, " +
                                                                                  DATE_SENT              + " INTEGER, " +
                                                                                  DATE_RECEIVED          + " INTEGER, " +
                                                                                  DATE_SERVER            + " INTEGER DEFAULT -1, " +
                                                                                  MESSAGE_BOX            + " INTEGER, " +
                                                                                  READ                   + " INTEGER DEFAULT 0, " +
                                                                                  BODY                   + " TEXT, " +
                                                                                  PART_COUNT             + " INTEGER, " +
                                                                                  CONTENT_LOCATION       + " TEXT, " +
                                                                                  RECIPIENT_ID           + " INTEGER, " +
                                                                                  ADDRESS_DEVICE_ID      + " INTEGER, " +
                                                                                  EXPIRY                 + " INTEGER, " +
                                                                                  MESSAGE_TYPE           + " INTEGER, " +
                                                                                  MESSAGE_SIZE           + " INTEGER, " +
                                                                                  STATUS                 + " INTEGER, " +
                                                                                  TRANSACTION_ID         + " TEXT, " +
                                                                                  DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " +
                                                                                  MISMATCHED_IDENTITIES  + " TEXT DEFAULT NULL, " +
                                                                                  NETWORK_FAILURE        + " TEXT DEFAULT NULL," +
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
                                                                                  QUOTE_TYPE             + " INTEGER DEFAULT 0," +
                                                                                  SHARED_CONTACTS        + " TEXT, " +
                                                                                  UNIDENTIFIED           + " INTEGER DEFAULT 0, " +
                                                                                  LINK_PREVIEWS          + " TEXT, " +
                                                                                  VIEW_ONCE              + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS_UNREAD       + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS_LAST_SEEN    + " INTEGER DEFAULT -1, " +
                                                                                  REMOTE_DELETED         + " INTEGER DEFAULT 0, " +
                                                                                  MENTIONS_SELF          + " INTEGER DEFAULT 0, " +
                                                                                  NOTIFIED_TIMESTAMP     + " INTEGER DEFAULT 0, " +
                                                                                  VIEWED_RECEIPT_COUNT   + " INTEGER DEFAULT 0, " +
                                                                                  SERVER_GUID            + " TEXT DEFAULT NULL, " +
                                                                                  RECEIPT_TIMESTAMP      + " INTEGER DEFAULT -1, " +
                                                                                  MESSAGE_RANGES         + " BLOB DEFAULT NULL, " +
                                                                                  STORY_TYPE             + " INTEGER DEFAULT 0, " +
                                                                                  PARENT_STORY_ID        + " INTEGER DEFAULT 0, " +
                                                                                  EXPORT_STATE           + " BLOB DEFAULT NULL, " +
                                                                                  EXPORTED               + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS mms_read_and_notified_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + NOTIFIED + "," + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_message_box_index ON " + TABLE_NAME + " (" + MESSAGE_BOX + ");",
    "CREATE INDEX IF NOT EXISTS mms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ", " + RECIPIENT_ID + ", " + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_date_server_index ON " + TABLE_NAME + " (" + DATE_SERVER + ");",
    "CREATE INDEX IF NOT EXISTS mms_thread_date_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");",
    "CREATE INDEX IF NOT EXISTS mms_reactions_unread_index ON " + TABLE_NAME + " (" + REACTIONS_UNREAD + ");",
    "CREATE INDEX IF NOT EXISTS mms_is_story_index ON " + TABLE_NAME + " (" + STORY_TYPE + ");",
    "CREATE INDEX IF NOT EXISTS mms_parent_story_id_index ON " + TABLE_NAME + " (" + PARENT_STORY_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_thread_story_parent_story_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + "," + STORY_TYPE + "," + PARENT_STORY_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_quote_id_quote_author_index ON " + TABLE_NAME + "(" + QUOTE_ID + ", " + QUOTE_AUTHOR + ");",
    "CREATE INDEX IF NOT EXISTS mms_exported_index ON " + TABLE_NAME + " (" + EXPORTED + ");"
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
      EXPIRES_IN, EXPIRE_STARTED, NOTIFIED, QUOTE_ID, QUOTE_AUTHOR, QUOTE_BODY, QUOTE_ATTACHMENT, QUOTE_TYPE, QUOTE_MISSING, QUOTE_MENTIONS,
      SHARED_CONTACTS, LINK_PREVIEWS, UNIDENTIFIED, VIEW_ONCE, REACTIONS_UNREAD, REACTIONS_LAST_SEEN,
      REMOTE_DELETED, MENTIONS_SELF, NOTIFIED_TIMESTAMP, VIEWED_RECEIPT_COUNT, RECEIPT_TIMESTAMP, MESSAGE_RANGES,
      STORY_TYPE, PARENT_STORY_ID,
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
      "'" + AttachmentDatabase.VIDEO_GIF + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.VIDEO_GIF + "," +
      "'" + AttachmentDatabase.WIDTH + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.WIDTH + "," +
      "'" + AttachmentDatabase.HEIGHT + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.HEIGHT + "," +
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
      "'" + AttachmentDatabase.UPLOAD_TIMESTAMP + "', " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.UPLOAD_TIMESTAMP +
      ")) AS " + AttachmentDatabase.ATTACHMENT_JSON_ALIAS,
      };

  private static final String IS_STORY_CLAUSE = STORY_TYPE + " > 0 AND " + REMOTE_DELETED + " = 0";

  private static final String RAW_ID_WHERE = TABLE_NAME + "._id = ?";

  private static final String OUTGOING_INSECURE_MESSAGES_CLAUSE = "(" + MESSAGE_BOX + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND NOT (" + MESSAGE_BOX + " & " + Types.SECURE_MESSAGE_BIT + ")";
  private static final String OUTGOING_SECURE_MESSAGES_CLAUSE   = "(" + MESSAGE_BOX + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND (" + MESSAGE_BOX + " & " + (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT) + ")";

  private final EarlyReceiptCache earlyDeliveryReceiptCache = new EarlyReceiptCache("MmsDelivery");

  public MmsDatabase(Context context, SignalDatabase databaseHelper) {
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
    return internalGetMessage(messageId);
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
  public InsertResult updateBundleMessageBody(long messageId, String body) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NonNull List<MarkedMessageInfo> getViewedIncomingMessages(long threadId) {
    SQLiteDatabase db      = databaseHelper.getSignalReadableDatabase();
    String[]       columns = new String[]{ID, RECIPIENT_ID, DATE_SENT, MESSAGE_BOX, THREAD_ID, STORY_TYPE};
    String         where   = THREAD_ID + " = ? AND " + VIEWED_RECEIPT_COUNT + " > 0 AND " + MESSAGE_BOX + " & " + Types.BASE_INBOX_TYPE + " = " + Types.BASE_INBOX_TYPE;
    String[]       args    = SqlUtil.buildArgs(threadId);


    try (Cursor cursor = db.query(getTableName(), columns, where, args, null, null, null, null)) {
      if (cursor == null) {
        return Collections.emptyList();
      }

      List<MarkedMessageInfo> results = new ArrayList<>(cursor.getCount());
      while (cursor.moveToNext()) {
        long           messageId     = CursorUtil.requireLong(cursor, ID);
        RecipientId    recipientId   = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
        long           dateSent      = CursorUtil.requireLong(cursor, DATE_SENT);
        SyncMessageId  syncMessageId = new SyncMessageId(recipientId, dateSent);
        StoryType      storyType     = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));

        results.add(new MarkedMessageInfo(threadId, syncMessageId, new MessageId(messageId, true), null));
      }

      return results;
    }
  }

  @Override
  public @Nullable MarkedMessageInfo setIncomingMessageViewed(long messageId) {
    List<MarkedMessageInfo> results = setIncomingMessagesViewed(Collections.singletonList(messageId));

    if (results.isEmpty()) {
      return null;
    } else {
      return results.get(0);
    }
  }

  @Override
  public @NonNull List<MarkedMessageInfo> setIncomingMessagesViewed(@NonNull List<Long> messageIds) {
    if (messageIds.isEmpty()) {
      return Collections.emptyList();
    }

    SQLiteDatabase          database    = databaseHelper.getSignalWritableDatabase();
    String[]                columns     = new String[]{ID, RECIPIENT_ID, DATE_SENT, MESSAGE_BOX, THREAD_ID, STORY_TYPE};
    String                  where       = ID + " IN (" + Util.join(messageIds, ",") + ") AND " + VIEWED_RECEIPT_COUNT + " = 0";
    List<MarkedMessageInfo> results     = new LinkedList<>();

    database.beginTransaction();
    try (Cursor cursor = database.query(TABLE_NAME, columns, where, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        long type = CursorUtil.requireLong(cursor, MESSAGE_BOX);
        if (Types.isSecureType(type) && Types.isInboxType(type)) {
          long          messageId     = CursorUtil.requireLong(cursor, ID);
          long          threadId      = CursorUtil.requireLong(cursor, THREAD_ID);
          RecipientId   recipientId   = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
          long          dateSent      = CursorUtil.requireLong(cursor, DATE_SENT);
          SyncMessageId syncMessageId = new SyncMessageId(recipientId, dateSent);
          StoryType      storyType    = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));

          results.add(new MarkedMessageInfo(threadId, syncMessageId, new MessageId(messageId, true), null));

          ContentValues contentValues = new ContentValues();
          contentValues.put(VIEWED_RECEIPT_COUNT, 1);
          contentValues.put(RECEIPT_TIMESTAMP, System.currentTimeMillis());

          database.update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(CursorUtil.requireLong(cursor, ID)));
        }
      }
      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }

    Set<Long> threadsUpdated = Stream.of(results)
                                     .map(MarkedMessageInfo::getThreadId)
                                     .collect(Collectors.toSet());

    notifyConversationListeners(threadsUpdated);
    notifyConversationListListeners();

    return results;
  }

  @Override
  public @NonNull List<MarkedMessageInfo>
  setOutgoingGiftsRevealed(@NonNull List<Long> messageIds) {
    String[]                projection = SqlUtil.buildArgs(ID, RECIPIENT_ID, DATE_SENT, THREAD_ID, STORY_TYPE);
    String                  where      = ID + " IN (" + Util.join(messageIds, ",") + ") AND (" + getOutgoingTypeClause() + ") AND (" + getTypeField() + " & " + Types.SPECIAL_TYPES_MASK + " = " + Types.SPECIAL_TYPE_GIFT_BADGE + ") AND " + VIEWED_RECEIPT_COUNT + " = 0";
    List<MarkedMessageInfo> results    = new LinkedList<>();

    getWritableDatabase().beginTransaction();
    try (Cursor cursor = getWritableDatabase().query(TABLE_NAME, projection, where, null, null, null, null)) {
      while (cursor.moveToNext()) {
        long          messageId     = CursorUtil.requireLong(cursor, ID);
        long          threadId      = CursorUtil.requireLong(cursor, THREAD_ID);
        RecipientId   recipientId   = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
        long          dateSent      = CursorUtil.requireLong(cursor, DATE_SENT);
        SyncMessageId syncMessageId = new SyncMessageId(recipientId, dateSent);
        StoryType     storyType    = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));

        results.add(new MarkedMessageInfo(threadId, syncMessageId, new MessageId(messageId, true), null));

        ContentValues contentValues = new ContentValues();
        contentValues.put(VIEWED_RECEIPT_COUNT, 1);
        contentValues.put(RECEIPT_TIMESTAMP, System.currentTimeMillis());

        getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(messageId));
      }
      getWritableDatabase().setTransactionSuccessful();
    } finally {
      getWritableDatabase().endTransaction();
    }

    Set<Long> threadsUpdated = Stream.of(results)
                                     .map(MarkedMessageInfo::getThreadId)
                                     .collect(Collectors.toSet());

    notifyConversationListeners(threadsUpdated);

    return results;
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
  public void insertNumberChangeMessages(@NonNull RecipientId recipientId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insertBoostRequestMessage(@NonNull RecipientId recipientId, long threadId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insertThreadMergeEvent(@NonNull RecipientId recipientId, long threadId, @NonNull ThreadMergeEvent event) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insertSmsExportMessage(@NonNull RecipientId recipientId, long threadId) {
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
    databaseHelper.getSignalWritableDatabase();
  }

  @Override
  public boolean isStory(long messageId) {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    String[]       projection = new String[]{"1"};
    String         where      = IS_STORY_CLAUSE + " AND " + ID + " = ?";
    String[]       whereArgs  = SqlUtil.buildArgs(messageId);

    try (Cursor cursor = database.query(TABLE_NAME, projection, where, whereArgs, null, null, null, "1")) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  @Override
  public @NonNull MessageDatabase.Reader getOutgoingStoriesTo(@NonNull RecipientId recipientId) {
    Recipient recipient = Recipient.resolved(recipientId);
    Long      threadId  = null;

    if (recipient.isGroup()) {
      threadId = SignalDatabase.threads().getThreadIdFor(recipientId);
    }

    String where = IS_STORY_CLAUSE + " AND (" + getOutgoingTypeClause() + ")";

    final String[] whereArgs;
    if (threadId == null) {
      where += " AND " + RECIPIENT_ID + " = ?";
      whereArgs = SqlUtil.buildArgs(recipientId);
    } else {
      where += " AND " + THREAD_ID_WHERE;
      whereArgs = SqlUtil.buildArgs(threadId);
    }

    return new Reader(rawQuery(where, whereArgs));
  }

  @Override
  public @NonNull MessageDatabase.Reader getAllOutgoingStories(boolean reverse, int limit) {
    String where = IS_STORY_CLAUSE + " AND (" + getOutgoingTypeClause() + ")";

    return new Reader(rawQuery(where, null, reverse, limit));
  }

  @Override
  public @NonNull MessageDatabase.Reader getAllOutgoingStoriesAt(long sentTimestamp) {
    String   where      = IS_STORY_CLAUSE + " AND " + DATE_SENT + " = ? AND (" + getOutgoingTypeClause() + ")";
    String[] whereArgs  = SqlUtil.buildArgs(sentTimestamp);
    Cursor   cursor     = rawQuery(where, whereArgs, false, -1L);

    return new Reader(cursor);
  }

  @Override
  public @NonNull List<MarkedMessageInfo> markAllIncomingStoriesRead() {
    String where = IS_STORY_CLAUSE + " AND NOT (" + getOutgoingTypeClause() + ") AND " + READ + " = 0";

    List<MarkedMessageInfo> markedMessageInfos = setMessagesRead(where, null);
    notifyConversationListListeners();

    return markedMessageInfos;
  }

  @Override
  public void markOnboardingStoryRead() {
    RecipientId recipientId = SignalStore.releaseChannelValues().getReleaseChannelRecipientId();
    if (recipientId == null) {
      return;
    }

    String where = IS_STORY_CLAUSE + " AND NOT (" + getOutgoingTypeClause() + ") AND " + READ + " = 0 AND " + RECIPIENT_ID + " = ?";

    List<MarkedMessageInfo> markedMessageInfos = setMessagesRead(where, SqlUtil.buildArgs(recipientId));
    if (!markedMessageInfos.isEmpty()) {
      notifyConversationListListeners();
    }
  }

  @Override
  public @NonNull MessageDatabase.Reader getAllStoriesFor(@NonNull RecipientId recipientId, int limit) {
    long     threadId  = SignalDatabase.threads().getThreadIdIfExistsFor(recipientId);
    String   where     = IS_STORY_CLAUSE + " AND " + THREAD_ID_WHERE;
    String[] whereArgs = SqlUtil.buildArgs(threadId);
    Cursor   cursor    = rawQuery(where, whereArgs, false, limit);

    return new Reader(cursor);
  }

  @Override
  public @NonNull MessageDatabase.Reader getUnreadStories(@NonNull RecipientId recipientId, int limit) {
    final long   threadId = SignalDatabase.threads().getThreadIdIfExistsFor(recipientId);
    final String query    = IS_STORY_CLAUSE +
                            " AND NOT (" + getOutgoingTypeClause() + ") " +
                            " AND " + THREAD_ID_WHERE +
                            " AND " + VIEWED_RECEIPT_COUNT + " = ?";
    final String[] args   = SqlUtil.buildArgs(threadId, 0);

    return new Reader(rawQuery(query, args, false, limit));
  }

  @Override
  public @Nullable ParentStoryId.GroupReply getParentStoryIdForGroupReply(long messageId) {
    String[] projection = SqlUtil.buildArgs(PARENT_STORY_ID);
    String[] args       = SqlUtil.buildArgs(messageId);

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, projection, ID_WHERE, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        ParentStoryId parentStoryId = ParentStoryId.deserialize(CursorUtil.requireLong(cursor, PARENT_STORY_ID));
        if (parentStoryId != null && parentStoryId.isGroupReply()) {
          return (ParentStoryId.GroupReply) parentStoryId;
        } else {
          return null;
        }
      }
    }

    return null;
  }

  @Override
  public @NonNull StoryViewState getStoryViewState(@NonNull RecipientId recipientId) {
    if (!Stories.isFeatureEnabled()) {
      return StoryViewState.NONE;
    }

    long threadId = SignalDatabase.threads().getThreadIdIfExistsFor(recipientId);

    return getStoryViewState(threadId);
  }

  /**
   * Synchronizes whether we've viewed a recipient's story based on incoming sync messages.
   */
  public void updateViewedStories(@NonNull Set<SyncMessageId> syncMessageIds) {
    final String   timestamps = Util.join(syncMessageIds.stream().map(SyncMessageId::getTimetamp).collect(java.util.stream.Collectors.toList()), ",");
    final String[] projection = SqlUtil.buildArgs(RECIPIENT_ID);
    final String   where      = IS_STORY_CLAUSE + " AND " + DATE_SENT + " IN (" + timestamps + ") AND NOT (" + getOutgoingTypeClause() + ") AND " + VIEWED_RECEIPT_COUNT + " > 0";

    try {
      getWritableDatabase().beginTransaction();
      try (Cursor cursor = getWritableDatabase().query(TABLE_NAME, projection, where, null, null, null, null)) {
        while (cursor != null && cursor.moveToNext()) {
          Recipient recipient = Recipient.resolved(RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID)));
          SignalDatabase.recipients().updateLastStoryViewTimestamp(recipient.getId());
        }
      }
      getWritableDatabase().setTransactionSuccessful();
    } finally {
      getWritableDatabase().endTransaction();
    }
  }

  @VisibleForTesting
  @NonNull StoryViewState getStoryViewState(long threadId) {
    final String   hasStoryQuery = "SELECT EXISTS(SELECT 1 FROM " + TABLE_NAME + " WHERE " + IS_STORY_CLAUSE + " AND " + THREAD_ID_WHERE + " LIMIT 1)";
    final String[] hasStoryArgs  = SqlUtil.buildArgs(threadId);
    final boolean  hasStories;

    try (Cursor cursor = getReadableDatabase().rawQuery(hasStoryQuery, hasStoryArgs)) {
      hasStories = cursor != null && cursor.moveToFirst() && !cursor.isNull(0) && cursor.getInt(0) == 1;
    }

    if (!hasStories) {
      return StoryViewState.NONE;
    }

    final String   hasUnviewedStoriesQuery = "SELECT EXISTS(SELECT 1 FROM " + TABLE_NAME + " WHERE " + IS_STORY_CLAUSE + " AND " + THREAD_ID_WHERE + " AND " + VIEWED_RECEIPT_COUNT + " = ? " + "AND NOT (" + getOutgoingTypeClause() + ") LIMIT 1)";
    final String[] hasUnviewedStoriesArgs  = SqlUtil.buildArgs(threadId, 0);
    final boolean  hasUnviewedStories;

    try (Cursor cursor = getReadableDatabase().rawQuery(hasUnviewedStoriesQuery, hasUnviewedStoriesArgs)) {
      hasUnviewedStories = cursor != null && cursor.moveToFirst() && !cursor.isNull(0) && cursor.getInt(0) == 1;
    }

    if (hasUnviewedStories) {
      return StoryViewState.UNVIEWED;
    } else {
      return StoryViewState.VIEWED;
    }
  }

  @Override
  public boolean isOutgoingStoryAlreadyInDatabase(@NonNull RecipientId recipientId, long sentTimestamp) {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    String[]       projection = new String[]{"COUNT(*)"};
    String         where      = RECIPIENT_ID + " = ? AND " + STORY_TYPE + " > 0 AND " + DATE_SENT + " = ? AND (" + getOutgoingTypeClause() + ")";
    String[]       whereArgs  = SqlUtil.buildArgs(recipientId, sentTimestamp);

    try (Cursor cursor = database.query(TABLE_NAME, projection, where, whereArgs, null, null, null, "1")) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0) > 0;
      }
    }

    return false;
  }

  @Override
  public @NonNull MessageId getStoryId(@NonNull RecipientId authorId, long sentTimestamp) throws NoSuchMessageException {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    String[]       projection = new String[]{ID, RECIPIENT_ID};
    String         where      = IS_STORY_CLAUSE + " AND " + DATE_SENT + " = ?";
    String[]       whereArgs  = SqlUtil.buildArgs(sentTimestamp);

    try (Cursor cursor = database.query(TABLE_NAME, projection, where, whereArgs, null, null, null, "1")) {
      if (cursor != null && cursor.moveToFirst()) {
        RecipientId rowRecipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)));

        if (Recipient.self().getId().equals(authorId) || rowRecipientId.equals(authorId)) {
          return new MessageId(CursorUtil.requireLong(cursor, ID), true);
        }
      }
    }

    throw new NoSuchMessageException("No story sent at " + sentTimestamp);
  }

  @Override
  public @NonNull List<RecipientId> getUnreadStoryThreadRecipientIds() {
    SQLiteDatabase db    = getReadableDatabase();
    String         query = "SELECT DISTINCT " + ThreadDatabase.RECIPIENT_ID + "\n"
                           + "FROM " + TABLE_NAME + "\n"
                           + "JOIN " + ThreadDatabase.TABLE_NAME + "\n"
                           + "ON " + TABLE_NAME + "." +  THREAD_ID + " = " + ThreadDatabase.TABLE_NAME + "." + ThreadDatabase.ID + "\n"
                           + "WHERE " + IS_STORY_CLAUSE + " AND (" + getOutgoingTypeClause() + ") = 0 AND " + VIEWED_RECEIPT_COUNT + " = 0 AND " + TABLE_NAME + "." + READ + " = 0";

    try (Cursor cursor = db.rawQuery(query, null)) {
      if (cursor != null) {
        List<RecipientId> recipientIds = new ArrayList<>(cursor.getCount());
        while (cursor.moveToNext()) {
          recipientIds.add(RecipientId.from(cursor.getLong(0)));
        }

        return recipientIds;
      }
    }

    return Collections.emptyList();
  }

  @Override
  public @NonNull List<StoryResult> getOrderedStoryRecipientsAndIds(boolean isOutgoingOnly) {
    String         where = "WHERE is_story > 0 AND remote_deleted = 0" + (isOutgoingOnly ? " AND is_outgoing != 0" : "") + "\n";
    SQLiteDatabase db    = getReadableDatabase();
    String         query = "SELECT\n"
                           + " mms.date AS sent_timestamp,\n"
                           + " mms._id AS mms_id,\n"
                           + " thread_recipient_id,\n"
                           + " (" + getOutgoingTypeClause() + ") AS is_outgoing,\n"
                           + " viewed_receipt_count,\n"
                           + " mms.date,\n"
                           + " receipt_timestamp,\n"
                           + " (" + getOutgoingTypeClause() + ") = 0 AND viewed_receipt_count = 0 AS is_unread\n"
                           + "FROM mms\n"
                           + "JOIN thread\n"
                           + "ON mms.thread_id = thread._id\n"
                           + where
                           + "ORDER BY\n"
                           + "is_unread DESC,\n"
                           + "CASE\n"
                           + "WHEN is_outgoing = 0 AND viewed_receipt_count = 0 THEN mms.date\n"
                           + "WHEN is_outgoing = 0 AND viewed_receipt_count > 0 THEN receipt_timestamp\n"
                           + "WHEN is_outgoing = 1 THEN mms.date\n"
                           + "END DESC";

    List<StoryResult> results;
    try (Cursor cursor = db.rawQuery(query, null)) {
      if (cursor != null) {
        results = new ArrayList<>(cursor.getCount());

        while (cursor.moveToNext()) {
          results.add(new StoryResult(RecipientId.from(CursorUtil.requireLong(cursor, ThreadDatabase.RECIPIENT_ID)),
                                           CursorUtil.requireLong(cursor, "mms_id"),
                                           CursorUtil.requireLong(cursor, "sent_timestamp"),
                                           CursorUtil.requireBoolean(cursor, "is_outgoing")));
        }

        return results;
      }
    }

    return Collections.emptyList();
  }

  @Override
  public @NonNull Cursor getStoryReplies(long parentStoryId) {
    String   where     = PARENT_STORY_ID + " = ?";
    String[] whereArgs = SqlUtil.buildArgs(parentStoryId);

    return rawQuery(where, whereArgs, false, 0);
  }

  @Override
  public int getNumberOfStoryReplies(long parentStoryId) {
    SQLiteDatabase db        = databaseHelper.getSignalReadableDatabase();
    String[]       columns   = new String[]{"COUNT(*)"};
    String         where     = PARENT_STORY_ID + " = ?";
    String[]       whereArgs = SqlUtil.buildArgs(parentStoryId);

    try (Cursor cursor = db.query(TABLE_NAME, columns, where, whereArgs, null, null, null, null)) {
      return cursor != null && cursor.moveToNext() ? cursor.getInt(0) : 0;
    }
  }

  @Override
  public boolean containsStories(long threadId) {
    SQLiteDatabase db        = databaseHelper.getSignalReadableDatabase();
    String[]       columns   = new String[]{"1"};
    String         where     = THREAD_ID_WHERE + " AND " + STORY_TYPE + " > 0";
    String[]       whereArgs = SqlUtil.buildArgs(threadId);

    try (Cursor cursor = db.query(TABLE_NAME, columns, where, whereArgs, null, null, null, "1")) {
      return cursor != null && cursor.moveToNext();
    }
  }

  @Override
  public boolean hasSelfReplyInStory(long parentStoryId) {
    SQLiteDatabase db        = databaseHelper.getSignalReadableDatabase();
    String[]       columns   = new String[]{"COUNT(*)"};
    String         where     = PARENT_STORY_ID + " = ? AND (" + getOutgoingTypeClause() + ")";
    String[]       whereArgs = SqlUtil.buildArgs(-parentStoryId);

    try (Cursor cursor = db.query(TABLE_NAME, columns, where, whereArgs, null, null, null, null)) {
      return cursor != null && cursor.moveToNext() && cursor.getInt(0) > 0;
    }
  }

  @Override
  public boolean hasGroupReplyOrReactionInStory(long parentStoryId) {
    return hasSelfReplyInStory(-parentStoryId);
  }

  @Override
  public @Nullable Long getOldestStorySendTimestamp(boolean hasSeenReleaseChannelStories) {
    long           releaseChannelThreadId = getReleaseChannelThreadId(hasSeenReleaseChannelStories);
    SQLiteDatabase db                     = databaseHelper.getSignalReadableDatabase();
    String[]       columns                = new String[] { DATE_SENT };
    String         where                  = IS_STORY_CLAUSE + " AND " + THREAD_ID + " != ?";
    String         orderBy                = DATE_SENT + " ASC";
    String         limit                  = "1";

    try (Cursor cursor = db.query(TABLE_NAME, columns, where, SqlUtil.buildArgs(releaseChannelThreadId), null, null, orderBy, limit)) {
      return cursor != null && cursor.moveToNext() ? cursor.getLong(0) : null;
    }
  }

  private static long getReleaseChannelThreadId(boolean hasSeenReleaseChannelStories) {
    if (hasSeenReleaseChannelStories) {
      return -1L;
    }

    RecipientId releaseChannelRecipientId = SignalStore.releaseChannelValues().getReleaseChannelRecipientId();
    if (releaseChannelRecipientId == null) {
      return -1L;
    }

    Long releaseChannelThreadId = SignalDatabase.threads().getThreadIdFor(releaseChannelRecipientId);
    if (releaseChannelThreadId == null) {
      return -1L;
    }

    return releaseChannelThreadId;
  }

  @Override
  public void deleteGroupStoryReplies(long parentStoryId) {
    SQLiteDatabase db   = databaseHelper.getSignalWritableDatabase();
    String[]       args = SqlUtil.buildArgs(parentStoryId);

    db.delete(TABLE_NAME, PARENT_STORY_ID + " = ?", args);
  }

  @Override
  public int deleteStoriesOlderThan(long timestamp, boolean hasSeenReleaseChannelStories) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      long        releaseChannelThreadId      = getReleaseChannelThreadId(hasSeenReleaseChannelStories);
      String      storiesBeforeTimestampWhere = IS_STORY_CLAUSE + " AND " + DATE_SENT + " < ? AND " + THREAD_ID + " != ?";
      String[]    sharedArgs                  = SqlUtil.buildArgs(timestamp, releaseChannelThreadId);
      String      deleteStoryRepliesQuery     = "DELETE FROM " + TABLE_NAME + " " +
                                                "WHERE " + PARENT_STORY_ID + " > 0 AND " + PARENT_STORY_ID + " IN (" +
                                                    "SELECT " + ID + " " +
                                                    "FROM " + TABLE_NAME + " " +
                                                    "WHERE " + storiesBeforeTimestampWhere +
                                                ")";
      String      disassociateQuoteQuery      = "UPDATE " + TABLE_NAME + " " +
                                                "SET " + QUOTE_MISSING + " = 1, " + QUOTE_BODY + " = '' " +
                                                "WHERE " + PARENT_STORY_ID + " < 0 AND ABS(" + PARENT_STORY_ID + ") IN (" +
                                                    "SELECT " + ID + " " +
                                                    "FROM " + TABLE_NAME + " " +
                                                    "WHERE " + storiesBeforeTimestampWhere +
                                                ")";

      db.execSQL(deleteStoryRepliesQuery, sharedArgs);
      db.execSQL(disassociateQuoteQuery, sharedArgs);

      try (Cursor cursor = db.query(TABLE_NAME, new String[]{RECIPIENT_ID}, storiesBeforeTimestampWhere, sharedArgs, null, null, null)) {
        while (cursor != null && cursor.moveToNext()) {
          RecipientId recipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)));
          ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(recipientId);
        }
      }

      int deletedStoryCount;
      try (Cursor cursor = db.query(TABLE_NAME, new String[]{ID}, storiesBeforeTimestampWhere, sharedArgs, null, null, null)) {
        deletedStoryCount = cursor.getCount();

        while (cursor.moveToNext()) {
          long id = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
          deleteMessage(id);
        }
      }

      db.setTransactionSuccessful();
      return deletedStoryCount;
    } finally {
      db.endTransaction();
    }
  }

  private void disassociateStoryQuotes(long storyId) {
    ContentValues contentValues = new ContentValues(2);
    contentValues.put(QUOTE_MISSING, 1);
    contentValues.putNull(QUOTE_BODY);

    getWritableDatabase().update(TABLE_NAME,
                                 contentValues,
                                 PARENT_STORY_ID + " = ?",
                                 SqlUtil.buildArgs(new ParentStoryId.DirectReply(storyId).serialize()));
  }

  @Override
  public boolean isGroupQuitMessage(long messageId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String[] columns = new String[]{ID};
    long     type    = Types.getOutgoingEncryptedMessageType() | Types.GROUP_LEAVE_BIT;
    String   query   = ID + " = ? AND " + MESSAGE_BOX + " & " + type + " = " + type + " AND " + MESSAGE_BOX + " & " + Types.GROUP_V2_BIT + " = 0";
    String[] args    = SqlUtil.buildArgs(messageId);

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, args, null, null, null, null)) {
      if (cursor.getCount() == 1) {
        return true;
      }
    }

    return false;
  }

  @Override
  public long getLatestGroupQuitTimestamp(long threadId, long quitTimeBarrier) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String[] columns = new String[]{DATE_SENT};
    long     type    = Types.getOutgoingEncryptedMessageType() | Types.GROUP_LEAVE_BIT;
    String   query   = THREAD_ID + " = ? AND " + MESSAGE_BOX + " & " + type + " = " + type + " AND " + MESSAGE_BOX + " & " + Types.GROUP_V2_BIT + " = 0 AND " + DATE_SENT + " < ?";
    String[] args    = new String[]{String.valueOf(threadId), String.valueOf(quitTimeBarrier)};
    String   orderBy = DATE_SENT + " DESC";
    String   limit   = "1";

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, args, null, null, orderBy, limit)) {
      if (cursor.moveToFirst()) {
        return CursorUtil.requireLong(cursor, DATE_SENT);
      }
    }

    return -1;
  }

  @Override
  public int getMessageCountForThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String   query = THREAD_ID + " = ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ?";
    String[] args  = SqlUtil.buildArgs(threadId, 0, 0);

    try (Cursor cursor = db.query(TABLE_NAME, COUNT, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  @Override
  public int getMessageCountForThread(long threadId, long beforeTime) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String   query = THREAD_ID + " = ? AND " + DATE_RECEIVED + " < ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ?";
    String[] args  = SqlUtil.buildArgs(threadId, beforeTime, 0, 0);

    try (Cursor cursor = db.query(TABLE_NAME, COUNT, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  @Override
  public boolean hasMeaningfulMessage(long threadId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { "1" }, THREAD_ID + " = ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ?", SqlUtil.buildArgs(threadId, 0, 0), null, null, null, "1")) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  @Override
  public int getIncomingMeaningfulMessageCountSince(long threadId, long afterTime) {
    SQLiteDatabase db                      = databaseHelper.getSignalReadableDatabase();
    String[]       projection              = SqlUtil.COUNT;
    String         where                   = THREAD_ID + " = ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ? AND " + DATE_RECEIVED + " >= ?";
    String[]       whereArgs               = SqlUtil.buildArgs(threadId, 0, 0, afterTime);

    try (Cursor cursor = db.query(TABLE_NAME, projection, where, whereArgs, null, null, null, "1")) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  @Override
  public void addFailures(long messageId, List<NetworkFailure> failure) {
    try {
      addToDocument(messageId, NETWORK_FAILURE, failure, NetworkFailureSet.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public void setNetworkFailures(long messageId, Set<NetworkFailure> failures) {
    try {
      setDocument(databaseHelper.getSignalWritableDatabase(), messageId, NETWORK_FAILURE, new NetworkFailureSet(failures));
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public Set<MessageUpdate> incrementReceiptCount(SyncMessageId messageId, long timestamp, @NonNull ReceiptType receiptType, MessageQualifier messageQualifier) {
    SQLiteDatabase     database       = databaseHelper.getSignalWritableDatabase();
    Set<MessageUpdate> messageUpdates = new HashSet<>();

    final String qualifierWhere;
    switch (messageQualifier) {
      case NORMAL:
        qualifierWhere = " AND NOT (" + IS_STORY_CLAUSE + ")";
        break;
      case STORY:
        qualifierWhere = " AND " + IS_STORY_CLAUSE;
        break;
      case ALL:
        qualifierWhere = "";
        break;
      default:
        throw new IllegalArgumentException("Unsupported qualifier: " + messageQualifier);
    }

    try (Cursor cursor = SQLiteDatabaseExtensionsKt.select(database, ID, THREAD_ID, MESSAGE_BOX, RECIPIENT_ID, receiptType.getColumnName(), RECEIPT_TIMESTAMP)
                                                   .from(TABLE_NAME)
                                                   .where(DATE_SENT + " = ?" + qualifierWhere, messageId.getTimetamp())
                                                   .run())
    {
      while (cursor.moveToNext()) {
        if (Types.isOutgoingMessageType(CursorUtil.requireLong(cursor, MESSAGE_BOX))) {
          RecipientId theirRecipientId = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
          RecipientId ourRecipientId   = messageId.getRecipientId();
          String      columnName       = receiptType.getColumnName();

          if (ourRecipientId.equals(theirRecipientId) || Recipient.resolved(theirRecipientId).isGroup()) {
            long    id               = CursorUtil.requireLong(cursor, ID);
            long    threadId         = CursorUtil.requireLong(cursor, THREAD_ID);
            int     status           = receiptType.getGroupStatus();
            boolean isFirstIncrement = CursorUtil.requireLong(cursor, columnName) == 0;
            long    savedTimestamp   = CursorUtil.requireLong(cursor, RECEIPT_TIMESTAMP);
            long    updatedTimestamp = isFirstIncrement ? Math.max(savedTimestamp, timestamp) : savedTimestamp;

            database.execSQL("UPDATE " + TABLE_NAME + " SET " +
                             columnName + " = " + columnName + " + 1, " +
                             RECEIPT_TIMESTAMP + " = ? WHERE " +
                             ID + " = ?",
                             SqlUtil.buildArgs(updatedTimestamp, id));

            SignalDatabase.groupReceipts().update(ourRecipientId, id, status, timestamp);

            messageUpdates.add(new MessageUpdate(threadId, new MessageId(id, true)));
          }
        }
      }

      if (messageUpdates.size() > 0 && receiptType == ReceiptType.DELIVERY) {
        earlyDeliveryReceiptCache.increment(messageId.getTimetamp(), messageId.getRecipientId(), timestamp);
      }
    }

    messageUpdates.addAll(incrementStoryReceiptCount(messageId, timestamp, receiptType));

    return messageUpdates;
  }

  private Set<MessageUpdate> incrementStoryReceiptCount(SyncMessageId messageId, long timestamp, @NonNull ReceiptType receiptType) {
    SQLiteDatabase     database       = databaseHelper.getSignalWritableDatabase();
    Set<MessageUpdate> messageUpdates = new HashSet<>();
    String             columnName     = receiptType.getColumnName();

    for (MessageId storyMessageId : SignalDatabase.storySends().getStoryMessagesFor(messageId)) {
      database.execSQL("UPDATE " + TABLE_NAME + " SET " +
                       columnName + " = " + columnName + " + 1, " +
                       RECEIPT_TIMESTAMP + " = CASE " +
                         "WHEN " + columnName + " = 0 THEN MAX(" + RECEIPT_TIMESTAMP + ", ?) " +
                         "ELSE " + RECEIPT_TIMESTAMP + " " +
                       "END " +
                       "WHERE " + ID + " = ?",
                       SqlUtil.buildArgs(timestamp, storyMessageId.getId()));

      SignalDatabase.groupReceipts().update(messageId.getRecipientId(), storyMessageId.getId(), receiptType.getGroupStatus(), timestamp);

      messageUpdates.add(new MessageUpdate(-1, storyMessageId));
    }

    return messageUpdates;
  }

  @Override
  public long getThreadIdForMessage(long id) {
    String sql        = "SELECT " + THREAD_ID + " FROM " + TABLE_NAME + " WHERE " + ID + " = ?";
    String[] sqlArgs  = new String[] {id+""};
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

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
      RecipientId groupRecipientId = SignalDatabase.recipients().getOrInsertFromPossiblyMigratedGroupId(retrieved.getGroupId());
      Recipient   groupRecipients  = Recipient.resolved(groupRecipientId);
      return SignalDatabase.threads().getOrCreateThreadIdFor(groupRecipients);
    } else {
      Recipient sender = Recipient.resolved(retrieved.getFrom());
      return SignalDatabase.threads().getOrCreateThreadIdFor(sender);
    }
  }

  private long getThreadIdFor(@NonNull NotificationInd notification) {
    String fromString = notification.getFrom() != null && notification.getFrom().getTextString() != null
                      ? Util.toIsoString(notification.getFrom().getTextString())
                      : "";
    Recipient recipient = Recipient.external(context, fromString);
    return SignalDatabase.threads().getOrCreateThreadIdFor(recipient);
  }

  private Cursor rawQuery(@NonNull String where, @Nullable String[] arguments) {
    return rawQuery(where, arguments, false, 0);
  }

  private Cursor rawQuery(@NonNull String where, @Nullable String[] arguments, boolean reverse, long limit) {
    return rawQuery(MMS_PROJECTION, where, arguments, reverse, limit);
  }

  private Cursor rawQuery(@NonNull String[] projection, @NonNull String where, @Nullable String[] arguments, boolean reverse, long limit) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String rawQueryString   = "SELECT " + Util.join(projection, ",") +
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
  public @Nullable MessageRecord getMessageRecordOrNull(long messageId) {
    try (Cursor cursor = rawQuery(RAW_ID_WHERE, new String[] {messageId + ""})) {
      return new Reader(cursor).getNext();
    }
  }

  @Override
  public Reader getMessages(Collection<Long> messageIds) {
    String ids = TextUtils.join(",", messageIds);
    return readerFor(rawQuery(MmsDatabase.TABLE_NAME + "." + MmsDatabase.ID + " IN (" + ids + ")", null));
  }

  private void updateMailboxBitmask(long id, long maskOff, long maskOn, Optional<Long> threadId) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      db.execSQL("UPDATE " + TABLE_NAME +
                 " SET " + MESSAGE_BOX + " = (" + MESSAGE_BOX + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
                 " WHERE " + ID + " = ?", new String[] { id + "" });

      if (threadId.isPresent()) {
        SignalDatabase.threads().updateSnippetTypeSilently(threadId.get());
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
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
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId, true));
  }

  @Override
  public void markAsRateLimited(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, 0, Types.MESSAGE_RATE_LIMITED_BIT, Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId, true));
  }

  @Override
  public void clearRateLimitStatus(@NonNull Collection<Long> ids) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      for (long id : ids) {
        long threadId = getThreadIdForMessage(id);
        updateMailboxBitmask(id, Types.MESSAGE_RATE_LIMITED_BIT, 0, Optional.of(threadId));
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  @Override
  public void markAsPendingInsecureSmsFallback(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_PENDING_INSECURE_SMS_FALLBACK, Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId, true));
  }

  @Override
  public void markAsSending(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENDING_TYPE, Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId, true));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

  @Override
  public void markAsSentFailed(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENT_FAILED_TYPE, Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId, true));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

  @Override
  public void markAsSent(long messageId, boolean secure) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENT_TYPE | (secure ? Types.PUSH_MESSAGE_BIT | Types.SECURE_MESSAGE_BIT : 0), Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId, true));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

  @Override
  public void markAsRemoteDelete(long messageId) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    long threadId;

    boolean deletedAttachments = false;

    db.beginTransaction();
    try {
      ContentValues values = new ContentValues();
      values.put(REMOTE_DELETED, 1);
      values.putNull(BODY);
      values.putNull(QUOTE_BODY);
      values.putNull(QUOTE_AUTHOR);
      values.putNull(QUOTE_ATTACHMENT);
      values.putNull(QUOTE_TYPE);
      values.putNull(QUOTE_ID);
      values.putNull(LINK_PREVIEWS);
      values.putNull(SHARED_CONTACTS);
      db.update(TABLE_NAME, values, ID_WHERE, new String[] { String.valueOf(messageId) });

      deletedAttachments = SignalDatabase.attachments().deleteAttachmentsForMessage(messageId);
      SignalDatabase.mentions().deleteMentionsForMessage(messageId);
      SignalDatabase.messageLog().deleteAllRelatedToMessage(messageId, true);
      SignalDatabase.reactions().deleteReactions(new MessageId(messageId, true));
      deleteGroupStoryReplies(messageId);
      disassociateStoryQuotes(messageId);

      threadId = getThreadIdForMessage(messageId);
      SignalDatabase.threads().update(threadId, false);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId, true));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();

    if (deletedAttachments) {
      ApplicationDependencies.getDatabaseObserver().notifyAttachmentObservers();
    }
  }

  @Override
  public void markDownloadState(long messageId, long state) {
    SQLiteDatabase database     = databaseHelper.getSignalWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(STATUS, state);

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId + ""});
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId, true));
  }

  @Override
  public void markAsInsecure(long messageId) {
    updateMailboxBitmask(messageId, Types.SECURE_MESSAGE_BIT, 0, Optional.empty());
  }

  @Override
  public void markUnidentified(long messageId, boolean unidentified) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(UNIDENTIFIED, unidentified ? 1 : 0);

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
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
  public void markAsNotified(long id) {
    SQLiteDatabase database      = databaseHelper.getSignalWritableDatabase();
    ContentValues  contentValues = new ContentValues();

    contentValues.put(NOTIFIED, 1);
    contentValues.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(id)});
  }

  @Override
  public List<MarkedMessageInfo> setMessagesReadSince(long threadId, long sinceTimestamp) {
    if (sinceTimestamp == -1) {
      return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " <= 0 AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND (" + getOutgoingTypeClause() + ")))", new String[] { String.valueOf(threadId)});
    } else {
      return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " <= 0 AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND ( " + getOutgoingTypeClause() + " ))) AND " + DATE_RECEIVED + " <= ?", new String[]{ String.valueOf(threadId), String.valueOf(sinceTimestamp)});
    }
  }

  @Override
  public @NonNull List<MarkedMessageInfo> setGroupStoryMessagesReadSince(long threadId, long groupStoryId, long sinceTimestamp) {
    if (sinceTimestamp == -1) {
      return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " = ? AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND (" + getOutgoingTypeClause() + ")))", SqlUtil.buildArgs(threadId, groupStoryId));
    } else {
      return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " = ? AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND ( " + getOutgoingTypeClause() + " ))) AND " + DATE_RECEIVED + " <= ?", SqlUtil.buildArgs(threadId, groupStoryId, sinceTimestamp));
    }
  }

  @Override
  public @NonNull List<StoryType> getStoryTypes(@NonNull List<MessageId> messageIds) {
    List<Long> mmsMessages = messageIds.stream()
                                       .filter(MessageId::isMms)
                                       .map(MessageId::getId)
                                       .collect(java.util.stream.Collectors.toList());

    if (mmsMessages.isEmpty()) {
      return Collections.emptyList();
    }

    String[]                 projection  = SqlUtil.buildArgs(ID, STORY_TYPE);
    List<SqlUtil.Query>      queries     = SqlUtil.buildCollectionQuery(ID, mmsMessages);
    HashMap<Long, StoryType> storyTypes  = new HashMap<>();

    for (final SqlUtil.Query query : queries) {
      try (Cursor cursor = getWritableDatabase().query(TABLE_NAME, projection, query.getWhere(), query.getWhereArgs(), null, null, null)) {
        while (cursor != null && cursor.moveToNext()) {
          storyTypes.put(CursorUtil.requireLong(cursor, ID), StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE)));
        }
      }
    }

    return messageIds.stream().map(id -> {
      if (id.isMms() && storyTypes.containsKey(id.getId())) {
        return storyTypes.get(id.getId());
      } else {
        return StoryType.NONE;
      }
    }).collect(java.util.stream.Collectors.toList());
  }

  @Override
  public List<MarkedMessageInfo> setEntireThreadRead(long threadId) {
    return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " <= 0", new String[] { String.valueOf(threadId)});
  }

  @Override
  public List<MarkedMessageInfo> setAllMessagesRead() {
    return setMessagesRead(STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " <= 0 AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND (" + getOutgoingTypeClause() + ")))", null);
  }

  private List<MarkedMessageInfo> setMessagesRead(String where, String[] arguments) {
    SQLiteDatabase          database         = databaseHelper.getSignalWritableDatabase();
    List<MarkedMessageInfo> result           = new LinkedList<>();
    Cursor                  cursor           = null;
    RecipientId             releaseChannelId = SignalStore.releaseChannelValues().getReleaseChannelRecipientId();

    database.beginTransaction();

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, RECIPIENT_ID, DATE_SENT, MESSAGE_BOX, EXPIRES_IN, EXPIRE_STARTED, THREAD_ID, STORY_TYPE }, where, arguments, null, null, null);

      while(cursor != null && cursor.moveToNext()) {
        if (Types.isSecureType(CursorUtil.requireLong(cursor, MESSAGE_BOX))) {
          long           threadId       = CursorUtil.requireLong(cursor, THREAD_ID);
          RecipientId    recipientId    = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
          long           dateSent       = CursorUtil.requireLong(cursor, DATE_SENT);
          long           messageId      = CursorUtil.requireLong(cursor, ID);
          long           expiresIn      = CursorUtil.requireLong(cursor, EXPIRES_IN);
          long           expireStarted  = CursorUtil.requireLong(cursor, EXPIRE_STARTED);
          SyncMessageId  syncMessageId  = new SyncMessageId(recipientId, dateSent);
          ExpirationInfo expirationInfo = new ExpirationInfo(messageId, expiresIn, expireStarted, true);
          StoryType      storyType      = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));

          if (!recipientId.equals(releaseChannelId)) {
            result.add(new MarkedMessageInfo(threadId, syncMessageId, new MessageId(messageId, true), expirationInfo));
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

    return result;
  }

  @Override
  public @Nullable Pair<RecipientId, Long> getOldestUnreadMentionDetails(long threadId) {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
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
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
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
    SQLiteDatabase database         = databaseHelper.getSignalWritableDatabase();
    String         trimmedCondition = " NOT IN (SELECT " + MmsDatabase.ID + " FROM " + MmsDatabase.TABLE_NAME + ")";

    database.delete(GroupReceiptDatabase.TABLE_NAME, GroupReceiptDatabase.MMS_ID + trimmedCondition, null);

    String[] columns = new String[] { AttachmentDatabase.ROW_ID, AttachmentDatabase.UNIQUE_ID };
    String   where   = AttachmentDatabase.MMS_ID + trimmedCondition;

    try (Cursor cursor = database.query(AttachmentDatabase.TABLE_NAME, columns, where, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        SignalDatabase.attachments().deleteAttachment(new AttachmentId(cursor.getLong(0), cursor.getLong(1)));
      }
    }

    SignalDatabase.mentions().deleteAbandonedMentions();

    try (Cursor cursor = database.query(ThreadDatabase.TABLE_NAME, new String[] { ThreadDatabase.ID }, ThreadDatabase.EXPIRES_IN + " > 0", null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        SignalDatabase.threads().setLastScrolled(cursor.getLong(0), 0);
        SignalDatabase.threads().update(cursor.getLong(0), false);
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
        return Optional.empty();
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
    AttachmentDatabase attachmentDatabase = SignalDatabase.attachments();
    MentionDatabase    mentionDatabase    = SignalDatabase.mentions();
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
        int              distributionType   = SignalDatabase.threads().getDistributionType(threadId);
        String           mismatchDocument   = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.MISMATCHED_IDENTITIES));
        String           networkDocument    = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.NETWORK_FAILURE));
        StoryType        storyType          = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));
        ParentStoryId    parentStoryId      = ParentStoryId.deserialize(CursorUtil.requireLong(cursor, PARENT_STORY_ID));

        long              quoteId            = cursor.getLong(cursor.getColumnIndexOrThrow(QUOTE_ID));
        long              quoteAuthor        = cursor.getLong(cursor.getColumnIndexOrThrow(QUOTE_AUTHOR));
        String            quoteText          = cursor.getString(cursor.getColumnIndexOrThrow(QUOTE_BODY));
        int               quoteType          = cursor.getInt(cursor.getColumnIndexOrThrow(QUOTE_TYPE));
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

        Recipient                recipient       = Recipient.resolved(RecipientId.from(recipientId));
        Set<NetworkFailure>      networkFailures = new HashSet<>();
        Set<IdentityKeyMismatch> mismatches      = new HashSet<>();
        QuoteModel               quote           = null;

        if (quoteId > 0 && quoteAuthor > 0 && (!TextUtils.isEmpty(quoteText) || !quoteAttachments.isEmpty())) {
          quote = new QuoteModel(quoteId, RecipientId.from(quoteAuthor), quoteText, quoteMissing, quoteAttachments, quoteMentions, QuoteModel.Type.fromCode(quoteType));
        }

        if (!TextUtils.isEmpty(mismatchDocument)) {
          try {
            mismatches = JsonUtils.fromJson(mismatchDocument, IdentityKeyMismatchSet.class).getItems();
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        if (!TextUtils.isEmpty(networkDocument)) {
          try {
            networkFailures = JsonUtils.fromJson(networkDocument, NetworkFailureSet.class).getItems();
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        if (body != null && (Types.isGroupQuit(outboxType) || Types.isGroupUpdate(outboxType))) {
          return new OutgoingGroupUpdateMessage(recipient, new MessageGroupContext(body, Types.isGroupV2(outboxType)), attachments, timestamp, 0, false, quote, contacts, previews, mentions);
        } else if (Types.isExpirationTimerUpdate(outboxType)) {
          return new OutgoingExpirationUpdateMessage(recipient, timestamp, expiresIn);
        } else if (Types.isPaymentsNotification(outboxType)) {
          return new OutgoingPaymentsNotificationMessage(recipient, Objects.requireNonNull(body), timestamp, expiresIn);
        } else if (Types.isPaymentsRequestToActivate(outboxType)) {
          return new OutgoingRequestToActivatePaymentMessages(recipient, timestamp, expiresIn);
        } else if (Types.isPaymentsActivated(outboxType)) {
          return new OutgoingPaymentsActivatedMessages(recipient, timestamp, expiresIn);
        }

        GiftBadge giftBadge = null;
        if (body != null && Types.isGiftBadge(outboxType)) {
          giftBadge = GiftBadge.parseFrom(Base64.decode(body));
        }

        OutgoingMediaMessage message = new OutgoingMediaMessage(recipient,
                                                                body,
                                                                attachments,
                                                                timestamp,
                                                                subscriptionId,
                                                                expiresIn,
                                                                viewOnce,
                                                                distributionType,
                                                                storyType,
                                                                parentStoryId,
                                                                Types.isStoryReaction(outboxType),
                                                                quote,
                                                                contacts,
                                                                previews,
                                                                mentions,
                                                                networkFailures,
                                                                mismatches,
                                                                giftBadge);

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
          } else {
            previews.add(preview);
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

    boolean silentUpdate = (mailbox & Types.GROUP_UPDATE_BIT) > 0;

    contentValues.put(DATE_SENT, retrieved.getSentTimeMillis());
    contentValues.put(DATE_SERVER, retrieved.getServerTimeMillis());
    contentValues.put(RECIPIENT_ID, retrieved.getFrom().serialize());

    contentValues.put(MESSAGE_BOX, mailbox);
    contentValues.put(MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(CONTENT_LOCATION, contentLocation);
    contentValues.put(STATUS, Status.DOWNLOAD_INITIALIZED);
    contentValues.put(DATE_RECEIVED, retrieved.isPushMessage() ? retrieved.getReceivedTimeMillis() : generatePduCompatTimestamp(retrieved.getReceivedTimeMillis()));
    contentValues.put(PART_COUNT, retrieved.getAttachments().size());
    contentValues.put(SUBSCRIPTION_ID, retrieved.getSubscriptionId());
    contentValues.put(EXPIRES_IN, retrieved.getExpiresIn());
    contentValues.put(VIEW_ONCE, retrieved.isViewOnce() ? 1 : 0);
    contentValues.put(STORY_TYPE, retrieved.getStoryType().getCode());
    contentValues.put(PARENT_STORY_ID, retrieved.getParentStoryId() != null ? retrieved.getParentStoryId().serialize() : 0);
    contentValues.put(READ, (silentUpdate || retrieved.isExpirationUpdate()) ? 1 : 0);
    contentValues.put(UNIDENTIFIED, retrieved.isUnidentified());
    contentValues.put(SERVER_GUID, retrieved.getServerGuid());

    if (!contentValues.containsKey(DATE_SENT)) {
      contentValues.put(DATE_SENT, contentValues.getAsLong(DATE_RECEIVED));
    }

    List<Attachment> quoteAttachments = new LinkedList<>();

    if (retrieved.getQuote() != null) {
      contentValues.put(QUOTE_ID, retrieved.getQuote().getId());
      contentValues.put(QUOTE_BODY, retrieved.getQuote().getText().toString());
      contentValues.put(QUOTE_AUTHOR, retrieved.getQuote().getAuthor().serialize());
      contentValues.put(QUOTE_TYPE, retrieved.getQuote().getType().getCode());
      contentValues.put(QUOTE_MISSING, retrieved.getQuote().isOriginalMissing() ? 1 : 0);

      BodyRangeList mentionsList = MentionUtil.mentionsToBodyRangeList(retrieved.getQuote().getMentions());
      if (mentionsList != null) {
        contentValues.put(QUOTE_MENTIONS, mentionsList.toByteArray());
      }

      quoteAttachments = retrieved.getQuote().getAttachments();
    }

    if (retrieved.isPushMessage() && isDuplicate(retrieved, threadId)) {
      Log.w(TAG, "Ignoring duplicate media message (" + retrieved.getSentTimeMillis() + ")");
      return Optional.empty();
    }

    boolean updateThread       = retrieved.getStoryType() == StoryType.NONE;
    boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && Recipient.resolved(retrieved.getFrom()).isMuted();
    long    messageId          = insertMediaMessage(threadId,
                                                    retrieved.getBody(),
                                                    retrieved.getAttachments(),
                                                    quoteAttachments,
                                                    retrieved.getSharedContacts(),
                                                    retrieved.getLinkPreviews(),
                                                    retrieved.getMentions(),
                                                    retrieved.getMessageRanges(),
                                                    contentValues,
                                                    null,
                                                    updateThread,
                                                    !keepThreadArchived);

    boolean isNotStoryGroupReply = retrieved.getParentStoryId() == null || !retrieved.getParentStoryId().isGroupReply();
    if (!Types.isPaymentsActivated(mailbox) && !Types.isPaymentsRequestToActivate(mailbox) && !Types.isExpirationTimerUpdate(mailbox) && !retrieved.getStoryType().isStory() && isNotStoryGroupReply) {
      boolean incrementUnreadMentions = !retrieved.getMentions().isEmpty() && retrieved.getMentions().stream().anyMatch(m -> m.getRecipientId().equals(Recipient.self().getId()));
      SignalDatabase.threads().incrementUnread(threadId, 1, incrementUnreadMentions ? 1 : 0);
      SignalDatabase.threads().update(threadId, !keepThreadArchived);
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

    if (retrieved.isPaymentsNotification()) {
      type |= Types.SPECIAL_TYPE_PAYMENTS_NOTIFICATION;
    }

    if (retrieved.isActivatePaymentsRequest()) {
      type |= Types.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST;
    }

    if (retrieved.isPaymentsActivated()) {
      type |= Types.SPECIAL_TYPE_PAYMENTS_ACTIVATED;
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

    boolean hasSpecialType = false;
    if (retrieved.isStoryReaction()) {
      hasSpecialType = true;
      type |= Types.SPECIAL_TYPE_STORY_REACTION;
    }

    if (retrieved.getGiftBadge() != null) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }

      type |= Types.SPECIAL_TYPE_GIFT_BADGE;
    }

    if (retrieved.isPaymentsNotification()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= Types.SPECIAL_TYPE_PAYMENTS_NOTIFICATION;
    }

    if (retrieved.isActivatePaymentsRequest()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= Types.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST;
    }

    if (retrieved.isPaymentsActivated()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= Types.SPECIAL_TYPE_PAYMENTS_ACTIVATED;
    }

    return insertMessageInbox(retrieved, "", threadId, type);
  }

  public Pair<Long, Long> insertMessageInbox(@NonNull NotificationInd notification, int subscriptionId) {
    SQLiteDatabase       db             = databaseHelper.getSignalWritableDatabase();
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
    contentValues.put(DATE_RECEIVED, generatePduCompatTimestamp(System.currentTimeMillis()));
    contentValues.put(READ, Util.isDefaultSmsProvider(context) ? 0 : 1);
    contentValues.put(SUBSCRIPTION_ID, subscriptionId);

    if (!contentValues.containsKey(DATE_SENT))
      contentValues.put(DATE_SENT, contentValues.getAsLong(DATE_RECEIVED));

    long messageId = db.insert(TABLE_NAME, null, contentValues);

    return new Pair<>(messageId, threadId);
  }

  @Override
  public @NonNull InsertResult insertChatSessionRefreshedMessage(@NonNull RecipientId recipientId, long senderDeviceId, long sentTimestamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void insertBadDecryptMessage(@NonNull RecipientId recipientId, int senderDevice, long sentTimestamp, long receivedTimestamp, long threadId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void markIncomingNotificationReceived(long threadId) {
    notifyConversationListeners(threadId);

    if (org.thoughtcrime.securesms.util.Util.isDefaultSmsProvider(context)) {
      SignalDatabase.threads().incrementUnread(threadId, 1, 0);
    }

    SignalDatabase.threads().update(threadId, true);

    TrimThreadJob.enqueueAsync(threadId);
  }

  @Override
  public void markGiftRedemptionCompleted(long messageId) {
    markGiftRedemptionState(messageId, GiftBadge.RedemptionState.REDEEMED);
  }

  @Override
  public void markGiftRedemptionStarted(long messageId) {
    markGiftRedemptionState(messageId, GiftBadge.RedemptionState.STARTED);
  }

  @Override
  public void markGiftRedemptionFailed(long messageId) {
    markGiftRedemptionState(messageId, GiftBadge.RedemptionState.FAILED);
  }

  private void markGiftRedemptionState(long messageId, @NonNull GiftBadge.RedemptionState redemptionState) {
    String[] projection = SqlUtil.buildArgs(BODY, THREAD_ID);
    String   where      = "(" + MESSAGE_BOX + " & " + Types.SPECIAL_TYPES_MASK + " = " + Types.SPECIAL_TYPE_GIFT_BADGE + ") AND " +
                          ID + " = ?";
    String[] args       = SqlUtil.buildArgs(messageId);
    boolean  updated    = false;
    long     threadId   = -1;

    getWritableDatabase().beginTransaction();
    try (Cursor cursor = getWritableDatabase().query(TABLE_NAME, projection, where, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        GiftBadge     giftBadge     = GiftBadge.parseFrom(Base64.decode(CursorUtil.requireString(cursor, BODY)));
        GiftBadge     updatedBadge  = giftBadge.toBuilder().setRedemptionState(redemptionState).build();
        ContentValues contentValues = new ContentValues(1);

        contentValues.put(BODY, Base64.encodeBytes(updatedBadge.toByteArray()));

        updated  = getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE, args) > 0;
        threadId = CursorUtil.requireLong(cursor, THREAD_ID);

        getWritableDatabase().setTransactionSuccessful();
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed to mark gift badge " + redemptionState.name(), e, true);
    } finally {
      getWritableDatabase().endTransaction();
    }

    if (updated) {
      ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId, true));
      notifyConversationListeners(threadId);
    }
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
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    long type = Types.BASE_SENDING_TYPE;

    if (message.isSecure()) type |= (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT);
    if (forceSms)           type |= Types.MESSAGE_FORCE_SMS_BIT;

    if (message.isGroup()) {
      OutgoingGroupUpdateMessage outgoingGroupUpdateMessage = (OutgoingGroupUpdateMessage) message;
      if (outgoingGroupUpdateMessage.isV2Group()) {
        type |= Types.GROUP_V2_BIT | Types.GROUP_UPDATE_BIT;
        if (outgoingGroupUpdateMessage.isJustAGroupLeave()) {
          type |= Types.GROUP_LEAVE_BIT;
        }
      } else {
        MessageGroupContext.GroupV1Properties properties = outgoingGroupUpdateMessage.requireGroupV1Properties();
        if      (properties.isUpdate()) type |= Types.GROUP_UPDATE_BIT;
        else if (properties.isQuit())   type |= Types.GROUP_LEAVE_BIT;
      }
    }

    if (message.isExpirationUpdate()) {
      type |= Types.EXPIRATION_TIMER_UPDATE_BIT;
    }

    boolean hasSpecialType = false;
    if (message.isStoryReaction()) {
      hasSpecialType = true;
      type |= Types.SPECIAL_TYPE_STORY_REACTION;
    }

    if (message.getGiftBadge() != null) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }

      type |= Types.SPECIAL_TYPE_GIFT_BADGE;
    }

    if (message.isPaymentsNotification()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= Types.SPECIAL_TYPE_PAYMENTS_NOTIFICATION;
    }

    if (message.isRequestToActivatePayments()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= Types.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST;
    }

    if (message.isPaymentsActivated()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= Types.SPECIAL_TYPE_PAYMENTS_ACTIVATED;
    }

    Map<RecipientId, EarlyReceiptCache.Receipt> earlyDeliveryReceipts = earlyDeliveryReceiptCache.remove(message.getSentTimeMillis());

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
    contentValues.put(DELIVERY_RECEIPT_COUNT, Stream.of(earlyDeliveryReceipts.values()).mapToLong(EarlyReceiptCache.Receipt::getCount).sum());
    contentValues.put(RECEIPT_TIMESTAMP, Stream.of(earlyDeliveryReceipts.values()).mapToLong(EarlyReceiptCache.Receipt::getTimestamp).max().orElse(-1));
    contentValues.put(STORY_TYPE, message.getStoryType().getCode());
    contentValues.put(PARENT_STORY_ID, message.getParentStoryId() != null ? message.getParentStoryId().serialize() : 0);

    if (message.getRecipient().isSelf() && hasAudioAttachment(message.getAttachments())) {
      contentValues.put(VIEWED_RECEIPT_COUNT, 1L);
    }

    List<Attachment> quoteAttachments = new LinkedList<>();

    if (message.getOutgoingQuote() != null) {
      MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyAndMentionsWithPlaceholders(message.getOutgoingQuote().getText(), message.getOutgoingQuote().getMentions());

      contentValues.put(QUOTE_ID, message.getOutgoingQuote().getId());
      contentValues.put(QUOTE_AUTHOR, message.getOutgoingQuote().getAuthor().serialize());
      contentValues.put(QUOTE_BODY, updated.getBodyAsString());
      contentValues.put(QUOTE_TYPE, message.getOutgoingQuote().getType().getCode());
      contentValues.put(QUOTE_MISSING, message.getOutgoingQuote().isOriginalMissing() ? 1 : 0);

      BodyRangeList mentionsList = MentionUtil.mentionsToBodyRangeList(updated.getMentions());
      if (mentionsList != null) {
        contentValues.put(QUOTE_MENTIONS, mentionsList.toByteArray());
      }

      quoteAttachments.addAll(message.getOutgoingQuote().getAttachments());
    }

    MentionUtil.UpdatedBodyAndMentions updatedBodyAndMentions = MentionUtil.updateBodyAndMentionsWithPlaceholders(message.getBody(), message.getMentions());

    long messageId = insertMediaMessage(threadId, updatedBodyAndMentions.getBodyAsString(), message.getAttachments(), quoteAttachments, message.getSharedContacts(), message.getLinkPreviews(), updatedBodyAndMentions.getMentions(), null, contentValues, insertListener, false, false);

    if (message.getRecipient().isGroup()) {
      OutgoingGroupUpdateMessage outgoingGroupUpdateMessage = (message instanceof OutgoingGroupUpdateMessage) ? (OutgoingGroupUpdateMessage) message : null;

      GroupReceiptDatabase receiptDatabase = SignalDatabase.groupReceipts();
      Set<RecipientId>     members         = new HashSet<>();

      if (outgoingGroupUpdateMessage != null && outgoingGroupUpdateMessage.isV2Group()) {
        MessageGroupContext.GroupV2Properties groupV2Properties = outgoingGroupUpdateMessage.requireGroupV2Properties();
        members.addAll(Stream.of(groupV2Properties.getAllActivePendingAndRemovedMembers())
                             .distinct()
                             .map(uuid -> RecipientId.from(ServiceId.from(uuid)))
                             .toList());
        members.remove(Recipient.self().getId());
      } else {
        members.addAll(Stream.of(SignalDatabase.groups().getGroupMembers(message.getRecipient().requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF)).map(Recipient::getId).toList());
      }

      receiptDatabase.insert(members, messageId, defaultReceiptStatus, message.getSentTimeMillis());

      for (RecipientId recipientId : earlyDeliveryReceipts.keySet()) {
        receiptDatabase.update(recipientId, messageId, GroupReceiptDatabase.STATUS_DELIVERED, -1);
      }
    } else if (message.getRecipient().isDistributionList()) {
      GroupReceiptDatabase receiptDatabase = SignalDatabase.groupReceipts();
      List<RecipientId>    members         = SignalDatabase.distributionLists().getMembers(message.getRecipient().requireDistributionListId());

      receiptDatabase.insert(members, messageId, defaultReceiptStatus, message.getSentTimeMillis());

      for (RecipientId recipientId : earlyDeliveryReceipts.keySet()) {
        receiptDatabase.update(recipientId, messageId, GroupReceiptDatabase.STATUS_DELIVERED, -1);
      }
    }

    SignalDatabase.threads().updateLastSeenAndMarkSentAndLastScrolledSilenty(threadId);

    if (!message.getStoryType().isStory()) {
      if (message.getOutgoingQuote() == null) {
        ApplicationDependencies.getDatabaseObserver().notifyMessageInsertObservers(threadId, new MessageId(messageId, true));
      } else {
        ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId);
      }
    } else {
      ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(message.getRecipient().getId());
    }

    notifyConversationListListeners();

    TrimThreadJob.enqueueAsync(threadId);

    return messageId;
  }

  private boolean hasAudioAttachment(@NonNull List<Attachment> attachments) {
    for (Attachment attachment : attachments) {
      if (MediaUtil.isAudio(attachment)) {
        return true;
      }
    }

    return false;
  }

  private long insertMediaMessage(long threadId,
                                  @Nullable String body,
                                  @NonNull List<Attachment> attachments,
                                  @NonNull List<Attachment> quoteAttachments,
                                  @NonNull List<Contact> sharedContacts,
                                  @NonNull List<LinkPreview> linkPreviews,
                                  @NonNull List<Mention> mentions,
                                  @Nullable BodyRangeList messageRanges,
                                  @NonNull ContentValues contentValues,
                                  @Nullable InsertListener insertListener,
                                  boolean updateThread,
                                  boolean unarchive)
      throws MmsException
  {
    SQLiteDatabase     db              = databaseHelper.getSignalWritableDatabase();
    AttachmentDatabase partsDatabase   = SignalDatabase.attachments();
    MentionDatabase    mentionDatabase = SignalDatabase.mentions();

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

    if (messageRanges != null) {
      contentValues.put(MESSAGE_RANGES, messageRanges.toByteArray());
    }

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

        SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
        int rows = database.update(TABLE_NAME, contactValues, ID + " = ?", new String[]{ String.valueOf(messageId) });

        if (rows <= 0) {
          Log.w(TAG, "Failed to update message with shared contact data.");
        }
      }

      if (!TextUtils.isEmpty(serializedPreviews)) {
        ContentValues contactValues = new ContentValues();
        contactValues.put(LINK_PREVIEWS, serializedPreviews);

        SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
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

      long contentValuesThreadId = contentValues.getAsLong(THREAD_ID);

      if (updateThread) {
        SignalDatabase.threads().setLastScrolled(contentValuesThreadId, 0);
        SignalDatabase.threads().update(threadId, unarchive);
      }
    }
  }

  @Override
  public boolean deleteMessage(long messageId) {
    Log.d(TAG, "deleteMessage(" + messageId + ")");

    long               threadId           = getThreadIdForMessage(messageId);
    AttachmentDatabase attachmentDatabase = SignalDatabase.attachments();
    attachmentDatabase.deleteAttachmentsForMessage(messageId);

    GroupReceiptDatabase groupReceiptDatabase = SignalDatabase.groupReceipts();
    groupReceiptDatabase.deleteRowsForMessage(messageId);

    MentionDatabase mentionDatabase = SignalDatabase.mentions();
    mentionDatabase.deleteMentionsForMessage(messageId);

    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    database.delete(TABLE_NAME, ID_WHERE, new String[] {messageId+""});

    SignalDatabase.threads().setLastScrolled(threadId, 0);
    boolean threadDeleted = SignalDatabase.threads().update(threadId, false);
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
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = DATE_SENT + " = ? AND " + RECIPIENT_ID + " = ? AND " + THREAD_ID + " = ?";
    String[]       args  = SqlUtil.buildArgs(message.getSentTimeMillis(), message.getFrom().serialize(), threadId);

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { "1" }, query, args, null, null, null, "1")) {
      return cursor.moveToFirst();
    }
  }

  @Override
  public boolean isSent(long messageId) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
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
  public Set<Long> getAllRateLimitedMessageIds() {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         where = "(" + MESSAGE_BOX + " & " + Types.TOTAL_MASK + " & " + Types.MESSAGE_RATE_LIMITED_BIT + ") > 0";

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
    return rawQuery(
        SqlUtil.appendArg(MMS_PROJECTION, EXPORT_STATE),
        getInsecureMessageClause() + " AND NOT " + EXPORTED,
        null,
        false,
        limit
    );
  }

  @Override
  public long getUnexportedInsecureMessagesEstimatedSize() {
    Cursor messageTextSize = SQLiteDatabaseExtensionsKt.select(getReadableDatabase(), "SUM(LENGTH(" + BODY + "))")
                                                       .from(TABLE_NAME)
                                                       .where(getInsecureMessageClause() + " AND " + EXPORTED + " < ?", MessageExportStatus.EXPORTED)
                                                       .run();

    long bodyTextSize = CursorExtensionsKt.readToSingleLong(messageTextSize);

    String select   = "SUM(" + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.SIZE + ") AS s";
    String fromJoin = TABLE_NAME + " INNER JOIN " + AttachmentDatabase.TABLE_NAME + " ON " + TABLE_NAME + "." + ID + " = " + AttachmentDatabase.TABLE_NAME + "." + AttachmentDatabase.MMS_ID;
    String where    = getInsecureMessageClause() + " AND " + EXPORTED + " < " + MessageExportStatus.EXPORTED.serialize();

    long fileSize = CursorExtensionsKt.readToSingleLong(getReadableDatabase().rawQuery("SELECT " + select + " FROM " + fromJoin + " WHERE " + where, null));

    return bodyTextSize + fileSize;
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

      SignalDatabase.attachments().deleteAbandonedAttachmentFiles();

      setTransactionSuccessful();
    } finally {
      endTransaction();
    }
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

    try (Cursor cursor = db.query(TABLE_NAME, new String[] {ID}, where, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        deleteMessage(cursor.getLong(0));
      }
    }
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
    String         where = THREAD_ID + " NOT IN (SELECT _id FROM " + ThreadDatabase.TABLE_NAME + ")";

    int deletes = db.delete(TABLE_NAME, where, null);
    if (deletes > 0) {
      Log.i(TAG, "Deleted " + deletes + " abandoned messages");
    }
  }

  @Override
  public void deleteRemotelyDeletedStory(long messageId) {
    try (Cursor cursor = getMessageCursor(messageId)) {
      if (cursor.moveToFirst() && CursorUtil.requireBoolean(cursor, REMOTE_DELETED)) {
        deleteMessage(messageId);
      } else {
        Log.i(TAG, "Unable to delete remotely deleted story: " + messageId);
      }
    }
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
    SignalDatabase.attachments().deleteAllAttachments();
    SignalDatabase.groupReceipts().deleteAllRows();
    SignalDatabase.mentions().deleteAllMentions();

    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    database.delete(TABLE_NAME, null, null);
  }

  @Override
  public @Nullable ViewOnceExpirationInfo getNearestExpiringViewOnceMessage() {
    SQLiteDatabase       db                = databaseHelper.getSignalReadableDatabase();
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
    databaseHelper.getSignalWritableDatabase().beginTransaction();
    return databaseHelper.getSignalWritableDatabase();
  }

  @Override
  public void setTransactionSuccessful() {
    databaseHelper.getSignalWritableDatabase().setTransactionSuccessful();
  }

  @Override
  public void endTransaction() {
    databaseHelper.getSignalWritableDatabase().endTransaction();
  }

  public static Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public static OutgoingMessageReader readerFor(OutgoingMediaMessage message, long threadId) {
    return new OutgoingMessageReader(message, threadId);
  }

  @Override
  public void remapRecipient(@NonNull RecipientId fromId, @NonNull RecipientId toId) {

  }

  @Override
  public void remapThread(long fromId, long toId) {
    ContentValues values = new ContentValues();
    values.put(SmsDatabase.THREAD_ID, toId);
    getWritableDatabase().update(TABLE_NAME, values, THREAD_ID + " = ?", SqlUtil.buildArgs(fromId));
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
                                       Collections.emptySet(),
                                       Collections.emptySet(),
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
                                                     quoteMentions,
                                                     message.getOutgoingQuote().getType()) :
                                           null,
                                       message.getSharedContacts(),
                                       message.getLinkPreviews(),
                                       false,
                                       Collections.emptyList(),
                                       false,
                                       false,
                                       0,
                                       0,
                                       -1,
                                       null,
                                       message.getStoryType(),
                                       message.getParentStoryId(),
                                       message.getGiftBadge(),
                                       null);
    }
  }

  /**
   * MessageRecord reader which implements the Iterable interface. This allows it to
   * be used with many Kotlin Extension Functions as well as with for-each loops.
   *
   * Note that it's the responsibility of the developer using the reader to ensure that:
   *
   * 1. They only utilize one of the two interfaces (legacy or iterator)
   * 1. They close this reader after use, preferably via try-with-resources or a use block.
   */
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

    @Override
    public @NonNull MessageExportState getMessageExportStateForCurrentRecord() {
      byte[] messageExportState = CursorUtil.requireBlob(cursor, MmsDatabase.EXPORT_STATE);
      if (messageExportState == null) {
        return MessageExportState.getDefaultInstance();
      }

      try {
        return MessageExportState.parseFrom(messageExportState);
      } catch (InvalidProtocolBufferException e) {
        return MessageExportState.getDefaultInstance();
      }
    }

    public int getCount() {
      if (cursor == null) return 0;
      else                return cursor.getCount();
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

      String        contentLocation      = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.CONTENT_LOCATION));
      String        transactionId        = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.TRANSACTION_ID));
      long          messageSize          = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_SIZE));
      long          expiry               = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.EXPIRY));
      int           status               = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.STATUS));
      int           deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.DELIVERY_RECEIPT_COUNT));
      int           readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.READ_RECEIPT_COUNT));
      int           subscriptionId       = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.SUBSCRIPTION_ID));
      int           viewedReceiptCount   = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsColumns.VIEWED_RECEIPT_COUNT));
      long          receiptTimestamp     = CursorUtil.requireLong(cursor, MmsSmsColumns.RECEIPT_TIMESTAMP);
      StoryType     storyType            = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));
      ParentStoryId parentStoryId        = ParentStoryId.deserialize(CursorUtil.requireLong(cursor, PARENT_STORY_ID));
      String        body                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.BODY));

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

      GiftBadge giftBadge = null;
      if (body != null && Types.isGiftBadge(mailbox)) {
        try {
          giftBadge = GiftBadge.parseFrom(Base64.decode(body));
        } catch (IOException e) {
          Log.w(TAG, "Error parsing gift badge", e);
        }
      }

      return new NotificationMmsMessageRecord(id, recipient, recipient,
                                              addressDeviceId, dateSent, dateReceived, deliveryReceiptCount, threadId,
                                              contentLocationBytes, messageSize, expiry, status,
                                              transactionIdBytes, mailbox, subscriptionId, slideDeck,
                                              readReceiptCount, viewedReceiptCount, receiptTimestamp, storyType,
                                              parentStoryId, giftBadge);
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
      boolean              mentionsSelf         = CursorUtil.requireBoolean(cursor, MENTIONS_SELF);
      long                 notifiedTimestamp    = CursorUtil.requireLong(cursor, NOTIFIED_TIMESTAMP);
      int                  viewedReceiptCount   = cursor.getInt(cursor.getColumnIndexOrThrow(MmsSmsColumns.VIEWED_RECEIPT_COUNT));
      long                 receiptTimestamp     = CursorUtil.requireLong(cursor, MmsSmsColumns.RECEIPT_TIMESTAMP);
      byte[]               messageRangesData    = CursorUtil.requireBlob(cursor, MESSAGE_RANGES);
      StoryType            storyType            = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));
      ParentStoryId        parentStoryId        = ParentStoryId.deserialize(CursorUtil.requireLong(cursor, PARENT_STORY_ID));

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount = 0;

        if (MmsSmsColumns.Types.isOutgoingMessageType(box) && !storyType.isStory()) {
          viewedReceiptCount = 0;
        }
      }

      Recipient                recipient          = Recipient.live(RecipientId.from(recipientId)).get();
      Set<IdentityKeyMismatch> mismatches         = getMismatchedIdentities(mismatchDocument);
      Set<NetworkFailure>      networkFailures    = getFailures(networkDocument);
      List<DatabaseAttachment> attachments        = SignalDatabase.attachments().getAttachments(cursor);
      List<Contact>            contacts           = getSharedContacts(cursor, attachments);
      Set<Attachment>          contactAttachments = Stream.of(contacts).map(Contact::getAvatarAttachment).withoutNulls().collect(Collectors.toSet());
      List<LinkPreview>        previews           = getLinkPreviews(cursor, attachments);
      Set<Attachment>          previewAttachments = Stream.of(previews).filter(lp -> lp.getThumbnail().isPresent()).map(lp -> lp.getThumbnail().get()).collect(Collectors.toSet());
      SlideDeck                slideDeck          = buildSlideDeck(context, Stream.of(attachments).filterNot(contactAttachments::contains).filterNot(previewAttachments::contains).toList());
      Quote                    quote              = getQuote(cursor);
      BodyRangeList            messageRanges      = null;

      try {
        if (messageRangesData != null) {
          messageRanges = BodyRangeList.parseFrom(messageRangesData);
        }
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "Error parsing message ranges", e);
      }

      GiftBadge giftBadge = null;
      if (body != null && Types.isGiftBadge(box)) {
        try {
          giftBadge = GiftBadge.parseFrom(Base64.decode(body));
        } catch (IOException e) {
          Log.w(TAG, "Error parsing gift badge", e);
        }
      }

      return new MediaMmsMessageRecord(id, recipient, recipient,
                                       addressDeviceId, dateSent, dateReceived, dateServer, deliveryReceiptCount,
                                       threadId, body, slideDeck, partCount, box, mismatches,
                                       networkFailures, subscriptionId, expiresIn, expireStarted,
                                       isViewOnce, readReceiptCount, quote, contacts, previews, unidentified, Collections.emptyList(),
                                       remoteDelete, mentionsSelf, notifiedTimestamp, viewedReceiptCount, receiptTimestamp, messageRanges,
                                       storyType, parentStoryId, giftBadge, null);
    }

    private Set<IdentityKeyMismatch> getMismatchedIdentities(String document) {
      if (!TextUtils.isEmpty(document)) {
        try {
          return JsonUtils.fromJson(document, IdentityKeyMismatchSet.class).getItems();
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      return Collections.emptySet();
    }

    private Set<NetworkFailure> getFailures(String document) {
      if (!TextUtils.isEmpty(document)) {
        try {
          return JsonUtils.fromJson(document, NetworkFailureSet.class).getItems();
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
        }
      }

      return Collections.emptySet();
    }

    public static SlideDeck buildSlideDeck(@NonNull Context context, @NonNull List<DatabaseAttachment> attachments) {
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
      int                        quoteType        = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.QUOTE_TYPE));
      boolean                    quoteMissing     = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.QUOTE_MISSING)) == 1;
      List<Mention>              quoteMentions    = parseQuoteMentions(context, cursor);
      List<DatabaseAttachment>   attachments      = SignalDatabase.attachments().getAttachments(cursor);
      List<? extends Attachment> quoteAttachments = Stream.of(attachments).filter(Attachment::isQuote).toList();
      SlideDeck                  quoteDeck        = new SlideDeck(context, quoteAttachments);

      if (quoteId > 0 && quoteAuthor > 0) {
        if (quoteText != null && !quoteMentions.isEmpty()) {
          MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, quoteText, quoteMentions);

          quoteText     = updated.getBody();
          quoteMentions = updated.getMentions();
        }

        return new Quote(quoteId, RecipientId.from(quoteAuthor), quoteText, quoteMissing, quoteDeck, quoteMentions, QuoteModel.Type.fromCode(quoteType));
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

    @NonNull
    @Override
    public Iterator<MessageRecord> iterator() {
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

  private long generatePduCompatTimestamp(long time) {
    return time - (time % 1000);
  }
}

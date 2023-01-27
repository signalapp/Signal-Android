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
import android.text.SpannableString;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.android.mms.pdu_alt.NotificationInd;
import com.google.android.mms.pdu_alt.PduHeaders;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.signal.core.util.CursorExtensionsKt;
import org.signal.core.util.CursorUtil;
import org.signal.core.util.SQLiteDatabaseExtensionsKt;
import org.signal.core.util.SqlUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.MmsNotificationAttachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.conversation.MessageStyler;
import org.thoughtcrime.securesms.database.documents.Document;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchSet;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.documents.NetworkFailureSet;
import org.thoughtcrime.securesms.database.model.DisplayRecord;
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageExportStatus;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ParentStoryId;
import org.thoughtcrime.securesms.database.model.Quote;
import org.thoughtcrime.securesms.database.model.StoryResult;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.database.model.StoryViewState;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge;
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExportState;
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent;
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.insights.InsightsConstants;
import org.thoughtcrime.securesms.jobs.OptimizeMessageSearchIndexJob;
import org.thoughtcrime.securesms.jobs.TrimThreadJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.notifications.v2.DefaultMessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.revealable.ViewOnceExpirationInfo;
import org.thoughtcrime.securesms.revealable.ViewOnceUtil;
import org.thoughtcrime.securesms.sms.IncomingGroupUpdateMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.stories.Stories;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.io.Closeable;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.thoughtcrime.securesms.contactshare.Contact.Avatar;

public class MessageTable extends DatabaseTable implements MessageTypes, RecipientIdDatabaseReference, ThreadIdDatabaseReference  {

  private static final String TAG = Log.tag(MessageTable.class);

  public static final String TABLE_NAME             = "message";
  public static final String ID                     = "_id";
  public static final String DATE_SENT              = "date_sent";
  public static final String DATE_RECEIVED          = "date_received";
  public static final String TYPE                   = "type";
  public static final String DATE_SERVER            = "date_server";
  public static final String THREAD_ID              = "thread_id";
  public static final String READ                   = "read";
  public static final String BODY                   = "body";
  public static final String RECIPIENT_ID           = "recipient_id";
  public static final String RECIPIENT_DEVICE_ID    = "recipient_device_id";
  public static final String DELIVERY_RECEIPT_COUNT = "delivery_receipt_count";
  public static final String READ_RECEIPT_COUNT     = "read_receipt_count";
  public static final String VIEWED_RECEIPT_COUNT   = "viewed_receipt_count";
  public static final String MISMATCHED_IDENTITIES  = "mismatched_identities";
  public static final String SMS_SUBSCRIPTION_ID    = "subscription_id";
  public static final String EXPIRES_IN             = "expires_in";
  public static final String EXPIRE_STARTED         = "expire_started";
  public static final String NOTIFIED               = "notified";
  public static final String NOTIFIED_TIMESTAMP     = "notified_timestamp";
  public static final String UNIDENTIFIED           = "unidentified";
  public static final String REACTIONS_UNREAD       = "reactions_unread";
  public static final String REACTIONS_LAST_SEEN    = "reactions_last_seen";
  public static final String REMOTE_DELETED         = "remote_deleted";
  public static final String SERVER_GUID            = "server_guid";
  public static final String RECEIPT_TIMESTAMP      = "receipt_timestamp";
  public static final String EXPORT_STATE           = "export_state";
  public static final String EXPORTED               = "exported";
  public static final String MMS_CONTENT_LOCATION   = "ct_l";
  public static final String MMS_EXPIRY             = "exp";
  public static final String MMS_MESSAGE_TYPE       = "m_type";
  public static final String MMS_MESSAGE_SIZE       = "m_size";
  public static final String MMS_STATUS             = "st";
  public static final String MMS_TRANSACTION_ID     = "tr_id";
  public static final String NETWORK_FAILURES       = "network_failures";
  public static final String QUOTE_ID               = "quote_id";
  public static final String QUOTE_AUTHOR           = "quote_author";
  public static final String QUOTE_BODY             = "quote_body";
  public static final String QUOTE_MISSING          = "quote_missing";
  public static final String QUOTE_BODY_RANGES      = "quote_mentions";
  public static final String QUOTE_TYPE             = "quote_type";
  public static final String SHARED_CONTACTS        = "shared_contacts";
  public static final String LINK_PREVIEWS          = "link_previews";
  public static final String MENTIONS_SELF          = "mentions_self";
  public static final String MESSAGE_RANGES         = "message_ranges";
  public static final String VIEW_ONCE              = "view_once";
  public static final String STORY_TYPE             = "story_type";
  public static final String PARENT_STORY_ID        = "parent_story_id";
  public static final String SCHEDULED_DATE         = "scheduled_date";

  public static class Status {
    public static final int STATUS_NONE      = -1;
    public static final int STATUS_COMPLETE  = 0;
    public static final int STATUS_PENDING   = 0x20;
    public static final int STATUS_FAILED    = 0x40;
  }

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID                     + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                                                                  DATE_SENT              + " INTEGER NOT NULL, " +
                                                                                  DATE_RECEIVED          + " INTEGER NOT NULL, " +
                                                                                  DATE_SERVER            + " INTEGER DEFAULT -1, " +
                                                                                  THREAD_ID              + " INTEGER NOT NULL REFERENCES " + ThreadTable.TABLE_NAME + " (" + ThreadTable.ID + ") ON DELETE CASCADE, " +
                                                                                  RECIPIENT_ID           + " INTEGER NOT NULL REFERENCES " + RecipientTable.TABLE_NAME + " (" + RecipientTable.ID + ") ON DELETE CASCADE, " +
                                                                                  RECIPIENT_DEVICE_ID    + " INTEGER, " +
                                                                                  TYPE                   + " INTEGER NOT NULL, " +
                                                                                  BODY                   + " TEXT, " +
                                                                                  READ                   + " INTEGER DEFAULT 0, " +
                                                                                  MMS_CONTENT_LOCATION   + " TEXT, " +
                                                                                  MMS_EXPIRY             + " INTEGER, " +
                                                                                  MMS_MESSAGE_TYPE       + " INTEGER, " +
                                                                                  MMS_MESSAGE_SIZE       + " INTEGER, " +
                                                                                  MMS_STATUS             + " INTEGER, " +
                                                                                  MMS_TRANSACTION_ID     + " TEXT, " +
                                                                                  SMS_SUBSCRIPTION_ID    + " INTEGER DEFAULT -1, " +
                                                                                  RECEIPT_TIMESTAMP      + " INTEGER DEFAULT -1, " +
                                                                                  DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " +
                                                                                  READ_RECEIPT_COUNT     + " INTEGER DEFAULT 0, " +
                                                                                  VIEWED_RECEIPT_COUNT   + " INTEGER DEFAULT 0, " +
                                                                                  MISMATCHED_IDENTITIES  + " TEXT DEFAULT NULL, " +
                                                                                  NETWORK_FAILURES       + " TEXT DEFAULT NULL," +
                                                                                  EXPIRES_IN             + " INTEGER DEFAULT 0, " +
                                                                                  EXPIRE_STARTED         + " INTEGER DEFAULT 0, " +
                                                                                  NOTIFIED               + " INTEGER DEFAULT 0, " +
                                                                                  QUOTE_ID               + " INTEGER DEFAULT 0, " +
                                                                                  QUOTE_AUTHOR           + " INTEGER DEFAULT 0, " +
                                                                                  QUOTE_BODY             + " TEXT DEFAULT NULL, " +
                                                                                  QUOTE_MISSING          + " INTEGER DEFAULT 0, " +
                                                                                  QUOTE_BODY_RANGES      + " BLOB DEFAULT NULL," +
                                                                                  QUOTE_TYPE             + " INTEGER DEFAULT 0," +
                                                                                  SHARED_CONTACTS        + " TEXT DEFAULT NULL, " +
                                                                                  UNIDENTIFIED           + " INTEGER DEFAULT 0, " +
                                                                                  LINK_PREVIEWS          + " TEXT DEFAULT NULL, " +
                                                                                  VIEW_ONCE              + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS_UNREAD       + " INTEGER DEFAULT 0, " +
                                                                                  REACTIONS_LAST_SEEN    + " INTEGER DEFAULT -1, " +
                                                                                  REMOTE_DELETED         + " INTEGER DEFAULT 0, " +
                                                                                  MENTIONS_SELF          + " INTEGER DEFAULT 0, " +
                                                                                  NOTIFIED_TIMESTAMP     + " INTEGER DEFAULT 0, " +
                                                                                  SERVER_GUID            + " TEXT DEFAULT NULL, " +
                                                                                  MESSAGE_RANGES         + " BLOB DEFAULT NULL, " +
                                                                                  STORY_TYPE             + " INTEGER DEFAULT 0, " +
                                                                                  PARENT_STORY_ID        + " INTEGER DEFAULT 0, " +
                                                                                  EXPORT_STATE           + " BLOB DEFAULT NULL, " +
                                                                                  EXPORTED               + " INTEGER DEFAULT 0, " +
                                                                                  SCHEDULED_DATE         + " INTEGER DEFAULT -1);";

  private static final String INDEX_THREAD_DATE                 = "mms_thread_date_index";
  private static final String INDEX_THREAD_STORY_SCHEDULED_DATE = "mms_thread_story_parent_story_scheduled_date_index";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS mms_read_and_notified_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + NOTIFIED + "," + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_type_index ON " + TABLE_NAME + " (" + TYPE + ");",
    "CREATE INDEX IF NOT EXISTS mms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ", " + RECIPIENT_ID + ", " + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_date_server_index ON " + TABLE_NAME + " (" + DATE_SERVER + ");",
    "CREATE INDEX IF NOT EXISTS " + INDEX_THREAD_DATE + " ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");",
    "CREATE INDEX IF NOT EXISTS mms_reactions_unread_index ON " + TABLE_NAME + " (" + REACTIONS_UNREAD + ");",
    "CREATE INDEX IF NOT EXISTS mms_story_type_index ON " + TABLE_NAME + " (" + STORY_TYPE + ");",
    "CREATE INDEX IF NOT EXISTS mms_parent_story_id_index ON " + TABLE_NAME + " (" + PARENT_STORY_ID + ");",
    "CREATE INDEX IF NOT EXISTS " + INDEX_THREAD_STORY_SCHEDULED_DATE + " ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + "," + STORY_TYPE + "," + PARENT_STORY_ID + "," + SCHEDULED_DATE + ");",
    "CREATE INDEX IF NOT EXISTS message_quote_id_quote_author_scheduled_date_index ON " + TABLE_NAME + " (" + QUOTE_ID + ", " + QUOTE_AUTHOR + ", " + SCHEDULED_DATE + ");",
    "CREATE INDEX IF NOT EXISTS mms_exported_index ON " + TABLE_NAME + " (" + EXPORTED + ");",
    "CREATE INDEX IF NOT EXISTS mms_id_type_payment_transactions_index ON " + TABLE_NAME + " (" + ID + "," + TYPE + ") WHERE " + TYPE + " & " + MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION + " != 0;"
  };

  private static final String[] MMS_PROJECTION_BASE = new String[] {
      MessageTable.TABLE_NAME + "." + ID + " AS " + ID,
      THREAD_ID,
      DATE_SENT,
      DATE_RECEIVED,
      DATE_SERVER,
      TYPE,
      READ,
      MMS_CONTENT_LOCATION,
      MMS_EXPIRY,
      MMS_MESSAGE_TYPE,
      MMS_MESSAGE_SIZE,
      MMS_STATUS,
      MMS_TRANSACTION_ID,
      BODY,
      RECIPIENT_ID,
      RECIPIENT_DEVICE_ID,
      DELIVERY_RECEIPT_COUNT,
      READ_RECEIPT_COUNT,
      MISMATCHED_IDENTITIES,
      NETWORK_FAILURES,
      SMS_SUBSCRIPTION_ID,
      EXPIRES_IN,
      EXPIRE_STARTED,
      NOTIFIED,
      QUOTE_ID,
      QUOTE_AUTHOR,
      QUOTE_BODY,
      QUOTE_TYPE,
      QUOTE_MISSING,
      QUOTE_BODY_RANGES,
      SHARED_CONTACTS,
      LINK_PREVIEWS,
      UNIDENTIFIED,
      VIEW_ONCE,
      REACTIONS_UNREAD,
      REACTIONS_LAST_SEEN,
      REMOTE_DELETED,
      MENTIONS_SELF,
      NOTIFIED_TIMESTAMP,
      VIEWED_RECEIPT_COUNT,
      RECEIPT_TIMESTAMP,
      MESSAGE_RANGES,
      STORY_TYPE,
      PARENT_STORY_ID,
      SCHEDULED_DATE,
  };

  private static final String[] MMS_PROJECTION = SqlUtil.appendArg(MMS_PROJECTION_BASE, "NULL AS " + AttachmentTable.ATTACHMENT_JSON_ALIAS);

  private static final String[] MMS_PROJECTION_WITH_ATTACHMENTS = SqlUtil.appendArg(MMS_PROJECTION_BASE, "json_group_array(json_object(" +
      "'" + AttachmentTable.ROW_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.ROW_ID + ", " +
      "'" + AttachmentTable.UNIQUE_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.UNIQUE_ID + ", " +
      "'" + AttachmentTable.MMS_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + ", " +
      "'" + AttachmentTable.SIZE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.SIZE + ", " +
      "'" + AttachmentTable.FILE_NAME + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.FILE_NAME + ", " +
      "'" + AttachmentTable.DATA + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.DATA + ", " +
      "'" + AttachmentTable.CONTENT_TYPE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_TYPE + ", " +
      "'" + AttachmentTable.CDN_NUMBER + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CDN_NUMBER + ", " +
      "'" + AttachmentTable.CONTENT_LOCATION + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.CONTENT_LOCATION + ", " +
      "'" + AttachmentTable.FAST_PREFLIGHT_ID + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.FAST_PREFLIGHT_ID + "," +
      "'" + AttachmentTable.VOICE_NOTE + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VOICE_NOTE + "," +
      "'" + AttachmentTable.BORDERLESS + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.BORDERLESS + "," +
      "'" + AttachmentTable.VIDEO_GIF + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.VIDEO_GIF + "," +
      "'" + AttachmentTable.WIDTH + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.WIDTH + "," +
      "'" + AttachmentTable.HEIGHT + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.HEIGHT + "," +
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
      "'" + AttachmentTable.UPLOAD_TIMESTAMP + "', " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.UPLOAD_TIMESTAMP +
      ")) AS " + AttachmentTable.ATTACHMENT_JSON_ALIAS);

  private static final String   THREAD_ID_WHERE      = THREAD_ID + " = ?";
  private static final String[] THREAD_ID_PROJECTION = new String[] { THREAD_ID };
  private static final String   IS_STORY_CLAUSE      = STORY_TYPE + " > 0 AND " + REMOTE_DELETED + " = 0";
  private static final String   RAW_ID_WHERE         = TABLE_NAME + "._id = ?";

  private static final String SNIPPET_QUERY = "SELECT " + MessageTable.ID + ", " + MessageTable.TYPE + ", " + MessageTable.DATE_RECEIVED + " FROM " + MessageTable.TABLE_NAME + " " +
                                              "WHERE " + MessageTable.THREAD_ID + " = ? AND " +
                                                MessageTable.TYPE + " & " + MessageTypes.GROUP_V2_LEAVE_BITS + " != " + MessageTypes.GROUP_V2_LEAVE_BITS + " AND " +
                                                MessageTable.STORY_TYPE + " = 0 AND " +
                                                MessageTable.PARENT_STORY_ID + " <= 0 AND " +
                                                MessageTable.SCHEDULED_DATE + " = -1 AND " +
                                                MessageTable.TYPE + " NOT IN (" + MessageTypes.PROFILE_CHANGE_TYPE + ", " + MessageTypes.GV1_MIGRATION_TYPE + ", " + MessageTypes.CHANGE_NUMBER_TYPE + ", " + MessageTypes.BOOST_REQUEST_TYPE + ", " + MessageTypes.SMS_EXPORT_TYPE + ") " +
                                              "ORDER BY " + MessageTable.DATE_RECEIVED + " DESC " +
                                              "LIMIT 1";

  private final EarlyReceiptCache earlyDeliveryReceiptCache = new EarlyReceiptCache("MmsDelivery");

  public MessageTable(Context context, SignalDatabase databaseHelper) {
    super(context, databaseHelper);
  }

  public @Nullable RecipientId getOldestGroupUpdateSender(long threadId, long minimumDateReceived) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String[] columns = new String[]{RECIPIENT_ID};
    String   query   = THREAD_ID + " = ? AND " + TYPE + " & ? AND " + DATE_RECEIVED + " >= ?";
    long     type    = MessageTypes.SECURE_MESSAGE_BIT | MessageTypes.PUSH_MESSAGE_BIT | MessageTypes.GROUP_UPDATE_BIT | MessageTypes.BASE_INBOX_TYPE;
    String[] args    = new String[]{String.valueOf(threadId), String.valueOf(type), String.valueOf(minimumDateReceived)};
    String   limit   = "1";

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, args, null, null, limit)) {
      if (cursor.moveToFirst()) {
        return RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
      }
    }

    return null;
  }

  public Cursor getExpirationStartedMessages() {
    String where = EXPIRE_STARTED + " > 0";
    return rawQuery(where, null);
  }

  public Cursor getMessageCursor(long messageId) {
    return internalGetMessage(messageId);
  }

  public boolean hasReceivedAnyCallsSince(long threadId, long timestamp) {
    SQLiteDatabase db            = databaseHelper.getSignalReadableDatabase();
    String[]       projection    = SqlUtil.buildArgs(TYPE);
    String         selection     = THREAD_ID + " = ? AND " + DATE_RECEIVED  + " > ? AND (" + TYPE + " = ? OR " + TYPE + " = ? OR " + TYPE + " = ? OR " + TYPE + " =?)";
    String[]       selectionArgs = SqlUtil.buildArgs(threadId,
                                                     timestamp,
                                                     MessageTypes.INCOMING_AUDIO_CALL_TYPE,
                                                     MessageTypes.INCOMING_VIDEO_CALL_TYPE,
                                                     MessageTypes.MISSED_AUDIO_CALL_TYPE,
                                                     MessageTypes.MISSED_VIDEO_CALL_TYPE);

    try (Cursor cursor = db.query(TABLE_NAME, projection, selection, selectionArgs, null, null, null)) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  public void markAsEndSession(long id) {
    updateTypeBitmask(id, MessageTypes.KEY_EXCHANGE_MASK, MessageTypes.END_SESSION_BIT);
  }

  public void markAsInvalidVersionKeyExchange(long id) {
    updateTypeBitmask(id, 0, MessageTypes.KEY_EXCHANGE_INVALID_VERSION_BIT);
  }

  public void markAsSecure(long id) {
    updateTypeBitmask(id, 0, MessageTypes.SECURE_MESSAGE_BIT);
  }

  public void markAsDecryptFailed(long id) {
    updateTypeBitmask(id, MessageTypes.ENCRYPTION_MASK, MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT);
  }

  public void markAsNoSession(long id) {
    updateTypeBitmask(id, MessageTypes.ENCRYPTION_MASK, MessageTypes.ENCRYPTION_REMOTE_NO_SESSION_BIT);
  }

  public void markAsUnsupportedProtocolVersion(long id) {
    updateTypeBitmask(id, MessageTypes.BASE_TYPE_MASK, MessageTypes.UNSUPPORTED_MESSAGE_TYPE);
  }

  public void markAsInvalidMessage(long id) {
    updateTypeBitmask(id, MessageTypes.BASE_TYPE_MASK, MessageTypes.INVALID_MESSAGE_TYPE);
  }

  public void markAsLegacyVersion(long id) {
    updateTypeBitmask(id, MessageTypes.ENCRYPTION_MASK, MessageTypes.ENCRYPTION_REMOTE_LEGACY_BIT);
  }

  public void markAsMissedCall(long id, boolean isVideoOffer) {
    updateTypeBitmask(id, MessageTypes.TOTAL_MASK, isVideoOffer ? MessageTypes.MISSED_VIDEO_CALL_TYPE : MessageTypes.MISSED_AUDIO_CALL_TYPE);
  }

  public void markSmsStatus(long id, int status) {
    Log.i(TAG, "Updating ID: " + id + " to status: " + status);
    ContentValues contentValues = new ContentValues();
    contentValues.put(MMS_STATUS, status);

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {id+""});

    long threadId = getThreadIdForMessage(id);
    SignalDatabase.threads().update(threadId, false);
    notifyConversationListeners(threadId);
  }

  private void updateTypeBitmask(long id, long maskOff, long maskOn) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    long threadId;

    db.beginTransaction();
    try {
      db.execSQL("UPDATE " + TABLE_NAME +
                 " SET " + TYPE + " = (" + TYPE + " & " + (MessageTypes.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
                 " WHERE " + ID + " = ?", SqlUtil.buildArgs(id));

      threadId = getThreadIdForMessage(id);

      SignalDatabase.threads().updateSnippetTypeSilently(threadId);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(id));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

  private InsertResult updateMessageBodyAndType(long messageId, String body, long maskOff, long maskOn) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + BODY + " = ?, " +
               TYPE + " = (" + TYPE + " & " + (MessageTypes.TOTAL_MASK - maskOff) + " | " + maskOn + ") " +
               "WHERE " + ID + " = ?",
               new String[] {body, messageId + ""});

    long threadId = getThreadIdForMessage(messageId);

    SignalDatabase.threads().update(threadId, true);
    notifyConversationListeners(threadId);

    return new InsertResult(messageId, threadId);
  }

  public InsertResult updateBundleMessageBody(long messageId, String body) {
    long type = MessageTypes.BASE_INBOX_TYPE | MessageTypes.SECURE_MESSAGE_BIT | MessageTypes.PUSH_MESSAGE_BIT;
    return updateMessageBodyAndType(messageId, body, MessageTypes.TOTAL_MASK, type);
  }

  public @NonNull List<MarkedMessageInfo> getViewedIncomingMessages(long threadId) {
    SQLiteDatabase db      = databaseHelper.getSignalReadableDatabase();
    String[]       columns = new String[]{ ID, RECIPIENT_ID, DATE_SENT, TYPE, THREAD_ID, STORY_TYPE};
    String         where   = THREAD_ID + " = ? AND " + VIEWED_RECEIPT_COUNT + " > 0 AND " + TYPE + " & " + MessageTypes.BASE_INBOX_TYPE + " = " + MessageTypes.BASE_INBOX_TYPE;
    String[]       args    = SqlUtil.buildArgs(threadId);


    try (Cursor cursor = db.query(TABLE_NAME, columns, where, args, null, null, null, null)) {
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

        results.add(new MarkedMessageInfo(threadId, syncMessageId, new MessageId(messageId), null, storyType));
      }

      return results;
    }
  }

  public @Nullable MarkedMessageInfo setIncomingMessageViewed(long messageId) {
    List<MarkedMessageInfo> results = setIncomingMessagesViewed(Collections.singletonList(messageId));

    if (results.isEmpty()) {
      return null;
    } else {
      return results.get(0);
    }
  }

  public @NonNull List<MarkedMessageInfo> setIncomingMessagesViewed(@NonNull List<Long> messageIds) {
    if (messageIds.isEmpty()) {
      return Collections.emptyList();
    }

    SQLiteDatabase          database    = databaseHelper.getSignalWritableDatabase();
    String[]                columns     = new String[]{ ID, RECIPIENT_ID, DATE_SENT, TYPE, THREAD_ID, STORY_TYPE};
    String                  where       = ID + " IN (" + Util.join(messageIds, ",") + ") AND " + VIEWED_RECEIPT_COUNT + " = 0";
    List<MarkedMessageInfo> results     = new LinkedList<>();

    database.beginTransaction();
    try (Cursor cursor = database.query(TABLE_NAME, columns, where, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        long type = CursorUtil.requireLong(cursor, TYPE);
        if (MessageTypes.isSecureType(type) && MessageTypes.isInboxType(type)) {
          long          messageId     = CursorUtil.requireLong(cursor, ID);
          long          threadId      = CursorUtil.requireLong(cursor, THREAD_ID);
          RecipientId   recipientId   = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
          long          dateSent      = CursorUtil.requireLong(cursor, DATE_SENT);
          SyncMessageId syncMessageId = new SyncMessageId(recipientId, dateSent);
          StoryType     storyType     = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));

          results.add(new MarkedMessageInfo(threadId, syncMessageId, new MessageId(messageId), null, storyType));

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

    Set<RecipientId> storyRecipientsUpdated = results.stream()
                                                     .filter(it -> it.storyType.isStory())
                                                     .map(it -> SignalDatabase.threads().getRecipientIdForThreadId(it.getThreadId()))
                                                     .filter(it -> it != null)
                                                     .collect(java.util.stream.Collectors.toSet());

    notifyConversationListeners(threadsUpdated);
    notifyConversationListListeners();

    ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(storyRecipientsUpdated);

    return results;
  }

  public @NonNull List<MarkedMessageInfo> setOutgoingGiftsRevealed(@NonNull List<Long> messageIds) {
    String[]                projection = SqlUtil.buildArgs(ID, RECIPIENT_ID, DATE_SENT, THREAD_ID, STORY_TYPE);
    String                  where      = ID + " IN (" + Util.join(messageIds, ",") + ") AND (" + getOutgoingTypeClause() + ") AND (" + TYPE + " & " + MessageTypes.SPECIAL_TYPES_MASK + " = " + MessageTypes.SPECIAL_TYPE_GIFT_BADGE + ") AND " + VIEWED_RECEIPT_COUNT + " = 0";
    List<MarkedMessageInfo> results    = new LinkedList<>();

    getWritableDatabase().beginTransaction();
    try (Cursor cursor = getWritableDatabase().query(TABLE_NAME, projection, where, null, null, null, null)) {
      while (cursor.moveToNext()) {
        long          messageId     = CursorUtil.requireLong(cursor, ID);
        long          threadId      = CursorUtil.requireLong(cursor, THREAD_ID);
        RecipientId   recipientId   = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
        long          dateSent      = CursorUtil.requireLong(cursor, DATE_SENT);
        SyncMessageId syncMessageId = new SyncMessageId(recipientId, dateSent);
        StoryType      storyType     = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));

        results.add(new MarkedMessageInfo(threadId, syncMessageId, new MessageId(messageId), null, storyType));

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

  public @NonNull InsertResult insertCallLog(@NonNull RecipientId recipientId, long type, long timestamp) {
    boolean   unread    = MessageTypes.isMissedAudioCall(type) || MessageTypes.isMissedVideoCall(type);
    Recipient recipient = Recipient.resolved(recipientId);
    long      threadId  = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);

    ContentValues values = new ContentValues(7);
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, timestamp);
    values.put(READ, unread ? 0 : 1);
    values.put(TYPE, type);
    values.put(THREAD_ID, threadId);

    long    messageId          = getWritableDatabase().insert(TABLE_NAME, null, values);
    boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && Recipient.resolved(recipientId).isMuted();

    if (unread) {
      SignalDatabase.threads().incrementUnread(threadId, 1, 0);
    }

    SignalDatabase.threads().update(threadId, !keepThreadArchived);

    notifyConversationListeners(threadId);
    TrimThreadJob.enqueueAsync(threadId);

    return new InsertResult(messageId, threadId);
  }

  public void updateCallLog(long messageId, long type) {
    boolean       unread = MessageTypes.isMissedAudioCall(type) || MessageTypes.isMissedVideoCall(type);
    ContentValues values = new ContentValues(2);
    values.put(TYPE, type);
    values.put(READ, unread ? 0 : 1);
    getWritableDatabase().update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(messageId));

    long      threadId           = getThreadIdForMessage(messageId);
    Recipient recipient          = SignalDatabase.threads().getRecipientForThreadId(threadId);
    boolean   keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && recipient != null && recipient.isMuted();

    if (unread) {
      SignalDatabase.threads().incrementUnread(threadId, 1, 0);
    }

    SignalDatabase.threads().update(threadId, !keepThreadArchived);

    notifyConversationListeners(threadId);
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
  }

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
        values.put(TYPE, MessageTypes.GROUP_CALL_TYPE);
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
      String[] args      = SqlUtil.buildArgs(MessageTypes.GROUP_CALL_TYPE, threadId);
      boolean  sameEraId = false;

      try (MmsReader reader = new MmsReader(db.query(TABLE_NAME, MMS_PROJECTION, where, args, null, null, DATE_RECEIVED + " DESC", "1"))) {
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
        values.put(TYPE, MessageTypes.GROUP_CALL_TYPE);
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

  public boolean updatePreviousGroupCall(long threadId, @Nullable String peekGroupCallEraId, @NonNull Collection<UUID> peekJoinedUuids, boolean isCallFull) {
    SQLiteDatabase db        = databaseHelper.getSignalWritableDatabase();
    String         where     = TYPE + " = ? AND " + THREAD_ID + " = ?";
    String[]       args      = SqlUtil.buildArgs(MessageTypes.GROUP_CALL_TYPE, threadId);
    boolean        sameEraId = false;

    try (MmsReader reader = new MmsReader(db.query(TABLE_NAME, MMS_PROJECTION, where, args, null, null, DATE_RECEIVED + " DESC", "1"))) {
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

  public Optional<InsertResult> insertMessageInbox(IncomingTextMessage message, long type) {
    boolean tryToCollapseJoinRequestEvents = false;

    if (message.isJoined()) {
      type = (type & (MessageTypes.TOTAL_MASK - MessageTypes.BASE_TYPE_MASK)) | MessageTypes.JOINED_TYPE;
    } else if (message.isPreKeyBundle()) {
      type |= MessageTypes.KEY_EXCHANGE_BIT | MessageTypes.KEY_EXCHANGE_BUNDLE_BIT;
    } else if (message.isSecureMessage()) {
      type |= MessageTypes.SECURE_MESSAGE_BIT;
    } else if (message.isGroup()) {
      IncomingGroupUpdateMessage incomingGroupUpdateMessage = (IncomingGroupUpdateMessage) message;

      type |= MessageTypes.SECURE_MESSAGE_BIT;

      if (incomingGroupUpdateMessage.isGroupV2()) {
        type |= MessageTypes.GROUP_V2_BIT | MessageTypes.GROUP_UPDATE_BIT;
        if (incomingGroupUpdateMessage.isJustAGroupLeave()) {
          type |= MessageTypes.GROUP_LEAVE_BIT;
        } else if (incomingGroupUpdateMessage.isCancelJoinRequest()) {
          tryToCollapseJoinRequestEvents = true;
        }
      } else if (incomingGroupUpdateMessage.isUpdate()) {
        type |= MessageTypes.GROUP_UPDATE_BIT;
      } else if (incomingGroupUpdateMessage.isQuit()) {
        type |= MessageTypes.GROUP_LEAVE_BIT;
      }

    } else if (message.isEndSession()) {
      type |= MessageTypes.SECURE_MESSAGE_BIT;
      type |= MessageTypes.END_SESSION_BIT;
    }

    if (message.isPush())                type |= MessageTypes.PUSH_MESSAGE_BIT;
    if (message.isIdentityUpdate())      type |= MessageTypes.KEY_EXCHANGE_IDENTITY_UPDATE_BIT;
    if (message.isContentPreKeyBundle()) type |= MessageTypes.KEY_EXCHANGE_CONTENT_FORMAT;

    if      (message.isIdentityVerified())    type |= MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT;
    else if (message.isIdentityDefault())     type |= MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT;

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
                     (type & MessageTypes.GROUP_UPDATE_BIT) > 0;

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
        final boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && (recipient.isMuted() || (groupRecipient != null && groupRecipient.isMuted()));
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

  public Optional<InsertResult> insertMessageInbox(IncomingTextMessage message) {
    return insertMessageInbox(message, MessageTypes.BASE_INBOX_TYPE);
  }

  public void insertProfileNameChangeMessages(@NonNull Recipient recipient, @NonNull String newProfileName, @NonNull String previousProfileName) {
    ThreadTable       threadTable       = SignalDatabase.threads();
    List<GroupRecord> groupRecords      = SignalDatabase.groups().getGroupsContainingMember(recipient.getId(), false);
    List<Long>        threadIdsToUpdate = new LinkedList<>();

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
      for (GroupRecord groupRecord : groupRecords) {
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
              values.put(TYPE, MessageTypes.PROFILE_CHANGE_TYPE);
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
    values.put(TYPE, MessageTypes.GV1_MIGRATION_TYPE);
    values.put(THREAD_ID, threadId);

    if (!membershipChange.isEmpty()) {
      values.put(BODY, membershipChange.serialize());
    }

    databaseHelper.getSignalWritableDatabase().insert(TABLE_NAME, null, values);
  }

  public void insertNumberChangeMessages(@NonNull RecipientId recipientId) {
    ThreadTable       threadTable       = SignalDatabase.threads();
    List<GroupRecord> groupRecords      = SignalDatabase.groups().getGroupsContainingMember(recipientId, false);
    List<Long>        threadIdsToUpdate = new LinkedList<>();

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.beginTransaction();

    try {
      threadIdsToUpdate.add(threadTable.getThreadIdFor(recipientId));
      for (GroupRecord groupRecord : groupRecords) {
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
                         values.put(TYPE, MessageTypes.CHANGE_NUMBER_TYPE);
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

  public void insertBoostRequestMessage(@NonNull RecipientId recipientId, long threadId) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, 1);
    values.put(TYPE, MessageTypes.BOOST_REQUEST_TYPE);
    values.put(THREAD_ID, threadId);
    values.putNull(BODY);

    getWritableDatabase().insert(TABLE_NAME, null, values);
  }

  public void insertThreadMergeEvent(@NonNull RecipientId recipientId, long threadId, @NonNull ThreadMergeEvent event) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, 1);
    values.put(TYPE, MessageTypes.THREAD_MERGE_TYPE);
    values.put(THREAD_ID, threadId);
    values.put(BODY, Base64.encodeBytes(event.toByteArray()));

    getWritableDatabase().insert(TABLE_NAME, null, values);

    ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId);
  }

  public void insertSessionSwitchoverEvent(@NonNull RecipientId recipientId, long threadId, @NonNull SessionSwitchoverEvent event) {
    if (!FeatureFlags.phoneNumberPrivacy()) {
      throw new IllegalStateException("Should not occur in a non-PNP world!");
    }

    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, 1);
    values.put(TYPE, MessageTypes.SESSION_SWITCHOVER_TYPE);
    values.put(THREAD_ID, threadId);
    values.put(BODY, Base64.encodeBytes(event.toByteArray()));

    getWritableDatabase().insert(TABLE_NAME, null, values);

    ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId);
  }

  public void insertSmsExportMessage(@NonNull RecipientId recipientId, long threadId) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, 1);
    values.put(DATE_RECEIVED, System.currentTimeMillis());
    values.put(DATE_SENT, System.currentTimeMillis());
    values.put(READ, 1);
    values.put(TYPE, MessageTypes.SMS_EXPORT_TYPE);
    values.put(THREAD_ID, threadId);
    values.putNull(BODY);

    boolean updated = SQLiteDatabaseExtensionsKt.withinTransaction(getWritableDatabase(), db -> {
      if (SignalDatabase.messages().hasSmsExportMessage(threadId)) {
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

  public void endTransaction(SQLiteDatabase database) {
    database.endTransaction();
  }

  public void ensureMigration() {
    databaseHelper.getSignalWritableDatabase();
  }

  public boolean isStory(long messageId) {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    String[]       projection = new String[]{"1"};
    String         where      = IS_STORY_CLAUSE + " AND " + ID + " = ?";
    String[]       whereArgs  = SqlUtil.buildArgs(messageId);

    try (Cursor cursor = database.query(TABLE_NAME, projection, where, whereArgs, null, null, null, "1")) {
      return cursor != null && cursor.moveToFirst();
    }
  }

  public @NonNull MessageTable.Reader getOutgoingStoriesTo(@NonNull RecipientId recipientId) {
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

    return new MmsReader(rawQuery(where, whereArgs));
  }

  public @NonNull MessageTable.Reader getAllOutgoingStories(boolean reverse, int limit) {
    String where = IS_STORY_CLAUSE + " AND (" + getOutgoingTypeClause() + ")";

    return new MmsReader(rawQuery(where, null, reverse, limit));
  }

  public @NonNull MessageTable.Reader getAllOutgoingStoriesAt(long sentTimestamp) {
    String   where      = IS_STORY_CLAUSE + " AND " + DATE_SENT + " = ? AND (" + getOutgoingTypeClause() + ")";
    String[] whereArgs  = SqlUtil.buildArgs(sentTimestamp);
    Cursor   cursor     = rawQuery(where, whereArgs, false, -1L);

    return new MmsReader(cursor);
  }

  public @NonNull List<MarkedMessageInfo> markAllIncomingStoriesRead() {
    String where = IS_STORY_CLAUSE + " AND NOT (" + getOutgoingTypeClause() + ") AND " + READ + " = 0";

    List<MarkedMessageInfo> markedMessageInfos = setMessagesRead(where, null);
    notifyConversationListListeners();

    return markedMessageInfos;
  }

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

  public @NonNull MessageTable.Reader getAllStoriesFor(@NonNull RecipientId recipientId, int limit) {
    long     threadId  = SignalDatabase.threads().getThreadIdIfExistsFor(recipientId);
    String   where     = IS_STORY_CLAUSE + " AND " + THREAD_ID_WHERE;
    String[] whereArgs = SqlUtil.buildArgs(threadId);
    Cursor   cursor    = rawQuery(where, whereArgs, false, limit);

    return new MmsReader(cursor);
  }

  public @NonNull MessageTable.Reader getUnreadStories(@NonNull RecipientId recipientId, int limit) {
    final long   threadId = SignalDatabase.threads().getThreadIdIfExistsFor(recipientId);
    final String query    = IS_STORY_CLAUSE +
                            " AND NOT (" + getOutgoingTypeClause() + ") " +
                            " AND " + THREAD_ID_WHERE +
                            " AND " + VIEWED_RECEIPT_COUNT + " = ?";
    final String[] args   = SqlUtil.buildArgs(threadId, 0);

    return new MmsReader(rawQuery(query, args, false, limit));
  }

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

  public @NonNull MessageId getStoryId(@NonNull RecipientId authorId, long sentTimestamp) throws NoSuchMessageException {
    SQLiteDatabase database   = databaseHelper.getSignalReadableDatabase();
    String[]       projection = new String[]{ID, RECIPIENT_ID};
    String         where      = IS_STORY_CLAUSE + " AND " + DATE_SENT + " = ?";
    String[]       whereArgs  = SqlUtil.buildArgs(sentTimestamp);

    try (Cursor cursor = database.query(TABLE_NAME, projection, where, whereArgs, null, null, null, "1")) {
      if (cursor != null && cursor.moveToFirst()) {
        RecipientId rowRecipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID)));

        if (Recipient.self().getId().equals(authorId) || rowRecipientId.equals(authorId)) {
          return new MessageId(CursorUtil.requireLong(cursor, ID));
        }
      }
    }

    throw new NoSuchMessageException("No story sent at " + sentTimestamp);
  }

  public @NonNull List<RecipientId> getUnreadStoryThreadRecipientIds() {
    SQLiteDatabase db    = getReadableDatabase();
    String         query = "SELECT DISTINCT " + ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID + "\n"
                           + "FROM " + TABLE_NAME + "\n"
                           + "JOIN " + ThreadTable.TABLE_NAME + "\n"
                           + "ON " + TABLE_NAME + "." + THREAD_ID + " = " + ThreadTable.TABLE_NAME + "." + ThreadTable.ID + "\n"
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

  public @NonNull List<StoryResult> getOrderedStoryRecipientsAndIds(boolean isOutgoingOnly) {
    String         where = "WHERE " + STORY_TYPE + " > 0 AND " + REMOTE_DELETED + " = 0" + (isOutgoingOnly ? " AND is_outgoing != 0" : "") + "\n";
    SQLiteDatabase db    = getReadableDatabase();
    String         query = "SELECT\n"
                           + " " + TABLE_NAME + "." + DATE_SENT + " AS sent_timestamp,\n"
                           + " " + TABLE_NAME + "." + ID + " AS mms_id,\n"
                           + " " + ThreadTable.TABLE_NAME + "." + ThreadTable.RECIPIENT_ID + ",\n"
                           + " (" + getOutgoingTypeClause() + ") AS is_outgoing,\n"
                           + " " + VIEWED_RECEIPT_COUNT + ",\n"
                           + " " + TABLE_NAME + "." + DATE_SENT + ",\n"
                           + " " + RECEIPT_TIMESTAMP + ",\n"
                           + " (" + getOutgoingTypeClause() + ") = 0 AND " + VIEWED_RECEIPT_COUNT + " = 0 AS is_unread\n"
                           + "FROM " + TABLE_NAME + "\n"
                           + "JOIN " + ThreadTable.TABLE_NAME + "\n"
                           + "ON " + TABLE_NAME + "." + THREAD_ID + " = " + ThreadTable.TABLE_NAME + "." + ThreadTable.ID + "\n"
                           + where
                           + "ORDER BY\n"
                           + "is_unread DESC,\n"
                           + "CASE\n"
                           + "WHEN is_outgoing = 0 AND " + VIEWED_RECEIPT_COUNT + " = 0 THEN " + MessageTable.TABLE_NAME + "." + MessageTable.DATE_SENT + "\n"
                           + "WHEN is_outgoing = 0 AND viewed_receipt_count > 0 THEN " + MessageTable.RECEIPT_TIMESTAMP + "\n"
                           + "WHEN is_outgoing = 1 THEN " + MessageTable.TABLE_NAME + "." + MessageTable.DATE_SENT + "\n"
                           + "END DESC";

    List<StoryResult> results;
    try (Cursor cursor = db.rawQuery(query, null)) {
      if (cursor != null) {
        results = new ArrayList<>(cursor.getCount());

        while (cursor.moveToNext()) {
          results.add(new StoryResult(RecipientId.from(CursorUtil.requireLong(cursor, ThreadTable.RECIPIENT_ID)),
                                           CursorUtil.requireLong(cursor, "mms_id"),
                                           CursorUtil.requireLong(cursor, "sent_timestamp"),
                                           CursorUtil.requireBoolean(cursor, "is_outgoing")));
        }

        return results;
      }
    }

    return Collections.emptyList();
  }

  public @NonNull Cursor getStoryReplies(long parentStoryId) {
    String   where     = PARENT_STORY_ID + " = ?";
    String[] whereArgs = SqlUtil.buildArgs(parentStoryId);

    return rawQuery(where, whereArgs, false, 0);
  }

  public int getNumberOfStoryReplies(long parentStoryId) {
    SQLiteDatabase db        = databaseHelper.getSignalReadableDatabase();
    String[]       columns   = new String[]{"COUNT(*)"};
    String         where     = PARENT_STORY_ID + " = ?";
    String[]       whereArgs = SqlUtil.buildArgs(parentStoryId);

    try (Cursor cursor = db.query(TABLE_NAME, columns, where, whereArgs, null, null, null, null)) {
      return cursor != null && cursor.moveToNext() ? cursor.getInt(0) : 0;
    }
  }

  public boolean containsStories(long threadId) {
    SQLiteDatabase db        = databaseHelper.getSignalReadableDatabase();
    String[]       columns   = new String[]{"1"};
    String         where     = THREAD_ID_WHERE + " AND " + STORY_TYPE + " > 0";
    String[]       whereArgs = SqlUtil.buildArgs(threadId);

    try (Cursor cursor = db.query(TABLE_NAME, columns, where, whereArgs, null, null, null, "1")) {
      return cursor != null && cursor.moveToNext();
    }
  }

  public boolean hasSelfReplyInStory(long parentStoryId) {
    SQLiteDatabase db        = databaseHelper.getSignalReadableDatabase();
    String[]       columns   = new String[]{"COUNT(*)"};
    String         where     = PARENT_STORY_ID + " = ? AND (" + getOutgoingTypeClause() + ")";
    String[]       whereArgs = SqlUtil.buildArgs(-parentStoryId);

    try (Cursor cursor = db.query(TABLE_NAME, columns, where, whereArgs, null, null, null, null)) {
      return cursor != null && cursor.moveToNext() && cursor.getInt(0) > 0;
    }
  }

  public boolean hasGroupReplyOrReactionInStory(long parentStoryId) {
    return hasSelfReplyInStory(-parentStoryId);
  }

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

  public void deleteGroupStoryReplies(long parentStoryId) {
    SQLiteDatabase db   = databaseHelper.getSignalWritableDatabase();
    String[]       args = SqlUtil.buildArgs(parentStoryId);

    db.delete(TABLE_NAME, PARENT_STORY_ID + " = ?", args);
    OptimizeMessageSearchIndexJob.enqueue();
  }

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

      if (deletedStoryCount > 0) {
        OptimizeMessageSearchIndexJob.enqueue();
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

  public boolean isGroupQuitMessage(long messageId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String[] columns = new String[]{ID};
    long     type    = MessageTypes.getOutgoingEncryptedMessageType() | MessageTypes.GROUP_LEAVE_BIT;
    String   query   = ID + " = ? AND " + TYPE + " & " + type + " = " + type + " AND " + TYPE + " & " + MessageTypes.GROUP_V2_BIT + " = 0";
    String[] args    = SqlUtil.buildArgs(messageId);

    try (Cursor cursor = db.query(TABLE_NAME, columns, query, args, null, null, null, null)) {
      if (cursor.getCount() == 1) {
        return true;
      }
    }

    return false;
  }

  public long getLatestGroupQuitTimestamp(long threadId, long quitTimeBarrier) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String[] columns = new String[]{DATE_SENT};
    long     type    = MessageTypes.getOutgoingEncryptedMessageType() | MessageTypes.GROUP_LEAVE_BIT;
    String   query   = THREAD_ID + " = ? AND " + TYPE + " & " + type + " = " + type + " AND " + TYPE + " & " + MessageTypes.GROUP_V2_BIT + " = 0 AND " + DATE_SENT + " < ?";
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

  public int getScheduledMessageCountForThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String   query = THREAD_ID + " = ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ? AND " + SCHEDULED_DATE + " != ?";
    String[] args  = SqlUtil.buildArgs(threadId, 0, 0, -1);

    try (Cursor cursor = db.query(TABLE_NAME + " INDEXED BY " + INDEX_THREAD_STORY_SCHEDULED_DATE, COUNT, query, args, null, null, null)) {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public int getMessageCountForThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String   query = THREAD_ID + " = ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ? AND " + SCHEDULED_DATE + " = ?";
    String[] args  = SqlUtil.buildArgs(threadId, 0, 0, -1);

    try (Cursor cursor = db.query(TABLE_NAME + " INDEXED BY " + INDEX_THREAD_STORY_SCHEDULED_DATE, COUNT, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public int getMessageCountForThread(long threadId, long beforeTime) {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();

    String   query = THREAD_ID + " = ? AND " + DATE_RECEIVED + " < ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ? AND " + SCHEDULED_DATE + " = ?";
    String[] args  = SqlUtil.buildArgs(threadId, beforeTime, 0, 0, -1);

    try (Cursor cursor = db.query(TABLE_NAME, COUNT, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public boolean hasMeaningfulMessage(long threadId) {
    if (threadId == -1) {
      return false;
    }

    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    SqlUtil.Query  query = buildMeaningfulMessagesQuery(threadId);

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { "1" }, query.getWhere(), query.getWhereArgs(), null, null, null, "1")) {
      return cursor != null && cursor.moveToFirst();
    }
  }

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
    String query = THREAD_ID + " = ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ? AND " + SCHEDULED_DATE + " = ? AND (NOT " + TYPE + " & ? AND " + TYPE + " != ? AND " + TYPE + " != ? AND " + TYPE + " != ? AND " + TYPE + " != ? AND " + TYPE + " & " + MessageTypes.GROUP_V2_LEAVE_BITS + " != " + MessageTypes.GROUP_V2_LEAVE_BITS + ")";
    return SqlUtil.buildQuery(query, threadId, 0, 0, -1, MessageTypes.IGNORABLE_TYPESMASK_WHEN_COUNTING, MessageTypes.PROFILE_CHANGE_TYPE, MessageTypes.CHANGE_NUMBER_TYPE, MessageTypes.SMS_EXPORT_TYPE, MessageTypes.BOOST_REQUEST_TYPE);
  }

  public void addFailures(long messageId, List<NetworkFailure> failure) {
    try {
      addToDocument(messageId, NETWORK_FAILURES, failure, NetworkFailureSet.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void setNetworkFailures(long messageId, Set<NetworkFailure> failures) {
    try {
      setDocument(databaseHelper.getSignalWritableDatabase(), messageId, NETWORK_FAILURES, new NetworkFailureSet(failures));
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

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
    return rawQuery(MMS_PROJECTION_WITH_ATTACHMENTS, where, arguments, reverse, limit);
  }

  private Cursor rawQuery(@NonNull String[] projection, @NonNull String where, @Nullable String[] arguments, boolean reverse, long limit) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    String rawQueryString   = "SELECT " + Util.join(projection, ",") +
                              " FROM " + MessageTable.TABLE_NAME + " LEFT OUTER JOIN " + AttachmentTable.TABLE_NAME +
                              " ON (" + MessageTable.TABLE_NAME + "." + MessageTable.ID + " = " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + ")" +
                              " WHERE " + where + " GROUP BY " + MessageTable.TABLE_NAME + "." + MessageTable.ID;

    if (reverse) {
      rawQueryString += " ORDER BY " + MessageTable.TABLE_NAME + "." + MessageTable.ID + " DESC";
    }

    if (limit > 0) {
      rawQueryString += " LIMIT " + limit;
    }

    return database.rawQuery(rawQueryString, arguments);
  }

  private Cursor internalGetMessage(long messageId) {
    return rawQuery(RAW_ID_WHERE, new String[] {messageId + ""});
  }

  public MessageRecord getMessageRecord(long messageId) throws NoSuchMessageException {
    try (Cursor cursor = rawQuery(RAW_ID_WHERE, new String[] {messageId + ""})) {
      MessageRecord record = new MmsReader(cursor).getNext();

      if (record == null) {
        throw new NoSuchMessageException("No message for ID: " + messageId);
      }

      return record;
    }
  }

  public @Nullable MessageRecord getMessageRecordOrNull(long messageId) {
    try (Cursor cursor = rawQuery(RAW_ID_WHERE, new String[] {messageId + ""})) {
      return new MmsReader(cursor).getNext();
    }
  }

  public MmsReader getMessages(Collection<Long> messageIds) {
    String ids = TextUtils.join(",", messageIds);
    return mmsReaderFor(rawQuery(MessageTable.TABLE_NAME + "." + MessageTable.ID + " IN (" + ids + ")", null));
  }

  private void updateMailboxBitmask(long id, long maskOff, long maskOn, Optional<Long> threadId) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      db.execSQL("UPDATE " + TABLE_NAME +
                 " SET " + TYPE + " = (" + TYPE + " & " + (MessageTypes.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
                 " WHERE " + ID + " = ?", new String[] { id + "" });

      if (threadId.isPresent()) {
        SignalDatabase.threads().updateSnippetTypeSilently(threadId.get());
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void markAsOutbox(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_OUTBOX_TYPE, Optional.of(threadId));
  }

  public void markAsForcedSms(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, MessageTypes.PUSH_MESSAGE_BIT, MessageTypes.MESSAGE_FORCE_SMS_BIT, Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
  }

  public void markAsRateLimited(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, 0, MessageTypes.MESSAGE_RATE_LIMITED_BIT, Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
  }

  public void clearRateLimitStatus(@NonNull Collection<Long> ids) {
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();

    db.beginTransaction();
    try {
      for (long id : ids) {
        long threadId = getThreadIdForMessage(id);
        updateMailboxBitmask(id, MessageTypes.MESSAGE_RATE_LIMITED_BIT, 0, Optional.of(threadId));
      }

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void markAsPendingInsecureSmsFallback(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_PENDING_INSECURE_SMS_FALLBACK, Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
  }

  public void markAsSending(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_SENDING_TYPE, Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

  public void markAsSentFailed(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_SENT_FAILED_TYPE, Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

  public void markAsSent(long messageId, boolean secure) {
    long threadId = getThreadIdForMessage(messageId);
    updateMailboxBitmask(messageId, MessageTypes.BASE_TYPE_MASK, MessageTypes.BASE_SENT_TYPE | (secure ? MessageTypes.PUSH_MESSAGE_BIT | MessageTypes.SECURE_MESSAGE_BIT : 0), Optional.of(threadId));
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
  }

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
      values.putNull(QUOTE_TYPE);
      values.putNull(QUOTE_ID);
      values.putNull(LINK_PREVIEWS);
      values.putNull(SHARED_CONTACTS);
      db.update(TABLE_NAME, values, ID_WHERE, new String[] { String.valueOf(messageId) });

      deletedAttachments = SignalDatabase.attachments().deleteAttachmentsForMessage(messageId);
      SignalDatabase.mentions().deleteMentionsForMessage(messageId);
      SignalDatabase.messageLog().deleteAllRelatedToMessage(messageId, true);
      SignalDatabase.reactions().deleteReactions(new MessageId(messageId));
      deleteGroupStoryReplies(messageId);
      disassociateStoryQuotes(messageId);

      threadId = getThreadIdForMessage(messageId);
      SignalDatabase.threads().update(threadId, false);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
    ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();

    if (deletedAttachments) {
      ApplicationDependencies.getDatabaseObserver().notifyAttachmentObservers();
    }
  }

  public void markDownloadState(long messageId, long state) {
    SQLiteDatabase database     = databaseHelper.getSignalWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(MMS_STATUS, state);

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId + ""});
    ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
  }

  public boolean clearScheduledStatus(long threadId, long messageId) {
    SQLiteDatabase database     = databaseHelper.getSignalWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(SCHEDULED_DATE, -1);
    contentValues.put(DATE_SENT, System.currentTimeMillis());
    contentValues.put(DATE_RECEIVED, System.currentTimeMillis());

    int rowsUpdated = database.update(TABLE_NAME, contentValues, ID_WHERE + " AND " + SCHEDULED_DATE + "!= ?", SqlUtil.buildArgs(messageId, -1));
    ApplicationDependencies.getDatabaseObserver().notifyMessageInsertObservers(threadId, new MessageId(messageId));
    ApplicationDependencies.getDatabaseObserver().notifyScheduledMessageObservers(threadId);

    return rowsUpdated > 0;
  }

  public void rescheduleMessage(long threadId, long messageId, long time) {
    SQLiteDatabase database      = databaseHelper.getSignalWritableDatabase();
    ContentValues  contentValues = new ContentValues();
    contentValues.put(SCHEDULED_DATE, time);

    int rowsUpdated = database.update(TABLE_NAME, contentValues, ID_WHERE + " AND " + SCHEDULED_DATE + "!= ?", SqlUtil.buildArgs(messageId, -1));
    ApplicationDependencies.getDatabaseObserver().notifyScheduledMessageObservers(threadId);
    ApplicationDependencies.getScheduledMessageManager().scheduleIfNecessary();
    if (rowsUpdated == 0) {
      Log.w(TAG, "Failed to reschedule messageId=" + messageId + " to new time " + time + ". may have been sent already");
    }
  }

  public void markAsInsecure(long messageId) {
    updateMailboxBitmask(messageId, MessageTypes.SECURE_MESSAGE_BIT, 0, Optional.empty());
  }

  public void markUnidentified(long messageId, boolean unidentified) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(UNIDENTIFIED, unidentified ? 1 : 0);

    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(messageId)});
  }

  public void markExpireStarted(long id) {
    markExpireStarted(id, System.currentTimeMillis());
  }

  public void markExpireStarted(long id, long startedTimestamp) {
    markExpireStarted(Collections.singleton(id), startedTimestamp);
  }

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

  public void markAsNotified(long id) {
    SQLiteDatabase database      = databaseHelper.getSignalWritableDatabase();
    ContentValues  contentValues = new ContentValues();

    contentValues.put(NOTIFIED, 1);
    contentValues.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(id)});
  }

  public List<MarkedMessageInfo> setMessagesReadSince(long threadId, long sinceTimestamp) {
    if (sinceTimestamp == -1) {
      return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " <= 0 AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND (" + getOutgoingTypeClause() + ")))", new String[] { String.valueOf(threadId)});
    } else {
      return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " <= 0 AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND ( " + getOutgoingTypeClause() + " ))) AND " + DATE_RECEIVED + " <= ?", new String[]{ String.valueOf(threadId), String.valueOf(sinceTimestamp)});
    }
  }

  public @NonNull List<MarkedMessageInfo> setGroupStoryMessagesReadSince(long threadId, long groupStoryId, long sinceTimestamp) {
    if (sinceTimestamp == -1) {
      return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " = ? AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND (" + getOutgoingTypeClause() + ")))", SqlUtil.buildArgs(threadId, groupStoryId));
    } else {
      return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " = ? AND (" + READ + " = 0 OR (" + REACTIONS_UNREAD + " = 1 AND ( " + getOutgoingTypeClause() + " ))) AND " + DATE_RECEIVED + " <= ?", SqlUtil.buildArgs(threadId, groupStoryId, sinceTimestamp));
    }
  }

  public @NonNull List<StoryType> getStoryTypes(@NonNull List<MessageId> messageIds) {
    List<Long> mmsMessages = messageIds.stream()
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
      if (storyTypes.containsKey(id.getId())) {
        return storyTypes.get(id.getId());
      } else {
        return StoryType.NONE;
      }
    }).collect(java.util.stream.Collectors.toList());
  }

  public List<MarkedMessageInfo> setEntireThreadRead(long threadId) {
    return setMessagesRead(THREAD_ID + " = ? AND " + STORY_TYPE + " = 0 AND " + PARENT_STORY_ID + " <= 0", new String[] { String.valueOf(threadId)});
  }

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
      cursor = database.query(TABLE_NAME + " INDEXED BY " + INDEX_THREAD_DATE, new String[] { ID, RECIPIENT_ID, DATE_SENT, TYPE, EXPIRES_IN, EXPIRE_STARTED, THREAD_ID, STORY_TYPE }, where, arguments, null, null, null);

      while(cursor != null && cursor.moveToNext()) {
        if (MessageTypes.isSecureType(CursorUtil.requireLong(cursor, TYPE))) {
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
            result.add(new MarkedMessageInfo(threadId, syncMessageId, new MessageId(messageId), expirationInfo, storyType));
          }
        }
      }

      ContentValues contentValues = new ContentValues();
      contentValues.put(READ, 1);
      contentValues.put(REACTIONS_UNREAD, 0);
      contentValues.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

      database.update(TABLE_NAME + " INDEXED BY " + INDEX_THREAD_DATE, contentValues, where, arguments);
      database.setTransactionSuccessful();
    } finally {
      if (cursor != null) cursor.close();
      database.endTransaction();
    }

    return result;
  }

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
    String         trimmedCondition = " NOT IN (SELECT " + MessageTable.ID + " FROM " + MessageTable.TABLE_NAME + ")";

    database.delete(GroupReceiptTable.TABLE_NAME, GroupReceiptTable.MMS_ID + trimmedCondition, null);

    String[] columns = new String[] { AttachmentTable.ROW_ID, AttachmentTable.UNIQUE_ID };
    String   where   = AttachmentTable.MMS_ID + trimmedCondition;

    try (Cursor cursor = database.query(AttachmentTable.TABLE_NAME, columns, where, null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        SignalDatabase.attachments().deleteAttachment(new AttachmentId(cursor.getLong(0), cursor.getLong(1)));
      }
    }

    SignalDatabase.mentions().deleteAbandonedMentions();

    try (Cursor cursor = database.query(ThreadTable.TABLE_NAME, new String[] { ThreadTable.ID }, ThreadTable.EXPIRES_IN + " > 0", null, null, null, null)) {
      while (cursor != null && cursor.moveToNext()) {
        SignalDatabase.threads().setLastScrolled(cursor.getLong(0), 0);
        SignalDatabase.threads().update(cursor.getLong(0), false);
      }
    }
  }

  public Optional<MmsNotificationInfo> getNotification(long messageId) {
    Cursor cursor = null;

    try {
      cursor = rawQuery(RAW_ID_WHERE, new String[] {String.valueOf(messageId)});

      if (cursor != null && cursor.moveToNext()) {
        return Optional.of(new MmsNotificationInfo(RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID))),
                                                   cursor.getString(cursor.getColumnIndexOrThrow(MMS_CONTENT_LOCATION)),
                                                   cursor.getString(cursor.getColumnIndexOrThrow(MMS_TRANSACTION_ID)),
                                                   cursor.getInt(cursor.getColumnIndexOrThrow(SMS_SUBSCRIPTION_ID))));
      } else {
        return Optional.empty();
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public OutgoingMessage getOutgoingMessage(long messageId)
      throws MmsException, NoSuchMessageException
  {
    AttachmentTable attachmentDatabase = SignalDatabase.attachments();
    MentionTable    mentionDatabase    = SignalDatabase.mentions();
    Cursor          cursor             = null;

    try {
      cursor = rawQuery(RAW_ID_WHERE, new String[] {String.valueOf(messageId)});

      if (cursor != null && cursor.moveToNext()) {
        List<DatabaseAttachment> associatedAttachments = attachmentDatabase.getAttachmentsForMessage(messageId);
        List<Mention>            mentions              = mentionDatabase.getMentionsForMessage(messageId);

        long             outboxType         = cursor.getLong(cursor.getColumnIndexOrThrow(TYPE));
        String           body               = cursor.getString(cursor.getColumnIndexOrThrow(BODY));
        long             timestamp          = cursor.getLong(cursor.getColumnIndexOrThrow(DATE_SENT));
        int              subscriptionId     = cursor.getInt(cursor.getColumnIndexOrThrow(SMS_SUBSCRIPTION_ID));
        long             expiresIn          = cursor.getLong(cursor.getColumnIndexOrThrow(EXPIRES_IN));
        boolean          viewOnce           = cursor.getLong(cursor.getColumnIndexOrThrow(VIEW_ONCE)) == 1;
        long             recipientId        = cursor.getLong(cursor.getColumnIndexOrThrow(RECIPIENT_ID));
        long             threadId           = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
        int              distributionType   = SignalDatabase.threads().getDistributionType(threadId);
        String           mismatchDocument   = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.MISMATCHED_IDENTITIES));
        String           networkDocument    = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.NETWORK_FAILURES));
        StoryType        storyType          = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));
        ParentStoryId    parentStoryId      = ParentStoryId.deserialize(CursorUtil.requireLong(cursor, PARENT_STORY_ID));
        byte[]           messageRangesData  = CursorUtil.requireBlob(cursor, MESSAGE_RANGES);
        long             scheduledDate      = cursor.getLong(cursor.getColumnIndexOrThrow(SCHEDULED_DATE));

        long              quoteId            = cursor.getLong(cursor.getColumnIndexOrThrow(QUOTE_ID));
        long              quoteAuthor        = cursor.getLong(cursor.getColumnIndexOrThrow(QUOTE_AUTHOR));
        String            quoteText          = cursor.getString(cursor.getColumnIndexOrThrow(QUOTE_BODY));
        int               quoteType          = cursor.getInt(cursor.getColumnIndexOrThrow(QUOTE_TYPE));
        boolean           quoteMissing       = cursor.getInt(cursor.getColumnIndexOrThrow(QUOTE_MISSING)) == 1;
        List<Attachment>  quoteAttachments   = Stream.of(associatedAttachments).filter(Attachment::isQuote).map(a -> (Attachment)a).toList();
        List<Mention>     quoteMentions      = parseQuoteMentions(cursor);
        BodyRangeList     quoteBodyRanges    = parseQuoteBodyRanges(cursor);
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
          quote = new QuoteModel(quoteId, RecipientId.from(quoteAuthor), quoteText, quoteMissing, quoteAttachments, quoteMentions, QuoteModel.Type.fromCode(quoteType), quoteBodyRanges);
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

        if (body != null && (MessageTypes.isGroupQuit(outboxType) || MessageTypes.isGroupUpdate(outboxType))) {
          return OutgoingMessage.groupUpdateMessage(recipient, new MessageGroupContext(body, MessageTypes.isGroupV2(outboxType)), attachments, timestamp, 0, false, quote, contacts, previews, mentions);
        } else if (MessageTypes.isExpirationTimerUpdate(outboxType)) {
          return OutgoingMessage.expirationUpdateMessage(recipient, timestamp, expiresIn);
        } else if (MessageTypes.isPaymentsNotification(outboxType)) {
          return OutgoingMessage.paymentNotificationMessage(recipient, Objects.requireNonNull(body), timestamp, expiresIn);
        } else if (MessageTypes.isPaymentsRequestToActivate(outboxType)) {
          return OutgoingMessage.requestToActivatePaymentsMessage(recipient, timestamp, expiresIn);
        } else if (MessageTypes.isPaymentsActivated(outboxType)) {
          return OutgoingMessage.paymentsActivatedMessage(recipient, timestamp, expiresIn);
        }

        GiftBadge giftBadge = null;
        if (body != null && MessageTypes.isGiftBadge(outboxType)) {
          giftBadge = GiftBadge.parseFrom(Base64.decode(body));
        }

        BodyRangeList messageRanges = null;
        if (messageRangesData != null) {
          try {
            messageRanges = BodyRangeList.parseFrom(messageRangesData);
          } catch (InvalidProtocolBufferException e) {
            Log.w(TAG, "Error parsing message ranges", e);
          }
        }

        OutgoingMessage message = new OutgoingMessage(recipient,
                                                      body,
                                                      attachments,
                                                      timestamp,
                                                      subscriptionId,
                                                      expiresIn,
                                                      viewOnce,
                                                      distributionType,
                                                      storyType,
                                                      parentStoryId,
                                                      MessageTypes.isStoryReaction(outboxType),
                                                      quote,
                                                      contacts,
                                                      previews,
                                                      mentions,
                                                      networkFailures,
                                                      mismatches,
                                                      giftBadge,
                                                      MessageTypes.isSecureType(outboxType),
                                                      messageRanges,
                                                      scheduledDate);

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

    boolean silentUpdate = (mailbox & MessageTypes.GROUP_UPDATE_BIT) > 0;

    contentValues.put(DATE_SENT, retrieved.getSentTimeMillis());
    contentValues.put(DATE_SERVER, retrieved.getServerTimeMillis());
    contentValues.put(RECIPIENT_ID, retrieved.getFrom().serialize());

    contentValues.put(TYPE, mailbox);
    contentValues.put(MMS_MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(MMS_CONTENT_LOCATION, contentLocation);
    contentValues.put(MMS_STATUS, MmsStatus.DOWNLOAD_INITIALIZED);
    contentValues.put(DATE_RECEIVED, retrieved.isPushMessage() ? retrieved.getReceivedTimeMillis() : generatePduCompatTimestamp(retrieved.getReceivedTimeMillis()));
    contentValues.put(SMS_SUBSCRIPTION_ID, retrieved.getSubscriptionId());
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
      contentValues.put(QUOTE_BODY, retrieved.getQuote().getText());
      contentValues.put(QUOTE_AUTHOR, retrieved.getQuote().getAuthor().serialize());
      contentValues.put(QUOTE_TYPE, retrieved.getQuote().getType().getCode());
      contentValues.put(QUOTE_MISSING, retrieved.getQuote().isOriginalMissing() ? 1 : 0);

      BodyRangeList.Builder quoteBodyRanges = retrieved.getQuote().getBodyRanges() != null ? retrieved.getQuote().getBodyRanges().toBuilder()
                                                                                           : BodyRangeList.newBuilder();

      BodyRangeList mentionsList = MentionUtil.mentionsToBodyRangeList(retrieved.getQuote().getMentions());
      if (mentionsList != null) {
        quoteBodyRanges.addAllRanges(mentionsList.getRangesList());
      }

      if (quoteBodyRanges.getRangesCount() > 0) {
        contentValues.put(QUOTE_BODY_RANGES, quoteBodyRanges.build().toByteArray());
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
    if (!MessageTypes.isPaymentsActivated(mailbox) && !MessageTypes.isPaymentsRequestToActivate(mailbox) && !MessageTypes.isExpirationTimerUpdate(mailbox) && !retrieved.getStoryType().isStory() && isNotStoryGroupReply) {
      boolean incrementUnreadMentions = !retrieved.getMentions().isEmpty() && retrieved.getMentions().stream().anyMatch(m -> m.getRecipientId().equals(Recipient.self().getId()));
      SignalDatabase.threads().incrementUnread(threadId, 1, incrementUnreadMentions ? 1 : 0);
      SignalDatabase.threads().update(threadId, !keepThreadArchived);
    }

    notifyConversationListeners(threadId);

    if (retrieved.getStoryType().isStory()) {
      ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(Objects.requireNonNull(SignalDatabase.threads().getRecipientIdForThreadId(threadId)));
    }

    return Optional.of(new InsertResult(messageId, threadId));
  }

  public Optional<InsertResult> insertMessageInbox(IncomingMediaMessage retrieved,
                                                   String contentLocation, long threadId)
      throws MmsException
  {
    long type = MessageTypes.BASE_INBOX_TYPE;

    if (retrieved.isPushMessage()) {
      type |= MessageTypes.PUSH_MESSAGE_BIT;
    }

    if (retrieved.isExpirationUpdate()) {
      type |= MessageTypes.EXPIRATION_TIMER_UPDATE_BIT;
    }

    if (retrieved.isPaymentsNotification()) {
      type |= MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION;
    }

    if (retrieved.isActivatePaymentsRequest()) {
      type |= MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST;
    }

    if (retrieved.isPaymentsActivated()) {
      type |= MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED;
    }

    return insertMessageInbox(retrieved, contentLocation, threadId, type);
  }

  public Optional<InsertResult> insertSecureDecryptedMessageInbox(IncomingMediaMessage retrieved, long threadId)
      throws MmsException
  {
    long type = MessageTypes.BASE_INBOX_TYPE | MessageTypes.SECURE_MESSAGE_BIT;

    if (retrieved.isPushMessage()) {
      type |= MessageTypes.PUSH_MESSAGE_BIT;
    }

    if (retrieved.isExpirationUpdate()) {
      type |= MessageTypes.EXPIRATION_TIMER_UPDATE_BIT;
    }

    boolean hasSpecialType = false;
    if (retrieved.isStoryReaction()) {
      hasSpecialType = true;
      type |= MessageTypes.SPECIAL_TYPE_STORY_REACTION;
    }

    if (retrieved.getGiftBadge() != null) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }

      type |= MessageTypes.SPECIAL_TYPE_GIFT_BADGE;
    }

    if (retrieved.isPaymentsNotification()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION;
    }

    if (retrieved.isActivatePaymentsRequest()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST;
    }

    if (retrieved.isPaymentsActivated()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED;
    }

    return insertMessageInbox(retrieved, "", threadId, type);
  }

  public Pair<Long, Long> insertMessageInbox(@NonNull NotificationInd notification, int subscriptionId) {
    SQLiteDatabase       db             = databaseHelper.getSignalWritableDatabase();
    long                 threadId       = getThreadIdFor(notification);
    ContentValues        contentValues  = new ContentValues();
    ContentValuesBuilder contentBuilder = new ContentValuesBuilder(contentValues);

    Log.i(TAG, "Message received type: " + notification.getMessageType());

    contentBuilder.add(MMS_CONTENT_LOCATION, notification.getContentLocation());
    contentBuilder.add(DATE_SENT, System.currentTimeMillis());
    contentBuilder.add(MMS_EXPIRY, notification.getExpiry());
    contentBuilder.add(MMS_MESSAGE_SIZE, notification.getMessageSize());
    contentBuilder.add(MMS_TRANSACTION_ID, notification.getTransactionId());
    contentBuilder.add(MMS_MESSAGE_TYPE, notification.getMessageType());

    if (notification.getFrom() != null) {
      Recipient recipient = Recipient.external(context, Util.toIsoString(notification.getFrom().getTextString()));
      contentValues.put(RECIPIENT_ID, recipient.getId().serialize());
    } else {
      contentValues.put(RECIPIENT_ID, RecipientId.UNKNOWN.serialize());
    }

    contentValues.put(TYPE, MessageTypes.BASE_INBOX_TYPE);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(MMS_STATUS, MmsStatus.DOWNLOAD_INITIALIZED);
    contentValues.put(DATE_RECEIVED, generatePduCompatTimestamp(System.currentTimeMillis()));
    contentValues.put(READ, Util.isDefaultSmsProvider(context) ? 0 : 1);
    contentValues.put(SMS_SUBSCRIPTION_ID, subscriptionId);

    if (!contentValues.containsKey(DATE_SENT))
      contentValues.put(DATE_SENT, contentValues.getAsLong(DATE_RECEIVED));

    long messageId = db.insert(TABLE_NAME, null, contentValues);

    return new Pair<>(messageId, threadId);
  }

  public @NonNull InsertResult insertChatSessionRefreshedMessage(@NonNull RecipientId recipientId, long senderDeviceId, long sentTimestamp) {
    SQLiteDatabase db       = databaseHelper.getSignalWritableDatabase();
    long           threadId = SignalDatabase.threads().getOrCreateThreadIdFor(Recipient.resolved(recipientId));
    long           type     = MessageTypes.SECURE_MESSAGE_BIT | MessageTypes.PUSH_MESSAGE_BIT;

    type = type & (MessageTypes.TOTAL_MASK - MessageTypes.ENCRYPTION_MASK) | MessageTypes.ENCRYPTION_REMOTE_FAILED_BIT;

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

  public void insertBadDecryptMessage(@NonNull RecipientId recipientId, int senderDevice, long sentTimestamp, long receivedTimestamp, long threadId) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, recipientId.serialize());
    values.put(RECIPIENT_DEVICE_ID, senderDevice);
    values.put(DATE_SENT, sentTimestamp);
    values.put(DATE_RECEIVED, receivedTimestamp);
    values.put(DATE_SERVER, -1);
    values.put(READ, 0);
    values.put(TYPE, MessageTypes.BAD_DECRYPT_TYPE);
    values.put(THREAD_ID, threadId);

    databaseHelper.getSignalWritableDatabase().insert(TABLE_NAME, null, values);

    SignalDatabase.threads().incrementUnread(threadId, 1, 0);
    boolean keepThreadArchived = SignalStore.settings().shouldKeepMutedChatsArchived() && Recipient.resolved(recipientId).isMuted();
    SignalDatabase.threads().update(threadId, !keepThreadArchived);

    notifyConversationListeners(threadId);

    TrimThreadJob.enqueueAsync(threadId);
  }

  public void markIncomingNotificationReceived(long threadId) {
    notifyConversationListeners(threadId);

    if (org.thoughtcrime.securesms.util.Util.isDefaultSmsProvider(context)) {
      SignalDatabase.threads().incrementUnread(threadId, 1, 0);
    }

    SignalDatabase.threads().update(threadId, true);

    TrimThreadJob.enqueueAsync(threadId);
  }

  public void markGiftRedemptionCompleted(long messageId) {
    markGiftRedemptionState(messageId, GiftBadge.RedemptionState.REDEEMED);
  }

  public void markGiftRedemptionStarted(long messageId) {
    markGiftRedemptionState(messageId, GiftBadge.RedemptionState.STARTED);
  }

  public void markGiftRedemptionFailed(long messageId) {
    markGiftRedemptionState(messageId, GiftBadge.RedemptionState.FAILED);
  }

  private void markGiftRedemptionState(long messageId, @NonNull GiftBadge.RedemptionState redemptionState) {
    String[] projection = SqlUtil.buildArgs(BODY, THREAD_ID);
    String   where      = "(" + TYPE + " & " + MessageTypes.SPECIAL_TYPES_MASK + " = " + MessageTypes.SPECIAL_TYPE_GIFT_BADGE + ") AND " +
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
      ApplicationDependencies.getDatabaseObserver().notifyMessageUpdateObservers(new MessageId(messageId));
      notifyConversationListeners(threadId);
    }
  }

  public long insertMessageOutbox(@NonNull OutgoingMessage message,
                                  long threadId,
                                  boolean forceSms,
                                  @Nullable InsertListener insertListener)
      throws MmsException
  {
    return insertMessageOutbox(message, threadId, forceSms, GroupReceiptTable.STATUS_UNDELIVERED, insertListener);
  }

  public long insertMessageOutbox(@NonNull OutgoingMessage message,
                                  long threadId, boolean forceSms, int defaultReceiptStatus,
                                  @Nullable InsertListener insertListener)
      throws MmsException
  {
    long type = MessageTypes.BASE_SENDING_TYPE;

    if (message.isSecure()) type |= (MessageTypes.SECURE_MESSAGE_BIT | MessageTypes.PUSH_MESSAGE_BIT);
    if (forceSms)           type |= MessageTypes.MESSAGE_FORCE_SMS_BIT;

    if      (message.isSecure())        type |= (MessageTypes.SECURE_MESSAGE_BIT | MessageTypes.PUSH_MESSAGE_BIT);
    else if (message.isEndSession())    type |= MessageTypes.END_SESSION_BIT;

    if      (message.isIdentityVerified()) type |= MessageTypes.KEY_EXCHANGE_IDENTITY_VERIFIED_BIT;
    else if (message.isIdentityDefault())  type |= MessageTypes.KEY_EXCHANGE_IDENTITY_DEFAULT_BIT;

    if (message.isGroup()) {
      if (message.isV2Group()) {
        type |= MessageTypes.GROUP_V2_BIT | MessageTypes.GROUP_UPDATE_BIT;
        if (message.isJustAGroupLeave()) {
          type |= MessageTypes.GROUP_LEAVE_BIT;
        }
      } else {
        MessageGroupContext.GroupV1Properties properties = message.requireGroupV1Properties();
        if      (properties.isUpdate()) type |= MessageTypes.GROUP_UPDATE_BIT;
        else if (properties.isQuit())   type |= MessageTypes.GROUP_LEAVE_BIT;
      }
    }

    if (message.isExpirationUpdate()) {
      type |= MessageTypes.EXPIRATION_TIMER_UPDATE_BIT;
    }

    boolean hasSpecialType = false;
    if (message.isStoryReaction()) {
      hasSpecialType = true;
      type |= MessageTypes.SPECIAL_TYPE_STORY_REACTION;
    }

    if (message.getGiftBadge() != null) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }

      type |= MessageTypes.SPECIAL_TYPE_GIFT_BADGE;
    }

    if (message.isPaymentsNotification()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION;
    }

    if (message.isRequestToActivatePayments()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST;
    }

    if (message.isPaymentsActivated()) {
      if (hasSpecialType) {
        throw new MmsException("Cannot insert message with multiple special types.");
      }
      type |= MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATED;
    }

    Map<RecipientId, EarlyReceiptCache.Receipt> earlyDeliveryReceipts = earlyDeliveryReceiptCache.remove(message.getSentTimeMillis());

    if (earlyDeliveryReceipts.size() > 0) {
      Log.w(TAG, "Found early delivery receipts for " + message.getSentTimeMillis() + ". Applying them.");
    }

    ContentValues contentValues = new ContentValues();
    contentValues.put(DATE_SENT, message.getSentTimeMillis());
    contentValues.put(MMS_MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ);

    contentValues.put(TYPE, type);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(READ, 1);
    contentValues.put(DATE_RECEIVED, System.currentTimeMillis());
    contentValues.put(SMS_SUBSCRIPTION_ID, message.getSubscriptionId());
    contentValues.put(EXPIRES_IN, message.getExpiresIn());
    contentValues.put(VIEW_ONCE, message.isViewOnce());
    contentValues.put(RECIPIENT_ID, message.getRecipient().getId().serialize());
    contentValues.put(DELIVERY_RECEIPT_COUNT, Stream.of(earlyDeliveryReceipts.values()).mapToLong(EarlyReceiptCache.Receipt::getCount).sum());
    contentValues.put(RECEIPT_TIMESTAMP, Stream.of(earlyDeliveryReceipts.values()).mapToLong(EarlyReceiptCache.Receipt::getTimestamp).max().orElse(-1));
    contentValues.put(STORY_TYPE, message.getStoryType().getCode());
    contentValues.put(PARENT_STORY_ID, message.getParentStoryId() != null ? message.getParentStoryId().serialize() : 0);
    contentValues.put(SCHEDULED_DATE, message.getScheduledDate());

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

      BodyRangeList adjustedQuoteBodyRanges = BodyRangeUtil.adjustBodyRanges(message.getOutgoingQuote().getBodyRanges(), updated.getBodyAdjustments());
      BodyRangeList.Builder quoteBodyRanges;
      if (adjustedQuoteBodyRanges != null) {
        quoteBodyRanges = adjustedQuoteBodyRanges.toBuilder();
      } else {
        quoteBodyRanges = BodyRangeList.newBuilder();
      }

      BodyRangeList mentionsList = MentionUtil.mentionsToBodyRangeList(updated.getMentions());
      if (mentionsList != null) {
        quoteBodyRanges.addAllRanges(mentionsList.getRangesList());
      }

      if (quoteBodyRanges.getRangesCount() > 0) {
        contentValues.put(QUOTE_BODY_RANGES, quoteBodyRanges.build().toByteArray());
      }

      quoteAttachments.addAll(message.getOutgoingQuote().getAttachments());
    }

    MentionUtil.UpdatedBodyAndMentions updatedBodyAndMentions = MentionUtil.updateBodyAndMentionsWithPlaceholders(message.getBody(), message.getMentions());
    BodyRangeList bodyRanges = BodyRangeUtil.adjustBodyRanges(message.getBodyRanges(), updatedBodyAndMentions.getBodyAdjustments());

    long messageId = insertMediaMessage(threadId, updatedBodyAndMentions.getBodyAsString(), message.getAttachments(), quoteAttachments, message.getSharedContacts(), message.getLinkPreviews(), updatedBodyAndMentions.getMentions(), bodyRanges, contentValues, insertListener, false, false);

    if (message.getRecipient().isGroup()) {
      GroupReceiptTable receiptDatabase = SignalDatabase.groupReceipts();
      Set<RecipientId>  members         = new HashSet<>();

      if (message.isGroupUpdate() && message.isV2Group()) {
        MessageGroupContext.GroupV2Properties groupV2Properties = message.requireGroupV2Properties();
        members.addAll(Stream.of(groupV2Properties.getAllActivePendingAndRemovedMembers())
                             .distinct()
                             .map(uuid -> RecipientId.from(ServiceId.from(uuid)))
                             .toList());
        members.remove(Recipient.self().getId());
      } else {
        members.addAll(Stream.of(SignalDatabase.groups().getGroupMembers(message.getRecipient().requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF)).map(Recipient::getId).toList());
      }

      receiptDatabase.insert(members, messageId, defaultReceiptStatus, message.getSentTimeMillis());

      for (RecipientId recipientId : earlyDeliveryReceipts.keySet()) {
        receiptDatabase.update(recipientId, messageId, GroupReceiptTable.STATUS_DELIVERED, -1);
      }
    } else if (message.getRecipient().isDistributionList()) {
      GroupReceiptTable receiptDatabase = SignalDatabase.groupReceipts();
      List<RecipientId> members         = SignalDatabase.distributionLists().getMembers(message.getRecipient().requireDistributionListId());

      receiptDatabase.insert(members, messageId, defaultReceiptStatus, message.getSentTimeMillis());

      for (RecipientId recipientId : earlyDeliveryReceipts.keySet()) {
        receiptDatabase.update(recipientId, messageId, GroupReceiptTable.STATUS_DELIVERED, -1);
      }
    }

    SignalDatabase.threads().updateLastSeenAndMarkSentAndLastScrolledSilenty(threadId);

    if (!message.getStoryType().isStory()) {
      if (message.getOutgoingQuote() == null) {
        ApplicationDependencies.getDatabaseObserver().notifyMessageInsertObservers(threadId, new MessageId(messageId));
      } else {
        ApplicationDependencies.getDatabaseObserver().notifyConversationListeners(threadId);
      }
      if (message.getScheduledDate() != -1) {
        ApplicationDependencies.getDatabaseObserver().notifyScheduledMessageObservers(threadId);
      }
    } else {
      ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(message.getRecipient().getId());
    }

    notifyConversationListListeners();

    if (!message.isIdentityVerified() && !message.isIdentityDefault()) {
      TrimThreadJob.enqueueAsync(threadId);
    }

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
    SQLiteDatabase  db              = databaseHelper.getSignalWritableDatabase();
    AttachmentTable partsDatabase   = SignalDatabase.attachments();
    MentionTable    mentionDatabase = SignalDatabase.mentions();

    boolean mentionsSelf = Stream.of(mentions).filter(m -> Recipient.resolved(m.getRecipientId()).isSelf()).findFirst().isPresent();

    List<Attachment> allAttachments     = new LinkedList<>();
    List<Attachment> contactAttachments = Stream.of(sharedContacts).map(Contact::getAvatarAttachment).filter(a -> a != null).toList();
    List<Attachment> previewAttachments = Stream.of(linkPreviews).filter(lp -> lp.getThumbnail().isPresent()).map(lp -> lp.getThumbnail().get()).toList();

    allAttachments.addAll(attachments);
    allAttachments.addAll(contactAttachments);
    allAttachments.addAll(previewAttachments);

    contentValues.put(BODY, body);
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

  public boolean deleteMessage(long messageId) {
    long threadId = getThreadIdForMessage(messageId);
    return deleteMessage(messageId, threadId);
  }

  public boolean deleteMessage(long messageId, boolean notify) {
    long threadId = getThreadIdForMessage(messageId);
    return deleteMessage(messageId, threadId, notify);
  }

  public boolean deleteMessage(long messageId, long threadId) {
    return deleteMessage(messageId, threadId, true);
  }

  private boolean deleteMessage(long messageId, long threadId, boolean notify) {
    Log.d(TAG, "deleteMessage(" + messageId + ")");

    AttachmentTable attachmentDatabase = SignalDatabase.attachments();
    attachmentDatabase.deleteAttachmentsForMessage(messageId);

    GroupReceiptTable groupReceiptDatabase = SignalDatabase.groupReceipts();
    groupReceiptDatabase.deleteRowsForMessage(messageId);

    MentionTable mentionDatabase = SignalDatabase.mentions();
    mentionDatabase.deleteMentionsForMessage(messageId);

    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    database.delete(TABLE_NAME, ID_WHERE, new String[] {messageId+""});

    SignalDatabase.threads().setLastScrolled(threadId, 0);
    boolean threadDeleted = SignalDatabase.threads().update(threadId, false);

    if (notify) {
      notifyConversationListeners(threadId);
      notifyStickerListeners();
      notifyStickerPackListeners();
      OptimizeMessageSearchIndexJob.enqueue();
    }

    return threadDeleted;
  }

  public void deleteScheduledMessage(long messageId) {
    Log.d(TAG, "deleteScheduledMessage(" + messageId + ")");
    SQLiteDatabase db = databaseHelper.getSignalWritableDatabase();
    long threadId = getThreadIdForMessage(messageId);
    db.beginTransaction();
    try {
      ContentValues contentValues = new ContentValues();
      contentValues.put(SCHEDULED_DATE, -1);
      contentValues.put(DATE_SENT, System.currentTimeMillis());
      contentValues.put(DATE_RECEIVED, System.currentTimeMillis());

      int rowsUpdated = db.update(TABLE_NAME, contentValues, ID_WHERE + " AND " + SCHEDULED_DATE + "!= ?", SqlUtil.buildArgs(messageId, -1));
      if (rowsUpdated > 0) {
        deleteMessage(messageId, threadId);
        db.setTransactionSuccessful();
      } else {
        Log.w(TAG, "tried to delete scheduled message but it may have already been sent");
      }
    } finally {
      db.endTransaction();
    }
    ApplicationDependencies.getScheduledMessageManager().scheduleIfNecessary();
    ApplicationDependencies.getDatabaseObserver().notifyScheduledMessageObservers(threadId);
  }

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

  private boolean isDuplicate(IncomingTextMessage message, long threadId) {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         query = DATE_SENT + " = ? AND " + RECIPIENT_ID + " = ? AND " + THREAD_ID + " = ?";
    String[]       args  = SqlUtil.buildArgs(message.getSentTimestampMillis(), message.getSender().serialize(), threadId);

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { "1" }, query, args, null, null, null, "1")) {
      return cursor.moveToFirst();
    }
  }

  public boolean isSent(long messageId) {
    SQLiteDatabase database = databaseHelper.getSignalReadableDatabase();
    try (Cursor cursor = database.query(TABLE_NAME, new String[] { TYPE }, ID + " = ?", new String[] { String.valueOf(messageId)}, null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        long type = cursor.getLong(cursor.getColumnIndexOrThrow(TYPE));
        return MessageTypes.isSentType(type);
      }
    }
    return false;
  }

  public List<MessageRecord> getProfileChangeDetailsRecords(long threadId, long afterTimestamp) {
    String   where = THREAD_ID + " = ? AND " + DATE_RECEIVED + " >= ? AND " + TYPE + " = ?";
    String[] args  = SqlUtil.buildArgs(threadId, afterTimestamp, MessageTypes.PROFILE_CHANGE_TYPE);

    try (MmsReader reader = mmsReaderFor(queryMessages(where, args, true, -1))) {
      List<MessageRecord> results = new ArrayList<>(reader.getCount());
      while (reader.getNext() != null) {
        results.add(reader.getCurrent());
      }

      return results;
    }
  }

  private Cursor queryMessages(@NonNull String where, @Nullable String[] args, boolean reverse, long limit) {
    return queryMessages(MMS_PROJECTION, where, args, reverse, limit);
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

  public Set<Long> getAllRateLimitedMessageIds() {
    SQLiteDatabase db    = databaseHelper.getSignalReadableDatabase();
    String         where = "(" + TYPE + " & " + MessageTypes.TOTAL_MASK + " & " + MessageTypes.MESSAGE_RATE_LIMITED_BIT + ") > 0";

    Set<Long> ids = new HashSet<>();

    try (Cursor cursor = db.query(TABLE_NAME, new String[] { ID }, where, null, null, null, null)) {
      while (cursor.moveToNext()) {
        ids.add(CursorUtil.requireLong(cursor, ID));
      }
    }

    return ids;
  }

  public Cursor getUnexportedInsecureMessages(int limit) {
    return rawQuery(
        SqlUtil.appendArg(MMS_PROJECTION_WITH_ATTACHMENTS, EXPORT_STATE),
        getInsecureMessageClause() + " AND NOT " + EXPORTED,
        null,
        false,
        limit
    );
  }

  public long getUnexportedInsecureMessagesEstimatedSize() {
    Cursor messageTextSize = SQLiteDatabaseExtensionsKt.select(getReadableDatabase(), "SUM(LENGTH(" + BODY + "))")
                                                       .from(TABLE_NAME)
                                                       .where(getInsecureMessageClause() + " AND " + EXPORTED + " < ?", MessageExportStatus.EXPORTED)
                                                       .run();

    long bodyTextSize = CursorExtensionsKt.readToSingleLong(messageTextSize);

    String select   = "SUM(" + AttachmentTable.TABLE_NAME + "." + AttachmentTable.SIZE + ") AS s";
    String fromJoin = TABLE_NAME + " INNER JOIN " + AttachmentTable.TABLE_NAME + " ON " + TABLE_NAME + "." + ID + " = " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID;
    String where    = getInsecureMessageClause() + " AND " + EXPORTED + " < " + MessageExportStatus.EXPORTED.serialize();

    long fileSize = CursorExtensionsKt.readToSingleLong(getReadableDatabase().rawQuery("SELECT " + select + " FROM " + fromJoin + " WHERE " + where, null));

    return bodyTextSize + fileSize;
  }

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
      OptimizeMessageSearchIndexJob.enqueue();
    }
  }

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
        deleteMessage(cursor.getLong(0), false);
      }
    }

    notifyConversationListeners(threadIds);
    notifyStickerListeners();
    notifyStickerPackListeners();
    OptimizeMessageSearchIndexJob.enqueue();
  }

  int deleteMessagesInThreadBeforeDate(long threadId, long date) {
    SQLiteDatabase db    = databaseHelper.getSignalWritableDatabase();
    String         where = THREAD_ID + " = ? AND " + DATE_RECEIVED + " < " + date;

    return db.delete(TABLE_NAME, where, SqlUtil.buildArgs(threadId));
  }

  int deleteAbandonedMessages() {
    SQLiteDatabase db    = databaseHelper.getSignalWritableDatabase();
    String         where = THREAD_ID + " NOT IN (SELECT _id FROM " + ThreadTable.TABLE_NAME + ")";

    int deletes = db.delete(TABLE_NAME, where, null);
    if (deletes > 0) {
      Log.i(TAG, "Deleted " + deletes + " abandoned messages");
    }
    return deletes;
  }

  public void deleteRemotelyDeletedStory(long messageId) {
    try (Cursor cursor = getMessageCursor(messageId)) {
      if (cursor.moveToFirst() && CursorUtil.requireBoolean(cursor, REMOTE_DELETED)) {
        deleteMessage(messageId);
      } else {
        Log.i(TAG, "Unable to delete remotely deleted story: " + messageId);
      }
    }
  }

  public List<MessageRecord> getMessagesInThreadAfterInclusive(long threadId, long timestamp, long limit) {
    String   where = TABLE_NAME + "." + THREAD_ID + " = ? AND " +
                     TABLE_NAME + "." + DATE_RECEIVED + " >= ?";
    String[] args  = SqlUtil.buildArgs(threadId, timestamp);

    try (MmsReader reader = mmsReaderFor(rawQuery(where, args, false, limit))) {
      List<MessageRecord> results = new ArrayList<>(reader.cursor.getCount());

      while (reader.getNext() != null) {
        results.add(reader.getCurrent());
      }

      return results;
    }
  }

  public void deleteAllThreads() {
    Log.d(TAG, "deleteAllThreads()");
    SignalDatabase.attachments().deleteAllAttachments();
    SignalDatabase.groupReceipts().deleteAllRows();
    SignalDatabase.mentions().deleteAllMentions();

    SQLiteDatabase database = databaseHelper.getSignalWritableDatabase();
    database.delete(TABLE_NAME, null, null);
    OptimizeMessageSearchIndexJob.enqueue();
  }

  public @Nullable ViewOnceExpirationInfo getNearestExpiringViewOnceMessage() {
    SQLiteDatabase       db                = databaseHelper.getSignalReadableDatabase();
    ViewOnceExpirationInfo info              = null;
    long                 nearestExpiration = Long.MAX_VALUE;

    String   query = "SELECT " +
                     TABLE_NAME + "." + ID + ", " +
                     VIEW_ONCE + ", " +
                     DATE_RECEIVED + " " +
                     "FROM " + TABLE_NAME + " INNER JOIN " + AttachmentTable.TABLE_NAME + " " +
                     "ON " + TABLE_NAME + "." + ID + " = " + AttachmentTable.TABLE_NAME + "." + AttachmentTable.MMS_ID + " " +
                     "WHERE " +
                     VIEW_ONCE + " > 0 AND " +
                     "(" + AttachmentTable.DATA + " NOT NULL OR " + AttachmentTable.TRANSFER_STATE + " != ?)";
    String[] args = new String[] { String.valueOf(AttachmentTable.TRANSFER_PROGRESS_DONE) };

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

  /**
   * The number of change number messages in the thread.
   * Currently only used for tests.
   */
  @VisibleForTesting
  int getChangeNumberMessageCount(@NonNull RecipientId recipientId) {
    try (Cursor cursor = SQLiteDatabaseExtensionsKt
        .select(getReadableDatabase(), "COUNT(*)")
        .from(TABLE_NAME)
        .where(RECIPIENT_ID + " = ? AND " + TYPE + " = ?", recipientId, MessageTypes.CHANGE_NUMBER_TYPE)
        .run())
    {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  private static @NonNull List<Mention> parseQuoteMentions(@NonNull Cursor cursor) {
    byte[]        raw        = cursor.getBlob(cursor.getColumnIndexOrThrow(QUOTE_BODY_RANGES));
    BodyRangeList bodyRanges = null;

    if (raw != null) {
      try {
        bodyRanges = BodyRangeList.parseFrom(raw);
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "Unable to parse quote body ranges", e);
      }
    }

    return MentionUtil.bodyRangeListToMentions(bodyRanges);
  }

  private static @Nullable BodyRangeList parseQuoteBodyRanges(@NonNull Cursor cursor) {
    byte[] data = cursor.getBlob(cursor.getColumnIndexOrThrow(QUOTE_BODY_RANGES));

    if (data != null) {
      try {
        final List<BodyRangeList.BodyRange> bodyRanges = Stream.of(BodyRangeList.parseFrom(data).getRangesList())
                                                               .filter(bodyRange -> bodyRange.getAssociatedValueCase() != BodyRangeList.BodyRange.AssociatedValueCase.MENTIONUUID)
                                                               .toList();

        return BodyRangeList.newBuilder().addAllRanges(bodyRanges).build();
      } catch (InvalidProtocolBufferException e) {
        // Intentionally left blank
      }
    }

    return null;
  }

  public SQLiteDatabase beginTransaction() {
    databaseHelper.getSignalWritableDatabase().beginTransaction();
    return databaseHelper.getSignalWritableDatabase();
  }

  public void setTransactionSuccessful() {
    databaseHelper.getSignalWritableDatabase().setTransactionSuccessful();
  }

  public void endTransaction() {
    databaseHelper.getSignalWritableDatabase().endTransaction();
  }

  public static MmsReader mmsReaderFor(Cursor cursor) {
    return new MmsReader(cursor);
  }

  public static OutgoingMmsReader readerFor(OutgoingMessage message, long threadId) {
    return new OutgoingMmsReader(message, threadId);
  }

  @VisibleForTesting
  Optional<InsertResult> collapseJoinRequestEventsIfPossible(long threadId, IncomingGroupUpdateMessage message) {
    InsertResult result = null;


    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();

    try {
      try (MessageTable.Reader reader = MessageTable.mmsReaderFor(getConversation(threadId, 0, 2))) {
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

  final @NonNull String getOutgoingTypeClause() {
    List<String> segments = new ArrayList<>(MessageTypes.OUTGOING_MESSAGE_TYPES.length);
    for (long outgoingMessageType : MessageTypes.OUTGOING_MESSAGE_TYPES) {
      segments.add("(" + TABLE_NAME + "." + TYPE + " & " + MessageTypes.BASE_TYPE_MASK + " = " + outgoingMessageType + ")");
    }

    return Util.join(segments, " OR ");
  }

  public final int getInsecureMessageSentCount(long threadId) {
    SQLiteDatabase db         = databaseHelper.getSignalReadableDatabase();
    String[]       projection = new String[]{"COUNT(*)"};
    String         query      = THREAD_ID + " = ? AND " + getOutgoingInsecureMessageClause() + " AND " + DATE_SENT + " > ?";
    String[]       args       = new String[]{String.valueOf(threadId), String.valueOf(System.currentTimeMillis() - InsightsConstants.PERIOD_IN_MILLIS)};

    try (Cursor cursor = db.query(TABLE_NAME, projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  public final int getInsecureMessageCountForInsights() {
    return getMessageCountForRecipientsAndType(getOutgoingInsecureMessageClause());
  }

  public int getInsecureMessageCount() {
    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, SqlUtil.COUNT, getInsecureMessageClause(), null, null, null, null)) {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public boolean hasSmsExportMessage(long threadId) {
    return SQLiteDatabaseExtensionsKt.exists(getReadableDatabase(), TABLE_NAME)
                                     .where(THREAD_ID_WHERE + " AND " + TYPE + " = ?", threadId, MessageTypes.SMS_EXPORT_TYPE)
                                     .run();
  }

  public final int getSecureMessageCountForInsights() {
    return getMessageCountForRecipientsAndType(getOutgoingSecureMessageClause());
  }

  public final int getSecureMessageCount(long threadId) {
    SQLiteDatabase db           = databaseHelper.getSignalReadableDatabase();
    String[]       projection   = new String[] {"COUNT(*)"};
    String         query        = getSecureMessageClause() + "AND " + THREAD_ID + " = ?";
    String[]       args         = new String[]{String.valueOf(threadId)};

    try (Cursor cursor = db.query(TABLE_NAME, projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  public final int getOutgoingSecureMessageCount(long threadId) {
    SQLiteDatabase db           = databaseHelper.getSignalReadableDatabase();
    String[]       projection   = new String[] {"COUNT(*)"};
    String         query        = getOutgoingSecureMessageClause() +
                                  "AND " + THREAD_ID + " = ? " +
                                  "AND (" + TYPE + " & " + MessageTypes.GROUP_LEAVE_BIT + " = 0 OR " + TYPE + " & " + MessageTypes.GROUP_V2_BIT + " = " + MessageTypes.GROUP_V2_BIT + ")";
    String[]       args         = new String[]{String.valueOf(threadId)};

    try (Cursor cursor = db.query(TABLE_NAME, projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  private int getMessageCountForRecipientsAndType(String typeClause) {

    SQLiteDatabase db           = databaseHelper.getSignalReadableDatabase();
    String[]       projection   = new String[] {"COUNT(*)"};
    String         query        = typeClause + " AND " + DATE_SENT + " > ?";
    String[]       args         = new String[]{String.valueOf(System.currentTimeMillis() - InsightsConstants.PERIOD_IN_MILLIS)};

    try (Cursor cursor = db.query(TABLE_NAME, projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  private String getOutgoingInsecureMessageClause() {
    return "(" + TYPE + " & " + MessageTypes.BASE_TYPE_MASK + ") = " + MessageTypes.BASE_SENT_TYPE + " AND NOT (" + TYPE + " & " + MessageTypes.SECURE_MESSAGE_BIT + ")";
  }

  private String getOutgoingSecureMessageClause() {
    return "(" + TYPE + " & " + MessageTypes.BASE_TYPE_MASK + ") = " + MessageTypes.BASE_SENT_TYPE + " AND (" + TYPE + " & " + (MessageTypes.SECURE_MESSAGE_BIT | MessageTypes.PUSH_MESSAGE_BIT) + ")";
  }

  private String getSecureMessageClause() {
    String isSent     = "(" + TYPE + " & " + MessageTypes.BASE_TYPE_MASK + ") = " + MessageTypes.BASE_SENT_TYPE;
    String isReceived = "(" + TYPE + " & " + MessageTypes.BASE_TYPE_MASK + ") = " + MessageTypes.BASE_INBOX_TYPE;
    String isSecure   = "(" + TYPE + " & " + (MessageTypes.SECURE_MESSAGE_BIT | MessageTypes.PUSH_MESSAGE_BIT) + ")";

    return String.format(Locale.ENGLISH, "(%s OR %s) AND %s", isSent, isReceived, isSecure);
  }

  protected String getInsecureMessageClause() {
    return getInsecureMessageClause(-1);
  }

  protected String getInsecureMessageClause(long threadId) {
    String isSent      = "(" + TABLE_NAME + "." + TYPE + " & " + MessageTypes.BASE_TYPE_MASK + ") = " + MessageTypes.BASE_SENT_TYPE;
    String isReceived  = "(" + TABLE_NAME + "." + TYPE + " & " + MessageTypes.BASE_TYPE_MASK + ") = " + MessageTypes.BASE_INBOX_TYPE;
    String isSecure    = "(" + TABLE_NAME + "." + TYPE + " & " + (MessageTypes.SECURE_MESSAGE_BIT | MessageTypes.PUSH_MESSAGE_BIT) + ")";
    String isNotSecure = "(" + TABLE_NAME + "." + TYPE + " <= " + (MessageTypes.BASE_TYPE_MASK | MessageTypes.MESSAGE_ATTRIBUTE_MASK) + ")";

    String whereClause = String.format(Locale.ENGLISH, "(%s OR %s) AND NOT %s AND %s", isSent, isReceived, isSecure, isNotSecure);

    if (threadId != -1) {
      whereClause += " AND " + TABLE_NAME + "." +  THREAD_ID + " = " + threadId;
    }

    return whereClause;
  }

  public int getUnexportedInsecureMessagesCount() {
    return getUnexportedInsecureMessagesCount(-1);
  }

  public int getUnexportedInsecureMessagesCount(long threadId) {
    try (Cursor cursor = getWritableDatabase().query(TABLE_NAME, SqlUtil.COUNT, getInsecureMessageClause(threadId) + " AND " + EXPORTED + " < ?", SqlUtil.buildArgs(MessageExportStatus.EXPORTED), null, null, null)) {
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

    SQLiteDatabaseExtensionsKt.update(getWritableDatabase(), TABLE_NAME)
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

    SQLiteDatabaseExtensionsKt.update(getWritableDatabase(), TABLE_NAME)
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
      whereClause +=  " AND " + DATE_RECEIVED + " <= " + sinceTimestamp;
    }

    values.put(REACTIONS_UNREAD, 0);
    values.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    db.update(TABLE_NAME, values, whereClause, whereArgs);
  }

  public void setAllReactionsSeen() {
    SQLiteDatabase db     = databaseHelper.getSignalWritableDatabase();
    ContentValues  values = new ContentValues();
    String         query  = REACTIONS_UNREAD + " != ?";
    String[]       args   = new String[] { "0" };

    values.put(REACTIONS_UNREAD, 0);
    values.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    db.update(TABLE_NAME, values, query, args);
  }

  public void setNotifiedTimestamp(long timestamp, @NonNull List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }

    SQLiteDatabase db     = databaseHelper.getSignalWritableDatabase();
    SqlUtil.Query  where  = SqlUtil.buildSingleCollectionQuery(ID, ids);
    ContentValues  values = new ContentValues();

    values.put(NOTIFIED_TIMESTAMP, timestamp);

    db.update(TABLE_NAME, values, where.getWhere(), where.getWhereArgs());
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
    String         query = THREAD_ID + " = ? AND " + DATE_RECEIVED + " <= ?";
    String[]       args  = SqlUtil.buildArgs(threadId, timestamp);

    List<ReportSpamData> data = new ArrayList<>();
    try (Cursor cursor = db.query(TABLE_NAME, new String[] { RECIPIENT_ID, SERVER_GUID, DATE_RECEIVED }, query, args, null, null, DATE_RECEIVED + " DESC", "3")) {
      while (cursor.moveToNext()) {
        RecipientId id         = RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID));
        String      serverGuid = CursorUtil.requireString(cursor, SERVER_GUID);
        long        dateReceived = CursorUtil.requireLong(cursor, DATE_RECEIVED);
        if (!Util.isEmpty(serverGuid)) {
          data.add(new ReportSpamData(id, serverGuid, dateReceived));
        }
      }
    }
    return data;
  }

  public List<Long> getIncomingPaymentRequestThreads() {
    Cursor cursor = SQLiteDatabaseExtensionsKt.select(getReadableDatabase(), "DISTINCT " + THREAD_ID)
                                              .from(TABLE_NAME)
                                              .where("(" + TYPE + " & " + MessageTypes.BASE_TYPE_MASK + ") = " + MessageTypes.BASE_INBOX_TYPE + " AND (" + TYPE + " & ?) != 0", MessageTypes.SPECIAL_TYPE_PAYMENTS_ACTIVATE_REQUEST)
                                              .run();

    return CursorExtensionsKt.readToList(cursor, c -> CursorUtil.requireLong(c, THREAD_ID));
  }

  public @Nullable MessageId getPaymentMessage(@NonNull UUID paymentUuid) {
    Cursor cursor = SQLiteDatabaseExtensionsKt.select(getReadableDatabase(), ID)
                                              .from(TABLE_NAME)
                                              .where(TYPE + " & ? != 0 AND body = ?", MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION, paymentUuid)
                                              .run();

    long id = CursorExtensionsKt.readToSingleLong(cursor, -1);
    if (id != -1) {
      return new MessageId(id);
    } else {
      return null;
    }
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
    long         latestQuit  = SignalDatabase.messages().getLatestGroupQuitTimestamp(threadId, lastQuitChecked);
    RecipientId  id          = SignalDatabase.messages().getOldestGroupUpdateSender(threadId, latestQuit);

    return new Pair<>(id, latestQuit);
  }

  /**
   * Whether or not the message has been quoted by another message.
   */
  public boolean isQuoted(@NonNull MessageRecord messageRecord) {
    RecipientId author    = messageRecord.isOutgoing() ? Recipient.self().getId() : messageRecord.getRecipient().getId();
    long        timestamp = messageRecord.getDateSent();

    String   where      = MessageTable.QUOTE_ID + " = ?  AND " + MessageTable.QUOTE_AUTHOR + " = ? AND " + SCHEDULED_DATE + " = ?";
    String[] whereArgs  = SqlUtil.buildArgs(timestamp, author, -1);

    try (Cursor cursor = getReadableDatabase().query(MessageTable.TABLE_NAME, new String[]{ "1" }, where, whereArgs, null, null, null, "1")) {
      return cursor.moveToFirst();
    }
  }

  /**
   * Given a collection of MessageRecords, this will return a set of the IDs of the records that have been quoted by another message.
   * Does an efficient bulk lookup that makes it faster than {@link #isQuoted(MessageRecord)} for multiple records.
   */
  public Set<Long> isQuoted(@NonNull Collection<MessageRecord> records) {
    if (records.isEmpty()) {
      return Collections.emptySet();
    }

    Map<QuoteDescriptor, MessageRecord> byQuoteDescriptor = new HashMap<>(records.size());
    List<String[]>                      args              = new ArrayList<>(records.size());

    for (MessageRecord record : records) {
      long        timestamp = record.getDateSent();
      RecipientId author    = record.isOutgoing() ? Recipient.self().getId() : record.getRecipient().getId();

      byQuoteDescriptor.put(new QuoteDescriptor(timestamp, author), record);
      args.add(SqlUtil.buildArgs(timestamp, author, -1));
    }


    String[]            projection = new String[] { QUOTE_ID, QUOTE_AUTHOR };
    List<SqlUtil.Query> queries    = SqlUtil.buildCustomCollectionQuery(QUOTE_ID + " = ?  AND " + QUOTE_AUTHOR + " = ? AND " + SCHEDULED_DATE + " = ?", args);
    Set<Long>           quotedIds  = new HashSet<>();

    for (SqlUtil.Query query : queries) {
      try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, projection, query.getWhere(), query.getWhereArgs(), null, null, null)) {
        while (cursor.moveToNext()) {
          long            timestamp    = CursorUtil.requireLong(cursor, QUOTE_ID);
          RecipientId     author       = RecipientId.from(CursorUtil.requireString(cursor, QUOTE_AUTHOR));
          QuoteDescriptor quoteLocator = new QuoteDescriptor(timestamp, author);

          quotedIds.add(byQuoteDescriptor.get(quoteLocator).getId());
        }
      }
    }

    return quotedIds;
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
      query = DATE_SENT + " = " + targetMessage.getQuote().getId() + " AND (" + TYPE + " & " + MessageTypes.BASE_TYPE_MASK + ") = " + MessageTypes.BASE_SENT_TYPE;
    } else {
      query = DATE_SENT + " = " + targetMessage.getQuote().getId() + " AND " + RECIPIENT_ID + " = '" + targetMessage.getQuote().getAuthor().serialize() + "'";
    }

    try (Reader reader = new MmsReader(getReadableDatabase().query(TABLE_NAME, MMS_PROJECTION, query, null, null, null, "1"))) {
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
      targetMessage = getMessageRecord(id.getId());
    } catch (NoSuchMessageException e) {
      throw new IllegalArgumentException("Invalid message ID!");
    }

    RecipientId author = targetMessage.isOutgoing() ? Recipient.self().getId() : targetMessage.getRecipient().getId();
    String      query  = QUOTE_ID + " = " + targetMessage.getDateSent() + " AND " + QUOTE_AUTHOR + " = " + author.serialize() + " AND " + SCHEDULED_DATE + " = -1";
    String      order  = DATE_RECEIVED + " DESC";

    List<MessageRecord> records = new ArrayList<>();

    try (Reader reader = new MmsReader(getReadableDatabase().query(TABLE_NAME, MMS_PROJECTION, query, null, null, null, order))) {
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

  public int getQuotedMessagePosition(long threadId, long quoteId, @NonNull RecipientId recipientId) {
    String[] projection = new String[]{ DATE_SENT, RECIPIENT_ID, REMOTE_DELETED};
    String   order      = DATE_RECEIVED + " DESC";
    String   selection  = THREAD_ID + " = " + threadId + " AND " + STORY_TYPE + " = 0" + " AND " + PARENT_STORY_ID + " <= 0 AND " + SCHEDULED_DATE + " = -1";

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, projection, selection, null, null, null, order)) {
      boolean isOwnNumber = Recipient.resolved(recipientId).isSelf();

      while (cursor != null && cursor.moveToNext()) {
        boolean quoteIdMatches     = cursor.getLong(0) == quoteId;
        boolean recipientIdMatches = recipientId.equals(RecipientId.from(CursorUtil.requireLong(cursor, RECIPIENT_ID)));

        if (quoteIdMatches && (recipientIdMatches || isOwnNumber)) {
          if (CursorUtil.requireBoolean(cursor, REMOTE_DELETED)) {
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
    String[] projection = new String[]{ DATE_RECEIVED, RECIPIENT_ID, REMOTE_DELETED};
    String   order      = DATE_RECEIVED + " DESC";
    String   selection  = THREAD_ID + " = " + threadId + " AND " + STORY_TYPE + " = 0" + " AND " + PARENT_STORY_ID + " <= 0 AND " + SCHEDULED_DATE + " = -1";

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, projection, selection, null, null, null, order)) {
      boolean isOwnNumber = Recipient.resolved(recipientId).isSelf();

      while (cursor != null && cursor.moveToNext()) {
        boolean timestampMatches   = cursor.getLong(0) == receivedTimestamp;
        boolean recipientIdMatches = recipientId.equals(RecipientId.from(cursor.getLong(1)));

        if (timestampMatches && (recipientIdMatches || isOwnNumber)) {
          if (CursorUtil.requireBoolean(cursor, REMOTE_DELETED)) {
            return -1;
          } else {
            return cursor.getPosition();
          }
        }
      }
    }
    return -1;
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
      order     = MessageTable.DATE_RECEIVED + " ASC";
      selection = MessageTable.THREAD_ID + " = " + threadId + " AND " +
                  MessageTable.DATE_RECEIVED + " < " + receivedTimestamp + " AND " +
                  MessageTable.STORY_TYPE + " = 0 AND " + MessageTable.PARENT_STORY_ID + " = " + groupStoryId;
    } else {
      order     = MessageTable.DATE_RECEIVED + " DESC";
      selection = MessageTable.THREAD_ID + " = " + threadId + " AND " +
                  MessageTable.DATE_RECEIVED + " > " + receivedTimestamp + " AND " +
                  MessageTable.STORY_TYPE + " = 0 AND " + MessageTable.PARENT_STORY_ID + " <= 0";
    }

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, new String[] { "COUNT(*)" }, selection, null, null, null, order)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }
    return -1;
  }

  public long getTimestampForFirstMessageAfterDate(long date) {
    String[] projection = new String[] { MessageTable.DATE_RECEIVED };
    String   order      = MessageTable.DATE_RECEIVED + " ASC";
    String   selection  = MessageTable.DATE_RECEIVED + " > " + date;

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, projection, selection, null, null, null, order, "1")) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(0);
      }
    }

    return 0;
  }

  public int getMessageCountBeforeDate(long date) {
    String selection = MessageTable.DATE_RECEIVED + " < " + date;

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, COUNT, selection, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      }
    }

    return 0;
  }

  public @NonNull List<MessageRecord> getMessagesAfterVoiceNoteInclusive(long messageId, long limit) throws NoSuchMessageException {
    MessageRecord       origin = getMessageRecord(messageId);
    List<MessageRecord> mms    = getMessagesInThreadAfterInclusive(origin.getThreadId(), origin.getDateReceived(), limit);

    Collections.sort(mms, Comparator.comparingLong(DisplayRecord::getDateReceived));

    return Stream.of(mms).limit(limit).toList();
  }

  public int getMessagePositionOnOrAfterTimestamp(long threadId, long timestamp) {
    String[] projection = new String[] { "COUNT(*)" };
    String   selection  = MessageTable.THREAD_ID + " = " + threadId + " AND " +
                          MessageTable.DATE_RECEIVED + " >= " + timestamp + " AND " +
                          MessageTable.STORY_TYPE + " = 0 AND " + MessageTable.PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, projection, selection, null, null, null, null)) {
      if (cursor != null && cursor.moveToNext()) {
        return cursor.getInt(0);
      }
    }
    return 0;
  }

  public long getConversationSnippetType(long threadId) throws NoSuchMessageException {
    SQLiteDatabase db = databaseHelper.getSignalReadableDatabase();
    try (Cursor cursor = db.rawQuery(SNIPPET_QUERY, SqlUtil.buildArgs(threadId))) {
      if (cursor.moveToFirst()) {
        return CursorUtil.requireLong(cursor, MessageTable.TYPE);
      } else {
        throw new NoSuchMessageException("no message");
      }
    }
  }

  public @NonNull MessageRecord getConversationSnippet(long threadId) throws NoSuchMessageException {
    try (Cursor cursor = getConversationSnippetCursor(threadId)) {
      if (cursor.moveToFirst()) {
        long id = CursorUtil.requireLong(cursor, MessageTable.ID);
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

  public int getUnreadCount(long threadId) {
    String selection = READ + " = 0 AND " + STORY_TYPE + " = 0 AND " + THREAD_ID + " = " + threadId + " AND " + PARENT_STORY_ID + " <= 0";

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME + " INDEXED BY " + INDEX_THREAD_DATE, COUNT, selection, null, null, null, null)) {
      if (cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  public boolean checkMessageExists(@NonNull MessageRecord messageRecord) {
    return SQLiteDatabaseExtensionsKt
        .exists(getReadableDatabase(), TABLE_NAME)
        .where(ID + " = ?", messageRecord.getId())
        .run();
  }

  public @NonNull List<MessageTable.ReportSpamData> getReportSpamMessageServerData(long threadId, long timestamp, int limit) {
    return SignalDatabase
        .messages()
        .getReportSpamMessageServerGuids(threadId, timestamp)
        .stream()
        .sorted((l, r) -> -Long.compare(l.getDateReceived(), r.getDateReceived()))
        .limit(limit)
        .collect(java.util.stream.Collectors.toList());
  }

  private @NonNull MessageExportState getMessageExportState(@NonNull MessageId messageId) throws NoSuchMessageException {
    String   table      = MessageTable.TABLE_NAME;
    String[] projection = SqlUtil.buildArgs(MessageTable.EXPORT_STATE);
    String[] args       = SqlUtil.buildArgs(messageId.getId());

    try (Cursor cursor = getReadableDatabase().query(table, projection, ID_WHERE, args, null, null, null, null)) {
      if (cursor.moveToFirst()) {
        byte[] bytes = CursorUtil.requireBlob(cursor,  MessageTable.EXPORT_STATE);
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
    ContentValues contentValues = new ContentValues(1);

    contentValues.put(MessageTable.EXPORTED, MessageExportStatus.EXPORTED.getCode());

    getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(messageId.getId()));
  }

  public void markMessageExportFailed(@NonNull MessageId messageId) {
    ContentValues contentValues = new ContentValues(1);

    contentValues.put(MessageTable.EXPORTED, MessageExportStatus.ERROR.getCode());

    getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(messageId.getId()));
  }

  private void setMessageExportState(@NonNull MessageId messageId, @NonNull MessageExportState messageExportState) {
    ContentValues contentValues = new ContentValues(1);

    contentValues.put(MessageTable.EXPORT_STATE, messageExportState.toByteArray());

    getWritableDatabase().update(TABLE_NAME, contentValues, ID_WHERE, SqlUtil.buildArgs(messageId.getId()));
  }

  public Collection<SyncMessageId> incrementDeliveryReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, ReceiptType.DELIVERY);
  }

  public boolean incrementDeliveryReceiptCount(SyncMessageId syncMessageId, long timestamp) {
    return incrementReceiptCount(syncMessageId, timestamp, ReceiptType.DELIVERY);
  }

  /**
   * @return A list of ID's that were not updated.
   */
  public @NonNull Collection<SyncMessageId> incrementReadReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, ReceiptType.READ);
  }

  public boolean incrementReadReceiptCount(SyncMessageId syncMessageId, long timestamp) {
    return incrementReceiptCount(syncMessageId, timestamp, ReceiptType.READ);
  }

  /**
   * @return A list of ID's that were not updated.
   */
  public @NonNull Collection<SyncMessageId> incrementViewedReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, ReceiptType.VIEWED);
  }

  public @NonNull Collection<SyncMessageId> incrementViewedNonStoryReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    return incrementReceiptCounts(syncMessageIds, timestamp, ReceiptType.VIEWED, MessageQualifier.NORMAL);
  }

  public boolean incrementViewedReceiptCount(SyncMessageId syncMessageId, long timestamp) {
    return incrementReceiptCount(syncMessageId, timestamp, ReceiptType.VIEWED);
  }

  public @NonNull Collection<SyncMessageId> incrementViewedStoryReceiptCounts(@NonNull List<SyncMessageId> syncMessageIds, long timestamp) {
    SQLiteDatabase            db             = databaseHelper.getSignalWritableDatabase();
    Set<MessageUpdate>        messageUpdates = new HashSet<>();
    Collection<SyncMessageId> unhandled      = new HashSet<>();

    db.beginTransaction();
    try {
      for (SyncMessageId id : syncMessageIds) {
        Set<MessageUpdate> updates = incrementReceiptCountInternal(id, timestamp, ReceiptType.VIEWED, MessageQualifier.STORY);

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

        if (messageQualifier == MessageQualifier.STORY) {
          ApplicationDependencies.getDatabaseObserver().notifyStoryObservers(Objects.requireNonNull(threadTable.getRecipientIdForThreadId(update.getThreadId())));
        }
      }

      if (messageUpdates.size() > 0) {
        notifyConversationListListeners();
      }
    }

    return unhandled;
  }

  private @NonNull Set<MessageUpdate> incrementReceiptCountInternal(SyncMessageId messageId, long timestamp, MessageTable.ReceiptType receiptType, @NonNull MessageTable.MessageQualifier messageQualifier) {
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

    try (Cursor cursor = SQLiteDatabaseExtensionsKt.select(database, ID, THREAD_ID, TYPE, RECIPIENT_ID, receiptType.getColumnName(), RECEIPT_TIMESTAMP)
                                                   .from(TABLE_NAME)
                                                   .where(DATE_SENT + " = ?" + qualifierWhere, messageId.getTimetamp())
                                                   .run())
    {
      while (cursor.moveToNext()) {
        if (MessageTypes.isOutgoingMessageType(CursorUtil.requireLong(cursor, TYPE))) {
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

            messageUpdates.add(new MessageUpdate(threadId, new MessageId(id)));
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

  /**
   * @return Unhandled ids
   */
  public Collection<SyncMessageId> setTimestampReadFromSyncMessage(@NonNull List<ReadMessage> readMessages, long proposedExpireStarted, @NonNull Map<Long, Long> threadToLatestRead) {
    SQLiteDatabase db = getWritableDatabase();

    List<Pair<Long, Long>>    expiringMessages = new LinkedList<>();
    Set<Long>                 updatedThreads   = new HashSet<>();
    Collection<SyncMessageId> unhandled        = new LinkedList<>();

    db.beginTransaction();
    try {
      for (ReadMessage readMessage : readMessages) {
        RecipientId         authorId = Recipient.externalPush(readMessage.getSender()).getId();
        TimestampReadResult result   = setTimestampReadFromSyncMessageInternal(new SyncMessageId(authorId, readMessage.getTimestamp()),
                                                                               proposedExpireStarted,
                                                                               threadToLatestRead);

        expiringMessages.addAll(result.expiring);
        updatedThreads.addAll(result.threads);

        if (result.threads.isEmpty()) {
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

    for (Pair<Long, Long> expiringMessage : expiringMessages) {
      ApplicationDependencies.getExpiringMessageManager()
                             .scheduleDeletion(expiringMessage.first(), true, proposedExpireStarted, expiringMessage.second());
    }

    for (long threadId : updatedThreads) {
      notifyConversationListeners(threadId);
    }

    return unhandled;
  }

  /**
   * Handles a synchronized read message.
   * @param messageId An id representing the author-timestamp pair of the message that was read on a linked device. Note that the author could be self when
   *                  syncing read receipts for reactions.
   */
  private final @NonNull TimestampReadResult setTimestampReadFromSyncMessageInternal(SyncMessageId messageId, long proposedExpireStarted, @NonNull Map<Long, Long> threadToLatestRead) {
    SQLiteDatabase         database   = databaseHelper.getSignalWritableDatabase();
    List<Pair<Long, Long>> expiring   = new LinkedList<>();
    String[]               projection = new String[] { ID, THREAD_ID, EXPIRES_IN, EXPIRE_STARTED };
    String                 query      = DATE_SENT + " = ? AND (" + RECIPIENT_ID + " = ? OR (" + RECIPIENT_ID + " = ? AND " + getOutgoingTypeClause() + "))";
    String[]               args       = SqlUtil.buildArgs(messageId.getTimetamp(), messageId.getRecipientId(), Recipient.self().getId());
    List<Long>             threads    = new LinkedList<>();

    try (Cursor cursor = database.query(TABLE_NAME, projection, query, args, null, null, null)) {
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

        database.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(id));

        threads.add(threadId);

        Long latest = threadToLatestRead.get(threadId);
        threadToLatestRead.put(threadId, (latest != null) ? Math.max(latest, messageId.getTimetamp()) : messageId.getTimetamp());
      }
    }

    return new TimestampReadResult(expiring, threads);
  }

  /**
   * Finds a message by timestamp+author.
   * Does *not* include attachments.
   */
  public @Nullable MessageRecord getMessageFor(long timestamp, RecipientId authorId) {
    Recipient author = Recipient.resolved(authorId);

    String   query = DATE_SENT + " = ?";
    String[] args  = SqlUtil.buildArgs(timestamp);

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, MMS_PROJECTION, query, args, null, null, null)) {
      MessageTable.Reader reader = MessageTable.mmsReaderFor(cursor);

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

  /**
   * A cursor containing all of the messages in a given thread, in the proper order.
   * This does *not* have attachments in it.
   */
  public Cursor getConversation(long threadId) {
    return getConversation(threadId, 0, 0);
  }

  /**
   * A cursor containing all of the messages in a given thread, in the proper order, respecting offset/limit.
   * This does *not* have attachments in it.
   */
  public Cursor getConversation(long threadId, long offset, long limit) {
    String   selection = THREAD_ID + " = ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ? AND " + SCHEDULED_DATE + " = ?";
    String[] args      = SqlUtil.buildArgs(threadId, 0, 0, -1);
    String   order     = DATE_RECEIVED + " DESC";
    String   limitStr  = limit > 0 || offset > 0 ? offset + ", " + limit : null;

    return getReadableDatabase().query(TABLE_NAME, MMS_PROJECTION, selection, args, null, null, order, limitStr);
  }

  /**
   * Returns messages ordered for display in a reverse list (newest first).
   */
  public List<MessageRecord> getScheduledMessagesInThread(long threadId) {
    String   selection = THREAD_ID + " = ? AND " + STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ? AND " + SCHEDULED_DATE + " != ?";
    String[] args      = SqlUtil.buildArgs(threadId, 0, 0, -1);
    String   order     = SCHEDULED_DATE + " DESC, " + ID + " DESC";

    try (MmsReader reader = mmsReaderFor(getReadableDatabase().query(TABLE_NAME + " INDEXED BY " + INDEX_THREAD_STORY_SCHEDULED_DATE, MMS_PROJECTION, selection, args, null, null, order))) {
      List<MessageRecord> results = new ArrayList<>(reader.getCount());
      while (reader.getNext() != null) {
        results.add(reader.getCurrent());
      }

      return results;
    }
  }

  /**
   * Returns messages order for sending (oldest first).
   */
  public List<MessageRecord> getScheduledMessagesBefore(long time) {
    String   selection = STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ? AND " + SCHEDULED_DATE + " != ? AND " + SCHEDULED_DATE + " <= ?";
    String[] args      = SqlUtil.buildArgs(0, 0, -1, time);
    String   order     = SCHEDULED_DATE + " ASC, " + ID + " ASC";

    try (MmsReader reader = mmsReaderFor(getReadableDatabase().query(TABLE_NAME, MMS_PROJECTION, selection, args, null, null, order))) {
      List<MessageRecord> results = new ArrayList<>(reader.getCount());
      while (reader.getNext() != null) {
        results.add(reader.getCurrent());
      }

      return results;
    }
  }

  public @Nullable Long getOldestScheduledSendTimestamp() {
    String[] columns   = new String[] { SCHEDULED_DATE };
    String   selection = STORY_TYPE + " = ? AND " + PARENT_STORY_ID + " <= ? AND " + SCHEDULED_DATE + " != ?";
    String[] args      = SqlUtil.buildArgs(0, 0, -1);
    String   order     = SCHEDULED_DATE + " ASC, " + ID + " ASC";
    String   limit     = "1";

    try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, columns, selection, args, null, null, order, limit)) {
      return cursor != null && cursor.moveToNext() ? cursor.getLong(0) : null;
    }
  }

  public Cursor getMessagesForNotificationState(Collection<DefaultMessageNotifier.StickyThread> stickyThreads) {
    StringBuilder stickyQuery = new StringBuilder();
    for (DefaultMessageNotifier.StickyThread stickyThread : stickyThreads) {
      if (stickyQuery.length() > 0) {
        stickyQuery.append(" OR ");
      }
      stickyQuery.append("(")
                 .append(MessageTable.THREAD_ID + " = ")
                 .append(stickyThread.getConversationId().getThreadId())
                 .append(" AND ")
                 .append(MessageTable.DATE_RECEIVED)
                 .append(" >= ")
                 .append(stickyThread.getEarliestTimestamp())
                 .append(getStickyWherePartForParentStoryId(stickyThread.getConversationId().getGroupStoryId()))
                 .append(")");
    }

    String order     = MessageTable.DATE_RECEIVED + " ASC";
    String selection = MessageTable.NOTIFIED + " = 0 AND " + MessageTable.STORY_TYPE + " = 0 AND (" + MessageTable.READ + " = 0 OR " + MessageTable.REACTIONS_UNREAD + " = 1" + (stickyQuery.length() > 0 ? " OR (" + stickyQuery + ")" : "") + ")";

    return getReadableDatabase().query(TABLE_NAME, MMS_PROJECTION, selection, null, null, null, order);
  }

  private @NonNull String getStickyWherePartForParentStoryId(@Nullable Long parentStoryId) {
    if (parentStoryId == null) {
      return " AND " + MessageTable.PARENT_STORY_ID + " <= 0";
    }

    return " AND " + MessageTable.PARENT_STORY_ID + " = " + parentStoryId;
  }

  @Override
  public void remapRecipient(@NonNull RecipientId fromId, @NonNull RecipientId toId) {
    ContentValues values = new ContentValues();
    values.put(RECIPIENT_ID, toId.serialize());
    getWritableDatabase().update(TABLE_NAME, values, RECIPIENT_ID + " = ?", SqlUtil.buildArgs(fromId));
  }

  @Override
  public void remapThread(long fromId, long toId) {
    ContentValues values = new ContentValues();
    values.put(THREAD_ID, toId);
    getWritableDatabase().update(TABLE_NAME, values, THREAD_ID + " = ?", SqlUtil.buildArgs(fromId));
  }

  /**
   * Returns the next ID that would be generated if an insert was done on this table.
   * You should *not* use this for actually generating an ID to use. That will happen automatically!
   * This was added for a very narrow usecase, and you probably don't need to use it.
   */
  public long getNextId() {
    return SqlUtil.getNextAutoIncrementId(getWritableDatabase(), TABLE_NAME);
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
        db.update(TABLE_NAME, values, ID_WHERE, SqlUtil.buildArgs(messageId));
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

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {String.valueOf(messageId)});
  }

  private <D extends Document> D getDocument(SQLiteDatabase database, long messageId,
                                             String column, Class<D> clazz)
  {

    try (Cursor cursor = database.query(TABLE_NAME, new String[] { column }, ID_WHERE, new String[] { String.valueOf(messageId) }, null, null, null)) {
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
      } catch (InstantiationException | IllegalAccessException e) {
        throw new AssertionError(e);
      }

    }
  }

  public @NonNull Map<Long, BodyRangeList> getBodyRangesForMessages(@NonNull List<Long> messageIds) {
    List<SqlUtil.Query>      queries    = SqlUtil.buildCollectionQuery(ID, messageIds);
    Map<Long, BodyRangeList> bodyRanges = new HashMap<>();

    for (SqlUtil.Query query : queries) {
      try (Cursor cursor = SQLiteDatabaseExtensionsKt.select(getReadableDatabase(), ID, MESSAGE_RANGES)
                                                     .from(TABLE_NAME)
                                                     .where(query.getWhere(), query.getWhereArgs())
                                                     .run())
      {
        while (cursor.moveToNext()) {
          byte[] data = CursorUtil.requireBlob(cursor, MESSAGE_RANGES);
          if (data != null) {
            try {
              bodyRanges.put(CursorUtil.requireLong(cursor, ID), BodyRangeList.parseFrom(data));
            } catch (InvalidProtocolBufferException e) {
              Log.w(TAG, "Unable to parse body ranges for search", e);
            }
          }
        }
      }
    }

    return bodyRanges;
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
    private final StoryType      storyType;

    public MarkedMessageInfo(long threadId, @NonNull SyncMessageId syncMessageId, @NonNull MessageId messageId, @Nullable ExpirationInfo expirationInfo, @NonNull StoryType storyType) {
      this.threadId       = threadId;
      this.syncMessageId  = syncMessageId;
      this.messageId      = messageId;
      this.expirationInfo = expirationInfo;
      this.storyType      = storyType;
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

    public @NonNull StoryType getStoryType() {
      return storyType;
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

  private static class QuoteDescriptor {
    private final long        timestamp;
    private final RecipientId author;

    private QuoteDescriptor(long timestamp, RecipientId author) {
      this.author    = author;
      this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final QuoteDescriptor that = (QuoteDescriptor) o;
      return timestamp == that.timestamp && author.equals(that.author);
    }

    @Override
    public int hashCode() {
      return Objects.hash(author, timestamp);
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

  public static class MmsStatus {
    public static final int DOWNLOAD_INITIALIZED     = 1;
    public static final int DOWNLOAD_NO_CONNECTIVITY = 2;
    public static final int DOWNLOAD_CONNECTING      = 3;
    public static final int DOWNLOAD_SOFT_FAILURE    = 4;
    public static final int DOWNLOAD_HARD_FAILURE    = 5;
    public static final int DOWNLOAD_APN_UNAVAILABLE = 6;
  }

  public static class OutgoingMmsReader {

    private final Context         context;
    private final OutgoingMessage message;
    private final long            id;
    private final long                 threadId;

    public OutgoingMmsReader(OutgoingMessage message, long threadId) {
      this.context  = ApplicationDependencies.getApplication();
      this.message  = message;
      this.id       = new SecureRandom().nextLong();
      this.threadId = threadId;
    }

    public MessageRecord getCurrent() {
      SlideDeck slideDeck = new SlideDeck(context, message.getAttachments());

      CharSequence  quoteText       = message.getOutgoingQuote() != null ? message.getOutgoingQuote().getText() : null;
      List<Mention> quoteMentions   = message.getOutgoingQuote() != null ? message.getOutgoingQuote().getMentions() : Collections.emptyList();
      BodyRangeList quoteBodyRanges = message.getOutgoingQuote() != null ? message.getOutgoingQuote().getBodyRanges() : null;

      if (quoteText != null && (Util.hasItems(quoteMentions) || quoteBodyRanges != null)) {
        MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, quoteText, quoteMentions);

        SpannableString styledText = new SpannableString(updated.getBody());
        MessageStyler.style(BodyRangeUtil.adjustBodyRanges(quoteBodyRanges, updated.getBodyAdjustments()), styledText);

        quoteText     = styledText;
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
                                       message.isSecure() ? MessageTypes.getOutgoingEncryptedMessageType() : MessageTypes.getOutgoingSmsMessageType(),
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
                                       null,
                                       null,
                                       -1);
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
  public static class MmsReader implements MessageTable.Reader {

    private final Cursor  cursor;
    private final Context context;

    public MmsReader(Cursor cursor) {
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
      long mmsType = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.MMS_MESSAGE_TYPE));

      if (mmsType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
        return getNotificationMmsMessageRecord(cursor);
      } else {
        return getMediaMmsMessageRecord(cursor);
      }
    }

    @Override
    public @NonNull MessageExportState getMessageExportStateForCurrentRecord() {
      byte[] messageExportState = CursorUtil.requireBlob(cursor, MessageTable.EXPORT_STATE);
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
      long      id                   = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.ID));
      long      dateSent             = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.DATE_SENT));
      long      dateReceived         = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.DATE_RECEIVED));
      long      threadId             = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.THREAD_ID));
      long      mailbox              = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.TYPE));
      long      recipientId          = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.RECIPIENT_ID));
      int       addressDeviceId      = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.RECIPIENT_DEVICE_ID));
      Recipient recipient            = Recipient.live(RecipientId.from(recipientId)).get();

      String        contentLocation      = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.MMS_CONTENT_LOCATION));
      String        transactionId        = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.MMS_TRANSACTION_ID));
      long          messageSize          = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.MMS_MESSAGE_SIZE));
      long          expiry               = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.MMS_EXPIRY));
      int           status               = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.MMS_STATUS));
      int           deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.DELIVERY_RECEIPT_COUNT));
      int           readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.READ_RECEIPT_COUNT));
      int           subscriptionId       = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.SMS_SUBSCRIPTION_ID));
      int           viewedReceiptCount   = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.VIEWED_RECEIPT_COUNT));
      long          receiptTimestamp     = CursorUtil.requireLong(cursor, MessageTable.RECEIPT_TIMESTAMP);
      StoryType     storyType            = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));
      ParentStoryId parentStoryId        = ParentStoryId.deserialize(CursorUtil.requireLong(cursor, PARENT_STORY_ID));
      String        body                 = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.BODY));

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
      if (body != null && MessageTypes.isGiftBadge(mailbox)) {
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
      long                 id                   = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.ID));
      long                 dateSent             = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.DATE_SENT));
      long                 dateReceived         = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.DATE_RECEIVED));
      long                 dateServer           = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.DATE_SERVER));
      long                 box                  = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.TYPE));
      long                 threadId             = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.THREAD_ID));
      long                 recipientId          = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.RECIPIENT_ID));
      int                  addressDeviceId      = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.RECIPIENT_DEVICE_ID));
      int                  deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.DELIVERY_RECEIPT_COUNT));
      int                  readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.READ_RECEIPT_COUNT));
      String               body                 = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.BODY));
      String               mismatchDocument     = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.MISMATCHED_IDENTITIES));
      String               networkDocument      = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.NETWORK_FAILURES));
      int                  subscriptionId       = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.SMS_SUBSCRIPTION_ID));
      long                 expiresIn            = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.EXPIRES_IN));
      long                 expireStarted        = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.EXPIRE_STARTED));
      boolean              unidentified         = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.UNIDENTIFIED)) == 1;
      boolean              isViewOnce           = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.VIEW_ONCE)) == 1;
      boolean              remoteDelete         = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.REMOTE_DELETED)) == 1;
      boolean              mentionsSelf         = CursorUtil.requireBoolean(cursor, MENTIONS_SELF);
      long                 notifiedTimestamp    = CursorUtil.requireLong(cursor, NOTIFIED_TIMESTAMP);
      int                  viewedReceiptCount   = cursor.getInt(cursor.getColumnIndexOrThrow(VIEWED_RECEIPT_COUNT));
      long                 receiptTimestamp     = CursorUtil.requireLong(cursor, RECEIPT_TIMESTAMP);
      byte[]               messageRangesData    = CursorUtil.requireBlob(cursor, MESSAGE_RANGES);
      StoryType            storyType            = StoryType.fromCode(CursorUtil.requireInt(cursor, STORY_TYPE));
      ParentStoryId        parentStoryId        = ParentStoryId.deserialize(CursorUtil.requireLong(cursor, PARENT_STORY_ID));
      long                 scheduledDate        = CursorUtil.requireLong(cursor, SCHEDULED_DATE);

      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
        readReceiptCount = 0;

        if (MessageTypes.isOutgoingMessageType(box) && !storyType.isStory()) {
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
      if (body != null && MessageTypes.isGiftBadge(box)) {
        try {
          giftBadge = GiftBadge.parseFrom(Base64.decode(body));
        } catch (IOException e) {
          Log.w(TAG, "Error parsing gift badge", e);
        }
      }

      return new MediaMmsMessageRecord(id, recipient, recipient,
                                       addressDeviceId, dateSent, dateReceived, dateServer, deliveryReceiptCount,
                                       threadId, body, slideDeck, box, mismatches,
                                       networkFailures, subscriptionId, expiresIn, expireStarted,
                                       isViewOnce, readReceiptCount, quote, contacts, previews, unidentified, Collections.emptyList(),
                                       remoteDelete, mentionsSelf, notifiedTimestamp, viewedReceiptCount, receiptTimestamp, messageRanges,
                                       storyType, parentStoryId, giftBadge, null, null, scheduledDate);
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
      long                       quoteId          = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.QUOTE_ID));
      long                       quoteAuthor      = cursor.getLong(cursor.getColumnIndexOrThrow(MessageTable.QUOTE_AUTHOR));
      CharSequence               quoteText        = cursor.getString(cursor.getColumnIndexOrThrow(MessageTable.QUOTE_BODY));
      int                        quoteType        = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.QUOTE_TYPE));
      boolean                    quoteMissing     = cursor.getInt(cursor.getColumnIndexOrThrow(MessageTable.QUOTE_MISSING)) == 1;
      List<Mention>              quoteMentions    = parseQuoteMentions(cursor);
      BodyRangeList              bodyRanges       = parseQuoteBodyRanges(cursor);
      List<DatabaseAttachment>   attachments      = SignalDatabase.attachments().getAttachments(cursor);
      List<? extends Attachment> quoteAttachments = Stream.of(attachments).filter(Attachment::isQuote).toList();
      SlideDeck                  quoteDeck        = new SlideDeck(context, quoteAttachments);

      if (quoteId > 0 && quoteAuthor > 0) {
        if (quoteText != null && (Util.hasItems(quoteMentions) || bodyRanges != null)) {
          MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, quoteText, quoteMentions);

          SpannableString styledText = new SpannableString(updated.getBody());
          MessageStyler.style(BodyRangeUtil.adjustBodyRanges(bodyRanges, updated.getBodyAdjustments()), styledText);

          quoteText     = styledText;
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

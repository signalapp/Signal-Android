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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.AsymmetricMasterCipher;
import org.thoughtcrime.securesms.crypto.MasterCipher;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUnion;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.documents.NetworkFailureList;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchList;
import org.thoughtcrime.securesms.database.model.DisplayRecord;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.jobs.TrimThreadJob;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.InvalidHeaderValueException;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.NotificationInd;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.PduPart;
import ws.com.google.android.mms.pdu.SendReq;

import static org.thoughtcrime.securesms.util.Util.canonicalizeNumber;
import static org.thoughtcrime.securesms.util.Util.canonicalizeNumberOrGroup;

// XXXX Clean up MMS efficiency:
// 1) We need to be careful about how much memory we're using for parts. SoftRefereences.
// 2) How many queries do we make?  calling getMediaMessageForId() from within an existing query
//    seems wasteful.

public class MmsDatabase extends MessagingDatabase {

  private static final String TAG = MmsDatabase.class.getSimpleName();

  public  static final String TABLE_NAME         = "mms";
          static final String DATE_SENT          = "date";
          static final String DATE_RECEIVED      = "date_received";
  public  static final String MESSAGE_BOX        = "msg_box";
  private static final String MESSAGE_ID         = "m_id";
  private static final String SUBJECT            = "sub";
  private static final String SUBJECT_CHARSET    = "sub_cs";
          static final String CONTENT_TYPE       = "ct_t";
          static final String CONTENT_LOCATION   = "ct_l";
          static final String EXPIRY             = "exp";
  private static final String MESSAGE_CLASS      = "m_cls";
  public  static final String MESSAGE_TYPE       = "m_type";
  private static final String MMS_VERSION        = "v";
          static final String MESSAGE_SIZE       = "m_size";
  private static final String PRIORITY           = "pri";
  private static final String READ_REPORT        = "rr";
  private static final String REPORT_ALLOWED     = "rpt_a";
  private static final String RESPONSE_STATUS    = "resp_st";
          static final String STATUS             = "st";
          static final String TRANSACTION_ID     = "tr_id";
  private static final String RETRIEVE_STATUS    = "retr_st";
  private static final String RETRIEVE_TEXT      = "retr_txt";
  private static final String RETRIEVE_TEXT_CS   = "retr_txt_cs";
  private static final String READ_STATUS        = "read_status";
  private static final String CONTENT_CLASS      = "ct_cls";
  private static final String RESPONSE_TEXT      = "resp_txt";
  private static final String DELIVERY_TIME      = "d_tm";
  private static final String DELIVERY_REPORT    = "d_rpt";
          static final String PART_COUNT         = "part_count";
          static final String NETWORK_FAILURE    = "network_failures";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, "                          +
    THREAD_ID + " INTEGER, " + DATE_SENT + " INTEGER, " + DATE_RECEIVED + " INTEGER, " + MESSAGE_BOX + " INTEGER, " +
    READ + " INTEGER DEFAULT 0, " + MESSAGE_ID + " TEXT, " + SUBJECT + " TEXT, "                +
    SUBJECT_CHARSET + " INTEGER, " + BODY + " TEXT, " + PART_COUNT + " INTEGER, "               +
    CONTENT_TYPE + " TEXT, " + CONTENT_LOCATION + " TEXT, " + ADDRESS + " TEXT, "               +
    ADDRESS_DEVICE_ID + " INTEGER, "                                                            +
    EXPIRY + " INTEGER, " + MESSAGE_CLASS + " TEXT, " + MESSAGE_TYPE + " INTEGER, "             +
    MMS_VERSION + " INTEGER, " + MESSAGE_SIZE + " INTEGER, " + PRIORITY + " INTEGER, "          +
    READ_REPORT + " INTEGER, " + REPORT_ALLOWED + " INTEGER, " + RESPONSE_STATUS + " INTEGER, " +
    STATUS + " INTEGER, " + TRANSACTION_ID + " TEXT, " + RETRIEVE_STATUS + " INTEGER, "         +
    RETRIEVE_TEXT + " TEXT, " + RETRIEVE_TEXT_CS + " INTEGER, " + READ_STATUS + " INTEGER, "    +
    CONTENT_CLASS + " INTEGER, " + RESPONSE_TEXT + " TEXT, " + DELIVERY_TIME + " INTEGER, "     +
    RECEIPT_COUNT + " INTEGER DEFAULT 0, " + MISMATCHED_IDENTITIES + " TEXT DEFAULT NULL, "     +
    NETWORK_FAILURE + " TEXT DEFAULT NULL," + DELIVERY_REPORT + " INTEGER);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS mms_thread_id_index ON " + TABLE_NAME + " (" + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_read_index ON " + TABLE_NAME + " (" + READ + ");",
    "CREATE INDEX IF NOT EXISTS mms_read_and_thread_id_index ON " + TABLE_NAME + "(" + READ + "," + THREAD_ID + ");",
    "CREATE INDEX IF NOT EXISTS mms_message_box_index ON " + TABLE_NAME + " (" + MESSAGE_BOX + ");",
    "CREATE INDEX IF NOT EXISTS mms_date_sent_index ON " + TABLE_NAME + " (" + DATE_SENT + ");",
    "CREATE INDEX IF NOT EXISTS mms_thread_date_index ON " + TABLE_NAME + " (" + THREAD_ID + ", " + DATE_RECEIVED + ");"
  };

  private static final String[] MMS_PROJECTION = new String[] {
      ID, THREAD_ID, DATE_SENT + " AS " + NORMALIZED_DATE_SENT,
      DATE_RECEIVED + " AS " + NORMALIZED_DATE_RECEIVED,
      MESSAGE_BOX, READ, MESSAGE_ID, SUBJECT, SUBJECT_CHARSET, CONTENT_TYPE,
      CONTENT_LOCATION, EXPIRY, MESSAGE_CLASS, MESSAGE_TYPE, MMS_VERSION,
      MESSAGE_SIZE, PRIORITY, REPORT_ALLOWED, STATUS, TRANSACTION_ID, RETRIEVE_STATUS,
      RETRIEVE_TEXT, RETRIEVE_TEXT_CS, READ_STATUS, CONTENT_CLASS, RESPONSE_TEXT,
      DELIVERY_TIME, DELIVERY_REPORT, BODY, PART_COUNT, ADDRESS, ADDRESS_DEVICE_ID,
      RECEIPT_COUNT, MISMATCHED_IDENTITIES, NETWORK_FAILURE
  };

  public static final ExecutorService slideResolver = org.thoughtcrime.securesms.util.Util.newSingleThreadedLifoExecutor();
  private static final Map<String, SoftReference<SlideDeck>> slideCache =
      Collections.synchronizedMap(new LRUCache<String, SoftReference<SlideDeck>>(20));

  private final JobManager jobManager;

  public MmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
    this.jobManager = ApplicationContext.getInstance(context).getJobManager();
  }

  @Override
  protected String getTableName() {
    return TABLE_NAME;
  }

  public int getMessageCountForThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor     = null;

    try {
      cursor = db.query(TABLE_NAME, new String[] {"COUNT(*)"}, THREAD_ID + " = ?", new String[] {threadId+""}, null, null, null);

      if (cursor != null && cursor.moveToFirst())
        return cursor.getInt(0);
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return 0;
  }

  public void addFailures(long messageId, List<NetworkFailure> failure) {
    try {
      addToDocument(messageId, NETWORK_FAILURE, failure, NetworkFailureList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void removeFailure(long messageId, NetworkFailure failure) {
    try {
      removeFromDocument(messageId, NETWORK_FAILURE, failure, NetworkFailureList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void incrementDeliveryReceiptCount(String address, long timestamp) {
    MmsAddressDatabase addressDatabase = DatabaseFactory.getMmsAddressDatabase(context);
    SQLiteDatabase     database        = databaseHelper.getWritableDatabase();
    Cursor             cursor          = null;

    try {
      cursor = database.query(TABLE_NAME, new String[] {ID, THREAD_ID, MESSAGE_BOX}, DATE_SENT + " = ?", new String[] {String.valueOf(timestamp)}, null, null, null, null);

      while (cursor.moveToNext()) {
        if (Types.isOutgoingMessageType(cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX)))) {
          List<String> addresses = addressDatabase.getAddressesForId(cursor.getLong(cursor.getColumnIndexOrThrow(ID)));

          for (String storedAddress : addresses) {
            try {
              String ourAddress   = canonicalizeNumber(context, address);
              String theirAddress = canonicalizeNumberOrGroup(context, storedAddress);

              if (ourAddress.equals(theirAddress) || GroupUtil.isEncodedGroup(theirAddress)) {
                long id       = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
                long threadId = cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));

                database.execSQL("UPDATE " + TABLE_NAME + " SET " +
                                 RECEIPT_COUNT + " = " + RECEIPT_COUNT + " + 1 WHERE " + ID + " = ?",
                                 new String[] {String.valueOf(id)});

                notifyConversationListeners(threadId);
              }
            } catch (InvalidNumberException e) {
              Log.w("MmsDatabase", e);
            }
          }
        }
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

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

  private long getThreadIdFor(IncomingMediaMessage retrieved) throws RecipientFormattingException, MmsException {
    if (retrieved.getGroupId() != null) {
      Recipients groupRecipients = RecipientFactory.getRecipientsFromString(context, retrieved.getGroupId(), true);
      return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipients);
    }

    try {
      PduHeaders headers = retrieved.getPduHeaders();
      Set<String> group = new HashSet<String>();

      EncodedStringValue   encodedFrom   = headers.getEncodedStringValue(PduHeaders.FROM);
      EncodedStringValue[] encodedCcList = headers.getEncodedStringValues(PduHeaders.CC);
      EncodedStringValue[] encodedToList = headers.getEncodedStringValues(PduHeaders.TO);

      if (encodedFrom == null) {
        throw new MmsException("FROM value in PduHeaders did not exist.");
      }

      group.add(new String(encodedFrom.getTextString(), CharacterSets.MIMENAME_ISO_8859_1));

      TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
      String           localNumber      = telephonyManager.getLine1Number();

      if (localNumber == null) {
          localNumber = TextSecurePreferences.getLocalNumber(context);
      }

      if (encodedCcList != null) {
        for (EncodedStringValue encodedCc : encodedCcList) {
          String cc = new String(encodedCc.getTextString(), CharacterSets.MIMENAME_ISO_8859_1);

          PhoneNumberUtil.MatchType match;

          if (localNumber == null) match = PhoneNumberUtil.MatchType.NO_MATCH;
          else                     match = PhoneNumberUtil.getInstance().isNumberMatch(localNumber, cc);

          if (match == PhoneNumberUtil.MatchType.NO_MATCH ||
              match == PhoneNumberUtil.MatchType.NOT_A_NUMBER)
          {
              group.add(cc);
          }
        }
      }

      if (encodedToList != null && (encodedToList.length > 1 || group.size() > 1)) {
        for (EncodedStringValue encodedTo : encodedToList) {
          String to = new String(encodedTo.getTextString(), CharacterSets.MIMENAME_ISO_8859_1);

          PhoneNumberUtil.MatchType match;

          if (localNumber == null) match = PhoneNumberUtil.MatchType.NO_MATCH;
          else                     match = PhoneNumberUtil.getInstance().isNumberMatch(localNumber, to);

          if (match == PhoneNumberUtil.MatchType.NO_MATCH ||
              match == PhoneNumberUtil.MatchType.NOT_A_NUMBER)
          {
            group.add(to);
          }
        }
      }

      String recipientsList = Util.join(group, ",");
      Recipients recipients = RecipientFactory.getRecipientsFromString(context, recipientsList, false);
      return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private long getThreadIdFor(@NonNull NotificationInd notification) {
    String fromString = notification.getFrom() != null && notification.getFrom().getTextString() != null
                      ? Util.toIsoString(notification.getFrom().getTextString())
                      : "";
    Recipients recipients = RecipientFactory.getRecipientsFromString(context, fromString, false);
    if (recipients.isEmpty()) recipients = RecipientFactory.getRecipientsFor(context, Recipient.getUnknownRecipient(), false);
    return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
  }

  public Cursor getMessage(long messageId) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    Cursor cursor = db.query(TABLE_NAME, MMS_PROJECTION, ID_WHERE, new String[] {messageId+""},
                             null, null, null);
    setNotifyConverationListeners(cursor, getThreadIdForMessage(messageId));
    return cursor;
  }

  public Reader getDecryptInProgressMessages(MasterSecret masterSecret) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();
    String where       = MESSAGE_BOX + " & " + (Types.ENCRYPTION_ASYMMETRIC_BIT) + " != 0";

    return readerFor(masterSecret, db.query(TABLE_NAME, MMS_PROJECTION, where, null, null, null, null));
  }

  private void updateMailboxBitmask(long id, long maskOff, long maskOn) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME +
                   " SET " + MESSAGE_BOX + " = (" + MESSAGE_BOX + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + " )" +
                   " WHERE " + ID + " = ?", new String[] {id + ""});
  }

  public void markAsOutbox(long messageId) {
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_OUTBOX_TYPE);
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }

  public void markAsForcedSms(long messageId) {
    updateMailboxBitmask(messageId, Types.PUSH_MESSAGE_BIT, Types.MESSAGE_FORCE_SMS_BIT);
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }

  public void markAsPendingInsecureSmsFallback(long messageId) {
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_PENDING_INSECURE_SMS_FALLBACK);
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }

  public void markAsSending(long messageId) {
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENDING_TYPE);
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }

  public void markAsSentFailed(long messageId) {
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENT_FAILED_TYPE);
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }

  public void markAsSent(long messageId, byte[] mmsId, long status) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(RESPONSE_STATUS, status);
    contentValues.put(MESSAGE_ID, new String(mmsId));

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId+""});
    updateMailboxBitmask(messageId, Types.BASE_TYPE_MASK, Types.BASE_SENT_TYPE);
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }

  public void markDownloadState(long messageId, long state) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(STATUS, state);

    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId + ""});
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }

  public void markAsNoSession(long messageId, long threadId) {
    updateMailboxBitmask(messageId, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_NO_SESSION_BIT);
    notifyConversationListeners(threadId);
  }

  public void markAsSecure(long messageId) {
    updateMailboxBitmask(messageId, 0, Types.SECURE_MESSAGE_BIT);
  }

  public void markAsInsecure(long messageId) {
    updateMailboxBitmask(messageId, Types.SECURE_MESSAGE_BIT, 0);
  }

  public void markAsPush(long messageId) {
    updateMailboxBitmask(messageId, 0, Types.PUSH_MESSAGE_BIT);
  }

  public void markAsDecryptFailed(long messageId, long threadId) {
    updateMailboxBitmask(messageId, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_FAILED_BIT);
    notifyConversationListeners(threadId);
  }

  public void markAsDecryptDuplicate(long messageId, long threadId) {
    updateMailboxBitmask(messageId, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_DUPLICATE_BIT);
    notifyConversationListeners(threadId);
  }

  public void markAsLegacyVersion(long messageId, long threadId) {
    updateMailboxBitmask(messageId, Types.ENCRYPTION_MASK, Types.ENCRYPTION_REMOTE_LEGACY_BIT);
    notifyConversationListeners(threadId);
  }

  public void setMessagesRead(long threadId) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, 1);

    database.update(TABLE_NAME, contentValues, THREAD_ID + " = ?", new String[] {threadId + ""});
  }

  public void setAllMessagesRead() {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, 1);

    database.update(TABLE_NAME, contentValues, null, null);
  }

  public void updateMessageBody(MasterSecretUnion masterSecret, long messageId, String body) {
    body = getEncryptedBody(masterSecret, body);

    long type;

    if (masterSecret.getMasterSecret().isPresent()) {
      type = Types.ENCRYPTION_SYMMETRIC_BIT;
    } else {
      type = Types.ENCRYPTION_ASYMMETRIC_BIT;
    }

    updateMessageBodyAndType(messageId, body, Types.ENCRYPTION_MASK, type);
  }

  private Pair<Long, Long> updateMessageBodyAndType(long messageId, String body, long maskOff, long maskOn) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.execSQL("UPDATE " + TABLE_NAME + " SET " + BODY + " = ?, " +
               MESSAGE_BOX + " = (" + MESSAGE_BOX + " & " + (Types.TOTAL_MASK - maskOff) + " | " + maskOn + ") " +
               "WHERE " + ID + " = ?",
               new String[] {body, messageId + ""});

    long threadId = getThreadIdForMessage(messageId);

    DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
    notifyConversationListListeners();

    return new Pair<>(messageId, threadId);
  }

  public Optional<NotificationInd> getNotification(long messageId) {
    SQLiteDatabase     db              = databaseHelper.getReadableDatabase();
    MmsAddressDatabase addressDatabase = DatabaseFactory.getMmsAddressDatabase(context);

    Cursor cursor = null;

    try {
      cursor = db.query(TABLE_NAME, MMS_PROJECTION, ID_WHERE, new String[] {String.valueOf(messageId)}, null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        PduHeaders headers = getHeadersFromCursor(cursor);
        addressDatabase.getAddressesForId(messageId, headers);

        return Optional.of(new NotificationInd(headers));
      } else {
        return Optional.absent();
      }
    } catch (InvalidHeaderValueException e) {
      Log.w("MmsDatabase", e);
      return Optional.absent();
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public SendReq getOutgoingMessage(MasterSecret masterSecret, long messageId)
      throws MmsException, NoSuchMessageException
  {
    MmsAddressDatabase addr         = DatabaseFactory.getMmsAddressDatabase(context);
    PartDatabase       partDatabase = DatabaseFactory.getPartDatabase(context);
    SQLiteDatabase     database     = databaseHelper.getReadableDatabase();
    MasterCipher       masterCipher = new MasterCipher(masterSecret);
    Cursor             cursor       = null;

    String   selection     = ID_WHERE;
    String[] selectionArgs = new String[]{String.valueOf(messageId)};

    try {
      cursor = database.query(TABLE_NAME, MMS_PROJECTION, selection, selectionArgs, null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        long       outboxType  = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX));
        String     messageText = cursor.getString(cursor.getColumnIndexOrThrow(BODY));
        long       timestamp   = cursor.getLong(cursor.getColumnIndexOrThrow(NORMALIZED_DATE_SENT));
        PduHeaders headers     = getHeadersFromCursor(cursor);
        addr.getAddressesForId(messageId, headers);

        PduBody body = getPartsAsBody(partDatabase.getParts(messageId));

        try {
          if (!TextUtils.isEmpty(messageText) && Types.isSymmetricEncryption(outboxType)) {
            body.addPart(new TextSlide(context, masterCipher.decryptBody(messageText)).getPart());
          } else if (!TextUtils.isEmpty(messageText)) {
            body.addPart(new TextSlide(context, messageText).getPart());
          }
        } catch (InvalidMessageException e) {
          Log.w("MmsDatabase", e);
        }

        return new SendReq(headers, body, messageId, outboxType, timestamp);
      }

      throw new NoSuchMessageException("No record found for id: " + messageId);
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public long copyMessageInbox(MasterSecret masterSecret, long messageId) throws MmsException {
    try {
      SendReq request = getOutgoingMessage(masterSecret, messageId);
      ContentValues contentValues = getContentValuesFromHeader(request.getPduHeaders());

      contentValues.put(MESSAGE_BOX, Types.BASE_INBOX_TYPE | Types.SECURE_MESSAGE_BIT | Types.ENCRYPTION_SYMMETRIC_BIT);
      contentValues.put(THREAD_ID, getThreadIdForMessage(messageId));
      contentValues.put(READ, 1);
      contentValues.put(DATE_RECEIVED, contentValues.getAsLong(DATE_SENT));

      for (int i = 0; i < request.getBody().getPartsNum(); i++) {
        request.getBody().getPart(i).setTransferProgress(PartDatabase.TRANSFER_PROGRESS_DONE);
      }

      return insertMediaMessage(new MasterSecretUnion(masterSecret), request.getPduHeaders(),
                                request.getBody(), contentValues);
    } catch (NoSuchMessageException e) {
      throw new MmsException(e);
    }
  }

  private Pair<Long, Long> insertMessageInbox(MasterSecretUnion masterSecret,
                                              IncomingMediaMessage retrieved,
                                              String contentLocation,
                                              long threadId, long mailbox)
      throws MmsException
  {
    PduHeaders    headers       = retrieved.getPduHeaders();
    ContentValues contentValues = getContentValuesFromHeader(headers);
    boolean       unread        = org.thoughtcrime.securesms.util.Util.isDefaultSmsProvider(context) ||
                                  ((mailbox & Types.SECURE_MESSAGE_BIT) != 0);

    if (threadId == -1 || retrieved.isGroupMessage()) {
      try {
        threadId = getThreadIdFor(retrieved);
      } catch (RecipientFormattingException e) {
        Log.w("MmsDatabase", e);
        if (threadId == -1)
          throw new MmsException(e);
      }
    }

    contentValues.put(MESSAGE_BOX, mailbox);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(CONTENT_LOCATION, contentLocation);
    contentValues.put(STATUS, Status.DOWNLOAD_INITIALIZED);
    contentValues.put(DATE_RECEIVED, generatePduCompatTimestamp());
    contentValues.put(READ, unread ? 0 : 1);

    if (!contentValues.containsKey(DATE_SENT)) {
      contentValues.put(DATE_SENT, contentValues.getAsLong(DATE_RECEIVED));
    }

    long messageId = insertMediaMessage(masterSecret, retrieved.getPduHeaders(),
                                        retrieved.getBody(), contentValues);

    if (unread) {
      DatabaseFactory.getThreadDatabase(context).setUnread(threadId);
    }

    DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
    jobManager.add(new TrimThreadJob(context, threadId));

    return new Pair<>(messageId, threadId);
  }

  public Pair<Long, Long> insertMessageInbox(MasterSecretUnion masterSecret,
                                             IncomingMediaMessage retrieved,
                                             String contentLocation, long threadId)
      throws MmsException
  {
    long type = Types.BASE_INBOX_TYPE;

    if (masterSecret.getMasterSecret().isPresent()) {
      type |= Types.ENCRYPTION_SYMMETRIC_BIT;
    } else {
      type |= Types.ENCRYPTION_ASYMMETRIC_BIT;
    }

    if (retrieved.isPushMessage()) {
      type |= Types.PUSH_MESSAGE_BIT;
    }

    return insertMessageInbox(masterSecret, retrieved, contentLocation, threadId, type);
  }

  public Pair<Long, Long> insertSecureDecryptedMessageInbox(MasterSecretUnion masterSecret,
                                                            IncomingMediaMessage retrieved,
                                                            long threadId)
      throws MmsException
  {
    long type = Types.BASE_INBOX_TYPE | Types.SECURE_MESSAGE_BIT;

    if (masterSecret.getMasterSecret().isPresent()) {
      type |= Types.ENCRYPTION_SYMMETRIC_BIT;
    } else {
      type |= Types.ENCRYPTION_ASYMMETRIC_BIT;
    }

    if (retrieved.isPushMessage()) {
      type |= Types.PUSH_MESSAGE_BIT;
    }

    return insertMessageInbox(masterSecret, retrieved, "", threadId, type);
  }

  public Pair<Long, Long> insertMessageInbox(@NonNull NotificationInd notification) {
    SQLiteDatabase     db              = databaseHelper.getWritableDatabase();
    MmsAddressDatabase addressDatabase = DatabaseFactory.getMmsAddressDatabase(context);
    long               threadId        = getThreadIdFor(notification);
    PduHeaders         headers         = notification.getPduHeaders();
    ContentValues      contentValues   = getContentValuesFromHeader(headers);

    Log.w(TAG, "Message received type: " + headers.getOctet(PduHeaders.MESSAGE_TYPE));

    contentValues.put(MESSAGE_BOX, Types.BASE_INBOX_TYPE);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(STATUS, Status.DOWNLOAD_INITIALIZED);
    contentValues.put(DATE_RECEIVED, generatePduCompatTimestamp());
    contentValues.put(READ, Util.isDefaultSmsProvider(context) ? 0 : 1);

    if (!contentValues.containsKey(DATE_SENT))
      contentValues.put(DATE_SENT, contentValues.getAsLong(DATE_RECEIVED));

    long messageId = db.insert(TABLE_NAME, null, contentValues);
    addressDatabase.insertAddressesForId(messageId, headers);

    return new Pair<>(messageId, threadId);
  }

  public void markIncomingNotificationReceived(long threadId) {
    notifyConversationListeners(threadId);
    DatabaseFactory.getThreadDatabase(context).update(threadId);

    if (org.thoughtcrime.securesms.util.Util.isDefaultSmsProvider(context)) {
      DatabaseFactory.getThreadDatabase(context).setUnread(threadId);
    }

    jobManager.add(new TrimThreadJob(context, threadId));
  }

  public long insertMessageOutbox(@NonNull MasterSecretUnion masterSecret,
                                  @NonNull OutgoingMediaMessage message,
                                  long threadId, boolean forceSms, long timestamp)
      throws MmsException
  {
    long type = Types.BASE_OUTBOX_TYPE;

    if (masterSecret.getMasterSecret().isPresent()) type |= Types.ENCRYPTION_SYMMETRIC_BIT;
    else                                            type |= Types.ENCRYPTION_ASYMMETRIC_BIT;

    if (message.isSecure()) type |= Types.SECURE_MESSAGE_BIT;
    if (forceSms)           type |= Types.MESSAGE_FORCE_SMS_BIT;

    if (message.isGroup()) {
      if      (((OutgoingGroupMediaMessage)message).isGroupUpdate()) type |= Types.GROUP_UPDATE_BIT;
      else if (((OutgoingGroupMediaMessage)message).isGroupQuit())   type |= Types.GROUP_QUIT_BIT;
    }

    SendReq sendRequest = new SendReq();
    sendRequest.setDate(timestamp / 1000L);
    sendRequest.setBody(message.getPduBody());
    sendRequest.setContentType(ContentType.MULTIPART_MIXED.getBytes());

    String[]             recipientsArray = message.getRecipients().toNumberStringArray(true);
    EncodedStringValue[] encodedNumbers  = EncodedStringValue.encodeStrings(recipientsArray);

    if (message.getRecipients().isSingleRecipient()) {
      sendRequest.setTo(encodedNumbers);
    } else if (message.getDistributionType() == ThreadDatabase.DistributionTypes.BROADCAST) {
      sendRequest.setBcc(encodedNumbers);
    } else if (message.getDistributionType() == ThreadDatabase.DistributionTypes.CONVERSATION  ||
               message.getDistributionType() == 0)
    {
      sendRequest.setTo(encodedNumbers);
    }

    PduHeaders    headers       = sendRequest.getPduHeaders();
    ContentValues contentValues = getContentValuesFromHeader(headers);

    contentValues.put(MESSAGE_BOX, type);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(READ, 1);
    contentValues.put(DATE_RECEIVED, contentValues.getAsLong(DATE_SENT));
    contentValues.remove(ADDRESS);

    if (sendRequest.getBody() != null) {
      for (int i = 0; i < sendRequest.getBody().getPartsNum(); i++) {
        sendRequest.getBody().getPart(i).setTransferProgress(PartDatabase.TRANSFER_PROGRESS_STARTED);
      }
    }

    long messageId = insertMediaMessage(masterSecret,
                                        sendRequest.getPduHeaders(),
                                        sendRequest.getBody(), contentValues);
    jobManager.add(new TrimThreadJob(context, threadId));

    return messageId;
  }

  private String getEncryptedBody(MasterSecretUnion masterSecret, String body) {
    if (masterSecret.getMasterSecret().isPresent()) {
      return new MasterCipher(masterSecret.getMasterSecret().get()).encryptBody(body);
    } else {
      return new AsymmetricMasterCipher(masterSecret.getAsymmetricMasterSecret().get()).encryptBody(body);
    }
  }

  private long insertMediaMessage(MasterSecretUnion masterSecret,
                                  PduHeaders headers,
                                  PduBody body,
                                  ContentValues contentValues)
      throws MmsException
  {
    SQLiteDatabase     db              = databaseHelper.getWritableDatabase();
    PartDatabase       partsDatabase   = DatabaseFactory.getPartDatabase(context);
    MmsAddressDatabase addressDatabase = DatabaseFactory.getMmsAddressDatabase(context);

    if (Types.isSymmetricEncryption(contentValues.getAsLong(MESSAGE_BOX)) ||
        Types.isAsymmetricEncryption(contentValues.getAsLong(MESSAGE_BOX)))
    {
      String messageText = PartParser.getMessageText(body);
      body               = PartParser.getSupportedMediaParts(body);

      if (!TextUtils.isEmpty(messageText)) {
        contentValues.put(BODY, getEncryptedBody(masterSecret, messageText));
      }
    }

    contentValues.put(PART_COUNT, PartParser.getSupportedMediaPartCount(body));

    db.beginTransaction();
    try {
      long messageId = db.insert(TABLE_NAME, null, contentValues);

      addressDatabase.insertAddressesForId(messageId, headers);
      partsDatabase.insertParts(masterSecret, messageId, body);

      notifyConversationListeners(contentValues.getAsLong(THREAD_ID));
      DatabaseFactory.getThreadDatabase(context).update(contentValues.getAsLong(THREAD_ID));
      db.setTransactionSuccessful();
      return messageId;
    } finally {
      db.endTransaction();
    }

  }

  public boolean delete(long messageId) {
    long threadId                   = getThreadIdForMessage(messageId);
    MmsAddressDatabase addrDatabase = DatabaseFactory.getMmsAddressDatabase(context);
    PartDatabase partDatabase       = DatabaseFactory.getPartDatabase(context);
    partDatabase.deleteParts(messageId);
    addrDatabase.deleteAddressesForId(messageId);

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, ID_WHERE, new String[] {messageId+""});
    boolean threadDeleted = DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
    return threadDeleted;
  }

  public void deleteThread(long threadId) {
    Set<Long> singleThreadSet = new HashSet<Long>();
    singleThreadSet.add(threadId);
    deleteThreads(singleThreadSet);
  }

  /*package*/ void deleteThreads(Set<Long> threadIds) {
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
        delete(cursor.getLong(0));
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  /*package*/void deleteMessagesInThreadBeforeDate(long threadId, long date) {
    Cursor cursor = null;

    try {
      SQLiteDatabase db = databaseHelper.getReadableDatabase();
      String where      = THREAD_ID + " = ? AND (CASE (" + MESSAGE_BOX + " & " + Types.BASE_TYPE_MASK + ") ";

      for (long outgoingType : Types.OUTGOING_MESSAGE_TYPES) {
        where += " WHEN " + outgoingType + " THEN " + DATE_SENT + " < " + date;
      }

      where += (" ELSE " + DATE_RECEIVED + " < " + date + " END)");

      Log.w("MmsDatabase", "Executing trim query: " + where);
      cursor = db.query(TABLE_NAME, new String[] {ID}, where, new String[] {threadId+""}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        Log.w("MmsDatabase", "Trimming: " + cursor.getLong(0));
        delete(cursor.getLong(0));
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }


  public void deleteAllThreads() {
    DatabaseFactory.getPartDatabase(context).deleteAllParts();
    DatabaseFactory.getMmsAddressDatabase(context).deleteAllAddresses();

    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);
  }

  public Cursor getCarrierMmsInformation(String apn) {
    Uri uri                = Uri.withAppendedPath(Uri.parse("content://telephony/carriers"), "current");
    String selection       = TextUtils.isEmpty(apn) ? null : "apn = ?";
    String[] selectionArgs = TextUtils.isEmpty(apn) ? null : new String[] {apn.trim()};

    try {
      return context.getContentResolver().query(uri, null, selection, selectionArgs, null);
    } catch (NullPointerException npe) {
      // NOTE - This is dumb, but on some devices there's an NPE in the Android framework
      // for the provider of this call, which gets rethrown back to here through a binder
      // call.
      throw new IllegalArgumentException(npe);
    }
  }

  private PduHeaders getHeadersFromCursor(Cursor cursor) throws InvalidHeaderValueException {
    PduHeaders headers    = new PduHeaders();
    PduHeadersBuilder phb = new PduHeadersBuilder(headers, cursor);

    phb.add(RETRIEVE_TEXT, RETRIEVE_TEXT_CS, PduHeaders.RETRIEVE_TEXT);
    phb.add(SUBJECT, SUBJECT_CHARSET, PduHeaders.SUBJECT);
    phb.addText(CONTENT_LOCATION, PduHeaders.CONTENT_LOCATION);
    phb.addText(CONTENT_TYPE, PduHeaders.CONTENT_TYPE);
    phb.addText(MESSAGE_CLASS, PduHeaders.MESSAGE_CLASS);
    phb.addText(MESSAGE_ID, PduHeaders.MESSAGE_ID);
    phb.addText(RESPONSE_TEXT, PduHeaders.RESPONSE_TEXT);
    phb.addText(TRANSACTION_ID, PduHeaders.TRANSACTION_ID);
    phb.addOctet(CONTENT_CLASS, PduHeaders.CONTENT_CLASS);
    phb.addOctet(DELIVERY_REPORT, PduHeaders.DELIVERY_REPORT);
    phb.addOctet(MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE);
    phb.addOctet(MMS_VERSION, PduHeaders.MMS_VERSION);
    phb.addOctet(PRIORITY, PduHeaders.PRIORITY);
    phb.addOctet(READ_STATUS, PduHeaders.READ_STATUS);
    phb.addOctet(REPORT_ALLOWED, PduHeaders.REPORT_ALLOWED);
    phb.addOctet(RETRIEVE_STATUS, PduHeaders.RETRIEVE_STATUS);
    phb.addOctet(STATUS, PduHeaders.STATUS);
    phb.addLong(NORMALIZED_DATE_SENT, PduHeaders.DATE);
    phb.addLong(DELIVERY_TIME, PduHeaders.DELIVERY_TIME);
    phb.addLong(EXPIRY, PduHeaders.EXPIRY);
    phb.addLong(MESSAGE_SIZE, PduHeaders.MESSAGE_SIZE);

    headers.setLongInteger(headers.getLongInteger(PduHeaders.DATE) / 1000L, PduHeaders.DATE);

    return headers;
  }

  private ContentValues getContentValuesFromHeader(PduHeaders headers) {
    ContentValues contentValues = new ContentValues();
    ContentValuesBuilder cvb    = new ContentValuesBuilder(contentValues);

    cvb.add(RETRIEVE_TEXT, RETRIEVE_TEXT_CS, headers.getEncodedStringValue(PduHeaders.RETRIEVE_TEXT));
    cvb.add(SUBJECT, SUBJECT_CHARSET, headers.getEncodedStringValue(PduHeaders.SUBJECT));
    cvb.add(CONTENT_LOCATION, headers.getTextString(PduHeaders.CONTENT_LOCATION));
    cvb.add(CONTENT_TYPE, headers.getTextString(PduHeaders.CONTENT_TYPE));
    cvb.add(MESSAGE_CLASS, headers.getTextString(PduHeaders.MESSAGE_CLASS));
    cvb.add(MESSAGE_ID, headers.getTextString(PduHeaders.MESSAGE_ID));
    cvb.add(RESPONSE_TEXT, headers.getTextString(PduHeaders.RESPONSE_TEXT));
    cvb.add(TRANSACTION_ID, headers.getTextString(PduHeaders.TRANSACTION_ID));
    cvb.add(CONTENT_CLASS, headers.getOctet(PduHeaders.CONTENT_CLASS));
    cvb.add(DELIVERY_REPORT, headers.getOctet(PduHeaders.DELIVERY_REPORT));
    cvb.add(MESSAGE_TYPE, headers.getOctet(PduHeaders.MESSAGE_TYPE));
    cvb.add(MMS_VERSION, headers.getOctet(PduHeaders.MMS_VERSION));
    cvb.add(PRIORITY, headers.getOctet(PduHeaders.PRIORITY));
    cvb.add(READ_REPORT, headers.getOctet(PduHeaders.READ_REPORT));
    cvb.add(READ_STATUS, headers.getOctet(PduHeaders.READ_STATUS));
    cvb.add(REPORT_ALLOWED, headers.getOctet(PduHeaders.REPORT_ALLOWED));
    cvb.add(RETRIEVE_STATUS, headers.getOctet(PduHeaders.RETRIEVE_STATUS));
    cvb.add(STATUS, headers.getOctet(PduHeaders.STATUS));
    cvb.add(DATE_SENT, headers.getLongInteger(PduHeaders.DATE) * 1000L);
    cvb.add(DELIVERY_TIME, headers.getLongInteger(PduHeaders.DELIVERY_TIME));
    cvb.add(EXPIRY, headers.getLongInteger(PduHeaders.EXPIRY));
    cvb.add(MESSAGE_SIZE, headers.getLongInteger(PduHeaders.MESSAGE_SIZE));

    if (headers.getEncodedStringValue(PduHeaders.FROM) != null)
      cvb.add(ADDRESS, headers.getEncodedStringValue(PduHeaders.FROM).getTextString());
    else
      cvb.add(ADDRESS, null);

    return cvb.getContentValues();
  }

  public Reader readerFor(MasterSecret masterSecret, Cursor cursor) {
    return new Reader(masterSecret, cursor);
  }

  public static class Status {
    public static final int DOWNLOAD_INITIALIZED     = 1;
    public static final int DOWNLOAD_NO_CONNECTIVITY = 2;
    public static final int DOWNLOAD_CONNECTING      = 3;
    public static final int DOWNLOAD_SOFT_FAILURE    = 4;
    public static final int DOWNLOAD_HARD_FAILURE    = 5;
    public static final int DOWNLOAD_APN_UNAVAILABLE = 6;

    public static boolean isDisplayDownloadButton(int status) {
      return
          status == DOWNLOAD_INITIALIZED     ||
          status == DOWNLOAD_NO_CONNECTIVITY ||
          status == DOWNLOAD_SOFT_FAILURE;
    }

    public static String getLabelForStatus(Context context, int status) {
      switch (status) {
        case DOWNLOAD_CONNECTING:      return context.getString(R.string.MmsDatabase_connecting_to_mms_server);
        case DOWNLOAD_INITIALIZED:     return context.getString(R.string.MmsDatabase_downloading_mms);
        case DOWNLOAD_HARD_FAILURE:    return context.getString(R.string.MmsDatabase_mms_download_failed);
        case DOWNLOAD_APN_UNAVAILABLE: return context.getString(R.string.MmsDatabase_mms_pending_download);
      }

      return context.getString(R.string.MmsDatabase_downloading);
    }

    public static boolean isHardError(int status) {
      return status == DOWNLOAD_HARD_FAILURE;
    }
  }

  public class Reader {

    private final Cursor       cursor;
    private final MasterSecret masterSecret;
    private final MasterCipher masterCipher;

    public Reader(MasterSecret masterSecret, Cursor cursor) {
      this.cursor       = cursor;
      this.masterSecret = masterSecret;

      if (masterSecret != null) masterCipher = new MasterCipher(masterSecret);
      else                      masterCipher = null;
    }

    public MessageRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public MessageRecord getCurrent() {
      long mmsType = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_TYPE));

      if (mmsType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
        return getNotificationMmsMessageRecord(cursor);
      } else {
        return getMediaMmsMessageRecord(cursor);
      }
    }

    private NotificationMmsMessageRecord getNotificationMmsMessageRecord(Cursor cursor) {
      long id                    = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.ID));
      long dateSent              = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_SENT));
      long dateReceived          = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_RECEIVED));
      long threadId              = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.THREAD_ID));
      long mailbox               = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));
      String address             = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS));
      int addressDeviceId        = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS_DEVICE_ID));
      Recipients recipients      = getRecipientsFor(address);

      String contentLocation     = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.CONTENT_LOCATION));
      String transactionId       = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.TRANSACTION_ID));
      long messageSize           = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_SIZE));
      long expiry                = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.EXPIRY));
      int status                 = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.STATUS));
      int receiptCount           = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.RECEIPT_COUNT));

      byte[]contentLocationBytes = null;
      byte[]transactionIdBytes   = null;

      if (!TextUtils.isEmpty(contentLocation))
        contentLocationBytes = org.thoughtcrime.securesms.util.Util.toIsoBytes(contentLocation);

      if (!TextUtils.isEmpty(transactionId))
        transactionIdBytes = org.thoughtcrime.securesms.util.Util.toIsoBytes(transactionId);


      return new NotificationMmsMessageRecord(context, id, recipients, recipients.getPrimaryRecipient(),
                                              addressDeviceId, dateSent, dateReceived, receiptCount, threadId,
                                              contentLocationBytes, messageSize, expiry, status,
                                              transactionIdBytes, mailbox);
    }

    private MediaMmsMessageRecord getMediaMmsMessageRecord(Cursor cursor) {
      long id                 = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.ID));
      long dateSent           = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_SENT));
      long dateReceived       = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.NORMALIZED_DATE_RECEIVED));
      long box                = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));
      long threadId           = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.THREAD_ID));
      String address          = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS));
      int addressDeviceId     = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.ADDRESS_DEVICE_ID));
      int receiptCount        = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.RECEIPT_COUNT));
      DisplayRecord.Body body = getBody(cursor);
      int partCount           = cursor.getInt(cursor.getColumnIndexOrThrow(MmsDatabase.PART_COUNT));
      String mismatchDocument = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.MISMATCHED_IDENTITIES));
      String networkDocument  = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.NETWORK_FAILURE));

      Recipients                recipients      = getRecipientsFor(address);
      List<IdentityKeyMismatch> mismatches      = getMismatchedIdentities(mismatchDocument);
      List<NetworkFailure>      networkFailures = getFailures(networkDocument);

      ListenableFutureTask<SlideDeck> slideDeck = getSlideDeck(dateReceived, id);

      return new MediaMmsMessageRecord(context, id, recipients, recipients.getPrimaryRecipient(),
                                       addressDeviceId, dateSent, dateReceived, receiptCount,
                                       threadId, body, slideDeck, partCount, box, mismatches, networkFailures);
    }

    private Recipients getRecipientsFor(String address) {
      if (TextUtils.isEmpty(address) || address.equals("insert-address-token")) {
        return RecipientFactory.getRecipientsFor(context, Recipient.getUnknownRecipient(), true);
      }

      Recipients recipients =  RecipientFactory.getRecipientsFromString(context, address, true);

      if (recipients == null || recipients.isEmpty()) {
        return RecipientFactory.getRecipientsFor(context, Recipient.getUnknownRecipient(), true);
      }

      return recipients;
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

    private DisplayRecord.Body getBody(Cursor cursor) {
      try {
        String body = cursor.getString(cursor.getColumnIndexOrThrow(MmsDatabase.BODY));
        long box    = cursor.getLong(cursor.getColumnIndexOrThrow(MmsDatabase.MESSAGE_BOX));

        if (!TextUtils.isEmpty(body) && masterCipher != null && Types.isSymmetricEncryption(box)) {
          return new DisplayRecord.Body(masterCipher.decryptBody(body), true);
        } else if (!TextUtils.isEmpty(body) && masterCipher == null && Types.isSymmetricEncryption(box)) {
          return new DisplayRecord.Body(body, false);
        } else if (!TextUtils.isEmpty(body) && Types.isAsymmetricEncryption(box)) {
          return new DisplayRecord.Body(body, false);
        } else {
          return new DisplayRecord.Body(body == null ? "" : body, true);
        }
      } catch (InvalidMessageException e) {
        Log.w("MmsDatabase", e);
        return new DisplayRecord.Body(context.getString(R.string.MmsDatabase_error_decrypting_message), true);
      }
    }

    private ListenableFutureTask<SlideDeck> getSlideDeck(final long timestamp,
                                                         final long id)
    {
      ListenableFutureTask<SlideDeck> future = getCachedSlideDeck(timestamp, id);

      if (future != null) {
        return future;
      }

      Callable<SlideDeck> task = new Callable<SlideDeck>() {
        @Override
        public SlideDeck call() throws Exception {
          PartDatabase partDatabase = DatabaseFactory.getPartDatabase(context);
          PduBody      body         = getPartsAsBody(partDatabase.getParts(id));
          SlideDeck    slideDeck    = new SlideDeck(context, body);

          if (!body.containsPushInProgress()) {
            slideCache.put(timestamp + "::" + id, new SoftReference<>(slideDeck));
          }

          return slideDeck;
        }
      };

      future = new ListenableFutureTask<>(task);
      slideResolver.execute(future);

      return future;
    }

    private ListenableFutureTask<SlideDeck> getCachedSlideDeck(final long timestamp, final long id) {
      SoftReference<SlideDeck> reference = slideCache.get(timestamp + "::" + id);

      if (reference != null) {
        final SlideDeck slideDeck = reference.get();

        if (slideDeck != null) {
          Callable<SlideDeck> task = new Callable<SlideDeck>() {
            @Override
            public SlideDeck call() throws Exception {
              return slideDeck;
            }
          };

          ListenableFutureTask<SlideDeck> future = new ListenableFutureTask<>(task);
          future.run();

          return future;
        }
      }

      return null;
    }

    public void close() {
      cursor.close();
    }
  }

  private long generatePduCompatTimestamp() {
    final long time = System.currentTimeMillis();
    return time - (time % 1000);
  }

  private PduBody getPartsAsBody(List<PduPart> parts) {
    PduBody body = new PduBody();

    for (PduPart part : parts) {
      body.addPart(part);
    }

    return body;
  }

}

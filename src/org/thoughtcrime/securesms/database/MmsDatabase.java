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

import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;

import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;

import ws.com.google.android.mms.InvalidHeaderValueException;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.CharacterSets;
import ws.com.google.android.mms.pdu.EncodedStringValue;
import ws.com.google.android.mms.pdu.MultimediaMessagePdu;
import ws.com.google.android.mms.pdu.NotificationInd;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.PduHeaders;
import ws.com.google.android.mms.pdu.RetrieveConf;
import ws.com.google.android.mms.pdu.SendReq;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

public class MmsDatabase extends Database {
	
  public  static final String TABLE_NAME         = "mms";
  public  static final String ID                 = "_id";
  private static final String THREAD_ID          = "thread_id";
  private static final String DATE               = "date";
  public  static final String MESSAGE_BOX        = "msg_box";
  private static final String READ               = "read";
  private static final String MESSAGE_ID         = "m_id";
  private static final String SUBJECT            = "sub";
  private static final String SUBJECT_CHARSET    = "sub_cs";
  private static final String CONTENT_TYPE       = "ct_t";
  private static final String CONTENT_LOCATION   = "ct_l";
  private static final String EXPIRY             = "exp";
  private static final String MESSAGE_CLASS      = "m_cls";
  public  static final String MESSAGE_TYPE       = "m_type";
  private static final String MMS_VERSION        = "v";
  private static final String MESSAGE_SIZE       = "m_size";
  private static final String PRIORITY           = "pri";
  private static final String READ_REPORT        = "rr";
  private static final String REPORT_ALLOWED     = "rpt_a";
  private static final String RESPONSE_STATUS    = "resp_st";
  private static final String STATUS             = "st";
  private static final String TRANSACTION_ID     = "tr_id";
  private static final String RETRIEVE_STATUS    = "retr_st";
  private static final String RETRIEVE_TEXT      = "retr_txt";
  private static final String RETRIEVE_TEXT_CS   = "retr_txt_cs";
  private static final String READ_STATUS        = "read_status";
  private static final String CONTENT_CLASS      = "ct_cls";
  private static final String RESPONSE_TEXT      = "resp_txt";
  private static final String DELIVERY_TIME      = "d_tm";
  private static final String DELIVERY_REPORT    = "d_rpt";
	
  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" + ID + " INTEGER PRIMARY KEY, "                          +
    THREAD_ID + " INTEGER, " + DATE + " INTEGER, " + MESSAGE_BOX + " INTEGER, "                 +
    READ + " INTEGER DEFAULT 0, " + MESSAGE_ID + " TEXT, " + SUBJECT + " TEXT, "                +
    SUBJECT_CHARSET + " INTEGER, " + CONTENT_TYPE + " TEXT, " + CONTENT_LOCATION + " TEXT, "    +
    EXPIRY + " INTEGER, " + MESSAGE_CLASS + " TEXT, " + MESSAGE_TYPE + " INTEGER, "             +
    MMS_VERSION + " INTEGER, " + MESSAGE_SIZE + " INTEGER, " + PRIORITY + " INTEGER, "          +
    READ_REPORT + " INTEGER, " + REPORT_ALLOWED + " INTEGER, " + RESPONSE_STATUS + " INTEGER, " +
    STATUS + " INTEGER, " + TRANSACTION_ID + " TEXT, " + RETRIEVE_STATUS + " INTEGER, "         +
    RETRIEVE_TEXT + " TEXT, " + RETRIEVE_TEXT_CS + " INTEGER, " + READ_STATUS + " INTEGER, "    + 
    CONTENT_CLASS + " INTEGER, " + RESPONSE_TEXT + " TEXT, " + DELIVERY_TIME + " INTEGER, "     +
    DELIVERY_REPORT + " INTEGER);";

  public MmsDatabase(Context context, SQLiteOpenHelper databaseHelper) {
    super(context, databaseHelper);
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
	
  private long getThreadIdForHeaders(PduHeaders headers) throws RecipientFormattingException {		
    try {
      EncodedStringValue encodedString = headers.getEncodedStringValue(PduHeaders.FROM);
      String fromString                = new String(encodedString.getTextString(), CharacterSets.MIMENAME_ISO_8859_1);
      Recipients recipients            = RecipientFactory.getRecipientsFromString(context, fromString);
      return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }
	
  public String getMessageRecipient(long messageId) {
    try {
      PduHeaders headers          = new PduHeaders();
      MmsAddressDatabase database = DatabaseFactory.getMmsAddressDatabase(context);
      database.getAddressesForId(messageId, headers);
			
      EncodedStringValue encodedFrom = headers.getEncodedStringValue(PduHeaders.FROM);
      if (encodedFrom != null)
        return new String(encodedFrom.getTextString(), CharacterSets.MIMENAME_ISO_8859_1);
      else
        return "Anonymous";
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }
	
  public void updateResponseStatus(long messageId, int status) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(RESPONSE_STATUS, status);
		
    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId+""});
  }

  public void markAsSentFailed(long messageId) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(MESSAGE_BOX, Types.MESSAGE_BOX_SENT_FAILED);
		
    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[]{messageId+""});
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }
	
  public void markAsSent(long messageId, byte[] mmsId, long status) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(RESPONSE_STATUS, status);
    contentValues.put(MESSAGE_ID, new String(mmsId));
    contentValues.put(MESSAGE_BOX, Types.MESSAGE_BOX_SENT);
		
    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId+""});
    notifyConversationListeners(getThreadIdForMessage(messageId));
  }
	
  public void markAsSecureSent(long messageId, byte[] mmsId, long status) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(RESPONSE_STATUS, status);
    contentValues.put(MESSAGE_ID, new String(mmsId));
    contentValues.put(MESSAGE_BOX, Types.MESSAGE_BOX_SECURE_SENT);
		
    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId+""});
    notifyConversationListeners(getThreadIdForMessage(messageId));		
  }
	
  public void markDownloadState(long messageId, long state) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(STATUS, state);
		
    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId+""});
    notifyConversationListeners(getThreadIdForMessage(messageId));				
  }
	
  public void markAsNoSession(long messageId, long threadId) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(MESSAGE_BOX, Types.MESSAGE_BOX_NO_SESSION_INBOX);
		
    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId+""});
    notifyConversationListeners(threadId);				
  }
	
  public void markAsDecryptFailed(long messageId, long threadId) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(MESSAGE_BOX, Types.MESSAGE_BOX_DECRYPT_FAILED_INBOX);
		
    database.update(TABLE_NAME, contentValues, ID_WHERE, new String[] {messageId+""});
    notifyConversationListeners(threadId);						
  }
	
  public void setMessagesRead(long threadId) {
    SQLiteDatabase database     = databaseHelper.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(READ, 1);
		
    database.update(TABLE_NAME, contentValues, THREAD_ID + " = ?", new String[] {threadId+""});
  }

  public NotificationInd getNotificationMessage(long messageId) throws MmsException {		
    PduHeaders headers        = getHeadersForId(messageId);
    return new NotificationInd(headers);
  }
	
  public MultimediaMessagePdu getMediaMessage(long messageId) throws MmsException {
    PduHeaders headers        = getHeadersForId(messageId);
    PartDatabase partDatabase = getPartDatabase();
    PduBody body              = partDatabase.getParts(messageId, false);

    return new MultimediaMessagePdu(headers, body);
  }
		
  public SendReq getSendRequest(long messageId) throws MmsException {
    PduHeaders headers        = getHeadersForId(messageId);
    PartDatabase partDatabase = getPartDatabase();
    PduBody body              = partDatabase.getParts(messageId, true);

    return new SendReq(headers, body, messageId, headers.getMessageBox());
  }
	
  public SendReq[] getOutgoingMessages() throws MmsException {
    MmsAddressDatabase addr = DatabaseFactory.getMmsAddressDatabase(context);
    PartDatabase parts      = getPartDatabase();
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = null;
		
    try {
      cursor = database.query(TABLE_NAME, null, MESSAGE_BOX + " = ? OR " + MESSAGE_BOX + " = ?", new String[] {Types.MESSAGE_BOX_OUTBOX+"", Types.MESSAGE_BOX_SECURE_OUTBOX+""}, null, null, null);
			
      if (cursor == null || cursor.getCount() == 0)
        return new SendReq[0];
			
      SendReq[] requests = new SendReq[cursor.getCount()];
      int i = 0;
			
      while (cursor.moveToNext()) {
        long messageId     = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      	long outboxType    = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX));
      	PduHeaders headers = getHeadersFromCursor(cursor);				
      	addr.getAddressesForId(messageId, headers);
      	PduBody body       = parts.getParts(messageId, true);
      	requests[i++]      = new SendReq(headers, body, messageId, outboxType);
      }

      return requests;
    } finally {
      if (cursor != null)
        cursor.close();
    }		
  }

  private long insertMessageReceived(MultimediaMessagePdu retrieved, String contentLocation, long threadId, long mailbox) throws MmsException {
    PduHeaders headers          = retrieved.getPduHeaders();
    ContentValues contentValues = getContentValuesFromHeader(headers);
		
    contentValues.put(MESSAGE_BOX, mailbox);
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(CONTENT_LOCATION, contentLocation);
    contentValues.put(STATUS, Types.DOWNLOAD_INITIALIZED);
		
    long messageId = insertMediaMessage(retrieved, contentValues);
    return messageId;
  }
	
  public long insertMessageReceived(RetrieveConf retrieved, String contentLocation, long threadId) throws MmsException {
    return insertMessageReceived(retrieved, contentLocation, threadId, Types.MESSAGE_BOX_INBOX);
  }	
	
  public long insertSecureMessageReceived(RetrieveConf retrieved, String contentLocation, long threadId) throws MmsException {
    return insertMessageReceived(retrieved, contentLocation, threadId, Types.MESSAGE_BOX_DECRYPTING_INBOX);
  }
	
  public long insertSecureDecryptedMessageReceived(MultimediaMessagePdu retrieved, long threadId) throws MmsException {
    return insertMessageReceived(retrieved, "", threadId, Types.MESSAGE_BOX_SECURE_INBOX);
  }
	
  public long insertMessageReceived(NotificationInd notification) {
    try {
      SQLiteDatabase db                  = databaseHelper.getWritableDatabase();
      PduHeaders headers                 = notification.getPduHeaders();
      ContentValues contentValues        = getContentValuesFromHeader(headers);
      long threadId                      = getThreadIdForHeaders(headers);
      MmsAddressDatabase addressDatabase = DatabaseFactory.getMmsAddressDatabase(context);
	
      Log.w("MmsDatabse", "Message received type: " + headers.getOctet(PduHeaders.MESSAGE_TYPE));
			
      contentValues.put(MESSAGE_BOX, Types.MESSAGE_BOX_INBOX);
      contentValues.put(THREAD_ID, threadId);
      contentValues.put(STATUS, Types.DOWNLOAD_INITIALIZED);
      if (!contentValues.containsKey(DATE))
        contentValues.put(DATE, System.currentTimeMillis() / 1000);
			
      long messageId = db.insert(TABLE_NAME, null, contentValues);		
      addressDatabase.insertAddressesForId(messageId, headers);
			
      notifyConversationListeners(threadId);			
      DatabaseFactory.getThreadDatabase(context).update(threadId);
      DatabaseFactory.getThreadDatabase(context).setUnread(threadId);
			
      return messageId;
    } catch (RecipientFormattingException rfe) {
      Log.w("MmsDatabase", rfe);
      return -1;
    }
  }
	
  public long insertMessageSent(SendReq sendRequest, long threadId, boolean isSecure) throws MmsException {
    PduHeaders headers          = sendRequest.getPduHeaders();
    ContentValues contentValues = getContentValuesFromHeader(headers);
		
    if (!isSecure) contentValues.put(MESSAGE_BOX, Types.MESSAGE_BOX_OUTBOX);
    else		   contentValues.put(MESSAGE_BOX, Types.MESSAGE_BOX_SECURE_OUTBOX);
		
    contentValues.put(THREAD_ID, threadId);
    contentValues.put(READ, 1);
		
    long messageId = insertMediaMessage(sendRequest, contentValues);
    DatabaseFactory.getThreadDatabase(context).setRead(threadId);
    return messageId;
  }
	
  private long insertMediaMessage(MultimediaMessagePdu message, ContentValues contentValues) throws MmsException {
    SQLiteDatabase db                  = databaseHelper.getWritableDatabase();
    long messageId                     = db.insert(TABLE_NAME, null, contentValues);		
    PduBody body                       = message.getBody();
    PartDatabase partsDatabase         = getPartDatabase();
    MmsAddressDatabase addressDatabase = DatabaseFactory.getMmsAddressDatabase(context);

    addressDatabase.insertAddressesForId(messageId, message.getPduHeaders());
    partsDatabase.insertParts(messageId, body);

    notifyConversationListeners(contentValues.getAsLong(THREAD_ID));
    DatabaseFactory.getThreadDatabase(context).update(contentValues.getAsLong(THREAD_ID));

    return messageId;		
  }
	
  public void delete(long messageId) {
    long threadId                   = getThreadIdForMessage(messageId);
    MmsAddressDatabase addrDatabase = DatabaseFactory.getMmsAddressDatabase(context);
    PartDatabase partDatabase       = DatabaseFactory.getPartDatabase(context);
    partDatabase.deleteParts(messageId);
    addrDatabase.deleteAddressesForId(messageId);
		
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, ID_WHERE, new String[] {messageId+""});
    DatabaseFactory.getThreadDatabase(context).update(threadId);
    notifyConversationListeners(threadId);
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

	
  public void deleteAllThreads() {
    DatabaseFactory.getPartDatabase(context).deleteAllParts();
    DatabaseFactory.getMmsAddressDatabase(context).deleteAllAddresses();
		
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.delete(TABLE_NAME, null, null);
  }
	
  public Cursor getCarrierMmsInformation() {
    Uri uri          = Uri.withAppendedPath(Uri.parse("content://telephony/carriers"), "current");
    String selection = "type = 'mms'";
		
    return context.getContentResolver().query(uri, null, selection, null, null);    
  }

  private PduHeaders getHeadersForId(long messageId) throws MmsException {
    SQLiteDatabase database = databaseHelper.getReadableDatabase();
    Cursor cursor           = null;
		
    try {
      cursor = database.query(TABLE_NAME, null, ID_WHERE, new String[] {messageId+""}, null, null, null);
			
      if (cursor == null || !cursor.moveToFirst())
        throw new MmsException("No headers available at ID: " + messageId);
			
      PduHeaders headers      = getHeadersFromCursor(cursor);
      long messageBox         = cursor.getLong(cursor.getColumnIndexOrThrow(MESSAGE_BOX));
      MmsAddressDatabase addr = DatabaseFactory.getMmsAddressDatabase(context);
			
      addr.getAddressesForId(messageId, headers);
      headers.setMessageBox(messageBox);

      return headers;
    } finally {
      if (cursor != null)
	cursor.close();
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
    phb.addLong(DATE, PduHeaders.DATE);
    phb.addLong(DELIVERY_TIME, PduHeaders.DELIVERY_TIME);
    phb.addLong(EXPIRY, PduHeaders.EXPIRY);
    phb.addLong(MESSAGE_SIZE, PduHeaders.MESSAGE_SIZE);
		
    return phb.getHeaders();
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
    cvb.add(DATE, headers.getLongInteger(PduHeaders.DATE));
    cvb.add(DELIVERY_TIME, headers.getLongInteger(PduHeaders.DELIVERY_TIME));
    cvb.add(EXPIRY, headers.getLongInteger(PduHeaders.EXPIRY));
    cvb.add(MESSAGE_SIZE, headers.getLongInteger(PduHeaders.MESSAGE_SIZE));
		
    return cvb.getContentValues();
  }
	
	
  protected PartDatabase getPartDatabase() {
    return DatabaseFactory.getPartDatabase(context);
  }
	
  public static class Types {
    public static final String  MMS_ERROR_TYPE       = "err_type";

    public static final int MESSAGE_BOX_INBOX             = 1;
    public static final int MESSAGE_BOX_SENT              = 2;
    public static final int MESSAGE_BOX_DRAFTS            = 3;
    public static final int MESSAGE_BOX_OUTBOX            = 4;
    public static final int MESSAGE_BOX_SECURE_OUTBOX     = 5;
    public static final int MESSAGE_BOX_SECURE_SENT       = 6;
    public static final int MESSAGE_BOX_DECRYPTING_INBOX  = 7;
    public static final int MESSAGE_BOX_SECURE_INBOX      = 8;
    public static final int MESSAGE_BOX_NO_SESSION_INBOX  = 9;
    public static final int MESSAGE_BOX_DECRYPT_FAILED_INBOX = 10;
        
    public static final int MESSAGE_BOX_SENT_FAILED = 12;
        
    public static final int DOWNLOAD_INITIALIZED     = 1;
    public static final int DOWNLOAD_NO_CONNECTIVITY = 2;
    public static final int DOWNLOAD_CONNECTING      = 3;
    public static final int	DOWNLOAD_SOFT_FAILURE    = 4;
    public static final int	DOWNLOAD_HARD_FAILURE	 = 5;


    public static boolean isSecureMmsBox(long mailbox) {
      return mailbox == Types.MESSAGE_BOX_SECURE_OUTBOX || mailbox == Types.MESSAGE_BOX_SECURE_SENT || mailbox == Types.MESSAGE_BOX_SECURE_INBOX;
    }
        
    public static boolean isOutgoingMmsBox(long mailbox) {
      return mailbox == Types.MESSAGE_BOX_OUTBOX || mailbox == Types.MESSAGE_BOX_SENT || mailbox == Types.MESSAGE_BOX_SECURE_OUTBOX || mailbox == Types.MESSAGE_BOX_SENT_FAILED || mailbox == Types.MESSAGE_BOX_SECURE_SENT;
    }
		
    public static boolean isPendingMmsBox(long mailbox) {
      return mailbox == Types.MESSAGE_BOX_OUTBOX || mailbox == MESSAGE_BOX_SECURE_OUTBOX;
    }
		
    public static boolean isFailedMmsBox(long mailbox) {
      return mailbox == Types.MESSAGE_BOX_SENT_FAILED;
    }

    public static boolean isDisplayDownloadButton(int status) {
      return status == DOWNLOAD_INITIALIZED || status == DOWNLOAD_NO_CONNECTIVITY || status == DOWNLOAD_SOFT_FAILURE;
    }

    public static String getLabelForStatus(int status) {
      Log.w("MmsDatabase", "Getting label for status: " + status);
			
      switch (status) {
      case DOWNLOAD_CONNECTING:   return "Connecting to MMS server...";
      case DOWNLOAD_INITIALIZED:  return "Downloading MMS...";
      case DOWNLOAD_HARD_FAILURE: return "MMS Download failed!";
      }
			
      return "Downloading...";
    }

    public static boolean isHardError(int status) {
      return status == DOWNLOAD_HARD_FAILURE;
    }
		

  }


}

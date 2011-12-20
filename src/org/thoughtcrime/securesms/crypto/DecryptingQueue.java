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
package org.thoughtcrime.securesms.crypto;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingMmsDatabase;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.mms.TextTransport;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.WorkerThread;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.MultimediaMessagePdu;
import ws.com.google.android.mms.pdu.PduParser;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

/**
 * A work queue for processing a number of encryption operations.
 * 
 * @author Moxie Marlinspike
 */

public class DecryptingQueue {

  private static List<Runnable> workQueue = new LinkedList<Runnable>();
  private static Thread workerThread;
	
  static {
    workerThread = new WorkerThread(workQueue, "Async Decryption Thread");
    workerThread.start();
  }	
	
  public static void scheduleDecryption(Context context, MasterSecret masterSecret, long messageId, long threadId, MultimediaMessagePdu mms) {
    MmsDecryptionItem runnable = new MmsDecryptionItem(context, masterSecret, messageId, threadId, mms);
    synchronized (workQueue) {
      workQueue.add(runnable);
      workQueue.notifyAll();
    }
  }
	
  public static void scheduleDecryption(Context context, MasterSecret masterSecret, long messageId, String originator, String body) {
    DecryptionWorkItem runnable = new DecryptionWorkItem(context, masterSecret, messageId, body, originator);
    synchronized (workQueue) {
      workQueue.add(runnable);
      workQueue.notifyAll();
    }
  }
	
  public static void schedulePendingDecrypts(Context context, MasterSecret masterSecret) {
    Cursor cursor = null;
    Log.w("DecryptingQueue", "Processing pending decrypts...");
		
    try {
      cursor = DatabaseFactory.getSmsDatabase(context).getDecryptInProgressMessages();
      if (cursor == null || cursor.getCount() == 0 || !cursor.moveToFirst())
        return;
			
      do {
        scheduleDecryptFromCursor(context, masterSecret, cursor);
      } while (cursor.moveToNext());
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public static void scheduleRogueMessages(Context context, MasterSecret masterSecret, Recipient recipient) {
    Cursor cursor = null;
		
    try {
      cursor = DatabaseFactory.getSmsDatabase(context).getEncryptedRogueMessages(recipient);
      if (cursor == null || cursor.getCount() == 0 || !cursor.moveToFirst())
        return;
			
      do {
        DatabaseFactory.getSmsDatabase(context).markAsDecrypting(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
        scheduleDecryptFromCursor(context, masterSecret, cursor);
      } while (cursor.moveToNext());
    } finally {
      if (cursor != null)
        cursor.close();
    }		
  }
	
  private static void scheduleDecryptFromCursor(Context context, MasterSecret masterSecret, Cursor cursor) {
    long id             = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
    String originator   = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
    String body         = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));

    scheduleDecryption(context, masterSecret, id, originator, body);
  }
	
  private static class MmsDecryptionItem implements Runnable {
    private long messageId;
    private long threadId;
    private Context context;
    private MasterSecret masterSecret;
    private MultimediaMessagePdu pdu;
		
    public MmsDecryptionItem(Context context, MasterSecret masterSecret, long messageId, long threadId, MultimediaMessagePdu pdu) {
      this.context      = context;
      this.masterSecret = masterSecret;
      this.messageId    = messageId;
      this.threadId     = threadId;
      this.pdu          = pdu;
    }
		
    private byte[] getEncryptedData() {
      for (int i=0;i<pdu.getBody().getPartsNum();i++) {
        Log.w("DecryptingQueue", "Content type (" + i + "): " + new String(pdu.getBody().getPart(i).getContentType()));
        if (new String(pdu.getBody().getPart(i).getContentType()).equals(ContentType.TEXT_PLAIN)) {
          return pdu.getBody().getPart(i).getData();
        }
      }
			
      return null;
    }
		
    public void run() {
      EncryptingMmsDatabase database = DatabaseFactory.getEncryptingMmsDatabase(context, masterSecret);
			
      try {
        String messageFrom        = pdu.getFrom().getString();
        Recipients recipients     = RecipientFactory.getRecipientsFromString(context, messageFrom);
        Recipient recipient       = recipients.getPrimaryRecipient();
        byte[] ciphertextPduBytes = getEncryptedData();

        if (ciphertextPduBytes == null) {
          Log.w("DecryptingQueue", "No encoded PNG data found on parts.");
          database.markAsDecryptFailed(messageId, threadId);
          return;
        }

        if (!KeyUtil.isSessionFor(context, recipient)) {
          Log.w("DecryptingQueue", "No such recipient session for MMS...");
          database.markAsNoSession(messageId, threadId);
          return;
        }
								
        byte[] plaintextPduBytes;
				
        synchronized (SessionCipher.CIPHER_LOCK) {
          Log.w("DecryptingQueue", "Decrypting: " + Hex.toString(ciphertextPduBytes));
          SessionCipher cipher = new SessionCipher(context, masterSecret, recipient, new TextTransport());        		
          plaintextPduBytes    = cipher.decryptMessage(ciphertextPduBytes);
        }
				
        MultimediaMessagePdu plaintextPdu = (MultimediaMessagePdu)new PduParser(plaintextPduBytes).parse();
        Log.w("DecryptingQueue", "Successfully decrypted MMS!");
        database.insertSecureDecryptedMessageReceived(plaintextPdu, threadId);
        database.delete(messageId);	
				
      } catch (RecipientFormattingException rfe) {
        Log.w("DecryptingQueue", rfe);
        database.markAsDecryptFailed(messageId, threadId);
      } catch (InvalidMessageException ime) {
        Log.w("DecryptingQueue", ime);
        database.markAsDecryptFailed(messageId, threadId);				
      } catch (MmsException mme) {
        Log.w("DecryptingQueue", mme);
        database.markAsDecryptFailed(messageId, threadId);								
      }
    }
  }
	
	
  private static class DecryptionWorkItem implements Runnable {
		
    private long messageId;
    private Context context;
    private MasterSecret masterSecret;
    private String body;
    private String originator;
		
    public DecryptionWorkItem(Context context, MasterSecret masterSecret, long messageId, String body, String originator) {
      this.context      = context;
      this.messageId    = messageId;
      this.masterSecret = masterSecret;
      this.body         = body;
      this.originator   = originator;
    }
				
    private void handleRemoteAsymmetricEncrypt() {
      EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
      String plaintextBody;

      synchronized (SessionCipher.CIPHER_LOCK) {
        try {
          Log.w("DecryptingQueue", "Parsing recipient for originator: " + originator);
          Recipients recipients = RecipientFactory.getRecipientsFromString(context, originator);
          Recipient recipient   = recipients.getPrimaryRecipient();
          Log.w("DecryptingQueue", "Parsed Recipient: " + recipient.getNumber());
        			
          if (!KeyUtil.isSessionFor(context, recipient)) {
            Log.w("DecryptingQueue", "No such recipient session...");
            database.markAsNoSession(messageId);
            return;
          }
    				
          SessionCipher cipher  = new SessionCipher(context, masterSecret, recipient, new SmsTransportDetails());        		
          plaintextBody         = new String(cipher.decryptMessage(body.getBytes()));
        } catch (InvalidMessageException e) {
          Log.w("DecryptionQueue", e);
          database.markAsDecryptFailed(messageId);
          return;
        } catch (RecipientFormattingException e) {
          Log.w("DecryptionQueue", e);
          database.markAsDecryptFailed(messageId);
          return;
        }
      }

      database.updateSecureMessageBody(masterSecret, messageId, plaintextBody);
    }
		
    private void handleLocalAsymmetricEncrypt() {
      EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
      String plaintextBody;
        	
      try {
        AsymmetricMasterCipher asymmetricMasterCipher = new AsymmetricMasterCipher(MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret));
        String encryptedBody                          = body.substring(Prefix.ASYMMETRIC_LOCAL_ENCRYPT.length());
        plaintextBody                                 = asymmetricMasterCipher.decryptBody(encryptedBody);
      } catch (InvalidMessageException ime) {
        Log.w("DecryptionQueue", ime);
        database.markAsDecryptFailed(messageId);
        return;
      } catch (IOException e) {
        Log.w("DecryptionQueue", e);
        database.markAsDecryptFailed(messageId);
        return;
      }
			
      database.updateMessageBody(masterSecret, messageId, plaintextBody);
    }
		
    public void run() {       	
      if      (body.startsWith(Prefix.ASYMMETRIC_ENCRYPT))       handleRemoteAsymmetricEncrypt();
      else if (body.startsWith(Prefix.ASYMMETRIC_LOCAL_ENCRYPT)) handleLocalAsymmetricEncrypt();
    }
  }
	
	
}

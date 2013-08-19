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

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.protocol.KeyExchangeMessage;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.TextTransport;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.SessionCipher;
import org.whispersystems.textsecure.util.Hex;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.WorkerThread;
import org.whispersystems.textsecure.crypto.KeyUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.MultimediaMessagePdu;
import ws.com.google.android.mms.pdu.PduParser;
import ws.com.google.android.mms.pdu.RetrieveConf;

/**
 * A work queue for processing a number of encryption operations.
 *
 * @author Moxie Marlinspike
 */

public class DecryptingQueue {

  private static final List<Runnable> workQueue = new LinkedList<Runnable>();

  static {
    Thread workerThread = new WorkerThread(workQueue, "Async Decryption Thread");
    workerThread.start();
  }

  public static void scheduleDecryption(Context context, MasterSecret masterSecret,
                                        long messageId, long threadId, MultimediaMessagePdu mms)
  {
    MmsDecryptionItem runnable = new MmsDecryptionItem(context, masterSecret, messageId, threadId, mms);
    synchronized (workQueue) {
      workQueue.add(runnable);
      workQueue.notifyAll();
    }
  }

  public static void scheduleDecryption(Context context, MasterSecret masterSecret,
                                        long messageId, long threadId, String originator,
                                        String body, boolean isSecureMessage, boolean isKeyExchange)
  {
    DecryptionWorkItem runnable = new DecryptionWorkItem(context, masterSecret, messageId, threadId,
                                                         originator, body, isSecureMessage, isKeyExchange);
    synchronized (workQueue) {
      workQueue.add(runnable);
      workQueue.notifyAll();
    }
  }

  public static void schedulePendingDecrypts(Context context, MasterSecret masterSecret) {
    Log.w("DecryptingQueue", "Processing pending decrypts...");

    EncryptingSmsDatabase.Reader reader = null;
    SmsMessageRecord record;

    try {
      reader = DatabaseFactory.getEncryptingSmsDatabase(context).getDecryptInProgressMessages(masterSecret);

      while ((record = reader.getNext()) != null) {
        scheduleDecryptFromCursor(context, masterSecret, record);
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  public static void scheduleRogueMessages(Context context, MasterSecret masterSecret, Recipient recipient) {
    SmsDatabase.Reader reader = null;
    SmsMessageRecord record;

    try {
      Cursor cursor = DatabaseFactory.getSmsDatabase(context).getEncryptedRogueMessages(recipient);
      reader        = DatabaseFactory.getEncryptingSmsDatabase(context).readerFor(masterSecret, cursor);

      while ((record = reader.getNext()) != null) {
        DatabaseFactory.getSmsDatabase(context).markAsDecrypting(record.getId());
        scheduleDecryptFromCursor(context, masterSecret, record);
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private static void scheduleDecryptFromCursor(Context context, MasterSecret masterSecret,
                                                SmsMessageRecord record)
  {
    long messageId          = record.getId();
    long threadId           = record.getThreadId();
    String body             = record.getBody().getBody();
    String originator       = record.getIndividualRecipient().getNumber();
    boolean isSecureMessage = record.isSecure();
    boolean isKeyExchange   = record.isKeyExchange();

    scheduleDecryption(context, masterSecret, messageId,  threadId,
                       originator, body, isSecureMessage, isKeyExchange);
  }

  private static class MmsDecryptionItem implements Runnable {
    private long messageId;
    private long threadId;
    private Context context;
    private MasterSecret masterSecret;
    private MultimediaMessagePdu pdu;

    public MmsDecryptionItem(Context context, MasterSecret masterSecret,
                             long messageId, long threadId, MultimediaMessagePdu pdu)
    {
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

    @Override
    public void run() {
      MmsDatabase database = DatabaseFactory.getMmsDatabase(context);

      try {
        String     messageFrom        = pdu.getFrom().getString();
        Recipients recipients         = RecipientFactory.getRecipientsFromString(context, messageFrom, false);
        Recipient  recipient          = recipients.getPrimaryRecipient();
        byte[]     ciphertextPduBytes = getEncryptedData();

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
          try {
            plaintextPduBytes = cipher.decryptMessage(ciphertextPduBytes);
          } catch (InvalidMessageException ime) {
            // XXX - For some reason, Sprint seems to append a single character to the
            // end of message text segments.  I don't know why, so here we just try
            // truncating the message by one if the MAC fails.
            if (ciphertextPduBytes.length > 2) {
              Log.w("DecryptingQueue", "Attempting truncated decrypt...");
              byte[] truncated = new byte[ciphertextPduBytes.length - 1];
              System.arraycopy(ciphertextPduBytes, 0, truncated, 0, truncated.length);
              plaintextPduBytes = cipher.decryptMessage(truncated);
            } else {
              throw ime;
            }
          }
        }

        MultimediaMessagePdu plaintextGenericPdu = (MultimediaMessagePdu)new PduParser(plaintextPduBytes).parse();
        RetrieveConf plaintextPdu                = new RetrieveConf(plaintextGenericPdu.getPduHeaders(),
                                                                    plaintextGenericPdu.getBody());
        Log.w("DecryptingQueue", "Successfully decrypted MMS!");
        database.insertSecureDecryptedMessageInbox(masterSecret, new IncomingMediaMessage(plaintextPdu), threadId);
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

    private final long         messageId;
    private final long         threadId;
    private final Context      context;
    private final MasterSecret masterSecret;
    private final String       body;
    private final String       originator;
    private final boolean      isSecureMessage;
    private final boolean      isKeyExchange;

    public DecryptionWorkItem(Context context, MasterSecret masterSecret, long messageId, long threadId,
                              String originator, String body, boolean isSecureMessage, boolean isKeyExchange)
    {
      this.context         = context;
      this.messageId       = messageId;
      this.threadId        = threadId;
      this.masterSecret    = masterSecret;
      this.body            = body;
      this.originator      = originator;
      this.isSecureMessage = isSecureMessage;
      this.isKeyExchange   = isKeyExchange;
    }

    private void handleRemoteAsymmetricEncrypt() {
      EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
      String plaintextBody;

      synchronized (SessionCipher.CIPHER_LOCK) {
        try {
          Log.w("DecryptingQueue", "Parsing recipient for originator: " + originator);
          Recipients recipients = RecipientFactory.getRecipientsFromString(context, originator, false);
          Recipient  recipient  = recipients.getPrimaryRecipient();
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

      database.updateMessageBody(masterSecret, messageId, plaintextBody);
      MessageNotifier.updateNotification(context, masterSecret);
    }

    private void handleLocalAsymmetricEncrypt() {
      EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
      String plaintextBody;

      try {
        AsymmetricMasterCipher asymmetricMasterCipher = new AsymmetricMasterCipher(MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret));
        plaintextBody                                 = asymmetricMasterCipher.decryptBody(body);

        if (isKeyExchange) {
          handleKeyExchangeProcessing(plaintextBody);
        }

        database.updateMessageBody(masterSecret, messageId, plaintextBody);
        MessageNotifier.updateNotification(context, masterSecret);
      } catch (InvalidMessageException ime) {
        Log.w("DecryptionQueue", ime);
        database.markAsDecryptFailed(messageId);
      } catch (IOException e) {
        Log.w("DecryptionQueue", e);
        database.markAsDecryptFailed(messageId);
      }
    }

    private void handleKeyExchangeProcessing(String plaintxtBody) {
      if (TextSecurePreferences.isAutoRespondKeyExchangeEnabled(context)) {
        try {
          Recipient recipient                   = new Recipient(null, originator, null, null);
          KeyExchangeMessage keyExchangeMessage = new KeyExchangeMessage(plaintxtBody);
          KeyExchangeProcessor processor        = new KeyExchangeProcessor(context, masterSecret, recipient);

          Log.w("DecryptingQuue", "KeyExchange with fingerprint: " + keyExchangeMessage.getPublicKey().getFingerprint());

          if (processor.isStale(keyExchangeMessage)) {
            DatabaseFactory.getEncryptingSmsDatabase(context).markAsStaleKeyExchange(messageId);
          } else if (processor.isTrusted(keyExchangeMessage)) {
            DatabaseFactory.getEncryptingSmsDatabase(context).markAsProcessedKeyExchange(messageId);
            processor.processKeyExchangeMessage(keyExchangeMessage, threadId);
          }
        } catch (InvalidVersionException e) {
          Log.w("DecryptingQueue", e);
        } catch (InvalidKeyException e) {
          Log.w("DecryptingQueue", e);
        }
      }
    }

    @Override
    public void run() {
      if (isSecureMessage) {
        handleRemoteAsymmetricEncrypt();
      } else {
        handleLocalAsymmetricEncrypt();
      }
    }
  }
}

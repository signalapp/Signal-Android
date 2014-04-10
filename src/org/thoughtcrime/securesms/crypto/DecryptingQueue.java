/**
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
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
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.protocol.KeyExchangeMessage;
import org.whispersystems.textsecure.crypto.LegacyMessageException;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.TextTransport;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.PushReceiver;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.DuplicateMessageException;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.InvalidVersionException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.SessionCipher;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.storage.Session;
import org.whispersystems.textsecure.storage.SessionRecordV2;
import org.whispersystems.textsecure.util.Hex;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

  private static final Executor executor = Executors.newSingleThreadExecutor();

  public static void scheduleDecryption(Context context, MasterSecret masterSecret,
                                        long messageId, long threadId, MultimediaMessagePdu mms)
  {
    MmsDecryptionItem runnable = new MmsDecryptionItem(context, masterSecret, messageId, threadId, mms);
    executor.execute(runnable);
  }

  public static void scheduleDecryption(Context context, MasterSecret masterSecret,
                                        long messageId, long threadId, String originator, int deviceId,
                                        String body, boolean isSecureMessage, boolean isKeyExchange,
                                        boolean isEndSession)
  {
    DecryptionWorkItem runnable = new DecryptionWorkItem(context, masterSecret, messageId, threadId,
                                                         originator, deviceId, body,
                                                         isSecureMessage, isKeyExchange, isEndSession);
    executor.execute(runnable);
  }

  public static void scheduleDecryption(Context context, MasterSecret masterSecret,
                                        long messageId, IncomingPushMessage message)
  {
    PushDecryptionWorkItem runnable = new PushDecryptionWorkItem(context, masterSecret,
                                                                 messageId, message);
    executor.execute(runnable);
  }

  public static void schedulePendingDecrypts(Context context, MasterSecret masterSecret) {
    Log.w("DecryptingQueue", "Processing pending decrypts...");

    EncryptingSmsDatabase smsDatabase  = DatabaseFactory.getEncryptingSmsDatabase(context);
    PushDatabase          pushDatabase = DatabaseFactory.getPushDatabase(context);

    EncryptingSmsDatabase.Reader smsReader  = null;
    PushDatabase.Reader          pushReader = null;

    SmsMessageRecord record;
    IncomingPushMessage message;

    try {
      smsReader  = smsDatabase.getDecryptInProgressMessages(masterSecret);
      pushReader = pushDatabase.readerFor(pushDatabase.getPending());

      while ((record = smsReader.getNext()) != null) {
        scheduleDecryptFromCursor(context, masterSecret, record);
      }

      while ((message = pushReader.getNext()) != null) {
        if (message.isPreKeyBundle()) {
          Intent intent = new Intent(context, SendReceiveService.class);
          intent.setAction(SendReceiveService.RECEIVE_PUSH_ACTION);
          intent.putExtra("message", message);
          context.startService(intent);

          pushDatabase.delete(pushReader.getCurrentId());
        } else {
          scheduleDecryption(context, masterSecret, pushReader.getCurrentId(), message);
        }
      }

    } finally {
      if (smsReader != null)
        smsReader.close();

      if (pushReader != null)
        pushReader.close();
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
    int originatorDeviceId  = record.getRecipientDeviceId();
    boolean isSecureMessage = record.isSecure();
    boolean isKeyExchange   = record.isKeyExchange();
    boolean isEndSession    = record.isEndSession();

    scheduleDecryption(context, masterSecret, messageId,  threadId,
                       originator, originatorDeviceId, body,
                       isSecureMessage, isKeyExchange, isEndSession);
  }

  private static class PushDecryptionWorkItem implements Runnable {

    private Context             context;
    private MasterSecret        masterSecret;
    private long                messageId;
    private IncomingPushMessage message;

    public PushDecryptionWorkItem(Context context, MasterSecret masterSecret,
                                  long messageId, IncomingPushMessage message)
    {
      this.context      = context;
      this.masterSecret = masterSecret;
      this.messageId    = messageId;
      this.message      = message;
    }

    public void run() {
      try {
        Recipients      recipients      = RecipientFactory.getRecipientsFromString(context, message.getSource(), false);
        Recipient       recipient       = recipients.getPrimaryRecipient();
        RecipientDevice recipientDevice = new RecipientDevice(recipient.getRecipientId(), message.getSourceDevice());

        if (!SessionRecordV2.hasSession(context, masterSecret, recipientDevice)) {
          sendResult(PushReceiver.RESULT_NO_SESSION);
          return;
        }

        SessionCipher sessionCipher = SessionCipher.createFor(context, masterSecret, recipientDevice);
        byte[]        plaintextBody = sessionCipher.decrypt(message.getBody());

        message = message.withBody(plaintextBody);
        sendResult(PushReceiver.RESULT_OK);
      } catch (InvalidMessageException e) {
        Log.w("DecryptionQueue", e);
        sendResult(PushReceiver.RESULT_DECRYPT_FAILED);
      } catch (RecipientFormattingException e) {
        Log.w("DecryptionQueue", e);
        sendResult(PushReceiver.RESULT_DECRYPT_FAILED);
      } catch (DuplicateMessageException e) {
        Log.w("DecryptingQueue", e);
        sendResult(PushReceiver.RESULT_DECRYPT_DUPLICATE);
      } catch (LegacyMessageException e) {
        Log.w("DecryptionQueue", e);
        sendResult(PushReceiver.RESULT_DECRYPT_FAILED);
      }
    }

    private void sendResult(int result) {
      Intent intent = new Intent(context, SendReceiveService.class);
      intent.setAction(SendReceiveService.DECRYPTED_PUSH_ACTION);
      intent.putExtra("message", message);
      intent.putExtra("message_id", messageId);
      intent.putExtra("result", result);
      context.startService(intent);
    }
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
        String          messageFrom        = pdu.getFrom().getString();
        Recipients      recipients         = RecipientFactory.getRecipientsFromString(context, messageFrom, false);
        Recipient       recipient          = recipients.getPrimaryRecipient();
        RecipientDevice recipientDevice    = new RecipientDevice(recipient.getRecipientId(), RecipientDevice.DEFAULT_DEVICE_ID);
        byte[]          ciphertextPduBytes = getEncryptedData();

        if (ciphertextPduBytes == null) {
          Log.w("DecryptingQueue", "No encoded PNG data found on parts.");
          database.markAsDecryptFailed(messageId, threadId);
          return;
        }

        if (!Session.hasSession(context, masterSecret, recipient)) {
          Log.w("DecryptingQueue", "No such recipient session for MMS...");
          database.markAsNoSession(messageId, threadId);
          return;
        }

        byte[] plaintextPduBytes;

        Log.w("DecryptingQueue", "Decrypting: " + Hex.toString(ciphertextPduBytes));
        TextTransport transportDetails  = new TextTransport();
        SessionCipher sessionCipher     = SessionCipher.createFor(context, masterSecret, recipientDevice);
        byte[]        decodedCiphertext = transportDetails.getDecodedMessage(ciphertextPduBytes);

        try {
          plaintextPduBytes = sessionCipher.decrypt(decodedCiphertext);
        } catch (InvalidMessageException ime) {
          // XXX - For some reason, Sprint seems to append a single character to the
          // end of message text segments.  I don't know why, so here we just try
          // truncating the message by one if the MAC fails.
          if (ciphertextPduBytes.length > 2) {
            Log.w("DecryptingQueue", "Attempting truncated decrypt...");
            byte[] truncated = Util.trim(ciphertextPduBytes, ciphertextPduBytes.length - 1);
            decodedCiphertext = transportDetails.getDecodedMessage(truncated);
            plaintextPduBytes = sessionCipher.decrypt(decodedCiphertext);
          } else {
            throw ime;
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
      } catch (DuplicateMessageException dme) {
        Log.w("DecryptingQueue", dme);
        database.markAsDecryptDuplicate(messageId, threadId);
      } catch (LegacyMessageException lme) {
        Log.w("DecryptingQueue", lme);
        database.markAsLegacyVersion(messageId, threadId);
      } catch (MmsException mme) {
        Log.w("DecryptingQueue", mme);
        database.markAsDecryptFailed(messageId, threadId);
      } catch (IOException e) {
        Log.w("DecryptingQueue", e);
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
    private final int          deviceId;
    private final boolean      isSecureMessage;
    private final boolean      isKeyExchange;
    private final boolean      isEndSession;

    public DecryptionWorkItem(Context context, MasterSecret masterSecret, long messageId, long threadId,
                              String originator, int deviceId, String body, boolean isSecureMessage,
                              boolean isKeyExchange, boolean isEndSession)
    {
      this.context         = context;
      this.messageId       = messageId;
      this.threadId        = threadId;
      this.masterSecret    = masterSecret;
      this.body            = body;
      this.originator      = originator;
      this.deviceId        = deviceId;
      this.isSecureMessage = isSecureMessage;
      this.isKeyExchange   = isKeyExchange;
      this.isEndSession    = isEndSession;
    }

    private void handleRemoteAsymmetricEncrypt() {
      EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
      String plaintextBody;

      try {
        Recipients      recipients      = RecipientFactory.getRecipientsFromString(context, originator, false);
        Recipient       recipient       = recipients.getPrimaryRecipient();
        RecipientDevice recipientDevice = new RecipientDevice(recipient.getRecipientId(), deviceId);

        if (!Session.hasSession(context, masterSecret, recipient)) {
          database.markAsNoSession(messageId);
          return;
        }

        SmsTransportDetails transportDetails  = new SmsTransportDetails();
        SessionCipher       sessionCipher     = SessionCipher.createFor(context, masterSecret, recipientDevice);
        byte[]              decodedCiphertext = transportDetails.getDecodedMessage(body.getBytes());
        byte[]              paddedPlaintext   = sessionCipher.decrypt(decodedCiphertext);

        plaintextBody = new String(transportDetails.getStrippedPaddingMessageBody(paddedPlaintext));

        if (isEndSession &&
            "TERMINATE".equals(plaintextBody) &&
            SessionRecordV2.hasSession(context, masterSecret, recipientDevice))
        {
          Session.abortSessionFor(context, recipient);
        }
      } catch (InvalidMessageException e) {
        Log.w("DecryptionQueue", e);
        database.markAsDecryptFailed(messageId);
        return;
      } catch (LegacyMessageException lme) {
        Log.w("DecryptionQueue", lme);
        database.markAsLegacyVersion(messageId);
        return;
      } catch (RecipientFormattingException e) {
        Log.w("DecryptionQueue", e);
        database.markAsDecryptFailed(messageId);
        return;
      } catch (IOException e) {
        Log.w("DecryptionQueue", e);
        database.markAsDecryptFailed(messageId);
        return;
      } catch (DuplicateMessageException e) {
        Log.w("DecryptionQueue", e);
        database.markAsDecryptDuplicate(messageId);
        return;
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

    private void handleKeyExchangeProcessing(String plaintextBody) {
      if (TextSecurePreferences.isAutoRespondKeyExchangeEnabled(context)) {
        try {
          Recipient            recipient       = RecipientFactory.getRecipientsFromString(context, originator, false).getPrimaryRecipient();
          RecipientDevice      recipientDevice = new RecipientDevice(recipient.getRecipientId(), deviceId);
          KeyExchangeMessage   message         = KeyExchangeMessage.createFor(plaintextBody);
          KeyExchangeProcessor processor       = KeyExchangeProcessor.createFor(context, masterSecret, recipientDevice, message);

          if (processor.isStale(message)) {
            DatabaseFactory.getEncryptingSmsDatabase(context).markAsStaleKeyExchange(messageId);
          } else if (processor.isTrusted(message)) {
            DatabaseFactory.getEncryptingSmsDatabase(context).markAsProcessedKeyExchange(messageId);
            processor.processKeyExchangeMessage(message, threadId);
          }
        } catch (InvalidVersionException e) {
          Log.w("DecryptingQueue", e);
          DatabaseFactory.getEncryptingSmsDatabase(context).markAsInvalidVersionKeyExchange(messageId);
        } catch (InvalidKeyException e) {
          Log.w("DecryptingQueue", e);
          DatabaseFactory.getEncryptingSmsDatabase(context).markAsCorruptKeyExchange(messageId);
        } catch (InvalidMessageException e) {
          Log.w("DecryptingQueue", e);
          DatabaseFactory.getEncryptingSmsDatabase(context).markAsCorruptKeyExchange(messageId);
        } catch (RecipientFormattingException e) {
          Log.w("DecryptingQueue", e);
          DatabaseFactory.getEncryptingSmsDatabase(context).markAsCorruptKeyExchange(messageId);
        } catch (LegacyMessageException e) {
          Log.w("DecryptingQueue", e);
          DatabaseFactory.getEncryptingSmsDatabase(context).markAsLegacyVersion(messageId);
        }
      }
    }

    @Override
    public void run() {
      if (isSecureMessage || isEndSession) {
        handleRemoteAsymmetricEncrypt();
      } else {
        handleLocalAsymmetricEncrypt();
      }
    }
  }
}

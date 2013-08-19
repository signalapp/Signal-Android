package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.SmsTransportDetails;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.SessionCipher;
import org.whispersystems.textsecure.push.PreKeyEntity;
import org.whispersystems.textsecure.push.PushAttachmentData;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.RateLimitException;
import org.whispersystems.textsecure.storage.SessionRecord;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.SendReq;

public class PushTransport extends BaseTransport {

  private final Context      context;
  private final MasterSecret masterSecret;

  public PushTransport(Context context, MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  public void deliver(SmsMessageRecord message) throws IOException {
    try {
      String            localNumber = TextSecurePreferences.getLocalNumber(context);
      String            password    = TextSecurePreferences.getPushServerPassword(context);
      PushServiceSocket socket      = new PushServiceSocket(context, localNumber, password);

      Recipient recipient             = message.getIndividualRecipient();
      String plaintext                = message.getBody().getBody();
      String recipientCanonicalNumber = PhoneNumberFormatter.formatNumber(recipient.getNumber(),
                                                                          localNumber);

//      if (SessionRecord.hasSession(context, recipient)) {
//        byte[] cipherText = getEncryptedMessageForExistingSession(recipient, plaintext);
//        socket.sendMessage(recipientCanonicalNumber, new String(cipherText));
//      } else {
//        byte[] cipherText = getEncryptedMessageForNewSession(socket, recipient,
//                                                             recipientCanonicalNumber,
//                                                             plaintext);
//        socket.sendMessage(recipientCanonicalNumber, new String(cipherText));
//      }


      socket.sendMessage(recipientCanonicalNumber, message.getBody().getBody());

      context.sendBroadcast(constructSentIntent(context, message.getId(), message.getType()));
    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    }
  }

  public void deliver(SendReq message, List<String> destinations) throws IOException {
    try {
      String                   localNumber = TextSecurePreferences.getLocalNumber(context);
      String                   password    = TextSecurePreferences.getPushServerPassword(context);
      PushServiceSocket        socket      = new PushServiceSocket(context, localNumber, password);
      String                   messageText = PartParser.getMessageText(message.getBody());
      List<PushAttachmentData> attachments = getAttachmentsFromBody(message.getBody());

      if (attachments.isEmpty()) socket.sendMessage(destinations, messageText);
      else                       socket.sendMessage(destinations, messageText, attachments);
    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    }
  }

  private List<PushAttachmentData> getAttachmentsFromBody(PduBody body) {
    List<PushAttachmentData> attachments = new LinkedList<PushAttachmentData>();

    for (int i=0;i<body.getPartsNum();i++) {
      String contentType = Util.toIsoString(body.getPart(i).getContentType());

      if (ContentType.isImageType(contentType) ||
          ContentType.isAudioType(contentType) ||
          ContentType.isVideoType(contentType))
      {
        attachments.add(new PushAttachmentData(contentType, body.getPart(i).getData()));
      }
    }

    return attachments;
  }

  private byte[] getEncryptedMessageForNewSession(PushServiceSocket socket, Recipient recipient, String canonicalRecipientNumber, String plaintext) throws IOException {
    PreKeyEntity         preKey    = socket.getPreKey(canonicalRecipientNumber);
    KeyExchangeProcessor processor = new KeyExchangeProcessor(context, masterSecret, recipient);
    processor.processKeyExchangeMessage(preKey);

    synchronized (SessionCipher.CIPHER_LOCK) {
      SessionCipher sessionCipher = new SessionCipher(context, masterSecret, recipient, new SmsTransportDetails());
      return sessionCipher.encryptMessage(plaintext.getBytes());
    }
  }

  private byte[] getEncryptedMessageForExistingSession(Recipient recipient, String plaintext) {
    synchronized (SessionCipher.CIPHER_LOCK) {
      SessionCipher sessionCipher = new SessionCipher(context, masterSecret, recipient, new SmsTransportDetails());
      return sessionCipher.encryptMessage(plaintext.getBytes());
    }
  }

}

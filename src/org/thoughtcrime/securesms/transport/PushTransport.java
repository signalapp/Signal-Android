package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.textsecure.push.RawTransportDetails;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.KeyUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.MessageCipher;
import org.whispersystems.textsecure.crypto.protocol.PreKeyBundleMessage;
import org.whispersystems.textsecure.push.OutgoingPushMessage;
import org.whispersystems.textsecure.push.PreKeyEntity;
import org.whispersystems.textsecure.push.PushAttachmentData;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.PushTransportDetails;
import org.whispersystems.textsecure.push.RateLimitException;
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

      Recipient recipient                = message.getIndividualRecipient();
      String    plaintext                = message.getBody().getBody();
      String    recipientCanonicalNumber = PhoneNumberFormatter.formatNumber(recipient.getNumber(),
                                                                             localNumber);

      Pair<Integer, byte[]> typeAndCiphertext = getEncryptedMessage(socket, recipient, recipientCanonicalNumber, plaintext);

      socket.sendMessage(recipientCanonicalNumber, typeAndCiphertext.second, typeAndCiphertext.first);

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
      byte[]                   messageText = PartParser.getMessageText(message.getBody()).getBytes();
      List<PushAttachmentData> attachments = getAttachmentsFromBody(message.getBody());

      List<byte[]>                   messagesList    = new LinkedList<byte[]>();
      List<List<PushAttachmentData>> attachmentsList = new LinkedList<List<PushAttachmentData>>();

      for (String recipient : destinations) {
        messagesList.add(messageText);
        attachmentsList.add(attachments);
      }

      socket.sendMessage(destinations, messagesList, attachmentsList,
                         OutgoingPushMessage.TYPE_MESSAGE_PLAINTEXT);
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

  private Pair<Integer, byte[]> getEncryptedMessage(PushServiceSocket socket, Recipient recipient,
                                                    String canonicalRecipientNumber, String plaintext)
      throws IOException
  {
    if (KeyUtil.isNonPrekeySessionFor(context, masterSecret, recipient)) {
      Log.w("PushTransport", "Sending standard ciphertext message...");
      byte[] ciphertext = getEncryptedMessageForExistingSession(recipient, plaintext);
      return new Pair<Integer, byte[]>(OutgoingPushMessage.TYPE_MESSAGE_CIPHERTEXT, ciphertext);
    } else if (KeyUtil.isSessionFor(context, recipient)) {
      Log.w("PushTransport", "Sending prekeybundle ciphertext message for existing session...");
      byte[] ciphertext = getEncryptedPrekeyBundleMessageForExistingSession(recipient, plaintext);
      return new Pair<Integer, byte[]>(OutgoingPushMessage.TYPE_MESSAGE_PREKEY_BUNDLE, ciphertext);
    } else {
      Log.w("PushTransport", "Sending prekeybundle ciphertext message for new session...");
      byte[] ciphertext = getEncryptedPrekeyBundleMessageForNewSession(socket, recipient, canonicalRecipientNumber, plaintext);
      return new Pair<Integer, byte[]>(OutgoingPushMessage.TYPE_MESSAGE_PREKEY_BUNDLE, ciphertext);
    }
  }

  private byte[] getEncryptedPrekeyBundleMessageForExistingSession(Recipient recipient,
                                                                   String plaintext)
  {
    IdentityKeyPair identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    IdentityKey     identityKey     = identityKeyPair.getPublicKey();

    MessageCipher message = new MessageCipher(context, masterSecret, identityKeyPair, new RawTransportDetails());
    byte[] bundledMessage = message.encrypt(recipient, plaintext.getBytes());

    PreKeyBundleMessage preKeyBundleMessage = new PreKeyBundleMessage(identityKey, bundledMessage);
    return preKeyBundleMessage.serialize();
  }

  private byte[] getEncryptedPrekeyBundleMessageForNewSession(PushServiceSocket socket,
                                                              Recipient recipient,
                                                              String canonicalRecipientNumber,
                                                              String plaintext)
      throws IOException
  {
    IdentityKeyPair      identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    IdentityKey          identityKey     = identityKeyPair.getPublicKey();
    PreKeyEntity         preKey          = socket.getPreKey(canonicalRecipientNumber);
    KeyExchangeProcessor processor       = new KeyExchangeProcessor(context, masterSecret, recipient);

    processor.processKeyExchangeMessage(preKey);

    MessageCipher message = new MessageCipher(context, masterSecret, identityKeyPair, new RawTransportDetails());
    byte[] bundledMessage = message.encrypt(recipient, plaintext.getBytes());

    PreKeyBundleMessage preKeyBundleMessage = new PreKeyBundleMessage(identityKey, bundledMessage);
    return preKeyBundleMessage.serialize();
  }

  private byte[] getEncryptedMessageForExistingSession(Recipient recipient, String plaintext)
      throws IOException
  {
    IdentityKeyPair identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    MessageCipher   messageCipher   = new MessageCipher(context, masterSecret, identityKeyPair,
                                                        new PushTransportDetails());

    return messageCipher.encrypt(recipient, plaintext.getBytes());
  }

}

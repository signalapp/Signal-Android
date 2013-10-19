package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.google.protobuf.ByteString;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.AttachmentCipher;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.KeyUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.MessageCipher;
import org.whispersystems.textsecure.crypto.protocol.PreKeyBundleMessage;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.push.OutgoingPushMessage;
import org.whispersystems.textsecure.push.PreKeyEntity;
import org.whispersystems.textsecure.push.PushAttachmentData;
import org.whispersystems.textsecure.push.PushAttachmentPointer;
import org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.PushTransportDetails;
import org.whispersystems.textsecure.push.RateLimitException;
import org.whispersystems.textsecure.push.RawTransportDetails;
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

      Recipient                  recipient                = message.getIndividualRecipient();
      String                     plaintextBody            = message.getBody().getBody();
      PushMessageContent.Builder builder                  = PushMessageContent.newBuilder();
      byte[]                     plaintext                = builder.setBody(plaintextBody).build().toByteArray();
      String                     recipientCanonicalNumber = PhoneNumberFormatter.formatNumber(recipient.getNumber(), localNumber);
      String                     relay                    = Directory.getInstance(context).getRelay(recipientCanonicalNumber);

      Pair<Integer, byte[]> typeAndCiphertext = getEncryptedMessage(socket, recipient, recipientCanonicalNumber, plaintext);

      socket.sendMessage(relay, recipientCanonicalNumber, typeAndCiphertext.second, typeAndCiphertext.first);

      context.sendBroadcast(constructSentIntent(context, message.getId(), message.getType()));
    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    }
  }

  public void deliver(SendReq message, List<String> destinations) throws IOException {
    try {
      String            localNumber = TextSecurePreferences.getLocalNumber(context);
      String            password    = TextSecurePreferences.getPushServerPassword(context);
      PushServiceSocket socket      = new PushServiceSocket(context, localNumber, password);
      String            messageBody = PartParser.getMessageText(message.getBody());
      List<String>      relays      = new LinkedList<String>();
      List<byte[]>      ciphertext  = new LinkedList<byte[]>();
      List<Integer>     types       = new LinkedList<Integer>();

      for (String destination : destinations) {
        Recipients                  recipients  = RecipientFactory.getRecipientsFromString(context, destination, false);
        List<PushAttachmentPointer> attachments = getPushAttachmentPointers(socket, message.getBody());
        PushMessageContent.Builder  builder     = PushMessageContent.newBuilder();

        if (messageBody != null) {
          builder.setBody(messageBody);
        }

        for (PushAttachmentPointer attachment : attachments) {
          PushMessageContent.AttachmentPointer.Builder attachmentBuilder =
              PushMessageContent.AttachmentPointer.newBuilder();

          attachmentBuilder.setId(attachment.getId());
          attachmentBuilder.setContentType(attachment.getContentType());
          attachmentBuilder.setKey(ByteString.copyFrom(attachment.getKey()));

          builder.addAttachments(attachmentBuilder.build());
        }

        byte[]                plaintext         = builder.build().toByteArray();
        Pair<Integer, byte[]> typeAndCiphertext = getEncryptedMessage(socket, recipients.getPrimaryRecipient(),
                                                                      destination, plaintext);

        relays.add(Directory.getInstance(context).getRelay(destination));
        types.add(typeAndCiphertext.first);
        ciphertext.add(typeAndCiphertext.second);
      }

      socket.sendMessage(relays, destinations, ciphertext, types);

    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    } catch (RecipientFormattingException e) {
      Log.w("PushTransport", e);
      throw new IOException("Bad destination!");
    }
  }

  private List<PushAttachmentPointer> getPushAttachmentPointers(PushServiceSocket socket, PduBody body)
      throws IOException
  {
    List<PushAttachmentPointer> attachments = new LinkedList<PushAttachmentPointer>();

    for (int i=0;i<body.getPartsNum();i++) {
      String contentType = Util.toIsoString(body.getPart(i).getContentType());
      if (ContentType.isImageType(contentType) ||
          ContentType.isAudioType(contentType) ||
          ContentType.isVideoType(contentType))
      {
        AttachmentCipher   cipher               = new AttachmentCipher();
        byte[]             key                  = cipher.getCombinedKeyMaterial();
        byte[]             ciphertextAttachment = cipher.encrypt(body.getPart(i).getData());
        PushAttachmentData attachmentData       = new PushAttachmentData(contentType, ciphertextAttachment);
        long               attachmentId         = socket.sendAttachment(attachmentData);

        attachments.add(new PushAttachmentPointer(contentType, attachmentId, key));
      }
    }

    return attachments;
  }

  private Pair<Integer, byte[]> getEncryptedMessage(PushServiceSocket socket, Recipient recipient,
                                                    String canonicalRecipientNumber, byte[] plaintext)
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
                                                                   byte[] plaintext)
  {
    IdentityKeyPair identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    IdentityKey     identityKey     = identityKeyPair.getPublicKey();

    MessageCipher message = new MessageCipher(context, masterSecret, identityKeyPair, new RawTransportDetails());
    byte[] bundledMessage = message.encrypt(recipient, plaintext);

    PreKeyBundleMessage preKeyBundleMessage = new PreKeyBundleMessage(identityKey, bundledMessage);
    return preKeyBundleMessage.serialize();
  }

  private byte[] getEncryptedPrekeyBundleMessageForNewSession(PushServiceSocket socket,
                                                              Recipient recipient,
                                                              String canonicalRecipientNumber,
                                                              byte[] plaintext)
      throws IOException
  {
    IdentityKeyPair      identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    IdentityKey          identityKey     = identityKeyPair.getPublicKey();
    String               relay           = Directory.getInstance(context).getRelay(canonicalRecipientNumber);
    PreKeyEntity         preKey          = socket.getPreKey(relay, canonicalRecipientNumber);
    KeyExchangeProcessor processor       = new KeyExchangeProcessor(context, masterSecret, recipient);

    processor.processKeyExchangeMessage(preKey);

    MessageCipher message = new MessageCipher(context, masterSecret, identityKeyPair, new RawTransportDetails());
    byte[] bundledMessage = message.encrypt(recipient, plaintext);

    PreKeyBundleMessage preKeyBundleMessage = new PreKeyBundleMessage(identityKey, bundledMessage);
    return preKeyBundleMessage.serialize();
  }

  private byte[] getEncryptedMessageForExistingSession(Recipient recipient, byte[] plaintext)
      throws IOException
  {
    IdentityKeyPair identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    MessageCipher   messageCipher   = new MessageCipher(context, masterSecret, identityKeyPair,
                                                        new PushTransportDetails());

    return messageCipher.encrypt(recipient, plaintext);
  }

}

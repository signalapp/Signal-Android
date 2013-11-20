/**
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

package org.thoughtcrime.securesms.transport;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.Release;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.TextSecurePushCredentials;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.AttachmentCipher;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.KeyUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.MessageCipher;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.crypto.protocol.PreKeyBundleMessage;
import org.whispersystems.textsecure.push.OutgoingPushMessage;
import org.whispersystems.textsecure.push.PreKeyEntity;
import org.whispersystems.textsecure.push.PushAttachmentData;
import org.whispersystems.textsecure.push.PushAttachmentPointer;
import org.whispersystems.textsecure.push.PushBody;
import org.whispersystems.textsecure.push.PushDestination;
import org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.RateLimitException;

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
      TextSecurePushCredentials credentials = TextSecurePushCredentials.getInstance();
      Recipient                 recipient   = message.getIndividualRecipient();
      long                      threadId    = message.getThreadId();
      PushServiceSocket         socket      = new PushServiceSocket(context, Release.PUSH_URL, credentials);
      PushDestination           destination = PushDestination.create(context, credentials,
                                                                     recipient.getNumber());

      String   plaintextBody = message.getBody().getBody();
      byte[]   plaintext     = PushMessageContent.newBuilder().setBody(plaintextBody).build().toByteArray();
      PushBody pushBody      = getEncryptedMessage(socket, threadId, recipient, destination, plaintext);

      socket.sendMessage(destination, pushBody);

      context.sendBroadcast(constructSentIntent(context, message.getId(), message.getType(), true));
    } catch (RateLimitException e) {
      Log.w("PushTransport", e);
      throw new IOException("Rate limit exceeded.");
    }
  }

  public void deliver(SendReq message, List<PushDestination> destinations, long threadId)
      throws IOException
  {
    try {
      TextSecurePushCredentials credentials = TextSecurePushCredentials.getInstance();
      PushServiceSocket         socket      = new PushServiceSocket(context, Release.PUSH_URL, credentials);
      String                    messageBody = PartParser.getMessageText(message.getBody());
      List<PushBody>            pushBodies  = new LinkedList<PushBody>();

      for (PushDestination destination : destinations) {
        Recipients                  recipients  = RecipientFactory.getRecipientsFromString(context, destination.getNumber(), false);
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

        byte[]   plaintext = builder.build().toByteArray();
        PushBody pushBody  = getEncryptedMessage(socket, threadId, recipients.getPrimaryRecipient(), destination, plaintext);

        pushBodies.add(pushBody);
      }

      socket.sendMessage(destinations, pushBodies);

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

  private PushBody getEncryptedMessage(PushServiceSocket socket, long threadId, Recipient recipient,
                                       PushDestination pushDestination, byte[] plaintext)
      throws IOException
  {
    if (KeyUtil.isNonPrekeySessionFor(context, masterSecret, recipient)) {
      Log.w("PushTransport", "Sending standard ciphertext message...");
      byte[] ciphertext = getEncryptedMessageForExistingSession(recipient, plaintext);
      return new PushBody(OutgoingPushMessage.TYPE_MESSAGE_CIPHERTEXT, ciphertext);
    } else if (KeyUtil.isSessionFor(context, recipient)) {
      Log.w("PushTransport", "Sending prekeybundle ciphertext message for existing session...");
      byte[] ciphertext = getEncryptedPrekeyBundleMessageForExistingSession(recipient, plaintext);
      return new PushBody(OutgoingPushMessage.TYPE_MESSAGE_PREKEY_BUNDLE, ciphertext);
    } else {
      Log.w("PushTransport", "Sending prekeybundle ciphertext message for new session...");
      byte[] ciphertext = getEncryptedPrekeyBundleMessageForNewSession(socket, threadId, recipient, pushDestination, plaintext);
      return new PushBody(OutgoingPushMessage.TYPE_MESSAGE_PREKEY_BUNDLE, ciphertext);
    }
  }

  private byte[] getEncryptedPrekeyBundleMessageForExistingSession(Recipient recipient,
                                                                   byte[] plaintext)
  {
    IdentityKeyPair     identityKeyPair     = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret, Curve.DJB_TYPE);
    IdentityKey         identityKey         = identityKeyPair.getPublicKey();
    MessageCipher       messageCipher       = new MessageCipher(context, masterSecret, identityKeyPair);
    CiphertextMessage   ciphertextMessage   = messageCipher.encrypt(recipient, plaintext);
    PreKeyBundleMessage preKeyBundleMessage = new PreKeyBundleMessage(ciphertextMessage, identityKey);

    return preKeyBundleMessage.serialize();
  }

  private byte[] getEncryptedPrekeyBundleMessageForNewSession(PushServiceSocket socket,
                                                              long threadId,
                                                              Recipient recipient,
                                                              PushDestination pushDestination,
                                                              byte[] plaintext)
      throws IOException
  {
    IdentityKeyPair      identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret, Curve.DJB_TYPE);
    IdentityKey          identityKey     = identityKeyPair.getPublicKey();
    PreKeyEntity         preKey          = socket.getPreKey(pushDestination);
    KeyExchangeProcessor processor       = new KeyExchangeProcessor(context, masterSecret, recipient);

    processor.processKeyExchangeMessage(preKey, threadId);

    MessageCipher       messageCipher       = new MessageCipher(context, masterSecret, identityKeyPair);
    CiphertextMessage   ciphertextMessage   = messageCipher.encrypt(recipient, plaintext);
    PreKeyBundleMessage preKeyBundleMessage = new PreKeyBundleMessage(ciphertextMessage, identityKey);

    return preKeyBundleMessage.serialize();
  }

  private byte[] getEncryptedMessageForExistingSession(Recipient recipient, byte[] plaintext)
      throws IOException
  {
    IdentityKeyPair   identityKeyPair   = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret, Curve.DJB_TYPE);
    MessageCipher     messageCipher     = new MessageCipher(context, masterSecret, identityKeyPair);
    CiphertextMessage ciphertextMessage = messageCipher.encrypt(recipient, plaintext);

    return ciphertextMessage.serialize();
  }

}

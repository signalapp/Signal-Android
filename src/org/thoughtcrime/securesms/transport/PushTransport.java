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

import org.thoughtcrime.securesms.crypto.KeyExchangeProcessorV2;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.AttachmentCipher;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.SessionCipher;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.push.MismatchedDevices;
import org.whispersystems.textsecure.push.MismatchedDevicesException;
import org.whispersystems.textsecure.push.OutgoingPushMessage;
import org.whispersystems.textsecure.push.OutgoingPushMessageList;
import org.whispersystems.textsecure.push.PreKeyEntity;
import org.whispersystems.textsecure.push.PushAddress;
import org.whispersystems.textsecure.push.PushAttachmentData;
import org.whispersystems.textsecure.push.PushAttachmentPointer;
import org.whispersystems.textsecure.push.PushBody;
import org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.UnregisteredUserException;
import org.whispersystems.textsecure.storage.SessionRecordV2;
import org.whispersystems.textsecure.util.InvalidNumberException;

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
      Recipient         recipient = message.getIndividualRecipient();
      long              threadId  = message.getThreadId();
      PushServiceSocket socket    = PushServiceSocketFactory.create(context);
      byte[]            plaintext = PushMessageContent.newBuilder()
                                                      .setBody(message.getBody().getBody())
                                                      .build().toByteArray();

      deliver(socket, recipient, threadId, plaintext);

      context.sendBroadcast(constructSentIntent(context, message.getId(), message.getType(), true));

    } catch (UnregisteredUserException e) {
      Log.w("PushTransport", e);
      //TODO We should probably remove the user from the directory?
//      destroySessions(message.getIndividualRecipient());
      throw new IOException("Not push registered after all.");
    } catch (InvalidNumberException e) {
      Log.w("PushTransport", e);
      throw new IOException("Badly formatted number.");
    }
  }

  public void deliver(SendReq message, long threadId) throws IOException {
    PushServiceSocket socket      = PushServiceSocketFactory.create(context);
    byte[]            plaintext   = getPlaintextMessage(socket, message);
    String            destination = message.getTo()[0].getString();

    Recipients recipients;

    try {
      if (GroupUtil.isEncodedGroup(destination)) {
        recipients = DatabaseFactory.getGroupDatabase(context)
                                    .getGroupMembers(GroupUtil.getDecodedId(destination));
      } else {
        recipients = RecipientFactory.getRecipientsFromString(context, destination, false);
      }

      for (Recipient recipient : recipients.getRecipientsList()) {
        deliver(socket, recipient, threadId, plaintext);
      }
    } catch (UnregisteredUserException uue) {
      // TODO: We should probably remove the user from the directory?
      throw new IOException(uue);
    } catch (RecipientFormattingException e) {
      throw new IOException(e);
    } catch (InvalidNumberException e) {
      throw new IOException(e);
    }
  }

  public List<Recipient> deliver(List<Recipient> recipients,
                                 PushMessageContent.GroupContext groupAction)
      throws IOException
  {
    PushServiceSocket socket    = PushServiceSocketFactory.create(context);
    byte[]            plaintext = PushMessageContent.newBuilder()
                                                    .setGroup(groupAction)
                                                    .build().toByteArray();
    List<Recipient> failures = new LinkedList<Recipient>();

    for (Recipient recipient : recipients) {
      try {
        deliver(socket, recipient, -1, plaintext);
      } catch (UnregisteredUserException e) {
        Log.w("PushTransport", e);
        failures.add(recipient);
      } catch (InvalidNumberException e) {
        Log.w("PushTransport", e);
        failures.add(recipient);
      } catch (IOException e) {
        Log.w("PushTransport", e);
        failures.add(recipient);
      }
    }

    if (failures.size() == recipients.size()) {
      throw new IOException("Total failure.");
    }

    return failures;
  }

  public PushAttachmentPointer createAttachment(String contentType, byte[] data)
      throws IOException
  {
    PushServiceSocket socket = PushServiceSocketFactory.create(context);
    return getPushAttachmentPointer(socket, contentType, data);
  }

  private void deliver(PushServiceSocket socket, Recipient recipient, long threadId, byte[] plaintext)
      throws IOException, InvalidNumberException
  {
    for (int i=0;i<3;i++) {
      try {
        OutgoingPushMessageList messages = getEncryptedMessages(socket, threadId,
                                                                recipient, plaintext);
        socket.sendMessage(messages);

        return;
      } catch (MismatchedDevicesException mde) {
        Log.w("PushTransport", mde);
        handleMismatchedDevices(socket, threadId, recipient, mde.getMismatchedDevices());
      }
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
        attachments.add(getPushAttachmentPointer(socket, contentType, body.getPart(i).getData()));
      }
    }

    return attachments;
  }

  private PushAttachmentPointer getPushAttachmentPointer(PushServiceSocket socket,
                                                         String contentType, byte[] data)
      throws IOException
  {
    AttachmentCipher   cipher               = new AttachmentCipher();
    byte[]             key                  = cipher.getCombinedKeyMaterial();
    byte[]             ciphertextAttachment = cipher.encrypt(data);
    PushAttachmentData attachmentData       = new PushAttachmentData(contentType, ciphertextAttachment);
    long               attachmentId         = socket.sendAttachment(attachmentData);

    return new PushAttachmentPointer(contentType, attachmentId, key);
  }

  private void handleMismatchedDevices(PushServiceSocket socket, long threadId,
                                       Recipient recipient,
                                       MismatchedDevices mismatchedDevices)
      throws InvalidNumberException, IOException
  {
    try {
      String e164number = Util.canonicalizeNumber(context, recipient.getNumber());
      long   recipientId = recipient.getRecipientId();

      for (int extraDeviceId : mismatchedDevices.getExtraDevices()) {
        PushAddress address = PushAddress.create(context, recipientId, e164number, extraDeviceId);
        SessionRecordV2.delete(context, address);
      }

      for (int missingDeviceId : mismatchedDevices.getMissingDevices()) {
        PushAddress            address   = PushAddress.create(context, recipientId, e164number, missingDeviceId);
        PreKeyEntity           preKey    = socket.getPreKey(address);
        KeyExchangeProcessorV2 processor = new KeyExchangeProcessorV2(context, masterSecret, address);

        processor.processKeyExchangeMessage(preKey, threadId);
      }
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private byte[] getPlaintextMessage(PushServiceSocket socket, SendReq message) throws IOException {
    String                      messageBody = PartParser.getMessageText(message.getBody());
    List<PushAttachmentPointer> attachments = getPushAttachmentPointers(socket, message.getBody());

    PushMessageContent.Builder builder = PushMessageContent.newBuilder();

    if (GroupUtil.isEncodedGroup(message.getTo()[0].getString())) {
      PushMessageContent.GroupContext.Builder groupBuilder =
          PushMessageContent.GroupContext.newBuilder();

      groupBuilder.setType(PushMessageContent.GroupContext.Type.DELIVER);
      groupBuilder.setId(ByteString.copyFrom(GroupUtil.getDecodedId(message.getTo()[0].getString())));

      builder.setGroup(groupBuilder.build());
    }

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

    return builder.build().toByteArray();
  }

  private OutgoingPushMessageList getEncryptedMessages(PushServiceSocket socket, long threadId,
                                                       Recipient recipient, byte[] plaintext)
      throws IOException, InvalidNumberException
  {
    String      e164number   = Util.canonicalizeNumber(context, recipient.getNumber());
    long        recipientId  = recipient.getRecipientId();
    PushAddress masterDevice = PushAddress.create(context, recipientId, e164number, 1);
    PushBody    masterBody   = getEncryptedMessage(socket, threadId, masterDevice, plaintext);

    List<OutgoingPushMessage> messages = new LinkedList<OutgoingPushMessage>();
    messages.add(new OutgoingPushMessage(masterDevice, masterBody));

    for (int deviceId : SessionRecordV2.getSessionSubDevices(context, recipient)) {
      PushAddress device = PushAddress.create(context, recipientId, e164number, deviceId);
      PushBody    body   = getEncryptedMessage(socket, threadId, device, plaintext);

      messages.add(new OutgoingPushMessage(device, body));
    }

    return new OutgoingPushMessageList(e164number, masterDevice.getRelay(), messages);
  }

  private PushBody getEncryptedMessage(PushServiceSocket socket, long threadId,
                                       PushAddress pushAddress, byte[] plaintext)
      throws IOException
  {
    if (!SessionRecordV2.hasSession(context, masterSecret, pushAddress)) {
      try {
        List<PreKeyEntity> preKeys = socket.getPreKeys(pushAddress);

        for (PreKeyEntity preKey : preKeys) {
          PushAddress            device    = PushAddress.create(context, pushAddress.getRecipientId(), pushAddress.getNumber(), preKey.getDeviceId());
          KeyExchangeProcessorV2 processor = new KeyExchangeProcessorV2(context, masterSecret, device);

          processor.processKeyExchangeMessage(preKey, threadId);
        }
      } catch (InvalidKeyException e) {
        throw new IOException(e);
      }
    }

    SessionCipher     cipher  = SessionCipher.createFor(context, masterSecret, pushAddress);
    CiphertextMessage message = cipher.encrypt(plaintext);

    if (message.getType() == CiphertextMessage.PREKEY_WHISPER_TYPE) {
      return new PushBody(OutgoingPushMessage.TYPE_MESSAGE_PREKEY_BUNDLE, message.serialize());
    } else if (message.getType() == CiphertextMessage.CURRENT_WHISPER_TYPE) {
      return new PushBody(OutgoingPushMessage.TYPE_MESSAGE_CIPHERTEXT, message.serialize());
    } else {
      throw new AssertionError("Unknown ciphertext type: " + message.getType());
    }
  }

  private void destroySessions(Recipient recipient) {
    SessionRecordV2.deleteAll(context, recipient);
  }
}

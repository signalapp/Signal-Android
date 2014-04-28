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

import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.mms.PartParser;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.textsecure.crypto.AttachmentCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.SessionCipherFactory;
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
import org.whispersystems.textsecure.push.StaleDevices;
import org.whispersystems.textsecure.push.StaleDevicesException;
import org.whispersystems.textsecure.push.UnregisteredUserException;
import org.whispersystems.textsecure.storage.SessionUtil;
import org.whispersystems.textsecure.storage.TextSecureSessionStore;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.pdu.PduBody;
import ws.com.google.android.mms.pdu.SendReq;

import static org.whispersystems.textsecure.push.PushMessageProtos.IncomingPushMessageSignal;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.AttachmentPointer;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext;

public class PushTransport extends BaseTransport {

  private final Context      context;
  private final MasterSecret masterSecret;

  public PushTransport(Context context, MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  public void deliver(SmsMessageRecord message)
      throws IOException, UntrustedIdentityException
  {
    try {
      Recipient         recipient = message.getIndividualRecipient();
      long              threadId  = message.getThreadId();
      PushServiceSocket socket    = PushServiceSocketFactory.create(context);
      byte[]            plaintext = getPlaintextMessage(message);

      deliver(socket, recipient, threadId, plaintext);

      if (message.isEndSession()) {
        SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);
        sessionStore.deleteAll(recipient.getRecipientId());
        KeyExchangeProcessor.broadcastSecurityUpdateEvent(context, threadId);
      }

      context.sendBroadcast(constructSentIntent(context, message.getId(), message.getType(), true, true));

    } catch (InvalidNumberException e) {
      Log.w("PushTransport", e);
      throw new IOException("Badly formatted number.");
    }
  }

  public void deliver(SendReq message, long threadId)
      throws IOException, RecipientFormattingException, InvalidNumberException, EncapsulatedExceptions
  {
    PushServiceSocket socket      = PushServiceSocketFactory.create(context);
    byte[]            plaintext   = getPlaintextMessage(socket, message);
    String            destination = message.getTo()[0].getString();

    Recipients recipients;

    if (GroupUtil.isEncodedGroup(destination)) {
      recipients = DatabaseFactory.getGroupDatabase(context)
                                  .getGroupMembers(GroupUtil.getDecodedId(destination), false);
    } else {
      recipients = RecipientFactory.getRecipientsFromString(context, destination, false);
    }

    List<UntrustedIdentityException> untrustedIdentities = new LinkedList<>();
    List<UnregisteredUserException>  unregisteredUsers   = new LinkedList<>();

    for (Recipient recipient : recipients.getRecipientsList()) {
      try {
        deliver(socket, recipient, threadId, plaintext);
      } catch (UntrustedIdentityException e) {
        Log.w("PushTransport", e);
        untrustedIdentities.add(e);
      } catch (UnregisteredUserException e) {
        Log.w("PushTransport", e);
        unregisteredUsers.add(e);
      }
    }

    if (!untrustedIdentities.isEmpty() || !unregisteredUsers.isEmpty()) {
      throw new EncapsulatedExceptions(untrustedIdentities, unregisteredUsers);
    }
  }

  private void deliver(PushServiceSocket socket, Recipient recipient, long threadId, byte[] plaintext)
      throws IOException, InvalidNumberException, UntrustedIdentityException
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
      } catch (StaleDevicesException ste) {
        Log.w("PushTransport", ste);
        handleStaleDevices(recipient, ste.getStaleDevices());
      }
    }
  }

  private List<PushAttachmentPointer> getPushAttachmentPointers(PushServiceSocket socket, PduBody body)
      throws IOException
  {
    List<PushAttachmentPointer> attachments = new LinkedList<>();

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
      throws InvalidNumberException, IOException, UntrustedIdentityException
  {
    try {
      SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);
      String       e164number   = Util.canonicalizeNumber(context, recipient.getNumber());
      long         recipientId  = recipient.getRecipientId();

      for (int extraDeviceId : mismatchedDevices.getExtraDevices()) {
        sessionStore.delete(recipientId, extraDeviceId);
      }

      for (int missingDeviceId : mismatchedDevices.getMissingDevices()) {
        PushAddress            address   = PushAddress.create(context, recipientId, e164number, missingDeviceId);
        PreKeyEntity           preKey    = socket.getPreKey(address);
        KeyExchangeProcessor processor = new KeyExchangeProcessor(context, masterSecret, address);

        try {
          processor.processKeyExchangeMessage(preKey, threadId);
        } catch (org.whispersystems.libaxolotl.UntrustedIdentityException e) {
          throw new UntrustedIdentityException("Untrusted identity key!", e164number, preKey.getIdentityKey());
        }

      }
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private void handleStaleDevices(Recipient recipient, StaleDevices staleDevices) {
    SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);
    long         recipientId  = recipient.getRecipientId();

    for (int staleDeviceId : staleDevices.getStaleDevices()) {
      sessionStore.delete(recipientId, staleDeviceId);
    }
  }

  private byte[] getPlaintextMessage(PushServiceSocket socket, SendReq message) throws IOException {
    String                      messageBody = PartParser.getMessageText(message.getBody());
    List<PushAttachmentPointer> attachments = getPushAttachmentPointers(socket, message.getBody());

    PushMessageContent.Builder builder = PushMessageContent.newBuilder();

    if (GroupUtil.isEncodedGroup(message.getTo()[0].getString())) {
      GroupContext.Builder groupBuilder = GroupContext.newBuilder();
      byte[]               groupId      = GroupUtil.getDecodedId(message.getTo()[0].getString());

      groupBuilder.setId(ByteString.copyFrom(groupId));
      groupBuilder.setType(GroupContext.Type.DELIVER);

      if (MmsSmsColumns.Types.isGroupUpdate(message.getDatabaseMessageBox()) ||
          MmsSmsColumns.Types.isGroupQuit(message.getDatabaseMessageBox()))
      {
        if (messageBody != null && messageBody.trim().length() > 0) {
          groupBuilder = GroupContext.parseFrom(Base64.decode(messageBody)).toBuilder();
          messageBody  = null;

          if (attachments != null && !attachments.isEmpty()) {
            groupBuilder.setAvatar(AttachmentPointer.newBuilder()
                                                    .setId(attachments.get(0).getId())
                                                    .setContentType(attachments.get(0).getContentType())
                                                    .setKey(ByteString.copyFrom(attachments.get(0).getKey()))
                                                    .build());

            attachments.remove(0);
          }
        }
      }

      builder.setGroup(groupBuilder.build());
    }

    if (messageBody != null) {
      builder.setBody(messageBody);
    }

    for (PushAttachmentPointer attachment : attachments) {
      AttachmentPointer.Builder attachmentBuilder =
          AttachmentPointer.newBuilder();

      attachmentBuilder.setId(attachment.getId());
      attachmentBuilder.setContentType(attachment.getContentType());
      attachmentBuilder.setKey(ByteString.copyFrom(attachment.getKey()));

      builder.addAttachments(attachmentBuilder.build());
    }

    return builder.build().toByteArray();
  }

  private byte[] getPlaintextMessage(SmsMessageRecord record) {
    PushMessageContent.Builder builder = PushMessageContent.newBuilder()
                                                           .setBody(record.getBody().getBody());

    if (record.isEndSession()) {
      builder.setFlags(PushMessageContent.Flags.END_SESSION_VALUE);
    }

    return builder.build().toByteArray();
  }

  private OutgoingPushMessageList getEncryptedMessages(PushServiceSocket socket, long threadId,
                                                       Recipient recipient, byte[] plaintext)
      throws IOException, InvalidNumberException, UntrustedIdentityException
  {
    SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);
    String       e164number   = Util.canonicalizeNumber(context, recipient.getNumber());
    long         recipientId  = recipient.getRecipientId();
    PushAddress  masterDevice = PushAddress.create(context, recipientId, e164number, 1);
    PushBody     masterBody   = getEncryptedMessage(socket, threadId, masterDevice, plaintext);

    List<OutgoingPushMessage> messages = new LinkedList<>();
    messages.add(new OutgoingPushMessage(masterDevice, masterBody));

    for (int deviceId : sessionStore.getSubDeviceSessions(recipientId)) {
      PushAddress device = PushAddress.create(context, recipientId, e164number, deviceId);
      PushBody    body   = getEncryptedMessage(socket, threadId, device, plaintext);

      messages.add(new OutgoingPushMessage(device, body));
    }

    return new OutgoingPushMessageList(e164number, masterDevice.getRelay(), messages);
  }

  private PushBody getEncryptedMessage(PushServiceSocket socket, long threadId,
                                       PushAddress pushAddress, byte[] plaintext)
      throws IOException, UntrustedIdentityException
  {
    if (!SessionUtil.hasEncryptCapableSession(context, masterSecret, pushAddress)) {
      try {
        List<PreKeyEntity> preKeys = socket.getPreKeys(pushAddress);

        for (PreKeyEntity preKey : preKeys) {
          PushAddress            device    = PushAddress.create(context, pushAddress.getRecipientId(), pushAddress.getNumber(), preKey.getDeviceId());
          KeyExchangeProcessor processor = new KeyExchangeProcessor(context, masterSecret, device);

          try {
            processor.processKeyExchangeMessage(preKey, threadId);
          } catch (org.whispersystems.libaxolotl.UntrustedIdentityException e) {
            throw new UntrustedIdentityException("Untrusted identity key!", pushAddress.getNumber(), preKey.getIdentityKey());
          }
        }
      } catch (InvalidKeyException e) {
        throw new IOException(e);
      }
    }

    SessionCipher     cipher               = SessionCipherFactory.getInstance(context, masterSecret, pushAddress);
    CiphertextMessage message              = cipher.encrypt(plaintext);
    int               remoteRegistrationId = cipher.getRemoteRegistrationId();

    if (message.getType() == CiphertextMessage.PREKEY_TYPE) {
      return new PushBody(IncomingPushMessageSignal.Type.PREKEY_BUNDLE_VALUE, remoteRegistrationId, message.serialize());
    } else if (message.getType() == CiphertextMessage.WHISPER_TYPE) {
      return new PushBody(IncomingPushMessageSignal.Type.CIPHERTEXT_VALUE, remoteRegistrationId, message.serialize());
    } else {
      throw new AssertionError("Unknown ciphertext type: " + message.getType());
    }
  }
}

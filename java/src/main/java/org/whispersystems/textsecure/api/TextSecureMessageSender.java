/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.whispersystems.textsecure.api;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.SessionBuilder;
import org.whispersystems.libaxolotl.logging.Log;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.crypto.TextSecureCipher;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentStream;
import org.whispersystems.textsecure.api.messages.TextSecureDataMessage;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.messages.multidevice.TextSecureSyncMessage;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.TrustStore;
import org.whispersystems.textsecure.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.textsecure.api.push.exceptions.NetworkFailureException;
import org.whispersystems.textsecure.api.push.exceptions.PushNetworkException;
import org.whispersystems.textsecure.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.textsecure.internal.push.MismatchedDevices;
import org.whispersystems.textsecure.internal.push.OutgoingPushMessage;
import org.whispersystems.textsecure.internal.push.OutgoingPushMessageList;
import org.whispersystems.textsecure.internal.push.PushAttachmentData;
import org.whispersystems.textsecure.internal.push.PushServiceSocket;
import org.whispersystems.textsecure.internal.push.SendMessageResponse;
import org.whispersystems.textsecure.internal.push.StaleDevices;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.AttachmentPointer;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.Content;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.DataMessage;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.GroupContext;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.SyncMessage;
import org.whispersystems.textsecure.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.textsecure.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.textsecure.internal.util.StaticCredentialsProvider;
import org.whispersystems.textsecure.internal.util.Util;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * The main interface for sending TextSecure messages.
 *
 * @author Moxie Marlinspike
 */
public class TextSecureMessageSender {

  private static final String TAG = TextSecureMessageSender.class.getSimpleName();

  private final PushServiceSocket       socket;
  private final AxolotlStore            store;
  private final TextSecureAddress       localAddress;
  private final Optional<EventListener> eventListener;

  /**
   * Construct a TextSecureMessageSender.
   *
   * @param url The URL of the TextSecure server.
   * @param trustStore The trust store containing the TextSecure server's signing TLS certificate.
   * @param user The TextSecure username (eg phone number).
   * @param password The TextSecure user's password.
   * @param store The AxolotlStore.
   * @param eventListener An optional event listener, which fires whenever sessions are
   *                      setup or torn down for a recipient.
   */
  public TextSecureMessageSender(String url, TrustStore trustStore,
                                 String user, String password,
                                 AxolotlStore store,
                                 String userAgent,
                                 Optional<EventListener> eventListener)
  {
    this.socket        = new PushServiceSocket(url, trustStore, new StaticCredentialsProvider(user, password, null), userAgent);
    this.store         = store;
    this.localAddress  = new TextSecureAddress(user);
    this.eventListener = eventListener;
  }

  /**
   * Send a delivery receipt for a received message.  It is not necessary to call this
   * when receiving messages through {@link org.whispersystems.textsecure.api.TextSecureMessagePipe}.
   * @param recipient The sender of the received message you're acknowledging.
   * @param messageId The message id of the received message you're acknowledging.
   * @throws IOException
   */
  public void sendDeliveryReceipt(TextSecureAddress recipient, long messageId) throws IOException {
    this.socket.sendReceipt(recipient.getNumber(), messageId, recipient.getRelay());
  }

  /**
   * Send a message to a single recipient.
   *
   * @param recipient The message's destination.
   * @param message The message.
   * @throws UntrustedIdentityException
   * @throws IOException
   */
  public void sendMessage(TextSecureAddress recipient, TextSecureDataMessage message)
      throws UntrustedIdentityException, IOException
  {
    byte[]              content   = createMessageContent(message);
    long                timestamp = message.getTimestamp();
    SendMessageResponse response  = sendMessage(recipient, timestamp, content, true);

    if (response != null && response.getNeedsSync()) {
      byte[] syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.of(recipient), timestamp);
      sendMessage(localAddress, timestamp, syncMessage, false);
    }

    if (message.isEndSession()) {
      store.deleteAllSessions(recipient.getNumber());

      if (eventListener.isPresent()) {
        eventListener.get().onSecurityEvent(recipient);
      }
    }
  }

  /**
   * Send a message to a group.
   *
   * @param recipients The group members.
   * @param message The group message.
   * @throws IOException
   * @throws EncapsulatedExceptions
   */
  public void sendMessage(List<TextSecureAddress> recipients, TextSecureDataMessage message)
      throws IOException, EncapsulatedExceptions
  {
    byte[]              content   = createMessageContent(message);
    long                timestamp = message.getTimestamp();
    SendMessageResponse response  = sendMessage(recipients, timestamp, content, true);

    try {
      if (response != null && response.getNeedsSync()) {
        byte[] syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.<TextSecureAddress>absent(), timestamp);
        sendMessage(localAddress, timestamp, syncMessage, false);
      }
    } catch (UntrustedIdentityException e) {
      throw new EncapsulatedExceptions(e);
    }
  }

  public void sendMessage(TextSecureSyncMessage message)
      throws IOException, UntrustedIdentityException
  {
    byte[] content;

    if (message.getContacts().isPresent()) {
      content = createMultiDeviceContactsContent(message.getContacts().get().asStream());
    } else if (message.getGroups().isPresent()) {
      content = createMultiDeviceGroupsContent(message.getGroups().get().asStream());
    } else {
      throw new IOException("Unsupported sync message!");
    }

    sendMessage(localAddress, System.currentTimeMillis(), content, false);
  }

  private byte[] createMessageContent(TextSecureDataMessage message) throws IOException {
    DataMessage.Builder     builder  = DataMessage.newBuilder();
    List<AttachmentPointer> pointers = createAttachmentPointers(message.getAttachments());

    if (!pointers.isEmpty()) {
      builder.addAllAttachments(pointers);
    }

    if (message.getBody().isPresent()) {
      builder.setBody(message.getBody().get());
    }

    if (message.getGroupInfo().isPresent()) {
      builder.setGroup(createGroupContent(message.getGroupInfo().get()));
    }

    if (message.isEndSession()) {
      builder.setFlags(DataMessage.Flags.END_SESSION_VALUE);
    }

    return builder.build().toByteArray();
  }

  private byte[] createMultiDeviceContactsContent(TextSecureAttachmentStream contacts) throws IOException {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = SyncMessage.newBuilder();
    builder.setContacts(SyncMessage.Contacts.newBuilder()
                                            .setBlob(createAttachmentPointer(contacts)));

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceGroupsContent(TextSecureAttachmentStream groups) throws IOException {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = SyncMessage.newBuilder();
    builder.setGroups(SyncMessage.Groups.newBuilder()
                                        .setBlob(createAttachmentPointer(groups)));

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceSentTranscriptContent(byte[] content, Optional<TextSecureAddress> recipient, long timestamp) {
    try {
      Content.Builder          container   = Content.newBuilder();
      SyncMessage.Builder      syncMessage = SyncMessage.newBuilder();
      SyncMessage.Sent.Builder sentMessage = SyncMessage.Sent.newBuilder();

      sentMessage.setTimestamp(timestamp);
      sentMessage.setMessage(DataMessage.parseFrom(content));

      if (recipient.isPresent()) {
        sentMessage.setDestination(recipient.get().getNumber());
      }

      return container.setSyncMessage(syncMessage.setSent(sentMessage)).build().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError(e);
    }
  }

  private GroupContext createGroupContent(TextSecureGroup group) throws IOException {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    if (group.getType() != TextSecureGroup.Type.DELIVER) {
      if      (group.getType() == TextSecureGroup.Type.UPDATE) builder.setType(GroupContext.Type.UPDATE);
      else if (group.getType() == TextSecureGroup.Type.QUIT)   builder.setType(GroupContext.Type.QUIT);
      else                                                     throw new AssertionError("Unknown type: " + group.getType());

      if (group.getName().isPresent()) builder.setName(group.getName().get());
      if (group.getMembers().isPresent()) builder.addAllMembers(group.getMembers().get());

      if (group.getAvatar().isPresent() && group.getAvatar().get().isStream()) {
        AttachmentPointer pointer = createAttachmentPointer(group.getAvatar().get().asStream());
        builder.setAvatar(pointer);
      }
    } else {
      builder.setType(GroupContext.Type.DELIVER);
    }

    return builder.build();
  }

  private SendMessageResponse sendMessage(List<TextSecureAddress> recipients, long timestamp, byte[] content, boolean legacy)
      throws IOException, EncapsulatedExceptions
  {
    List<UntrustedIdentityException> untrustedIdentities = new LinkedList<>();
    List<UnregisteredUserException>  unregisteredUsers   = new LinkedList<>();
    List<NetworkFailureException>    networkExceptions   = new LinkedList<>();

    SendMessageResponse response = null;

    for (TextSecureAddress recipient : recipients) {
      try {
        response = sendMessage(recipient, timestamp, content, legacy);
      } catch (UntrustedIdentityException e) {
        Log.w(TAG, e);
        untrustedIdentities.add(e);
      } catch (UnregisteredUserException e) {
        Log.w(TAG, e);
        unregisteredUsers.add(e);
      } catch (PushNetworkException e) {
        Log.w(TAG, e);
        networkExceptions.add(new NetworkFailureException(recipient.getNumber(), e));
      }
    }

    if (!untrustedIdentities.isEmpty() || !unregisteredUsers.isEmpty() || !networkExceptions.isEmpty()) {
      throw new EncapsulatedExceptions(untrustedIdentities, unregisteredUsers, networkExceptions);
    }

    return response;
  }

  private SendMessageResponse sendMessage(TextSecureAddress recipient, long timestamp, byte[] content, boolean legacy)
      throws UntrustedIdentityException, IOException
  {
    for (int i=0;i<3;i++) {
      try {
        OutgoingPushMessageList messages = getEncryptedMessages(socket, recipient, timestamp, content, legacy);
        return socket.sendMessage(messages);
      } catch (MismatchedDevicesException mde) {
        Log.w(TAG, mde);
        handleMismatchedDevices(socket, recipient, mde.getMismatchedDevices());
      } catch (StaleDevicesException ste) {
        Log.w(TAG, ste);
        handleStaleDevices(recipient, ste.getStaleDevices());
      }
    }

    throw new IOException("Failed to resolve conflicts after 3 attempts!");
  }

  private List<AttachmentPointer> createAttachmentPointers(Optional<List<TextSecureAttachment>> attachments) throws IOException {
    List<AttachmentPointer> pointers = new LinkedList<>();

    if (!attachments.isPresent() || attachments.get().isEmpty()) {
      Log.w(TAG, "No attachments present...");
      return pointers;
    }

    for (TextSecureAttachment attachment : attachments.get()) {
      if (attachment.isStream()) {
        Log.w(TAG, "Found attachment, creating pointer...");
        pointers.add(createAttachmentPointer(attachment.asStream()));
      }
    }

    return pointers;
  }

  private AttachmentPointer createAttachmentPointer(TextSecureAttachmentStream attachment)
      throws IOException
  {
    byte[]             attachmentKey  = Util.getSecretBytes(64);
    PushAttachmentData attachmentData = new PushAttachmentData(attachment.getContentType(),
                                                               attachment.getInputStream(),
                                                               attachment.getLength(),
                                                               attachment.getListener(),
                                                               attachmentKey);

    long attachmentId = socket.sendAttachment(attachmentData);

    AttachmentPointer.Builder builder = AttachmentPointer.newBuilder()
                                                         .setContentType(attachment.getContentType())
                                                         .setId(attachmentId)
                                                         .setKey(ByteString.copyFrom(attachmentKey))
                                                         .setSize((int)attachment.getLength());

    if (attachment.getPreview().isPresent()) {
      builder.setThumbnail(ByteString.copyFrom(attachment.getPreview().get()));
    }

    return builder.build();
  }


  private OutgoingPushMessageList getEncryptedMessages(PushServiceSocket socket,
                                                       TextSecureAddress recipient,
                                                       long timestamp,
                                                       byte[] plaintext,
                                                       boolean legacy)
      throws IOException, UntrustedIdentityException
  {
    List<OutgoingPushMessage> messages = new LinkedList<>();

    if (!recipient.equals(localAddress)) {
      messages.add(getEncryptedMessage(socket, recipient, TextSecureAddress.DEFAULT_DEVICE_ID, plaintext, legacy));
    }

    for (int deviceId : store.getSubDeviceSessions(recipient.getNumber())) {
      messages.add(getEncryptedMessage(socket, recipient, deviceId, plaintext, legacy));
    }

    return new OutgoingPushMessageList(recipient.getNumber(), timestamp, recipient.getRelay().orNull(), messages);
  }

  private OutgoingPushMessage getEncryptedMessage(PushServiceSocket socket, TextSecureAddress recipient, int deviceId, byte[] plaintext, boolean legacy)
      throws IOException, UntrustedIdentityException
  {
    AxolotlAddress   axolotlAddress = new AxolotlAddress(recipient.getNumber(), deviceId);
    TextSecureCipher cipher         = new TextSecureCipher(localAddress, store);

    if (!store.containsSession(axolotlAddress)) {
      try {
        List<PreKeyBundle> preKeys = socket.getPreKeys(recipient, deviceId);

        for (PreKeyBundle preKey : preKeys) {
          try {
            AxolotlAddress preKeyAddress  = new AxolotlAddress(recipient.getNumber(), preKey.getDeviceId());
            SessionBuilder sessionBuilder = new SessionBuilder(store, preKeyAddress);
            sessionBuilder.process(preKey);
          } catch (org.whispersystems.libaxolotl.UntrustedIdentityException e) {
            throw new UntrustedIdentityException("Untrusted identity key!", recipient.getNumber(), preKey.getIdentityKey());
          }
        }

        if (eventListener.isPresent()) {
          eventListener.get().onSecurityEvent(recipient);
        }
      } catch (InvalidKeyException e) {
        throw new IOException(e);
      }
    }

    return cipher.encrypt(axolotlAddress, plaintext, legacy);
  }

  private void handleMismatchedDevices(PushServiceSocket socket, TextSecureAddress recipient,
                                       MismatchedDevices mismatchedDevices)
      throws IOException, UntrustedIdentityException
  {
    try {
      for (int extraDeviceId : mismatchedDevices.getExtraDevices()) {
        store.deleteSession(new AxolotlAddress(recipient.getNumber(), extraDeviceId));
      }

      for (int missingDeviceId : mismatchedDevices.getMissingDevices()) {
        PreKeyBundle preKey = socket.getPreKey(recipient, missingDeviceId);

        try {
          SessionBuilder sessionBuilder = new SessionBuilder(store, new AxolotlAddress(recipient.getNumber(), missingDeviceId));
          sessionBuilder.process(preKey);
        } catch (org.whispersystems.libaxolotl.UntrustedIdentityException e) {
          throw new UntrustedIdentityException("Untrusted identity key!", recipient.getNumber(), preKey.getIdentityKey());
        }
      }
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private void handleStaleDevices(TextSecureAddress recipient, StaleDevices staleDevices) {
    for (int staleDeviceId : staleDevices.getStaleDevices()) {
      store.deleteSession(new AxolotlAddress(recipient.getNumber(), staleDeviceId));
    }
  }

  public static interface EventListener {
    public void onSecurityEvent(TextSecureAddress address);
  }

}

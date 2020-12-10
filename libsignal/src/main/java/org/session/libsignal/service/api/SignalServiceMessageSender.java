/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.service.api;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.jetbrains.annotations.Nullable;
import org.session.libsignal.metadata.SealedSessionCipher;
import org.session.libsignal.libsignal.InvalidKeyException;
import org.session.libsignal.libsignal.SessionBuilder;
import org.session.libsignal.libsignal.SignalProtocolAddress;
import org.session.libsignal.libsignal.logging.Log;
import org.session.libsignal.libsignal.loki.FallbackMessage;
import org.session.libsignal.libsignal.loki.FallbackSessionCipher;
import org.session.libsignal.libsignal.loki.SessionResetProtocol;
import org.session.libsignal.libsignal.state.PreKeyBundle;
import org.session.libsignal.libsignal.state.SignalProtocolStore;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.crypto.AttachmentCipherOutputStream;
import org.session.libsignal.service.api.crypto.SignalServiceCipher;
import org.session.libsignal.service.api.crypto.UnidentifiedAccess;
import org.session.libsignal.service.api.crypto.UnidentifiedAccessPair;
import org.session.libsignal.service.api.crypto.UntrustedIdentityException;
import org.session.libsignal.service.api.messages.SendMessageResult;
import org.session.libsignal.service.api.messages.SignalServiceAttachment;
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer;
import org.session.libsignal.service.api.messages.SignalServiceAttachmentStream;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceGroup;
import org.session.libsignal.service.api.messages.SignalServiceReceiptMessage;
import org.session.libsignal.service.api.messages.SignalServiceTypingMessage;
import org.session.libsignal.service.api.messages.calls.AnswerMessage;
import org.session.libsignal.service.api.messages.calls.IceUpdateMessage;
import org.session.libsignal.service.api.messages.calls.OfferMessage;
import org.session.libsignal.service.api.messages.calls.SignalServiceCallMessage;
import org.session.libsignal.service.api.messages.multidevice.BlockedListMessage;
import org.session.libsignal.service.api.messages.multidevice.ConfigurationMessage;
import org.session.libsignal.service.api.messages.multidevice.ReadMessage;
import org.session.libsignal.service.api.messages.multidevice.SentTranscriptMessage;
import org.session.libsignal.service.api.messages.multidevice.SignalServiceSyncMessage;
import org.session.libsignal.service.api.messages.multidevice.StickerPackOperationMessage;
import org.session.libsignal.service.api.messages.multidevice.VerifiedMessage;
import org.session.libsignal.service.api.messages.shared.SharedContact;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.api.push.exceptions.PushNetworkException;
import org.session.libsignal.service.api.push.exceptions.UnregisteredUserException;
import org.session.libsignal.service.api.util.CredentialsProvider;
import org.session.libsignal.service.internal.configuration.SignalServiceConfiguration;
import org.session.libsignal.service.internal.crypto.PaddingInputStream;
import org.session.libsignal.service.internal.push.OutgoingPushMessage;
import org.session.libsignal.service.internal.push.OutgoingPushMessageList;
import org.session.libsignal.service.internal.push.PushAttachmentData;
import org.session.libsignal.service.internal.push.PushServiceSocket;
import org.session.libsignal.service.internal.push.PushTransportDetails;
import org.session.libsignal.service.internal.push.SignalServiceProtos;
import org.session.libsignal.service.internal.push.SignalServiceProtos.AttachmentPointer;
import org.session.libsignal.service.internal.push.SignalServiceProtos.CallMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.Content;
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext;
import org.session.libsignal.service.internal.push.SignalServiceProtos.LokiUserProfile;
import org.session.libsignal.service.internal.push.SignalServiceProtos.PreKeyBundleMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.ReceiptMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.SyncMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.TypingMessage;
import org.session.libsignal.service.internal.push.http.AttachmentCipherOutputStreamFactory;
import org.session.libsignal.service.internal.push.http.OutputStreamFactory;
import org.session.libsignal.service.internal.util.Base64;
import org.session.libsignal.service.internal.util.StaticCredentialsProvider;
import org.session.libsignal.service.internal.util.Util;
import org.session.libsignal.service.internal.util.concurrent.SettableFuture;
import org.session.libsignal.service.loki.api.LokiDotNetAPI;
import org.session.libsignal.service.loki.api.PushNotificationAPI;
import org.session.libsignal.service.loki.api.SignalMessageInfo;
import org.session.libsignal.service.loki.api.SnodeAPI;
import org.session.libsignal.service.loki.api.fileserver.FileServerAPI;
import org.session.libsignal.service.loki.api.opengroups.PublicChat;
import org.session.libsignal.service.loki.api.opengroups.PublicChatAPI;
import org.session.libsignal.service.loki.api.opengroups.PublicChatMessage;
import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol;
import org.session.libsignal.service.loki.database.LokiMessageDatabaseProtocol;
import org.session.libsignal.service.loki.database.LokiOpenGroupDatabaseProtocol;
import org.session.libsignal.service.loki.database.LokiPreKeyBundleDatabaseProtocol;
import org.session.libsignal.service.loki.database.LokiThreadDatabaseProtocol;
import org.session.libsignal.service.loki.database.LokiUserDatabaseProtocol;
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysDatabaseProtocol;
import org.session.libsignal.service.loki.protocol.meta.TTLUtilities;
import org.session.libsignal.service.loki.protocol.sessionmanagement.SessionManagementProtocol;
import org.session.libsignal.service.loki.protocol.shelved.multidevice.DeviceLink;
import org.session.libsignal.service.loki.protocol.shelved.multidevice.MultiDeviceProtocol;
import org.session.libsignal.service.loki.protocol.shelved.syncmessages.SyncMessagesProtocol;
import org.session.libsignal.service.loki.utilities.Broadcaster;
import org.session.libsignal.service.loki.utilities.PlaintextOutputStreamFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import nl.komponents.kovenant.Promise;

import static org.session.libsignal.libsignal.SessionCipher.SESSION_LOCK;

/**
 * The main interface for sending Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceMessageSender {

  private static final String TAG = SignalServiceMessageSender.class.getSimpleName();

  private final PushServiceSocket                                   socket;
  private final SignalProtocolStore                                 store;
  private final SignalServiceAddress                                localAddress;
  private final Optional<EventListener>                             eventListener;

  private final AtomicReference<Optional<SignalServiceMessagePipe>> pipe;
  private final AtomicReference<Optional<SignalServiceMessagePipe>> unidentifiedPipe;
  private final AtomicBoolean                                       isMultiDevice;

  // Loki
  private final String                                              userPublicKey;
  private final LokiAPIDatabaseProtocol                             apiDatabase;
  private final SharedSenderKeysDatabaseProtocol                    sskDatabase;
  private final LokiThreadDatabaseProtocol                          threadDatabase;
  private final LokiMessageDatabaseProtocol                         messageDatabase;
  private final LokiPreKeyBundleDatabaseProtocol                    preKeyBundleDatabase;
  private final SessionResetProtocol                                sessionResetImpl;
  private final LokiUserDatabaseProtocol                            userDatabase;
  private final LokiOpenGroupDatabaseProtocol                       openGroupDatabase;
  private final Broadcaster                                         broadcaster;

  /**
   * Construct a SignalServiceMessageSender.
   *
   * @param urls The URL of the Signal Service.
   * @param user The Signal Service username (eg phone number).
   * @param password The Signal Service user password.
   * @param store The SignalProtocolStore.
   * @param eventListener An optional event listener, which fires whenever sessions are
   *                      setup or torn down for a recipient.
   */
  public SignalServiceMessageSender(SignalServiceConfiguration urls,
                                    String user, String password,
                                    SignalProtocolStore store,
                                    String userAgent,
                                    boolean isMultiDevice,
                                    Optional<SignalServiceMessagePipe> pipe,
                                    Optional<SignalServiceMessagePipe> unidentifiedPipe,
                                    Optional<EventListener> eventListener,
                                    String userPublicKey,
                                    LokiAPIDatabaseProtocol apiDatabase,
                                    SharedSenderKeysDatabaseProtocol sskDatabase,
                                    LokiThreadDatabaseProtocol threadDatabase,
                                    LokiMessageDatabaseProtocol messageDatabase,
                                    LokiPreKeyBundleDatabaseProtocol preKeyBundleDatabase,
                                    SessionResetProtocol sessionResetImpl,
                                    LokiUserDatabaseProtocol userDatabase,
                                    LokiOpenGroupDatabaseProtocol openGroupDatabase,
                                    Broadcaster broadcaster)
  {
    this(urls, new StaticCredentialsProvider(user, password, null), store, userAgent, isMultiDevice, pipe, unidentifiedPipe, eventListener, userPublicKey, apiDatabase, sskDatabase, threadDatabase, messageDatabase, preKeyBundleDatabase, sessionResetImpl, userDatabase, openGroupDatabase, broadcaster);
  }

  public SignalServiceMessageSender(SignalServiceConfiguration urls,
                                    CredentialsProvider credentialsProvider,
                                    SignalProtocolStore store,
                                    String userAgent,
                                    boolean isMultiDevice,
                                    Optional<SignalServiceMessagePipe> pipe,
                                    Optional<SignalServiceMessagePipe> unidentifiedPipe,
                                    Optional<EventListener> eventListener,
                                    String userPublicKey,
                                    LokiAPIDatabaseProtocol apiDatabase,
                                    SharedSenderKeysDatabaseProtocol sskDatabase,
                                    LokiThreadDatabaseProtocol threadDatabase,
                                    LokiMessageDatabaseProtocol messageDatabase,
                                    LokiPreKeyBundleDatabaseProtocol preKeyBundleDatabase,
                                    SessionResetProtocol sessionResetImpl,
                                    LokiUserDatabaseProtocol userDatabase,
                                    LokiOpenGroupDatabaseProtocol openGroupDatabase,
                                    Broadcaster broadcaster)
  {
    this.socket                    = new PushServiceSocket(urls, credentialsProvider, userAgent);
    this.store                     = store;
    this.localAddress              = new SignalServiceAddress(credentialsProvider.getUser());
    this.pipe                      = new AtomicReference<>(pipe);
    this.unidentifiedPipe          = new AtomicReference<>(unidentifiedPipe);
    this.isMultiDevice             = new AtomicBoolean(isMultiDevice);
    this.eventListener             = eventListener;
    this.userPublicKey             = userPublicKey;
    this.apiDatabase               = apiDatabase;
    this.sskDatabase               = sskDatabase;
    this.threadDatabase            = threadDatabase;
    this.messageDatabase           = messageDatabase;
    this.preKeyBundleDatabase      = preKeyBundleDatabase;
    this.sessionResetImpl          = sessionResetImpl;
    this.userDatabase              = userDatabase;
    this.openGroupDatabase         = openGroupDatabase;
    this.broadcaster               = broadcaster;
  }

  /**
   * Send a read receipt for a received message.
   *
   * @param recipient The sender of the received message you're acknowledging.
   * @param message The read receipt to deliver.
   * @throws IOException
   * @throws UntrustedIdentityException
   */
  public void sendReceipt(SignalServiceAddress recipient,
                          Optional<UnidentifiedAccessPair> unidentifiedAccess,
                          SignalServiceReceiptMessage message)
      throws IOException, UntrustedIdentityException
  {
    byte[] content = createReceiptContent(message);
    boolean useFallbackEncryption = SessionManagementProtocol.shared.shouldMessageUseFallbackEncryption(message, recipient.getNumber(), store);
    sendMessage(recipient, getTargetUnidentifiedAccess(unidentifiedAccess), message.getWhen(), content, false, message.getTTL(), useFallbackEncryption, false);
  }

  /**
   * Send a typing indicator.
   *
   * @param recipient The destination
   * @param message The typing indicator to deliver
   * @throws IOException
   * @throws UntrustedIdentityException
   */
  public void sendTyping(SignalServiceAddress recipient,
                         Optional<UnidentifiedAccessPair> unidentifiedAccess,
                         SignalServiceTypingMessage message)
      throws IOException, UntrustedIdentityException
  {
    byte[] content = createTypingContent(message);
    boolean useFallbackEncryption = SessionManagementProtocol.shared.shouldMessageUseFallbackEncryption(message, recipient.getNumber(), store);
    sendMessage(recipient, getTargetUnidentifiedAccess(unidentifiedAccess), message.getTimestamp(), content, true, message.getTTL(), useFallbackEncryption, false);
  }

  public void sendTyping(List<SignalServiceAddress>             recipients,
                         List<Optional<UnidentifiedAccessPair>> unidentifiedAccess,
                         SignalServiceTypingMessage             message)
      throws IOException
  {
    byte[] content = createTypingContent(message);
    sendMessage(0, recipients, getTargetUnidentifiedAccess(unidentifiedAccess), message.getTimestamp(), content, true, message.getTTL(), false, false);
  }

  /**
   * Send a call setup message to a single recipient.
   *
   * @param recipient The message's destination.
   * @param message The call message.
   * @throws IOException
   */
  public void sendCallMessage(SignalServiceAddress recipient,
                              Optional<UnidentifiedAccessPair> unidentifiedAccess,
                              SignalServiceCallMessage message)
      throws IOException, UntrustedIdentityException
  {
    byte[] content = createCallContent(message);
    boolean useFallbackEncryption = SessionManagementProtocol.shared.shouldMessageUseFallbackEncryption(message, recipient.getNumber(), store);
    sendMessage(recipient, getTargetUnidentifiedAccess(unidentifiedAccess), System.currentTimeMillis(), content, false, message.getTTL(), useFallbackEncryption, false);
  }

  /**
   * Send a message to a single recipient.
   *
   * @param recipient The message's destination.
   * @param message The message.
   * @throws UntrustedIdentityException
   * @throws IOException
   */
  public SendMessageResult sendMessage(long                             messageID,
                                       SignalServiceAddress             recipient,
                                       Optional<UnidentifiedAccessPair> unidentifiedAccess,
                                       SignalServiceDataMessage         message)
      throws UntrustedIdentityException, IOException
  {
    byte[]            content               = createMessageContent(message, recipient);
    long              timestamp             = message.getTimestamp();
    boolean           useFallbackEncryption = SessionManagementProtocol.shared.shouldMessageUseFallbackEncryption(message, recipient.getNumber(), store);
    boolean           isClosedGroup         = message.group.isPresent() && message.group.get().getGroupType() == SignalServiceGroup.GroupType.SIGNAL;
    SendMessageResult result                = sendMessage(messageID, recipient, getTargetUnidentifiedAccess(unidentifiedAccess), timestamp, content, false, message.getTTL(), message.getDeviceLink().isPresent(), useFallbackEncryption, isClosedGroup, false, message.hasVisibleContent());

//    // Loki - This shouldn't get invoked for note to self
//    boolean wouldSignalSendSyncMessage = (result.getSuccess() != null && result.getSuccess().isNeedsSync()) || unidentifiedAccess.isPresent();
//    if (wouldSignalSendSyncMessage && SyncMessagesProtocol.shared.shouldSyncMessage(message)) {
//      byte[] syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.of(recipient), timestamp, Collections.singletonList(result));
//      // Loki - Customize multi device logic
//      Set<String> linkedDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(userPublicKey);
//      for (String device : linkedDevices) {
//        SignalServiceAddress deviceAsAddress = new SignalServiceAddress(device);
//        boolean useFallbackEncryptionForSyncMessage = SessionManagementProtocol.shared.shouldMessageUseFallbackEncryption(syncMessage, device, store);
//        sendMessage(deviceAsAddress, Optional.<UnidentifiedAccess>absent(), timestamp, syncMessage, false, message.getTTL(), useFallbackEncryptionForSyncMessage, true);
//      }
//    }

    // Loki - Start a session reset if needed
    if (message.isEndSession()) {
      SessionManagementProtocol.shared.setSessionResetStatusToInProgressIfNeeded(recipient, eventListener);
    }

    return result;
  }

  /**
   * Send a message to a group.
   *
   * @param recipients The group members.
   * @param message The group message.
   * @throws IOException
   */
  public List<SendMessageResult> sendMessage(long                                   messageID,
                                             List<SignalServiceAddress>             recipients,
                                             List<Optional<UnidentifiedAccessPair>> unidentifiedAccess,
                                             SignalServiceDataMessage               message)
      throws IOException, UntrustedIdentityException
  {
    // Loki - We only need the first recipient in the line below. This is because the recipient is only used to determine
    // whether an attachment is being sent to an open group or not.
    byte[]                  content            = createMessageContent(message, recipients.get(0));
    long                    timestamp          = message.getTimestamp();
    boolean                 isClosedGroup      = message.group.isPresent() && message.group.get().getGroupType() == SignalServiceGroup.GroupType.SIGNAL;
    List<SendMessageResult> results            = sendMessage(messageID, recipients, getTargetUnidentifiedAccess(unidentifiedAccess), timestamp, content, false, message.getTTL(), isClosedGroup, message.hasVisibleContent());
    boolean                 needsSyncInResults = false;

    for (SendMessageResult result : results) {
      if (result.getSuccess() != null && result.getSuccess().isNeedsSync()) {
        needsSyncInResults = true;
        break;
      }
    }

    // Loki - This shouldn't get invoked for note to self
    if (needsSyncInResults && SyncMessagesProtocol.shared.shouldSyncMessage(message)) {
      byte[] syncMessage = createMultiDeviceSentTranscriptContent(content, Optional.<SignalServiceAddress>absent(), timestamp, results);
      // Loki - Customize multi device logic
      Set<String> linkedDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(userPublicKey);
      for (String device : linkedDevices) {
        SignalServiceAddress deviceAsAddress = new SignalServiceAddress(device);
        boolean useFallbackEncryption = SessionManagementProtocol.shared.shouldMessageUseFallbackEncryption(syncMessage, device, store);
        sendMessage(deviceAsAddress, Optional.<UnidentifiedAccess>absent(), timestamp, syncMessage, false, message.getTTL(), useFallbackEncryption, true);
      }
    }

    return results;
  }

  public void sendMessage(SignalServiceSyncMessage message, Optional<UnidentifiedAccessPair> unidentifiedAccess)
      throws IOException, UntrustedIdentityException
  {
    byte[] content;
    long timestamp = System.currentTimeMillis();

    if (message.getContacts().isPresent()) {
      content = createMultiDeviceContactsContent(message.getContacts().get().getContactsStream().asStream(), message.getContacts().get().isComplete());
    } else if (message.getGroups().isPresent()) {
      content = createMultiDeviceGroupsContent(message.getGroups().get().asStream());
    } else if (message.getOpenGroups().isPresent()) {
      content = createMultiDeviceOpenGroupsContent(message.getOpenGroups().get());
    } else if (message.getRead().isPresent()) {
      content = createMultiDeviceReadContent(message.getRead().get());
    } else if (message.getBlockedList().isPresent()) {
      content = createMultiDeviceBlockedContent(message.getBlockedList().get());
    } else if (message.getConfiguration().isPresent()) {
      content = createMultiDeviceConfigurationContent(message.getConfiguration().get());
    } else if (message.getSent().isPresent()) {
      content = createMultiDeviceSentTranscriptContent(message.getSent().get(), unidentifiedAccess);
      timestamp = message.getSent().get().getTimestamp();
    } else if (message.getStickerPackOperations().isPresent()) {
      content = createMultiDeviceStickerPackOperationContent(message.getStickerPackOperations().get());
    } else if (message.getVerified().isPresent()) {
      sendMessage(message.getVerified().get(), unidentifiedAccess);
      return;
    } else {
      throw new IOException("Unsupported sync message!");
    }

    // Loki - Customize multi device logic
    Set<String> linkedDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(userPublicKey);
    for (String device : linkedDevices) {
      SignalServiceAddress deviceAsAddress = new SignalServiceAddress(device);
      boolean useFallbackEncryption = SessionManagementProtocol.shared.shouldMessageUseFallbackEncryption(message, device, store);
      sendMessageToPrivateChat(0, deviceAsAddress, Optional.<UnidentifiedAccess>absent(), timestamp, content, false, message.getTTL(), useFallbackEncryption, false, false);
    }
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    socket.setSoTimeoutMillis(soTimeoutMillis);
  }

  public void cancelInFlightRequests() {
    socket.cancelInFlightRequests();
  }

  public void setMessagePipe(SignalServiceMessagePipe pipe, SignalServiceMessagePipe unidentifiedPipe) {
    this.pipe.set(Optional.fromNullable(pipe));
    this.unidentifiedPipe.set(Optional.fromNullable(unidentifiedPipe));
  }

  public void setIsMultiDevice(boolean isMultiDevice) {
    this.isMultiDevice.set(isMultiDevice);
  }

  public SignalServiceAttachmentPointer uploadAttachment(SignalServiceAttachmentStream attachment, boolean usePadding, @Nullable SignalServiceAddress recipient)
      throws IOException
  {
    boolean shouldEncrypt = true;
    String server = FileServerAPI.shared.getServer();

    // Loki - Check if we are sending to an open group
    if (recipient != null) {
      long threadID = threadDatabase.getThreadID(recipient.getNumber());
      PublicChat publicChat = threadDatabase.getPublicChat(threadID);
      if (publicChat != null) {
        shouldEncrypt = false;
        server = publicChat.getServer();
      }
    }

    byte[]             attachmentKey    = Util.getSecretBytes(64);
    long               paddedLength     = usePadding ? PaddingInputStream.getPaddedSize(attachment.getLength()) : attachment.getLength();
    InputStream        dataStream       = usePadding ? new PaddingInputStream(attachment.getInputStream(), attachment.getLength()) : attachment.getInputStream();
    long               ciphertextLength = shouldEncrypt ? AttachmentCipherOutputStream.getCiphertextLength(paddedLength) : attachment.getLength();

    OutputStreamFactory outputStreamFactory = shouldEncrypt ? new AttachmentCipherOutputStreamFactory(attachmentKey) : new PlaintextOutputStreamFactory();
    PushAttachmentData  attachmentData      = new PushAttachmentData(attachment.getContentType(), dataStream, ciphertextLength, outputStreamFactory, attachment.getListener());

    // Loki - Upload attachment
    LokiDotNetAPI.UploadResult result = FileServerAPI.shared.uploadAttachment(server, attachmentData);
    return new SignalServiceAttachmentPointer(result.getId(),
                                              attachment.getContentType(),
                                              attachmentKey,
                                              Optional.of(Util.toIntExact(attachment.getLength())),
                                              attachment.getPreview(),
                                              attachment.getWidth(), attachment.getHeight(),
                                              Optional.fromNullable(result.getDigest()),
                                              attachment.getFileName(),
                                              attachment.getVoiceNote(),
                                              attachment.getCaption(),
                                              result.getUrl());
  }

  private void sendMessage(VerifiedMessage message, Optional<UnidentifiedAccessPair> unidentifiedAccess)
      throws IOException, UntrustedIdentityException
  {

  }

  private byte[] createTypingContent(SignalServiceTypingMessage message) {
    Content.Builder       container = Content.newBuilder();
    TypingMessage.Builder builder   = TypingMessage.newBuilder();

    builder.setTimestamp(message.getTimestamp());

    if      (message.isTypingStarted()) builder.setAction(TypingMessage.Action.STARTED);
    else if (message.isTypingStopped()) builder.setAction(TypingMessage.Action.STOPPED);
    else                                throw new IllegalArgumentException("Unknown typing indicator");

    if (message.getGroupId().isPresent()) {
      builder.setGroupId(ByteString.copyFrom(message.getGroupId().get()));
    }

    return container.setTypingMessage(builder).build().toByteArray();
  }

  private byte[] createReceiptContent(SignalServiceReceiptMessage message) {
    Content.Builder        container = Content.newBuilder();
    ReceiptMessage.Builder builder   = ReceiptMessage.newBuilder();

    for (long timestamp : message.getTimestamps()) {
      builder.addTimestamp(timestamp);
    }

    if      (message.isDeliveryReceipt()) builder.setType(ReceiptMessage.Type.DELIVERY);
    else if (message.isReadReceipt())     builder.setType(ReceiptMessage.Type.READ);

    return container.setReceiptMessage(builder).build().toByteArray();
  }

  private byte[] createMessageContent(SignalServiceDataMessage message, SignalServiceAddress recipient)
      throws IOException
  {
    Content.Builder container = Content.newBuilder();

//    if (message.getPreKeyBundle().isPresent()) {
//      PreKeyBundle preKeyBundle = message.getPreKeyBundle().get();
//      PreKeyBundleMessage.Builder preKeyBundleMessageBuilder = PreKeyBundleMessage.newBuilder()
//          .setDeviceId(preKeyBundle.getDeviceId())
//          .setIdentityKey(ByteString.copyFrom(preKeyBundle.getIdentityKey().serialize()))
//          .setPreKeyId(preKeyBundle.getPreKeyId())
//          .setPreKey(ByteString.copyFrom(preKeyBundle.getPreKey().serialize()))
//          .setSignedKeyId(preKeyBundle.getSignedPreKeyId())
//          .setSignedKey(ByteString.copyFrom(preKeyBundle.getSignedPreKey().serialize()))
//          .setSignature(ByteString.copyFrom(preKeyBundle.getSignedPreKeySignature()))
//          .setIdentityKey(ByteString.copyFrom(preKeyBundle.getIdentityKey().serialize()));
//      container.setPreKeyBundleMessage(preKeyBundleMessageBuilder);
//    }

//    if (message.getDeviceLink().isPresent()) {
//      DeviceLink deviceLink = message.getDeviceLink().get();
//      SignalServiceProtos.DeviceLinkMessage.Builder deviceLinkMessageBuilder = SignalServiceProtos.DeviceLinkMessage.newBuilder()
//          .setPrimaryPublicKey(deviceLink.getMasterPublicKey())
//          .setSecondaryPublicKey(deviceLink.getSlavePublicKey())
//          .setRequestSignature(ByteString.copyFrom(Objects.requireNonNull(deviceLink.getRequestSignature())));
//      if (deviceLink.getAuthorizationSignature() != null) {
//          deviceLinkMessageBuilder.setAuthorizationSignature(ByteString.copyFrom(deviceLink.getAuthorizationSignature()));
//      }
//      container.setDeviceLinkMessage(deviceLinkMessageBuilder.build());
//    }

    DataMessage.Builder builder = DataMessage.newBuilder();
    List<AttachmentPointer> pointers = createAttachmentPointers(message.getAttachments(), recipient);

    if (!pointers.isEmpty()) {
      builder.addAllAttachments(pointers);
    }

    if (message.getBody().isPresent()) {
      builder.setBody(message.getBody().get());
    }

    if (message.getGroupInfo().isPresent()) {
      builder.setGroup(createGroupContent(message.getGroupInfo().get(), recipient));
    }

    if (message.isEndSession()) {
      builder.setFlags(DataMessage.Flags.END_SESSION_VALUE);
    }

    if (message.isExpirationUpdate()) {
      builder.setFlags(DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE);
    }

    if (message.isProfileKeyUpdate()) {
      builder.setFlags(DataMessage.Flags.PROFILE_KEY_UPDATE_VALUE);
    }

    if (message.isDeviceUnlinkingRequest()) {
      builder.setFlags(DataMessage.Flags.DEVICE_UNLINKING_REQUEST_VALUE);
    }

    if (message.getExpiresInSeconds() > 0) {
      builder.setExpireTimer(message.getExpiresInSeconds());
    }

    if (message.getProfileKey().isPresent()) {
      builder.setProfileKey(ByteString.copyFrom(message.getProfileKey().get()));
    }

    if (message.getQuote().isPresent()) {
      DataMessage.Quote.Builder quoteBuilder = DataMessage.Quote.newBuilder()
          .setId(message.getQuote().get().getId())
          .setAuthor(message.getQuote().get().getAuthor().getNumber())
          .setText(message.getQuote().get().getText());

      for (SignalServiceDataMessage.Quote.QuotedAttachment attachment : message.getQuote().get().getAttachments()) {
        DataMessage.Quote.QuotedAttachment.Builder quotedAttachment = DataMessage.Quote.QuotedAttachment.newBuilder();

        quotedAttachment.setContentType(attachment.getContentType());

        if (attachment.getFileName() != null) {
          quotedAttachment.setFileName(attachment.getFileName());
        }

        if (attachment.getThumbnail() != null) {
          quotedAttachment.setThumbnail(createAttachmentPointer(attachment.getThumbnail().asStream(), recipient));
        }

        quoteBuilder.addAttachments(quotedAttachment);
      }

      builder.setQuote(quoteBuilder);
    }

    if (message.getSharedContacts().isPresent()) {
      builder.addAllContact(createSharedContactContent(message.getSharedContacts().get(), recipient));
    }

    if (message.getPreviews().isPresent()) {
      for (SignalServiceDataMessage.Preview preview : message.getPreviews().get()) {
        DataMessage.Preview.Builder previewBuilder = DataMessage.Preview.newBuilder();
        previewBuilder.setTitle(preview.getTitle());
        previewBuilder.setUrl(preview.getUrl());

        if (preview.getImage().isPresent()) {
          if (preview.getImage().get().isStream()) {
            previewBuilder.setImage(createAttachmentPointer(preview.getImage().get().asStream(), recipient));
          } else {
            previewBuilder.setImage(createAttachmentPointer(preview.getImage().get().asPointer()));
          }
        }

        builder.addPreview(previewBuilder.build());
      }
    }

    if (message.getSticker().isPresent()) {
      DataMessage.Sticker.Builder stickerBuilder = DataMessage.Sticker.newBuilder();

      stickerBuilder.setPackId(ByteString.copyFrom(message.getSticker().get().getPackId()));
      stickerBuilder.setPackKey(ByteString.copyFrom(message.getSticker().get().getPackKey()));
      stickerBuilder.setStickerId(message.getSticker().get().getStickerId());

      if (message.getSticker().get().getAttachment().isStream()) {
        stickerBuilder.setData(createAttachmentPointer(message.getSticker().get().getAttachment().asStream(), true, recipient));
      } else {
        stickerBuilder.setData(createAttachmentPointer(message.getSticker().get().getAttachment().asPointer()));
      }

      builder.setSticker(stickerBuilder.build());
    }

    LokiUserProfile.Builder lokiUserProfileBuilder = LokiUserProfile.newBuilder();
    String displayName = userDatabase.getDisplayName(userPublicKey);
    if (displayName != null) { lokiUserProfileBuilder.setDisplayName(displayName); }
    String profilePictureURL = userDatabase.getProfilePictureURL(userPublicKey);
    if (profilePictureURL != null) { lokiUserProfileBuilder.setProfilePictureURL(profilePictureURL); }
    builder.setProfile(lokiUserProfileBuilder.build());

    builder.setTimestamp(message.getTimestamp());

    container.setDataMessage(builder);

    return container.build().toByteArray();
  }

  private byte[] createCallContent(SignalServiceCallMessage callMessage) {
    Content.Builder     container = Content.newBuilder();
    CallMessage.Builder builder   = CallMessage.newBuilder();

    if (callMessage.getOfferMessage().isPresent()) {
      OfferMessage offer = callMessage.getOfferMessage().get();
      builder.setOffer(CallMessage.Offer.newBuilder()
                                        .setId(offer.getId())
                                        .setDescription(offer.getDescription()));
    } else if (callMessage.getAnswerMessage().isPresent()) {
      AnswerMessage answer = callMessage.getAnswerMessage().get();
      builder.setAnswer(CallMessage.Answer.newBuilder()
                                          .setId(answer.getId())
                                          .setDescription(answer.getDescription()));
    } else if (callMessage.getIceUpdateMessages().isPresent()) {
      List<IceUpdateMessage> updates = callMessage.getIceUpdateMessages().get();

      for (IceUpdateMessage update : updates) {
        builder.addIceUpdate(CallMessage.IceUpdate.newBuilder()
                                                  .setId(update.getId())
                                                  .setSdp(update.getSdp())
                                                  .setSdpMid(update.getSdpMid())
                                                  .setSdpMLineIndex(update.getSdpMLineIndex()));
      }
    } else if (callMessage.getHangupMessage().isPresent()) {
      builder.setHangup(CallMessage.Hangup.newBuilder().setId(callMessage.getHangupMessage().get().getId()));
    } else if (callMessage.getBusyMessage().isPresent()) {
      builder.setBusy(CallMessage.Busy.newBuilder().setId(callMessage.getBusyMessage().get().getId()));
    }

    container.setCallMessage(builder);
    return container.build().toByteArray();
  }

  private byte[] createMultiDeviceContactsContent(SignalServiceAttachmentStream contacts, boolean complete)
      throws IOException
  {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = createSyncMessageBuilder();
    builder.setContacts(SyncMessage.Contacts.newBuilder()
                                            .setData(ByteString.readFrom(contacts.getInputStream()))
                                            .setComplete(complete));

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceGroupsContent(SignalServiceAttachmentStream groups)
      throws IOException
  {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = createSyncMessageBuilder();
    builder.setGroups(SyncMessage.Groups.newBuilder()
                                        .setData(ByteString.readFrom(groups.getInputStream())));

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceOpenGroupsContent(List<PublicChat> openGroups)
      throws IOException
  {
      Content.Builder     container = Content.newBuilder();
      SyncMessage.Builder builder   = createSyncMessageBuilder();
      for (PublicChat openGroup : openGroups) {
          String url = openGroup.getServer();
          int channel = Long.valueOf(openGroup.getChannel()).intValue();
          SyncMessage.OpenGroupDetails details = SyncMessage.OpenGroupDetails.newBuilder()
                                                                              .setUrl(url)
                                                                              .setChannelID(channel)
                                                                              .build();
          builder.addOpenGroups(details);
      }

      return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceSentTranscriptContent(SentTranscriptMessage transcript, Optional<UnidentifiedAccessPair> unidentifiedAccess)
      throws IOException
  {
    SignalServiceAddress address = new SignalServiceAddress(transcript.getDestination().get());
    SendMessageResult    result  = SendMessageResult.success(address, unidentifiedAccess.isPresent(), true);

    return createMultiDeviceSentTranscriptContent(createMessageContent(transcript.getMessage(), address),
                                                  Optional.of(address),
                                                  transcript.getTimestamp(),
                                                  Collections.singletonList(result));
  }

  private byte[] createMultiDeviceSentTranscriptContent(byte[] content, Optional<SignalServiceAddress> recipient,
    long timestamp, List<SendMessageResult> sendMessageResults)
  {
    try {
      Content.Builder          container   = Content.newBuilder();
      SyncMessage.Builder      syncMessage = createSyncMessageBuilder();
      SyncMessage.Sent.Builder sentMessage = SyncMessage.Sent.newBuilder();
      DataMessage              dataMessage = Content.parseFrom(content).getDataMessage();

      sentMessage.setTimestamp(timestamp);
      sentMessage.setMessage(dataMessage);

      for (SendMessageResult result : sendMessageResults) {
        if (result.getSuccess() != null) {
          sentMessage.addUnidentifiedStatus(SyncMessage.Sent.UnidentifiedDeliveryStatus.newBuilder()
                                                                                       .setDestination(result.getAddress().getNumber())
                                                                                       .setUnidentified(result.getSuccess().isUnidentified()));
        }
      }

      if (recipient.isPresent()) {
        sentMessage.setDestination(recipient.get().getNumber());
      }

      if (dataMessage.getExpireTimer() > 0) {
        sentMessage.setExpirationStartTimestamp(System.currentTimeMillis());
      }

      return container.setSyncMessage(syncMessage.setSent(sentMessage)).build().toByteArray();
    } catch (InvalidProtocolBufferException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] createMultiDeviceReadContent(List<ReadMessage> readMessages) {
    Content.Builder     container = Content.newBuilder();
    SyncMessage.Builder builder   = createSyncMessageBuilder();

    for (ReadMessage readMessage : readMessages) {
      builder.addRead(SyncMessage.Read.newBuilder()
                                      .setTimestamp(readMessage.getTimestamp())
                                      .setSender(readMessage.getSender()));
    }

    return container.setSyncMessage(builder).build().toByteArray();
  }

  private byte[] createMultiDeviceBlockedContent(BlockedListMessage blocked) {
    Content.Builder             container      = Content.newBuilder();
    SyncMessage.Builder         syncMessage    = createSyncMessageBuilder();
    SyncMessage.Blocked.Builder blockedMessage = SyncMessage.Blocked.newBuilder();

    blockedMessage.addAllNumbers(blocked.getNumbers());

    for (byte[] groupId : blocked.getGroupIds()) {
      blockedMessage.addGroupIds(ByteString.copyFrom(groupId));
    }

    return container.setSyncMessage(syncMessage.setBlocked(blockedMessage)).build().toByteArray();
  }

  private byte[] createMultiDeviceConfigurationContent(ConfigurationMessage configuration) {
    Content.Builder                   container            = Content.newBuilder();
    SyncMessage.Builder               syncMessage          = createSyncMessageBuilder();
    SyncMessage.Configuration.Builder configurationMessage = SyncMessage.Configuration.newBuilder();

    if (configuration.getReadReceipts().isPresent()) {
      configurationMessage.setReadReceipts(configuration.getReadReceipts().get());
    }

    if (configuration.getUnidentifiedDeliveryIndicators().isPresent()) {
      configurationMessage.setUnidentifiedDeliveryIndicators(configuration.getUnidentifiedDeliveryIndicators().get());
    }

    if (configuration.getTypingIndicators().isPresent()) {
      configurationMessage.setTypingIndicators(configuration.getTypingIndicators().get());
    }

    if (configuration.getLinkPreviews().isPresent()) {
      configurationMessage.setLinkPreviews(configuration.getLinkPreviews().get());
    }

    return container.setSyncMessage(syncMessage.setConfiguration(configurationMessage)).build().toByteArray();
  }

  private byte[] createMultiDeviceStickerPackOperationContent(List<StickerPackOperationMessage> stickerPackOperations) {
    Content.Builder     container   = Content.newBuilder();
    SyncMessage.Builder syncMessage = createSyncMessageBuilder();

    for (StickerPackOperationMessage stickerPackOperation : stickerPackOperations) {
      SyncMessage.StickerPackOperation.Builder builder = SyncMessage.StickerPackOperation.newBuilder();

      if (stickerPackOperation.getPackId().isPresent()) {
        builder.setPackId(ByteString.copyFrom(stickerPackOperation.getPackId().get()));
      }

      if (stickerPackOperation.getPackKey().isPresent()) {
        builder.setPackKey(ByteString.copyFrom(stickerPackOperation.getPackKey().get()));
      }

      if (stickerPackOperation.getType().isPresent()) {
        switch (stickerPackOperation.getType().get()) {
          case INSTALL: builder.setType(SyncMessage.StickerPackOperation.Type.INSTALL); break;
          case REMOVE:  builder.setType(SyncMessage.StickerPackOperation.Type.REMOVE); break;
        }
      }

      syncMessage.addStickerPackOperation(builder);
    }

    return container.setSyncMessage(syncMessage).build().toByteArray();
  }

  private SyncMessage.Builder createSyncMessageBuilder() {
    SecureRandom random  = new SecureRandom();
    byte[]       padding = Util.getRandomLengthBytes(512);
    random.nextBytes(padding);

    SyncMessage.Builder builder = SyncMessage.newBuilder();
    builder.setPadding(ByteString.copyFrom(padding));

    return builder;
  }

  private GroupContext createGroupContent(SignalServiceGroup group, SignalServiceAddress recipient)
      throws IOException
  {
    GroupContext.Builder builder = GroupContext.newBuilder();
    builder.setId(ByteString.copyFrom(group.getGroupId()));

    if (group.getType() != SignalServiceGroup.Type.DELIVER) {
      if      (group.getType() == SignalServiceGroup.Type.UPDATE)       builder.setType(GroupContext.Type.UPDATE);
      else if (group.getType() == SignalServiceGroup.Type.QUIT)         builder.setType(GroupContext.Type.QUIT);
      else if (group.getType() == SignalServiceGroup.Type.REQUEST_INFO) builder.setType(GroupContext.Type.REQUEST_INFO);
      else                                                              throw new AssertionError("Unknown type: " + group.getType());

      if (group.getName().isPresent()) builder.setName(group.getName().get());
      if (group.getMembers().isPresent()) builder.addAllMembers(group.getMembers().get());
      if (group.getAdmins().isPresent()) builder.addAllAdmins(group.getAdmins().get());

      if (group.getAvatar().isPresent()) {
        if (group.getAvatar().get().isStream()) {
          builder.setAvatar(createAttachmentPointer(group.getAvatar().get().asStream(), recipient));
        } else {
          builder.setAvatar(createAttachmentPointer(group.getAvatar().get().asPointer()));
        }
      }
    } else {
      builder.setType(GroupContext.Type.DELIVER);
    }

    return builder.build();
  }

  private List<DataMessage.Contact> createSharedContactContent(List<SharedContact> contacts, SignalServiceAddress recipient)
      throws IOException
  {
    List<DataMessage.Contact> results = new LinkedList<>();

    for (SharedContact contact : contacts) {
      DataMessage.Contact.Name.Builder nameBuilder = DataMessage.Contact.Name.newBuilder();

      if (contact.getName().getFamily().isPresent())  nameBuilder.setFamilyName(contact.getName().getFamily().get());
      if (contact.getName().getGiven().isPresent())   nameBuilder.setGivenName(contact.getName().getGiven().get());
      if (contact.getName().getMiddle().isPresent())  nameBuilder.setMiddleName(contact.getName().getMiddle().get());
      if (contact.getName().getPrefix().isPresent())  nameBuilder.setPrefix(contact.getName().getPrefix().get());
      if (contact.getName().getSuffix().isPresent())  nameBuilder.setSuffix(contact.getName().getSuffix().get());
      if (contact.getName().getDisplay().isPresent()) nameBuilder.setDisplayName(contact.getName().getDisplay().get());

      DataMessage.Contact.Builder contactBuilder = DataMessage.Contact.newBuilder()
                                                                      .setName(nameBuilder);

      if (contact.getAddress().isPresent()) {
        for (SharedContact.PostalAddress address : contact.getAddress().get()) {
          DataMessage.Contact.PostalAddress.Builder addressBuilder = DataMessage.Contact.PostalAddress.newBuilder();

          switch (address.getType()) {
            case HOME:   addressBuilder.setType(DataMessage.Contact.PostalAddress.Type.HOME); break;
            case WORK:   addressBuilder.setType(DataMessage.Contact.PostalAddress.Type.WORK); break;
            case CUSTOM: addressBuilder.setType(DataMessage.Contact.PostalAddress.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + address.getType());
          }

          if (address.getCity().isPresent())         addressBuilder.setCity(address.getCity().get());
          if (address.getCountry().isPresent())      addressBuilder.setCountry(address.getCountry().get());
          if (address.getLabel().isPresent())        addressBuilder.setLabel(address.getLabel().get());
          if (address.getNeighborhood().isPresent()) addressBuilder.setNeighborhood(address.getNeighborhood().get());
          if (address.getPobox().isPresent())        addressBuilder.setPobox(address.getPobox().get());
          if (address.getPostcode().isPresent())     addressBuilder.setPostcode(address.getPostcode().get());
          if (address.getRegion().isPresent())       addressBuilder.setRegion(address.getRegion().get());
          if (address.getStreet().isPresent())       addressBuilder.setStreet(address.getStreet().get());

          contactBuilder.addAddress(addressBuilder);
        }
      }

      if (contact.getEmail().isPresent()) {
        for (SharedContact.Email email : contact.getEmail().get()) {
          DataMessage.Contact.Email.Builder emailBuilder = DataMessage.Contact.Email.newBuilder()
                                                                                    .setValue(email.getValue());

          switch (email.getType()) {
            case HOME:   emailBuilder.setType(DataMessage.Contact.Email.Type.HOME);   break;
            case WORK:   emailBuilder.setType(DataMessage.Contact.Email.Type.WORK);   break;
            case MOBILE: emailBuilder.setType(DataMessage.Contact.Email.Type.MOBILE); break;
            case CUSTOM: emailBuilder.setType(DataMessage.Contact.Email.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + email.getType());
          }

          if (email.getLabel().isPresent()) emailBuilder.setLabel(email.getLabel().get());

          contactBuilder.addEmail(emailBuilder);
        }
      }

      if (contact.getPhone().isPresent()) {
        for (SharedContact.Phone phone : contact.getPhone().get()) {
          DataMessage.Contact.Phone.Builder phoneBuilder = DataMessage.Contact.Phone.newBuilder()
                                                                                    .setValue(phone.getValue());

          switch (phone.getType()) {
            case HOME:   phoneBuilder.setType(DataMessage.Contact.Phone.Type.HOME);   break;
            case WORK:   phoneBuilder.setType(DataMessage.Contact.Phone.Type.WORK);   break;
            case MOBILE: phoneBuilder.setType(DataMessage.Contact.Phone.Type.MOBILE); break;
            case CUSTOM: phoneBuilder.setType(DataMessage.Contact.Phone.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + phone.getType());
          }

          if (phone.getLabel().isPresent()) phoneBuilder.setLabel(phone.getLabel().get());

          contactBuilder.addNumber(phoneBuilder);
        }
      }

      if (contact.getAvatar().isPresent()) {
        AttachmentPointer pointer = contact.getAvatar().get().getAttachment().isStream() ? createAttachmentPointer(contact.getAvatar().get().getAttachment().asStream(), recipient)
                                                                                         : createAttachmentPointer(contact.getAvatar().get().getAttachment().asPointer());
        contactBuilder.setAvatar(DataMessage.Contact.Avatar.newBuilder()
                                                           .setAvatar(pointer)
                                                           .setIsProfile(contact.getAvatar().get().isProfile()));
      }

      if (contact.getOrganization().isPresent()) {
        contactBuilder.setOrganization(contact.getOrganization().get());
      }

      results.add(contactBuilder.build());
    }

    return results;
  }

  private List<SendMessageResult> sendMessage(long                               messageID,
                                              List<SignalServiceAddress>         recipients,
                                              List<Optional<UnidentifiedAccess>> unidentifiedAccess,
                                              long                               timestamp,
                                              byte[]                             content,
                                              boolean                            online,
                                              int                                ttl,
                                              boolean                            isClosedGroup,
                                              boolean                            notifyPNServer)
      throws IOException
  {
    List<SendMessageResult>                results                    = new LinkedList<>();
    Iterator<SignalServiceAddress>         recipientIterator          = recipients.iterator();
    Iterator<Optional<UnidentifiedAccess>> unidentifiedAccessIterator = unidentifiedAccess.iterator();

    while (recipientIterator.hasNext()) {
      SignalServiceAddress recipient = recipientIterator.next();

      try {
        boolean useFallbackEncryption = SessionManagementProtocol.shared.shouldMessageUseFallbackEncryption(content, recipient.getNumber(), store);
        SendMessageResult result = sendMessage(messageID, recipient, unidentifiedAccessIterator.next(), timestamp, content, online, ttl, false, useFallbackEncryption, isClosedGroup, false, notifyPNServer);
        results.add(result);
      } catch (UnregisteredUserException e) {
        Log.w(TAG, e);
        results.add(SendMessageResult.unregisteredFailure(recipient));
      } catch (PushNetworkException e) {
        Log.w(TAG, e);
        results.add(SendMessageResult.networkFailure(recipient));
      }
    }

    return results;
  }

  private SendMessageResult sendMessage(SignalServiceAddress         recipient,
                                        Optional<UnidentifiedAccess> unidentifiedAccess,
                                        long                         timestamp,
                                        byte[]                       content,
                                        boolean                      online,
                                        int                          ttl,
                                        boolean                      useFallbackEncryption,
                                        boolean                      isSyncMessage)
      throws IOException
  {
    // Loki - This method is only invoked for various types of control messages
    return sendMessage(0, recipient, unidentifiedAccess, timestamp, content, online, ttl, false, false, useFallbackEncryption, isSyncMessage, false);
  }

  public SendMessageResult sendMessage(final long                   messageID,
                                       final SignalServiceAddress   recipient,
                                       Optional<UnidentifiedAccess> unidentifiedAccess,
                                       long                         timestamp,
                                       byte[]                       content,
                                       boolean                      online,
                                       int                          ttl,
                                       boolean                      isDeviceLinkMessage,
                                       boolean                      useFallbackEncryption,
                                       boolean                      isClosedGroup,
                                       boolean                      isSyncMessage,
                                       boolean                      notifyPNServer)
      throws IOException
  {
    long threadID = threadDatabase.getThreadID(recipient.getNumber());
    PublicChat publicChat = threadDatabase.getPublicChat(threadID);
    try {
      if (publicChat != null) {
        return sendMessageToPublicChat(messageID, recipient, timestamp, content, publicChat);
      } else {
        return sendMessageToPrivateChat(messageID, recipient, unidentifiedAccess, timestamp, content, online, ttl, useFallbackEncryption, isClosedGroup, notifyPNServer);
      }
    } catch (PushNetworkException e) {
      return SendMessageResult.networkFailure(recipient);
    } catch (UntrustedIdentityException e) {
      return SendMessageResult.identityFailure(recipient, e.getIdentityKey());
    }
  }

  private SendMessageResult sendMessageToPublicChat(final long                 messageID,
                                                    final SignalServiceAddress recipient,
                                                    long                       timestamp,
                                                    byte[]                     content,
                                                    PublicChat publicChat) {
    if (messageID == 0) {
      Log.d("Loki", "Missing message ID.");
    }
    final SettableFuture<?>[] future = { new SettableFuture<Unit>() };
    try {
      DataMessage data = Content.parseFrom(content).getDataMessage();
      String body = (data.getBody() != null && data.getBody().length() > 0) ? data.getBody() : Long.toString(data.getTimestamp());
      PublicChatMessage.Quote quote = null;
      if (data.hasQuote()) {
        long quoteID = data.getQuote().getId();
        String quoteePublicKey = data.getQuote().getAuthor();
        long serverID = messageDatabase.getQuoteServerID(quoteID, quoteePublicKey);
        quote = new PublicChatMessage.Quote(quoteID, quoteePublicKey, data.getQuote().getText(), serverID);
      }
      DataMessage.Preview linkPreview = (data.getPreviewList().size() > 0) ? data.getPreviewList().get(0) : null;
      ArrayList<PublicChatMessage.Attachment> attachments = new ArrayList<>();
      if (linkPreview != null && linkPreview.hasImage()) {
        AttachmentPointer attachmentPointer = linkPreview.getImage();
        String caption = attachmentPointer.hasCaption() ? attachmentPointer.getCaption() : null;
        attachments.add(new PublicChatMessage.Attachment(
            PublicChatMessage.Attachment.Kind.LinkPreview,
            publicChat.getServer(),
            attachmentPointer.getId(),
            attachmentPointer.getContentType(),
            attachmentPointer.getSize(),
            attachmentPointer.getFileName(),
            attachmentPointer.getFlags(),
            attachmentPointer.getWidth(),
            attachmentPointer.getHeight(),
            caption,
            attachmentPointer.getUrl(),
            linkPreview.getUrl(),
            linkPreview.getTitle()
        ));
      }
      for (AttachmentPointer attachmentPointer : data.getAttachmentsList()) {
        String caption = attachmentPointer.hasCaption() ? attachmentPointer.getCaption() : null;
        attachments.add(new PublicChatMessage.Attachment(
            PublicChatMessage.Attachment.Kind.Attachment,
            publicChat.getServer(),
            attachmentPointer.getId(),
            attachmentPointer.getContentType(),
            attachmentPointer.getSize(),
            attachmentPointer.getFileName(),
            attachmentPointer.getFlags(),
            attachmentPointer.getWidth(),
            attachmentPointer.getHeight(),
            caption,
            attachmentPointer.getUrl(),
            null,
            null
        ));
      }
      PublicChatMessage message = new PublicChatMessage(userPublicKey, "", body, timestamp, PublicChatAPI.getPublicChatMessageType(), quote, attachments);
      byte[] privateKey = store.getIdentityKeyPair().getPrivateKey().serialize();
      new PublicChatAPI(userPublicKey, privateKey, apiDatabase, userDatabase, openGroupDatabase).sendMessage(message, publicChat.getChannel(), publicChat.getServer()).success(new Function1<PublicChatMessage, Unit>() {

        @Override
        public Unit invoke(PublicChatMessage message) {
          @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
          messageDatabase.setServerID(messageID, message.getServerID());
          f.set(Unit.INSTANCE);
          return Unit.INSTANCE;
        }
      }).fail(new Function1<Exception, Unit>() {

        @Override
        public Unit invoke(Exception exception) {
          @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
          f.setException(exception);
          return Unit.INSTANCE;
        }
      });
    } catch (Exception exception) {
      @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
      f.setException(exception);
    }
    @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
    try {
      f.get(1, TimeUnit.MINUTES);
      return SendMessageResult.success(recipient, false, false);
    } catch (Exception exception) {
      return SendMessageResult.networkFailure(recipient);
    }
  }

  private SendMessageResult sendMessageToPrivateChat(final long                   messageID,
                                                     final SignalServiceAddress   recipient,
                                                     Optional<UnidentifiedAccess> unidentifiedAccess,
                                                     final long                   timestamp,
                                                     byte[]                       content,
                                                     boolean                      online,
                                                     int                          ttl,
                                                     boolean                      useFallbackEncryption,
                                                     boolean                      isClosedGroup,
                                                     final boolean                notifyPNServer)
      throws IOException, UntrustedIdentityException
  {
    if (recipient.getNumber().equals(userPublicKey)) { return SendMessageResult.success(recipient, false, false); }
    final SettableFuture<?>[] future = { new SettableFuture<Unit>() };
    try {
      OutgoingPushMessageList messages = getEncryptedMessages(socket, recipient, unidentifiedAccess, timestamp, content, online, useFallbackEncryption, isClosedGroup);
      // Loki - Remove this when we have shared sender keys
      // ========
      if (messages.getMessages().isEmpty()) {
          return SendMessageResult.success(recipient, false, false);
      }
      // ========
      Set<String> userLinkedDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(userPublicKey);
      if (sskDatabase.isSSKBasedClosedGroup(recipient.getNumber())) {
        Log.d("Loki", "Sending message to closed group.")  ;
      } else if (recipient.getNumber().equals(userPublicKey)) {
        Log.d("Loki", "Sending message to self.");
      } else if (userLinkedDevices.contains(recipient.getNumber())) {
        Log.d("Loki", "Sending message to linked device.");
      } else {
        Log.d("Loki", "Sending message to " + recipient.getNumber() + ".");
      }
      OutgoingPushMessage message = messages.getMessages().get(0);
      final SignalServiceProtos.Envelope.Type type = SignalServiceProtos.Envelope.Type.valueOf(message.type);
      final String senderID;
      if (type == SignalServiceProtos.Envelope.Type.CLOSED_GROUP_CIPHERTEXT) {
          senderID = recipient.getNumber();
//      } else if (type == SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER) {
//          senderID = "";
      } else {
          senderID = userPublicKey;
      }
      final int senderDeviceID = (type == SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER) ? 0 : SignalServiceAddress.DEFAULT_DEVICE_ID;
      // Make sure we have a valid ttl; otherwise default to 2 days
      if (ttl <= 0) { ttl = TTLUtilities.INSTANCE.getFallbackMessageTTL(); }
      final int regularMessageTTL = TTLUtilities.getTTL(TTLUtilities.MessageType.Regular);
      final int __ttl = ttl;
      final SignalMessageInfo messageInfo = new SignalMessageInfo(type, timestamp, senderID, senderDeviceID, message.content, recipient.getNumber(), ttl, false);
      SnodeAPI.shared.sendSignalMessage(messageInfo).success(new Function1<Set<Promise<Map<?, ?>, Exception>>, Unit>() {

        @Override
        public Unit invoke(Set<Promise<Map<?, ?>, Exception>> promises) {
          final boolean[] isSuccess = { false };
          final int[] promiseCount = {promises.size()};
          final int[] errorCount = { 0 };
          for (Promise<Map<?, ?>, Exception> promise : promises) {
            promise.success(new Function1<Map<?, ?>, Unit>() {

              @Override
              public Unit invoke(Map<?, ?> map) {
                if (isSuccess[0]) { return Unit.INSTANCE; } // Succeed as soon as the first promise succeeds
                if (__ttl == regularMessageTTL) {
                  broadcaster.broadcast("messageSent", timestamp);
                }
                isSuccess[0] = true;
                if (notifyPNServer) {
                    PushNotificationAPI.shared.notify(messageInfo);
                }
                @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
                f.set(Unit.INSTANCE);
                return Unit.INSTANCE;
              }
            }).fail(new Function1<Exception, Unit>() {

              @Override
              public Unit invoke(Exception exception) {
                errorCount[0] += 1;
                if (errorCount[0] != promiseCount[0]) { return Unit.INSTANCE; } // Only error out if all promises failed
                if (__ttl == regularMessageTTL) {
                    broadcaster.broadcast("messageFailed", timestamp);
                }
                @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
                f.setException(exception);
                return Unit.INSTANCE;
              }
            });
          }
          return Unit.INSTANCE;
        }
      }).fail(new Function1<Exception, Unit>() {

        @Override
        public Unit invoke(Exception exception) {
          @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
          f.setException(exception);
          return Unit.INSTANCE;
        }
      });
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
    @SuppressWarnings("unchecked") SettableFuture<Unit> f = (SettableFuture<Unit>)future[0];
    try {
      f.get(1, TimeUnit.MINUTES);
      return SendMessageResult.success(recipient, false, true);
    } catch (Exception exception) {
      Throwable underlyingError = exception.getCause();
      if (underlyingError instanceof SnodeAPI.Error) {
        return SendMessageResult.lokiAPIError(recipient, (SnodeAPI.Error)underlyingError);
      } else {
        return SendMessageResult.networkFailure(recipient);
      }
    }
  }

  private List<AttachmentPointer> createAttachmentPointers(Optional<List<SignalServiceAttachment>> attachments, SignalServiceAddress recipient)
      throws IOException
  {
    List<AttachmentPointer> pointers = new LinkedList<>();

    if (!attachments.isPresent() || attachments.get().isEmpty()) {
      Log.w(TAG, "No attachments present...");
      return pointers;
    }

    for (SignalServiceAttachment attachment : attachments.get()) {
      if (attachment.isStream()) {
        Log.w(TAG, "Found attachment, creating pointer...");
        pointers.add(createAttachmentPointer(attachment.asStream(), recipient));
      } else if (attachment.isPointer()) {
        Log.w(TAG, "Including existing attachment pointer...");
        pointers.add(createAttachmentPointer(attachment.asPointer()));
      }
    }

    return pointers;
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentPointer attachment) {
    AttachmentPointer.Builder builder = AttachmentPointer.newBuilder()
                                                         .setContentType(attachment.getContentType())
                                                         .setId(attachment.getId())
                                                         .setKey(ByteString.copyFrom(attachment.getKey()))
                                                         .setDigest(ByteString.copyFrom(attachment.getDigest().get()))
                                                         .setSize(attachment.getSize().get())
                                                         .setUrl(attachment.getUrl());

    if (attachment.getFileName().isPresent()) {
      builder.setFileName(attachment.getFileName().get());
    }

    if (attachment.getPreview().isPresent()) {
      builder.setThumbnail(ByteString.copyFrom(attachment.getPreview().get()));
    }

    if (attachment.getWidth() > 0) {
      builder.setWidth(attachment.getWidth());
    }

    if (attachment.getHeight() > 0) {
      builder.setHeight(attachment.getHeight());
    }

    if (attachment.getVoiceNote()) {
      builder.setFlags(AttachmentPointer.Flags.VOICE_MESSAGE_VALUE);
    }

    if (attachment.getCaption().isPresent()) {
      builder.setCaption(attachment.getCaption().get());
    }

    return builder.build();
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentStream attachment)
      throws IOException
  {
    return createAttachmentPointer(attachment, false, null);
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentStream attachment, SignalServiceAddress recipient)
      throws IOException
  {
    return createAttachmentPointer(attachment, false, recipient);
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentStream attachment, boolean usePadding, SignalServiceAddress recipient)
      throws IOException
  {
    SignalServiceAttachmentPointer pointer = uploadAttachment(attachment, usePadding, recipient);
    return createAttachmentPointer(pointer);
  }

  private OutgoingPushMessageList getEncryptedMessages(PushServiceSocket            socket,
                                                       SignalServiceAddress         recipient,
                                                       Optional<UnidentifiedAccess> unidentifiedAccess,
                                                       long                         timestamp,
                                                       byte[]                       plaintext,
                                                       boolean                      online,
                                                       boolean                      useFallbackEncryption,
                                                       boolean                      isClosedGroup)
      throws IOException, InvalidKeyException, UntrustedIdentityException
  {
    List<OutgoingPushMessage> messages = new LinkedList<>();

    // Loki - The way this works is:
    // • Alice sends a session request (i.e. a pre key bundle) to Bob using fallback encryption.
    // • She may send any number of subsequent messages also encrypted using fallback encryption.
    // • When Bob receives the session request, he sets up his Signal cipher session locally and sends back a null message,
    //   now encrypted using Signal encryption.
    // • Alice receives this, sets up her Signal cipher session locally, and sends any subsequent messages
    //   using Signal encryption.

    if (!recipient.equals(localAddress) || unidentifiedAccess.isPresent()) {
      if (sskDatabase.isSSKBasedClosedGroup(recipient.getNumber()) && unidentifiedAccess.isPresent()) {
        messages.add(getSSKEncryptedMessage(recipient.getNumber(), unidentifiedAccess.get(), plaintext));
//      } else if (useFallbackEncryption) {
//        messages.add(getFallbackCipherEncryptedMessage(recipient.getNumber(), plaintext, unidentifiedAccess));
//      } else {
//        OutgoingPushMessage message = getEncryptedMessage(socket, recipient, unidentifiedAccess, plaintext, isClosedGroup);
//        if (message != null) { // May be null in a closed group context
//          messages.add(message);
//        }
//      }
      } else {
        OutgoingPushMessage message = getFallbackCipherEncryptedMessage(recipient.getNumber(), plaintext, unidentifiedAccess);
        messages.add(message);
      }
    }

    return new OutgoingPushMessageList(recipient.getNumber(), timestamp, messages, online);
  }

  private OutgoingPushMessage getFallbackCipherEncryptedMessage(String publicKey, byte[] plaintext, Optional<UnidentifiedAccess> unidentifiedAccess)
      throws InvalidKeyException
  {
    Log.d("Loki", "Using fallback cipher.");
    int deviceID = SignalServiceAddress.DEFAULT_DEVICE_ID;
//    SignalProtocolAddress signalProtocolAddress = new SignalProtocolAddress(publicKey, deviceID);
//    byte[] userPrivateKey = store.getIdentityKeyPair().getPrivateKey().serialize();
//    FallbackSessionCipher cipher = new FallbackSessionCipher(userPrivateKey, publicKey);
    PushTransportDetails transportDetails = new PushTransportDetails(FallbackSessionCipher.getSessionVersion());
//    byte[] bytes = cipher.encrypt(transportDetails.getPaddedMessageBody(plaintext));
    byte[] bytes = transportDetails.getPaddedMessageBody(plaintext);
//    if (bytes == null) { bytes = new byte[0]; }
    if (unidentifiedAccess.isPresent()) {
//      SealedSessionCipher sealedSessionCipher = new SealedSessionCipher(store, null, signalProtocolAddress);
//      FallbackMessage message = new FallbackMessage(bytes);
//      byte[] ciphertext = sealedSessionCipher.encrypt(signalProtocolAddress, unidentifiedAccess.get().getUnidentifiedCertificate(), message);
//      return new OutgoingPushMessage(SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER_VALUE, deviceID, 0, Base64.encodeBytes(ciphertext));
      return new OutgoingPushMessage(SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER_VALUE, deviceID, 0, Base64.encodeBytes(bytes));
    } else {
      return new OutgoingPushMessage(SignalServiceProtos.Envelope.Type.FALLBACK_MESSAGE_VALUE, deviceID, 0, Base64.encodeBytes(bytes));
    }
  }

  private OutgoingPushMessage getSSKEncryptedMessage(String groupPublicKey, UnidentifiedAccess unidentifiedAccess, byte[] plaintext)
      throws InvalidKeyException, UntrustedIdentityException, IOException
  {
      int deviceID = SignalServiceAddress.DEFAULT_DEVICE_ID;
      SignalProtocolAddress signalProtocolAddress = new SignalProtocolAddress(groupPublicKey, deviceID);
      SignalServiceCipher cipher = new SignalServiceCipher(localAddress, store, sskDatabase, sessionResetImpl, null);
      try {
          return cipher.encrypt(signalProtocolAddress, Optional.of(unidentifiedAccess), plaintext);
      } catch (org.session.libsignal.libsignal.UntrustedIdentityException e) {
          throw new UntrustedIdentityException("Untrusted identity", groupPublicKey, e.getUntrustedIdentity());
      }
  }

  private OutgoingPushMessage getEncryptedMessage(PushServiceSocket            socket,
                                                  SignalServiceAddress         recipient,
                                                  Optional<UnidentifiedAccess> unidentifiedAccess,
                                                  byte[]                       plaintext,
                                                  boolean                      isClosedGroup)
      throws IOException, InvalidKeyException, UntrustedIdentityException
  {
    Log.d("Loki", "Using Signal cipher.");
    int deviceID = SignalServiceAddress.DEFAULT_DEVICE_ID;
    SignalProtocolAddress signalProtocolAddress = new SignalProtocolAddress(recipient.getNumber(), deviceID);
    SignalServiceCipher cipher = new SignalServiceCipher(localAddress, store, sskDatabase, sessionResetImpl, null);

    try {
      String contactPublicKey = recipient.getNumber();
      PreKeyBundle preKeyBundle = preKeyBundleDatabase.getPreKeyBundle(contactPublicKey);
      if (preKeyBundle == null) {
        if (!store.containsSession(signalProtocolAddress)) {
          SessionManagementProtocol.shared.repairSessionIfNeeded(recipient, isClosedGroup);
          // Loki - Remove this when we have shared sender keys
          // ========
          if (SessionManagementProtocol.shared.shouldIgnoreMissingPreKeyBundleException(isClosedGroup)) {
            return null;
          }
          // ========
          throw new InvalidKeyException("Pre key bundle not found for: " + recipient.getNumber() + ".");
        }
      } else {
        try {
          SignalProtocolAddress address = new SignalProtocolAddress(contactPublicKey, preKeyBundle.getDeviceId());
          SessionBuilder sessionBuilder = new SessionBuilder(store, address);
          sessionBuilder.process(preKeyBundle);
          // Loki - Discard the pre key bundle once the session has been established
          preKeyBundleDatabase.removePreKeyBundle(contactPublicKey);
        } catch (org.session.libsignal.libsignal.UntrustedIdentityException e) {
          throw new UntrustedIdentityException("Untrusted identity key", recipient.getNumber(), preKeyBundle.getIdentityKey());
        }
        if (eventListener.isPresent()) {
          eventListener.get().onSecurityEvent(recipient);
        }
      }
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }

    // Loki - Ensure all session processing has finished
    synchronized (SESSION_LOCK) {
      try {
        return cipher.encrypt(signalProtocolAddress, unidentifiedAccess, plaintext);
      } catch (org.session.libsignal.libsignal.UntrustedIdentityException e) {
        throw new UntrustedIdentityException("Untrusted on send", recipient.getNumber(), e.getUntrustedIdentity());
      }
    }
  }

  private Optional<UnidentifiedAccess> getTargetUnidentifiedAccess(Optional<UnidentifiedAccessPair> unidentifiedAccess) {
    if (unidentifiedAccess.isPresent()) {
      return unidentifiedAccess.get().getTargetUnidentifiedAccess();
    }

    return Optional.absent();
  }

  private List<Optional<UnidentifiedAccess>> getTargetUnidentifiedAccess(List<Optional<UnidentifiedAccessPair>> unidentifiedAccess) {
    List<Optional<UnidentifiedAccess>> results = new LinkedList<>();

    for (Optional<UnidentifiedAccessPair> item : unidentifiedAccess) {
      if (item.isPresent()) results.add(item.get().getTargetUnidentifiedAccess());
      else                  results.add(Optional.<UnidentifiedAccess>absent());
    }

    return results;
  }

  public static interface EventListener {

    public void onSecurityEvent(SignalServiceAddress address);
  }
}

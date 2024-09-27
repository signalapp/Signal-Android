/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api;

import org.signal.core.util.Base64;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidRegistrationIdException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SessionBuilder;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.groups.GroupSessionBuilder;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.message.DecryptionErrorMessage;
import org.signal.libsignal.protocol.message.PlaintextContent;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.zkgroup.groupsend.GroupSendFullToken;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.whispersystems.signalservice.api.attachment.AttachmentApi;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.EnvelopeContent;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.crypto.SignalGroupSessionBuilder;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.SignalSessionBuilder;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.GroupSendEndorsements;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEditMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.messages.SignalServicePreview;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessageRecipient;
import org.whispersystems.signalservice.api.messages.SignalServiceTextAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.CallingResponse;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.OutgoingPaymentMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewedMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.services.AttachmentService;
import org.whispersystems.signalservice.api.services.MessagingService;
import org.whispersystems.signalservice.api.util.AttachmentPointerUtil;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.api.util.Uint64RangeException;
import org.whispersystems.signalservice.api.util.Uint64Util;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.crypto.AttachmentDigest;
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream;
import org.whispersystems.signalservice.internal.push.AttachmentPointer;
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm;
import org.whispersystems.signalservice.internal.push.BodyRange;
import org.whispersystems.signalservice.internal.push.CallMessage;
import org.whispersystems.signalservice.internal.push.Content;
import org.whispersystems.signalservice.internal.push.DataMessage;
import org.whispersystems.signalservice.internal.push.EditMessage;
import org.whispersystems.signalservice.internal.push.GroupContextV2;
import org.whispersystems.signalservice.internal.push.GroupMismatchedDevices;
import org.whispersystems.signalservice.internal.push.GroupStaleDevices;
import org.whispersystems.signalservice.internal.push.MismatchedDevices;
import org.whispersystems.signalservice.internal.push.NullMessage;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.push.PniSignatureMessage;
import org.whispersystems.signalservice.internal.push.Preview;
import org.whispersystems.signalservice.internal.push.ProvisioningVersion;
import org.whispersystems.signalservice.internal.push.PushAttachmentData;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.ReceiptMessage;
import org.whispersystems.signalservice.internal.push.SendGroupMessageResponse;
import org.whispersystems.signalservice.internal.push.SendMessageResponse;
import org.whispersystems.signalservice.internal.push.StaleDevices;
import org.whispersystems.signalservice.internal.push.StoryMessage;
import org.whispersystems.signalservice.internal.push.SyncMessage;
import org.whispersystems.signalservice.internal.push.TextAttachment;
import org.whispersystems.signalservice.internal.push.TypingMessage;
import org.whispersystems.signalservice.internal.push.Verified;
import org.whispersystems.signalservice.internal.push.exceptions.GroupMismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupStaleDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.InvalidUnidentifiedAccessHeaderException;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory;
import org.whispersystems.signalservice.internal.push.http.CancelationSignal;
import org.whispersystems.signalservice.internal.push.http.PartialSendBatchCompleteListener;
import org.whispersystems.signalservice.internal.push.http.PartialSendCompleteListener;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.exceptions.CompositeException;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;
import okio.ByteString;

/**
 * The main interface for sending Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceMessageSender {

  private static final String TAG = SignalServiceMessageSender.class.getSimpleName();

  private static final int RETRY_COUNT = 4;

  private final PushServiceSocket             socket;
  private final SignalWebSocket               webSocket;
  private final SignalServiceAccountDataStore aciStore;
  private final SignalSessionLock             sessionLock;
  private final SignalServiceAddress          localAddress;
  private final int                           localDeviceId;
  private final PNI                           localPni;
  private final Optional<EventListener>       eventListener;
  private final IdentityKeyPair               localPniIdentity;

  private final AttachmentService attachmentService;
  private final MessagingService  messagingService;

  private final ExecutorService executor;
  private final Scheduler       scheduler;
  private final long            maxEnvelopeSize;

  public SignalServiceMessageSender(PushServiceSocket pushServiceSocket,
                                    SignalServiceDataStore store,
                                    SignalSessionLock sessionLock,
                                    SignalWebSocket signalWebSocket,
                                    Optional<EventListener> eventListener,
                                    ExecutorService executor,
                                    long maxEnvelopeSize)
  {
    CredentialsProvider credentialsProvider = pushServiceSocket.getCredentialsProvider();

    this.socket            = pushServiceSocket;
    this.webSocket         = signalWebSocket;
    this.aciStore          = store.aci();
    this.sessionLock       = sessionLock;
    this.localAddress      = new SignalServiceAddress(credentialsProvider.getAci(), credentialsProvider.getE164());
    this.localDeviceId     = credentialsProvider.getDeviceId();
    this.localPni          = credentialsProvider.getPni();
    this.attachmentService = new AttachmentService(signalWebSocket);
    this.messagingService  = new MessagingService(signalWebSocket);
    this.eventListener     = eventListener;
    this.executor          = executor != null ? executor : Executors.newSingleThreadExecutor();
    this.maxEnvelopeSize   = maxEnvelopeSize;
    this.localPniIdentity  = store.pni().getIdentityKeyPair();
    this.scheduler         = Schedulers.from(executor, false, false);
  }

  /**
   * Send a read receipt for a received message.
   *
   * @param recipient The sender of the received message you're acknowledging.
   * @param message The read receipt to deliver.
   */
  public SendMessageResult sendReceipt(SignalServiceAddress recipient,
                                       @Nullable SealedSenderAccess sealedSenderAccess,
                                       SignalServiceReceiptMessage message,
                                       boolean includePniSignature)
      throws IOException, UntrustedIdentityException
  {
    Log.d(TAG, "[" + message.getWhen() + "] Sending a receipt.");

    Content content = createReceiptContent(message);

    if (includePniSignature) {
      content = content.newBuilder()
                       .pniSignatureMessage(createPniSignatureMessage())
                       .build();
    }

    EnvelopeContent envelopeContent = EnvelopeContent.encrypted(content, ContentHint.IMPLICIT, Optional.empty());

    return sendMessage(recipient, sealedSenderAccess, message.getWhen(), envelopeContent, false, null, null, false, false);
  }

  /**
   * Send a retry receipt for a bad-encrypted envelope.
   */
  public void sendRetryReceipt(SignalServiceAddress recipient,
                               @Nullable SealedSenderAccess sealedSenderAccess,
                               Optional<byte[]> groupId,
                               DecryptionErrorMessage errorMessage)
      throws IOException, UntrustedIdentityException

  {
    Log.d(TAG, "[" + errorMessage.getTimestamp() + "] Sending a retry receipt.");

    PlaintextContent content         = new PlaintextContent(errorMessage);
    EnvelopeContent  envelopeContent = EnvelopeContent.plaintext(content, groupId);

    sendMessage(recipient, sealedSenderAccess, System.currentTimeMillis(), envelopeContent, false, null, null, false, false);
  }

  /**
   * Sends a typing indicator using client-side fanout. Doesn't bother with return results, since these are best-effort.
   */
  public void sendTyping(List<SignalServiceAddress> recipients,
                         List<SealedSenderAccess> sealedSenderAccesses,
                         SignalServiceTypingMessage message,
                         CancelationSignal cancelationSignal)
      throws IOException
  {
    Log.d(TAG, "[" + message.getTimestamp() + "] Sending a typing message to " + recipients.size() + " recipient(s) using 1:1 messages.");

    Content         content         = createTypingContent(message);
    EnvelopeContent envelopeContent = EnvelopeContent.encrypted(content, ContentHint.IMPLICIT, Optional.empty());

    sendMessage(recipients, sealedSenderAccesses, message.getTimestamp(), envelopeContent, true, null, cancelationSignal, null, false, false);
  }

  /**
   * Send a typing indicator to a group using sender key. Doesn't bother with return results, since these are best-effort.
   */
  public void sendGroupTyping(DistributionId distributionId,
                              List<SignalServiceAddress> recipients,
                              List<UnidentifiedAccess> unidentifiedAccess,
                              @Nullable GroupSendEndorsements groupSendEndorsements,
                              SignalServiceTypingMessage message)
      throws IOException, UntrustedIdentityException, InvalidKeyException, NoSessionException, InvalidRegistrationIdException
  {
    Log.d(TAG, "[" + message.getTimestamp() + "] Sending a typing message to " + recipients.size() + " recipient(s) using sender key.");

    Content content = createTypingContent(message);
    sendGroupMessage(distributionId, recipients, unidentifiedAccess, groupSendEndorsements, message.getTimestamp(), content, ContentHint.IMPLICIT, message.getGroupId(), true, SenderKeyGroupEvents.EMPTY, false, false);
  }

  /**
   * Only sends sync message for a story. Useful if you're sending to a group with no one else in it -- meaning you don't need to send a story, but you do need
   * to send it to your linked devices.
   */
  public void sendStorySyncMessage(SignalServiceStoryMessage message,
                                   long timestamp,
                                   boolean isRecipientUpdate,
                                   Set<SignalServiceStoryMessageRecipient> manifest)
      throws IOException, UntrustedIdentityException
  {
    Log.d(TAG, "[" + timestamp + "] Sending a story sync message.");

    if (manifest.isEmpty() && !message.getGroupContext().isPresent()) {
      Log.w(TAG, "Refusing to send sync message for empty manifest in non-group story.");
      return;
    }

    SignalServiceSyncMessage syncMessage = createSelfSendSyncMessageForStory(message, timestamp, isRecipientUpdate, manifest);
    sendSyncMessage(syncMessage);
  }

  /**
   * Send a story using sender key. Note: This is not just for group stories -- it's for any story. Just following the naming convention of making sender key
   * method named "sendGroup*"
   */
  public List<SendMessageResult> sendGroupStory(DistributionId distributionId,
                                                Optional<byte[]> groupId,
                                                List<SignalServiceAddress> recipients,
                                                List<UnidentifiedAccess> unidentifiedAccess,
                                                @Nullable GroupSendEndorsements groupSendEndorsements,
                                                boolean isRecipientUpdate,
                                                SignalServiceStoryMessage message,
                                                long timestamp,
                                                Set<SignalServiceStoryMessageRecipient> manifest,
                                                PartialSendBatchCompleteListener partialListener)
      throws IOException, UntrustedIdentityException, InvalidKeyException, NoSessionException, InvalidRegistrationIdException
  {
    Log.d(TAG, "[" + timestamp + "] Sending a story.");

    Content                  content            = createStoryContent(message);
    List<SendMessageResult>  sendMessageResults = sendGroupMessage(distributionId, recipients, unidentifiedAccess, groupSendEndorsements, timestamp, content, ContentHint.IMPLICIT, groupId, false, SenderKeyGroupEvents.EMPTY, false, true);

    if (partialListener != null) {
      partialListener.onPartialSendComplete(sendMessageResults);
    }

    if (aciStore.isMultiDevice()) {
      sendStorySyncMessage(message, timestamp, isRecipientUpdate, manifest);
    }

    return sendMessageResults;
  }


  /**
   * Send a call setup message to a single recipient.
   *
   * @param recipient The message's destination.
   * @param message The call message.
   * @throws IOException
   */
  public void sendCallMessage(SignalServiceAddress recipient,
                              @Nullable SealedSenderAccess sealedSenderAccess,
                              SignalServiceCallMessage message)
      throws IOException, UntrustedIdentityException
  {
    long timestamp = System.currentTimeMillis();
    Log.d(TAG, "[" + timestamp + "] Sending a call message (single recipient).");

    Content         content         = createCallContent(message);
    EnvelopeContent envelopeContent = EnvelopeContent.encrypted(content, ContentHint.DEFAULT, Optional.empty());

    sendMessage(recipient, sealedSenderAccess, timestamp, envelopeContent, false, null, null, message.isUrgent(), false);
  }

  public List<SendMessageResult> sendCallMessage(List<SignalServiceAddress> recipients,
                                                 List<SealedSenderAccess> sealedSenderAccesses,
                                                 SignalServiceCallMessage message)
      throws IOException
  {
    long timestamp = System.currentTimeMillis();
    Log.d(TAG, "[" + timestamp + "] Sending a call message (multiple recipients).");

    Content         content         = createCallContent(message);
    EnvelopeContent envelopeContent = EnvelopeContent.encrypted(content, ContentHint.DEFAULT, Optional.empty());

    return sendMessage(recipients, sealedSenderAccesses, timestamp, envelopeContent, false, null, null, null, message.isUrgent(), false);
  }

  public List<SendMessageResult> sendCallMessage(DistributionId distributionId,
                                                 List<SignalServiceAddress> recipients,
                                                 List<UnidentifiedAccess> unidentifiedAccess,
                                                 @Nullable GroupSendEndorsements groupSendEndorsements,
                                                 SignalServiceCallMessage message,
                                                 PartialSendBatchCompleteListener partialListener)
      throws IOException, UntrustedIdentityException, InvalidKeyException, NoSessionException, InvalidRegistrationIdException
  {
    Log.d(TAG, "[" + message.getTimestamp().get() + "] Sending a call message (sender key).");

    Content content = createCallContent(message);

    List<SendMessageResult> results = sendGroupMessage(distributionId, recipients, unidentifiedAccess, groupSendEndorsements, message.getTimestamp().get(), content, ContentHint.IMPLICIT, message.getGroupId(), false, SenderKeyGroupEvents.EMPTY, message.isUrgent(), false);

    if (partialListener != null) {
      partialListener.onPartialSendComplete(results);
    }

    return results;
  }

  /**
   * Send an http request on behalf of the calling infrastructure.
   *
   * @param requestId Request identifier
   * @param url Fully qualified URL to request
   * @param httpMethod Http method to use (e.g., "GET", "POST")
   * @param headers Optional list of headers to send with request
   * @param body Optional body to send with request
   * @return
   */
  public CallingResponse makeCallingRequest(long requestId, String url, String httpMethod, List<Pair<String, String>> headers, byte[] body) {
    return socket.makeCallingRequest(requestId, url, httpMethod, headers, body);
  }

  /**
   * Send a message to a single recipient.
   *
   * @param recipient The message's destination.
   * @param message The message.
   * @throws UntrustedIdentityException
   * @throws IOException
   */
  public SendMessageResult sendDataMessage(SignalServiceAddress recipient,
                                           @Nullable SealedSenderAccess sealedSenderAccess,
                                           ContentHint contentHint,
                                           SignalServiceDataMessage message,
                                           IndividualSendEvents sendEvents,
                                           boolean urgent,
                                           boolean includePniSignature)
      throws UntrustedIdentityException, IOException
  {
    Log.d(TAG, "[" + message.getTimestamp() + "] Sending a data message.");

    Content content = createMessageContent(message);

    return sendContent(recipient, sealedSenderAccess, contentHint, message, sendEvents, urgent, includePniSignature, content);
  }

  /**
   * Send an edit message to a single recipient.
   */
  public SendMessageResult sendEditMessage(SignalServiceAddress recipient,
                                           @Nullable SealedSenderAccess sealedSenderAccess,
                                           ContentHint contentHint,
                                           SignalServiceDataMessage message,
                                           IndividualSendEvents sendEvents,
                                           boolean urgent,
                                           long targetSentTimestamp)
      throws UntrustedIdentityException, IOException
  {
    Log.d(TAG, "[" + message.getTimestamp() + "] Sending an edit message for " + targetSentTimestamp + ".");

    Content content = createEditMessageContent(new SignalServiceEditMessage(targetSentTimestamp, message));

    return sendContent(recipient, sealedSenderAccess, contentHint, message, sendEvents, urgent, false, content);
  }

  /**
   * Sends content to a single recipient.
   */
  private SendMessageResult sendContent(SignalServiceAddress recipient,
                                        @Nullable SealedSenderAccess sealedSenderAccess,
                                        ContentHint contentHint,
                                        SignalServiceDataMessage message,
                                        IndividualSendEvents sendEvents,
                                        boolean urgent,
                                        boolean includePniSignature,
                                        Content content)
      throws UntrustedIdentityException, IOException
  {
    if (includePniSignature) {
      Log.d(TAG, "[" + message.getTimestamp() + "] Including PNI signature.");
      content = content.newBuilder()
                       .pniSignatureMessage(createPniSignatureMessage())
                       .build();
    }

    EnvelopeContent envelopeContent = EnvelopeContent.encrypted(content, contentHint, message.getGroupId());

    long              timestamp = message.getTimestamp();
    SendMessageResult result    = sendMessage(recipient, sealedSenderAccess, timestamp, envelopeContent, false, null, sendEvents, urgent, false);

    sendEvents.onMessageSent();

    if (result.getSuccess() != null && result.getSuccess().isNeedsSync()) {
      Content         syncMessage        = createMultiDeviceSentTranscriptContent(content, Optional.of(recipient), timestamp, Collections.singletonList(result), false, Collections.emptySet());
      EnvelopeContent syncMessageContent = EnvelopeContent.encrypted(syncMessage, ContentHint.IMPLICIT, Optional.empty());

      sendMessage(localAddress, SealedSenderAccess.NONE, timestamp, syncMessageContent, false, null, null, false, false);
    }

    sendEvents.onSyncMessageSent();

    return result;
  }

  /**
   * Gives you a {@link SenderKeyDistributionMessage} that can then be sent out to recipients to tell them about your sender key.
   * Will create a sender key session for the provided DistributionId if one doesn't exist.
   */
  public SenderKeyDistributionMessage getOrCreateNewGroupSession(DistributionId distributionId) {
    SignalProtocolAddress self = new SignalProtocolAddress(localAddress.getIdentifier(), localDeviceId);
    return new SignalGroupSessionBuilder(sessionLock, new GroupSessionBuilder(aciStore)).create(self, distributionId.asUuid());
  }

  /**
   * Sends the provided {@link SenderKeyDistributionMessage} to the specified recipients.
   */
  public List<SendMessageResult> sendSenderKeyDistributionMessage(DistributionId distributionId,
                                                                  List<SignalServiceAddress> recipients,
                                                                  List<SealedSenderAccess> sealedSenderAccesses,
                                                                  SenderKeyDistributionMessage message,
                                                                  Optional<byte[]> groupId,
                                                                  boolean urgent,
                                                                  boolean story)
      throws IOException
  {
    ByteString      distributionBytes = ByteString.of(message.serialize());
    Content         content           = new Content.Builder().senderKeyDistributionMessage(distributionBytes).build();
    EnvelopeContent envelopeContent   = EnvelopeContent.encrypted(content, ContentHint.IMPLICIT, groupId);
    long            timestamp         = System.currentTimeMillis();

    Log.d(TAG, "[" + timestamp + "] Sending SKDM to " + recipients.size() + " recipients for DistributionId " + distributionId);

    return sendMessage(recipients, sealedSenderAccesses, timestamp, envelopeContent, false, null, null, null, urgent, story);
  }

  /**
   * Resend a previously-sent message.
   */
  public SendMessageResult resendContent(SignalServiceAddress address,
                                         @Nullable SealedSenderAccess sealedSenderAccess,
                                         long timestamp,
                                         Content content,
                                         ContentHint contentHint,
                                         Optional<byte[]> groupId,
                                         boolean urgent)
      throws UntrustedIdentityException, IOException
  {
    Log.d(TAG, "[" + timestamp + "] Resending content.");

    EnvelopeContent              envelopeContent = EnvelopeContent.encrypted(content, contentHint, groupId);

    if (address.getServiceId().equals(localAddress.getServiceId())) {
      sealedSenderAccess = SealedSenderAccess.NONE;
    }

    return sendMessage(address, sealedSenderAccess, timestamp, envelopeContent, false, null, null, urgent, false);
  }

  /**
   * Sends a {@link SignalServiceDataMessage} to a group using sender keys.
   */
  public List<SendMessageResult> sendGroupDataMessage(DistributionId distributionId,
                                                      List<SignalServiceAddress> recipients,
                                                      List<UnidentifiedAccess> unidentifiedAccess,
                                                      @Nullable GroupSendEndorsements groupSendEndorsements,
                                                      boolean isRecipientUpdate,
                                                      ContentHint contentHint,
                                                      SignalServiceDataMessage message,
                                                      SenderKeyGroupEvents sendEvents,
                                                      boolean urgent,
                                                      boolean isForStory,
                                                      SignalServiceEditMessage editMessage,
                                                      PartialSendBatchCompleteListener partialListener)
      throws IOException, UntrustedIdentityException, NoSessionException, InvalidKeyException, InvalidRegistrationIdException
  {
    Log.d(TAG, "[" + message.getTimestamp() + "] Sending a group " + (editMessage != null ? "edit data message" : "data message") + " to " + recipients.size() + " recipients using DistributionId " + distributionId);

    Content content;

    if (editMessage != null) {
      content = createEditMessageContent(editMessage);
    } else {
      content = createMessageContent(message);
    }

    Optional<byte[]>        groupId = message.getGroupId();
    List<SendMessageResult> results = sendGroupMessage(distributionId, recipients, unidentifiedAccess, groupSendEndorsements, message.getTimestamp(), content, contentHint, groupId, false, sendEvents, urgent, isForStory);

    if (partialListener != null) {
      partialListener.onPartialSendComplete(results);
    }

    sendEvents.onMessageSent();

    if (aciStore.isMultiDevice()) {
      Content         syncMessage        = createMultiDeviceSentTranscriptContent(content, Optional.empty(), message.getTimestamp(), results, isRecipientUpdate, Collections.emptySet());
      EnvelopeContent syncMessageContent = EnvelopeContent.encrypted(syncMessage, ContentHint.IMPLICIT, Optional.empty());

      sendMessage(localAddress, SealedSenderAccess.NONE, message.getTimestamp(), syncMessageContent, false, null, null, false, false);
    }

    sendEvents.onSyncMessageSent();

    return results;
  }

  /**
   * Sends a message to a group using client-side fanout.
   *
   * @param partialListener A listener that will be called when an individual send is completed. Will be invoked on an arbitrary background thread, *not*
   *                        the calling thread.
   */
  public List<SendMessageResult> sendDataMessage(List<SignalServiceAddress> recipients,
                                                 List<SealedSenderAccess> sealedSenderAccesses,
                                                 boolean isRecipientUpdate,
                                                 ContentHint contentHint,
                                                 SignalServiceDataMessage message,
                                                 LegacyGroupEvents sendEvents,
                                                 PartialSendCompleteListener partialListener,
                                                 CancelationSignal cancelationSignal,
                                                 boolean urgent)
      throws IOException, UntrustedIdentityException
  {
    Log.d(TAG, "[" + message.getTimestamp() + "] Sending a data message to " + recipients.size() + " recipients.");

    Content                 content            = createMessageContent(message);
    EnvelopeContent         envelopeContent    = EnvelopeContent.encrypted(content, contentHint, message.getGroupId());
    long                    timestamp          = message.getTimestamp();
    List<SendMessageResult> results            = sendMessage(recipients, sealedSenderAccesses, timestamp, envelopeContent, false, partialListener, cancelationSignal, sendEvents, urgent, false);
    boolean                 needsSyncInResults = false;

    sendEvents.onMessageSent();

    for (SendMessageResult result : results) {
      if (result.getSuccess() != null && result.getSuccess().isNeedsSync()) {
        needsSyncInResults = true;
        break;
      }
    }

    if (needsSyncInResults || aciStore.isMultiDevice()) {
      Optional<SignalServiceAddress> recipient = Optional.empty();
      if (!message.getGroupContext().isPresent() && recipients.size() == 1) {
        recipient = Optional.of(recipients.get(0));
      }

      Content         syncMessage        = createMultiDeviceSentTranscriptContent(content, recipient, timestamp, results, isRecipientUpdate, Collections.emptySet());
      EnvelopeContent syncMessageContent = EnvelopeContent.encrypted(syncMessage, ContentHint.IMPLICIT, Optional.empty());

      sendMessage(localAddress, SealedSenderAccess.NONE, timestamp, syncMessageContent, false, null, null, false, false);
    }

    sendEvents.onSyncMessageSent();

    return results;
  }

  /**
   * Sends an edit message to a group using client-side fanout.
   *
   * @param partialListener A listener that will be called when an individual send is completed. Will be invoked on an arbitrary background thread, *not*
   *                        the calling thread.
   */
  public List<SendMessageResult> sendEditMessage(List<SignalServiceAddress> recipients,
                                                 List<SealedSenderAccess> sealedSenderAccesses,
                                                 boolean isRecipientUpdate,
                                                 ContentHint contentHint,
                                                 SignalServiceDataMessage message,
                                                 LegacyGroupEvents sendEvents,
                                                 PartialSendCompleteListener partialListener,
                                                 CancelationSignal cancelationSignal,
                                                 boolean urgent,
                                                 long targetSentTimestamp)
      throws IOException, UntrustedIdentityException
  {
    Log.d(TAG, "[" + message.getTimestamp() + "] Sending a edit message to " + recipients.size() + " recipients.");

    Content                 content            = createEditMessageContent(new SignalServiceEditMessage(targetSentTimestamp, message));
    EnvelopeContent         envelopeContent    = EnvelopeContent.encrypted(content, contentHint, message.getGroupId());
    long                    timestamp          = message.getTimestamp();
    List<SendMessageResult> results            = sendMessage(recipients, sealedSenderAccesses, timestamp, envelopeContent, false, partialListener, cancelationSignal, null, urgent, false);
    boolean                 needsSyncInResults = false;

    sendEvents.onMessageSent();

    for (SendMessageResult result : results) {
      if (result.getSuccess() != null && result.getSuccess().isNeedsSync()) {
        needsSyncInResults = true;
        break;
      }
    }

    if (needsSyncInResults || aciStore.isMultiDevice()) {
      Optional<SignalServiceAddress> recipient = Optional.empty();
      if (!message.getGroupContext().isPresent() && recipients.size() == 1) {
        recipient = Optional.of(recipients.get(0));
      }

      Content         syncMessage        = createMultiDeviceSentTranscriptContent(content, recipient, timestamp, results, isRecipientUpdate, Collections.emptySet());
      EnvelopeContent syncMessageContent = EnvelopeContent.encrypted(syncMessage, ContentHint.IMPLICIT, Optional.empty());

      sendMessage(localAddress, SealedSenderAccess.NONE, timestamp, syncMessageContent, false, null, null, false, false);
    }

    sendEvents.onSyncMessageSent();

    return results;
  }

  public SendMessageResult sendSyncMessage(SignalServiceDataMessage dataMessage)
      throws IOException, UntrustedIdentityException
  {
    Log.d(TAG, "[" + dataMessage.getTimestamp() + "] Sending self-sync message.");
    return sendSyncMessage(createSelfSendSyncMessage(dataMessage));
  }

  public SendMessageResult sendSelfSyncEditMessage(SignalServiceEditMessage editMessage)
      throws IOException, UntrustedIdentityException
  {
    Log.d(TAG, "[" + editMessage.getDataMessage().getTimestamp() + "] Sending self-sync edit message for " + editMessage.getTargetSentTimestamp() + ".");
    return sendSyncMessage(createSelfSendSyncEditMessage(editMessage));
  }

  public SendMessageResult sendSyncMessage(SignalServiceSyncMessage message)
      throws IOException, UntrustedIdentityException
  {
    Content content;
    boolean urgent = false;

    if (message.getContacts().isPresent()) {
      content = createMultiDeviceContactsContent(message.getContacts().get().getContactsStream().asStream(), message.getContacts().get().isComplete());
    } else if (message.getRead().isPresent()) {
      content = createMultiDeviceReadContent(message.getRead().get());
      urgent  = true;
    } else if (message.getViewed().isPresent()) {
      content = createMultiDeviceViewedContent(message.getViewed().get());
    } else if (message.getViewOnceOpen().isPresent()) {
      content = createMultiDeviceViewOnceOpenContent(message.getViewOnceOpen().get());
    } else if (message.getBlockedList().isPresent()) {
      content = createMultiDeviceBlockedContent(message.getBlockedList().get());
    } else if (message.getConfiguration().isPresent()) {
      content = createMultiDeviceConfigurationContent(message.getConfiguration().get());
    } else if (message.getSent().isPresent()) {
      content = createMultiDeviceSentTranscriptContent(message.getSent().get());
    } else if (message.getStickerPackOperations().isPresent()) {
      content = createMultiDeviceStickerPackOperationContent(message.getStickerPackOperations().get());
    } else if (message.getFetchType().isPresent()) {
      content = createMultiDeviceFetchTypeContent(message.getFetchType().get());
    } else if (message.getMessageRequestResponse().isPresent()) {
      content = createMultiDeviceMessageRequestResponseContent(message.getMessageRequestResponse().get());
    } else if (message.getOutgoingPaymentMessage().isPresent()) {
      content = createMultiDeviceOutgoingPaymentContent(message.getOutgoingPaymentMessage().get());
    } else if (message.getKeys().isPresent()) {
      content = createMultiDeviceSyncKeysContent(message.getKeys().get());
    } else if (message.getVerified().isPresent()) {
      return sendVerifiedSyncMessage(message.getVerified().get());
    } else if (message.getRequest().isPresent()) {
      content = createRequestContent(message.getRequest().get().getRequest());
      urgent  = message.getRequest().get().isUrgent();
    } else if (message.getCallEvent().isPresent()) {
      content = createCallEventContent(message.getCallEvent().get());
    } else if (message.getCallLinkUpdate().isPresent()) {
      content = createCallLinkUpdateContent(message.getCallLinkUpdate().get());
    } else if (message.getCallLogEvent().isPresent()) {
      content = createCallLogEventContent(message.getCallLogEvent().get());
    } else {
      throw new IOException("Unsupported sync message!");
    }

    Optional<Long> timestamp = message.getSent().map(SentTranscriptMessage::getTimestamp);

    return sendSyncMessage(content, urgent, timestamp);
  }

  public @Nonnull SendMessageResult sendSyncMessage(Content content, boolean urgent, Optional<Long> sent)
      throws IOException, UntrustedIdentityException
  {
    long timestamp = sent.orElseGet(System::currentTimeMillis);

    EnvelopeContent envelopeContent = EnvelopeContent.encrypted(content, ContentHint.IMPLICIT, Optional.empty());

    return sendMessage(localAddress, SealedSenderAccess.NONE, timestamp, envelopeContent, false, null, null, urgent, false);
  }

  /**
   * Create a device specific sync message that includes updated PNI details for that specific linked device. This message is
   * sent to the server via the change number endpoint and not the normal sync message sending flow.
   *
   * @param deviceId - Device ID of linked device to build message for
   * @param pniChangeNumber - Linked device specific updated PNI details
   * @return Encrypted {@link OutgoingPushMessage} to be included in the change number request sent to the server
   */
  public @Nonnull OutgoingPushMessage getEncryptedSyncPniInitializeDeviceMessage(int deviceId, @Nonnull SyncMessage.PniChangeNumber pniChangeNumber)
      throws UntrustedIdentityException, IOException, InvalidKeyException
  {
    SyncMessage.Builder syncMessage     = createSyncMessageBuilder().pniChangeNumber(pniChangeNumber);
    Content.Builder     content         = new Content.Builder().syncMessage(syncMessage.build());
    EnvelopeContent     envelopeContent = EnvelopeContent.encrypted(content.build(), ContentHint.IMPLICIT, Optional.empty());

    return getEncryptedMessage(localAddress, SealedSenderAccess.NONE, deviceId, envelopeContent, false);
  }

  public void cancelInFlightRequests() {
    socket.cancelInFlightRequests();
  }

  public SignalServiceAttachmentPointer uploadAttachment(SignalServiceAttachmentStream attachment) throws IOException {
    byte[]             attachmentKey    = attachment.getResumableUploadSpec().map(ResumableUploadSpec::getAttachmentKey).orElseGet(() -> Util.getSecretBytes(64));
    byte[]             attachmentIV     = attachment.getResumableUploadSpec().map(ResumableUploadSpec::getAttachmentIv).orElseGet(() -> Util.getSecretBytes(16));
    long               paddedLength     = PaddingInputStream.getPaddedSize(attachment.getLength());
    InputStream        dataStream       = new PaddingInputStream(attachment.getInputStream(), attachment.getLength());
    long               ciphertextLength = AttachmentCipherStreamUtil.getCiphertextLength(paddedLength);
    PushAttachmentData attachmentData   = new PushAttachmentData(attachment.getContentType(),
                                                                 dataStream,
                                                                 ciphertextLength,
                                                                 attachment.isFaststart(),
                                                                 new AttachmentCipherOutputStreamFactory(attachmentKey, attachmentIV),
                                                                 attachment.getListener(),
                                                                 attachment.getCancelationSignal(),
                                                                 attachment.getResumableUploadSpec().get());

    if (attachment.getResumableUploadSpec().isEmpty()) {
      throw new IllegalStateException("Attachment must have a resumable upload spec.");
    }

    return uploadAttachmentV4(attachment, attachmentKey, attachmentData);
  }

  public ResumableUploadSpec getResumableUploadSpec() throws IOException {
    AttachmentUploadForm v4UploadAttributes = null;

    Log.d(TAG, "Using pipe to retrieve attachment upload attributes...");
    try {
      v4UploadAttributes = new AttachmentService.AttachmentAttributesResponseProcessor<>(attachmentService.getAttachmentV4UploadAttributes().blockingGet()).getResultOrThrow();
    } catch (WebSocketUnavailableException e) {
      Log.w(TAG, "[getResumableUploadSpec] Pipe unavailable, falling back... (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
    } catch (IOException e) {
      if (e instanceof RateLimitException) {
        throw e;
      }
      Log.w(TAG, "Failed to retrieve attachment upload attributes using pipe. Falling back...");
    }
    
    if (v4UploadAttributes == null) {
      Log.d(TAG, "Not using pipe to retrieve attachment upload attributes...");
      v4UploadAttributes = socket.getAttachmentV4UploadAttributes();
    }

    return socket.getResumableUploadSpec(v4UploadAttributes);
  }

  private SignalServiceAttachmentPointer uploadAttachmentV4(SignalServiceAttachmentStream attachment, byte[] attachmentKey, PushAttachmentData attachmentData) throws IOException {
    AttachmentDigest digest = socket.uploadAttachment(attachmentData);
    return new SignalServiceAttachmentPointer(attachmentData.getResumableUploadSpec().getCdnNumber(),
                                              new SignalServiceAttachmentRemoteId.V4(attachmentData.getResumableUploadSpec().getCdnKey()),
                                              attachment.getContentType(),
                                              attachmentKey,
                                              Optional.of(Util.toIntExact(attachment.getLength())),
                                              attachment.getPreview(),
                                              attachment.getWidth(),
                                              attachment.getHeight(),
                                              Optional.of(digest.getDigest()),
                                              Optional.ofNullable(digest.getIncrementalDigest()),
                                              digest.getIncrementalDigest() != null ? digest.getIncrementalMacChunkSize() : 0,
                                              attachment.getFileName(),
                                              attachment.getVoiceNote(),
                                              attachment.isBorderless(),
                                              attachment.isGif(),
                                              attachment.getCaption(),
                                              attachment.getBlurHash(),
                                              attachment.getUploadTimestamp(),
                                              attachment.getUuid());
  }

  private SendMessageResult sendVerifiedSyncMessage(VerifiedMessage message)
      throws IOException, UntrustedIdentityException
  {
    byte[] nullMessageBody = new DataMessage.Builder()
                                            .body(Base64.encodeWithPadding(Util.getRandomLengthSecretBytes(140)))
                                            .build()
                                            .encode();

    NullMessage nullMessage = new NullMessage.Builder()
                                             .padding(ByteString.of(nullMessageBody))
                                             .build();

    Content     content     = new Content.Builder()
                                         .nullMessage(nullMessage)
                                         .build();

    EnvelopeContent envelopeContent = EnvelopeContent.encrypted(content, ContentHint.IMPLICIT, Optional.empty());

    SendMessageResult result = sendMessage(message.getDestination(), null, message.getTimestamp(), envelopeContent, false, null, null, false, false);

    if (result.getSuccess().isNeedsSync()) {
      Content         syncMessage        = createMultiDeviceVerifiedContent(message, nullMessage.encode());
      EnvelopeContent syncMessageContent = EnvelopeContent.encrypted(syncMessage, ContentHint.IMPLICIT, Optional.empty());

      sendMessage(localAddress, SealedSenderAccess.NONE, message.getTimestamp(), syncMessageContent, false, null, null, false, false);
    }

    return result;
  }

  public SendMessageResult sendNullMessage(SignalServiceAddress address, @Nullable SealedSenderAccess sealedSenderAccess)
      throws UntrustedIdentityException, IOException
  {
    byte[] nullMessageBody = new DataMessage.Builder()
                                            .body(Base64.encodeWithPadding(Util.getRandomLengthSecretBytes(140)))
                                            .build()
                                            .encode();

    NullMessage nullMessage = new NullMessage.Builder()
                                             .padding(ByteString.of(nullMessageBody))
                                             .build();

    Content     content     = new Content.Builder()
                                         .nullMessage(nullMessage)
                                         .build();

    EnvelopeContent envelopeContent = EnvelopeContent.encrypted(content, ContentHint.IMPLICIT, Optional.empty());

    return sendMessage(address, sealedSenderAccess, System.currentTimeMillis(), envelopeContent, false, null, null, false, false);
  }

  private PniSignatureMessage createPniSignatureMessage() {
    byte[] signature = localPniIdentity.signAlternateIdentity(aciStore.getIdentityKeyPair().getPublicKey());

    return new PniSignatureMessage.Builder()
                                  .pni(UuidUtil.toByteString(localPni.getRawUuid()))
                                  .signature(ByteString.of(signature))
                                  .build();
  }

  private Content createTypingContent(SignalServiceTypingMessage message) {
    Content.Builder       container = new Content.Builder();
    TypingMessage.Builder builder   = new TypingMessage.Builder();

    builder.timestamp(message.getTimestamp());

    if      (message.isTypingStarted()) builder.action(TypingMessage.Action.STARTED);
    else if (message.isTypingStopped()) builder.action(TypingMessage.Action.STOPPED);
    else                                throw new IllegalArgumentException("Unknown typing indicator");

    if (message.getGroupId().isPresent()) {
      builder.groupId(ByteString.of(message.getGroupId().get()));
    }

    return container.typingMessage(builder.build()).build();
  }

  private Content createStoryContent(SignalServiceStoryMessage message) throws IOException {
    Content.Builder      container = new Content.Builder();
    StoryMessage.Builder builder   = new StoryMessage.Builder();

    if (message.getProfileKey().isPresent()) {
      builder.profileKey(ByteString.of(message.getProfileKey().get()));
    }

    if (message.getGroupContext().isPresent()) {
      builder.group(createGroupContent(message.getGroupContext().get()));
    }

    if (message.getFileAttachment().isPresent()) {
      if (message.getFileAttachment().get().isStream()) {
        builder.fileAttachment(createAttachmentPointer(message.getFileAttachment().get().asStream()));
      } else {
        builder.fileAttachment(createAttachmentPointer(message.getFileAttachment().get().asPointer()));
      }
    }

    if (message.getTextAttachment().isPresent()) {
      builder.textAttachment(createTextAttachment(message.getTextAttachment().get()));
    }

    if (message.getBodyRanges().isPresent()) {
      builder.bodyRanges(message.getBodyRanges().get());
    }

    builder.allowsReplies(message.getAllowsReplies().orElse(true));

    return container.storyMessage(builder.build()).build();
  }

  private Content createReceiptContent(SignalServiceReceiptMessage message) {
    Content.Builder        container = new Content.Builder();
    ReceiptMessage.Builder builder   = new ReceiptMessage.Builder();

    builder.timestamp = message.getTimestamps();

    if      (message.isDeliveryReceipt()) builder.type(ReceiptMessage.Type.DELIVERY);
    else if (message.isReadReceipt())     builder.type(ReceiptMessage.Type.READ);
    else if (message.isViewedReceipt())   builder.type(ReceiptMessage.Type.VIEWED);

    return container.receiptMessage(builder.build()).build();
  }

  private Content createMessageContent(SentTranscriptMessage transcriptMessage) throws IOException {
    if (transcriptMessage.getStoryMessage().isPresent()) {
      return createStoryContent(transcriptMessage.getStoryMessage().get());
    } else if (transcriptMessage.getDataMessage().isPresent()) {
      return createMessageContent(transcriptMessage.getDataMessage().get());
    } else if (transcriptMessage.getEditMessage().isPresent()) {
      return createEditMessageContent(transcriptMessage.getEditMessage().get());
    } else {
      return null;
    }
  }

  private Content createMessageContent(SignalServiceDataMessage message) throws IOException {
    Content.Builder     container   = new Content.Builder();
    DataMessage.Builder dataMessage = createDataMessage(message);

    return enforceMaxContentSize(container.dataMessage(dataMessage.build()).build());
  }

  private Content createEditMessageContent(SignalServiceEditMessage editMessage) throws IOException {
    Content.Builder     container        = new Content.Builder();
    DataMessage.Builder dataMessage      = createDataMessage(editMessage.getDataMessage());
    EditMessage.Builder editMessageProto = new EditMessage.Builder()
                                                          .dataMessage(dataMessage.build())
                                                          .targetSentTimestamp(editMessage.getTargetSentTimestamp());

    return enforceMaxContentSize(container.editMessage(editMessageProto.build()).build());
  }

  private DataMessage.Builder createDataMessage(SignalServiceDataMessage message) throws IOException {
    DataMessage.Builder     builder  = new DataMessage.Builder();
    List<AttachmentPointer> pointers = createAttachmentPointers(message.getAttachments());

    builder.requiredProtocolVersion = 0;

    if (!pointers.isEmpty()) {
      builder.attachments(pointers);

      for (AttachmentPointer pointer : pointers) {
        // TODO [cody] wire
//        if (pointer.getAttachmentIdentifierCase() == AttachmentPointer.AttachmentIdentifierCase.CDNKEY || pointer.getCdnNumber() != 0) {
//          builder.setRequiredProtocolVersion(Math.max(DataMessage.ProtocolVersion.CDN_SELECTOR_ATTACHMENTS_VALUE, builder.getRequiredProtocolVersion()));
//          break;
//        }
      }
    }

    if (message.getBody().isPresent()) {
      builder.body(message.getBody().get());
    }

    if (message.getGroupContext().isPresent()) {
      builder.groupV2(createGroupContent(message.getGroupContext().get()));
    }

    if (message.isEndSession()) {
      builder.flags(DataMessage.Flags.END_SESSION.getValue());
    }

    if (message.isExpirationUpdate()) {
      builder.flags(DataMessage.Flags.EXPIRATION_TIMER_UPDATE.getValue());
    }

    if (message.isProfileKeyUpdate()) {
      builder.flags(DataMessage.Flags.PROFILE_KEY_UPDATE.getValue());
    }

    if (message.getExpiresInSeconds() > 0) {
      builder.expireTimer(message.getExpiresInSeconds());
    }
    builder.expireTimerVersion(message.getExpireTimerVersion());

    if (message.getProfileKey().isPresent()) {
      builder.profileKey(ByteString.of(message.getProfileKey().get()));
    }

    if (message.getQuote().isPresent()) {
      DataMessage.Quote.Builder quoteBuilder = new DataMessage.Quote.Builder()
                                                                .id(message.getQuote().get().getId())
                                                                .text(message.getQuote().get().getText())
                                                                .authorAci(message.getQuote().get().getAuthor().toString())
                                                                .type(message.getQuote().get().getType().getProtoType());

      List<SignalServiceDataMessage.Mention> mentions = message.getQuote().get().getMentions();
      if (mentions != null && !mentions.isEmpty()) {
        List<BodyRange> bodyRanges = new ArrayList<>(quoteBuilder.bodyRanges);
        for (SignalServiceDataMessage.Mention mention : mentions) {
          bodyRanges.add(new BodyRange.Builder()
                                      .start(mention.getStart())
                                      .length(mention.getLength())
                                      .mentionAci(mention.getServiceId().toString())
                                      .build());
        }
        quoteBuilder.bodyRanges(bodyRanges);

        builder.requiredProtocolVersion(Math.max(DataMessage.ProtocolVersion.MENTIONS.getValue(), builder.requiredProtocolVersion));
      }

      List<BodyRange> bodyRanges = message.getQuote().get().getBodyRanges();
      if (bodyRanges != null) {
        List<BodyRange> quoteBodyRanges = new ArrayList<>(quoteBuilder.bodyRanges);
        quoteBodyRanges.addAll(bodyRanges);
        quoteBuilder.bodyRanges(quoteBodyRanges);
      }

      List<SignalServiceDataMessage.Quote.QuotedAttachment> attachments = message.getQuote().get().getAttachments();
      if (attachments != null) {
        List<DataMessage.Quote.QuotedAttachment> quotedAttachments = new ArrayList<>(attachments.size());
        for (SignalServiceDataMessage.Quote.QuotedAttachment attachment : attachments) {
          DataMessage.Quote.QuotedAttachment.Builder quotedAttachment = new DataMessage.Quote.QuotedAttachment.Builder();

          quotedAttachment.contentType(attachment.getContentType());

          if (attachment.getFileName() != null) {
            quotedAttachment.fileName(attachment.getFileName());
          }

          if (attachment.getThumbnail() != null) {
            quotedAttachment.thumbnail(createAttachmentPointer(attachment.getThumbnail().asStream()));
          }

          quotedAttachments.add(quotedAttachment.build());
        }
        quoteBuilder.attachments(quotedAttachments);
      }

      builder.quote(quoteBuilder.build());
    }

    if (message.getSharedContacts().isPresent()) {
      builder.contact = createSharedContactContent(message.getSharedContacts().get());
    }

    if (message.getPreviews().isPresent()) {
      List<Preview> previews = new ArrayList<>(message.getPreviews().get().size());
      for (SignalServicePreview preview : message.getPreviews().get()) {
        previews.add(createPreview(preview));
      }
      builder.preview(previews);
    }

    if (message.getMentions().isPresent()) {
      List<BodyRange> bodyRanges = new ArrayList<>(builder.bodyRanges);
      for (SignalServiceDataMessage.Mention mention : message.getMentions().get()) {
        bodyRanges.add(new BodyRange.Builder()
                                    .start(mention.getStart())
                                    .length(mention.getLength())
                                    .mentionAci(mention.getServiceId().toString())
                                    .build());
      }
      builder.bodyRanges(bodyRanges);
      builder.requiredProtocolVersion(Math.max(DataMessage.ProtocolVersion.MENTIONS.getValue(), builder.requiredProtocolVersion));
    }

    if (message.getSticker().isPresent()) {
      DataMessage.Sticker.Builder stickerBuilder = new DataMessage.Sticker.Builder();

      stickerBuilder.packId(ByteString.of(message.getSticker().get().getPackId()));
      stickerBuilder.packKey(ByteString.of(message.getSticker().get().getPackKey()));
      stickerBuilder.stickerId(message.getSticker().get().getStickerId());

      if (message.getSticker().get().getEmoji() != null) {
        stickerBuilder.emoji(message.getSticker().get().getEmoji());
      }

      if (message.getSticker().get().getAttachment().isStream()) {
        stickerBuilder.data_(createAttachmentPointer(message.getSticker().get().getAttachment().asStream()));
      } else {
        stickerBuilder.data_(createAttachmentPointer(message.getSticker().get().getAttachment().asPointer()));
      }

      builder.sticker(stickerBuilder.build());
    }

    if (message.isViewOnce()) {
      builder.isViewOnce(message.isViewOnce());
      builder.requiredProtocolVersion(Math.max(DataMessage.ProtocolVersion.VIEW_ONCE_VIDEO.getValue(), builder.requiredProtocolVersion));
    }

    if (message.getReaction().isPresent()) {
      DataMessage.Reaction.Builder reactionBuilder = new DataMessage.Reaction.Builder()
                                                                             .emoji(message.getReaction().get().getEmoji())
                                                                             .remove(message.getReaction().get().isRemove())
                                                                             .targetSentTimestamp(message.getReaction().get().getTargetSentTimestamp())
                                                                             .targetAuthorAci(message.getReaction().get().getTargetAuthor().toString());

      builder.reaction(reactionBuilder.build());
      builder.requiredProtocolVersion(Math.max(DataMessage.ProtocolVersion.REACTIONS.getValue(), builder.requiredProtocolVersion));
    }

    if (message.getRemoteDelete().isPresent()) {
      DataMessage.Delete delete = new DataMessage.Delete.Builder()
                                                        .targetSentTimestamp(message.getRemoteDelete().get().getTargetSentTimestamp())
                                                        .build();
      builder.delete(delete);
    }

    if (message.getGroupCallUpdate().isPresent()) {
      String eraId = message.getGroupCallUpdate().get().getEraId();
      if (eraId != null) {
        builder.groupCallUpdate(new DataMessage.GroupCallUpdate.Builder().eraId(eraId).build());
      } else {
        builder.groupCallUpdate(new DataMessage.GroupCallUpdate());
      }
    }

    if (message.getPayment().isPresent()) {
      SignalServiceDataMessage.Payment payment = message.getPayment().get();

      if (payment.getPaymentNotification().isPresent()) {
        SignalServiceDataMessage.PaymentNotification        paymentNotification = payment.getPaymentNotification().get();
        DataMessage.Payment.Notification.MobileCoin.Builder mobileCoinPayment   = new DataMessage.Payment.Notification.MobileCoin.Builder().receipt(ByteString.of(paymentNotification.getReceipt()));
        DataMessage.Payment.Notification.Builder            paymentBuilder      = new DataMessage.Payment.Notification.Builder()
                                                                                                                      .note(paymentNotification.getNote())
                                                                                                                      .mobileCoin(mobileCoinPayment.build());

        builder.payment(new DataMessage.Payment.Builder().notification(paymentBuilder.build()).build());
      } else if (payment.getPaymentActivation().isPresent()) {
        DataMessage.Payment.Activation.Builder activationBuilder = new DataMessage.Payment.Activation.Builder().type(payment.getPaymentActivation().get().getType());
        builder.payment(new DataMessage.Payment.Builder().activation(activationBuilder.build()).build());
      }
        builder.requiredProtocolVersion(Math.max(DataMessage.ProtocolVersion.PAYMENTS.getValue(), builder.requiredProtocolVersion));
    }

    if (message.getStoryContext().isPresent()) {
      SignalServiceDataMessage.StoryContext storyContext = message.getStoryContext().get();

      builder.storyContext(new DataMessage.StoryContext.Builder()
                                                       .authorAci(storyContext.getAuthorServiceId().toString())
                                                       .sentTimestamp(storyContext.getSentTimestamp())
                                                       .build());
    }

    if (message.getGiftBadge().isPresent()) {
      SignalServiceDataMessage.GiftBadge giftBadge = message.getGiftBadge().get();

      builder.giftBadge(new DataMessage.GiftBadge.Builder()
                                                 .receiptCredentialPresentation(ByteString.of(giftBadge.getReceiptCredentialPresentation().serialize()))
                                                 .build());
    }

    if (message.getBodyRanges().isPresent()) {
      List<BodyRange> bodyRanges = new ArrayList<>(builder.bodyRanges);
      bodyRanges.addAll(message.getBodyRanges().get());
      builder.bodyRanges(bodyRanges);
    }

    builder.timestamp(message.getTimestamp());

    return builder;
  }

  private Preview createPreview(SignalServicePreview preview) throws IOException {
    Preview.Builder previewBuilder = new Preview.Builder()
                                                .title(preview.getTitle())
                                                .description(preview.getDescription())
                                                .date(preview.getDate())
                                                .url(preview.getUrl());

    if (preview.getImage().isPresent()) {
      if (preview.getImage().get().isStream()) {
        previewBuilder.image(createAttachmentPointer(preview.getImage().get().asStream()));
      } else {
        previewBuilder.image(createAttachmentPointer(preview.getImage().get().asPointer()));
      }
    }

    return previewBuilder.build();
  }

  private Content createCallContent(SignalServiceCallMessage callMessage) {
    Content.Builder     container = new Content.Builder();
    CallMessage.Builder builder   = new CallMessage.Builder();

    if (callMessage.getOfferMessage().isPresent()) {
      OfferMessage offer = callMessage.getOfferMessage().get();
      CallMessage.Offer.Builder offerBuilder = new CallMessage.Offer.Builder()
                                                                    .id(offer.getId())
                                                                    .type(offer.getType().getProtoType());

      if (offer.getOpaque() != null) {
        offerBuilder.opaque(ByteString.of(offer.getOpaque()));
      }

      builder.offer(offerBuilder.build());
    } else if (callMessage.getAnswerMessage().isPresent()) {
      AnswerMessage answer = callMessage.getAnswerMessage().get();
      CallMessage.Answer.Builder answerBuilder = new CallMessage.Answer.Builder()
                                                                       .id(answer.getId());

      if (answer.getOpaque() != null) {
        answerBuilder.opaque(ByteString.of(answer.getOpaque()));
      }

      builder.answer(answerBuilder.build());
    } else if (callMessage.getIceUpdateMessages().isPresent()) {
      List<IceUpdateMessage> updates = callMessage.getIceUpdateMessages().get();
      List<CallMessage.IceUpdate> iceUpdates = new ArrayList<>(updates.size());
      for (IceUpdateMessage update : updates) {
        CallMessage.IceUpdate.Builder iceBuilder = new CallMessage.IceUpdate.Builder()
                                                                            .id(update.getId());

        if (update.getOpaque() != null) {
          iceBuilder.opaque(ByteString.of(update.getOpaque()));
        }

        iceUpdates.add(iceBuilder.build());
      }
      builder.iceUpdate(iceUpdates);
    } else if (callMessage.getHangupMessage().isPresent()) {
      CallMessage.Hangup.Type    protoType        = callMessage.getHangupMessage().get().getType().getProtoType();
      CallMessage.Hangup.Builder builderForHangup = new CallMessage.Hangup.Builder()
                                                                          .type(protoType)
                                                                          .id(callMessage.getHangupMessage().get().getId());

      if (protoType != CallMessage.Hangup.Type.HANGUP_NORMAL) {
        builderForHangup.deviceId(callMessage.getHangupMessage().get().getDeviceId());
      }

      builder.hangup(builderForHangup.build());
    } else if (callMessage.getBusyMessage().isPresent()) {
      builder.busy(new CallMessage.Busy.Builder().id(callMessage.getBusyMessage().get().getId()).build());
    } else if (callMessage.getOpaqueMessage().isPresent()) {
      OpaqueMessage              opaqueMessage = callMessage.getOpaqueMessage().get();
      ByteString                 data          = ByteString.of(opaqueMessage.getOpaque());
      CallMessage.Opaque.Urgency urgency       = opaqueMessage.getUrgency().toProto();

      builder.opaque(new CallMessage.Opaque.Builder().data_(data).urgency(urgency).build());
    }

    if (callMessage.getDestinationDeviceId().isPresent()) {
      builder.destinationDeviceId(callMessage.getDestinationDeviceId().get());
    }

    container.callMessage(builder.build());
    return container.build();
  }

  private Content createMultiDeviceContactsContent(SignalServiceAttachmentStream contacts, boolean complete) throws IOException {
    Content.Builder     container = new Content.Builder();
    SyncMessage.Builder builder   = createSyncMessageBuilder();
    builder.contacts(new SyncMessage.Contacts.Builder()
                                             .blob(createAttachmentPointer(contacts))
                                             .complete(complete)
                                             .build());

    return container.syncMessage(builder.build()).build();
  }

  private Content createMultiDeviceSentTranscriptContent(SentTranscriptMessage transcript) throws IOException {
    SignalServiceAddress address = transcript.getDestination().get();
    Content              content = createMessageContent(transcript);
    SendMessageResult    result  = SendMessageResult.success(address, Collections.emptyList(), false, true, -1, Optional.ofNullable(content));


    return createMultiDeviceSentTranscriptContent(content,
                                                  Optional.of(address),
                                                  transcript.getTimestamp(),
                                                  Collections.singletonList(result),
                                                  transcript.isRecipientUpdate(),
                                                  transcript.getStoryMessageRecipients());
  }

  private Content createMultiDeviceSentTranscriptContent(Content content, Optional<SignalServiceAddress> recipient,
                                                         long timestamp, List<SendMessageResult> sendMessageResults,
                                                         boolean isRecipientUpdate,
                                                         Set<SignalServiceStoryMessageRecipient> storyMessageRecipients)
  {
    Content.Builder          container    = new Content.Builder();
    SyncMessage.Builder      syncMessage  = createSyncMessageBuilder();
    SyncMessage.Sent.Builder sentMessage  = new SyncMessage.Sent.Builder();
    DataMessage              dataMessage  = content != null && content.dataMessage != null ? content.dataMessage : null;
    StoryMessage             storyMessage = content != null && content.storyMessage != null ? content.storyMessage : null;
    EditMessage              editMessage  = content != null && content.editMessage != null ? content.editMessage : null;

    sentMessage.timestamp(timestamp);

    List<SyncMessage.Sent.UnidentifiedDeliveryStatus> unidentifiedDeliveryStatuses = new ArrayList<>(sendMessageResults.size());
    for (SendMessageResult result : sendMessageResults) {
      if (result.getSuccess() != null) {
        ByteString identity = null;

        if (result.getAddress().getServiceId() instanceof PNI) {
          IdentityKey identityKey = aciStore.getIdentity(result.getAddress().getServiceId().toProtocolAddress(SignalServiceAddress.DEFAULT_DEVICE_ID));
          if (identityKey != null) {
            identity = ByteString.of(identityKey.getPublicKey().serialize());
          } else {
            Log.w(TAG, "[" + timestamp + "] Could not find an identity for PNI when sending sync message! " + result.getAddress().getServiceId());
          }
        }

        unidentifiedDeliveryStatuses.add(new SyncMessage.Sent.UnidentifiedDeliveryStatus.Builder()
                                                                                        .destinationServiceId(result.getAddress().getServiceId().toString())
                                                                                        .unidentified(false)
                                                                                        .destinationIdentityKey(identity)
                                                                                        .build());
      }
    }
    sentMessage.unidentifiedStatus(unidentifiedDeliveryStatuses);

    if (recipient.isPresent()) {
      sentMessage.destinationServiceId(recipient.get().getServiceId().toString());
      if (recipient.get().getNumber().isPresent()) {
        sentMessage.destinationE164(recipient.get().getNumber().get());
      }
    }

    if (dataMessage != null) {
      sentMessage.message(dataMessage);
      if (dataMessage.expireTimer != null && dataMessage.expireTimer > 0) {
        sentMessage.expirationStartTimestamp(System.currentTimeMillis());
      }

      if (dataMessage.isViewOnce != null && dataMessage.isViewOnce) {
        dataMessage = dataMessage.newBuilder().attachments(Collections.emptyList()).build();
        sentMessage.message(dataMessage);
      }
    }

    if (storyMessage != null) {
      sentMessage.storyMessage(storyMessage);
    }

    if (editMessage != null) {
      sentMessage.editMessage(editMessage);
    }

    Set<SyncMessage.Sent.StoryMessageRecipient> storyMessageRecipientsSet = storyMessageRecipients.stream()
                                                                                                  .map(this::createStoryMessageRecipient)
                                                                                                  .collect(Collectors.toSet());
    sentMessage.storyMessageRecipients(new ArrayList<>(storyMessageRecipientsSet));

    sentMessage.isRecipientUpdate(isRecipientUpdate);

    return container.syncMessage(syncMessage.sent(sentMessage.build()).build()).build();
  }
  
  private SyncMessage.Sent.StoryMessageRecipient createStoryMessageRecipient(SignalServiceStoryMessageRecipient storyMessageRecipient) {
    return new SyncMessage.Sent.StoryMessageRecipient.Builder()
                                                     .distributionListIds(storyMessageRecipient.getDistributionListIds())
                                                     .destinationServiceId(storyMessageRecipient.getSignalServiceAddress().getIdentifier())
                                                     .isAllowedToReply(storyMessageRecipient.isAllowedToReply())
                                                     .build();
  }

  private Content createMultiDeviceReadContent(List<ReadMessage> readMessages) {
    Content.Builder     container = new Content.Builder();
    SyncMessage.Builder builder   = createSyncMessageBuilder();

    builder.read(
        readMessages.stream()
                    .map(readMessage -> new SyncMessage.Read.Builder()
                                                            .timestamp(readMessage.getTimestamp())
                                                            .senderAci(readMessage.getSender().toString())
                                                            .build())
                    .collect(Collectors.toList())
    );

    return container.syncMessage(builder.build()).build();
  }

  private Content createMultiDeviceViewedContent(List<ViewedMessage> readMessages) {
    Content.Builder     container = new Content.Builder();
    SyncMessage.Builder builder   = createSyncMessageBuilder();

    builder.viewed(
        readMessages.stream()
                    .map(readMessage -> new SyncMessage.Viewed.Builder()
                                                              .timestamp(readMessage.getTimestamp())
                                                              .senderAci(readMessage.getSender().toString())
                                                              .build())
                    .collect(Collectors.toList())
    );

    return container.syncMessage(builder.build()).build();
  }

  private Content createMultiDeviceViewOnceOpenContent(ViewOnceOpenMessage readMessage) {
    Content.Builder                  container       = new Content.Builder();
    SyncMessage.Builder              builder         = createSyncMessageBuilder();

    builder.viewOnceOpen(new SyncMessage.ViewOnceOpen.Builder()
                                                     .timestamp(readMessage.getTimestamp())
                                                     .senderAci(readMessage.getSender().toString())
                                                     .build());

    return container.syncMessage(builder.build()).build();
  }

  private Content createMultiDeviceBlockedContent(BlockedListMessage blocked) {
    Content.Builder             container      = new Content.Builder();
    SyncMessage.Builder         syncMessage    = createSyncMessageBuilder();
    SyncMessage.Blocked.Builder blockedMessage = new SyncMessage.Blocked.Builder();

    blockedMessage.acis(blocked.getAddresses().stream().map(a -> a.getServiceId().toString()).collect(Collectors.toList()));
    blockedMessage.numbers(blocked.getAddresses().stream().filter(a -> a.getNumber().isPresent()).map(a -> a.getNumber().get()).collect(Collectors.toList()));
    blockedMessage.groupIds(blocked.getGroupIds().stream().map(ByteString::of).collect(Collectors.toList()));

    return container.syncMessage(syncMessage.blocked(blockedMessage.build()).build()).build();
  }

  private Content createMultiDeviceConfigurationContent(ConfigurationMessage configuration) {
    Content.Builder                   container            = new Content.Builder();
    SyncMessage.Builder               syncMessage          = createSyncMessageBuilder();
    SyncMessage.Configuration.Builder configurationMessage = new SyncMessage.Configuration.Builder();

    if (configuration.getReadReceipts().isPresent()) {
      configurationMessage.readReceipts(configuration.getReadReceipts().get());
    }

    if (configuration.getUnidentifiedDeliveryIndicators().isPresent()) {
      configurationMessage.unidentifiedDeliveryIndicators(configuration.getUnidentifiedDeliveryIndicators().get());
    }

    if (configuration.getTypingIndicators().isPresent()) {
      configurationMessage.typingIndicators(configuration.getTypingIndicators().get());
    }

    if (configuration.getLinkPreviews().isPresent()) {
      configurationMessage.linkPreviews(configuration.getLinkPreviews().get());
    }

    configurationMessage.provisioningVersion(ProvisioningVersion.CURRENT.getValue());

    return container.syncMessage(syncMessage.configuration(configurationMessage.build()).build()).build();
  }

  private Content createMultiDeviceStickerPackOperationContent(List<StickerPackOperationMessage> stickerPackOperations) {
    Content.Builder     container   = new Content.Builder();
    SyncMessage.Builder syncMessage = createSyncMessageBuilder();

    List<SyncMessage.StickerPackOperation> stickerPackOperationProtos = new ArrayList<>(stickerPackOperations.size());
    for (StickerPackOperationMessage stickerPackOperation : stickerPackOperations) {
      SyncMessage.StickerPackOperation.Builder builder = new SyncMessage.StickerPackOperation.Builder();

      if (stickerPackOperation.getPackId().isPresent()) {
        builder.packId(ByteString.of(stickerPackOperation.getPackId().get()));
      }

      if (stickerPackOperation.getPackKey().isPresent()) {
        builder.packKey(ByteString.of(stickerPackOperation.getPackKey().get()));
      }

      if (stickerPackOperation.getType().isPresent()) {
        switch (stickerPackOperation.getType().get()) {
          case INSTALL:
            builder.type(SyncMessage.StickerPackOperation.Type.INSTALL);
            break;
          case REMOVE:
            builder.type(SyncMessage.StickerPackOperation.Type.REMOVE);
            break;
        }
      }

      stickerPackOperationProtos.add(builder.build());
    }

    return container.syncMessage(syncMessage.stickerPackOperation(stickerPackOperationProtos).build()).build();
  }

  private Content createMultiDeviceFetchTypeContent(SignalServiceSyncMessage.FetchType fetchType) {
    Content.Builder                 container    = new Content.Builder();
    SyncMessage.Builder             syncMessage  = createSyncMessageBuilder();
    SyncMessage.FetchLatest.Builder fetchMessage = new SyncMessage.FetchLatest.Builder();

    switch (fetchType) {
      case LOCAL_PROFILE:
        fetchMessage.type(SyncMessage.FetchLatest.Type.LOCAL_PROFILE);
        break;
      case STORAGE_MANIFEST:
        fetchMessage.type(SyncMessage.FetchLatest.Type.STORAGE_MANIFEST);
        break;
      case SUBSCRIPTION_STATUS:
       fetchMessage.type(SyncMessage.FetchLatest.Type.SUBSCRIPTION_STATUS);
        break;
      default:
        Log.w(TAG, "Unknown fetch type!");
        break;
    }

    return container.syncMessage(syncMessage.fetchLatest(fetchMessage.build()).build()).build();
  }

  private Content createMultiDeviceMessageRequestResponseContent(MessageRequestResponseMessage message) {
    Content.Builder container = new Content.Builder();
    SyncMessage.Builder syncMessage = createSyncMessageBuilder();
    SyncMessage.MessageRequestResponse.Builder responseMessage = new SyncMessage.MessageRequestResponse.Builder();

    if (message.getGroupId().isPresent()) {
      responseMessage.groupId(ByteString.of(message.getGroupId().get()));
    }

    if (message.getPerson().isPresent()) {
      responseMessage.threadAci(message.getPerson().get().toString());
    }

    switch (message.getType()) {
      case ACCEPT:
        responseMessage.type(SyncMessage.MessageRequestResponse.Type.ACCEPT);
        break;
      case DELETE:
        responseMessage.type(SyncMessage.MessageRequestResponse.Type.DELETE);
        break;
      case BLOCK:
        responseMessage.type(SyncMessage.MessageRequestResponse.Type.BLOCK);
        break;
      case BLOCK_AND_DELETE:
        responseMessage.type(SyncMessage.MessageRequestResponse.Type.BLOCK_AND_DELETE);
        break;
      case SPAM:
        responseMessage.type(SyncMessage.MessageRequestResponse.Type.SPAM);
        break;
      case BLOCK_AND_SPAM:
        responseMessage.type(SyncMessage.MessageRequestResponse.Type.BLOCK_AND_SPAM);
        break;
      default:
        Log.w(TAG, "Unknown type!");
        responseMessage.type(SyncMessage.MessageRequestResponse.Type.UNKNOWN);
        break;
    }

    syncMessage.messageRequestResponse(responseMessage.build());

    return container.syncMessage(syncMessage.build()).build();
  }

  private Content createMultiDeviceOutgoingPaymentContent(OutgoingPaymentMessage message) {
    Content.Builder                     container      = new Content.Builder();
    SyncMessage.Builder                 syncMessage    = createSyncMessageBuilder();
    SyncMessage.OutgoingPayment.Builder paymentMessage = new SyncMessage.OutgoingPayment.Builder();

    if (message.getRecipient().isPresent()) {
      paymentMessage.recipientServiceId(message.getRecipient().get().toString());
    }

    if (message.getNote().isPresent()) {
      paymentMessage.note(message.getNote().get());
    }

    try {
      SyncMessage.OutgoingPayment.MobileCoin.Builder mobileCoinBuilder = new SyncMessage.OutgoingPayment.MobileCoin.Builder();

      if (message.getAddress().isPresent()) {
        mobileCoinBuilder.recipientAddress(ByteString.of(message.getAddress().get()));
      }
      mobileCoinBuilder.amountPicoMob(Uint64Util.bigIntegerToUInt64(message.getAmount().toPicoMobBigInteger()))
                       .feePicoMob(Uint64Util.bigIntegerToUInt64(message.getFee().toPicoMobBigInteger()))
                       .receipt(message.getReceipt())
                       .ledgerBlockTimestamp(message.getBlockTimestamp())
                       .ledgerBlockIndex(message.getBlockIndex())
                       .outputPublicKeys(message.getPublicKeys())
                       .spentKeyImages(message.getKeyImages());

      paymentMessage.mobileCoin(mobileCoinBuilder.build());
    } catch (Uint64RangeException e) {
      throw new AssertionError(e);
    }

    syncMessage.outgoingPayment(paymentMessage.build());

    return container.syncMessage(syncMessage.build()).build();
  }

  private Content createMultiDeviceSyncKeysContent(KeysMessage keysMessage) {
    Content.Builder          container   = new Content.Builder();
    SyncMessage.Builder      syncMessage = createSyncMessageBuilder();
    SyncMessage.Keys.Builder builder     = new SyncMessage.Keys.Builder();

    if (keysMessage.getStorageService().isPresent()) {
      builder.storageService(ByteString.of(keysMessage.getStorageService().get().serialize()));
    }

    if (keysMessage.getMaster().isPresent()) {
      builder.master(ByteString.of(keysMessage.getMaster().get().serialize()));
    }

    if (builder.storageService == null && builder.master == null) {
      Log.w(TAG, "Invalid keys message!");
    }

    return container.syncMessage(syncMessage.keys(builder.build()).build()).build();
  }

  private Content createMultiDeviceVerifiedContent(VerifiedMessage verifiedMessage, byte[] nullMessage) {
    Content.Builder     container              = new Content.Builder();
    SyncMessage.Builder syncMessage            = createSyncMessageBuilder();
    Verified.Builder    verifiedMessageBuilder = new Verified.Builder();

    verifiedMessageBuilder.nullMessage(ByteString.of(nullMessage));
    verifiedMessageBuilder.identityKey(ByteString.of(verifiedMessage.getIdentityKey().serialize()));
    verifiedMessageBuilder.destinationAci(verifiedMessage.getDestination().getServiceId().toString());


    switch (verifiedMessage.getVerified()) {
      case DEFAULT:    verifiedMessageBuilder.state(Verified.State.DEFAULT);    break;
      case VERIFIED:   verifiedMessageBuilder.state(Verified.State.VERIFIED);   break;
      case UNVERIFIED: verifiedMessageBuilder.state(Verified.State.UNVERIFIED); break;
      default:         throw new AssertionError("Unknown: " + verifiedMessage.getVerified());
    }

    syncMessage.verified(verifiedMessageBuilder.build());
    return container.syncMessage(syncMessage.build()).build();
  }

  private Content createRequestContent(SyncMessage.Request request) throws IOException {
    if (localDeviceId == SignalServiceAddress.DEFAULT_DEVICE_ID) {
      throw new IOException("Sync requests should only be sent from a linked device");
    }

    Content.Builder     container = new Content.Builder();
    SyncMessage.Builder builder   = createSyncMessageBuilder().request(request);

    return container.syncMessage(builder.build()).build();
  }

  private Content createCallEventContent(SyncMessage.CallEvent proto) {
    Content.Builder     container = new Content.Builder();
    SyncMessage.Builder builder   = createSyncMessageBuilder().callEvent(proto);

    return container.syncMessage(builder.build()).build();
  }

  private Content createCallLinkUpdateContent(SyncMessage.CallLinkUpdate proto) {
    Content.Builder     container = new Content.Builder();
    SyncMessage.Builder builder   = createSyncMessageBuilder().callLinkUpdate(proto);

    return container.syncMessage(builder.build()).build();
  }

  private Content createCallLogEventContent(SyncMessage.CallLogEvent proto) {
    Content.Builder     container = new Content.Builder();
    SyncMessage.Builder builder   = createSyncMessageBuilder().callLogEvent(proto);

    return container.syncMessage(builder.build()).build();
  }

  private SyncMessage.Builder createSyncMessageBuilder() {
    byte[]       padding = Util.getRandomLengthSecretBytes(512);

    SyncMessage.Builder builder = new SyncMessage.Builder();
    builder.padding(ByteString.of(padding));

    return builder;
  }

  private static GroupContextV2 createGroupContent(SignalServiceGroupV2 group) {
    GroupContextV2.Builder builder = new GroupContextV2.Builder()
                                                       .masterKey(ByteString.of(group.getMasterKey().serialize()))
                                                       .revision(group.getRevision());

    byte[] signedGroupChange = group.getSignedGroupChange();
    if (signedGroupChange != null && signedGroupChange.length <= 2048) {
      builder.groupChange(ByteString.of(signedGroupChange));
    }

    return builder.build();
  }

  private List<DataMessage.Contact> createSharedContactContent(List<SharedContact> contacts) throws IOException {
    List<DataMessage.Contact> results = new LinkedList<>();

    for (SharedContact contact : contacts) {
      DataMessage.Contact.Name.Builder nameBuilder = new DataMessage.Contact.Name.Builder();

      if (contact.getName().getFamily().isPresent())   nameBuilder.familyName(contact.getName().getFamily().get());
      if (contact.getName().getGiven().isPresent())    nameBuilder.givenName(contact.getName().getGiven().get());
      if (contact.getName().getMiddle().isPresent())   nameBuilder.middleName(contact.getName().getMiddle().get());
      if (contact.getName().getPrefix().isPresent())   nameBuilder.prefix(contact.getName().getPrefix().get());
      if (contact.getName().getSuffix().isPresent())   nameBuilder.suffix(contact.getName().getSuffix().get());
      if (contact.getName().getNickname().isPresent()) nameBuilder.nickname(contact.getName().getNickname().get());

      DataMessage.Contact.Builder contactBuilder = new DataMessage.Contact.Builder().name(nameBuilder.build());

      if (contact.getAddress().isPresent()) {
        List<DataMessage.Contact.PostalAddress> postalAddresses = new ArrayList<>(contact.getAddress().get().size());
        for (SharedContact.PostalAddress address : contact.getAddress().get()) {
          DataMessage.Contact.PostalAddress.Builder addressBuilder = new DataMessage.Contact.PostalAddress.Builder();

          switch (address.getType()) {
            case HOME:   addressBuilder.type(DataMessage.Contact.PostalAddress.Type.HOME); break;
            case WORK:   addressBuilder.type(DataMessage.Contact.PostalAddress.Type.WORK); break;
            case CUSTOM: addressBuilder.type(DataMessage.Contact.PostalAddress.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + address.getType());
          }

          if (address.getCity().isPresent())         addressBuilder.city(address.getCity().get());
          if (address.getCountry().isPresent())      addressBuilder.country(address.getCountry().get());
          if (address.getLabel().isPresent())        addressBuilder.label(address.getLabel().get());
          if (address.getNeighborhood().isPresent()) addressBuilder.neighborhood(address.getNeighborhood().get());
          if (address.getPobox().isPresent())        addressBuilder.pobox(address.getPobox().get());
          if (address.getPostcode().isPresent())     addressBuilder.postcode(address.getPostcode().get());
          if (address.getRegion().isPresent())       addressBuilder.region(address.getRegion().get());
          if (address.getStreet().isPresent())       addressBuilder.street(address.getStreet().get());

          postalAddresses.add(addressBuilder.build());
        }
        contactBuilder.address(postalAddresses);
      }

      if (contact.getEmail().isPresent()) {
        List<DataMessage.Contact.Email> emails = new ArrayList<>(contact.getEmail().get().size());
        for (SharedContact.Email email : contact.getEmail().get()) {
          DataMessage.Contact.Email.Builder emailBuilder = new DataMessage.Contact.Email.Builder().value_(email.getValue());

          switch (email.getType()) {
            case HOME:   emailBuilder.type(DataMessage.Contact.Email.Type.HOME);   break;
            case WORK:   emailBuilder.type(DataMessage.Contact.Email.Type.WORK);   break;
            case MOBILE: emailBuilder.type(DataMessage.Contact.Email.Type.MOBILE); break;
            case CUSTOM: emailBuilder.type(DataMessage.Contact.Email.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + email.getType());
          }

          if (email.getLabel().isPresent()) emailBuilder.label(email.getLabel().get());

          emails.add(emailBuilder.build());
        }
        contactBuilder.email(emails);
      }

      if (contact.getPhone().isPresent()) {
        List<DataMessage.Contact.Phone> phones = new ArrayList<>(contact.getPhone().get().size());
        for (SharedContact.Phone phone : contact.getPhone().get()) {
          DataMessage.Contact.Phone.Builder phoneBuilder = new DataMessage.Contact.Phone.Builder().value_(phone.getValue());

          switch (phone.getType()) {
            case HOME:   phoneBuilder.type(DataMessage.Contact.Phone.Type.HOME);   break;
            case WORK:   phoneBuilder.type(DataMessage.Contact.Phone.Type.WORK);   break;
            case MOBILE: phoneBuilder.type(DataMessage.Contact.Phone.Type.MOBILE); break;
            case CUSTOM: phoneBuilder.type(DataMessage.Contact.Phone.Type.CUSTOM); break;
            default:     throw new AssertionError("Unknown type: " + phone.getType());
          }

          if (phone.getLabel().isPresent()) phoneBuilder.label(phone.getLabel().get());

          phones.add(phoneBuilder.build());
        }
        contactBuilder.number(phones);
      }

      if (contact.getAvatar().isPresent()) {
        AttachmentPointer pointer = contact.getAvatar().get().getAttachment().isStream() ? createAttachmentPointer(contact.getAvatar().get().getAttachment().asStream())
                                                                                         : createAttachmentPointer(contact.getAvatar().get().getAttachment().asPointer());
        contactBuilder.avatar(new DataMessage.Contact.Avatar.Builder()
                                                            .avatar(pointer)
                                                            .isProfile(contact.getAvatar().get().isProfile())
                                                            .build());
      }

      if (contact.getOrganization().isPresent()) {
        contactBuilder.organization(contact.getOrganization().get());
      }

      results.add(contactBuilder.build());
    }

    return results;
  }

  private SignalServiceSyncMessage createSelfSendSyncMessageForStory(SignalServiceStoryMessage message,
                                                                     long sentTimestamp,
                                                                     boolean isRecipientUpdate,
                                                                     Set<SignalServiceStoryMessageRecipient> manifest)
  {
    SentTranscriptMessage transcript = new SentTranscriptMessage(Optional.of(localAddress),
                                                                 sentTimestamp,
                                                                 Optional.empty(),
                                                                 0,
                                                                 Collections.singletonMap(localAddress.getServiceId(), false),
                                                                 isRecipientUpdate,
                                                                 Optional.of(message),
                                                                 manifest,
                                                                 Optional.empty());

    return SignalServiceSyncMessage.forSentTranscript(transcript);
  }

  private SignalServiceSyncMessage createSelfSendSyncMessage(SignalServiceDataMessage message) {
    SentTranscriptMessage transcript = new SentTranscriptMessage(Optional.of(localAddress),
                                                                 message.getTimestamp(),
                                                                 Optional.of(message),
                                                                 message.getExpiresInSeconds(),
                                                                 Collections.singletonMap(localAddress.getServiceId(), false),
                                                                 false,
                                                                 Optional.empty(),
                                                                 Collections.emptySet(),
                                                                 Optional.empty());
    return SignalServiceSyncMessage.forSentTranscript(transcript);
  }

  private SignalServiceSyncMessage createSelfSendSyncEditMessage(SignalServiceEditMessage message) {
    SentTranscriptMessage transcript = new SentTranscriptMessage(Optional.of(localAddress),
                                                                 message.getDataMessage().getTimestamp(),
                                                                 Optional.empty(),
                                                                 message.getDataMessage().getExpiresInSeconds(),
                                                                 Collections.singletonMap(localAddress.getServiceId(), false),
                                                                 false,
                                                                 Optional.empty(),
                                                                 Collections.emptySet(),
                                                                 Optional.of(message));
    return SignalServiceSyncMessage.forSentTranscript(transcript);
  }

  private SendMessageResult sendMessage(SignalServiceAddress recipient,
                                        @Nullable SealedSenderAccess sealedSenderAccess,
                                        long timestamp,
                                        EnvelopeContent content,
                                        boolean online,
                                        CancelationSignal cancelationSignal,
                                        SendEvents sendEvents,
                                        boolean urgent,
                                        boolean story)
      throws UntrustedIdentityException, IOException
  {
    enforceMaxContentSize(content);

    long startTime = System.currentTimeMillis();

    for (int i = 0; i < RETRY_COUNT; i++) {
      if (cancelationSignal != null && cancelationSignal.isCanceled()) {
        throw new CancelationException();
      }

      try {
        OutgoingPushMessageList messages = getEncryptedMessages(recipient,
                                                                sealedSenderAccess,
                                                                timestamp,
                                                                content,
                                                                online,
                                                                urgent,
                                                                story);
        if (i == 0 && sendEvents != null) {
          sendEvents.onMessageEncrypted();
        }

        if (content.getContent().isPresent() && content.getContent().get().syncMessage != null && content.getContent().get().syncMessage.sent != null) {
          Log.d(TAG, "[sendMessage][" + timestamp + "] Sending a sent sync message to devices: " + messages.getDevices());
        } else if (content.getContent().isPresent() && content.getContent().get().senderKeyDistributionMessage != null) {
          Log.d(TAG, "[sendMessage][" + timestamp + "] Sending a SKDM to " + messages.getDestination() + " for devices: " + messages.getDevices() + (content.getContent().get().dataMessage != null ? " (it's piggy-backing on a DataMessage)" : ""));
        }

        if (cancelationSignal != null && cancelationSignal.isCanceled()) {
          throw new CancelationException();
        }

        try {
          SendMessageResponse response = new MessagingService.SendResponseProcessor<>(messagingService.send(messages, sealedSenderAccess, story).blockingGet()).getResultOrThrow();
          return SendMessageResult.success(recipient, messages.getDevices(), response.sentUnidentified(), response.getNeedsSync() || aciStore.isMultiDevice(), System.currentTimeMillis() - startTime, content.getContent());
        } catch (InvalidUnidentifiedAccessHeaderException | UnregisteredUserException | MismatchedDevicesException | StaleDevicesException e) {
          // Non-technical failures shouldn't be retried with socket
          throw e;
        } catch (WebSocketUnavailableException e) {
          String pipe = sealedSenderAccess == null ? "Pipe" : "Unidentified pipe";
          Log.i(TAG, "[sendMessage][" + timestamp + "] " + pipe + " unavailable, falling back... (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        } catch (IOException e) {
          String pipe = sealedSenderAccess == null ? "Pipe" : "Unidentified pipe";
          Throwable cause = e;
          if (e.getCause() != null) {
            cause = e.getCause();
          }
          Log.w(TAG, "[sendMessage][" + timestamp + "] " + pipe + " failed, falling back... (" + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")");
        }

        if (cancelationSignal != null && cancelationSignal.isCanceled()) {
          throw new CancelationException();
        }

        SendMessageResponse response = socket.sendMessage(messages, sealedSenderAccess, story);

        return SendMessageResult.success(recipient, messages.getDevices(), response.sentUnidentified(), response.getNeedsSync() || aciStore.isMultiDevice(), System.currentTimeMillis() - startTime, content.getContent());

      } catch (InvalidKeyException ike) {
        Log.w(TAG, ike);
        if (sealedSenderAccess != null) {
          sealedSenderAccess = sealedSenderAccess.switchToFallback();
        }
      } catch (AuthorizationFailedException afe) {
        if (sealedSenderAccess != null) {
          Log.w(TAG, "Got an AuthorizationFailedException when trying to send using sealed sender. Falling back.");
          sealedSenderAccess = sealedSenderAccess.switchToFallback();
        } else {
          Log.w(TAG, "Got an AuthorizationFailedException without using sealed sender!", afe);
          throw afe;
        }
      } catch (MismatchedDevicesException mde) {
        Log.w(TAG, "[sendMessage][" + timestamp + "] Handling mismatched devices. (" + mde.getMessage() + ")");
        handleMismatchedDevices(socket, recipient, mde.getMismatchedDevices());
      } catch (StaleDevicesException ste) {
        Log.w(TAG, "[sendMessage][" + timestamp + "] Handling stale devices. (" + ste.getMessage() + ")");
        handleStaleDevices(recipient, ste.getStaleDevices());
      }
    }

    throw new IOException("Failed to resolve conflicts after " + RETRY_COUNT + " attempts!");
  }

  /**
   * Send a message to multiple recipients.
   *
   * @return An unordered list of a {@link SendMessageResult} for each send.
   * @throws IOException - Unknown failure or a failure not representable by an unsuccessful {@code SendMessageResult}.
   */
  private List<SendMessageResult> sendMessage(List<SignalServiceAddress> recipients,
                                              List<SealedSenderAccess> sealedSenderAccesses,
                                              long timestamp,
                                              EnvelopeContent content,
                                              boolean online,
                                              PartialSendCompleteListener partialListener,
                                              CancelationSignal cancelationSignal,
                                              @Nullable SendEvents sendEvents,
                                              boolean urgent,
                                              boolean story)
      throws IOException
  {
    Log.d(TAG, "[" + timestamp + "] Sending to " + recipients.size() + " recipients.");
    enforceMaxContentSize(content);

    long                                startTime                  = System.currentTimeMillis();
    List<Observable<SendMessageResult>> singleResults              = new LinkedList<>();
    Iterator<SignalServiceAddress>      recipientIterator          = recipients.iterator();
    Iterator<SealedSenderAccess>        sealedSenderAccessIterator = sealedSenderAccesses.iterator();

    while (recipientIterator.hasNext()) {
      SignalServiceAddress recipient          = recipientIterator.next();
      SealedSenderAccess   sealedSenderAccess = sealedSenderAccessIterator.next();

      singleResults.add(sendMessageRx(recipient, sealedSenderAccess, timestamp, content, online, cancelationSignal, sendEvents, urgent, story, 0).toObservable());
    }

    List<SendMessageResult> results;
    try {
      results = Observable.mergeDelayError(singleResults, Integer.MAX_VALUE, 1)
                          .observeOn(scheduler, true)
                          .scan(new ArrayList<SendMessageResult>(singleResults.size()), (state, result) -> {
                            state.add(result);
                            if (partialListener != null) {
                              partialListener.onPartialSendComplete(result);
                            }
                            return state;
                          })
                          .lastOrError()
                          .blockingGet();
    } catch (RuntimeException e) {
      Throwable cause = e instanceof CompositeException ? ((CompositeException) e).getExceptions().get(0)
                                                        : e.getCause();

      if (cause instanceof IOException) {
        throw (IOException) cause;
      } else if (cause instanceof InterruptedException) {
        throw new CancelationException(e);
      } else {
        throw e;
      }
    }

    double sendsForAverage = 0;
    for (SendMessageResult result : results) {
      if (result.getSuccess() != null && result.getSuccess().getDuration() != -1) {
        sendsForAverage++;
      }
    }

    double average = 0;
    if (sendsForAverage > 0) {
      for (SendMessageResult result : results) {
        if (result.getSuccess() != null && result.getSuccess().getDuration() != -1) {
          average += result.getSuccess().getDuration() / sendsForAverage;
        }
      }
    }

    Log.d(TAG, "[" + timestamp + "] Completed send to " + recipients.size() + " recipients in " + (System.currentTimeMillis() - startTime) + " ms, with an average time of " + Math.round(average) + " ms per send via Rx.");
    return results;
  }

  /**
   * Sends a message over the appropriate websocket, falls back to REST when unavailable, and emits a {@link SendMessageResult} for most business
   * logic error cases.
   * <p>
   * Uses a "feature" or Rx where if no {@link Single#subscribeOn(Scheduler)} operator is used, the subscribing thread is used to perform the
   * initial work. This allows the calling thread to do the starting of the send work (encryption and putting it on the wire) and can be called
   * multiple times in a loop, but allow the network transit/processing/error retry logic to run on a background thread.
   * <p>
   * Processing happens on the background thread via an {@link Single#observeOn(Scheduler)} call after the encrypt and send. Error
   * handling operators are added after the observe so they will also run on a background thread. Retry logic during error handling
   * is a recursive call, so error handling thread becomes the method "calling and subscribing" thread so all retries will perform the
   * encryption/send/processing on that background thread.
   *
   * @return A single that wraps success and business failures as a {@link SendMessageResult} but will still emit unhandled/unrecoverable
   * errors via {@code onError}
   */
  private Single<SendMessageResult> sendMessageRx(SignalServiceAddress recipient,
                                                  @Nullable SealedSenderAccess sealedSenderAccess,
                                                  long timestamp,
                                                  EnvelopeContent content,
                                                  boolean online,
                                                  CancelationSignal cancelationSignal,
                                                  @Nullable SendEvents sendEvents,
                                                  boolean urgent,
                                                  boolean story,
                                                  int retryCount)
  {
    long startTime = System.currentTimeMillis();
    enforceMaxContentSize(content);

    Single<OutgoingPushMessageList> messagesSingle = Single.fromCallable(() -> {
      OutgoingPushMessageList messages = getEncryptedMessages(recipient, sealedSenderAccess, timestamp, content, online, urgent, story);

      if (retryCount == 0 && sendEvents != null) {
        sendEvents.onMessageEncrypted();
      }

      if (content.getContent().isPresent() && content.getContent().get().syncMessage != null && content.getContent().get().syncMessage.sent != null) {
        Log.d(TAG, "[sendMessage][" + timestamp + "] Sending a sent sync message to devices: " + messages.getDevices() + " via Rx");
      } else if (content.getContent().isPresent() && content.getContent().get().senderKeyDistributionMessage != null) {
        Log.d(TAG, "[sendMessage][" + timestamp + "] Sending a SKDM to " + messages.getDestination() + " for devices: " + messages.getDevices() + (content.getContent().get().dataMessage != null ? " (it's piggy-backing on a DataMessage) via Rx" : " via Rx"));
      }

      return messages;
    });

    Single<SendMessageResult> sendWithFallback = messagesSingle
        .flatMap(messages -> {
          if (cancelationSignal != null && cancelationSignal.isCanceled()) {
            return Single.error(new CancelationException());
          }

          return messagingService.send(messages, sealedSenderAccess, story)
                                 .map(r -> new kotlin.Pair<>(messages, r));
        })
        .observeOn(scheduler)
        .flatMap(pair -> {
          final OutgoingPushMessageList              messages        = pair.getFirst();
          final ServiceResponse<SendMessageResponse> serviceResponse = pair.getSecond();

          if (serviceResponse.getResult().isPresent()) {
            SendMessageResponse response = serviceResponse.getResult().get();
            SendMessageResult   result   = SendMessageResult.success(
                recipient,
                messages.getDevices(),
                response.sentUnidentified(),
                response.getNeedsSync() || aciStore.isMultiDevice(),
                System.currentTimeMillis() - startTime,
                content.getContent()
            );
            return Single.just(result);
          } else {
            if (cancelationSignal != null && cancelationSignal.isCanceled()) {
              return Single.error(new CancelationException());
            }

            //noinspection OptionalGetWithoutIsPresent
            Throwable throwable = serviceResponse.getApplicationError().or(serviceResponse::getExecutionError).get();

            if (throwable instanceof InvalidUnidentifiedAccessHeaderException ||
                throwable instanceof UnregisteredUserException ||
                throwable instanceof MismatchedDevicesException ||
                throwable instanceof StaleDevicesException)
            {
              // Non-technical failures shouldn't be retried with socket
              return Single.error(throwable);
            } else if (throwable instanceof WebSocketUnavailableException) {
              Log.i(TAG, "[sendMessage][" + timestamp + "] " + (sealedSenderAccess != null ? "Unidentified " : "") + "pipe unavailable, falling back... (" + throwable.getClass().getSimpleName() + ": " + throwable.getMessage() + ")");
            } else if (throwable instanceof IOException) {
              Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
              Log.w(TAG, "[sendMessage][" + timestamp + "] " + (sealedSenderAccess != null ? "Unidentified " : "") + "pipe failed, falling back... (" + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")");
            }

            return Single.fromCallable(() -> {
              SendMessageResponse response = socket.sendMessage(messages, sealedSenderAccess, story);
              return SendMessageResult.success(
                  recipient,
                  messages.getDevices(),
                  response.sentUnidentified(),
                  response.getNeedsSync() || aciStore.isMultiDevice(),
                  System.currentTimeMillis() - startTime,
                  content.getContent()
              );
            }).subscribeOn(scheduler);
          }
        });

    return sendWithFallback.onErrorResumeNext(t -> {
      if (cancelationSignal != null && cancelationSignal.isCanceled()) {
        return Single.error(new CancelationException());
      }

      if (retryCount >= RETRY_COUNT) {
        return Single.error(t);
      }

      if (t instanceof InvalidKeyException) {
        Log.w(TAG, t);
        return sendMessageRx(
            recipient,
            SealedSenderAccess.NONE,
            timestamp,
            content,
            online,
            cancelationSignal,
            sendEvents,
            urgent,
            story,
            retryCount + 1
        );
      } else if (t instanceof AuthorizationFailedException) {
        if (sealedSenderAccess != null) {
          Log.w(TAG, "Got an AuthorizationFailedException when trying to send using sealed sender. Falling back.");
          return sendMessageRx(
              recipient,
              sealedSenderAccess.switchToFallback(),
              timestamp,
              content,
              online,
              cancelationSignal,
              sendEvents,
              urgent,
              story,
              retryCount + 1
          );
        } else {
          Log.w(TAG, "Got an AuthorizationFailedException without using sealed sender!", t);
          return Single.error(t);
        }
      } else if (t instanceof MismatchedDevicesException) {
        MismatchedDevicesException mde = (MismatchedDevicesException) t;
        Log.w(TAG, "[sendMessage][" + timestamp + "] Handling mismatched devices. (" + mde.getMessage() + ")");

        return Single.fromCallable(() -> {
                       handleMismatchedDevices(socket, recipient, mde.getMismatchedDevices());
                       return Unit.INSTANCE;
                     })
                     .flatMap(unused -> sendMessageRx(
                         recipient,
                         sealedSenderAccess,
                         timestamp,
                         content,
                         online,
                         cancelationSignal,
                         sendEvents,
                         urgent,
                         story,
                         retryCount + 1)
                     );
      } else if (t instanceof StaleDevicesException) {
        StaleDevicesException ste = (StaleDevicesException) t;
        Log.w(TAG, "[sendMessage][" + timestamp + "] Handling stale devices. (" + ste.getMessage() + ")");

        return Single.fromCallable(() -> {
                       handleStaleDevices(recipient, ste.getStaleDevices());
                       return Unit.INSTANCE;
                     })
                     .flatMap(unused -> sendMessageRx(
                         recipient,
                         sealedSenderAccess,
                         timestamp,
                         content,
                         online,
                         cancelationSignal,
                         sendEvents,
                         urgent,
                         story,
                         retryCount + 1)
                     );
      }

      return Single.error(t);
    }).onErrorResumeNext(t -> {
      try {
        SendMessageResult result = mapSendErrorToSendResult(t, timestamp, recipient);
        return Single.just(result);
      } catch (IOException e) {
        return Single.error(e);
      }
    });
  }

  /**
   * Converts common exceptions thrown during message sending to the appropriate {@link SendMessageResult}.
   * <p>
   * Exceptions that cannot be mapped will be rethrown as wrapped {@link IOException}s.
   */
  public static @Nonnull SendMessageResult mapSendErrorToSendResult(@Nonnull Throwable t, long timestamp, @Nonnull SignalServiceAddress recipient) throws IOException {
    if (t instanceof UntrustedIdentityException) {
      Log.w(TAG, "[" + timestamp + "] Hit identity mismatch: " + recipient.getIdentifier(), t);
      return SendMessageResult.identityFailure(recipient, ((UntrustedIdentityException) t).getIdentityKey());
    } else if (t instanceof UnregisteredUserException) {
      Log.w(TAG, "[" + timestamp + "] Hit unregistered user: " + recipient.getIdentifier());
      return SendMessageResult.unregisteredFailure(recipient);
    } else if (t instanceof PushNetworkException) {
      Log.w(TAG, "[" + timestamp + "] Hit network failure: " + recipient.getIdentifier(), t);
      return SendMessageResult.networkFailure(recipient);
    } else if (t instanceof ServerRejectedException) {
      Log.w(TAG, "[" + timestamp + "] Hit server rejection: " + recipient.getIdentifier(), t);
      throw (ServerRejectedException) t;
    } else if (t instanceof ProofRequiredException) {
      Log.w(TAG, "[" + timestamp + "] Hit proof required: " + recipient.getIdentifier(), t);
      return SendMessageResult.proofRequiredFailure(recipient, (ProofRequiredException) t);
    } else if (t instanceof RateLimitException) {
      Log.w(TAG, "[" + timestamp + "] Hit rate limit: " + recipient.getIdentifier(), t);
      return SendMessageResult.rateLimitFailure(recipient, (RateLimitException) t);
    } else if (t instanceof InvalidPreKeyException) {
      Log.w(TAG, "[" + timestamp + "] Hit invalid prekey: " + recipient.getIdentifier(), t);
      return SendMessageResult.invalidPreKeyFailure(recipient);
    } else {
      Log.w(TAG, "[" + timestamp + "] Hit unknown exception: " + recipient.getIdentifier(), t);
      throw new IOException(t);
    }
  }

  /**
   * Will send a message using sender keys to all of the specified recipients. It is assumed that
   * all of the recipients have UUIDs.
   *
   * This method will handle sending out SenderKeyDistributionMessages as necessary.
   */
  private List<SendMessageResult> sendGroupMessage(DistributionId distributionId,
                                                   List<SignalServiceAddress> recipients,
                                                   List<UnidentifiedAccess> unidentifiedAccess,
                                                   @Nullable GroupSendEndorsements groupSendEndorsements,
                                                   long timestamp,
                                                   Content content,
                                                   ContentHint contentHint,
                                                   Optional<byte[]> groupId,
                                                   boolean online,
                                                   SenderKeyGroupEvents sendEvents,
                                                   boolean urgent,
                                                   boolean story)
      throws IOException, UntrustedIdentityException, NoSessionException, InvalidKeyException, InvalidRegistrationIdException
  {
    if (recipients.isEmpty()) {
      Log.w(TAG, "[sendGroupMessage][" + timestamp + "] Empty recipient list!");
      return Collections.emptyList();
    }

    Preconditions.checkArgument(recipients.size() == unidentifiedAccess.size(), "[" + timestamp + "] Unidentified access mismatch!");

    Map<ServiceId, UnidentifiedAccess> accessBySid     = new HashMap<>();
    Iterator<SignalServiceAddress>     addressIterator = recipients.iterator();
    Iterator<UnidentifiedAccess>       accessIterator  = unidentifiedAccess.iterator();

    while (addressIterator.hasNext()) {
      accessBySid.put(addressIterator.next().getServiceId(), accessIterator.next());
    }

    SealedSenderAccess sealedSenderAccess = SealedSenderAccess.forGroupSend(groupSendEndorsements, unidentifiedAccess, story);

    for (int i = 0; i < RETRY_COUNT; i++) {
            GroupTargetInfo targetInfo         = buildGroupTargetInfo(recipients);
      final GroupTargetInfo targetInfoSnapshot = targetInfo;

      Set<SignalProtocolAddress> sharedWith            = aciStore.getSenderKeySharedWith(distributionId);
      List<SignalServiceAddress> needsSenderKeyTargets = targetInfo.destinations.stream()
                                                                                .filter(a -> !sharedWith.contains(a) || targetInfoSnapshot.sessions.get(a) == null)
                                                                                .map(a -> ServiceId.parseOrThrow(a.getName()))
                                                                                .distinct()
                                                                                .map(SignalServiceAddress::new)
                                                                                .collect(Collectors.toList());
      if (needsSenderKeyTargets.size() > 0) {
        Log.i(TAG, "[sendGroupMessage][" + timestamp + "] Need to send the distribution message to " + needsSenderKeyTargets.size() + " addresses.");
        SenderKeyDistributionMessage senderKeyDistributionMessage = getOrCreateNewGroupSession(distributionId);
        List<UnidentifiedAccess>     needsSenderKeyAccesses       = needsSenderKeyTargets.stream()
                                                                                         .map(r -> accessBySid.get(r.getServiceId()))
                                                                                         .collect(Collectors.toList());

        List<GroupSendFullToken> needsSenderKeyGroupSendTokens      = groupSendEndorsements != null ? groupSendEndorsements.forIndividuals(needsSenderKeyTargets) : null;
        List<SealedSenderAccess> needsSenderKeySealedSenderAccesses = SealedSenderAccess.forFanOutGroupSend(needsSenderKeyGroupSendTokens, sealedSenderAccess.getSenderCertificate(), needsSenderKeyAccesses);

        List<SendMessageResult> results = sendSenderKeyDistributionMessage(distributionId,
                                                                           needsSenderKeyTargets,
                                                                           needsSenderKeySealedSenderAccesses,
                                                                           senderKeyDistributionMessage,
                                                                           groupId,
                                                                           urgent,
                                                                           story && groupId.isEmpty()); // We don't want to flag SKDM's as stories for group stories, since we reuse distributionIds for normal group messages

        List<SignalServiceAddress> successes = results.stream()
                                                      .filter(SendMessageResult::isSuccess)
                                                      .map(SendMessageResult::getAddress)
                                                      .collect(Collectors.toList());

        Set<String>                successSids      = successes.stream().map(a -> a.getServiceId().toString()).collect(Collectors.toSet());
        Set<SignalProtocolAddress> successAddresses = targetInfo.destinations.stream().filter(a -> successSids.contains(a.getName())).collect(Collectors.toSet());

        aciStore.markSenderKeySharedWith(distributionId, successAddresses);

        Log.i(TAG, "[sendGroupMessage][" + timestamp + "] Successfully sent sender keys to " + successes.size() + "/" + needsSenderKeyTargets.size() + " recipients.");

        int failureCount = results.size() - successes.size();
        if (failureCount > 0) {
          Log.w(TAG, "[sendGroupMessage][" + timestamp + "] Failed to send sender keys to " + failureCount + " recipients. Sending back failed results now.");

          List<SendMessageResult> trueFailures = results.stream()
                                                        .filter(r -> !r.isSuccess())
                                                        .collect(Collectors.toList());

          Set<ServiceId> failedAddresses = trueFailures.stream()
                                                       .map(result -> result.getAddress().getServiceId())
                                                       .collect(Collectors.toSet());

          List<SendMessageResult> fakeNetworkFailures = recipients.stream()
                                                                  .filter(r -> !failedAddresses.contains(r.getServiceId()))
                                                                  .map(SendMessageResult::networkFailure)
                                                                  .collect(Collectors.toList());

          List<SendMessageResult> modifiedResults = new LinkedList<>();
          modifiedResults.addAll(trueFailures);
          modifiedResults.addAll(fakeNetworkFailures);

          return modifiedResults;
        } else {
          targetInfo = buildGroupTargetInfo(recipients);
        }
      }

      sendEvents.onSenderKeyShared();

      SignalServiceCipher cipher = new SignalServiceCipher(localAddress, localDeviceId, aciStore, sessionLock, null);

      byte[] ciphertext;
      try {
        ciphertext = cipher.encryptForGroup(distributionId, targetInfo.destinations, targetInfo.sessions, sealedSenderAccess.getSenderCertificate(), content.encode(), contentHint, groupId);
      } catch (org.signal.libsignal.protocol.UntrustedIdentityException e) {
        throw new UntrustedIdentityException("Untrusted during group encrypt", e.getName(), e.getUntrustedIdentity());
      }

      sendEvents.onMessageEncrypted();

      try {
        try {
          SendGroupMessageResponse response = new MessagingService.SendResponseProcessor<>(messagingService.sendToGroup(ciphertext, sealedSenderAccess, timestamp, online, urgent, story).blockingGet()).getResultOrThrow();
          return transformGroupResponseToMessageResults(targetInfo.devices, response, content);
        } catch (InvalidUnidentifiedAccessHeaderException | NotFoundException | GroupMismatchedDevicesException | GroupStaleDevicesException e) {
          // Non-technical failures shouldn't be retried with socket
          throw e;
        } catch (WebSocketUnavailableException e) {
          Log.i(TAG, "[sendGroupMessage][" + timestamp + "] Pipe unavailable, falling back... (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        } catch (IOException e) {
          Log.w(TAG, "[sendGroupMessage][" + timestamp + "] Pipe failed, falling back... (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
        }

        SendGroupMessageResponse response = socket.sendGroupMessage(ciphertext, sealedSenderAccess, timestamp, online, urgent, story);
        return transformGroupResponseToMessageResults(targetInfo.devices, response, content);
      } catch (GroupMismatchedDevicesException e) {
        Log.w(TAG, "[sendGroupMessage][" + timestamp + "] Handling mismatched devices. (" + e.getMessage() + ")");
        for (GroupMismatchedDevices mismatched : e.getMismatchedDevices()) {
          SignalServiceAddress address = new SignalServiceAddress(ServiceId.parseOrThrow(mismatched.getUuid()), Optional.empty());
          handleMismatchedDevices(socket, address, mismatched.getDevices());
        }
      } catch (GroupStaleDevicesException e) {
        Log.w(TAG, "[sendGroupMessage][" + timestamp + "] Handling stale devices. (" + e.getMessage() + ")");
        for (GroupStaleDevices stale : e.getStaleDevices()) {
          SignalServiceAddress address = new SignalServiceAddress(ServiceId.parseOrThrow(stale.getUuid()), Optional.empty());
          handleStaleDevices(address, stale.getDevices());
        }
      } catch (InvalidUnidentifiedAccessHeaderException e) {
        sealedSenderAccess = sealedSenderAccess.switchToFallback();
        if (sealedSenderAccess != null) {
          Log.w(TAG, "[sendGroupMessage][" + timestamp + "] Handling invalid group send endorsements. (" + e.getMessage() + ")");
        } else {
          throw e;
        }
      }

      Log.w(TAG, "[sendGroupMessage][" + timestamp + "] Attempt failed (i = " + i + ")");
    }

    throw new IOException("Failed to resolve conflicts after " + RETRY_COUNT + " attempts!");
  }

  private GroupTargetInfo buildGroupTargetInfo(List<SignalServiceAddress> recipients) {
    List<String>                              addressNames         = recipients.stream().map(SignalServiceAddress::getIdentifier).collect(Collectors.toList());
    Map<SignalProtocolAddress, SessionRecord> sessionMap           = aciStore.getAllAddressesWithActiveSessions(addressNames);
    Map<String, List<Integer>>                devicesByAddressName = new HashMap<>();

    Set<SignalProtocolAddress> destinations = new HashSet<>(sessionMap.keySet());

    destinations.addAll(recipients.stream()
                                  .map(a -> new SignalProtocolAddress(a.getIdentifier(), SignalServiceAddress.DEFAULT_DEVICE_ID))
                                  .collect(Collectors.toList()));

    for (SignalProtocolAddress destination : destinations) {
      List<Integer> devices = devicesByAddressName.containsKey(destination.getName()) ? devicesByAddressName.get(destination.getName()) : new LinkedList<>();
      devices.add(destination.getDeviceId());
      devicesByAddressName.put(destination.getName(), devices);
    }

    Map<SignalServiceAddress, List<Integer>> recipientDevices = new HashMap<>();

    for (SignalServiceAddress recipient : recipients) {
      if (devicesByAddressName.containsKey(recipient.getIdentifier())) {
        recipientDevices.put(recipient, devicesByAddressName.get(recipient.getIdentifier()));
      }
    }

    return new GroupTargetInfo(new ArrayList<>(destinations), recipientDevices, sessionMap);
  }


  private static final class GroupTargetInfo {
    private final List<SignalProtocolAddress>               destinations;
    private final Map<SignalServiceAddress, List<Integer>>  devices;
    private final Map<SignalProtocolAddress, SessionRecord> sessions;

    private GroupTargetInfo(
        List<SignalProtocolAddress> destinations,
        Map<SignalServiceAddress, List<Integer>> devices,
        Map<SignalProtocolAddress, SessionRecord> sessions) {
      this.destinations = destinations;
      this.devices      = devices;
      this.sessions     = sessions;
    }
  }

  private List<SendMessageResult> transformGroupResponseToMessageResults(Map<SignalServiceAddress, List<Integer>> recipients, SendGroupMessageResponse response, Content content) {
    Set<ServiceId> unregistered = response.getUnsentTargets();

    List<SendMessageResult> failures = unregistered.stream()
                                                   .map(SignalServiceAddress::new)
                                                   .map(SendMessageResult::unregisteredFailure)
                                                   .collect(Collectors.toList());

    List<SendMessageResult> success = recipients.keySet()
                                                .stream()
                                                .filter(r -> !unregistered.contains(r.getServiceId()))
                                                .map(a -> SendMessageResult.success(a, recipients.get(a), true, aciStore.isMultiDevice(), -1, Optional.of(content)))
                                                .collect(Collectors.toList());

    List<SendMessageResult> results = new ArrayList<>(success.size() + failures.size());
    results.addAll(success);
    results.addAll(failures);

    return results;
  }

  private List<AttachmentPointer> createAttachmentPointers(Optional<List<SignalServiceAttachment>> attachments) throws IOException {
    List<AttachmentPointer> pointers = new LinkedList<>();

    if (!attachments.isPresent() || attachments.get().isEmpty()) {
      return pointers;
    }

    for (SignalServiceAttachment attachment : attachments.get()) {
      if (attachment.isStream()) {
        Log.i(TAG, "Found attachment, creating pointer...");
        pointers.add(createAttachmentPointer(attachment.asStream()));
      } else if (attachment.isPointer()) {
        Log.i(TAG, "Including existing attachment pointer...");
        pointers.add(createAttachmentPointer(attachment.asPointer()));
      }
    }

    return pointers;
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentPointer attachment) {
    return AttachmentPointerUtil.createAttachmentPointer(attachment);
  }

  private AttachmentPointer createAttachmentPointer(SignalServiceAttachmentStream attachment)
      throws IOException
  {
    return createAttachmentPointer(uploadAttachment(attachment));
  }

  private TextAttachment createTextAttachment(SignalServiceTextAttachment attachment) throws IOException {
    TextAttachment.Builder builder = new TextAttachment.Builder();

    if (attachment.getStyle().isPresent()) {
      switch (attachment.getStyle().get()) {
        case DEFAULT:
          builder.textStyle(TextAttachment.Style.DEFAULT);
          break;
        case REGULAR:
          builder.textStyle(TextAttachment.Style.REGULAR);
          break;
        case BOLD:
          builder.textStyle(TextAttachment.Style.BOLD);
          break;
        case SERIF:
          builder.textStyle(TextAttachment.Style.SERIF);
          break;
        case SCRIPT:
          builder.textStyle(TextAttachment.Style.SCRIPT);
          break;
        case CONDENSED:
          builder.textStyle(TextAttachment.Style.CONDENSED);
          break;
        default:
          throw new AssertionError("Unknown type: " + attachment.getStyle().get());
      }
    }

    TextAttachment.Gradient.Builder gradientBuilder = new TextAttachment.Gradient.Builder();

    if (attachment.getBackgroundGradient().isPresent()) {
      SignalServiceTextAttachment.Gradient gradient = attachment.getBackgroundGradient().get();

      if (gradient.getAngle().isPresent()) gradientBuilder.angle(gradient.getAngle().get());

      if (!gradient.getColors().isEmpty()) {
        gradientBuilder.startColor(gradient.getColors().get(0));
        gradientBuilder.endColor(gradient.getColors().get(gradient.getColors().size() - 1));
      }

      gradientBuilder.colors = gradient.getColors();
      gradientBuilder.positions = gradient.getPositions();

      builder.gradient(gradientBuilder.build());
    }

    if (attachment.getText().isPresent())                builder.text(attachment.getText().get());
    if (attachment.getTextForegroundColor().isPresent()) builder.textForegroundColor(attachment.getTextForegroundColor().get());
    if (attachment.getTextBackgroundColor().isPresent()) builder.textBackgroundColor(attachment.getTextBackgroundColor().get());
    if (attachment.getPreview().isPresent())             builder.preview(createPreview(attachment.getPreview().get()));
    if (attachment.getBackgroundColor().isPresent())     builder.color(attachment.getBackgroundColor().get());

    return builder.build();
  }

  private OutgoingPushMessageList getEncryptedMessages(SignalServiceAddress         recipient,
                                                       @Nullable SealedSenderAccess sealedSenderAccess,
                                                       long                         timestamp,
                                                       EnvelopeContent              plaintext,
                                                       boolean                      online,
                                                       boolean                      urgent,
                                                       boolean                      story)
      throws IOException, InvalidKeyException, UntrustedIdentityException
  {
    List<OutgoingPushMessage> messages = new LinkedList<>();

    List<Integer> subDevices = aciStore.getSubDeviceSessions(recipient.getIdentifier());

    List<Integer> deviceIds = new ArrayList<>(subDevices.size() + 1);
    deviceIds.add(SignalServiceAddress.DEFAULT_DEVICE_ID);
    deviceIds.addAll(subDevices);

    if (recipient.matches(localAddress)) {
      deviceIds.remove(Integer.valueOf(localDeviceId));
    }

    for (int deviceId : deviceIds) {
      if (deviceId == SignalServiceAddress.DEFAULT_DEVICE_ID || aciStore.containsSession(new SignalProtocolAddress(recipient.getIdentifier(), deviceId))) {
        messages.add(getEncryptedMessage(recipient, sealedSenderAccess, deviceId, plaintext, story));
      }
    }

    return new OutgoingPushMessageList(recipient.getIdentifier(), timestamp, messages, online, urgent);
  }

  // Visible for testing only
  public OutgoingPushMessage getEncryptedMessage(SignalServiceAddress         recipient,
                                                 @Nullable SealedSenderAccess sealedSenderAccess,
                                                 int                          deviceId,
                                                 EnvelopeContent              plaintext,
                                                 boolean                      story)
      throws IOException, InvalidKeyException, UntrustedIdentityException
  {
    SignalProtocolAddress signalProtocolAddress = new SignalProtocolAddress(recipient.getIdentifier(), deviceId);
    SignalServiceCipher   cipher                = new SignalServiceCipher(localAddress, localDeviceId, aciStore, sessionLock, null);

    if (!aciStore.containsSession(signalProtocolAddress)) {
      try {
        List<PreKeyBundle> preKeys = getPreKeys(recipient, sealedSenderAccess, deviceId, story);

        for (PreKeyBundle preKey : preKeys) {
          Log.d(TAG, "Initializing prekey session for " + signalProtocolAddress);

          try {
            SignalProtocolAddress preKeyAddress  = new SignalProtocolAddress(recipient.getIdentifier(), preKey.getDeviceId());
            SignalSessionBuilder  sessionBuilder = new SignalSessionBuilder(sessionLock, new SessionBuilder(aciStore, preKeyAddress));
            sessionBuilder.process(preKey);
          } catch (org.signal.libsignal.protocol.UntrustedIdentityException e) {
            throw new UntrustedIdentityException("Untrusted identity key!", recipient.getIdentifier(), preKey.getIdentityKey());
          }
        }

        if (eventListener.isPresent()) {
          eventListener.get().onSecurityEvent(recipient);
        }
      } catch (InvalidKeyException e) {
        throw new InvalidPreKeyException(signalProtocolAddress, e);
      }
    }

    try {
      return cipher.encrypt(signalProtocolAddress, sealedSenderAccess, plaintext);
    } catch (org.signal.libsignal.protocol.UntrustedIdentityException e) {
      throw new UntrustedIdentityException("Untrusted on send", recipient.getIdentifier(), e.getUntrustedIdentity());
    }
  }

  private List<PreKeyBundle> getPreKeys(SignalServiceAddress recipient, @Nullable SealedSenderAccess sealedSenderAccess, int deviceId, boolean story) throws IOException {
    try {
      // If it's only unrestricted because it's a story send, then we know it'll fail
      if (story && SealedSenderAccess.isUnrestrictedForStory(sealedSenderAccess)) {
        sealedSenderAccess = null;
      }

      return socket.getPreKeys(recipient, sealedSenderAccess, deviceId);
    } catch (NonSuccessfulResponseCodeException e) {
      if (e.getCode() == 401 && story) {
        Log.d(TAG, "Got 401 when fetching prekey for story. Trying without UD.");
        return socket.getPreKeys(recipient, null, deviceId);
      } else {
        throw e;
      }
    }
  }

  private void handleMismatchedDevices(PushServiceSocket socket, SignalServiceAddress recipient,
                                       MismatchedDevices mismatchedDevices)
      throws IOException, UntrustedIdentityException
  {
    try {
      Log.w(TAG, "[handleMismatchedDevices] Address: " + recipient.getIdentifier() + ", ExtraDevices: " + mismatchedDevices.getExtraDevices() + ", MissingDevices: " + mismatchedDevices.getMissingDevices());
      archiveSessions(recipient, mismatchedDevices.getExtraDevices());

      for (int missingDeviceId : mismatchedDevices.getMissingDevices()) {
        PreKeyBundle preKey = socket.getPreKey(recipient, missingDeviceId);

        try {
          SignalSessionBuilder sessionBuilder = new SignalSessionBuilder(sessionLock, new SessionBuilder(aciStore, new SignalProtocolAddress(recipient.getIdentifier(), missingDeviceId)));
          sessionBuilder.process(preKey);
        } catch (org.signal.libsignal.protocol.UntrustedIdentityException e) {
          throw new UntrustedIdentityException("Untrusted identity key!", recipient.getIdentifier(), preKey.getIdentityKey());
        }
      }
    } catch (InvalidKeyException e) {
      throw new IOException(e);
    }
  }

  private void handleStaleDevices(SignalServiceAddress recipient, StaleDevices staleDevices) {
    Log.w(TAG, "[handleStaleDevices] Address: " + recipient.getIdentifier() + ", StaleDevices: " + staleDevices.getStaleDevices());
    archiveSessions(recipient, staleDevices.getStaleDevices());
  }

  public void handleChangeNumberMismatchDevices(@Nonnull MismatchedDevices mismatchedDevices)
      throws IOException, UntrustedIdentityException
  {
    handleMismatchedDevices(socket, localAddress, mismatchedDevices);
  }

  private void archiveSessions(SignalServiceAddress recipient, List<Integer> devices) {
    List<SignalProtocolAddress> addressesToClear = convertToProtocolAddresses(recipient, devices);

    for (SignalProtocolAddress address : addressesToClear) {
      aciStore.archiveSession(address);
    }
  }

  private List<SignalProtocolAddress> convertToProtocolAddresses(SignalServiceAddress recipient, List<Integer> devices) {
    List<SignalProtocolAddress> addresses = new ArrayList<>(devices.size());

    for (int staleDeviceId : devices) {
      addresses.add(new SignalProtocolAddress(recipient.getServiceId().toString(), staleDeviceId));

      if (recipient.getNumber().isPresent()) {
        addresses.add(new SignalProtocolAddress(recipient.getNumber().get(), staleDeviceId));
      }
    }

    return addresses;
  }

  private EnvelopeContent enforceMaxContentSize(EnvelopeContent content) {
    int size = content.size();

    if (maxEnvelopeSize > 0 && size > maxEnvelopeSize) {
      throw new ContentTooLargeException(size);
    }
    return content;
  }

  private Content enforceMaxContentSize(Content content) {
    int size = content.encode().length;

    if (maxEnvelopeSize > 0 && size > maxEnvelopeSize) {
      throw new ContentTooLargeException(size);
    }
    return content;
  }

  public interface EventListener {
    void onSecurityEvent(SignalServiceAddress address);
  }

  public interface SendEvents {
    void onMessageEncrypted();
    void onMessageSent();
    void onSyncMessageSent();
  }

  public interface IndividualSendEvents extends SendEvents {
    IndividualSendEvents EMPTY = new IndividualSendEvents() {
      @Override
      public void onMessageEncrypted() { }

      @Override
      public void onMessageSent() { }

      @Override
      public void onSyncMessageSent() { }
    };
  }

  public interface SenderKeyGroupEvents extends SendEvents {
    SenderKeyGroupEvents EMPTY = new SenderKeyGroupEvents() {
      @Override
      public void onSenderKeyShared() { }

      @Override
      public void onMessageEncrypted() { }

      @Override
      public void onMessageSent() { }

      @Override
      public void onSyncMessageSent() { }
    };

    void onSenderKeyShared();
  }

  public interface LegacyGroupEvents extends SendEvents {
    LegacyGroupEvents EMPTY = new LegacyGroupEvents() {
      @Override
      public void onMessageEncrypted() {}

      @Override
      public void onMessageSent() { }

      @Override
      public void onSyncMessageSent() { }
    };

  }
}

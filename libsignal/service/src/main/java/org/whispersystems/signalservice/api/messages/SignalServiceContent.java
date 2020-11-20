/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageProtocolVersionException;
import org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.SignalServiceMetadataProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext.Type.DELIVER;

public final class SignalServiceContent {

  private static final String TAG = SignalServiceContent.class.getSimpleName();

  private final SignalServiceAddress      sender;
  private final int                       senderDevice;
  private final long                      timestamp;
  private final long                      serverReceivedTimestamp;
  private final long                      serverDeliveredTimestamp;
  private final boolean                   needsReceipt;
  private final SignalServiceContentProto serializedState;

  private final Optional<SignalServiceDataMessage>    message;
  private final Optional<SignalServiceSyncMessage>    synchronizeMessage;
  private final Optional<SignalServiceCallMessage>    callMessage;
  private final Optional<SignalServiceReceiptMessage> readMessage;
  private final Optional<SignalServiceTypingMessage>  typingMessage;

  private SignalServiceContent(SignalServiceDataMessage message,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serializedState          = serializedState;

    this.message            = Optional.fromNullable(message);
    this.synchronizeMessage = Optional.absent();
    this.callMessage        = Optional.absent();
    this.readMessage        = Optional.absent();
    this.typingMessage      = Optional.absent();
  }

  private SignalServiceContent(SignalServiceSyncMessage synchronizeMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serializedState          = serializedState;

    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.fromNullable(synchronizeMessage);
    this.callMessage        = Optional.absent();
    this.readMessage        = Optional.absent();
    this.typingMessage      = Optional.absent();
  }

  private SignalServiceContent(SignalServiceCallMessage callMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serializedState          = serializedState;

    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.absent();
    this.callMessage        = Optional.of(callMessage);
    this.readMessage        = Optional.absent();
    this.typingMessage      = Optional.absent();
  }

  private SignalServiceContent(SignalServiceReceiptMessage receiptMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serializedState          = serializedState;

    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.absent();
    this.callMessage        = Optional.absent();
    this.readMessage        = Optional.of(receiptMessage);
    this.typingMessage      = Optional.absent();
  }

  private SignalServiceContent(SignalServiceTypingMessage typingMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serializedState          = serializedState;

    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.absent();
    this.callMessage        = Optional.absent();
    this.readMessage        = Optional.absent();
    this.typingMessage      = Optional.of(typingMessage);
  }

  public Optional<SignalServiceDataMessage> getDataMessage() {
    return message;
  }

  public Optional<SignalServiceSyncMessage> getSyncMessage() {
    return synchronizeMessage;
  }

  public Optional<SignalServiceCallMessage> getCallMessage() {
    return callMessage;
  }

  public Optional<SignalServiceReceiptMessage> getReceiptMessage() {
    return readMessage;
  }

  public Optional<SignalServiceTypingMessage> getTypingMessage() {
    return typingMessage;
  }

  public SignalServiceAddress getSender() {
    return sender;
  }

  public int getSenderDevice() {
    return senderDevice;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getServerReceivedTimestamp() {
    return serverReceivedTimestamp;
  }

  public long getServerDeliveredTimestamp() {
    return serverDeliveredTimestamp;
  }

  public boolean isNeedsReceipt() {
    return needsReceipt;
  }

  public byte[] serialize() {
    return serializedState.toByteArray();
  }

  public static SignalServiceContent deserialize(byte[] data) {
    try {
      if (data == null) return null;

      SignalServiceContentProto signalServiceContentProto = SignalServiceContentProto.parseFrom(data);

      return createFromProto(signalServiceContentProto);
    } catch (InvalidProtocolBufferException | ProtocolInvalidMessageException | ProtocolInvalidKeyException | UnsupportedDataMessageException e) {
      // We do not expect any of these exceptions if this byte[] has come from serialize.
      throw new AssertionError(e);
    }
  }

  /**
   * Takes internal protobuf serialization format and processes it into a {@link SignalServiceContent}.
   */
  public static SignalServiceContent createFromProto(SignalServiceContentProto serviceContentProto)
      throws ProtocolInvalidMessageException, ProtocolInvalidKeyException, UnsupportedDataMessageException
  {
    SignalServiceMetadata metadata     = SignalServiceMetadataProtobufSerializer.fromProtobuf(serviceContentProto.getMetadata());
    SignalServiceAddress  localAddress = SignalServiceAddressProtobufSerializer.fromProtobuf(serviceContentProto.getLocalAddress());

    if (serviceContentProto.getDataCase() == SignalServiceContentProto.DataCase.LEGACYDATAMESSAGE) {
      SignalServiceProtos.DataMessage message = serviceContentProto.getLegacyDataMessage();

      return new SignalServiceContent(createSignalServiceMessage(metadata, message),
                                      metadata.getSender(),
                                      metadata.getSenderDevice(),
                                      metadata.getTimestamp(),
                                      metadata.getServerReceivedTimestamp(),
                                      metadata.getServerDeliveredTimestamp(),
                                      metadata.isNeedsReceipt(),
                                      serviceContentProto);
    } else if (serviceContentProto.getDataCase() == SignalServiceContentProto.DataCase.CONTENT) {
      SignalServiceProtos.Content message = serviceContentProto.getContent();

      if (message.hasDataMessage()) {
        return new SignalServiceContent(createSignalServiceMessage(metadata, message.getDataMessage()),
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        metadata.isNeedsReceipt(),
                                        serviceContentProto);
      } else if (message.hasSyncMessage() && localAddress.matches(metadata.getSender())) {
        return new SignalServiceContent(createSynchronizeMessage(metadata, message.getSyncMessage()),
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        metadata.isNeedsReceipt(),
                                        serviceContentProto);
      } else if (message.hasCallMessage()) {
        return new SignalServiceContent(createCallMessage(message.getCallMessage()),
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        metadata.isNeedsReceipt(),
                                        serviceContentProto);
      } else if (message.hasReceiptMessage()) {
        return new SignalServiceContent(createReceiptMessage(metadata, message.getReceiptMessage()),
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        metadata.isNeedsReceipt(),
                                        serviceContentProto);
      } else if (message.hasTypingMessage()) {
        return new SignalServiceContent(createTypingMessage(metadata, message.getTypingMessage()),
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        false,
                                        serviceContentProto);
      }
    }

    return null;
  }

  private static SignalServiceDataMessage createSignalServiceMessage(SignalServiceMetadata metadata,
                                                                     SignalServiceProtos.DataMessage content)
      throws ProtocolInvalidMessageException, UnsupportedDataMessageException
  {
    SignalServiceGroup                  groupInfoV1  = createGroupV1Info(content);
    SignalServiceGroupV2                groupInfoV2  = createGroupV2Info(content);
    Optional<SignalServiceGroupContext> groupContext;

    try {
      groupContext = SignalServiceGroupContext.createOptional(groupInfoV1, groupInfoV2);
    } catch (InvalidMessageException e) {
      throw new ProtocolInvalidMessageException(e, null, 0);
    }


    List<SignalServiceAttachment>            attachments      = new LinkedList<>();
    boolean                                  endSession       = ((content.getFlags() & SignalServiceProtos.DataMessage.Flags.END_SESSION_VALUE            ) != 0);
    boolean                                  expirationUpdate = ((content.getFlags() & SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE) != 0);
    boolean                                  profileKeyUpdate = ((content.getFlags() & SignalServiceProtos.DataMessage.Flags.PROFILE_KEY_UPDATE_VALUE     ) != 0);
    boolean                                  isGroupV2        = groupInfoV2 != null;
    SignalServiceDataMessage.Quote           quote            = createQuote(content, isGroupV2);
    List<SharedContact>                      sharedContacts   = createSharedContacts(content);
    List<SignalServiceDataMessage.Preview>   previews         = createPreviews(content);
    List<SignalServiceDataMessage.Mention>   mentions         = createMentions(content.getBodyRangesList(), content.getBody(), isGroupV2);
    SignalServiceDataMessage.Sticker         sticker          = createSticker(content);
    SignalServiceDataMessage.Reaction        reaction         = createReaction(content);
    SignalServiceDataMessage.RemoteDelete    remoteDelete     = createRemoteDelete(content);
    SignalServiceDataMessage.GroupCallUpdate groupCallUpdate  = createGroupCallUpdate(content);

    if (content.getRequiredProtocolVersion() > SignalServiceProtos.DataMessage.ProtocolVersion.CURRENT_VALUE) {
      throw new UnsupportedDataMessageProtocolVersionException(SignalServiceProtos.DataMessage.ProtocolVersion.CURRENT_VALUE,
                                                               content.getRequiredProtocolVersion(),
                                                               metadata.getSender().getIdentifier(),
                                                               metadata.getSenderDevice(),
                                                               groupContext);
    }

    for (SignalServiceProtos.AttachmentPointer pointer : content.getAttachmentsList()) {
      attachments.add(createAttachmentPointer(pointer));
    }

    if (content.hasTimestamp() && content.getTimestamp() != metadata.getTimestamp()) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("Timestamps don't match: " + content.getTimestamp() + " vs " + metadata.getTimestamp()),
                                                                            metadata.getSender().getIdentifier(),
                                                                            metadata.getSenderDevice());
    }

    return new SignalServiceDataMessage(metadata.getTimestamp(),
                                        groupInfoV1, groupInfoV2,
                                        attachments,
                                        content.hasBody() ? content.getBody() : null,
                                        endSession,
                                        content.getExpireTimer(),
                                        expirationUpdate,
                                        content.hasProfileKey() ? content.getProfileKey().toByteArray() : null,
                                        profileKeyUpdate,
                                        quote,
                                        sharedContacts,
                                        previews,
                                        mentions,
                                        sticker,
                                        content.getIsViewOnce(),
                                        reaction,
                                        remoteDelete,
                                        groupCallUpdate);
  }

  private static SignalServiceSyncMessage createSynchronizeMessage(SignalServiceMetadata metadata,
                                                                   SignalServiceProtos.SyncMessage content)
      throws ProtocolInvalidMessageException, ProtocolInvalidKeyException, UnsupportedDataMessageException
  {
    if (content.hasSent()) {
      Map<SignalServiceAddress, Boolean>   unidentifiedStatuses = new HashMap<>();
      SignalServiceProtos.SyncMessage.Sent sentContent          = content.getSent();
      SignalServiceDataMessage             dataMessage          = createSignalServiceMessage(metadata, sentContent.getMessage());
      Optional<SignalServiceAddress>       address              = SignalServiceAddress.isValidAddress(sentContent.getDestinationUuid(), sentContent.getDestinationE164())
                                                                  ? Optional.of(new SignalServiceAddress(UuidUtil.parseOrNull(sentContent.getDestinationUuid()), sentContent.getDestinationE164()))
                                                                  : Optional.<SignalServiceAddress>absent();

      if (!address.isPresent() && !dataMessage.getGroupContext().isPresent()) {
        throw new ProtocolInvalidMessageException(new InvalidMessageException("SyncMessage missing both destination and group ID!"), null, 0);
      }

      for (SignalServiceProtos.SyncMessage.Sent.UnidentifiedDeliveryStatus status : sentContent.getUnidentifiedStatusList()) {
        if (SignalServiceAddress.isValidAddress(status.getDestinationUuid(), status.getDestinationE164())) {
          SignalServiceAddress recipient = new SignalServiceAddress(UuidUtil.parseOrNull(status.getDestinationUuid()), status.getDestinationE164());
          unidentifiedStatuses.put(recipient, status.getUnidentified());
        } else {
          Log.w(TAG, "Encountered an invalid UnidentifiedDeliveryStatus in a SentTranscript! Ignoring.");
        }
      }

      return SignalServiceSyncMessage.forSentTranscript(new SentTranscriptMessage(address,
                                                                                  sentContent.getTimestamp(),
                                                                                  dataMessage,
                                                                                  sentContent.getExpirationStartTimestamp(),
                                                                                  unidentifiedStatuses,
                                                                                  sentContent.getIsRecipientUpdate()));
    }

    if (content.hasRequest()) {
      return SignalServiceSyncMessage.forRequest(new RequestMessage(content.getRequest()));
    }

    if (content.getReadList().size() > 0) {
      List<ReadMessage> readMessages = new LinkedList<>();

      for (SignalServiceProtos.SyncMessage.Read read : content.getReadList()) {
        if (SignalServiceAddress.isValidAddress(read.getSenderUuid(), read.getSenderE164())) {
          SignalServiceAddress address = new SignalServiceAddress(UuidUtil.parseOrNull(read.getSenderUuid()), read.getSenderE164());
          readMessages.add(new ReadMessage(address, read.getTimestamp()));
        } else {
          Log.w(TAG, "Encountered an invalid ReadMessage! Ignoring.");
        }
      }

      return SignalServiceSyncMessage.forRead(readMessages);
    }

    if (content.hasViewOnceOpen()) {
      if (SignalServiceAddress.isValidAddress(content.getViewOnceOpen().getSenderUuid(), content.getViewOnceOpen().getSenderE164())) {
        SignalServiceAddress address   = new SignalServiceAddress(UuidUtil.parseOrNull(content.getViewOnceOpen().getSenderUuid()), content.getViewOnceOpen().getSenderE164());
        ViewOnceOpenMessage timerRead = new ViewOnceOpenMessage(address, content.getViewOnceOpen().getTimestamp());
        return SignalServiceSyncMessage.forViewOnceOpen(timerRead);
      } else {
        throw new ProtocolInvalidMessageException(new InvalidMessageException("ViewOnceOpen message has no sender!"), null, 0);
      }
    }

    if (content.hasVerified()) {
      if (SignalServiceAddress.isValidAddress(content.getVerified().getDestinationUuid(), content.getVerified().getDestinationE164())) {
        try {
          SignalServiceProtos.Verified verified    = content.getVerified();
          SignalServiceAddress destination = new SignalServiceAddress(UuidUtil.parseOrNull(verified.getDestinationUuid()), verified.getDestinationE164());
          IdentityKey identityKey = new IdentityKey(verified.getIdentityKey().toByteArray(), 0);

          VerifiedMessage.VerifiedState verifiedState;

          if (verified.getState() == SignalServiceProtos.Verified.State.DEFAULT) {
            verifiedState = VerifiedMessage.VerifiedState.DEFAULT;
          } else if (verified.getState() == SignalServiceProtos.Verified.State.VERIFIED) {
            verifiedState = VerifiedMessage.VerifiedState.VERIFIED;
          } else if (verified.getState() == SignalServiceProtos.Verified.State.UNVERIFIED) {
            verifiedState = VerifiedMessage.VerifiedState.UNVERIFIED;
          } else {
            throw new ProtocolInvalidMessageException(new InvalidMessageException("Unknown state: " + verified.getState().getNumber()),
                                                      metadata.getSender().getIdentifier(), metadata.getSenderDevice());
          }

          return SignalServiceSyncMessage.forVerified(new VerifiedMessage(destination, identityKey, verifiedState, System.currentTimeMillis()));
        } catch (InvalidKeyException e) {
          throw new ProtocolInvalidKeyException(e, metadata.getSender().getIdentifier(), metadata.getSenderDevice());
        }
      } else {
        throw new ProtocolInvalidMessageException(new InvalidMessageException("Verified message has no sender!"), null, 0);
      }
    }

    if (content.getStickerPackOperationList().size() > 0) {
      List<StickerPackOperationMessage> operations = new LinkedList<>();

      for (SignalServiceProtos.SyncMessage.StickerPackOperation operation : content.getStickerPackOperationList()) {
        byte[]                           packId  = operation.hasPackId() ? operation.getPackId().toByteArray() : null;
        byte[]                           packKey = operation.hasPackKey() ? operation.getPackKey().toByteArray() : null;
        StickerPackOperationMessage.Type type    = null;

        if (operation.hasType()) {
          switch (operation.getType()) {
            case INSTALL: type = StickerPackOperationMessage.Type.INSTALL; break;
            case REMOVE:  type = StickerPackOperationMessage.Type.REMOVE; break;
          }
        }
        operations.add(new StickerPackOperationMessage(packId, packKey, type));
      }

      return SignalServiceSyncMessage.forStickerPackOperations(operations);
    }

    if (content.hasBlocked()) {
      List<String>               numbers   = content.getBlocked().getNumbersList();
      List<String>               uuids     = content.getBlocked().getUuidsList();
      List<SignalServiceAddress> addresses = new ArrayList<>(numbers.size() + uuids.size());
      List<byte[]>               groupIds  = new ArrayList<>(content.getBlocked().getGroupIdsList().size());

      for (String e164 : numbers) {
        Optional<SignalServiceAddress> address = SignalServiceAddress.fromRaw(null, e164);
        if (address.isPresent()) {
          addresses.add(address.get());
        }
      }

      for (String uuid : uuids) {
        Optional<SignalServiceAddress> address = SignalServiceAddress.fromRaw(uuid, null);
        if (address.isPresent()) {
          addresses.add(address.get());
        }
      }

      for (ByteString groupId : content.getBlocked().getGroupIdsList()) {
        groupIds.add(groupId.toByteArray());
      }

      return SignalServiceSyncMessage.forBlocked(new BlockedListMessage(addresses, groupIds));
    }

    if (content.hasConfiguration()) {
      Boolean readReceipts                   = content.getConfiguration().hasReadReceipts() ? content.getConfiguration().getReadReceipts() : null;
      Boolean unidentifiedDeliveryIndicators = content.getConfiguration().hasUnidentifiedDeliveryIndicators() ? content.getConfiguration().getUnidentifiedDeliveryIndicators() : null;
      Boolean typingIndicators               = content.getConfiguration().hasTypingIndicators() ? content.getConfiguration().getTypingIndicators() : null;
      Boolean linkPreviews                   = content.getConfiguration().hasLinkPreviews() ? content.getConfiguration().getLinkPreviews() : null;

      return SignalServiceSyncMessage.forConfiguration(new ConfigurationMessage(Optional.fromNullable(readReceipts),
                                                                                Optional.fromNullable(unidentifiedDeliveryIndicators),
                                                                                Optional.fromNullable(typingIndicators),
                                                                                Optional.fromNullable(linkPreviews)));
    }

    if (content.hasFetchLatest() && content.getFetchLatest().hasType()) {
      switch (content.getFetchLatest().getType()) {
        case LOCAL_PROFILE:    return SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.LOCAL_PROFILE);
        case STORAGE_MANIFEST: return SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.STORAGE_MANIFEST);
      }
    }

    if (content.hasMessageRequestResponse()) {
      MessageRequestResponseMessage.Type type;

      switch (content.getMessageRequestResponse().getType()) {
        case ACCEPT:
          type = MessageRequestResponseMessage.Type.ACCEPT;
          break;
        case DELETE:
          type = MessageRequestResponseMessage.Type.DELETE;
          break;
        case BLOCK:
          type = MessageRequestResponseMessage.Type.BLOCK;
          break;
        case BLOCK_AND_DELETE:
          type = MessageRequestResponseMessage.Type.BLOCK_AND_DELETE;
          break;
        default:
         type = MessageRequestResponseMessage.Type.UNKNOWN;
         break;
      }

      MessageRequestResponseMessage responseMessage;

      if (content.getMessageRequestResponse().hasGroupId()) {
        responseMessage = MessageRequestResponseMessage.forGroup(content.getMessageRequestResponse().getGroupId().toByteArray(), type);
      } else {
        Optional<SignalServiceAddress> address = SignalServiceAddress.fromRaw(content.getMessageRequestResponse().getThreadUuid(), content.getMessageRequestResponse().getThreadE164());

        if (address.isPresent()) {
          responseMessage = MessageRequestResponseMessage.forIndividual(address.get(), type);
        } else {
          throw new ProtocolInvalidMessageException(new InvalidMessageException("Message request response has an invalid thread identifier!"), null, 0);
        }
      }

      return SignalServiceSyncMessage.forMessageRequestResponse(responseMessage);
    }

    return SignalServiceSyncMessage.empty();
  }

  private static SignalServiceCallMessage createCallMessage(SignalServiceProtos.CallMessage content) {
    boolean isMultiRing         = content.getMultiRing();
    Integer destinationDeviceId = content.hasDestinationDeviceId() ? content.getDestinationDeviceId() : null;

    if (content.hasOffer()) {
      SignalServiceProtos.CallMessage.Offer offerContent = content.getOffer();
      return SignalServiceCallMessage.forOffer(new OfferMessage(offerContent.getId(), offerContent.hasSdp() ? offerContent.getSdp() : null, OfferMessage.Type.fromProto(offerContent.getType()), offerContent.hasOpaque() ? offerContent.getOpaque().toByteArray() : null), isMultiRing, destinationDeviceId);
    } else if (content.hasAnswer()) {
      SignalServiceProtos.CallMessage.Answer answerContent = content.getAnswer();
      return SignalServiceCallMessage.forAnswer(new AnswerMessage(answerContent.getId(), answerContent.hasSdp() ? answerContent.getSdp() : null, answerContent.hasOpaque() ? answerContent.getOpaque().toByteArray() : null), isMultiRing, destinationDeviceId);
    } else if (content.getIceUpdateCount() > 0) {
      List<IceUpdateMessage> iceUpdates = new LinkedList<>();

      for (SignalServiceProtos.CallMessage.IceUpdate iceUpdate : content.getIceUpdateList()) {
        iceUpdates.add(new IceUpdateMessage(iceUpdate.getId(), iceUpdate.hasOpaque() ? iceUpdate.getOpaque().toByteArray() : null, iceUpdate.hasSdp() ? iceUpdate.getSdp() : null));
      }

      return SignalServiceCallMessage.forIceUpdates(iceUpdates, isMultiRing, destinationDeviceId);
    } else if (content.hasLegacyHangup()) {
      SignalServiceProtos.CallMessage.Hangup hangup = content.getLegacyHangup();
      return SignalServiceCallMessage.forHangup(new HangupMessage(hangup.getId(), HangupMessage.Type.fromProto(hangup.getType()), hangup.getDeviceId(), content.hasLegacyHangup()), isMultiRing, destinationDeviceId);
    } else if (content.hasHangup()) {
      SignalServiceProtos.CallMessage.Hangup hangup = content.getHangup();
      return SignalServiceCallMessage.forHangup(new HangupMessage(hangup.getId(), HangupMessage.Type.fromProto(hangup.getType()), hangup.getDeviceId(), content.hasLegacyHangup()), isMultiRing, destinationDeviceId);
    } else if (content.hasBusy()) {
      SignalServiceProtos.CallMessage.Busy busy = content.getBusy();
      return SignalServiceCallMessage.forBusy(new BusyMessage(busy.getId()), isMultiRing, destinationDeviceId);
    } else if (content.hasOpaque()) {
      SignalServiceProtos.CallMessage.Opaque opaque = content.getOpaque();
      return SignalServiceCallMessage.forOpaque(new OpaqueMessage(opaque.getData().toByteArray()), isMultiRing, destinationDeviceId);
    }

    return SignalServiceCallMessage.empty();
  }

  private static SignalServiceReceiptMessage createReceiptMessage(SignalServiceMetadata metadata, SignalServiceProtos.ReceiptMessage content) {
    SignalServiceReceiptMessage.Type type;

    if      (content.getType() == SignalServiceProtos.ReceiptMessage.Type.DELIVERY) type = SignalServiceReceiptMessage.Type.DELIVERY;
    else if (content.getType() == SignalServiceProtos.ReceiptMessage.Type.READ)     type = SignalServiceReceiptMessage.Type.READ;
    else if (content.getType() == SignalServiceProtos.ReceiptMessage.Type.VIEWED)   type = SignalServiceReceiptMessage.Type.VIEWED;
    else                                                        type = SignalServiceReceiptMessage.Type.UNKNOWN;

    return new SignalServiceReceiptMessage(type, content.getTimestampList(), metadata.getTimestamp());
  }

  private static SignalServiceTypingMessage createTypingMessage(SignalServiceMetadata metadata, SignalServiceProtos.TypingMessage content) throws ProtocolInvalidMessageException {
    SignalServiceTypingMessage.Action action;

    if      (content.getAction() == SignalServiceProtos.TypingMessage.Action.STARTED) action = SignalServiceTypingMessage.Action.STARTED;
    else if (content.getAction() == SignalServiceProtos.TypingMessage.Action.STOPPED) action = SignalServiceTypingMessage.Action.STOPPED;
    else                                                          action = SignalServiceTypingMessage.Action.UNKNOWN;

    if (content.hasTimestamp() && content.getTimestamp() != metadata.getTimestamp()) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("Timestamps don't match: " + content.getTimestamp() + " vs " + metadata.getTimestamp()),
                                                metadata.getSender().getIdentifier(),
                                                metadata.getSenderDevice());
    }

    return new SignalServiceTypingMessage(action, content.getTimestamp(),
                                          content.hasGroupId() ? Optional.of(content.getGroupId().toByteArray()) :
                                                                 Optional.<byte[]>absent());
  }

  private static SignalServiceDataMessage.Quote createQuote(SignalServiceProtos.DataMessage content, boolean isGroupV2) throws ProtocolInvalidMessageException {
    if (!content.hasQuote()) return null;

    List<SignalServiceDataMessage.Quote.QuotedAttachment> attachments = new LinkedList<>();

    for (SignalServiceProtos.DataMessage.Quote.QuotedAttachment attachment : content.getQuote().getAttachmentsList()) {
      attachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.getContentType(),
                                                                          attachment.getFileName(),
                                                                          attachment.hasThumbnail() ? createAttachmentPointer(attachment.getThumbnail()) : null));
    }

    if (SignalServiceAddress.isValidAddress(content.getQuote().getAuthorUuid(), content.getQuote().getAuthorE164())) {
      SignalServiceAddress address = new SignalServiceAddress(UuidUtil.parseOrNull(content.getQuote().getAuthorUuid()), content.getQuote().getAuthorE164());

      return new SignalServiceDataMessage.Quote(content.getQuote().getId(),
                                                address,
                                                content.getQuote().getText(),
                                                attachments,
                                                createMentions(content.getQuote().getBodyRangesList(), content.getQuote().getText(), isGroupV2));
    } else {
      Log.w(TAG, "Quote was missing an author! Returning null.");
      return null;
    }
  }

  private static List<SignalServiceDataMessage.Preview> createPreviews(SignalServiceProtos.DataMessage content) throws ProtocolInvalidMessageException {
    if (content.getPreviewCount() <= 0) return null;

    List<SignalServiceDataMessage.Preview> results = new LinkedList<>();

    for (SignalServiceProtos.DataMessage.Preview preview : content.getPreviewList()) {
      SignalServiceAttachment attachment = null;

      if (preview.hasImage()) {
        attachment = createAttachmentPointer(preview.getImage());
      }

      results.add(new SignalServiceDataMessage.Preview(preview.getUrl(),
                                                       preview.getTitle(),
                                                       preview.getDescription(),
                                                       preview.getDate(),
                                                       Optional.fromNullable(attachment)));
    }

    return results;
  }

  private static List<SignalServiceDataMessage.Mention> createMentions(List<SignalServiceProtos.DataMessage.BodyRange> bodyRanges, String body, boolean isGroupV2) throws ProtocolInvalidMessageException {
    if (bodyRanges == null || bodyRanges.isEmpty() || body == null) {
      return null;
    }

    List<SignalServiceDataMessage.Mention> mentions = new LinkedList<>();

    for (SignalServiceProtos.DataMessage.BodyRange bodyRange : bodyRanges) {
      if (bodyRange.hasMentionUuid()) {
        try {
          validateBodyRange(body, bodyRange);
          mentions.add(new SignalServiceDataMessage.Mention(UuidUtil.parseOrThrow(bodyRange.getMentionUuid()), bodyRange.getStart(), bodyRange.getLength()));
        } catch (IllegalArgumentException e) {
          throw new ProtocolInvalidMessageException(new InvalidMessageException(e), null, 0);
        }
      }
    }

    if (mentions.size() > 0 && !isGroupV2) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("Mentions received in non-GV2 message"), null, 0);
    }

    return mentions;
  }

  private static void validateBodyRange(String body, SignalServiceProtos.DataMessage.BodyRange bodyRange) throws ProtocolInvalidMessageException {
    int incomingBodyLength = body != null ? body.length() : -1;
    int start              = bodyRange.hasStart() ? bodyRange.getStart() : -1;
    int length             = bodyRange.hasLength() ? bodyRange.getLength() : -1;

    if (start < 0 || length < 0 || (start + length) > incomingBodyLength) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("Incoming body range has out-of-bound range"), null, 0);
    }
  }

  private static SignalServiceDataMessage.Sticker createSticker(SignalServiceProtos.DataMessage content) throws ProtocolInvalidMessageException {
    if (!content.hasSticker()                ||
        !content.getSticker().hasPackId()    ||
        !content.getSticker().hasPackKey()   ||
        !content.getSticker().hasStickerId() ||
        !content.getSticker().hasData())
    {
      return null;
    }

    SignalServiceProtos.DataMessage.Sticker sticker = content.getSticker();

    return new SignalServiceDataMessage.Sticker(sticker.getPackId().toByteArray(),
                                                sticker.getPackKey().toByteArray(),
                                                sticker.getStickerId(),
                                                sticker.getEmoji(),
                                                createAttachmentPointer(sticker.getData()));
  }

  private static SignalServiceDataMessage.Reaction createReaction(SignalServiceProtos.DataMessage content) {
    if (!content.hasReaction()                           ||
        !content.getReaction().hasEmoji()                ||
        !content.getReaction().hasTargetAuthorUuid()     ||
        !content.getReaction().hasTargetSentTimestamp())
    {
      return null;
    }

    SignalServiceProtos.DataMessage.Reaction reaction = content.getReaction();
    UUID                                     uuid     = UuidUtil.parseOrNull(reaction.getTargetAuthorUuid());

    if (uuid == null) {
      Log.w(TAG, "Cannot parse author UUID on reaction");
      return null;
    }

    return new SignalServiceDataMessage.Reaction(reaction.getEmoji(),
                        reaction.getRemove(),
                        new SignalServiceAddress(uuid, null),
                        reaction.getTargetSentTimestamp());
  }

  private static SignalServiceDataMessage.RemoteDelete createRemoteDelete(SignalServiceProtos.DataMessage content) {
    if (!content.hasDelete() || !content.getDelete().hasTargetSentTimestamp()) {
      return null;
    }

    SignalServiceProtos.DataMessage.Delete delete = content.getDelete();

    return new SignalServiceDataMessage.RemoteDelete(delete.getTargetSentTimestamp());
  }

  private static SignalServiceDataMessage.GroupCallUpdate createGroupCallUpdate(SignalServiceProtos.DataMessage content) {
    if (!content.hasGroupCallUpdate()) {
      return null;
    }

    SignalServiceProtos.DataMessage.GroupCallUpdate groupCallUpdate = content.getGroupCallUpdate();

    return new SignalServiceDataMessage.GroupCallUpdate(groupCallUpdate.getEraId());
  }

  private static List<SharedContact> createSharedContacts(SignalServiceProtos.DataMessage content) throws ProtocolInvalidMessageException {
    if (content.getContactCount() <= 0) return null;

    List<SharedContact> results = new LinkedList<>();

    for (SignalServiceProtos.DataMessage.Contact contact : content.getContactList()) {
      SharedContact.Builder builder = SharedContact.newBuilder()
                                                   .setName(SharedContact.Name.newBuilder()
                                                                              .setDisplay(contact.getName().getDisplayName())
                                                                              .setFamily(contact.getName().getFamilyName())
                                                                              .setGiven(contact.getName().getGivenName())
                                                                              .setMiddle(contact.getName().getMiddleName())
                                                                              .setPrefix(contact.getName().getPrefix())
                                                                              .setSuffix(contact.getName().getSuffix())
                                                                              .build());

      if (contact.getAddressCount() > 0) {
        for (SignalServiceProtos.DataMessage.Contact.PostalAddress address : contact.getAddressList()) {
          SharedContact.PostalAddress.Type type = SharedContact.PostalAddress.Type.HOME;

          switch (address.getType()) {
            case WORK:   type = SharedContact.PostalAddress.Type.WORK;   break;
            case HOME:   type = SharedContact.PostalAddress.Type.HOME;   break;
            case CUSTOM: type = SharedContact.PostalAddress.Type.CUSTOM; break;
          }

          builder.withAddress(SharedContact.PostalAddress.newBuilder()
                                                         .setCity(address.getCity())
                                                         .setCountry(address.getCountry())
                                                         .setLabel(address.getLabel())
                                                         .setNeighborhood(address.getNeighborhood())
                                                         .setPobox(address.getPobox())
                                                         .setPostcode(address.getPostcode())
                                                         .setRegion(address.getRegion())
                                                         .setStreet(address.getStreet())
                                                         .setType(type)
                                                         .build());
        }
      }

      if (contact.getNumberCount() > 0) {
        for (SignalServiceProtos.DataMessage.Contact.Phone phone : contact.getNumberList()) {
          SharedContact.Phone.Type type = SharedContact.Phone.Type.HOME;

          switch (phone.getType()) {
            case HOME:   type = SharedContact.Phone.Type.HOME;   break;
            case WORK:   type = SharedContact.Phone.Type.WORK;   break;
            case MOBILE: type = SharedContact.Phone.Type.MOBILE; break;
            case CUSTOM: type = SharedContact.Phone.Type.CUSTOM; break;
          }

          builder.withPhone(SharedContact.Phone.newBuilder()
                                               .setLabel(phone.getLabel())
                                               .setType(type)
                                               .setValue(phone.getValue())
                                               .build());
        }
      }

      if (contact.getEmailCount() > 0) {
        for (SignalServiceProtos.DataMessage.Contact.Email email : contact.getEmailList()) {
          SharedContact.Email.Type type = SharedContact.Email.Type.HOME;

          switch (email.getType()) {
            case HOME:   type = SharedContact.Email.Type.HOME;   break;
            case WORK:   type = SharedContact.Email.Type.WORK;   break;
            case MOBILE: type = SharedContact.Email.Type.MOBILE; break;
            case CUSTOM: type = SharedContact.Email.Type.CUSTOM; break;
          }

          builder.withEmail(SharedContact.Email.newBuilder()
                                               .setLabel(email.getLabel())
                                               .setType(type)
                                               .setValue(email.getValue())
                                               .build());
        }
      }

      if (contact.hasAvatar()) {
        builder.setAvatar(SharedContact.Avatar.newBuilder()
                                              .withAttachment(createAttachmentPointer(contact.getAvatar().getAvatar()))
                                              .withProfileFlag(contact.getAvatar().getIsProfile())
                                              .build());
      }

      if (contact.hasOrganization()) {
        builder.withOrganization(contact.getOrganization());
      }

      results.add(builder.build());
    }

    return results;
  }

  private static SignalServiceAttachmentPointer createAttachmentPointer(SignalServiceProtos.AttachmentPointer pointer) throws ProtocolInvalidMessageException {
    return new SignalServiceAttachmentPointer(pointer.getCdnNumber(),
                                              SignalServiceAttachmentRemoteId.from(pointer),
                                              pointer.getContentType(),
                                              pointer.getKey().toByteArray(),
                                              pointer.hasSize() ? Optional.of(pointer.getSize()) : Optional.<Integer>absent(),
                                              pointer.hasThumbnail() ? Optional.of(pointer.getThumbnail().toByteArray()): Optional.<byte[]>absent(),
                                              pointer.getWidth(), pointer.getHeight(),
                                              pointer.hasDigest() ? Optional.of(pointer.getDigest().toByteArray()) : Optional.<byte[]>absent(),
                                              pointer.hasFileName() ? Optional.of(pointer.getFileName()) : Optional.<String>absent(),
                                              (pointer.getFlags() & SignalServiceProtos.AttachmentPointer.Flags.VOICE_MESSAGE_VALUE) != 0,
                                              (pointer.getFlags() & SignalServiceProtos.AttachmentPointer.Flags.BORDERLESS_VALUE) != 0,
                                              pointer.hasCaption() ? Optional.of(pointer.getCaption()) : Optional.<String>absent(),
                                              pointer.hasBlurHash() ? Optional.of(pointer.getBlurHash()) : Optional.<String>absent(),
                                              pointer.hasUploadTimestamp() ? pointer.getUploadTimestamp() : 0);

  }

  private static SignalServiceGroup createGroupV1Info(SignalServiceProtos.DataMessage content) throws ProtocolInvalidMessageException {
    if (!content.hasGroup()) return null;

    SignalServiceGroup.Type type;

    switch (content.getGroup().getType()) {
      case DELIVER:      type = SignalServiceGroup.Type.DELIVER;      break;
      case UPDATE:       type = SignalServiceGroup.Type.UPDATE;       break;
      case QUIT:         type = SignalServiceGroup.Type.QUIT;         break;
      case REQUEST_INFO: type = SignalServiceGroup.Type.REQUEST_INFO; break;
      default:           type = SignalServiceGroup.Type.UNKNOWN;      break;
    }

    if (content.getGroup().getType() != DELIVER) {
      String                         name    = null;
      List<SignalServiceAddress>     members = null;
      SignalServiceAttachmentPointer avatar  = null;

      if (content.getGroup().hasName()) {
        name = content.getGroup().getName();
      }

      if (content.getGroup().getMembersCount() > 0) {
        members = new ArrayList<>(content.getGroup().getMembersCount());

        for (SignalServiceProtos.GroupContext.Member member : content.getGroup().getMembersList()) {
          if (SignalServiceAddress.isValidAddress(null, member.getE164())) {
            members.add(new SignalServiceAddress(null, member.getE164()));
          } else {
            throw new ProtocolInvalidMessageException(new InvalidMessageException("GroupContext.Member had no address!"), null, 0);
          }
        }
      } else if (content.getGroup().getMembersE164Count() > 0) {
        members = new ArrayList<>(content.getGroup().getMembersE164Count());

        for (String member : content.getGroup().getMembersE164List()) {
          members.add(new SignalServiceAddress(null, member));
        }
      }

      if (content.getGroup().hasAvatar()) {
        SignalServiceProtos.AttachmentPointer pointer = content.getGroup().getAvatar();

        avatar = new SignalServiceAttachmentPointer(pointer.getCdnNumber(),
                                                    SignalServiceAttachmentRemoteId.from(pointer),
                                                    pointer.getContentType(),
                                                    pointer.getKey().toByteArray(),
                                                    Optional.of(pointer.getSize()),
                                                    Optional.<byte[]>absent(), 0, 0,
                                                    Optional.fromNullable(pointer.hasDigest() ? pointer.getDigest().toByteArray() : null),
                                                    Optional.<String>absent(),
                                                    false,
                                                    false,
                                                    Optional.<String>absent(),
                                                    Optional.<String>absent(),
                                                    pointer.hasUploadTimestamp() ? pointer.getUploadTimestamp() : 0);
      }

      return new SignalServiceGroup(type, content.getGroup().getId().toByteArray(), name, members, avatar);
    }

    return new SignalServiceGroup(content.getGroup().getId().toByteArray());
  }

  private static SignalServiceGroupV2 createGroupV2Info(SignalServiceProtos.DataMessage content) throws ProtocolInvalidMessageException {
    if (!content.hasGroupV2()) return null;

    SignalServiceProtos.GroupContextV2 groupV2 = content.getGroupV2();
    if (!groupV2.hasMasterKey()) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("No GV2 master key on message"), null, 0);
    }
    if (!groupV2.hasRevision()) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("No GV2 revision on message"), null, 0);
    }

    SignalServiceGroupV2.Builder builder;
    try {
      builder = SignalServiceGroupV2.newBuilder(new GroupMasterKey(groupV2.getMasterKey().toByteArray()))
                                    .withRevision(groupV2.getRevision());
    } catch (InvalidInputException e) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException(e), null, 0);
    }

    if (groupV2.hasGroupChange() && !groupV2.getGroupChange().isEmpty()) {
      builder.withSignedGroupChange(groupV2.getGroupChange().toByteArray());
    }

    return builder.build();
  }
}

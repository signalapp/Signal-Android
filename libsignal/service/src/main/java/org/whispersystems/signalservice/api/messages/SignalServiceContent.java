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
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.InvalidVersionException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.message.DecryptionErrorMessage;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation;
import org.whispersystems.signalservice.api.InvalidMessageStructureException;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.signalservice.api.messages.multidevice.OutgoingPaymentMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ViewedMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.payments.Money;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.util.AttachmentPointerUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageProtocolVersionException;
import org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.SignalServiceMetadataProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class SignalServiceContent {

  private static final String TAG = SignalServiceContent.class.getSimpleName();

  private final SignalServiceAddress      sender;
  private final int                       senderDevice;
  private final long                      timestamp;
  private final long                      serverReceivedTimestamp;
  private final long                      serverDeliveredTimestamp;
  private final boolean                   needsReceipt;
  private final SignalServiceContentProto serializedState;
  private final String                    serverUuid;
  private final Optional<byte[]>          groupId;
  private final String                    destinationUuid;

  private final Optional<SignalServiceDataMessage>         message;
  private final Optional<SignalServiceSyncMessage>         synchronizeMessage;
  private final Optional<SignalServiceCallMessage>         callMessage;
  private final Optional<SignalServiceReceiptMessage>      readMessage;
  private final Optional<SignalServiceTypingMessage>       typingMessage;
  private final Optional<SenderKeyDistributionMessage>     senderKeyDistributionMessage;
  private final Optional<DecryptionErrorMessage>           decryptionErrorMessage;
  private final Optional<SignalServiceStoryMessage>        storyMessage;
  private final Optional<SignalServicePniSignatureMessage> pniSignatureMessage;

  private SignalServiceContent(SignalServiceDataMessage message,
                               Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage,
                               Optional<SignalServicePniSignatureMessage> pniSignatureMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverUuid,
                               Optional<byte[]> groupId,
                               String destinationUuid,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverUuid               = serverUuid;
    this.groupId                  = groupId;
    this.destinationUuid          = destinationUuid;
    this.serializedState          = serializedState;

    this.message                      = Optional.ofNullable(message);
    this.synchronizeMessage           = Optional.empty();
    this.callMessage                  = Optional.empty();
    this.readMessage                  = Optional.empty();
    this.typingMessage                = Optional.empty();
    this.senderKeyDistributionMessage = senderKeyDistributionMessage;
    this.decryptionErrorMessage       = Optional.empty();
    this.storyMessage                 = Optional.empty();
    this.pniSignatureMessage          = pniSignatureMessage;
  }

  private SignalServiceContent(SignalServiceSyncMessage synchronizeMessage,
                               Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage,
                               Optional<SignalServicePniSignatureMessage> pniSignatureMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverUuid,
                               Optional<byte[]> groupId,
                               String destinationUuid,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverUuid               = serverUuid;
    this.groupId                  = groupId;
    this.destinationUuid          = destinationUuid;
    this.serializedState          = serializedState;

    this.message                      = Optional.empty();
    this.synchronizeMessage           = Optional.ofNullable(synchronizeMessage);
    this.callMessage                  = Optional.empty();
    this.readMessage                  = Optional.empty();
    this.typingMessage                = Optional.empty();
    this.senderKeyDistributionMessage = senderKeyDistributionMessage;
    this.decryptionErrorMessage       = Optional.empty();
    this.storyMessage                 = Optional.empty();
    this.pniSignatureMessage          = pniSignatureMessage;
  }

  private SignalServiceContent(SignalServiceCallMessage callMessage,
                               Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage,
                               Optional<SignalServicePniSignatureMessage> pniSignatureMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverUuid,
                               Optional<byte[]> groupId,
                               String destinationUuid,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverUuid               = serverUuid;
    this.groupId                  = groupId;
    this.destinationUuid          = destinationUuid;
    this.serializedState          = serializedState;

    this.message                      = Optional.empty();
    this.synchronizeMessage           = Optional.empty();
    this.callMessage                  = Optional.of(callMessage);
    this.readMessage                  = Optional.empty();
    this.typingMessage                = Optional.empty();
    this.senderKeyDistributionMessage = senderKeyDistributionMessage;
    this.decryptionErrorMessage       = Optional.empty();
    this.storyMessage                 = Optional.empty();
    this.pniSignatureMessage          = pniSignatureMessage;
  }

  private SignalServiceContent(SignalServiceReceiptMessage receiptMessage,
                               Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage,
                               Optional<SignalServicePniSignatureMessage> pniSignatureMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverUuid,
                               Optional<byte[]> groupId,
                               String destinationUuid,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverUuid               = serverUuid;
    this.groupId                  = groupId;
    this.destinationUuid          = destinationUuid;
    this.serializedState          = serializedState;

    this.message                      = Optional.empty();
    this.synchronizeMessage           = Optional.empty();
    this.callMessage                  = Optional.empty();
    this.readMessage                  = Optional.of(receiptMessage);
    this.typingMessage                = Optional.empty();
    this.senderKeyDistributionMessage = senderKeyDistributionMessage;
    this.decryptionErrorMessage       = Optional.empty();
    this.storyMessage                 = Optional.empty();
    this.pniSignatureMessage          = pniSignatureMessage;
  }

  private SignalServiceContent(DecryptionErrorMessage errorMessage,
                               Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage,
                               Optional<SignalServicePniSignatureMessage> pniSignatureMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverUuid,
                               Optional<byte[]> groupId,
                               String destinationUuid,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverUuid               = serverUuid;
    this.groupId                  = groupId;
    this.destinationUuid          = destinationUuid;
    this.serializedState          = serializedState;

    this.message                      = Optional.empty();
    this.synchronizeMessage           = Optional.empty();
    this.callMessage                  = Optional.empty();
    this.readMessage                  = Optional.empty();
    this.typingMessage                = Optional.empty();
    this.senderKeyDistributionMessage = senderKeyDistributionMessage;
    this.decryptionErrorMessage       = Optional.of(errorMessage);
    this.storyMessage                 = Optional.empty();
    this.pniSignatureMessage          = pniSignatureMessage;
  }

  private SignalServiceContent(SignalServiceTypingMessage typingMessage,
                               Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage,
                               Optional<SignalServicePniSignatureMessage> pniSignatureMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverUuid,
                               Optional<byte[]> groupId,
                               String destinationUuid,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverUuid               = serverUuid;
    this.groupId                  = groupId;
    this.destinationUuid          = destinationUuid;
    this.serializedState          = serializedState;

    this.message                      = Optional.empty();
    this.synchronizeMessage           = Optional.empty();
    this.callMessage                  = Optional.empty();
    this.readMessage                  = Optional.empty();
    this.typingMessage                = Optional.of(typingMessage);
    this.senderKeyDistributionMessage = senderKeyDistributionMessage;
    this.decryptionErrorMessage       = Optional.empty();
    this.storyMessage                 = Optional.empty();
    this.pniSignatureMessage          = pniSignatureMessage;
  }

  private SignalServiceContent(SenderKeyDistributionMessage senderKeyDistributionMessage,
                               Optional<SignalServicePniSignatureMessage> pniSignatureMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverUuid,
                               Optional<byte[]> groupId,
                               String destinationUuid,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverUuid               = serverUuid;
    this.groupId                  = groupId;
    this.destinationUuid          = destinationUuid;
    this.serializedState          = serializedState;

    this.message                      = Optional.empty();
    this.synchronizeMessage           = Optional.empty();
    this.callMessage                  = Optional.empty();
    this.readMessage                  = Optional.empty();
    this.typingMessage                = Optional.empty();
    this.senderKeyDistributionMessage = Optional.of(senderKeyDistributionMessage);
    this.decryptionErrorMessage       = Optional.empty();
    this.storyMessage                 = Optional.empty();
    this.pniSignatureMessage          = pniSignatureMessage;
  }

  private SignalServiceContent(SignalServicePniSignatureMessage pniSignatureMessage,
                               Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverUuid,
                               Optional<byte[]> groupId,
                               String destinationUuid,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverUuid               = serverUuid;
    this.groupId                  = groupId;
    this.destinationUuid          = destinationUuid;
    this.serializedState          = serializedState;

    this.message                      = Optional.empty();
    this.synchronizeMessage           = Optional.empty();
    this.callMessage                  = Optional.empty();
    this.readMessage                  = Optional.empty();
    this.typingMessage                = Optional.empty();
    this.senderKeyDistributionMessage = senderKeyDistributionMessage;
    this.decryptionErrorMessage       = Optional.empty();
    this.storyMessage                 = Optional.empty();
    this.pniSignatureMessage          = Optional.of(pniSignatureMessage);
  }

  private SignalServiceContent(SignalServiceStoryMessage storyMessage,
                               Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage,
                               Optional<SignalServicePniSignatureMessage> pniSignatureMessage,
                               SignalServiceAddress sender,
                               int senderDevice,
                               long timestamp,
                               long serverReceivedTimestamp,
                               long serverDeliveredTimestamp,
                               boolean needsReceipt,
                               String serverUuid,
                               Optional<byte[]> groupId,
                               String destinationUuid,
                               SignalServiceContentProto serializedState)
  {
    this.sender                   = sender;
    this.senderDevice             = senderDevice;
    this.timestamp                = timestamp;
    this.serverReceivedTimestamp  = serverReceivedTimestamp;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
    this.needsReceipt             = needsReceipt;
    this.serverUuid               = serverUuid;
    this.groupId                  = groupId;
    this.destinationUuid          = destinationUuid;
    this.serializedState          = serializedState;

    this.message                      = Optional.empty();
    this.synchronizeMessage           = Optional.empty();
    this.callMessage                  = Optional.empty();
    this.readMessage                  = Optional.empty();
    this.typingMessage                = Optional.empty();
    this.senderKeyDistributionMessage = senderKeyDistributionMessage;
    this.decryptionErrorMessage       = Optional.empty();
    this.storyMessage                 = Optional.of(storyMessage);
    this.pniSignatureMessage          = pniSignatureMessage;
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

  public Optional<SignalServiceStoryMessage> getStoryMessage() {
    return storyMessage;
  }

  public Optional<SenderKeyDistributionMessage> getSenderKeyDistributionMessage() {
    return senderKeyDistributionMessage;
  }

  public Optional<DecryptionErrorMessage> getDecryptionErrorMessage() {
    return decryptionErrorMessage;
  }

  public Optional<SignalServicePniSignatureMessage> getPniSignatureMessage() {
    return pniSignatureMessage;
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

  public String getServerUuid() {
    return serverUuid;
  }

  public Optional<byte[]> getGroupId() {
    return groupId;
  }

  public String getDestinationUuid() {
    return destinationUuid;
  }

  public byte[] serialize() {
    return serializedState.toByteArray();
  }

  public static SignalServiceContent deserialize(byte[] data) {
    try {
      if (data == null) return null;

      SignalServiceContentProto signalServiceContentProto = SignalServiceContentProto.parseFrom(data);

      return createFromProto(signalServiceContentProto);
    } catch (InvalidProtocolBufferException | ProtocolInvalidMessageException | ProtocolInvalidKeyException | UnsupportedDataMessageException | InvalidMessageStructureException e) {
      // We do not expect any of these exceptions if this byte[] has come from serialize.
      throw new AssertionError(e);
    }
  }

  /**
   * Takes internal protobuf serialization format and processes it into a {@link SignalServiceContent}.
   */
  public static SignalServiceContent createFromProto(SignalServiceContentProto serviceContentProto)
      throws ProtocolInvalidMessageException, ProtocolInvalidKeyException, UnsupportedDataMessageException, InvalidMessageStructureException
  {
    SignalServiceMetadata metadata     = SignalServiceMetadataProtobufSerializer.fromProtobuf(serviceContentProto.getMetadata());
    SignalServiceAddress  localAddress = SignalServiceAddressProtobufSerializer.fromProtobuf(serviceContentProto.getLocalAddress());

    if (serviceContentProto.getDataCase() == SignalServiceContentProto.DataCase.LEGACYDATAMESSAGE) {
      throw new InvalidMessageStructureException("Legacy message!");
    } else if (serviceContentProto.getDataCase() == SignalServiceContentProto.DataCase.CONTENT) {
      SignalServiceProtos.Content            message                      = serviceContentProto.getContent();
      Optional<SenderKeyDistributionMessage> senderKeyDistributionMessage = Optional.empty();

      if (message.hasSenderKeyDistributionMessage()) {
        try {
          senderKeyDistributionMessage = Optional.of(new SenderKeyDistributionMessage(message.getSenderKeyDistributionMessage().toByteArray()));
        } catch (LegacyMessageException | InvalidMessageException | InvalidVersionException | InvalidKeyException e) {
          Log.w(TAG, "Failed to parse SenderKeyDistributionMessage!", e);
        }
      }

      Optional<SignalServicePniSignatureMessage> pniSignatureMessage = Optional.empty();

      if (message.hasPniSignatureMessage()) {
        PNI pni = PNI.parseOrNull(message.getPniSignatureMessage().getPni().toByteArray());
        if (pni != null) {
          pniSignatureMessage = Optional.of(new SignalServicePniSignatureMessage(pni, message.getPniSignatureMessage().getSignature().toByteArray()));
        } else {
          Log.w(TAG, "Invalid PNI on PNI signature message! Ignoring.");
        }
      }

      if (message.hasDataMessage()) {
        return new SignalServiceContent(createSignalServiceMessage(metadata, message.getDataMessage()),
                                        senderKeyDistributionMessage,
                                        pniSignatureMessage,
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        metadata.isNeedsReceipt(),
                                        metadata.getServerGuid(),
                                        metadata.getGroupId(),
                                        metadata.getDestinationUuid(),
                                        serviceContentProto);
      } else if (message.hasSyncMessage() && localAddress.matches(metadata.getSender())) {
        return new SignalServiceContent(createSynchronizeMessage(metadata, message.getSyncMessage()),
                                        senderKeyDistributionMessage,
                                        pniSignatureMessage,
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        metadata.isNeedsReceipt(),
                                        metadata.getServerGuid(),
                                        metadata.getGroupId(),
                                        metadata.getDestinationUuid(),
                                        serviceContentProto);
      } else if (message.hasCallMessage()) {
        return new SignalServiceContent(createCallMessage(message.getCallMessage()),
                                        senderKeyDistributionMessage,
                                        pniSignatureMessage,
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        metadata.isNeedsReceipt(),
                                        metadata.getServerGuid(),
                                        metadata.getGroupId(),
                                        metadata.getDestinationUuid(),
                                        serviceContentProto);
      } else if (message.hasReceiptMessage()) {
        return new SignalServiceContent(createReceiptMessage(metadata, message.getReceiptMessage()),
                                        senderKeyDistributionMessage,
                                        pniSignatureMessage,
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        metadata.isNeedsReceipt(),
                                        metadata.getServerGuid(),
                                        metadata.getGroupId(),
                                        metadata.getDestinationUuid(),
                                        serviceContentProto);
      } else if (message.hasTypingMessage()) {
        return new SignalServiceContent(createTypingMessage(metadata, message.getTypingMessage()),
                                        senderKeyDistributionMessage,
                                        pniSignatureMessage,
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        false,
                                        metadata.getServerGuid(),
                                        metadata.getGroupId(),
                                        metadata.getDestinationUuid(),
                                        serviceContentProto);
      } else if (message.hasDecryptionErrorMessage()) {
        return new SignalServiceContent(createDecryptionErrorMessage(metadata, message.getDecryptionErrorMessage()),
                                        senderKeyDistributionMessage,
                                        pniSignatureMessage,
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        metadata.isNeedsReceipt(),
                                        metadata.getServerGuid(),
                                        metadata.getGroupId(),
                                        metadata.getDestinationUuid(),
                                        serviceContentProto);
      } else if (message.hasStoryMessage()) {
        return new SignalServiceContent(createStoryMessage(message.getStoryMessage()),
                                        senderKeyDistributionMessage,
                                        pniSignatureMessage,
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        false,
                                        metadata.getServerGuid(),
                                        metadata.getGroupId(),
                                        metadata.getDestinationUuid(),
                                        serviceContentProto);
      } else if (pniSignatureMessage.isPresent()) {
        return new SignalServiceContent(pniSignatureMessage.get(),
                                        senderKeyDistributionMessage,
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        false,
                                        metadata.getServerGuid(),
                                        metadata.getGroupId(),
                                        metadata.getDestinationUuid(),
                                        serviceContentProto);
      } else if (senderKeyDistributionMessage.isPresent()) {
        // IMPORTANT: This block should always be last, since you can pair SKDM's with other content
        return new SignalServiceContent(senderKeyDistributionMessage.get(),
                                        pniSignatureMessage,
                                        metadata.getSender(),
                                        metadata.getSenderDevice(),
                                        metadata.getTimestamp(),
                                        metadata.getServerReceivedTimestamp(),
                                        metadata.getServerDeliveredTimestamp(),
                                        false,
                                        metadata.getServerGuid(),
                                        metadata.getGroupId(),
                                        metadata.getDestinationUuid(),
                                        serviceContentProto);
      }
    }

    return null;
  }

  private static SignalServiceDataMessage createSignalServiceMessage(SignalServiceMetadata metadata,
                                                                     SignalServiceProtos.DataMessage content)
      throws UnsupportedDataMessageException, InvalidMessageStructureException
  {
    SignalServiceGroupV2           groupInfoV2  = createGroupV2Info(content);
    Optional<SignalServiceGroupV2> groupContext = Optional.ofNullable(groupInfoV2);

    List<SignalServiceAttachment>            attachments      = new LinkedList<>();
    boolean                                  endSession       = ((content.getFlags() & SignalServiceProtos.DataMessage.Flags.END_SESSION_VALUE            ) != 0);
    boolean                                  expirationUpdate = ((content.getFlags() & SignalServiceProtos.DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE) != 0);
    boolean                                  profileKeyUpdate = ((content.getFlags() & SignalServiceProtos.DataMessage.Flags.PROFILE_KEY_UPDATE_VALUE     ) != 0);
    boolean                                  isGroupV2        = groupInfoV2 != null;
    SignalServiceDataMessage.Quote           quote            = createQuote(content, isGroupV2);
    List<SharedContact>                      sharedContacts   = createSharedContacts(content);
    List<SignalServicePreview>               previews         = createPreviews(content);
    List<SignalServiceDataMessage.Mention>   mentions         = createMentions(content.getBodyRangesList(), content.getBody(), isGroupV2);
    SignalServiceDataMessage.Sticker         sticker          = createSticker(content);
    SignalServiceDataMessage.Reaction        reaction         = createReaction(content);
    SignalServiceDataMessage.RemoteDelete    remoteDelete     = createRemoteDelete(content);
    SignalServiceDataMessage.GroupCallUpdate groupCallUpdate  = createGroupCallUpdate(content);
    SignalServiceDataMessage.StoryContext    storyContext     = createStoryContext(content);
    SignalServiceDataMessage.GiftBadge       giftBadge        = createGiftBadge(content);

    if (content.getRequiredProtocolVersion() > SignalServiceProtos.DataMessage.ProtocolVersion.CURRENT_VALUE) {
      throw new UnsupportedDataMessageProtocolVersionException(SignalServiceProtos.DataMessage.ProtocolVersion.CURRENT_VALUE,
                                                               content.getRequiredProtocolVersion(),
                                                               metadata.getSender().getIdentifier(),
                                                               metadata.getSenderDevice(),
                                                               groupContext);
    }

    SignalServiceDataMessage.Payment payment = createPayment(content);

    if (content.getRequiredProtocolVersion() > SignalServiceProtos.DataMessage.ProtocolVersion.CURRENT.getNumber()) {
      throw new UnsupportedDataMessageProtocolVersionException(SignalServiceProtos.DataMessage.ProtocolVersion.CURRENT.getNumber(),
                                                               content.getRequiredProtocolVersion(),
                                                               metadata.getSender().getIdentifier(),
                                                               metadata.getSenderDevice(),
                                                               groupContext);
    }

    for (SignalServiceProtos.AttachmentPointer pointer : content.getAttachmentsList()) {
      attachments.add(createAttachmentPointer(pointer));
    }

    if (content.hasTimestamp() && content.getTimestamp() != metadata.getTimestamp()) {
      throw new InvalidMessageStructureException("Timestamps don't match: " + content.getTimestamp() + " vs " + metadata.getTimestamp(),
                                                 metadata.getSender().getIdentifier(),
                                                 metadata.getSenderDevice());
    }

    return new SignalServiceDataMessage(metadata.getTimestamp(),
                                        groupInfoV2,
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
                                        groupCallUpdate,
                                        payment,
                                        storyContext,
                                        giftBadge);
  }

  private static SignalServiceSyncMessage createSynchronizeMessage(SignalServiceMetadata metadata,
                                                                   SignalServiceProtos.SyncMessage content)
      throws ProtocolInvalidKeyException, UnsupportedDataMessageException, InvalidMessageStructureException
  {
    if (content.hasSent()) {
      Map<ServiceId, Boolean>                 unidentifiedStatuses = new HashMap<>();
      SignalServiceProtos.SyncMessage.Sent    sentContent          = content.getSent();
      Optional<SignalServiceDataMessage>      dataMessage          = sentContent.hasMessage() ? Optional.of(createSignalServiceMessage(metadata, sentContent.getMessage())) : Optional.empty();
      Optional<SignalServiceStoryMessage>     storyMessage         = sentContent.hasStoryMessage() ? Optional.of(createStoryMessage(sentContent.getStoryMessage())) : Optional.empty();
      Optional<SignalServiceAddress>          address              = SignalServiceAddress.isValidAddress(sentContent.getDestinationUuid())
                                                                     ? Optional.of(new SignalServiceAddress(ServiceId.parseOrThrow(sentContent.getDestinationUuid()), sentContent.getDestinationE164()))
                                                                     : Optional.empty();
      Set<SignalServiceStoryMessageRecipient> recipientManifest    = sentContent.getStoryMessageRecipientsList()
                                                                                .stream()
                                                                                .map(SignalServiceContent::createSignalServiceStoryMessageRecipient)
                                                                                .collect(Collectors.toSet());

      if (!address.isPresent()                                                        &&
          !dataMessage.flatMap(SignalServiceDataMessage::getGroupContext).isPresent() &&
          !storyMessage.flatMap(SignalServiceStoryMessage::getGroupContext).isPresent() &&
          recipientManifest.isEmpty())
      {
        throw new InvalidMessageStructureException("SyncMessage missing destination, group ID, and recipient manifest!");
      }

      for (SignalServiceProtos.SyncMessage.Sent.UnidentifiedDeliveryStatus status : sentContent.getUnidentifiedStatusList()) {
        if (SignalServiceAddress.isValidAddress(status.getDestinationUuid(), null)) {
          unidentifiedStatuses.put(ServiceId.parseOrNull(status.getDestinationUuid()), status.getUnidentified());
        } else {
          Log.w(TAG, "Encountered an invalid UnidentifiedDeliveryStatus in a SentTranscript! Ignoring.");
        }
      }

      return SignalServiceSyncMessage.forSentTranscript(new SentTranscriptMessage(address,
                                                                                  sentContent.getTimestamp(),
                                                                                  dataMessage,
                                                                                  sentContent.getExpirationStartTimestamp(),
                                                                                  unidentifiedStatuses,
                                                                                  sentContent.getIsRecipientUpdate(),
                                                                                  storyMessage,
                                                                                  recipientManifest));
    }

    if (content.hasRequest()) {
      return SignalServiceSyncMessage.forRequest(new RequestMessage(content.getRequest()));
    }

    if (content.getReadList().size() > 0) {
      List<ReadMessage> readMessages = new LinkedList<>();

      for (SignalServiceProtos.SyncMessage.Read read : content.getReadList()) {
        ServiceId serviceId = ServiceId.parseOrNull(read.getSenderUuid());
        if (serviceId != null) {
          readMessages.add(new ReadMessage(serviceId, read.getTimestamp()));
        } else {
          Log.w(TAG, "Encountered an invalid ReadMessage! Ignoring.");
        }
      }

      return SignalServiceSyncMessage.forRead(readMessages);
    }

    if (content.getViewedList().size() > 0) {
      List<ViewedMessage> viewedMessages = new LinkedList<>();

      for (SignalServiceProtos.SyncMessage.Viewed viewed : content.getViewedList()) {
        ServiceId serviceId = ServiceId.parseOrNull(viewed.getSenderUuid());
        if (serviceId != null) {
          viewedMessages.add(new ViewedMessage(serviceId, viewed.getTimestamp()));
        } else {
          Log.w(TAG, "Encountered an invalid ReadMessage! Ignoring.");
        }
      }

      return SignalServiceSyncMessage.forViewed(viewedMessages);
    }

    if (content.hasViewOnceOpen()) {
      ServiceId serviceId = ServiceId.parseOrNull(content.getViewOnceOpen().getSenderUuid());
      if (serviceId != null) {
        ViewOnceOpenMessage timerRead = new ViewOnceOpenMessage(serviceId, content.getViewOnceOpen().getTimestamp());
        return SignalServiceSyncMessage.forViewOnceOpen(timerRead);
      } else {
        throw new InvalidMessageStructureException("ViewOnceOpen message has no sender!");
      }
    }

    if (content.hasVerified()) {
      if (SignalServiceAddress.isValidAddress(content.getVerified().getDestinationUuid())) {
        try {
          SignalServiceProtos.Verified verified    = content.getVerified();
          SignalServiceAddress         destination = new SignalServiceAddress(ServiceId.parseOrThrow(verified.getDestinationUuid()));
          IdentityKey                  identityKey = new IdentityKey(verified.getIdentityKey().toByteArray(), 0);

          VerifiedMessage.VerifiedState verifiedState;

          if (verified.getState() == SignalServiceProtos.Verified.State.DEFAULT) {
            verifiedState = VerifiedMessage.VerifiedState.DEFAULT;
          } else if (verified.getState() == SignalServiceProtos.Verified.State.VERIFIED) {
            verifiedState = VerifiedMessage.VerifiedState.VERIFIED;
          } else if (verified.getState() == SignalServiceProtos.Verified.State.UNVERIFIED) {
            verifiedState = VerifiedMessage.VerifiedState.UNVERIFIED;
          } else {
            throw new InvalidMessageStructureException("Unknown state: " + verified.getState().getNumber(),
                                                       metadata.getSender().getIdentifier(),
                                                       metadata.getSenderDevice());
          }

          return SignalServiceSyncMessage.forVerified(new VerifiedMessage(destination, identityKey, verifiedState, System.currentTimeMillis()));
        } catch (InvalidKeyException e) {
          throw new ProtocolInvalidKeyException(e, metadata.getSender().getIdentifier(), metadata.getSenderDevice());
        }
      } else {
        throw new InvalidMessageStructureException("Verified message has no sender!");
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

      return SignalServiceSyncMessage.forConfiguration(new ConfigurationMessage(Optional.ofNullable(readReceipts),
                                                                                Optional.ofNullable(unidentifiedDeliveryIndicators),
                                                                                Optional.ofNullable(typingIndicators),
                                                                                Optional.ofNullable(linkPreviews)));
    }

    if (content.hasFetchLatest() && content.getFetchLatest().hasType()) {
      switch (content.getFetchLatest().getType()) {
        case LOCAL_PROFILE:       return SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.LOCAL_PROFILE);
        case STORAGE_MANIFEST:    return SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.STORAGE_MANIFEST);
        case SUBSCRIPTION_STATUS: return SignalServiceSyncMessage.forFetchLatest(SignalServiceSyncMessage.FetchType.SUBSCRIPTION_STATUS);
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
        ServiceId serviceId = ServiceId.parseOrNull(content.getMessageRequestResponse().getThreadUuid());
        if (serviceId != null) {
          responseMessage = MessageRequestResponseMessage.forIndividual(serviceId, type);
        } else {
          throw new InvalidMessageStructureException("Message request response has an invalid thread identifier!");
        }
      }

      return SignalServiceSyncMessage.forMessageRequestResponse(responseMessage);
    }

    if (content.hasOutgoingPayment()) {
      SignalServiceProtos.SyncMessage.OutgoingPayment outgoingPayment = content.getOutgoingPayment();
      switch (outgoingPayment.getPaymentDetailCase()) {
        case MOBILECOIN: {
          SignalServiceProtos.SyncMessage.OutgoingPayment.MobileCoin mobileCoin = outgoingPayment.getMobileCoin();
          Money.MobileCoin                                           amount     = Money.picoMobileCoin(mobileCoin.getAmountPicoMob());
          Money.MobileCoin                                           fee        = Money.picoMobileCoin(mobileCoin.getFeePicoMob());
          ByteString                                                 address    = mobileCoin.getRecipientAddress();
          Optional<ServiceId>                                        recipient  = Optional.ofNullable(ServiceId.parseOrNull(outgoingPayment.getRecipientUuid()));

          return SignalServiceSyncMessage.forOutgoingPayment(new OutgoingPaymentMessage(recipient,
                                                                                        amount,
                                                                                        fee,
                                                                                        mobileCoin.getReceipt(),
                                                                                        mobileCoin.getLedgerBlockIndex(),
                                                                                        mobileCoin.getLedgerBlockTimestamp(),
                                                                                        address.isEmpty() ? Optional.empty() : Optional.of(address.toByteArray()),
                                                                                        Optional.of(outgoingPayment.getNote()),
                                                                                        mobileCoin.getOutputPublicKeysList(),
                                                                                        mobileCoin.getSpentKeyImagesList()));
        }
        default:
          return SignalServiceSyncMessage.empty();
      }
    }

    if (content.hasKeys() && content.getKeys().hasStorageService()) {
      byte[] storageKey = content.getKeys().getStorageService().toByteArray();

      return SignalServiceSyncMessage.forKeys(new KeysMessage(Optional.of(new StorageKey(storageKey))));
    }

    if (content.hasContacts()) {
      return SignalServiceSyncMessage.forContacts(new ContactsMessage(createAttachmentPointer(content.getContacts().getBlob()), content.getContacts().getComplete()));
    }

    return SignalServiceSyncMessage.empty();
  }

  private static SignalServiceStoryMessageRecipient createSignalServiceStoryMessageRecipient(SignalServiceProtos.SyncMessage.Sent.StoryMessageRecipient storyMessageRecipient) {
    return new SignalServiceStoryMessageRecipient(
        new SignalServiceAddress(ServiceId.parseOrThrow(storyMessageRecipient.getDestinationUuid())),
        storyMessageRecipient.getDistributionListIdsList(),
        storyMessageRecipient.getIsAllowedToReply()
    );
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
      return SignalServiceCallMessage.forOpaque(new OpaqueMessage(opaque.getData().toByteArray(), null), isMultiRing, destinationDeviceId);
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

  private static DecryptionErrorMessage createDecryptionErrorMessage(SignalServiceMetadata metadata, ByteString content) throws InvalidMessageStructureException {
    try {
      return new DecryptionErrorMessage(content.toByteArray());
    } catch (InvalidMessageException e) {
      throw new InvalidMessageStructureException(e, metadata.getSender().getIdentifier(), metadata.getSenderDevice());
    }
  }

  private static SignalServiceTypingMessage createTypingMessage(SignalServiceMetadata metadata, SignalServiceProtos.TypingMessage content) throws InvalidMessageStructureException {
    SignalServiceTypingMessage.Action action;

    if      (content.getAction() == SignalServiceProtos.TypingMessage.Action.STARTED) action = SignalServiceTypingMessage.Action.STARTED;
    else if (content.getAction() == SignalServiceProtos.TypingMessage.Action.STOPPED) action = SignalServiceTypingMessage.Action.STOPPED;
    else                                                          action = SignalServiceTypingMessage.Action.UNKNOWN;

    if (content.hasTimestamp() && content.getTimestamp() != metadata.getTimestamp()) {
      throw new InvalidMessageStructureException("Timestamps don't match: " + content.getTimestamp() + " vs " + metadata.getTimestamp(),
                                                 metadata.getSender().getIdentifier(),
                                                 metadata.getSenderDevice());
    }

    return new SignalServiceTypingMessage(action, content.getTimestamp(),
                                          content.hasGroupId() ? Optional.of(content.getGroupId().toByteArray()) :
                                                                 Optional.empty());
  }

  private static SignalServiceStoryMessage createStoryMessage(SignalServiceProtos.StoryMessage content) throws InvalidMessageStructureException {
    byte[] profileKey = content.hasProfileKey() ? content.getProfileKey().toByteArray() : null;

    if (content.hasFileAttachment()) {
      return SignalServiceStoryMessage.forFileAttachment(profileKey,
                                                         createGroupV2Info(content),
                                                         createAttachmentPointer(content.getFileAttachment()),
                                                         content.getAllowsReplies());
    } else {
      return SignalServiceStoryMessage.forTextAttachment(profileKey,
                                                         createGroupV2Info(content),
                                                         createTextAttachment(content.getTextAttachment()),
                                                         content.getAllowsReplies());
    }
  }

  private static SignalServiceDataMessage.Quote createQuote(SignalServiceProtos.DataMessage content, boolean isGroupV2)
      throws  InvalidMessageStructureException
  {
    if (!content.hasQuote()) return null;

    List<SignalServiceDataMessage.Quote.QuotedAttachment> attachments = new LinkedList<>();

    for (SignalServiceProtos.DataMessage.Quote.QuotedAttachment attachment : content.getQuote().getAttachmentsList()) {
      attachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.getContentType(),
                                                                          attachment.getFileName(),
                                                                          attachment.hasThumbnail() ? createAttachmentPointer(attachment.getThumbnail()) : null));
    }

    ServiceId author = ServiceId.parseOrNull(content.getQuote().getAuthorUuid());
    if (author != null) {
      return new SignalServiceDataMessage.Quote(content.getQuote().getId(),
                                                author,
                                                content.getQuote().getText(),
                                                attachments,
                                                createMentions(content.getQuote().getBodyRangesList(), content.getQuote().getText(), isGroupV2),
                                                SignalServiceDataMessage.Quote.Type.fromProto(content.getQuote().getType()));
    } else {
      Log.w(TAG, "Quote was missing an author! Returning null.");
      return null;
    }
  }

  private static List<SignalServicePreview> createPreviews(SignalServiceProtos.DataMessage content) throws InvalidMessageStructureException {
    if (content.getPreviewCount() <= 0) return null;

    List<SignalServicePreview> results = new LinkedList<>();

    for (SignalServiceProtos.Preview preview : content.getPreviewList()) {
      results.add(createPreview(preview));
    }

    return results;
  }

  private static SignalServicePreview createPreview(SignalServiceProtos.Preview preview) throws InvalidMessageStructureException {
    SignalServiceAttachment attachment = null;

    if (preview.hasImage()) {
      attachment = createAttachmentPointer(preview.getImage());
    }

    return new SignalServicePreview(preview.getUrl(),
                                    preview.getTitle(),
                                    preview.getDescription(),
                                    preview.getDate(),
                                    Optional.ofNullable(attachment));
  }

  private static List<SignalServiceDataMessage.Mention> createMentions(List<SignalServiceProtos.DataMessage.BodyRange> bodyRanges, String body, boolean isGroupV2)
      throws InvalidMessageStructureException
  {
    if (bodyRanges == null || bodyRanges.isEmpty() || body == null) {
      return null;
    }

    List<SignalServiceDataMessage.Mention> mentions = new LinkedList<>();

    for (SignalServiceProtos.DataMessage.BodyRange bodyRange : bodyRanges) {
      if (bodyRange.hasMentionUuid()) {
        try {
          mentions.add(new SignalServiceDataMessage.Mention(ServiceId.parseOrThrow(bodyRange.getMentionUuid()), bodyRange.getStart(), bodyRange.getLength()));
        } catch (IllegalArgumentException e) {
          throw new InvalidMessageStructureException("Invalid body range!");
        }
      }
    }

    if (mentions.size() > 0 && !isGroupV2) {
      Log.w(TAG, "Mentions received in non-GV2 message");
    }

    return mentions;
  }

  private static SignalServiceDataMessage.Sticker createSticker(SignalServiceProtos.DataMessage content) throws InvalidMessageStructureException {
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

    SignalServiceProtos.DataMessage.Reaction reaction  = content.getReaction();
    ServiceId                                serviceId = ServiceId.parseOrNull(reaction.getTargetAuthorUuid());

    if (serviceId == null) {
      Log.w(TAG, "Cannot parse author UUID on reaction");
      return null;
    }

    return new SignalServiceDataMessage.Reaction(reaction.getEmoji(),
                                                 reaction.getRemove(),
                                                 serviceId,
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

  private static SignalServiceDataMessage.Payment createPayment(SignalServiceProtos.DataMessage content) throws InvalidMessageStructureException {
    if (!content.hasPayment()) {
      return null;
    }

    SignalServiceProtos.DataMessage.Payment payment = content.getPayment();

    switch (payment.getItemCase()) {
      case NOTIFICATION:
        return new SignalServiceDataMessage.Payment(createPaymentNotification(payment), null);
      case ACTIVATION:
        return new SignalServiceDataMessage.Payment(null, createPaymentActivation(payment));
      default:
        throw new InvalidMessageStructureException("Unknown payment item");
    }
  }

  private static SignalServiceDataMessage.StoryContext createStoryContext(SignalServiceProtos.DataMessage content) throws InvalidMessageStructureException {
    if (!content.hasStoryContext()) {
      return null;
    }

    ServiceId serviceId = ServiceId.parseOrNull(content.getStoryContext().getAuthorUuid());

    if (serviceId == null) {
      throw new InvalidMessageStructureException("Invalid author ACI!");
    }

    return new SignalServiceDataMessage.StoryContext(serviceId, content.getStoryContext().getSentTimestamp());
  }

  private static SignalServiceDataMessage.GiftBadge createGiftBadge(SignalServiceProtos.DataMessage content) throws InvalidMessageStructureException {
    if (!content.hasGiftBadge()) {
      return null;
    }

    if (!content.getGiftBadge().hasReceiptCredentialPresentation()) {
      throw new InvalidMessageStructureException("GiftBadge does not contain a receipt credential presentation!");
    }

    try {
      ReceiptCredentialPresentation receiptCredentialPresentation = new ReceiptCredentialPresentation(content.getGiftBadge().getReceiptCredentialPresentation().toByteArray());
      return new SignalServiceDataMessage.GiftBadge(receiptCredentialPresentation);
    } catch (InvalidInputException invalidInputException) {
      throw new InvalidMessageStructureException(invalidInputException);
    }
  }

  private static SignalServiceDataMessage.PaymentNotification createPaymentNotification(SignalServiceProtos.DataMessage.Payment content)
      throws InvalidMessageStructureException
  {
    if (!content.hasNotification() ||
        content.getNotification().getTransactionCase() != SignalServiceProtos.DataMessage.Payment.Notification.TransactionCase.MOBILECOIN)
    {
      throw new InvalidMessageStructureException("Badly-formatted payment notification!");
    }

    SignalServiceProtos.DataMessage.Payment.Notification payment = content.getNotification();

    return new SignalServiceDataMessage.PaymentNotification(payment.getMobileCoin().getReceipt().toByteArray(), payment.getNote());
  }

  private static SignalServiceDataMessage.PaymentActivation createPaymentActivation(SignalServiceProtos.DataMessage.Payment content)
      throws InvalidMessageStructureException
  {
    if (!content.hasActivation() ||
        content.getItemCase() != SignalServiceProtos.DataMessage.Payment.ItemCase.ACTIVATION)
    {
      throw new InvalidMessageStructureException("Badly-formatted payment activation!");
    }

    SignalServiceProtos.DataMessage.Payment.Activation payment = content.getActivation();

    return new SignalServiceDataMessage.PaymentActivation(payment.getType());
  }

  private static List<SharedContact> createSharedContacts(SignalServiceProtos.DataMessage content) throws InvalidMessageStructureException {
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

  private static SignalServiceAttachmentPointer createAttachmentPointer(SignalServiceProtos.AttachmentPointer pointer) throws InvalidMessageStructureException {
    return AttachmentPointerUtil.createSignalAttachmentPointer(pointer);
  }

  private static SignalServiceTextAttachment createTextAttachment(SignalServiceProtos.TextAttachment attachment) throws InvalidMessageStructureException {
    SignalServiceTextAttachment.Style style = null;
    if (attachment.hasTextStyle()) {
      switch (attachment.getTextStyle()) {
        case DEFAULT:
          style = SignalServiceTextAttachment.Style.DEFAULT;
          break;
        case REGULAR:
          style = SignalServiceTextAttachment.Style.REGULAR;
          break;
        case BOLD:
          style = SignalServiceTextAttachment.Style.BOLD;
          break;
        case SERIF:
          style = SignalServiceTextAttachment.Style.SERIF;
          break;
        case SCRIPT:
          style = SignalServiceTextAttachment.Style.SCRIPT;
          break;
        case CONDENSED:
          style = SignalServiceTextAttachment.Style.CONDENSED;
          break;
      }
    }

    Optional<String>               text                = Optional.ofNullable(attachment.hasText() ? attachment.getText() : null);
    Optional<Integer>              textForegroundColor = Optional.ofNullable(attachment.hasTextForegroundColor() ? attachment.getTextForegroundColor() : null);
    Optional<Integer>              textBackgroundColor = Optional.ofNullable(attachment.hasTextBackgroundColor() ? attachment.getTextBackgroundColor() : null);
    Optional<SignalServicePreview> preview             = Optional.ofNullable(attachment.hasPreview() ? createPreview(attachment.getPreview()) : null);

    if (attachment.hasGradient()) {
      SignalServiceProtos.TextAttachment.Gradient attachmentGradient = attachment.getGradient();

      Integer                              startColor         = attachmentGradient.hasStartColor() ? attachmentGradient.getStartColor() : null;
      Integer                              endColor           = attachmentGradient.hasEndColor() ? attachmentGradient.getEndColor() : null;
      Integer                              angle              = attachmentGradient.hasAngle() ? attachmentGradient.getAngle() : null;
      List<Integer>                        colors;
      List<Float>                          positions;

      if (attachmentGradient.getColorsCount() > 0 && attachmentGradient.getColorsCount() == attachmentGradient.getPositionsCount()) {
        colors = new ArrayList<>(attachmentGradient.getColorsList());
        positions = new ArrayList<>(attachmentGradient.getPositionsList());
      } else if (startColor != null && endColor != null) {
        colors = Arrays.asList(startColor, endColor);
        positions = Arrays.asList(0f, 1f);
      } else {
        colors = Collections.emptyList();
        positions = Collections.emptyList();
      }

      SignalServiceTextAttachment.Gradient gradient = new SignalServiceTextAttachment.Gradient(Optional.ofNullable(angle),
                                                                                               colors,
                                                                                               positions);

      return SignalServiceTextAttachment.forGradientBackground(text, Optional.ofNullable(style), textForegroundColor, textBackgroundColor, preview, gradient);
    } else {
      return SignalServiceTextAttachment.forSolidBackground(text, Optional.ofNullable(style), textForegroundColor, textBackgroundColor, preview, attachment.getColor());
    }
  }

  private static SignalServiceGroupV2 createGroupV2Info(SignalServiceProtos.StoryMessage storyMessage) throws InvalidMessageStructureException {
    if (!storyMessage.hasGroup()) {
      return null;
    }
    return createGroupV2Info(storyMessage.getGroup());
  }

  private static SignalServiceGroupV2 createGroupV2Info(SignalServiceProtos.DataMessage dataMessage) throws InvalidMessageStructureException {
    if (!dataMessage.hasGroupV2()) {
      return null;
    }
    return createGroupV2Info(dataMessage.getGroupV2());
  }

  private static SignalServiceGroupV2 createGroupV2Info(SignalServiceProtos.GroupContextV2 groupV2) throws InvalidMessageStructureException {
    if (groupV2 == null) {
      return null;
    }

    if (!groupV2.hasMasterKey()) {
      throw new InvalidMessageStructureException("No GV2 master key on message");
    }
    if (!groupV2.hasRevision()) {
      throw new InvalidMessageStructureException("No GV2 revision on message");
    }

    SignalServiceGroupV2.Builder builder;
    try {
      builder = SignalServiceGroupV2.newBuilder(new GroupMasterKey(groupV2.getMasterKey().toByteArray()))
                                    .withRevision(groupV2.getRevision());
    } catch (InvalidInputException e) {
      throw new InvalidMessageStructureException("Invalid GV2 input!");
    }

    if (groupV2.hasGroupChange() && !groupV2.getGroupChange().isEmpty()) {
      builder.withSignedGroupChange(groupV2.getGroupChange().toByteArray());
    }

    return builder.build();
  }
}

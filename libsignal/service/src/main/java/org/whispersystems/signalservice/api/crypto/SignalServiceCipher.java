/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.crypto;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidVersionException;
import org.signal.libsignal.metadata.ProtocolLegacyMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.signal.libsignal.metadata.SealedSessionCipher;
import org.signal.libsignal.metadata.SealedSessionCipher.DecryptionResult;
import org.signal.libsignal.metadata.SelfSendException;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Preview;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage.Sticker;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ConfigurationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage.VerifiedState;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.PushTransportDetails;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope.Type;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.ReceiptMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.SyncMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.TypingMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Verified;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.util.Base64;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.CallMessage;
import static org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext.Type.DELIVER;

/**
 * This is used to decrypt received {@link SignalServiceEnvelope}s.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceCipher {

  @SuppressWarnings("unused")
  private static final String TAG = SignalServiceCipher.class.getSimpleName();

  private final SignalProtocolStore  signalProtocolStore;
  private final SignalServiceAddress localAddress;
  private final CertificateValidator certificateValidator;

  public SignalServiceCipher(SignalServiceAddress localAddress,
                             SignalProtocolStore signalProtocolStore,
                             CertificateValidator certificateValidator)
  {
    this.signalProtocolStore  = signalProtocolStore;
    this.localAddress         = localAddress;
    this.certificateValidator = certificateValidator;
  }

  public OutgoingPushMessage encrypt(SignalProtocolAddress        destination,
                                     Optional<UnidentifiedAccess> unidentifiedAccess,
                                     byte[]                       unpaddedMessage)
      throws UntrustedIdentityException, InvalidKeyException
  {
    if (unidentifiedAccess.isPresent()) {
      SealedSessionCipher  sessionCipher        = new SealedSessionCipher(signalProtocolStore, localAddress.getUuid().orNull(), localAddress.getNumber().orNull(), 1);
      PushTransportDetails transportDetails     = new PushTransportDetails(sessionCipher.getSessionVersion(destination));
      byte[]               ciphertext           = sessionCipher.encrypt(destination, unidentifiedAccess.get().getUnidentifiedCertificate(), transportDetails.getPaddedMessageBody(unpaddedMessage));
      String               body                 = Base64.encodeBytes(ciphertext);
      int                  remoteRegistrationId = sessionCipher.getRemoteRegistrationId(destination);

      return new OutgoingPushMessage(Type.UNIDENTIFIED_SENDER_VALUE, destination.getDeviceId(), remoteRegistrationId, body);
    } else {
      SessionCipher        sessionCipher        = new SessionCipher(signalProtocolStore, destination);
      PushTransportDetails transportDetails     = new PushTransportDetails(sessionCipher.getSessionVersion());
      CiphertextMessage    message              = sessionCipher.encrypt(transportDetails.getPaddedMessageBody(unpaddedMessage));
      int                  remoteRegistrationId = sessionCipher.getRemoteRegistrationId();
      String               body                 = Base64.encodeBytes(message.serialize());

      int type;

      switch (message.getType()) {
        case CiphertextMessage.PREKEY_TYPE:  type = Type.PREKEY_BUNDLE_VALUE; break;
        case CiphertextMessage.WHISPER_TYPE: type = Type.CIPHERTEXT_VALUE;    break;
        default: throw new AssertionError("Bad type: " + message.getType());
      }

      return new OutgoingPushMessage(type, destination.getDeviceId(), remoteRegistrationId, body);
    }
  }

  /**
   * Decrypt a received {@link SignalServiceEnvelope}
   *
   * @param envelope The received SignalServiceEnvelope
   *
   * @return a decrypted SignalServiceContent
   */
  public SignalServiceContent decrypt(SignalServiceEnvelope envelope)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException,
      ProtocolInvalidKeyIdException, ProtocolLegacyMessageException,
      ProtocolUntrustedIdentityException, ProtocolNoSessionException,
      ProtocolInvalidVersionException, ProtocolInvalidMessageException,
      ProtocolInvalidKeyException, ProtocolDuplicateMessageException,
      SelfSendException, UnsupportedDataMessageException

  {
    try {
      if (envelope.hasLegacyMessage()) {
        Plaintext plaintext = decrypt(envelope, envelope.getLegacyMessage());
        DataMessage message = DataMessage.parseFrom(plaintext.getData());
        return new SignalServiceContent(createSignalServiceMessage(plaintext.getMetadata(), message),
                                        plaintext.getMetadata().getSender(),
                                        plaintext.getMetadata().getSenderDevice(),
                                        plaintext.getMetadata().getTimestamp(),
                                        plaintext.getMetadata().isNeedsReceipt());
      } else if (envelope.hasContent()) {
        Plaintext plaintext = decrypt(envelope, envelope.getContent());
        Content   message   = Content.parseFrom(plaintext.getData());

        if (message.hasDataMessage()) {
          return new SignalServiceContent(createSignalServiceMessage(plaintext.getMetadata(), message.getDataMessage()),
                                          plaintext.getMetadata().getSender(),
                                          plaintext.getMetadata().getSenderDevice(),
                                          plaintext.getMetadata().getTimestamp(),
                                          plaintext.getMetadata().isNeedsReceipt());
        } else if (message.hasSyncMessage() && localAddress.matches(plaintext.getMetadata().getSender())) {
          return new SignalServiceContent(createSynchronizeMessage(plaintext.getMetadata(), message.getSyncMessage()),
                                          plaintext.getMetadata().getSender(),
                                          plaintext.getMetadata().getSenderDevice(),
                                          plaintext.getMetadata().getTimestamp(),
                                          plaintext.getMetadata().isNeedsReceipt());
        } else if (message.hasCallMessage()) {
          return new SignalServiceContent(createCallMessage(message.getCallMessage()),
                                          plaintext.getMetadata().getSender(),
                                          plaintext.getMetadata().getSenderDevice(),
                                          plaintext.getMetadata().getTimestamp(),
                                          plaintext.getMetadata().isNeedsReceipt());
        } else if (message.hasReceiptMessage()) {
          return new SignalServiceContent(createReceiptMessage(plaintext.getMetadata(), message.getReceiptMessage()),
                                          plaintext.getMetadata().getSender(),
                                          plaintext.getMetadata().getSenderDevice(),
                                          plaintext.getMetadata().getTimestamp(),
                                          plaintext.getMetadata().isNeedsReceipt());
        } else if (message.hasTypingMessage()) {
          return new SignalServiceContent(createTypingMessage(plaintext.getMetadata(), message.getTypingMessage()),
                                          plaintext.getMetadata().getSender(),
                                          plaintext.getMetadata().getSenderDevice(),
                                          plaintext.getMetadata().getTimestamp(),
                                          false);
        }
      }

      return null;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMetadataMessageException(e);
    }
  }

  private Plaintext decrypt(SignalServiceEnvelope envelope, byte[] ciphertext)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException,
      ProtocolDuplicateMessageException, ProtocolUntrustedIdentityException,
      ProtocolLegacyMessageException, ProtocolInvalidKeyException,
      ProtocolInvalidVersionException, ProtocolInvalidMessageException,
      ProtocolInvalidKeyIdException, ProtocolNoSessionException,
      SelfSendException
  {
    try {

      byte[] paddedMessage;
      Metadata metadata;
      int sessionVersion;

      if (!envelope.hasSource() && !envelope.isUnidentifiedSender()) {
        throw new ProtocolInvalidMessageException(new InvalidMessageException("Non-UD envelope is missing a source!"), null, 0);
      }

      if (envelope.isPreKeySignalMessage()) {
        SignalProtocolAddress sourceAddress = getPreferredProtocolAddress(signalProtocolStore, envelope.getSourceAddress(), envelope.getSourceDevice());
        SessionCipher         sessionCipher = new SessionCipher(signalProtocolStore, sourceAddress);

        paddedMessage  = sessionCipher.decrypt(new PreKeySignalMessage(ciphertext));
        metadata       = new Metadata(envelope.getSourceAddress(), envelope.getSourceDevice(), envelope.getTimestamp(), false);
        sessionVersion = sessionCipher.getSessionVersion();
      } else if (envelope.isSignalMessage()) {
        SignalProtocolAddress sourceAddress = getPreferredProtocolAddress(signalProtocolStore, envelope.getSourceAddress(), envelope.getSourceDevice());
        SessionCipher         sessionCipher = new SessionCipher(signalProtocolStore, sourceAddress);

        paddedMessage  = sessionCipher.decrypt(new SignalMessage(ciphertext));
        metadata       = new Metadata(envelope.getSourceAddress(), envelope.getSourceDevice(), envelope.getTimestamp(), false);
        sessionVersion = sessionCipher.getSessionVersion();
      } else if (envelope.isUnidentifiedSender()) {
        SealedSessionCipher   sealedSessionCipher = new SealedSessionCipher(signalProtocolStore, localAddress.getUuid().orNull(), localAddress.getNumber().orNull(), 1);
        DecryptionResult      result              = sealedSessionCipher.decrypt(certificateValidator, ciphertext, envelope.getServerTimestamp());
        SignalServiceAddress  resultAddress       = new SignalServiceAddress(UuidUtil.parse(result.getSenderUuid().orNull()), result.getSenderE164());
        SignalProtocolAddress protocolAddress     = getPreferredProtocolAddress(signalProtocolStore, resultAddress, result.getDeviceId());

        paddedMessage  = result.getPaddedMessage();
        metadata       = new Metadata(resultAddress, result.getDeviceId(), envelope.getTimestamp(), true);
        sessionVersion = sealedSessionCipher.getSessionVersion(protocolAddress);
      } else {
        throw new InvalidMetadataMessageException("Unknown type: " + envelope.getType());
      }

      PushTransportDetails transportDetails = new PushTransportDetails(sessionVersion);
      byte[]               data             = transportDetails.getStrippedPaddingMessageBody(paddedMessage);

      return new Plaintext(metadata, data);
    } catch (DuplicateMessageException e) {
      throw new ProtocolDuplicateMessageException(e, envelope.getSourceIdentifier(), envelope.getSourceDevice());
    } catch (LegacyMessageException e) {
      throw new ProtocolLegacyMessageException(e, envelope.getSourceIdentifier(), envelope.getSourceDevice());
    } catch (InvalidMessageException e) {
      throw new ProtocolInvalidMessageException(e, envelope.getSourceIdentifier(), envelope.getSourceDevice());
    } catch (InvalidKeyIdException e) {
      throw new ProtocolInvalidKeyIdException(e, envelope.getSourceIdentifier(), envelope.getSourceDevice());
    } catch (InvalidKeyException e) {
      throw new ProtocolInvalidKeyException(e, envelope.getSourceIdentifier(), envelope.getSourceDevice());
    } catch (UntrustedIdentityException e) {
      throw new ProtocolUntrustedIdentityException(e, envelope.getSourceIdentifier(), envelope.getSourceDevice());
    } catch (InvalidVersionException e) {
      throw new ProtocolInvalidVersionException(e, envelope.getSourceIdentifier(), envelope.getSourceDevice());
    } catch (NoSessionException e) {
      throw new ProtocolNoSessionException(e, envelope.getSourceIdentifier(), envelope.getSourceDevice());
    }
  }

  private static SignalProtocolAddress getPreferredProtocolAddress(SignalProtocolStore store, SignalServiceAddress address, int sourceDevice) {
    SignalProtocolAddress uuidAddress = address.getUuid().isPresent() ? new SignalProtocolAddress(address.getUuid().get().toString(), sourceDevice) : null;
    SignalProtocolAddress e164Address = address.getNumber().isPresent() ? new SignalProtocolAddress(address.getNumber().get(), sourceDevice) : null;

    if (uuidAddress != null && store.containsSession(uuidAddress)) {
      return uuidAddress;
    } else if (e164Address != null && store.containsSession(e164Address)) {
      return e164Address;
    } else {
      return new SignalProtocolAddress(address.getIdentifier(), sourceDevice);
    }
  }

  private SignalServiceDataMessage createSignalServiceMessage(Metadata metadata, DataMessage content)
      throws ProtocolInvalidMessageException, UnsupportedDataMessageException
  {
    SignalServiceGroup             groupInfo        = createGroupInfo(content);
    List<SignalServiceAttachment>  attachments      = new LinkedList<>();
    boolean                        endSession       = ((content.getFlags() & DataMessage.Flags.END_SESSION_VALUE            ) != 0);
    boolean                        expirationUpdate = ((content.getFlags() & DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE) != 0);
    boolean                        profileKeyUpdate = ((content.getFlags() & DataMessage.Flags.PROFILE_KEY_UPDATE_VALUE     ) != 0);
    SignalServiceDataMessage.Quote quote            = createQuote(content);
    List<SharedContact>            sharedContacts   = createSharedContacts(content);
    List<Preview>                  previews         = createPreviews(content);
    Sticker                        sticker          = createSticker(content);

    if (content.getRequiredProtocolVersion() > DataMessage.ProtocolVersion.CURRENT.getNumber()) {
      throw new UnsupportedDataMessageException(DataMessage.ProtocolVersion.CURRENT.getNumber(),
                                                content.getRequiredProtocolVersion(),
                                                metadata.getSender().getIdentifier(),
                                                metadata.getSenderDevice(),
                                                Optional.fromNullable(groupInfo));
    }

    for (AttachmentPointer pointer : content.getAttachmentsList()) {
      attachments.add(createAttachmentPointer(pointer));
    }

    if (content.hasTimestamp() && content.getTimestamp() != metadata.getTimestamp()) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("Timestamps don't match: " + content.getTimestamp() + " vs " + metadata.getTimestamp()),
                                                                            metadata.getSender().getIdentifier(),
                                                                            metadata.getSenderDevice());
    }

    return new SignalServiceDataMessage(metadata.getTimestamp(),
                                        groupInfo,
                                        attachments,
                                        content.getBody(),
                                        endSession,
                                        content.getExpireTimer(),
                                        expirationUpdate,
                                        content.hasProfileKey() ? content.getProfileKey().toByteArray() : null,
                                        profileKeyUpdate,
                                        quote,
                                        sharedContacts,
                                        previews,
                                        sticker,
                                        content.getIsViewOnce());
  }

  private SignalServiceSyncMessage createSynchronizeMessage(Metadata metadata, SyncMessage content)
      throws ProtocolInvalidMessageException, ProtocolInvalidKeyException, UnsupportedDataMessageException
  {
    if (content.hasSent()) {
      SyncMessage.Sent                   sentContent          = content.getSent();
      SignalServiceDataMessage           dataMessage          = createSignalServiceMessage(metadata, sentContent.getMessage());
      Optional<SignalServiceAddress>     address              = SignalServiceAddress.isValidAddress(sentContent.getDestinationUuid(), sentContent.getDestinationE164())
                                                                  ? Optional.of(new SignalServiceAddress(UuidUtil.parseOrNull(sentContent.getDestinationUuid()), sentContent.getDestinationE164()))
                                                                  : Optional.<SignalServiceAddress>absent();
      Map<SignalServiceAddress, Boolean> unidentifiedStatuses = new HashMap<>();

      if (!address.isPresent() && !dataMessage.getGroupInfo().isPresent()) {
        throw new ProtocolInvalidMessageException(new InvalidMessageException("SyncMessage missing both destination and group ID!"), null, 0);
      }

      for (SyncMessage.Sent.UnidentifiedDeliveryStatus status : sentContent.getUnidentifiedStatusList()) {
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

      for (SyncMessage.Read read : content.getReadList()) {
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
        ViewOnceOpenMessage  timerRead = new ViewOnceOpenMessage(address, content.getViewOnceOpen().getTimestamp());
        return SignalServiceSyncMessage.forViewOnceOpen(timerRead);
      } else {
        throw new ProtocolInvalidMessageException(new InvalidMessageException("ViewOnceOpen message has no sender!"), null, 0);
      }
    }

    if (content.hasVerified()) {
      if (SignalServiceAddress.isValidAddress(content.getVerified().getDestinationUuid(), content.getVerified().getDestinationE164())) {
        try {
          Verified             verified    = content.getVerified();
          SignalServiceAddress destination = new SignalServiceAddress(UuidUtil.parseOrNull(verified.getDestinationUuid()), verified.getDestinationE164());
          IdentityKey          identityKey = new IdentityKey(verified.getIdentityKey().toByteArray(), 0);

          VerifiedState verifiedState;

          if (verified.getState() == Verified.State.DEFAULT) {
            verifiedState = VerifiedState.DEFAULT;
          } else if (verified.getState() == Verified.State.VERIFIED) {
            verifiedState = VerifiedState.VERIFIED;
          } else if (verified.getState() == Verified.State.UNVERIFIED) {
            verifiedState = VerifiedState.UNVERIFIED;
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

      for (SyncMessage.StickerPackOperation operation : content.getStickerPackOperationList()) {
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

    return SignalServiceSyncMessage.empty();
  }

  private SignalServiceCallMessage createCallMessage(CallMessage content) {
    if (content.hasOffer()) {
      CallMessage.Offer offerContent = content.getOffer();
      return SignalServiceCallMessage.forOffer(new OfferMessage(offerContent.getId(), offerContent.getDescription()));
    } else if (content.hasAnswer()) {
      CallMessage.Answer answerContent = content.getAnswer();
      return SignalServiceCallMessage.forAnswer(new AnswerMessage(answerContent.getId(), answerContent.getDescription()));
    } else if (content.getIceUpdateCount() > 0) {
      List<IceUpdateMessage> iceUpdates = new LinkedList<>();

      for (CallMessage.IceUpdate iceUpdate : content.getIceUpdateList()) {
        iceUpdates.add(new IceUpdateMessage(iceUpdate.getId(), iceUpdate.getSdpMid(), iceUpdate.getSdpMLineIndex(), iceUpdate.getSdp()));
      }

      return SignalServiceCallMessage.forIceUpdates(iceUpdates);
    } else if (content.hasHangup()) {
      CallMessage.Hangup hangup = content.getHangup();
      return SignalServiceCallMessage.forHangup(new HangupMessage(hangup.getId()));
    } else if (content.hasBusy()) {
      CallMessage.Busy busy = content.getBusy();
      return SignalServiceCallMessage.forBusy(new BusyMessage(busy.getId()));
    }

    return SignalServiceCallMessage.empty();
  }

  private SignalServiceReceiptMessage createReceiptMessage(Metadata metadata, ReceiptMessage content) {
    SignalServiceReceiptMessage.Type type;

    if      (content.getType() == ReceiptMessage.Type.DELIVERY) type = SignalServiceReceiptMessage.Type.DELIVERY;
    else if (content.getType() == ReceiptMessage.Type.READ)     type = SignalServiceReceiptMessage.Type.READ;
    else                                                        type = SignalServiceReceiptMessage.Type.UNKNOWN;

    return new SignalServiceReceiptMessage(type, content.getTimestampList(), metadata.getTimestamp());
  }

  private SignalServiceTypingMessage createTypingMessage(Metadata metadata, TypingMessage content) throws ProtocolInvalidMessageException {
    SignalServiceTypingMessage.Action action;

    if      (content.getAction() == TypingMessage.Action.STARTED) action = SignalServiceTypingMessage.Action.STARTED;
    else if (content.getAction() == TypingMessage.Action.STOPPED) action = SignalServiceTypingMessage.Action.STOPPED;
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

  private SignalServiceDataMessage.Quote createQuote(DataMessage content) {
    if (!content.hasQuote()) return null;

    List<SignalServiceDataMessage.Quote.QuotedAttachment> attachments = new LinkedList<>();

    for (DataMessage.Quote.QuotedAttachment attachment : content.getQuote().getAttachmentsList()) {
      attachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.getContentType(),
                                                                          attachment.getFileName(),
                                                                          attachment.hasThumbnail() ? createAttachmentPointer(attachment.getThumbnail()) : null));
    }

    if (SignalServiceAddress.isValidAddress(content.getQuote().getAuthorUuid(), content.getQuote().getAuthorE164())) {
      SignalServiceAddress address = new SignalServiceAddress(UuidUtil.parseOrNull(content.getQuote().getAuthorUuid()), content.getQuote().getAuthorE164());

      return new SignalServiceDataMessage.Quote(content.getQuote().getId(),
                                                address,
                                                content.getQuote().getText(),
                                                attachments);
    } else {
      Log.w(TAG, "Quote was missing an author! Returning null.");
      return null;
    }
  }

  private List<Preview> createPreviews(DataMessage content) {
    if (content.getPreviewCount() <= 0) return null;

    List<Preview> results = new LinkedList<>();

    for (DataMessage.Preview preview : content.getPreviewList()) {
      SignalServiceAttachment attachment = null;

      if (preview.hasImage()) {
        attachment = createAttachmentPointer(preview.getImage());
      }

      results.add(new Preview(preview.getUrl(),
                              preview.getTitle(),
                              Optional.fromNullable(attachment)));
    }

    return results;
  }

  private Sticker createSticker(DataMessage content) {
    if (!content.hasSticker()                ||
        !content.getSticker().hasPackId()    ||
        !content.getSticker().hasPackKey()   ||
        !content.getSticker().hasStickerId() ||
        !content.getSticker().hasData())
    {
      return null;
    }

    DataMessage.Sticker sticker = content.getSticker();

    return new Sticker(sticker.getPackId().toByteArray(),
                       sticker.getPackKey().toByteArray(),
                       sticker.getStickerId(),
                       createAttachmentPointer(sticker.getData()));
  }

  private List<SharedContact> createSharedContacts(DataMessage content) {
    if (content.getContactCount() <= 0) return null;

    List<SharedContact> results = new LinkedList<>();

    for (DataMessage.Contact contact : content.getContactList()) {
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
        for (DataMessage.Contact.PostalAddress address : contact.getAddressList()) {
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
        for (DataMessage.Contact.Phone phone : contact.getNumberList()) {
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
        for (DataMessage.Contact.Email email : contact.getEmailList()) {
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

  private SignalServiceAttachmentPointer createAttachmentPointer(AttachmentPointer pointer) {
    return new SignalServiceAttachmentPointer(pointer.getId(),
                                              pointer.getContentType(),
                                              pointer.getKey().toByteArray(),
                                              pointer.hasSize() ? Optional.of(pointer.getSize()) : Optional.<Integer>absent(),
                                              pointer.hasThumbnail() ? Optional.of(pointer.getThumbnail().toByteArray()): Optional.<byte[]>absent(),
                                              pointer.getWidth(), pointer.getHeight(),
                                              pointer.hasDigest() ? Optional.of(pointer.getDigest().toByteArray()) : Optional.<byte[]>absent(),
                                              pointer.hasFileName() ? Optional.of(pointer.getFileName()) : Optional.<String>absent(),
                                              (pointer.getFlags() & AttachmentPointer.Flags.VOICE_MESSAGE_VALUE) != 0,
                                              pointer.hasCaption() ? Optional.of(pointer.getCaption()) : Optional.<String>absent(),
                                              pointer.hasBlurHash() ? Optional.of(pointer.getBlurHash()) : Optional.<String>absent());

  }

  private SignalServiceGroup createGroupInfo(DataMessage content) throws ProtocolInvalidMessageException {
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
          if (SignalServiceAddress.isValidAddress(member.getUuid(), member.getE164())) {
            members.add(new SignalServiceAddress(UuidUtil.parseOrNull(member.getUuid()), member.getE164()));
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
        AttachmentPointer pointer = content.getGroup().getAvatar();

        avatar = new SignalServiceAttachmentPointer(pointer.getId(),
                                                    pointer.getContentType(),
                                                    pointer.getKey().toByteArray(),
                                                    Optional.of(pointer.getSize()),
                                                    Optional.<byte[]>absent(), 0, 0,
                                                    Optional.fromNullable(pointer.hasDigest() ? pointer.getDigest().toByteArray() : null),
                                                    Optional.<String>absent(),
                                                    false,
                                                    Optional.<String>absent(),
                                                    Optional.<String>absent());
      }

      return new SignalServiceGroup(type, content.getGroup().getId().toByteArray(), name, members, avatar);
    }

    return new SignalServiceGroup(content.getGroup().getId().toByteArray());
  }

  private static class Metadata {
    private final SignalServiceAddress sender;
    private final int                  senderDevice;
    private final long                 timestamp;
    private final boolean              needsReceipt;

    private Metadata(SignalServiceAddress sender, int senderDevice, long timestamp, boolean needsReceipt) {
      this.sender       = sender;
      this.senderDevice = senderDevice;
      this.timestamp    = timestamp;
      this.needsReceipt = needsReceipt;
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

    public boolean isNeedsReceipt() {
      return needsReceipt;
    }
  }

  private static class Plaintext {
    private final Metadata metadata;
    private final byte[]   data;

    private Plaintext(Metadata metadata, byte[] data) {
      this.metadata = metadata;
      this.data     = data;
    }

    public Metadata getMetadata() {
      return metadata;
    }

    public byte[] getData() {
      return data;
    }
  }

}


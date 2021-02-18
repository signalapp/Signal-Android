/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.service.api.crypto;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.session.libsignal.libsignal.ecc.ECKeyPair;
import org.session.libsignal.metadata.InvalidMetadataMessageException;
import org.session.libsignal.metadata.InvalidMetadataVersionException;
import org.session.libsignal.metadata.ProtocolDuplicateMessageException;
import org.session.libsignal.metadata.ProtocolInvalidKeyException;
import org.session.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.session.libsignal.metadata.ProtocolInvalidMessageException;
import org.session.libsignal.metadata.ProtocolInvalidVersionException;
import org.session.libsignal.metadata.ProtocolLegacyMessageException;
import org.session.libsignal.metadata.ProtocolNoSessionException;
import org.session.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.session.libsignal.metadata.SealedSessionCipher;
import org.session.libsignal.metadata.SelfSendException;
import org.session.libsignal.metadata.certificate.CertificateValidator;
import org.session.libsignal.libsignal.DuplicateMessageException;
import org.session.libsignal.libsignal.IdentityKey;
import org.session.libsignal.libsignal.InvalidKeyException;
import org.session.libsignal.libsignal.InvalidKeyIdException;
import org.session.libsignal.libsignal.InvalidMessageException;
import org.session.libsignal.libsignal.InvalidVersionException;
import org.session.libsignal.libsignal.LegacyMessageException;
import org.session.libsignal.libsignal.NoSessionException;
import org.session.libsignal.libsignal.SessionCipher;
import org.session.libsignal.libsignal.SignalProtocolAddress;
import org.session.libsignal.libsignal.UntrustedIdentityException;
import org.session.libsignal.libsignal.loki.LokiSessionCipher;
import org.session.libsignal.libsignal.loki.SessionResetProtocol;
import org.session.libsignal.libsignal.protocol.CiphertextMessage;
import org.session.libsignal.libsignal.protocol.PreKeySignalMessage;
import org.session.libsignal.libsignal.protocol.SignalMessage;
import org.session.libsignal.libsignal.state.SignalProtocolStore;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.messages.SignalServiceAttachment;
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer;
import org.session.libsignal.service.api.messages.SignalServiceAttachmentStream;
import org.session.libsignal.service.api.messages.SignalServiceContent;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage.Preview;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage.Sticker;
import org.session.libsignal.service.api.messages.SignalServiceEnvelope;
import org.session.libsignal.service.api.messages.SignalServiceGroup;
import org.session.libsignal.service.api.messages.SignalServiceNullMessage;
import org.session.libsignal.service.api.messages.SignalServiceReceiptMessage;
import org.session.libsignal.service.api.messages.SignalServiceTypingMessage;
import org.session.libsignal.service.api.messages.calls.AnswerMessage;
import org.session.libsignal.service.api.messages.calls.BusyMessage;
import org.session.libsignal.service.api.messages.calls.HangupMessage;
import org.session.libsignal.service.api.messages.calls.IceUpdateMessage;
import org.session.libsignal.service.api.messages.calls.OfferMessage;
import org.session.libsignal.service.api.messages.calls.SignalServiceCallMessage;
import org.session.libsignal.service.api.messages.multidevice.BlockedListMessage;
import org.session.libsignal.service.api.messages.multidevice.ContactsMessage;
import org.session.libsignal.service.api.messages.multidevice.ReadMessage;
import org.session.libsignal.service.api.messages.multidevice.RequestMessage;
import org.session.libsignal.service.api.messages.multidevice.SentTranscriptMessage;
import org.session.libsignal.service.api.messages.multidevice.SignalServiceSyncMessage;
import org.session.libsignal.service.api.messages.multidevice.StickerPackOperationMessage;
import org.session.libsignal.service.api.messages.multidevice.VerifiedMessage;
import org.session.libsignal.service.api.messages.multidevice.VerifiedMessage.VerifiedState;
import org.session.libsignal.service.api.messages.shared.SharedContact;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.internal.push.OutgoingPushMessage;
import org.session.libsignal.service.internal.push.PushTransportDetails;
import org.session.libsignal.service.internal.push.SignalServiceProtos;
import org.session.libsignal.service.internal.push.SignalServiceProtos.AttachmentPointer;
import org.session.libsignal.service.internal.push.SignalServiceProtos.ClosedGroupUpdate;
import org.session.libsignal.service.internal.push.SignalServiceProtos.ClosedGroupUpdateV2;
import org.session.libsignal.service.internal.push.SignalServiceProtos.Content;
import org.session.libsignal.service.internal.push.SignalServiceProtos.DataMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.Envelope.Type;
import org.session.libsignal.service.internal.push.SignalServiceProtos.ReceiptMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.SyncMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.TypingMessage;
import org.session.libsignal.service.internal.push.SignalServiceProtos.Verified;
import org.session.libsignal.utilities.Base64;
import org.session.libsignal.service.loki.api.crypto.SessionProtocol;
import org.session.libsignal.service.loki.api.crypto.SessionProtocolUtilities;
import org.session.libsignal.service.loki.api.opengroups.PublicChat;
import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol;
import org.session.libsignal.service.loki.protocol.sessionmanagement.PreKeyBundleMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.session.libsignal.service.internal.push.SignalServiceProtos.CallMessage;
import static org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext.Type.DELIVER;

/**
 * This is used to decrypt received {@link SignalServiceEnvelope}s.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceCipher {

  @SuppressWarnings("unused")
  private static final String TAG = SignalServiceCipher.class.getSimpleName();

  private final SignalProtocolStore              signalProtocolStore;
  private final SessionResetProtocol             sessionResetProtocol;
  private final SignalServiceAddress             localAddress;
  private final SessionProtocol                  sessionProtocolImpl;
  private final LokiAPIDatabaseProtocol          apiDB;
  private final CertificateValidator             certificateValidator;

  public SignalServiceCipher(SignalServiceAddress localAddress,
                             SignalProtocolStore signalProtocolStore,
                             SessionResetProtocol sessionResetProtocol,
                             SessionProtocol sessionProtocolImpl,
                             LokiAPIDatabaseProtocol apiDB,
                             CertificateValidator certificateValidator)
  {
    this.signalProtocolStore  = signalProtocolStore;
    this.sessionResetProtocol = sessionResetProtocol;
    this.localAddress         = localAddress;
    this.sessionProtocolImpl  = sessionProtocolImpl;
    this.apiDB                = apiDB;
    this.certificateValidator = certificateValidator;
  }

//  public OutgoingPushMessage encrypt(SignalProtocolAddress        destination,
//                                     Optional<UnidentifiedAccess> unidentifiedAccess,
//                                     byte[]                       unpaddedMessage)
//          throws UntrustedIdentityException, InvalidKeyException, IOException
//  {
//    if (unidentifiedAccess.isPresent() && sskDatabase.isSSKBasedClosedGroup(destination.getName())) {
//      String                userPublicKey         = localAddress.getNumber();
//      SignalProtocolAddress signalProtocolAddress = new SignalProtocolAddress(userPublicKey, 1);
//      SealedSessionCipher   sessionCipher         = new SealedSessionCipher(signalProtocolStore, sessionResetProtocol, signalProtocolAddress);
//      PushTransportDetails  transportDetails      = new PushTransportDetails(sessionCipher.getSessionVersion(destination));
//      byte[]                plaintext             = transportDetails.getPaddedMessageBody(unpaddedMessage);
//      byte[]                ciphertext            = ClosedGroupUtilities.encrypt(plaintext, destination.getName(), userPublicKey);
//      String                body                  = Base64.encodeBytes(ciphertext);
//      int                   remoteRegistrationId  = sessionCipher.getRemoteRegistrationId(destination);
//      return new OutgoingPushMessage(Type.CLOSED_GROUP_CIPHERTEXT_VALUE, destination.getDeviceId(), remoteRegistrationId, body);
//    } else if (unidentifiedAccess.isPresent()) {
//      SealedSessionCipher  sessionCipher        = new SealedSessionCipher(signalProtocolStore, sessionResetProtocol, new SignalProtocolAddress(localAddress.getNumber(), 1));
//      PushTransportDetails transportDetails     = new PushTransportDetails(sessionCipher.getSessionVersion(destination));
//      byte[]               ciphertext           = sessionCipher.encrypt(destination, unidentifiedAccess.get().getUnidentifiedCertificate(), transportDetails.getPaddedMessageBody(unpaddedMessage));
//      String               body                 = Base64.encodeBytes(ciphertext);
//      int                  remoteRegistrationId = sessionCipher.getRemoteRegistrationId(destination);
//
//      return new OutgoingPushMessage(Type.UNIDENTIFIED_SENDER_VALUE, destination.getDeviceId(), remoteRegistrationId, body);
//    } else {
//      SessionCipher        sessionCipher        = new SessionCipher(signalProtocolStore, destination);
//      PushTransportDetails transportDetails     = new PushTransportDetails(sessionCipher.getSessionVersion());
//      CiphertextMessage    message              = sessionCipher.encrypt(transportDetails.getPaddedMessageBody(unpaddedMessage));
//      int                  remoteRegistrationId = sessionCipher.getRemoteRegistrationId();
//      String               body                 = Base64.encodeBytes(message.serialize());
//
//      int type;
//
//      switch (message.getType()) {
//        case CiphertextMessage.PREKEY_TYPE:             type = Type.PREKEY_BUNDLE_VALUE;    break;
//        case CiphertextMessage.WHISPER_TYPE:            type = Type.CIPHERTEXT_VALUE;       break;
//        case CiphertextMessage.FALLBACK_MESSAGE_TYPE:   type = Type.FALLBACK_MESSAGE_VALUE; break;
//        case CiphertextMessage.CLOSED_GROUP_CIPHERTEXT: type = Type.CLOSED_GROUP_CIPHERTEXT_VALUE; break;
//        default: throw new AssertionError("Bad type: " + message.getType());
//      }
//
//      return new OutgoingPushMessage(type, destination.getDeviceId(), remoteRegistrationId, body);
//    }
//  }

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
          SelfSendException, IOException, SessionProtocol.Exception

  {
    try {
      Plaintext plaintext = decrypt(envelope, envelope.getContent());
      Content   message   = Content.parseFrom(plaintext.getData());

      PreKeyBundleMessage preKeyBundleMessage = null;
      if (message.hasPreKeyBundleMessage()) {
        SignalServiceProtos.PreKeyBundleMessage protoPreKeyBundleMessage = message.getPreKeyBundleMessage();
        preKeyBundleMessage = new PreKeyBundleMessage(protoPreKeyBundleMessage.getIdentityKey().toByteArray(),
                protoPreKeyBundleMessage.getDeviceId(),
                protoPreKeyBundleMessage.getPreKeyId(),
                protoPreKeyBundleMessage.getSignedKeyId(),
                protoPreKeyBundleMessage.getPreKey().toByteArray(),
                protoPreKeyBundleMessage.getSignedKey().toByteArray(),
                protoPreKeyBundleMessage.getSignature().toByteArray()
        );
      }

      if (message.hasConfigurationMessage()) {
        SignalServiceCipher.Metadata metadata = plaintext.getMetadata();
        SignalServiceContent content = new SignalServiceContent(message, metadata.getSender(), metadata.getSenderDevice(), metadata.getTimestamp());

        if (message.hasDataMessage()) {
          setProfile(message.getDataMessage(), content);
          SignalServiceDataMessage signalServiceDataMessage = createSignalServiceMessage(metadata, message.getDataMessage());
          content.setDataMessage(signalServiceDataMessage);
        }

        return content;
      } else if (message.hasDataMessage()) {
        DataMessage dataMessage = message.getDataMessage();

        SignalServiceDataMessage signalServiceDataMessage = createSignalServiceMessage(plaintext.getMetadata(), dataMessage);
        SignalServiceContent content = new SignalServiceContent(signalServiceDataMessage,
                plaintext.getMetadata().getSender(),
                plaintext.getMetadata().getSenderDevice(),
                plaintext.getMetadata().getTimestamp(),
                plaintext.getMetadata().isNeedsReceipt());

        content.setPreKeyBundleMessage(preKeyBundleMessage);

        setProfile(dataMessage, content);

        return content;
      } else if (message.hasSyncMessage()) {
        SignalServiceContent content = new SignalServiceContent(createSynchronizeMessage(
                plaintext.getMetadata(),
                message.getSyncMessage()),
                plaintext.getMetadata().getSender(),
                plaintext.getMetadata().getSenderDevice(),
                plaintext.getMetadata().getTimestamp());

        if (message.getSyncMessage().hasSent() && message.getSyncMessage().getSent().hasMessage()) {
          DataMessage dataMessage = message.getSyncMessage().getSent().getMessage();
          setProfile(dataMessage, content);
        }

        return content;
      } else if (message.hasCallMessage()) {
        return new SignalServiceContent(createCallMessage(message.getCallMessage()),
                plaintext.getMetadata().getSender(),
                plaintext.getMetadata().getSenderDevice(),
                plaintext.getMetadata().getTimestamp());
      } else if (message.hasReceiptMessage()) {
        return new SignalServiceContent(createReceiptMessage(plaintext.getMetadata(), message.getReceiptMessage()),
                plaintext.getMetadata().getSender(),
                plaintext.getMetadata().getSenderDevice(),
                plaintext.getMetadata().getTimestamp());
      } else if (message.hasTypingMessage()) {
        return new SignalServiceContent(createTypingMessage(plaintext.getMetadata(), message.getTypingMessage()),
                plaintext.getMetadata().getSender(),
                plaintext.getMetadata().getSenderDevice(),
                plaintext.getMetadata().getTimestamp());
      } else if (message.hasNullMessage()) {
        SignalServiceContent content = new SignalServiceContent(new SignalServiceNullMessage(),
                plaintext.getMetadata().getSender(),
                plaintext.getMetadata().getSenderDevice(),
                plaintext.getMetadata().getTimestamp());

        content.setPreKeyBundleMessage(preKeyBundleMessage);

        return content;
      }

      return null;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMetadataMessageException(e);
    }
  }

  private void setProfile(DataMessage message, SignalServiceContent content) {
    if (!message.hasProfile()) { return; }
    SignalServiceProtos.LokiUserProfile profile = message.getProfile();
    if (profile.hasDisplayName()) { content.setSenderDisplayName(profile.getDisplayName()); }
    if (profile.hasProfilePictureURL()) { content.setSenderProfilePictureURL(profile.getProfilePictureURL()); }
  }

  protected Plaintext decrypt(SignalServiceEnvelope envelope, byte[] ciphertext)
          throws InvalidMetadataMessageException, InvalidMetadataVersionException,
          ProtocolDuplicateMessageException, ProtocolUntrustedIdentityException,
          ProtocolLegacyMessageException, ProtocolInvalidKeyException,
          ProtocolInvalidVersionException, ProtocolInvalidMessageException,
          ProtocolInvalidKeyIdException, ProtocolNoSessionException,
          SelfSendException, IOException, SessionProtocol.Exception
  {
    try {
      SignalProtocolAddress sourceAddress       = new SignalProtocolAddress(envelope.getSource(), envelope.getSourceDevice());
      SessionCipher         sessionCipher       = new LokiSessionCipher(signalProtocolStore, sessionResetProtocol, sourceAddress);
      SealedSessionCipher   sealedSessionCipher = new SealedSessionCipher(signalProtocolStore, sessionResetProtocol, new SignalProtocolAddress(localAddress.getNumber(), 1));

      byte[] paddedMessage;
      Metadata metadata;
      int sessionVersion;

      if (envelope.isClosedGroupCiphertext()) {
        String groupPublicKey = envelope.getSource();
        kotlin.Pair<byte[], String> plaintextAndSenderPublicKey = SessionProtocolUtilities.INSTANCE.decryptClosedGroupCiphertext(ciphertext, groupPublicKey, apiDB, sessionProtocolImpl);
        paddedMessage = plaintextAndSenderPublicKey.getFirst();
        String senderPublicKey = plaintextAndSenderPublicKey.getSecond();
        metadata = new Metadata(senderPublicKey, 1, envelope.getTimestamp(), false);
        sessionVersion = sessionCipher.getSessionVersion();
      } else if (envelope.isPreKeySignalMessage()) {
        paddedMessage  = sessionCipher.decrypt(new PreKeySignalMessage(ciphertext));
        metadata       = new Metadata(envelope.getSource(), envelope.getSourceDevice(), envelope.getTimestamp(), false);
        sessionVersion = sessionCipher.getSessionVersion();
      } else if (envelope.isSignalMessage()) {
        paddedMessage  = sessionCipher.decrypt(new SignalMessage(ciphertext));
        metadata       = new Metadata(envelope.getSource(), envelope.getSourceDevice(), envelope.getTimestamp(), false);
        sessionVersion = sessionCipher.getSessionVersion();
      } else if (envelope.isUnidentifiedSender()) {
        ECKeyPair userX25519KeyPair = apiDB.getUserX25519KeyPair();
        kotlin.Pair<byte[], String> plaintextAndSenderPublicKey = sessionProtocolImpl.decrypt(ciphertext, userX25519KeyPair);
        paddedMessage = plaintextAndSenderPublicKey.getFirst();
        String senderPublicKey = plaintextAndSenderPublicKey.getSecond();
        metadata = new Metadata(senderPublicKey, 1, envelope.getTimestamp(), false);
        sessionVersion = sealedSessionCipher.getSessionVersion(new SignalProtocolAddress(metadata.getSender(), metadata.getSenderDevice()));
      } else {
        throw new InvalidMetadataMessageException("Unknown type: " + envelope.getType());
      }

      PushTransportDetails transportDetails = new PushTransportDetails(sessionVersion);
      byte[]               data             = transportDetails.getStrippedPaddingMessageBody(paddedMessage);

      return new Plaintext(metadata, data);
    } catch (DuplicateMessageException e) {
      throw new ProtocolDuplicateMessageException(e, envelope.getSource(), envelope.getSourceDevice());
    } catch (LegacyMessageException e) {
      throw new ProtocolLegacyMessageException(e, envelope.getSource(), envelope.getSourceDevice());
    } catch (InvalidMessageException e) {
      throw new ProtocolInvalidMessageException(e, envelope.getSource(), envelope.getSourceDevice());
    } catch (InvalidKeyIdException e) {
      throw new ProtocolInvalidKeyIdException(e, envelope.getSource(), envelope.getSourceDevice());
    } catch (InvalidKeyException e) {
      throw new ProtocolInvalidKeyException(e, envelope.getSource(), envelope.getSourceDevice());
    } catch (UntrustedIdentityException e) {
      throw new ProtocolUntrustedIdentityException(e, envelope.getSource(), envelope.getSourceDevice());
    } catch (InvalidVersionException e) {
      throw new ProtocolInvalidVersionException(e, envelope.getSource(), envelope.getSourceDevice());
    } catch (NoSessionException e) {
      throw new ProtocolNoSessionException(e, envelope.getSource(), envelope.getSourceDevice());
    }
  }

  private SignalServiceDataMessage createSignalServiceMessage(Metadata metadata, DataMessage content) throws ProtocolInvalidMessageException {
    SignalServiceGroup             groupInfo                   = createGroupInfo(content);
    List<SignalServiceAttachment>  attachments                 = new LinkedList<SignalServiceAttachment>();
    boolean                        endSession                  = ((content.getFlags() & DataMessage.Flags.END_SESSION_VALUE            ) != 0);
    boolean                        expirationUpdate            = ((content.getFlags() & DataMessage.Flags.EXPIRATION_TIMER_UPDATE_VALUE) != 0);
    boolean                        profileKeyUpdate            = ((content.getFlags() & DataMessage.Flags.PROFILE_KEY_UPDATE_VALUE     ) != 0);
    SignalServiceDataMessage.Quote quote                       = createQuote(content);
    List<SharedContact>            sharedContacts              = createSharedContacts(content);
    List<Preview>                  previews                    = createPreviews(content);
    Sticker                        sticker                     = createSticker(content);
    ClosedGroupUpdate              closedGroupUpdate           = content.getClosedGroupUpdate();
    ClosedGroupUpdateV2            closedGroupUpdateV2         = content.getClosedGroupUpdateV2();
    boolean                        isDeviceUnlinkingRequest    = ((content.getFlags() & DataMessage.Flags.DEVICE_UNLINKING_REQUEST_VALUE) != 0);
    String                         syncTarget                  = content.getSyncTarget();

    for (AttachmentPointer pointer : content.getAttachmentsList()) {
      attachments.add(createAttachmentPointer(pointer));
    }

    if (content.hasTimestamp() && content.getTimestamp() != metadata.getTimestamp()) {
      throw new ProtocolInvalidMessageException(new InvalidMessageException("Timestamps don't match: " + content.getTimestamp() + " vs " + metadata.getTimestamp()),
              metadata.getSender(),
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
            null,
            closedGroupUpdate,
            closedGroupUpdateV2,
            syncTarget);
  }

  private SignalServiceSyncMessage createSynchronizeMessage(Metadata metadata, SyncMessage content)
          throws ProtocolInvalidMessageException, ProtocolInvalidKeyException
  {
    if (content.hasSent()) {
      SyncMessage.Sent     sentContent          = content.getSent();
      Map<String, Boolean> unidentifiedStatuses = new HashMap<String, Boolean>();

      for (SyncMessage.Sent.UnidentifiedDeliveryStatus status : sentContent.getUnidentifiedStatusList()) {
        unidentifiedStatuses.put(status.getDestination(), status.getUnidentified());
      }

      return SignalServiceSyncMessage.forSentTranscript(new SentTranscriptMessage(sentContent.getDestination(),
              sentContent.getTimestamp(),
              createSignalServiceMessage(metadata, sentContent.getMessage()),
              sentContent.getExpirationStartTimestamp(),
              unidentifiedStatuses));
    }

    if (content.hasRequest()) {
      return SignalServiceSyncMessage.forRequest(new RequestMessage(content.getRequest()));
    }

    if (content.getReadList().size() > 0) {
      List<ReadMessage> readMessages = new LinkedList<ReadMessage>();

      for (SyncMessage.Read read : content.getReadList()) {
        readMessages.add(new ReadMessage(read.getSender(), read.getTimestamp()));
      }

      return SignalServiceSyncMessage.forRead(readMessages);
    }

    if (content.hasContacts()) {
      SyncMessage.Contacts contacts = content.getContacts();
      ByteString data = contacts.getData();
      if (data != null && !data.isEmpty()) {
        byte[] bytes = data.toByteArray();
        SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                .withStream(new ByteArrayInputStream(data.toByteArray()))
                .withContentType("application/octet-stream")
                .withLength(bytes.length)
                .build();
        return SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, contacts.getComplete()));
      }
    }

    if (content.hasGroups()) {
      SyncMessage.Groups groups = content.getGroups();
      ByteString data = groups.getData();
      if (data != null && !data.isEmpty()) {
        byte[] bytes = data.toByteArray();
        SignalServiceAttachmentStream attachmentStream   = SignalServiceAttachment.newStreamBuilder()
                .withStream(new ByteArrayInputStream(data.toByteArray()))
                .withContentType("application/octet-stream")
                .withLength(bytes.length)
                .build();
        return SignalServiceSyncMessage.forGroups(attachmentStream);
      }
    }

    if (content.hasVerified()) {
      try {
        Verified    verified    = content.getVerified();
        String      destination = verified.getDestination();
        IdentityKey identityKey = new IdentityKey(verified.getIdentityKey().toByteArray(), 0);

        VerifiedState verifiedState;

        if (verified.getState() == Verified.State.DEFAULT) {
          verifiedState = VerifiedState.DEFAULT;
        } else if (verified.getState() == Verified.State.VERIFIED) {
          verifiedState = VerifiedState.VERIFIED;
        } else if (verified.getState() == Verified.State.UNVERIFIED) {
          verifiedState = VerifiedState.UNVERIFIED;
        } else {
          throw new ProtocolInvalidMessageException(new InvalidMessageException("Unknown state: " + verified.getState().getNumber()),
                  metadata.getSender(), metadata.getSenderDevice());
        }

        return SignalServiceSyncMessage.forVerified(new VerifiedMessage(destination, identityKey, verifiedState, System.currentTimeMillis()));
      } catch (InvalidKeyException e) {
        throw new ProtocolInvalidKeyException(e, metadata.getSender(), metadata.getSenderDevice());
      }
    }

    if (content.getStickerPackOperationList().size() > 0) {
      List<StickerPackOperationMessage> operations = new LinkedList<StickerPackOperationMessage>();

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

    List<SyncMessage.OpenGroupDetails> openGroupDetails = content.getOpenGroupsList();
    if (openGroupDetails.size() > 0) {
      List<PublicChat> openGroups = new LinkedList<>();
      for (SyncMessage.OpenGroupDetails details : content.getOpenGroupsList()) {
        openGroups.add(new PublicChat(details.getChannelID(), details.getUrl(), "", true));
      }
      return SignalServiceSyncMessage.forOpenGroups(openGroups);
    }

    if (content.hasBlocked()) {
      SyncMessage.Blocked blocked = content.getBlocked();
      List<String> publicKeys = blocked.getNumbersList();
      return SignalServiceSyncMessage.forBlocked(new BlockedListMessage(publicKeys, new ArrayList<byte[]>()));
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
      List<IceUpdateMessage> iceUpdates = new LinkedList<IceUpdateMessage>();

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
              metadata.getSender(),
              metadata.getSenderDevice());
    }

    return new SignalServiceTypingMessage(action, content.getTimestamp(),
            content.hasGroupId() ? Optional.of(content.getGroupId().toByteArray()) :
                    Optional.<byte[]>absent());
  }

  private SignalServiceDataMessage.Quote createQuote(DataMessage content) {
    if (!content.hasQuote()) return null;

    List<SignalServiceDataMessage.Quote.QuotedAttachment> attachments = new LinkedList<SignalServiceDataMessage.Quote.QuotedAttachment>();

    for (DataMessage.Quote.QuotedAttachment attachment : content.getQuote().getAttachmentsList()) {
      attachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.getContentType(),
              attachment.getFileName(),
              attachment.hasThumbnail() ? createAttachmentPointer(attachment.getThumbnail()) : null));
    }

    return new SignalServiceDataMessage.Quote(content.getQuote().getId(),
            new SignalServiceAddress(content.getQuote().getAuthor()),
            content.getQuote().getText(),
            attachments);
  }

  private List<Preview> createPreviews(DataMessage content) {
    if (content.getPreviewCount() <= 0) return null;

    List<Preview> results = new LinkedList<Preview>();

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

    List<SharedContact> results = new LinkedList<SharedContact>();

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
            pointer.getUrl());

  }

  private SignalServiceGroup createGroupInfo(DataMessage content) {
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
      String                      name    = null;
      List<String>                members = null;
      SignalServiceAttachmentPointer avatar  = null;
      List<String> admins = null;

      if (content.getGroup().hasName()) {
        name = content.getGroup().getName();
      }

      if (content.getGroup().getMembersCount() > 0) {
        members = content.getGroup().getMembersList();
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
                pointer.getUrl());
      }

      if (content.getGroup().getAdminsCount() > 0) {
        admins = content.getGroup().getAdminsList();
      }

      return new SignalServiceGroup(type, content.getGroup().getId().toByteArray(), SignalServiceGroup.GroupType.SIGNAL, name, members, avatar, admins);
    }

    return new SignalServiceGroup(content.getGroup().getId().toByteArray(), SignalServiceGroup.GroupType.SIGNAL);
  }

  protected static class Metadata {
    private final String  sender;
    private final int     senderDevice;
    private final long    timestamp;
    private final boolean needsReceipt;

    public Metadata(String sender, int senderDevice, long timestamp, boolean needsReceipt) {
      this.sender            = sender;
      this.senderDevice      = senderDevice;
      this.timestamp         = timestamp;
      this.needsReceipt      = needsReceipt;
    }

    public String getSender() {
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

  protected static class Plaintext {
    private final Metadata metadata;
    private final byte[]   data;

    public Plaintext(Metadata metadata, byte[] data) {
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

/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.crypto;

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
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.signal.libsignal.metadata.protocol.UnidentifiedSenderMessageContent;
import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.InvalidRegistrationIdException;
import org.signal.libsignal.protocol.InvalidVersionException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SessionCipher;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.UntrustedIdentityException;
import org.signal.libsignal.protocol.groups.GroupCipher;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PlaintextContent;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.message.SignalMessage;
import org.whispersystems.signalservice.api.InvalidMessageStructureException;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceMetadata;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.PushTransportDetails;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.SignalServiceMetadataProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * This is used to encrypt + decrypt received {@link SignalServiceEnvelope}s.
 */
public class SignalServiceCipher {

  @SuppressWarnings("unused")
  private static final String TAG = SignalServiceCipher.class.getSimpleName();

  private final SignalServiceAccountDataStore signalProtocolStore;
  private final SignalSessionLock             sessionLock;
  private final SignalServiceAddress localAddress;
  private final int                  localDeviceId;
  private final CertificateValidator certificateValidator;

  public SignalServiceCipher(SignalServiceAddress localAddress,
                             int localDeviceId,
                             SignalServiceAccountDataStore signalProtocolStore,
                             SignalSessionLock sessionLock,
                             CertificateValidator certificateValidator)
  {
    this.signalProtocolStore  = signalProtocolStore;
    this.sessionLock          = sessionLock;
    this.localAddress         = localAddress;
    this.localDeviceId        = localDeviceId;
    this.certificateValidator = certificateValidator;
  }

  public byte[] encryptForGroup(DistributionId distributionId,
                                List<SignalProtocolAddress> destinations,
                                SenderCertificate senderCertificate,
                                byte[] unpaddedMessage,
                                ContentHint contentHint,
                                Optional<byte[]> groupId)
      throws NoSessionException, UntrustedIdentityException, InvalidKeyException, InvalidRegistrationIdException
  {
    PushTransportDetails             transport            = new PushTransportDetails();
    SignalProtocolAddress            localProtocolAddress = new SignalProtocolAddress(localAddress.getIdentifier(), localDeviceId);
    SignalGroupCipher                groupCipher          = new SignalGroupCipher(sessionLock, new GroupCipher(signalProtocolStore, localProtocolAddress));
    SignalSealedSessionCipher        sessionCipher        = new SignalSealedSessionCipher(sessionLock, new SealedSessionCipher(signalProtocolStore, localAddress.getServiceId().uuid(), localAddress.getNumber().orElse(null), localDeviceId));
    CiphertextMessage                message              = groupCipher.encrypt(distributionId.asUuid(), transport.getPaddedMessageBody(unpaddedMessage));
    UnidentifiedSenderMessageContent messageContent       = new UnidentifiedSenderMessageContent(message,
                                                                                                 senderCertificate,
                                                                                                 contentHint.getType(),
                                                                                                 groupId);

    return sessionCipher.multiRecipientEncrypt(destinations, messageContent);
  }

  public OutgoingPushMessage encrypt(SignalProtocolAddress        destination,
                                     Optional<UnidentifiedAccess> unidentifiedAccess,
                                     EnvelopeContent              content)
      throws UntrustedIdentityException, InvalidKeyException
  {
    if (unidentifiedAccess.isPresent()) {
      SignalSessionCipher       sessionCipher        = new SignalSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, destination));
      SignalSealedSessionCipher sealedSessionCipher  = new SignalSealedSessionCipher(sessionLock, new SealedSessionCipher(signalProtocolStore, localAddress.getServiceId().uuid(), localAddress.getNumber().orElse(null), localDeviceId));

      return content.processSealedSender(sessionCipher, sealedSessionCipher, destination, unidentifiedAccess.get().getUnidentifiedCertificate());
    } else {
      SignalSessionCipher sessionCipher = new SignalSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, destination));

      return content.processUnsealedSender(sessionCipher, destination);
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
      SelfSendException, UnsupportedDataMessageException, InvalidMessageStructureException
  {
    try {
      if (envelope.hasContent()) {
        Plaintext                   plaintext = decryptInternal(envelope.getProto(), envelope.getServerDeliveredTimestamp());
        SignalServiceProtos.Content content   = SignalServiceProtos.Content.parseFrom(plaintext.getData());

        SignalServiceContentProto contentProto = SignalServiceContentProto.newBuilder()
                                                                          .setLocalAddress(SignalServiceAddressProtobufSerializer.toProtobuf(localAddress))
                                                                          .setMetadata(SignalServiceMetadataProtobufSerializer.toProtobuf(plaintext.metadata))
                                                                          .setContent(content)
                                                                          .build();

        return SignalServiceContent.createFromProto(contentProto);
      }

      return null;
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMetadataMessageException(e);
    }
  }

  public SignalServiceCipherResult decrypt(Envelope envelope, long serverDeliveredTimestamp)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException,
             ProtocolInvalidKeyIdException, ProtocolLegacyMessageException,
             ProtocolUntrustedIdentityException, ProtocolNoSessionException,
             ProtocolInvalidVersionException, ProtocolInvalidMessageException,
             ProtocolInvalidKeyException, ProtocolDuplicateMessageException,
             SelfSendException, InvalidMessageStructureException
  {
    try {
      if (envelope.hasContent()) {
        Plaintext                   plaintext = decryptInternal(envelope, serverDeliveredTimestamp);
        SignalServiceProtos.Content content   = SignalServiceProtos.Content.parseFrom(plaintext.getData());

        return new SignalServiceCipherResult(
            content,
            new EnvelopeMetadata(
                plaintext.metadata.getSender().getServiceId(),
                plaintext.metadata.getSender().getNumber().orElse(null),
                plaintext.metadata.getSenderDevice(),
                plaintext.metadata.isNeedsReceipt(),
                plaintext.metadata.getGroupId().orElse(null),
                localAddress.getServiceId()
            )
        );
      } else {
        return null;
      }
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidMetadataMessageException(e);
    }
  }

  private Plaintext decryptInternal(Envelope envelope, long serverDeliveredTimestamp)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException,
      ProtocolDuplicateMessageException, ProtocolUntrustedIdentityException,
      ProtocolLegacyMessageException, ProtocolInvalidKeyException,
      ProtocolInvalidVersionException, ProtocolInvalidMessageException,
      ProtocolInvalidKeyIdException, ProtocolNoSessionException,
      SelfSendException, InvalidMessageStructureException
  {
    try {

      byte[]                paddedMessage;
      SignalServiceMetadata metadata;

      if (!envelope.hasSourceUuid() && envelope.getType().getNumber() != Envelope.Type.UNIDENTIFIED_SENDER_VALUE) {
        throw new InvalidMessageStructureException("Non-UD envelope is missing a UUID!");
      }

      if (envelope.getType().getNumber() == Envelope.Type.PREKEY_BUNDLE_VALUE) {
        SignalProtocolAddress sourceAddress = new SignalProtocolAddress(envelope.getSourceUuid(), envelope.getSourceDevice());
        SignalSessionCipher   sessionCipher = new SignalSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, sourceAddress));

        paddedMessage = sessionCipher.decrypt(new PreKeySignalMessage(envelope.getContent().toByteArray()));
        metadata      = new SignalServiceMetadata(getSourceAddress(envelope), envelope.getSourceDevice(), envelope.getTimestamp(), envelope.getServerTimestamp(), serverDeliveredTimestamp, false, envelope.getServerGuid(), Optional.empty(), envelope.getDestinationUuid());

        signalProtocolStore.clearSenderKeySharedWith(Collections.singleton(sourceAddress));
      } else if (envelope.getType().getNumber() == Envelope.Type.CIPHERTEXT_VALUE) {
        SignalProtocolAddress sourceAddress = new SignalProtocolAddress(envelope.getSourceUuid(), envelope.getSourceDevice());
        SignalSessionCipher   sessionCipher = new SignalSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, sourceAddress));

        paddedMessage = sessionCipher.decrypt(new SignalMessage(envelope.getContent().toByteArray()));
        metadata      = new SignalServiceMetadata(getSourceAddress(envelope), envelope.getSourceDevice(), envelope.getTimestamp(), envelope.getServerTimestamp(), serverDeliveredTimestamp, false, envelope.getServerGuid(), Optional.empty(), envelope.getDestinationUuid());
      } else if (envelope.getType().getNumber() == Envelope.Type.PLAINTEXT_CONTENT_VALUE) {
        paddedMessage = new PlaintextContent(envelope.getContent().toByteArray()).getBody();
        metadata      = new SignalServiceMetadata(getSourceAddress(envelope), envelope.getSourceDevice(), envelope.getTimestamp(), envelope.getServerTimestamp(), serverDeliveredTimestamp, false, envelope.getServerGuid(), Optional.empty(), envelope.getDestinationUuid());
      } else if (envelope.getType().getNumber() == Envelope.Type.UNIDENTIFIED_SENDER_VALUE) {
        SignalSealedSessionCipher sealedSessionCipher = new SignalSealedSessionCipher(sessionLock, new SealedSessionCipher(signalProtocolStore, localAddress.getServiceId().uuid(), localAddress.getNumber().orElse(null), localDeviceId));
        DecryptionResult          result              = sealedSessionCipher.decrypt(certificateValidator, envelope.getContent().toByteArray(), envelope.getServerTimestamp());
        SignalServiceAddress      resultAddress       = new SignalServiceAddress(ACI.parseOrThrow(result.getSenderUuid()), result.getSenderE164());
        Optional<byte[]>          groupId             = result.getGroupId();
        boolean                   needsReceipt        = true;

        if (envelope.hasSourceUuid()) {
          Log.w(TAG, "[" + envelope.getTimestamp() + "] Received a UD-encrypted message sent over an identified channel. Marking as needsReceipt=false");
          needsReceipt = false;
        }

        if (result.getCiphertextMessageType() == CiphertextMessage.PREKEY_TYPE) {
          signalProtocolStore.clearSenderKeySharedWith(Collections.singleton(new SignalProtocolAddress(result.getSenderUuid(), result.getDeviceId())));
        }

        paddedMessage = result.getPaddedMessage();
        metadata      = new SignalServiceMetadata(resultAddress, result.getDeviceId(), envelope.getTimestamp(), envelope.getServerTimestamp(), serverDeliveredTimestamp, needsReceipt, envelope.getServerGuid(), groupId, envelope.getDestinationUuid());
      } else {
        throw new InvalidMetadataMessageException("Unknown type: " + envelope.getType());
      }

      PushTransportDetails transportDetails = new PushTransportDetails();
      byte[]               data             = transportDetails.getStrippedPaddingMessageBody(paddedMessage);

      return new Plaintext(metadata, data);
    } catch (DuplicateMessageException e) {
      throw new ProtocolDuplicateMessageException(e, envelope.getSourceUuid(), envelope.getSourceDevice());
    } catch (LegacyMessageException e) {
      throw new ProtocolLegacyMessageException(e, envelope.getSourceUuid(), envelope.getSourceDevice());
    } catch (InvalidMessageException e) {
      throw new ProtocolInvalidMessageException(e, envelope.getSourceUuid(), envelope.getSourceDevice());
    } catch (InvalidKeyIdException e) {
      throw new ProtocolInvalidKeyIdException(e, envelope.getSourceUuid(), envelope.getSourceDevice());
    } catch (InvalidKeyException e) {
      throw new ProtocolInvalidKeyException(e, envelope.getSourceUuid(), envelope.getSourceDevice());
    } catch (UntrustedIdentityException e) {
      throw new ProtocolUntrustedIdentityException(e, envelope.getSourceUuid(), envelope.getSourceDevice());
    } catch (InvalidVersionException e) {
      throw new ProtocolInvalidVersionException(e, envelope.getSourceUuid(), envelope.getSourceDevice());
    } catch (NoSessionException e) {
      throw new ProtocolNoSessionException(e, envelope.getSourceUuid(), envelope.getSourceDevice());
    }
  }

  private static SignalServiceAddress getSourceAddress(Envelope envelope) {
    return new SignalServiceAddress(ServiceId.parseOrNull(envelope.getSourceUuid()));
  }

  private static class Plaintext {
    private final SignalServiceMetadata metadata;
    private final byte[]   data;

    private Plaintext(SignalServiceMetadata metadata, byte[] data) {
      this.metadata = metadata;
      this.data     = data;
    }

    public SignalServiceMetadata getMetadata() {
      return metadata;
    }

    public byte[] getData() {
      return data;
    }
  }
}

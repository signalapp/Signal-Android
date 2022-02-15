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
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidRegistrationIdException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.groups.GroupCipher;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PlaintextContent;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.InvalidMessageStructureException;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceMetadata;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.PushTransportDetails;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.SignalServiceMetadataProtobufSerializer;
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto;

import java.util.Collections;
import java.util.List;

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
                                byte[] groupId)
      throws NoSessionException, UntrustedIdentityException, InvalidKeyException, InvalidRegistrationIdException
  {
    PushTransportDetails             transport            = new PushTransportDetails();
    SignalProtocolAddress            localProtocolAddress = new SignalProtocolAddress(localAddress.getIdentifier(), localDeviceId);
    SignalGroupCipher                groupCipher          = new SignalGroupCipher(sessionLock, new GroupCipher(signalProtocolStore, localProtocolAddress));
    SignalSealedSessionCipher        sessionCipher        = new SignalSealedSessionCipher(sessionLock, new SealedSessionCipher(signalProtocolStore, localAddress.getAci().uuid(), localAddress.getNumber().orNull(), localDeviceId));
    CiphertextMessage                message              = groupCipher.encrypt(distributionId.asUuid(), transport.getPaddedMessageBody(unpaddedMessage));
    UnidentifiedSenderMessageContent messageContent       = new UnidentifiedSenderMessageContent(message,
                                                                                                 senderCertificate,
                                                                                                 contentHint.getType(),
                                                                                                 Optional.of(groupId));

    return sessionCipher.multiRecipientEncrypt(destinations, messageContent);
  }

  public OutgoingPushMessage encrypt(SignalProtocolAddress        destination,
                                     Optional<UnidentifiedAccess> unidentifiedAccess,
                                     EnvelopeContent              content)
      throws UntrustedIdentityException, InvalidKeyException
  {
    if (unidentifiedAccess.isPresent()) {
      SignalSessionCipher       sessionCipher        = new SignalSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, destination));
      SignalSealedSessionCipher sealedSessionCipher  = new SignalSealedSessionCipher(sessionLock, new SealedSessionCipher(signalProtocolStore, localAddress.getAci().uuid(), localAddress.getNumber().orNull(), localDeviceId));

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
      if (envelope.hasLegacyMessage()) {
        Plaintext                       plaintext   = decrypt(envelope, envelope.getLegacyMessage());
        SignalServiceProtos.DataMessage dataMessage = SignalServiceProtos.DataMessage.parseFrom(plaintext.getData());

        SignalServiceContentProto contentProto = SignalServiceContentProto.newBuilder()
                                                                          .setLocalAddress(SignalServiceAddressProtobufSerializer.toProtobuf(localAddress))
                                                                          .setMetadata(SignalServiceMetadataProtobufSerializer.toProtobuf(plaintext.metadata))
                                                                          .setLegacyDataMessage(dataMessage)
                                                                          .build();

        return SignalServiceContent.createFromProto(contentProto);
      } else if (envelope.hasContent()) {
        Plaintext                   plaintext = decrypt(envelope, envelope.getContent());
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

  private Plaintext decrypt(SignalServiceEnvelope envelope, byte[] ciphertext)
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

      if (!envelope.hasSourceUuid() && !envelope.isUnidentifiedSender()) {
        throw new InvalidMessageStructureException("Non-UD envelope is missing a UUID!");
      }

      if (envelope.isPreKeySignalMessage()) {
        SignalProtocolAddress sourceAddress = new SignalProtocolAddress(envelope.getSourceUuid().get(), envelope.getSourceDevice());
        SignalSessionCipher   sessionCipher = new SignalSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, sourceAddress));

        paddedMessage = sessionCipher.decrypt(new PreKeySignalMessage(ciphertext));
        metadata      = new SignalServiceMetadata(envelope.getSourceAddress(), envelope.getSourceDevice(), envelope.getTimestamp(), envelope.getServerReceivedTimestamp(), envelope.getServerDeliveredTimestamp(), false, envelope.getServerGuid(), Optional.absent());

        signalProtocolStore.clearSenderKeySharedWith(Collections.singleton(sourceAddress));
      } else if (envelope.isSignalMessage()) {
        SignalProtocolAddress sourceAddress = new SignalProtocolAddress(envelope.getSourceUuid().get(), envelope.getSourceDevice());
        SignalSessionCipher   sessionCipher = new SignalSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, sourceAddress));

        paddedMessage = sessionCipher.decrypt(new SignalMessage(ciphertext));
        metadata      = new SignalServiceMetadata(envelope.getSourceAddress(), envelope.getSourceDevice(), envelope.getTimestamp(), envelope.getServerReceivedTimestamp(), envelope.getServerDeliveredTimestamp(), false, envelope.getServerGuid(), Optional.absent());
      } else if (envelope.isPlaintextContent()) {
        paddedMessage = new PlaintextContent(ciphertext).getBody();
        metadata      = new SignalServiceMetadata(envelope.getSourceAddress(), envelope.getSourceDevice(), envelope.getTimestamp(), envelope.getServerReceivedTimestamp(), envelope.getServerDeliveredTimestamp(), false, envelope.getServerGuid(), Optional.absent());
      } else if (envelope.isUnidentifiedSender()) {
        SignalSealedSessionCipher sealedSessionCipher = new SignalSealedSessionCipher(sessionLock, new SealedSessionCipher(signalProtocolStore, localAddress.getAci().uuid(), localAddress.getNumber().orNull(), localDeviceId));
        DecryptionResult          result              = sealedSessionCipher.decrypt(certificateValidator, ciphertext, envelope.getServerReceivedTimestamp());
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
        metadata      = new SignalServiceMetadata(resultAddress, result.getDeviceId(), envelope.getTimestamp(), envelope.getServerReceivedTimestamp(), envelope.getServerDeliveredTimestamp(), needsReceipt, envelope.getServerGuid(), groupId);
      } else {
        throw new InvalidMetadataMessageException("Unknown type: " + envelope.getType());
      }

      PushTransportDetails transportDetails = new PushTransportDetails();
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

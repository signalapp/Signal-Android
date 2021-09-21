package org.whispersystems.signalservice.api.crypto;

import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.signal.libsignal.metadata.protocol.UnidentifiedSenderMessageContent;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.protocol.PlaintextContent;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.PushTransportDetails;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope.Type;
import org.whispersystems.util.Base64;

/**
 * An abstraction over the different types of message contents we can have.
 */
public interface EnvelopeContent {

  /**
   * Processes the content using sealed sender.
   */
  OutgoingPushMessage processSealedSender(SignalSessionCipher sessionCipher,
                                          SignalSealedSessionCipher sealedSessionCipher,
                                          SignalProtocolAddress destination,
                                          SenderCertificate senderCertificate)
      throws UntrustedIdentityException, InvalidKeyException;

  /**
   * Processes the content using unsealed sender.
   */
  OutgoingPushMessage processUnsealedSender(SignalSessionCipher sessionCipher, SignalProtocolAddress destination) throws UntrustedIdentityException;

  /**
   * An estimated size, in bytes.
   */
  int size();

  /**
   * A content proto, if applicable.
   */
  Optional<Content> getContent();

  /**
   * Wrap {@link Content} you plan on sending as an encrypted message.
   * This is the default. Consider anything else exceptional.
   */
  static EnvelopeContent encrypted(Content content, ContentHint contentHint, Optional<byte[]> groupId) {
    return new Encrypted(content, contentHint, groupId);
  }

  /**
   * Wraps a {@link PlaintextContent}. This is exceptional, currently limited only to {@link DecryptionErrorMessage}.
   */
  static EnvelopeContent plaintext(PlaintextContent content, Optional<byte[]> groupId) {
    return new Plaintext(content, groupId);
  }

  class Encrypted implements EnvelopeContent {

    private final Content          content;
    private final ContentHint      contentHint;
    private final Optional<byte[]> groupId;

    public Encrypted(Content content, ContentHint contentHint, Optional<byte[]> groupId) {
      this.content     = content;
      this.contentHint = contentHint;
      this.groupId     = groupId;
    }

    @Override
    public OutgoingPushMessage processSealedSender(SignalSessionCipher sessionCipher,
                                                   SignalSealedSessionCipher sealedSessionCipher,
                                                   SignalProtocolAddress destination,
                                                   SenderCertificate senderCertificate)
        throws UntrustedIdentityException, InvalidKeyException
    {
      PushTransportDetails             transportDetails = new PushTransportDetails();
      CiphertextMessage                message          = sessionCipher.encrypt(transportDetails.getPaddedMessageBody(content.toByteArray()));
      UnidentifiedSenderMessageContent messageContent   = new UnidentifiedSenderMessageContent(message,
                                                                                               senderCertificate,
                                                                                               contentHint.getType(),
                                                                                               groupId);

      byte[] ciphertext           = sealedSessionCipher.encrypt(destination, messageContent);
      String body                 = Base64.encodeBytes(ciphertext);
      int    remoteRegistrationId = sealedSessionCipher.getRemoteRegistrationId(destination);

      return new OutgoingPushMessage(Type.UNIDENTIFIED_SENDER_VALUE, destination.getDeviceId(), remoteRegistrationId, body);
    }

    @Override
    public OutgoingPushMessage processUnsealedSender(SignalSessionCipher sessionCipher, SignalProtocolAddress destination) throws UntrustedIdentityException {
      PushTransportDetails transportDetails     = new PushTransportDetails();
      CiphertextMessage    message              = sessionCipher.encrypt(transportDetails.getPaddedMessageBody(content.toByteArray()));
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

    @Override
    public int size() {
      return content.getSerializedSize();
    }

    @Override
    public Optional<Content> getContent() {
      return Optional.of(content);
    }
  }

  class Plaintext implements EnvelopeContent {

    private final PlaintextContent plaintextContent;
    private final Optional<byte[]> groupId;

    public Plaintext(PlaintextContent plaintextContent, Optional<byte[]> groupId) {
      this.plaintextContent = plaintextContent;
      this.groupId          = groupId;
    }

    @Override
    public OutgoingPushMessage processSealedSender(SignalSessionCipher sessionCipher,
                                                   SignalSealedSessionCipher sealedSessionCipher,
                                                   SignalProtocolAddress destination,
                                                   SenderCertificate senderCertificate)
        throws UntrustedIdentityException, InvalidKeyException
    {
      UnidentifiedSenderMessageContent messageContent = new UnidentifiedSenderMessageContent(plaintextContent,
                                                                                             senderCertificate,
                                                                                             ContentHint.IMPLICIT.getType(),
                                                                                             groupId);

      byte[] ciphertext           = sealedSessionCipher.encrypt(destination, messageContent);
      String body                 = Base64.encodeBytes(ciphertext);
      int    remoteRegistrationId = sealedSessionCipher.getRemoteRegistrationId(destination);

      return new OutgoingPushMessage(Type.UNIDENTIFIED_SENDER_VALUE, destination.getDeviceId(), remoteRegistrationId, body);
    }

    @Override
    public OutgoingPushMessage processUnsealedSender(SignalSessionCipher sessionCipher, SignalProtocolAddress destination) {
      String body                 = Base64.encodeBytes(plaintextContent.serialize());
      int    remoteRegistrationId = sessionCipher.getRemoteRegistrationId();

      return new OutgoingPushMessage(Type.PLAINTEXT_CONTENT_VALUE, destination.getDeviceId(), remoteRegistrationId, body);
    }

    @Override
    public int size() {
      return plaintextContent.getBody().length;
    }

    @Override
    public Optional<Content> getContent() {
      return Optional.absent();
    }
  }
}

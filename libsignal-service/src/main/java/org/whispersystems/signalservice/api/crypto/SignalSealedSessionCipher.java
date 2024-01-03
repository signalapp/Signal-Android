package org.whispersystems.signalservice.api.crypto;

import org.signal.libsignal.internal.Native;
import org.signal.libsignal.internal.NativeHandleGuard;
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
import org.signal.libsignal.metadata.SelfSendException;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.protocol.UnidentifiedSenderMessageContent;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidRegistrationIdException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.UntrustedIdentityException;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SignalProtocolStore;
import org.whispersystems.signalservice.api.SignalSessionLock;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A thread-safe wrapper around {@link SealedSessionCipher}.
 */
public class SignalSealedSessionCipher {

  private final SignalSessionLock   lock;
  private final SealedSessionCipher cipher;

  public SignalSealedSessionCipher(SignalSessionLock lock, SealedSessionCipher cipher) {
    this.lock   = lock;
    this.cipher = cipher;
  }

  public byte[] encrypt(SignalProtocolAddress destinationAddress, UnidentifiedSenderMessageContent content)
      throws InvalidKeyException, UntrustedIdentityException
  {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(destinationAddress, content);
    }
  }

  // TODO: Revert the change here to use the libsignal SealedSessionCipher when the API changes
  public byte[] multiRecipientEncrypt(SignalProtocolStore signalProtocolStore, List<SignalProtocolAddress> recipients, Map<SignalProtocolAddress, SessionRecord> sessionMap, UnidentifiedSenderMessageContent content)
      throws InvalidKeyException, UntrustedIdentityException, NoSessionException, InvalidRegistrationIdException
  {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      if (sessionMap == null) {
        return cipher.multiRecipientEncrypt(recipients, content);
      }
      List<SessionRecord> recipientSessions = recipients.stream().map(sessionMap::get).collect(Collectors.toList());
      if (recipientSessions.stream().anyMatch(Objects::isNull)) {
        throw new NoSessionException("Failed to find one or more sessions.");
      }
      // Unsafely access the native handles for the recipients and sessions,
      // because try-with-resources syntax doesn't support a List of resources.
      long[] recipientHandles = new long[recipients.size()];
      int i = 0;
      for (SignalProtocolAddress nextRecipient : recipients) {
        recipientHandles[i] = nextRecipient.unsafeNativeHandleWithoutGuard();
        i++;
      }

      long[] recipientSessionHandles = new long[recipientSessions.size()];
      i = 0;
      for (SessionRecord nextSession : recipientSessions) {
        recipientSessionHandles[i] = nextSession.unsafeNativeHandleWithoutGuard();
        i++;
      }

      try (NativeHandleGuard contentGuard = new NativeHandleGuard(content)) {
        byte[] result =
            Native.SealedSessionCipher_MultiRecipientEncrypt(
                recipientHandles,
                recipientSessionHandles,
                contentGuard.nativeHandle(),
                signalProtocolStore);
        // Manually keep the lists of recipients and sessions from being garbage collected
        // while we're using their native handles.
        Native.keepAlive(recipients);
        Native.keepAlive(recipientSessions);
        return result;
      }
    }
  }

  public SealedSessionCipher.DecryptionResult decrypt(CertificateValidator validator, byte[] ciphertext, long timestamp) throws InvalidMetadataMessageException, InvalidMetadataVersionException, ProtocolInvalidMessageException, ProtocolInvalidKeyException, ProtocolNoSessionException, ProtocolLegacyMessageException, ProtocolInvalidVersionException, ProtocolDuplicateMessageException, ProtocolInvalidKeyIdException, ProtocolUntrustedIdentityException, SelfSendException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(validator, ciphertext, timestamp);
    }
  }

  public int getSessionVersion(SignalProtocolAddress remoteAddress) {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.getSessionVersion(remoteAddress);
    }
  }

  public int getRemoteRegistrationId(SignalProtocolAddress remoteAddress) {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.getRemoteRegistrationId(remoteAddress);
    }
  }
}

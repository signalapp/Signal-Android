package org.whispersystems.signalservice.api.crypto;

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
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.SignalSessionLock;

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

  public byte[] encrypt(SignalProtocolAddress destinationAddress, SenderCertificate senderCertificate, byte[] paddedPlaintext) throws InvalidKeyException, org.whispersystems.libsignal.UntrustedIdentityException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(destinationAddress, senderCertificate, paddedPlaintext);
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

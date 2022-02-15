package org.whispersystems.signalservice.api.crypto;

import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.signalservice.api.SignalSessionLock;

/**
 * A thread-safe wrapper around {@link SessionCipher}.
 */
public class SignalSessionCipher {

  private final SignalSessionLock lock;
  private final SessionCipher     cipher;

  public SignalSessionCipher(SignalSessionLock lock, SessionCipher cipher) {
    this.lock   = lock;
    this.cipher = cipher;
  }

  public CiphertextMessage encrypt(byte[] paddedMessage) throws org.whispersystems.libsignal.UntrustedIdentityException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(paddedMessage);
    }
  }

  public byte[] decrypt(PreKeySignalMessage ciphertext) throws DuplicateMessageException, LegacyMessageException, InvalidMessageException, InvalidKeyIdException, InvalidKeyException, org.whispersystems.libsignal.UntrustedIdentityException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(ciphertext);
    }
  }

  public byte[] decrypt(SignalMessage ciphertext) throws InvalidMessageException, DuplicateMessageException, LegacyMessageException, NoSessionException, UntrustedIdentityException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(ciphertext);
    }
  }

  public int getRemoteRegistrationId() {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.getRemoteRegistrationId();
    }
  }

  public int getSessionVersion() {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.getSessionVersion();
    }
  }
}

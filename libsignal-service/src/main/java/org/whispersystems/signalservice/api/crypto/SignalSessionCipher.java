package org.whispersystems.signalservice.api.crypto;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.InvalidVersionException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SessionCipher;
import org.signal.libsignal.protocol.UntrustedIdentityException;
import org.signal.libsignal.protocol.UsePqRatchet;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.message.SignalMessage;
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

  public CiphertextMessage encrypt(byte[] paddedMessage) throws org.signal.libsignal.protocol.UntrustedIdentityException, NoSessionException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(paddedMessage);
    }
  }

  public byte[] decrypt(PreKeySignalMessage ciphertext, UsePqRatchet usePqRatchet) throws DuplicateMessageException, LegacyMessageException, InvalidMessageException, InvalidKeyIdException, InvalidKeyException, org.signal.libsignal.protocol.UntrustedIdentityException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(ciphertext, usePqRatchet);
    }
  }

  public byte[] decrypt(SignalMessage ciphertext) throws InvalidMessageException, InvalidVersionException, DuplicateMessageException, LegacyMessageException, NoSessionException, UntrustedIdentityException {
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

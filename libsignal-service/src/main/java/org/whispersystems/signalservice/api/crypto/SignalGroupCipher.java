package org.whispersystems.signalservice.api.crypto;

import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.groups.GroupCipher;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.whispersystems.signalservice.api.SignalSessionLock;

import java.util.UUID;

/**
 * A thread-safe wrapper around {@link GroupCipher}.
 */
public class SignalGroupCipher {

  private final SignalSessionLock lock;
  private final GroupCipher       cipher;

  public SignalGroupCipher(SignalSessionLock lock, GroupCipher cipher) {
    this.lock   = lock;
    this.cipher = cipher;
  }

  public CiphertextMessage encrypt(UUID distributionId, byte[] paddedPlaintext) throws NoSessionException {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.encrypt(distributionId, paddedPlaintext);
    }
  }

  public byte[] decrypt(byte[] senderKeyMessageBytes)
      throws LegacyMessageException, DuplicateMessageException, InvalidMessageException, NoSessionException
  {
    try (SignalSessionLock.Lock unused = lock.acquire()) {
      return cipher.decrypt(senderKeyMessageBytes);
    }
  }
}

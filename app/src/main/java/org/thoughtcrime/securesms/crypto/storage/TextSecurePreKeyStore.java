package org.thoughtcrime.securesms.crypto.storage;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyStore;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyStore;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.List;

public class TextSecurePreKeyStore implements PreKeyStore, SignedPreKeyStore {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(TextSecurePreKeyStore.class);

  @NonNull
  private final ServiceId accountId;

  public TextSecurePreKeyStore(@NonNull ServiceId accountId) {
    this.accountId = accountId;
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      PreKeyRecord preKeyRecord = SignalDatabase.oneTimePreKeys().get(accountId, preKeyId);

      if (preKeyRecord == null) throw new InvalidKeyIdException("No such key: " + preKeyId);
      else                      return preKeyRecord;
    }
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SignedPreKeyRecord signedPreKeyRecord = SignalDatabase.signedPreKeys().get(accountId, signedPreKeyId);

      if (signedPreKeyRecord == null) throw new InvalidKeyIdException("No such signed prekey: " + signedPreKeyId);
      else                            return signedPreKeyRecord;
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      return SignalDatabase.signedPreKeys().getAll(accountId);
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SignalDatabase.oneTimePreKeys().insert(accountId, preKeyId, record);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      SignalDatabase.signedPreKeys().insert(accountId, signedPreKeyId, record);
    }
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    return SignalDatabase.oneTimePreKeys().get(accountId, preKeyId) != null;
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return SignalDatabase.signedPreKeys().get(accountId, signedPreKeyId) != null;
  }

  @Override
  public void removePreKey(int preKeyId) {
    SignalDatabase.oneTimePreKeys().delete(accountId, preKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    SignalDatabase.signedPreKeys().delete(accountId, signedPreKeyId);
  }
}

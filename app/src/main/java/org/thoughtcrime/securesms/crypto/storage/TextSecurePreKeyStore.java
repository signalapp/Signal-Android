package org.thoughtcrime.securesms.crypto.storage;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.List;

public class TextSecurePreKeyStore implements PreKeyStore, SignedPreKeyStore {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(TextSecurePreKeyStore.class);

  private static final Object LOCK = new Object();

  @NonNull
  private final ServiceId accountId;

  public TextSecurePreKeyStore(@NonNull ServiceId accountId) {
    this.accountId = accountId;
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    synchronized (LOCK) {
      PreKeyRecord preKeyRecord = SignalDatabase.oneTimePreKeys().get(accountId, preKeyId);

      if (preKeyRecord == null) throw new InvalidKeyIdException("No such key: " + preKeyId);
      else                      return preKeyRecord;
    }
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    synchronized (LOCK) {
      SignedPreKeyRecord signedPreKeyRecord = SignalDatabase.signedPreKeys().get(accountId, signedPreKeyId);

      if (signedPreKeyRecord == null) throw new InvalidKeyIdException("No such signed prekey: " + signedPreKeyId);
      else                            return signedPreKeyRecord;
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    synchronized (LOCK) {
      return SignalDatabase.signedPreKeys().getAll(accountId);
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    synchronized (LOCK) {
      SignalDatabase.oneTimePreKeys().insert(accountId, preKeyId, record);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    synchronized (LOCK) {
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

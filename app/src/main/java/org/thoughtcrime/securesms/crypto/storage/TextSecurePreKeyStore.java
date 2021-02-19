package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.DatabaseSessionLock;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.signalservice.api.SignalSessionLock;

import java.util.List;

public class TextSecurePreKeyStore implements PreKeyStore, SignedPreKeyStore {

  @SuppressWarnings("unused")
  private static final String TAG = TextSecurePreKeyStore.class.getSimpleName();

  @NonNull
  private final Context context;

  public TextSecurePreKeyStore(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      PreKeyRecord preKeyRecord = DatabaseFactory.getPreKeyDatabase(context).getPreKey(preKeyId);

      if (preKeyRecord == null) throw new InvalidKeyIdException("No such key: " + preKeyId);
      else                      return preKeyRecord;
    }
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      SignedPreKeyRecord signedPreKeyRecord = DatabaseFactory.getSignedPreKeyDatabase(context).getSignedPreKey(signedPreKeyId);

      if (signedPreKeyRecord == null) throw new InvalidKeyIdException("No such signed prekey: " + signedPreKeyId);
      else                            return signedPreKeyRecord;
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      return DatabaseFactory.getSignedPreKeyDatabase(context).getAllSignedPreKeys();
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      DatabaseFactory.getPreKeyDatabase(context).insertPreKey(preKeyId, record);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    try (SignalSessionLock.Lock unused = DatabaseSessionLock.INSTANCE.acquire()) {
      DatabaseFactory.getSignedPreKeyDatabase(context).insertSignedPreKey(signedPreKeyId, record);
    }
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    return DatabaseFactory.getPreKeyDatabase(context).getPreKey(preKeyId) != null;
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return DatabaseFactory.getSignedPreKeyDatabase(context).getSignedPreKey(signedPreKeyId) != null;
  }

  @Override
  public void removePreKey(int preKeyId) {
    DatabaseFactory.getPreKeyDatabase(context).removePreKey(preKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    DatabaseFactory.getSignedPreKeyDatabase(context).removeSignedPreKey(signedPreKeyId);
  }
}

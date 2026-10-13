package org.thoughtcrime.securesms.crypto;


import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.crypto.KeyStoreHelper;
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * It can be rather expensive to read from the keystore, so this class caches the key in memory
 * after it is created.
 */
public final class DatabaseSecretProvider {

  private static volatile DatabaseSecret instance;

  public static DatabaseSecret getOrCreateDatabaseSecret(@NonNull Context context) {
    if (instance == null) {
      synchronized (DatabaseSecretProvider.class) {
        if (instance == null) {
          instance = getOrCreate();
        }
      }
    }

    return instance;
  }

  private DatabaseSecretProvider() {
  }

  private static @NonNull DatabaseSecret getOrCreate() {
    String unencryptedSecret = PlainTextKeyValueStore.getDatabaseLegacyUnencryptedSecret();
    String encryptedSecret   = PlainTextKeyValueStore.getDatabaseEncryptedSecret();

    if      (unencryptedSecret != null) return getUnencryptedDatabaseSecret(unencryptedSecret);
    else if (encryptedSecret != null)   return getEncryptedDatabaseSecret(encryptedSecret);
    else                                return createAndStoreDatabaseSecret();
  }

  private static @NonNull DatabaseSecret getUnencryptedDatabaseSecret(@NonNull String unencryptedSecret) {
    try {
      DatabaseSecret databaseSecret = new DatabaseSecret(unencryptedSecret);

      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());

      PlainTextKeyValueStore.setDatabaseEncryptedSecret(encryptedSecret.serialize());
      PlainTextKeyValueStore.setDatabaseLegacyUnencryptedSecret(null);

      return databaseSecret;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static @NonNull DatabaseSecret getEncryptedDatabaseSecret(@NonNull String serializedEncryptedSecret) {
    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret);
    return new DatabaseSecret(KeyStoreHelper.unseal(encryptedSecret));
  }

  private static @NonNull DatabaseSecret createAndStoreDatabaseSecret() {
    SecureRandom random = new SecureRandom();
    byte[]       secret = new byte[32];
    random.nextBytes(secret);

    DatabaseSecret databaseSecret = new DatabaseSecret(secret);

    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());
    PlainTextKeyValueStore.setDatabaseEncryptedSecret(encryptedSecret.serialize());

    return databaseSecret;
  }
}

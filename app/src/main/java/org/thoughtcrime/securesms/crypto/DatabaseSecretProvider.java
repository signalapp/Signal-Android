package org.thoughtcrime.securesms.crypto;


import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

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
          instance = getOrCreate(context);
        }
      }
    }

    return instance;
  }

  private DatabaseSecretProvider() {
  }

  private static @NonNull DatabaseSecret getOrCreate(@NonNull Context context) {
    String unencryptedSecret = TextSecurePreferences.getDatabaseUnencryptedSecret(context);
    String encryptedSecret   = TextSecurePreferences.getDatabaseEncryptedSecret(context);

    if      (unencryptedSecret != null) return getUnencryptedDatabaseSecret(context, unencryptedSecret);
    else if (encryptedSecret != null)   return getEncryptedDatabaseSecret(encryptedSecret);
    else                                return createAndStoreDatabaseSecret(context);
  }

  private static @NonNull DatabaseSecret getUnencryptedDatabaseSecret(@NonNull Context context, @NonNull String unencryptedSecret)
  {
    try {
      DatabaseSecret databaseSecret = new DatabaseSecret(unencryptedSecret);

      if (Build.VERSION.SDK_INT < 23) {
        return databaseSecret;
      } else {
        KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());

        TextSecurePreferences.setDatabaseEncryptedSecret(context, encryptedSecret.serialize());
        TextSecurePreferences.setDatabaseUnencryptedSecret(context, null);

        return databaseSecret;
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static @NonNull DatabaseSecret getEncryptedDatabaseSecret(@NonNull String serializedEncryptedSecret) {
    if (Build.VERSION.SDK_INT < 23) {
      throw new AssertionError("OS downgrade not supported. KeyStore sealed data exists on platform < M!");
    } else {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret);
      return new DatabaseSecret(KeyStoreHelper.unseal(encryptedSecret));
    }
  }

  private static @NonNull DatabaseSecret createAndStoreDatabaseSecret(@NonNull Context context) {
    SecureRandom random = new SecureRandom();
    byte[]       secret = new byte[32];
    random.nextBytes(secret);

    DatabaseSecret databaseSecret = new DatabaseSecret(secret);

    if (Build.VERSION.SDK_INT >= 23) {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(databaseSecret.asBytes());
      TextSecurePreferences.setDatabaseEncryptedSecret(context, encryptedSecret.serialize());
    } else {
      TextSecurePreferences.setDatabaseUnencryptedSecret(context, databaseSecret.asString());
    }

    return databaseSecret;
  }
}

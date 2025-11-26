package org.thoughtcrime.securesms.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.KeyStoreHelper;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Allows the getting and setting of the backup passphrase, which is stored encrypted on API >= 23.
 */
public final class BackupPassphrase {

  private BackupPassphrase() {
  }

  private static final String TAG = Log.tag(BackupPassphrase.class);

  public static @Nullable String get(@NonNull Context context) {
    String passphrase          = TextSecurePreferences.getBackupPassphrase(context);
    String encryptedPassphrase = TextSecurePreferences.getEncryptedBackupPassphrase(context);

    if (passphrase == null && encryptedPassphrase == null) {
      return null;
    }

    if (encryptedPassphrase == null) {
      Log.i(TAG, "Migrating to encrypted passphrase.");
      set(context, passphrase);
      encryptedPassphrase = TextSecurePreferences.getEncryptedBackupPassphrase(context);
      if (encryptedPassphrase == null) throw new AssertionError("Passphrase migration failed");
    }

    KeyStoreHelper.SealedData data = KeyStoreHelper.SealedData.fromString(encryptedPassphrase);
    return stripSpaces(new String(KeyStoreHelper.unseal(data)));
  }

  public static void set(@NonNull Context context, @Nullable String passphrase) {
    if (passphrase == null) {
      TextSecurePreferences.setBackupPassphrase(context, null);
      TextSecurePreferences.setEncryptedBackupPassphrase(context, null);
    } else {
      KeyStoreHelper.SealedData encryptedPassphrase = KeyStoreHelper.seal(passphrase.getBytes());
      TextSecurePreferences.setEncryptedBackupPassphrase(context, encryptedPassphrase.serialize());
      TextSecurePreferences.setBackupPassphrase(context, null);
    }
  }

  private static String stripSpaces(@Nullable String passphrase) {
    return passphrase != null ? passphrase.replace(" ", "") : null;
  }
}

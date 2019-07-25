package org.thoughtcrime.securesms.backup;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.crypto.KeyStoreHelper;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Allows the getting and setting of the backup passphrase, which is stored encrypted on API >= 23.
 */
public class BackupPassphrase {

  private static final String TAG = BackupPassphrase.class.getSimpleName();

  public static String get(@NonNull Context context) {
    String passphrase          = TextSecurePreferences.getBackupPassphrase(context);
    String encryptedPassphrase = TextSecurePreferences.getEncryptedBackupPassphrase(context);

    if (Build.VERSION.SDK_INT < 23 || (passphrase == null && encryptedPassphrase == null)) {
      return passphrase;
    }

    if (encryptedPassphrase == null) {
      Log.i(TAG, "Migrating to encrypted passphrase.");
      set(context, passphrase);
      encryptedPassphrase = TextSecurePreferences.getEncryptedBackupPassphrase(context);
    }

    KeyStoreHelper.SealedData data = KeyStoreHelper.SealedData.fromString(encryptedPassphrase);
    return new String(KeyStoreHelper.unseal(data));
  }

  public static void set(@NonNull Context context, @Nullable String passphrase) {
    if (passphrase == null || Build.VERSION.SDK_INT < 23) {
      TextSecurePreferences.setBackupPassphrase(context, passphrase);
      TextSecurePreferences.setEncryptedBackupPassphrase(context, null);
    } else {
      KeyStoreHelper.SealedData encryptedPassphrase = KeyStoreHelper.seal(passphrase.getBytes());
      TextSecurePreferences.setEncryptedBackupPassphrase(context, encryptedPassphrase.serialize());
      TextSecurePreferences.setBackupPassphrase(context, null);
    }
  }
}

package org.thoughtcrime.securesms.logging;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.KeyStoreHelper;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.security.SecureRandom;

class LogSecretProvider {

  static byte[] getOrCreateAttachmentSecret(@NonNull Context context) {
    String unencryptedSecret = TextSecurePreferences.getLogUnencryptedSecret(context);
    String encryptedSecret   = TextSecurePreferences.getLogEncryptedSecret(context);

    if      (unencryptedSecret != null) return parseUnencryptedSecret(unencryptedSecret);
    else if (encryptedSecret != null)   return parseEncryptedSecret(encryptedSecret);
    else                                return createAndStoreSecret(context);
  }

  private static byte[] parseUnencryptedSecret(String secret) {
    try {
      return Base64.decode(secret);
    } catch (IOException e) {
      throw new AssertionError("Failed to decode the unecrypted secret.");
    }
  }

  private static byte[] parseEncryptedSecret(String secret) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(secret);
      return KeyStoreHelper.unseal(encryptedSecret);
    } else {
      throw new AssertionError("OS downgrade not supported. KeyStore sealed data exists on platform < M!");
    }
  }

  private static byte[] createAndStoreSecret(@NonNull Context context) {
    SecureRandom random = new SecureRandom();
    byte[]       secret = new byte[32];
    random.nextBytes(secret);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(secret);
      TextSecurePreferences.setLogEncryptedSecret(context, encryptedSecret.serialize());
    } else {
      TextSecurePreferences.setLogUnencryptedSecret(context, Base64.encodeBytes(secret));
    }

    return secret;
  }
}

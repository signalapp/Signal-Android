package org.thoughtcrime.securesms.crypto;


import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.security.SecureRandom;

/**
 * A provider that is responsible for creating or retrieving the AttachmentSecret model.
 *
 * On modern Android, the serialized secrets are themselves encrypted using a key that lives
 * in the system KeyStore, for whatever that is worth.
 */
public class AttachmentSecretProvider {

  private static AttachmentSecretProvider provider;

  public static synchronized AttachmentSecretProvider getInstance(@NonNull Context context) {
    if (provider == null) provider = new AttachmentSecretProvider(context.getApplicationContext());
    return provider;
  }

  private final Context context;

  private AttachmentSecret attachmentSecret;

  private AttachmentSecretProvider(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  public synchronized AttachmentSecret getOrCreateAttachmentSecret() {
    if (attachmentSecret != null) return attachmentSecret;

    String unencryptedSecret = TextSecurePreferences.getAttachmentUnencryptedSecret(context);
    String encryptedSecret   = TextSecurePreferences.getAttachmentEncryptedSecret(context);

    if      (unencryptedSecret != null) attachmentSecret = getUnencryptedAttachmentSecret(context, unencryptedSecret);
    else if (encryptedSecret != null)   attachmentSecret = getEncryptedAttachmentSecret(encryptedSecret);
    else                                attachmentSecret = createAndStoreAttachmentSecret(context);

    return attachmentSecret;
  }

  public synchronized AttachmentSecret setClassicKey(@NonNull Context context, @NonNull byte[] classicCipherKey, @NonNull byte[] classicMacKey) {
    AttachmentSecret currentSecret    = getOrCreateAttachmentSecret();
    currentSecret.setClassicCipherKey(classicCipherKey);
    currentSecret.setClassicMacKey(classicMacKey);

    storeAttachmentSecret(context, attachmentSecret);

    return attachmentSecret;
  }

  private AttachmentSecret getUnencryptedAttachmentSecret(@NonNull Context context, @NonNull String unencryptedSecret)
  {
    AttachmentSecret attachmentSecret = AttachmentSecret.fromString(unencryptedSecret);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return attachmentSecret;
    } else {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(attachmentSecret.serialize().getBytes());

      TextSecurePreferences.setAttachmentEncryptedSecret(context, encryptedSecret.serialize());
      TextSecurePreferences.setAttachmentUnencryptedSecret(context, null);

      return attachmentSecret;
    }
  }

  private AttachmentSecret getEncryptedAttachmentSecret(@NonNull String serializedEncryptedSecret) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      throw new AssertionError("OS downgrade not supported. KeyStore sealed data exists on platform < M!");
    } else {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret);
      return AttachmentSecret.fromString(new String(KeyStoreHelper.unseal(encryptedSecret)));
    }
  }

  private AttachmentSecret createAndStoreAttachmentSecret(@NonNull Context context) {
    SecureRandom random = new SecureRandom();
    byte[]       secret = new byte[32];
    random.nextBytes(secret);

    AttachmentSecret attachmentSecret = new AttachmentSecret(null, null, secret);
    storeAttachmentSecret(context, attachmentSecret);

    return attachmentSecret;
  }

  private void storeAttachmentSecret(@NonNull Context context, @NonNull AttachmentSecret attachmentSecret) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(attachmentSecret.serialize().getBytes());
      TextSecurePreferences.setAttachmentEncryptedSecret(context, encryptedSecret.serialize());
    } else {
      TextSecurePreferences.setAttachmentUnencryptedSecret(context, attachmentSecret.serialize());
    }
  }

}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.crypto;


import android.content.Context;

import androidx.annotation.NonNull;

import java.security.SecureRandom;

/**
 * A provider that is responsible for creating or retrieving the AttachmentSecret model.
 * <p>
 * On modern Android, the serialized secrets are themselves encrypted using a key that lives
 * in the system KeyStore, for whatever that is worth.
 */
public class AttachmentSecretProvider {

  private static AttachmentSecretProvider provider;

  public static synchronized AttachmentSecretProvider getInstance(@NonNull Context context,@NonNull AttachmentSecretStore secretStore) {
    if (provider == null) provider = new AttachmentSecretProvider(context.getApplicationContext(), secretStore);
    return provider;
  }

  private final Context               context;
  private final AttachmentSecretStore secretStore;

  private AttachmentSecret attachmentSecret;

  private AttachmentSecretProvider(@NonNull Context context, @NonNull AttachmentSecretStore secretStore) {
    this.context     = context.getApplicationContext();
    this.secretStore = secretStore;
  }

  /**
   * Because we need this store when we initialize the database, we have a separate method here to allow the app to pass in the concrete [AttachmentSecretStore]
   * so that we don't need to reply on [CoreUtilDependencies] which isn't initialized at the time.
   */
  public synchronized AttachmentSecret getOrCreateAttachmentSecret() {
    if (attachmentSecret != null) return attachmentSecret;

    String unencryptedSecret = secretStore.getAttachmentUnencryptedSecret(context);
    String encryptedSecret   = secretStore.getAttachmentEncryptedSecret(context);

    if      (unencryptedSecret != null) attachmentSecret = getUnencryptedAttachmentSecret(context, unencryptedSecret);
    else if (encryptedSecret != null)   attachmentSecret = getEncryptedAttachmentSecret(encryptedSecret);
    else                                attachmentSecret = createAndStoreAttachmentSecret(context);

    return attachmentSecret;
  }

  private AttachmentSecret getUnencryptedAttachmentSecret(@NonNull Context context, @NonNull String unencryptedSecret)
  {
    AttachmentSecret attachmentSecret = AttachmentSecret.fromString(unencryptedSecret);

    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(attachmentSecret.serialize().getBytes());

    secretStore.setAttachmentEncryptedSecret(context, encryptedSecret.serialize());
    secretStore.setAttachmentUnencryptedSecret(context, null);

    return attachmentSecret;
  }

  private AttachmentSecret getEncryptedAttachmentSecret(@NonNull String serializedEncryptedSecret) {
    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.SealedData.fromString(serializedEncryptedSecret);
    return AttachmentSecret.fromString(new String(KeyStoreHelper.unseal(encryptedSecret)));
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
    KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(attachmentSecret.serialize().getBytes());
    secretStore.setAttachmentEncryptedSecret(context, encryptedSecret.serialize());
  }
}

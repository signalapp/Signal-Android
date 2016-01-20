package org.privatechats.securesms.crypto;

import android.support.annotation.NonNull;

import org.privatechats.securesms.util.Base64;
import org.whispersystems.libaxolotl.InvalidMessageException;

import java.io.IOException;

public class MediaKey {

  public static String getEncrypted(@NonNull MasterSecretUnion masterSecret, @NonNull byte[] key) {
    if (masterSecret.getMasterSecret().isPresent()) {
      return Base64.encodeBytes(new MasterCipher(masterSecret.getMasterSecret().get()).encryptBytes(key));
    } else {
      return "?ASYNC-" + Base64.encodeBytes(new AsymmetricMasterCipher(masterSecret.getAsymmetricMasterSecret().get()).encryptBytes(key));
    }
  }

  public static byte[] getDecrypted(@NonNull MasterSecret masterSecret,
                                    @NonNull AsymmetricMasterSecret asymmetricMasterSecret,
                                    @NonNull String encodedKey)
      throws IOException, InvalidMessageException
  {
    if (encodedKey.startsWith("?ASYNC-")) {
      return new AsymmetricMasterCipher(asymmetricMasterSecret).decryptBytes(Base64.decode(encodedKey.substring("?ASYNC-".length())));
    } else {
      return new MasterCipher(masterSecret).decryptBytes(Base64.decode(encodedKey));
    }
  }
}

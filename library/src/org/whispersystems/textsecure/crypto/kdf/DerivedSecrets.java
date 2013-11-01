package org.whispersystems.textsecure.crypto.kdf;

import javax.crypto.spec.SecretKeySpec;

public class DerivedSecrets {

  private final SecretKeySpec cipherKey;
  private final SecretKeySpec macKey;

  public DerivedSecrets(SecretKeySpec cipherKey, SecretKeySpec macKey) {
    this.cipherKey = cipherKey;
    this.macKey    = macKey;
  }

  public SecretKeySpec getCipherKey() {
    return cipherKey;
  }

  public SecretKeySpec getMacKey() {
    return macKey;
  }
}

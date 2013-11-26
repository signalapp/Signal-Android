package org.whispersystems.textsecure.crypto.ratchet;

import javax.crypto.spec.SecretKeySpec;

public class MessageKeys {

  private final SecretKeySpec cipherKey;
  private final SecretKeySpec macKey;
  private final int           counter;

  public MessageKeys(SecretKeySpec cipherKey, SecretKeySpec macKey, int counter) {
    this.cipherKey = cipherKey;
    this.macKey    = macKey;
    this.counter   = counter;
  }

  public SecretKeySpec getCipherKey() {
    return cipherKey;
  }

  public SecretKeySpec getMacKey() {
    return macKey;
  }

  public int getCounter() {
    return counter;
  }
}

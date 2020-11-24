/**
 * Copyright (C) 2013-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal.ecc;

public class ECKeyPair {

  private final ECPublicKey  publicKey;
  private final ECPrivateKey privateKey;

  public ECKeyPair(ECPublicKey publicKey, ECPrivateKey privateKey) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
  }

  public ECPublicKey getPublicKey() {
    return publicKey;
  }

  public ECPrivateKey getPrivateKey() {
    return privateKey;
  }
}

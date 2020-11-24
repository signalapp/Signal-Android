/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal;


import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.Hex;

/**
 * A class for representing an identity key.
 * 
 * @author Moxie Marlinspike
 */

public class IdentityKey {

  private final ECPublicKey publicKey;

  public IdentityKey(ECPublicKey publicKey) {
    this.publicKey = publicKey;
  }

  public IdentityKey(byte[] bytes, int offset) throws InvalidKeyException {
    this.publicKey = Curve.decodePoint(bytes, offset);
  }

  public ECPublicKey getPublicKey() {
    return publicKey;
  }

  public byte[] serialize() {
    return publicKey.serialize();
  }

  public String getFingerprint() {
    return Hex.toString(publicKey.serialize());
  }
	
  @Override
  public boolean equals(Object other) {
    if (other == null)                   return false;
    if (!(other instanceof IdentityKey)) return false;

    return publicKey.equals(((IdentityKey) other).getPublicKey());
  }
	
  @Override
  public int hashCode() {
    return publicKey.hashCode();
  }
}

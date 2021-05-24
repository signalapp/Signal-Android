/**
 * Copyright (C) 2013-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.crypto.ecc;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;
import org.session.libsignal.exceptions.InvalidKeyException;
import static org.whispersystems.curve25519.Curve25519.BEST;

public class Curve {

  public  static final int DJB_TYPE   = 0x05;

  public static ECKeyPair generateKeyPair() {
    Curve25519KeyPair keyPair = Curve25519.getInstance(BEST).generateKeyPair();
    return new ECKeyPair(new DjbECPublicKey(keyPair.getPublicKey()), new DjbECPrivateKey(keyPair.getPrivateKey()));
  }

  public static ECPublicKey decodePoint(byte[] bytes, int offset)
      throws InvalidKeyException
  {
    if (bytes == null || bytes.length - offset < 1) {
      throw new InvalidKeyException("No key type identifier");
    }

    int type = bytes[offset] & 0xFF;

    switch (type) {
      case Curve.DJB_TYPE:
        if (bytes.length - offset < 33) {
          throw new InvalidKeyException("Bad key length: " + bytes.length);
        }

        byte[] keyBytes = new byte[32];
        System.arraycopy(bytes, offset+1, keyBytes, 0, keyBytes.length);
        return new DjbECPublicKey(keyBytes);
      default:
        throw new InvalidKeyException("Bad key type: " + type);
    }
  }

  public static ECPrivateKey decodePrivatePoint(byte[] bytes) {
    return new DjbECPrivateKey(bytes);
  }
}

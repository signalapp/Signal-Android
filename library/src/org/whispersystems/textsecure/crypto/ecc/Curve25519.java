/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.crypto.ecc;

import org.whispersystems.textsecure.crypto.InvalidKeyException;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Curve25519 {

  static {
    System.loadLibrary("curve25519");

    try {
      random = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static final SecureRandom random;

  private static native byte[] calculateAgreement(byte[] ourPrivate, byte[] theirPublic);
  private static native byte[] generatePublicKey(byte[] privateKey);
  private static native byte[] generatePrivateKey(byte[] random, boolean ephemeral);

  public static ECKeyPair generateKeyPair(boolean ephemeral) {
    byte[] privateKey = generatePrivateKey(ephemeral);
    byte[] publicKey  = generatePublicKey(privateKey);

    return new ECKeyPair(new DjbECPublicKey(publicKey), new DjbECPrivateKey(privateKey));
  }

  static byte[] calculateAgreement(ECPublicKey publicKey, ECPrivateKey privateKey) {
    return calculateAgreement(((DjbECPrivateKey)privateKey).getPrivateKey(),
                              ((DjbECPublicKey)publicKey).getPublicKey());
  }

  static ECPublicKey decodePoint(byte[] encoded, int offset)
      throws InvalidKeyException
  {
    int    type     = encoded[offset] & 0xFF;
    byte[] keyBytes = new byte[32];
    System.arraycopy(encoded, offset+1, keyBytes, 0, keyBytes.length);

    if (type != Curve.DJB_TYPE) {
      throw new InvalidKeyException("Bad key type: " + type);
    }

    return new DjbECPublicKey(keyBytes);
  }

  private static byte[] generatePrivateKey(boolean ephemeral) {
    byte[] privateKey = new byte[32];
    random.nextBytes(privateKey);

    return generatePrivateKey(privateKey, ephemeral);
  }

}

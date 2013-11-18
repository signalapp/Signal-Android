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
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;

public class Curve {

  public  static final int NIST_TYPE  = 0x02;
  private static final int NIST_TYPE2 = 0x03;
  public  static final int DJB_TYPE   = 0x05;

  public static ECKeyPair generateKeyPairForType(int keyType) {
    if (keyType == DJB_TYPE) {
      return Curve25519.generateKeyPair();
    } else if (keyType == NIST_TYPE || keyType == NIST_TYPE2) {
      return CurveP256.generateKeyPair();
    } else {
      throw new AssertionError("Bad key type: " + keyType);
    }
  }

  public static ECKeyPair generateKeyPairForSession(int messageVersion) {
    if (messageVersion >= CiphertextMessage.CURVE25519_INTRODUCED_VERSION) {
      return generateKeyPairForType(DJB_TYPE);
    } else {
      return generateKeyPairForType(NIST_TYPE);
    }
  }

  public static ECPublicKey decodePoint(byte[] bytes, int offset)
      throws InvalidKeyException
  {
    int type = bytes[offset];

    if (type == DJB_TYPE) {
      return Curve25519.decodePoint(bytes, offset);
    } else if (type == NIST_TYPE || type == NIST_TYPE2) {
      return CurveP256.decodePoint(bytes, offset);
    } else {
      throw new InvalidKeyException("Unknown key type: " + type);
    }
  }

  public static ECPrivateKey decodePrivatePoint(int type, byte[] bytes) {
    if (type == DJB_TYPE) {
      return new DjbECPrivateKey(bytes);
    } else if (type == NIST_TYPE || type == NIST_TYPE2) {
      return CurveP256.decodePrivatePoint(bytes);
    } else {
      throw new AssertionError("Bad key type: " + type);
    }
  }

  public static byte[] calculateAgreement(ECPublicKey publicKey, ECPrivateKey privateKey)
      throws InvalidKeyException
  {
    if (publicKey.getType() != privateKey.getType()) {
      throw new InvalidKeyException("Public and private keys must be of the same type!");
    }

    if (publicKey.getType() == DJB_TYPE) {
      return Curve25519.calculateAgreement(publicKey, privateKey);
    } else if (publicKey.getType() == NIST_TYPE || publicKey.getType() == NIST_TYPE2) {
      return CurveP256.calculateAgreement(publicKey, privateKey);
    } else {
      throw new InvalidKeyException("Unknown type: " + publicKey.getType());
    }
  }
}

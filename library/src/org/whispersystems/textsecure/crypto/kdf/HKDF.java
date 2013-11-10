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

package org.whispersystems.textsecure.crypto.kdf;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HKDF extends KDF {

  private static final int HASH_OUTPUT_SIZE  = 32;
  private static final int KEY_MATERIAL_SIZE = 72;

  private static final int CIPHER_KEYS_OFFSET = 0;
  private static final int MAC_KEYS_OFFSET    = 32;

  @Override
  public DerivedSecrets deriveSecrets(List<byte[]> sharedSecret,
                                      boolean isLowEnd, byte[] info)
  {
    byte[] inputKeyMaterial = concatenateSharedSecrets(sharedSecret);
    byte[] salt             = new byte[HASH_OUTPUT_SIZE];
    byte[] prk              = extract(salt, inputKeyMaterial);
    byte[] okm              = expand(prk, info, KEY_MATERIAL_SIZE);

    SecretKeySpec cipherKey = deriveCipherKey(okm, isLowEnd);
    SecretKeySpec macKey    = deriveMacKey(okm, isLowEnd);

    return new DerivedSecrets(cipherKey, macKey);
  }

  private SecretKeySpec deriveCipherKey(byte[] okm, boolean isLowEnd) {
    byte[] cipherKey = new byte[16];

    if (isLowEnd) {
      System.arraycopy(okm, CIPHER_KEYS_OFFSET + 0, cipherKey, 0, cipherKey.length);
    } else {
      System.arraycopy(okm, CIPHER_KEYS_OFFSET + 16, cipherKey, 0, cipherKey.length);
    }

    return new SecretKeySpec(cipherKey, "AES");
  }

  private SecretKeySpec deriveMacKey(byte[] okm, boolean isLowEnd) {
    byte[] macKey = new byte[20];

    if (isLowEnd) {
      System.arraycopy(okm, MAC_KEYS_OFFSET + 0, macKey, 0, macKey.length);
    } else {
      System.arraycopy(okm, MAC_KEYS_OFFSET + 20, macKey, 0, macKey.length);
    }

    return new SecretKeySpec(macKey, "HmacSHA1");
  }

  private byte[] extract(byte[] salt, byte[] inputKeyMaterial) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(salt, "HmacSHA256"));
      return mac.doFinal(inputKeyMaterial);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] expand(byte[] prk, byte[] info, int outputSize) {
    try {
      int                   iterations = (int)Math.ceil((double)outputSize/(double)HASH_OUTPUT_SIZE);
      byte[]                mixin      = new byte[0];
      ByteArrayOutputStream results    = new ByteArrayOutputStream();

      for (int i=0;i<iterations;i++) {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));

        mac.update(mixin);
        mac.update(info);
        mac.update((byte)i);

        byte[] stepResult = mac.doFinal();
        results.write(stepResult, 0, stepResult.length);

        mixin = stepResult;
      }

      return results.toByteArray();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }



}

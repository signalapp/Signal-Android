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

import android.util.Log;

import org.whispersystems.textsecure.util.Conversions;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.spec.SecretKeySpec;

public class NKDF {

  public static final int LEGACY_CIPHER_KEY_LENGTH = 16;
  public static final int LEGACY_MAC_KEY_LENGTH    = 20;

  public DerivedSecrets deriveSecrets(byte[] sharedSecret, boolean isLowEnd)
  {
    SecretKeySpec cipherKey = deriveCipherSecret(isLowEnd, sharedSecret);
    SecretKeySpec macKey    = deriveMacSecret(cipherKey);

    return new DerivedSecrets(cipherKey, macKey);
  }

  private SecretKeySpec deriveCipherSecret(boolean isLowEnd, byte[] sharedSecret) {
    byte[] derivedBytes = deriveBytes(sharedSecret, LEGACY_CIPHER_KEY_LENGTH * 2);
    byte[] cipherSecret = new byte[LEGACY_CIPHER_KEY_LENGTH];

    if (isLowEnd)  {
      System.arraycopy(derivedBytes, LEGACY_CIPHER_KEY_LENGTH, cipherSecret, 0, LEGACY_CIPHER_KEY_LENGTH);
    } else {
      System.arraycopy(derivedBytes, 0, cipherSecret, 0, LEGACY_CIPHER_KEY_LENGTH);
    }

    return new SecretKeySpec(cipherSecret, "AES");
  }

  private SecretKeySpec deriveMacSecret(SecretKeySpec key) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] secret    = md.digest(key.getEncoded());

      return new SecretKeySpec(secret, "HmacSHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("SHA-1 Not Supported!",e);
    }
  }

  private byte[] deriveBytes(byte[] seed, int bytesNeeded) {
    MessageDigest md;

    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      Log.w("NKDF", e);
      throw new IllegalArgumentException("SHA-256 Not Supported!");
    }

    int rounds = bytesNeeded / md.getDigestLength();

    for (int i=1;i<=rounds;i++) {
      byte[] roundBytes = Conversions.intToByteArray(i);
      md.update(roundBytes);
      md.update(seed);
    }

    return md.digest();
  }
}

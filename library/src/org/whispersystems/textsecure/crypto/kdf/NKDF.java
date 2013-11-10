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
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

public class NKDF extends KDF {

  @Override
  public DerivedSecrets deriveSecrets(List<byte[]> sharedSecret,
                                      boolean isLowEnd, byte[] info)
  {
    SecretKeySpec cipherKey = deriveCipherSecret(isLowEnd, sharedSecret);
    SecretKeySpec macKey    = deriveMacSecret(cipherKey);

    return new DerivedSecrets(cipherKey, macKey);
  }

  private SecretKeySpec deriveCipherSecret(boolean isLowEnd, List<byte[]> sharedSecret) {
    byte[] sharedSecretBytes = concatenateSharedSecrets(sharedSecret);
    byte[] derivedBytes      = deriveBytes(sharedSecretBytes, 16 * 2);
    byte[] cipherSecret      = new byte[16];

    if (isLowEnd)  {
      System.arraycopy(derivedBytes, 16, cipherSecret, 0, 16);
    } else {
      System.arraycopy(derivedBytes, 0, cipherSecret, 0, 16);
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

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

package org.whispersystems.libaxolotl.kdf;

import javax.crypto.spec.SecretKeySpec;

public class DerivedSecrets {

  public  static final int SIZE               = 64;
  private static final int CIPHER_KEYS_OFFSET = 0;
  private static final int MAC_KEYS_OFFSET    = 32;

  private final SecretKeySpec cipherKey;
  private final SecretKeySpec macKey;

  public DerivedSecrets(byte[] okm) {
    this.cipherKey = deriveCipherKey(okm);
    this.macKey    = deriveMacKey(okm);
  }
  private SecretKeySpec deriveCipherKey(byte[] okm) {
    byte[] cipherKey = new byte[32];
    System.arraycopy(okm, CIPHER_KEYS_OFFSET, cipherKey, 0, cipherKey.length);
    return new SecretKeySpec(cipherKey, "AES");
  }

  private SecretKeySpec deriveMacKey(byte[] okm) {
    byte[] macKey = new byte[32];
    System.arraycopy(okm, MAC_KEYS_OFFSET, macKey, 0, macKey.length);
    return new SecretKeySpec(macKey, "HmacSHA256");
  }

  public SecretKeySpec getCipherKey() {
    return cipherKey;
  }

  public SecretKeySpec getMacKey() {
    return macKey;
  }
}

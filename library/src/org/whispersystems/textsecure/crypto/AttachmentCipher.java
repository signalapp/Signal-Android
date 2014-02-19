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
package org.whispersystems.textsecure.crypto;

import android.util.Log;

import org.whispersystems.textsecure.util.Hex;
import org.whispersystems.textsecure.util.Util;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Arrays;

/**
 * Encrypts push attachments.
 *
 * @author Moxie Marlinspike
 */
public class AttachmentCipher {

  static final int CIPHER_KEY_SIZE = 32;
  static final int MAC_KEY_SIZE    = 32;

  private final SecretKeySpec cipherKey;
  private final SecretKeySpec macKey;
  private final Cipher        cipher;
  private final Mac           mac;

  public AttachmentCipher() {
    this.cipherKey = initializeRandomCipherKey();
    this.macKey    = initializeRandomMacKey();
    this.cipher    = initializeCipher();
    this.mac       = initializeMac();
  }

  public AttachmentCipher(byte[] combinedKeyMaterial) {
    byte[][] parts = Util.split(combinedKeyMaterial, CIPHER_KEY_SIZE, MAC_KEY_SIZE);
    this.cipherKey = new SecretKeySpec(parts[0], "AES");
    this.macKey    = new SecretKeySpec(parts[1], "HmacSHA256");
    this.cipher    = initializeCipher();
    this.mac       = initializeMac();
  }

  public byte[] getCombinedKeyMaterial() {
    return Util.combine(this.cipherKey.getEncoded(), this.macKey.getEncoded());
  }

  public byte[] encrypt(byte[] plaintext) {
    try {
      this.cipher.init(Cipher.ENCRYPT_MODE, this.cipherKey);
      this.mac.init(this.macKey);

      byte[] ciphertext = this.cipher.doFinal(plaintext);
      byte[] iv         = this.cipher.getIV();
      byte[] mac        = this.mac.doFinal(Util.combine(iv, ciphertext));

      return Util.combine(iv, ciphertext, mac);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public byte[] decrypt(byte[] ciphertext)
      throws InvalidMacException, InvalidMessageException
  {
    try {
      if (ciphertext.length <= cipher.getBlockSize() + mac.getMacLength()) {
        throw new InvalidMessageException("Message too short!");
      }

      byte[][] ciphertextParts = Util.split(ciphertext,
                                            this.cipher.getBlockSize(),
                                            ciphertext.length - this.cipher.getBlockSize() - this.mac.getMacLength(),
                                            this.mac.getMacLength());

      this.mac.update(ciphertext, 0, ciphertext.length - mac.getMacLength());
      byte[] ourMac = this.mac.doFinal();

      if (!Arrays.equals(ourMac, ciphertextParts[2])) {
        throw new InvalidMacException("Mac doesn't match!");
      }

      this.cipher.init(Cipher.DECRYPT_MODE, this.cipherKey,
                       new IvParameterSpec(ciphertextParts[0]));

      return cipher.doFinal(ciphertextParts[1]);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new InvalidMessageException(e);
    } catch (ParseException e) {
      throw new InvalidMessageException(e);
    }
  }

  private Mac initializeMac() {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      return mac;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private Cipher initializeCipher() {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      return cipher;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private SecretKeySpec initializeRandomCipherKey() {
    byte[] key = new byte[CIPHER_KEY_SIZE];
    Util.getSecureRandom().nextBytes(key);
    return new SecretKeySpec(key, "AES");
  }

  private SecretKeySpec initializeRandomMacKey() {
    byte[] key = new byte[MAC_KEY_SIZE];
    Util.getSecureRandom().nextBytes(key);
    return new SecretKeySpec(key, "HmacSHA256");
  }

}

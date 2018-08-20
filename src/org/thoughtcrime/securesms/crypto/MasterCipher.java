/** 
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.crypto;

import android.support.annotation.NonNull;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Hex;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPrivateKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Class that handles encryption for local storage.
 * 
 * The protocol format is roughly:
 * 
 * 1) 16 byte random IV.
 * 2) AES-CBC(plaintext)
 * 3) HMAC-SHA1 of 1 and 2
 * 
 * @author Moxie Marlinspike
 */

public class MasterCipher {

  private static final String TAG = MasterCipher.class.getSimpleName();

  private final MasterSecret masterSecret;
  private final Cipher encryptingCipher;
  private final Cipher decryptingCipher;
  private final Mac hmac;
	
  public MasterCipher(MasterSecret masterSecret) {
    try {
      this.masterSecret = masterSecret;		
      this.encryptingCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      this.decryptingCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      this.hmac             = Mac.getInstance("HmacSHA1");
    } catch (NoSuchPaddingException | NoSuchAlgorithmException nspe) {
      throw new AssertionError(nspe);
    }
  }

  public byte[] encryptKey(ECPrivateKey privateKey) {
    return encryptBytes(privateKey.serialize());
  }

  public String encryptBody(@NonNull  String body)  {
    return encryptAndEncodeBytes(body.getBytes());
  }
	
  public String decryptBody(String body) throws InvalidMessageException {
    return new String(decodeAndDecryptBytes(body));
  }
	
  public ECPrivateKey decryptKey(byte[] key)
      throws org.whispersystems.libsignal.InvalidKeyException
  {
    try {
      return Curve.decodePrivatePoint(decryptBytes(key));
    } catch (InvalidMessageException ime) {
      throw new org.whispersystems.libsignal.InvalidKeyException(ime);
    }
  }
	
  public byte[] decryptBytes(@NonNull byte[] decodedBody) throws InvalidMessageException {
    try {
      Mac mac              = getMac(masterSecret.getMacKey());
      byte[] encryptedBody = verifyMacBody(mac, decodedBody);
			
      Cipher cipher        = getDecryptingCipher(masterSecret.getEncryptionKey(), encryptedBody);
      byte[] encrypted     = getDecryptedBody(cipher, encryptedBody);
			
      return encrypted;
    } catch (GeneralSecurityException ge) {
      throw new InvalidMessageException(ge);
    }
  }
	
  public byte[] encryptBytes(byte[] body) {
    try {
      Cipher cipher              = getEncryptingCipher(masterSecret.getEncryptionKey());
      Mac    mac                 = getMac(masterSecret.getMacKey());
		
      byte[] encryptedBody       = getEncryptedBody(cipher, body);
      byte[] encryptedAndMacBody = getMacBody(mac, encryptedBody);
		
      return encryptedAndMacBody;
    } catch (GeneralSecurityException ge) {
      Log.w("bodycipher", ge);
      return null;
    }	
		
  }
	
  public boolean verifyMacFor(String content, byte[] theirMac) {
    byte[] ourMac = getMacFor(content);
    Log.i(TAG, "Our Mac: " + Hex.toString(ourMac));
    Log.i(TAG, "Thr Mac: " + Hex.toString(theirMac));
    return Arrays.equals(ourMac, theirMac);
  }
	
  public byte[] getMacFor(String content) {
    Log.w(TAG, "Macing: " + content);
    try {
      Mac mac = getMac(masterSecret.getMacKey());
      return mac.doFinal(content.getBytes());
    } catch (GeneralSecurityException ike) {
      throw new AssertionError(ike);
    }
  }

  private byte[] decodeAndDecryptBytes(String body) throws InvalidMessageException {
    try {
      byte[] decodedBody = Base64.decode(body);
      return decryptBytes(decodedBody);
    } catch (IOException e) {
      throw new InvalidMessageException("Bad Base64 Encoding...", e);
    }
  }
	
  private String encryptAndEncodeBytes(@NonNull  byte[] bytes) {
    byte[] encryptedAndMacBody = encryptBytes(bytes);
    return Base64.encodeBytes(encryptedAndMacBody);
  }
	
  private byte[] verifyMacBody(@NonNull Mac hmac, @NonNull byte[] encryptedAndMac) throws InvalidMessageException {
    if (encryptedAndMac.length < hmac.getMacLength()) {
      throw new InvalidMessageException("length(encrypted body + MAC) < length(MAC)");
    }

    byte[] encrypted = new byte[encryptedAndMac.length - hmac.getMacLength()];
    System.arraycopy(encryptedAndMac, 0, encrypted, 0, encrypted.length);
		
    byte[] remoteMac = new byte[hmac.getMacLength()];
    System.arraycopy(encryptedAndMac, encryptedAndMac.length - remoteMac.length, remoteMac, 0, remoteMac.length);
		
    byte[] localMac  = hmac.doFinal(encrypted);
		
    if (!Arrays.equals(remoteMac, localMac))
      throw new InvalidMessageException("MAC doesen't match.");
		
    return encrypted;
  }
	
  private byte[] getDecryptedBody(Cipher cipher, byte[] encryptedBody) throws IllegalBlockSizeException, BadPaddingException {
    return cipher.doFinal(encryptedBody, cipher.getBlockSize(), encryptedBody.length - cipher.getBlockSize());
  }
	
  private byte[] getEncryptedBody(Cipher cipher, byte[] body) throws IllegalBlockSizeException, BadPaddingException {
    byte[] encrypted = cipher.doFinal(body);
    byte[] iv        = cipher.getIV();
		
    byte[] ivAndBody = new byte[iv.length + encrypted.length];
    System.arraycopy(iv, 0, ivAndBody, 0, iv.length);
    System.arraycopy(encrypted, 0, ivAndBody, iv.length, encrypted.length);
		
    return ivAndBody;
  }
	
  private Mac getMac(SecretKeySpec key) throws NoSuchAlgorithmException, InvalidKeyException {
    //		Mac hmac = Mac.getInstance("HmacSHA1");
    hmac.init(key);

    return hmac;
  }
	
  private byte[] getMacBody(Mac hmac, byte[] encryptedBody) {
    byte[] mac             = hmac.doFinal(encryptedBody);
    byte[] encryptedAndMac = new byte[encryptedBody.length + mac.length];

    System.arraycopy(encryptedBody, 0, encryptedAndMac, 0, encryptedBody.length);
    System.arraycopy(mac, 0, encryptedAndMac, encryptedBody.length, mac.length);
		
    return encryptedAndMac;
  }
	
  private Cipher getDecryptingCipher(SecretKeySpec key, byte[] encryptedBody) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException {
    //		Cipher cipher      = Cipher.getInstance("AES/CBC/PKCS5Padding");
    IvParameterSpec iv = new IvParameterSpec(encryptedBody, 0, decryptingCipher.getBlockSize());
    decryptingCipher.init(Cipher.DECRYPT_MODE, key, iv);
		
    return decryptingCipher;
  }
	
  private Cipher getEncryptingCipher(SecretKeySpec key) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
    //		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    encryptingCipher.init(Cipher.ENCRYPT_MODE, key);
		
    return encryptingCipher;
  }
	
}

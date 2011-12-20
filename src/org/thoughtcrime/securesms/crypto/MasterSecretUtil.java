/** 
 * Copyright (C) 2011 Whisper Systems
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.thoughtcrime.securesms.util.Base64;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

/**
 * Helper class for generating and securely storing a MasterSecret.
 * 
 * @author Moxie Marlinspike
 */

public class MasterSecretUtil {
	
  public static final String PREFERENCES_NAME        = "SecureSMS-Preferences";
  public static final String ASYMMETRIC_LOCAL_PUBLIC = "asymmetric_master_secret_public";
	
  public static MasterSecret changeMasterSecretPassphrase(Context context, String originalPassphrase, String newPassphrase) throws InvalidPassphraseException {
    try {
      MasterSecret masterSecret    = getMasterSecret(context, originalPassphrase);
      byte[] combinedSecrets       = combineSecrets(masterSecret.getEncryptionKey().getEncoded(), masterSecret.getMacKey().getEncoded());
      encryptWithPassphraseAndSave(context, combinedSecrets, newPassphrase);
			
      return masterSecret;
    } catch (GeneralSecurityException gse) {
      throw new AssertionError(gse);
    }
  }
	
  public static MasterSecret getMasterSecret(Context context, String passphrase) throws InvalidPassphraseException {
    try {
      byte[] encryptedAndMacdMasterSecret = retrieve(context, "master_secret");
      byte[] encryptedMasterSecret        = verifyMac(context, encryptedAndMacdMasterSecret, passphrase);
      byte[] combinedSecrets              = decryptWithPassphrase(context, encryptedMasterSecret, passphrase);
      byte[] encryptionSecret             = getEncryptionSecret(combinedSecrets);
      byte[] macSecret                    = getMacSecret(combinedSecrets);
			
      return new MasterSecret(new SecretKeySpec(encryptionSecret, "AES"),
			      new SecretKeySpec(macSecret, "HmacSHA1"));
    } catch (GeneralSecurityException e) {
      Log.w("keyutil", e);
      return null; //XXX
    } catch (IOException e) {
      Log.w("keyutil", e);
      return null; //XXX
    }
  }	
	
  public static AsymmetricMasterSecret getAsymmetricMasterSecret(Context context, MasterSecret masterSecret) {
    try {
      PublicKey publicKey               = new PublicKey(retrieve(context, ASYMMETRIC_LOCAL_PUBLIC));
      ECPrivateKeyParameters privateKey = null;
			
      if (masterSecret != null) {
        MasterCipher masterCipher = new MasterCipher(masterSecret);
        privateKey                = masterCipher.decryptKey(retrieve(context, "asymmetric_master_secret_private"));
      }
			
      return new AsymmetricMasterSecret(publicKey, privateKey);
    } catch (InvalidKeyException ike) {
      throw new AssertionError(ike);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
	
  public static AsymmetricMasterSecret generateAsymmetricMasterSecret(Context context, MasterSecret masterSecret) {
    MasterCipher masterCipher         = new MasterCipher(masterSecret);			
    AsymmetricCipherKeyPair ackp      = KeyUtil.generateKeyPair();
    KeyPair keyPair                   = new KeyPair(31337, ackp, masterSecret);
    PublicKey publicKey               = keyPair.getPublicKey();
    ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters)ackp.getPrivate();
		
    save(context, ASYMMETRIC_LOCAL_PUBLIC, publicKey.serialize());
    save(context, "asymmetric_master_secret_private", masterCipher.encryptKey(privateKey));
		
    return new AsymmetricMasterSecret(publicKey, privateKey);
  }
	
  public static MasterSecret generateMasterSecret(Context context, String passphrase) {
    try {
      byte[] encryptionSecret             = generateEncryptionSecret();
      byte[] macSecret                    = generateMacSecret();
      byte[] masterSecret                 = combineSecrets(encryptionSecret, macSecret);
			
      encryptWithPassphraseAndSave(context, masterSecret, passphrase);
			
      return new MasterSecret(new SecretKeySpec(encryptionSecret, "AES"), 
			      new SecretKeySpec(macSecret, "HmacSHA1"));
    } catch (GeneralSecurityException e) {
      Log.w("keyutil", e);
      return null;
    }
  }
	
  public static boolean hasAsymmericMasterSecret(Context context) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    return settings.contains(ASYMMETRIC_LOCAL_PUBLIC);		
  }
	
  private static void encryptWithPassphraseAndSave(Context context, byte[] masterSecret, String passphrase) throws GeneralSecurityException {
    byte[] encryptedMasterSecret        = encryptWithPassphrase(context, masterSecret, passphrase);
    byte[] encryptedAndMacdMasterSecret = macWithPassphrase(context, encryptedMasterSecret, passphrase);
		
    save(context, "master_secret", encryptedAndMacdMasterSecret);
    save(context, "passphrase_initialized", true);		
  }
	
  private static byte[] getEncryptionSecret(byte[] combinedSecrets) {
    byte[] encryptionSecret = new byte[16];
    System.arraycopy(combinedSecrets, 0, encryptionSecret, 0, encryptionSecret.length);
    return encryptionSecret;
  }
	
  private static byte[] getMacSecret(byte[] combinedSecrets) {
    byte[] macSecret = new byte[20];
    System.arraycopy(combinedSecrets, 16, macSecret, 0, macSecret.length);
    return macSecret;
  }
	
  private static byte[] combineSecrets(byte[] encryptionSecret, byte[] macSecret) {
    byte[] combinedSecret = new byte[encryptionSecret.length + macSecret.length];
    System.arraycopy(encryptionSecret, 0, combinedSecret, 0, encryptionSecret.length);
    System.arraycopy(macSecret, 0, combinedSecret, encryptionSecret.length, macSecret.length);
		
    return combinedSecret;
  }
	
  private static void save(Context context, String key, byte[] value) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    Editor editor              = settings.edit();
		
    editor.putString(key, Base64.encodeBytes(value));
    editor.commit();		
  }
	
  private static void save(Context context, String key, boolean value) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    Editor editor              = settings.edit();
		
    editor.putBoolean(key, value);
    editor.commit();				
  }
	
  private static byte[] retrieve(Context context, String key) throws IOException {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    String encodedValue        = settings.getString(key, "");
		
    if (encodedValue == "") return null;
    else                    return Base64.decode(encodedValue);
  }
	
  private static byte[] generateEncryptionSecret() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(128);

      SecretKey key = generator.generateKey();
      return key.getEncoded();
    } catch (NoSuchAlgorithmException ex) {
      Log.w("keyutil", ex);
      return null;
    }		
  }
	
  private static byte[] generateMacSecret() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("HmacSHA1");
      return generator.generateKey().getEncoded();
    } catch (NoSuchAlgorithmException e) {
      Log.w("keyutil", e);
      return null;
    }
  }
	
  private static byte[] generateSalt() throws NoSuchAlgorithmException {
    SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
    byte[] salt         = new byte[8];
    random.nextBytes(salt);
	    
    return salt;
  }
		
  private static SecretKey getKeyFromPassphrase(String passphrase, byte[] salt) throws GeneralSecurityException {
    PBEKeySpec keyspec    = new PBEKeySpec(passphrase.toCharArray(), salt, 100);
    SecretKeyFactory skf  = SecretKeyFactory.getInstance("PBEWITHSHA1AND128BITAES-CBC-BC");
    return skf.generateSecret(keyspec);
  }
	
  private static Cipher getCipherFromPassphrase(String passphrase, byte[] salt, int opMode) throws GeneralSecurityException {		
    SecretKey key              = getKeyFromPassphrase(passphrase, salt);
    Cipher cipher              = Cipher.getInstance(key.getAlgorithm());
    cipher.init(opMode, key, new PBEParameterSpec(salt, 100));
		
    return cipher;
  }
	
  private static byte[] encryptWithPassphrase(Context context, byte[] data, String passphrase) throws NoSuchAlgorithmException, GeneralSecurityException {
    byte[] encryptionSalt = generateSalt();		
    Cipher cipher         = getCipherFromPassphrase(passphrase, encryptionSalt, Cipher.ENCRYPT_MODE);
    byte[] cipherText     = cipher.doFinal(data);
		
    save(context, "encryption_salt", encryptionSalt);       
    return cipherText;
  }
	
  private static byte[] decryptWithPassphrase(Context context, byte[] data, String passphrase) throws GeneralSecurityException, IOException {
    byte[] encryptionSalt = retrieve(context, "encryption_salt");
    Cipher cipher         = getCipherFromPassphrase(passphrase, encryptionSalt, Cipher.DECRYPT_MODE);
    return cipher.doFinal(data);		
  }

  private static Mac getMacForPassphrase(String passphrase, byte[] salt) throws GeneralSecurityException {
    SecretKey key              = getKeyFromPassphrase(passphrase, salt);
    byte[] pbkdf2              = key.getEncoded();
    SecretKeySpec hmacKey      = new SecretKeySpec(pbkdf2, "HmacSHA1");
    Mac hmac                   = Mac.getInstance("HmacSHA1");
    hmac.init(hmacKey);

    return hmac;
  }
	
  private static byte[] verifyMac(Context context, byte[] encryptedAndMacdData, String passphrase) throws InvalidPassphraseException, GeneralSecurityException, IOException {
    byte[] macSalt  = retrieve(context, "mac_salt");
    Mac hmac        = getMacForPassphrase(passphrase, macSalt);		

    byte[] encryptedData = new byte[encryptedAndMacdData.length - hmac.getMacLength()];
    System.arraycopy(encryptedAndMacdData, 0, encryptedData, 0, encryptedData.length);
		
    byte[] givenMac      = new byte[hmac.getMacLength()];
    System.arraycopy(encryptedAndMacdData, encryptedAndMacdData.length-hmac.getMacLength(), givenMac, 0, givenMac.length);

    byte[] localMac      = hmac.doFinal(encryptedData);
		
    if (Arrays.equals(givenMac, localMac)) return encryptedData;
    else                                   throw new InvalidPassphraseException("MAC Error");		
  }
	
  private static byte[] macWithPassphrase(Context context, byte[] data, String passphrase) throws GeneralSecurityException {
    byte[] macSalt = generateSalt();				
    Mac hmac       = getMacForPassphrase(passphrase, macSalt);
    byte[] mac     = hmac.doFinal(data);
    byte[] result  = new byte[data.length + mac.length];
        
    System.arraycopy(data, 0, result, 0, data.length);
    System.arraycopy(mac,  0, result, data.length, mac.length);
        
    save(context, "mac_salt", macSalt);
    return result;
  }
}

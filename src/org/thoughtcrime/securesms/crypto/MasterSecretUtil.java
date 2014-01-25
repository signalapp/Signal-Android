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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.SystemClock;
import android.util.Log;

import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPrivateKey;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper class for generating and securely storing a MasterSecret.
 *
 * @author Moxie Marlinspike
 */

public class MasterSecretUtil {

  public static final String UNENCRYPTED_PASSPHRASE  = "unencrypted";
  public static final String PREFERENCES_NAME        = "SecureSMS-Preferences";

  private static final String ASYMMETRIC_LOCAL_PUBLIC_NIST  = "asymmetric_master_secret_public";
  private static final String ASYMMETRIC_LOCAL_PRIVATE_NIST = "asymmetric_master_secret_private";
  private static final String ASYMMETRIC_LOCAL_PUBLIC_DJB   = "asymmetric_master_secret_curve25519_public";
  private static final String ASYMMETRIC_LOCAL_PRIVATE_DJB  = "asymmetric_master_secret_curve25519_private";
  private static final String GENERATED_ITERATION_COUNT     = "key_iterations_count";
  private static final int DEFAULT_ITERATION_COUNT          = 99;

  public static MasterSecret changeMasterSecretPassphrase(Context context,
                                                          MasterSecret masterSecret,
                                                          String newPassphrase)
  {
    try {
      byte[] combinedSecrets = combineSecrets(masterSecret.getEncryptionKey().getEncoded(),
                                              masterSecret.getMacKey().getEncoded());

      encryptWithPassphraseAndSave(context, combinedSecrets, newPassphrase);

      return masterSecret;
    } catch (GeneralSecurityException gse) {
      throw new AssertionError(gse);
    }
  }

  public static MasterSecret changeMasterSecretPassphrase(Context context,
                                                          String originalPassphrase,
                                                          String newPassphrase)
      throws InvalidPassphraseException
  {
    MasterSecret masterSecret = getMasterSecret(context, originalPassphrase);
    changeMasterSecretPassphrase(context, masterSecret, newPassphrase);

    return masterSecret;
  }

  public static MasterSecret getMasterSecret(Context context, String passphrase)
      throws InvalidPassphraseException
  {
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

  public static AsymmetricMasterSecret getAsymmetricMasterSecret(Context context,
                                                                 MasterSecret masterSecret)
  {
    try {
      byte[] nistPublicBytes  = retrieve(context, ASYMMETRIC_LOCAL_PUBLIC_NIST);
      byte[] djbPublicBytes   = retrieve(context, ASYMMETRIC_LOCAL_PUBLIC_DJB);

      byte[] nistPrivateBytes = retrieve(context, ASYMMETRIC_LOCAL_PRIVATE_NIST);
      byte[] djbPrivateBytes  = retrieve(context, ASYMMETRIC_LOCAL_PRIVATE_DJB);

      ECPublicKey  nistPublicKey  = null;
      ECPublicKey  djbPublicKey   = null;

      ECPrivateKey nistPrivateKey = null;
      ECPrivateKey djbPrivateKey  = null;

      if (nistPublicBytes != null) {
        nistPublicKey = new PublicKey(nistPublicBytes, 0).getKey();
      }

      if (djbPublicBytes != null) {
        djbPublicKey = Curve.decodePoint(djbPublicBytes, 0);
      }

      if (masterSecret != null) {
        MasterCipher masterCipher = new MasterCipher(masterSecret);

        if (nistPrivateBytes != null) {
          nistPrivateKey = masterCipher.decryptKey(Curve.NIST_TYPE, nistPrivateBytes);
        }

        if (djbPrivateBytes != null) {
          djbPrivateKey = masterCipher.decryptKey(Curve.DJB_TYPE, djbPrivateBytes);
        }
      }

      return new AsymmetricMasterSecret(djbPublicKey, djbPrivateKey, nistPublicKey, nistPrivateKey);
    } catch (InvalidKeyException ike) {
      throw new AssertionError(ike);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static AsymmetricMasterSecret generateAsymmetricMasterSecret(Context context,
                                                                      MasterSecret masterSecret)
  {
    MasterCipher masterCipher = new MasterCipher(masterSecret);
    ECKeyPair    keyPair      = Curve.generateKeyPairForType(Curve.DJB_TYPE);

    save(context, ASYMMETRIC_LOCAL_PUBLIC_DJB, keyPair.getPublicKey().serialize());
    save(context, ASYMMETRIC_LOCAL_PRIVATE_DJB, masterCipher.encryptKey(keyPair.getPrivateKey()));

    return new AsymmetricMasterSecret(keyPair.getPublicKey(), keyPair.getPrivateKey(), null, null);
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

    return
        settings.contains(ASYMMETRIC_LOCAL_PUBLIC_NIST) ||
        settings.contains(ASYMMETRIC_LOCAL_PUBLIC_DJB);
  }

  public static boolean isPassphraseInitialized(Context context) {
    SharedPreferences preferences = context.getSharedPreferences(PREFERENCES_NAME, 0);
    return preferences.getBoolean("passphrase_initialized", false);
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

  private static void save(Context context, String key, int value) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    Editor editor              = settings.edit();

    editor.putInt(key, value);
    editor.commit();
  }

  private static byte[] retrieve(Context context, String key) throws IOException {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    String encodedValue        = settings.getString(key, "");

    if (Util.isEmpty(encodedValue)) return null;
    else                            return Base64.decode(encodedValue);
  }

  private static int getIterations(Context context, String passphrase, byte[] salt) {
    SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
    int iterations             = settings.getInt(GENERATED_ITERATION_COUNT, DEFAULT_ITERATION_COUNT);

    if (iterations == DEFAULT_ITERATION_COUNT) {
      iterations               = generateIterationTarget(passphrase, salt);
      save(context, GENERATED_ITERATION_COUNT, iterations);
    }

    return iterations;
  }

  private static int generateIterationTarget(String passphrase, byte[] salt) {
    int TARGET_ITERATION_TIME      = 500;   //ms
    int MINIMUM_ITERATION_COUNT    = 100;   //default for low-end devices
    int BASELINE_ITERATION_COUNT   = 10000; //baseline starting iteration count
    SecretKeyFactory skf           = null;

    long startTime                 = SystemClock.elapsedRealtime();
    PBEKeySpec keyspec             = new PBEKeySpec(passphrase.toCharArray(), salt, BASELINE_ITERATION_COUNT);
    try {
      skf                          = SecretKeyFactory.getInstance("PBEWITHSHA1AND128BITAES-CBC-BC");
      skf.generateSecret(keyspec);
    } catch (NoSuchAlgorithmException e) {
      //Do nothing.
    } catch (InvalidKeySpecException e) {
      //Do nothing.
    }
    long finishTime                = SystemClock.elapsedRealtime();
    int scaledIterationTarget      = (int)(((double)BASELINE_ITERATION_COUNT / (double)(finishTime - startTime)) * TARGET_ITERATION_TIME);

    if (scaledIterationTarget < MINIMUM_ITERATION_COUNT) return MINIMUM_ITERATION_COUNT;
    return scaledIterationTarget;
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
    byte[] salt         = new byte[16];
    random.nextBytes(salt);

    return salt;
  }

  private static SecretKey getKeyFromPassphrase(Context context, String passphrase, byte[] salt) throws GeneralSecurityException {
    PBEKeySpec keyspec    = new PBEKeySpec(passphrase.toCharArray(), salt, getIterations(context, passphrase, salt));
    SecretKeyFactory skf  = SecretKeyFactory.getInstance("PBEWITHSHA1AND128BITAES-CBC-BC");

    return skf.generateSecret(keyspec);
  }

  private static Cipher getCipherFromPassphrase(Context context, String passphrase, byte[] salt, int opMode) throws GeneralSecurityException {
    SecretKey key              = getKeyFromPassphrase(context, passphrase, salt);
    Cipher cipher              = Cipher.getInstance(key.getAlgorithm());
    cipher.init(opMode, key, new PBEParameterSpec(salt, getIterations(context, passphrase, salt)));

    return cipher;
  }

  private static byte[] encryptWithPassphrase(Context context, byte[] data, String passphrase) throws GeneralSecurityException {
    byte[] encryptionSalt = generateSalt();
    Cipher cipher         = getCipherFromPassphrase(context, passphrase, encryptionSalt, Cipher.ENCRYPT_MODE);
    byte[] cipherText     = cipher.doFinal(data);

    save(context, "encryption_salt", encryptionSalt);
    return cipherText;
  }

  private static byte[] decryptWithPassphrase(Context context, byte[] data, String passphrase) throws GeneralSecurityException, IOException {
    byte[] encryptionSalt = retrieve(context, "encryption_salt");
    Cipher cipher         = getCipherFromPassphrase(context, passphrase, encryptionSalt, Cipher.DECRYPT_MODE);
    return cipher.doFinal(data);
  }

  private static Mac getMacForPassphrase(Context context, String passphrase, byte[] salt) throws GeneralSecurityException {
    SecretKey key              = getKeyFromPassphrase(context, passphrase, salt);
    byte[] pbkdf2              = key.getEncoded();
    SecretKeySpec hmacKey      = new SecretKeySpec(pbkdf2, "HmacSHA1");
    Mac hmac                   = Mac.getInstance("HmacSHA1");
    hmac.init(hmacKey);

    return hmac;
  }

  private static byte[] verifyMac(Context context, byte[] encryptedAndMacdData, String passphrase) throws InvalidPassphraseException, GeneralSecurityException, IOException {
    byte[] macSalt  = retrieve(context, "mac_salt");
    Mac hmac        = getMacForPassphrase(context, passphrase, macSalt);

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
    Mac hmac       = getMacForPassphrase(context, passphrase, macSalt);
    byte[] mac     = hmac.doFinal(data);
    byte[] result  = new byte[data.length + mac.length];

    System.arraycopy(data, 0, result, 0, data.length);
    System.arraycopy(mac,  0, result, data.length, mac.length);

    save(context, "mac_salt", macSalt);
    return result;
  }
}

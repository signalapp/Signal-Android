/*
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
import android.os.Build;

import androidx.annotation.NonNull;

import org.session.libsignal.crypto.IdentityKey;
import org.session.libsignal.crypto.IdentityKeyPair;
import org.session.libsignal.crypto.ecc.Curve;
import org.session.libsignal.crypto.ecc.ECKeyPair;
import org.session.libsignal.crypto.ecc.ECPrivateKey;
import org.session.libsignal.crypto.ecc.ECPublicKey;
import org.session.libsignal.exceptions.InvalidKeyException;
import org.session.libsignal.utilities.Base64;

import java.io.IOException;

/**
 * Utility class for working with identity keys.
 * 
 * @author Moxie Marlinspike
 */
public class IdentityKeyUtil {

  private static final String MASTER_SECRET_UTIL_PREFERENCES_NAME = "SecureSMS-Preferences";

  @SuppressWarnings("unused")
  private static final String TAG = IdentityKeyUtil.class.getSimpleName();
  private static final String ENCRYPTED_SUFFIX = "_encrypted";

  public static final String IDENTITY_PUBLIC_KEY_PREF                    = "pref_identity_public_v3";
  public static final String IDENTITY_PRIVATE_KEY_PREF                   = "pref_identity_private_v3";
  public static final String ED25519_PUBLIC_KEY                          = "pref_ed25519_public_key";
  public static final String ED25519_SECRET_KEY                          = "pref_ed25519_secret_key";
  public static final String LOKI_SEED                                   = "loki_seed";
  public static final String HAS_MIGRATED_KEY                            = "has_migrated_keys";

  private static SharedPreferences getSharedPreferences(Context context) {
    return context.getSharedPreferences(MASTER_SECRET_UTIL_PREFERENCES_NAME, 0);
  }

  public static boolean hasIdentityKey(Context context) {
    SharedPreferences preferences = getSharedPreferences(context);

    return (preferences.contains(IDENTITY_PUBLIC_KEY_PREF) &&
            preferences.contains(IDENTITY_PRIVATE_KEY_PREF))
            || (preferences.contains(IDENTITY_PUBLIC_KEY_PREF+ENCRYPTED_SUFFIX) &&
            preferences.contains(IDENTITY_PRIVATE_KEY_PREF+ENCRYPTED_SUFFIX));
  }

  public static void checkUpdate(Context context) {
    SharedPreferences preferences = getSharedPreferences(context);
    // check if any keys are not migrated
    if (hasIdentityKey(context) && !preferences.getBoolean(HAS_MIGRATED_KEY, false)) {
      // this will retrieve and force upgrade if possible
      // retrieve will force upgrade if available
      retrieve(context,IDENTITY_PUBLIC_KEY_PREF);
      retrieve(context,IDENTITY_PRIVATE_KEY_PREF);
      retrieve(context,ED25519_PUBLIC_KEY);
      retrieve(context,ED25519_SECRET_KEY);
      retrieve(context,LOKI_SEED);
      preferences.edit().putBoolean(HAS_MIGRATED_KEY, true).apply();
    }
  }

  public static @NonNull IdentityKey getIdentityKey(@NonNull Context context) {
    if (!hasIdentityKey(context)) throw new AssertionError("There isn't one!");

    try {
      byte[] publicKeyBytes = Base64.decode(retrieve(context, IDENTITY_PUBLIC_KEY_PREF));
      return new IdentityKey(publicKeyBytes, 0);
    } catch (IOException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public static @NonNull IdentityKeyPair getIdentityKeyPair(@NonNull Context context) {
    if (!hasIdentityKey(context)) throw new AssertionError("There isn't one!");

    try {
      IdentityKey  publicKey  = getIdentityKey(context);
      ECPrivateKey privateKey = Curve.decodePrivatePoint(Base64.decode(retrieve(context, IDENTITY_PRIVATE_KEY_PREF)));

      return new IdentityKeyPair(publicKey, privateKey);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static void generateIdentityKeyPair(@NonNull Context context) {
    ECKeyPair keyPair = Curve.generateKeyPair();
    ECPublicKey publicKey = keyPair.getPublicKey();
    ECPrivateKey privateKey = keyPair.getPrivateKey();
    save(context, IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(publicKey.serialize()));
    save(context, IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(privateKey.serialize()));
  }

  public static String retrieve(Context context, String key) {
    SharedPreferences preferences = context.getSharedPreferences(MASTER_SECRET_UTIL_PREFERENCES_NAME, 0);

    String unencryptedSecret = preferences.getString(key, null);
    String encryptedSecret   = preferences.getString(key+ENCRYPTED_SUFFIX, null);

    if      (unencryptedSecret != null) return getUnencryptedSecret(key, unencryptedSecret, context);
    else if (encryptedSecret != null)   return getEncryptedSecret(encryptedSecret);

    return null;
  }

  private static String getUnencryptedSecret(String key, String unencryptedSecret, Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return unencryptedSecret;
    } else {
      KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(unencryptedSecret.getBytes());

      // save the encrypted suffix secret "key_encrypted"
      save(context,key+ENCRYPTED_SUFFIX,encryptedSecret.serialize());
      // delete the regular secret "key"
      delete(context,key);

      return unencryptedSecret;
    }
  }

  private static String getEncryptedSecret(String encryptedSecret) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      throw new AssertionError("OS downgrade not supported. KeyStore sealed data exists on platform < M!");
    } else {
      KeyStoreHelper.SealedData sealedData = KeyStoreHelper.SealedData.fromString(encryptedSecret);
      return new String(KeyStoreHelper.unseal(sealedData));
    }
  }


  public static void save(Context context, String key, String value) {
    SharedPreferences preferences   = context.getSharedPreferences(MASTER_SECRET_UTIL_PREFERENCES_NAME, 0);
    Editor preferencesEditor        = preferences.edit();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      boolean isEncryptedSuffix = key.endsWith(ENCRYPTED_SUFFIX);
      if (isEncryptedSuffix) {
        preferencesEditor.putString(key, value);
      } else {
        KeyStoreHelper.SealedData encryptedSecret = KeyStoreHelper.seal(value.getBytes());
        preferencesEditor.putString(key+ENCRYPTED_SUFFIX, encryptedSecret.serialize());
      }
    } else {
      preferencesEditor.putString(key, value);
    }
    if (!preferencesEditor.commit()) throw new AssertionError("failed to save identity key/value to shared preferences");
  }

  public static void delete(Context context, String key) {
    context.getSharedPreferences(MASTER_SECRET_UTIL_PREFERENCES_NAME, 0).edit().remove(key).commit();
  }
}

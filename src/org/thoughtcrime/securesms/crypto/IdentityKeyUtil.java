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
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.backup.BackupProtos;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility class for working with identity keys.
 * 
 * @author Moxie Marlinspike
 */

public class IdentityKeyUtil {

  @SuppressWarnings("unused")
  private static final String TAG = IdentityKeyUtil.class.getSimpleName();

  private static final String IDENTITY_PUBLIC_KEY_CIPHERTEXT_LEGACY_PREF  = "pref_identity_public_curve25519";
  private static final String IDENTITY_PRIVATE_KEY_CIPHERTEXT_LEGACY_PREF = "pref_identity_private_curve25519";

  private static final String IDENTITY_PUBLIC_KEY_PREF                    = "pref_identity_public_v3";
  private static final String IDENTITY_PRIVATE_KEY_PREF                   = "pref_identity_private_v3";

  public static boolean hasIdentityKey(Context context) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);

    return
        preferences.contains(IDENTITY_PUBLIC_KEY_PREF) &&
        preferences.contains(IDENTITY_PRIVATE_KEY_PREF);
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

  public static void generateIdentityKeys(Context context) {
    ECKeyPair    djbKeyPair     = Curve.generateKeyPair();
    IdentityKey  djbIdentityKey = new IdentityKey(djbKeyPair.getPublicKey());
    ECPrivateKey djbPrivateKey  = djbKeyPair.getPrivateKey();

    save(context, IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(djbIdentityKey.serialize()));
    save(context, IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(djbPrivateKey.serialize()));
  }

  public static void migrateIdentityKeys(@NonNull Context context,
                                         @NonNull MasterSecret masterSecret)
  {
    if (!hasIdentityKey(context)) {
      if (hasLegacyIdentityKeys(context)) {
        IdentityKeyPair legacyPair = getLegacyIdentityKeyPair(context, masterSecret);

        save(context, IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(legacyPair.getPublicKey().serialize()));
        save(context, IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(legacyPair.getPrivateKey().serialize()));

        delete(context, IDENTITY_PUBLIC_KEY_CIPHERTEXT_LEGACY_PREF);
        delete(context, IDENTITY_PRIVATE_KEY_CIPHERTEXT_LEGACY_PREF);
      } else {
        generateIdentityKeys(context);
      }
    }
  }

  public static List<BackupProtos.SharedPreference> getBackupRecord(@NonNull Context context) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);

    return new LinkedList<BackupProtos.SharedPreference>() {{
      add(BackupProtos.SharedPreference.newBuilder()
                                       .setFile(MasterSecretUtil.PREFERENCES_NAME)
                                       .setKey(IDENTITY_PUBLIC_KEY_PREF)
                                       .setValue(preferences.getString(IDENTITY_PUBLIC_KEY_PREF, null))
                                       .build());
      add(BackupProtos.SharedPreference.newBuilder()
                                       .setFile(MasterSecretUtil.PREFERENCES_NAME)
                                       .setKey(IDENTITY_PRIVATE_KEY_PREF)
                                       .setValue(preferences.getString(IDENTITY_PRIVATE_KEY_PREF, null))
                                       .build());
    }};
  }

  private static boolean hasLegacyIdentityKeys(Context context) {
    return
        retrieve(context, IDENTITY_PUBLIC_KEY_CIPHERTEXT_LEGACY_PREF) != null &&
        retrieve(context, IDENTITY_PRIVATE_KEY_CIPHERTEXT_LEGACY_PREF) != null;
  }

  private static IdentityKeyPair getLegacyIdentityKeyPair(@NonNull Context context,
                                                          @NonNull MasterSecret masterSecret)
  {
    try {
      MasterCipher masterCipher   = new MasterCipher(masterSecret);
      byte[]       publicKeyBytes = Base64.decode(retrieve(context, IDENTITY_PUBLIC_KEY_CIPHERTEXT_LEGACY_PREF));
      IdentityKey  identityKey    = new IdentityKey(publicKeyBytes, 0);
      ECPrivateKey privateKey     = masterCipher.decryptKey(Base64.decode(retrieve(context, IDENTITY_PRIVATE_KEY_CIPHERTEXT_LEGACY_PREF)));

      return new IdentityKeyPair(identityKey, privateKey);
    } catch (IOException | InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  private static String retrieve(Context context, String key) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    return preferences.getString(key, null);
  }

  private static void save(Context context, String key, String value) {
    SharedPreferences preferences   = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    Editor preferencesEditor        = preferences.edit();

    preferencesEditor.putString(key, value);
    if (!preferencesEditor.commit()) throw new AssertionError("failed to save identity key/value to shared preferences");
  }

  private static void delete(Context context, String key) {
    context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0).edit().remove(key).commit();
  }

}

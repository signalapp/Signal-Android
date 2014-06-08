/**
 * Copyright (C) 2011-2012 Whisper Systems
 * Copyright (C) 2013-2014 Open WhisperSystems
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
import android.util.Log;

import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPrivateKey;
import org.whispersystems.textsecure.util.Base64;

import java.io.IOException;

/**
 * Utility class for working with identity keys.
 * 
 * @author Moxie Marlinspike
 */

public class IdentityKeyUtil {

  private static final String IDENTITY_PUBLIC_KEY_DJB_PREF  = "pref_identity_public_curve25519";
  private static final String IDENTITY_PRIVATE_KEY_DJB_PREF = "pref_identity_private_curve25519";
	
  public static boolean hasIdentityKey(Context context) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);

    return
        preferences.contains(IDENTITY_PUBLIC_KEY_DJB_PREF) &&
        preferences.contains(IDENTITY_PRIVATE_KEY_DJB_PREF);
  }
	
  public static IdentityKey getIdentityKey(Context context) {
    if (!hasIdentityKey(context)) return null;
		
    try {
      byte[] publicKeyBytes = Base64.decode(retrieve(context, IDENTITY_PUBLIC_KEY_DJB_PREF));
      return new IdentityKey(publicKeyBytes, 0);
    } catch (IOException ioe) {
      Log.w("IdentityKeyUtil", ioe);
      return null;
    } catch (InvalidKeyException e) {
      Log.w("IdentityKeyUtil", e);
      return null;
    }
  }

  public static IdentityKeyPair getIdentityKeyPair(Context context,
                                                   MasterSecret masterSecret)
  {
    if (!hasIdentityKey(context))
      return null;

    try {
      MasterCipher masterCipher = new MasterCipher(masterSecret);
      IdentityKey  publicKey    = getIdentityKey(context);
      ECPrivateKey privateKey   = masterCipher.decryptKey(Base64.decode(retrieve(context, IDENTITY_PRIVATE_KEY_DJB_PREF)));

      return new IdentityKeyPair(publicKey, privateKey);
    } catch (IOException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public static void generateIdentityKeys(Context context, MasterSecret masterSecret) {
    ECKeyPair    djbKeyPair     = Curve.generateKeyPair(false);

    MasterCipher masterCipher   = new MasterCipher(masterSecret);
    IdentityKey  djbIdentityKey = new IdentityKey(djbKeyPair.getPublicKey());
    byte[]       djbPrivateKey  = masterCipher.encryptKey(djbKeyPair.getPrivateKey());

    save(context, IDENTITY_PUBLIC_KEY_DJB_PREF, Base64.encodeBytes(djbIdentityKey.serialize()));
    save(context, IDENTITY_PRIVATE_KEY_DJB_PREF, Base64.encodeBytes(djbPrivateKey));
  }

  public static boolean hasCurve25519IdentityKeys(Context context) {
    return
        retrieve(context, IDENTITY_PUBLIC_KEY_DJB_PREF) != null &&
        retrieve(context, IDENTITY_PRIVATE_KEY_DJB_PREF) != null;
  }

  public static void generateCurve25519IdentityKeys(Context context, MasterSecret masterSecret) {
    MasterCipher masterCipher    = new MasterCipher(masterSecret);
    ECKeyPair    djbKeyPair      = Curve.generateKeyPair(false);
    IdentityKey  djbIdentityKey  = new IdentityKey(djbKeyPair.getPublicKey());
    byte[]       djbPrivateKey   = masterCipher.encryptKey(djbKeyPair.getPrivateKey());

    save(context, IDENTITY_PUBLIC_KEY_DJB_PREF, Base64.encodeBytes(djbIdentityKey.serialize()));
    save(context, IDENTITY_PRIVATE_KEY_DJB_PREF, Base64.encodeBytes(djbPrivateKey));
  }

  public static String retrieve(Context context, String key) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    return preferences.getString(key, null);
  }
	
  public static void save(Context context, String key, String value) {
    SharedPreferences preferences   = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    Editor preferencesEditor        = preferences.edit();
		
    preferencesEditor.putString(key, value);
    preferencesEditor.commit();
  }
}

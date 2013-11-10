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

import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.kdf.DerivedSecrets;
import org.whispersystems.textsecure.crypto.kdf.HKDF;
import org.whispersystems.textsecure.crypto.kdf.KDF;
import org.whispersystems.textsecure.crypto.kdf.NKDF;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.util.Conversions;

import java.util.LinkedList;
import java.util.List;

public class SharedSecretCalculator {

  public static DerivedSecrets calculateSharedSecret(boolean isLowEnd, KeyPair localKeyPair,
                                                     int localKeyId,
                                                     IdentityKeyPair localIdentityKeyPair,
                                                     ECPublicKey remoteKey,
                                                     int remoteKeyId,
                                                     IdentityKey remoteIdentityKey)
      throws InvalidKeyException
  {
    Log.w("SharedSecretCalculator", "Calculating shared secret with 3DHE agreement...");
    KDF          kdf     = new HKDF();
    List<byte[]> results = new LinkedList<byte[]>();

    if (isSmaller(localKeyPair.getPublicKey().getKey(), remoteKey)) {
      results.add(Curve.calculateAgreement(remoteKey, localIdentityKeyPair.getPrivateKey()));
      results.add(Curve.calculateAgreement(remoteIdentityKey.getPublicKey(),
                                           localKeyPair.getPrivateKey()));
    } else {
      results.add(Curve.calculateAgreement(remoteIdentityKey.getPublicKey(),
                                           localKeyPair.getPrivateKey()));
      results.add(Curve.calculateAgreement(remoteKey, localIdentityKeyPair.getPrivateKey()));
    }

    results.add(Curve.calculateAgreement(remoteKey, localKeyPair.getPrivateKey()));

    return kdf.deriveSecrets(results, isLowEnd, getInfo(localKeyId, remoteKeyId));
  }

  public static DerivedSecrets calculateSharedSecret(int messageVersion, boolean isLowEnd,
                                                     KeyPair localKeyPair, int localKeyId,
                                                     ECPublicKey remoteKey, int remoteKeyId)
      throws InvalidKeyException
  {
    Log.w("SharedSecretCalculator", "Calculating shared secret with standard agreement...");
    KDF kdf;

    if (messageVersion >= CiphertextMessage.DHE3_INTRODUCED_VERSION) kdf = new HKDF();
    else                                                             kdf = new NKDF();

    Log.w("SharedSecretCalculator", "Using kdf:  " + kdf);

    List<byte[]> results = new LinkedList<byte[]>();
    results.add(Curve.calculateAgreement(remoteKey, localKeyPair.getPrivateKey()));

    return kdf.deriveSecrets(results, isLowEnd, getInfo(localKeyId, remoteKeyId));
  }

  private static byte[] getInfo(int localKeyId, int remoteKeyId) {
    byte[] info = new byte[3 * 2];

    if (localKeyId < remoteKeyId) {
      Conversions.mediumToByteArray(info, 0, localKeyId);
      Conversions.mediumToByteArray(info, 3, remoteKeyId);
    } else {
      Conversions.mediumToByteArray(info, 0, remoteKeyId);
      Conversions.mediumToByteArray(info, 3, localKeyId);
    }

    return info;
  }

  private static boolean isSmaller(ECPublicKey localPublic,
                                   ECPublicKey remotePublic)
  {
    return localPublic.compareTo(remotePublic) < 0;
  }

}

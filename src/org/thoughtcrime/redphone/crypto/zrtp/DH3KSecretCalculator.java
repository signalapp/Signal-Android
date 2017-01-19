/*
 * Copyright (C) 2012 Whisper Systems
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

package org.thoughtcrime.redphone.crypto.zrtp;

import android.util.Log;

import org.thoughtcrime.redphone.util.Conversions;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHPublicKeySpec;

/**
 * An instance of SecretCalculator that handles DH3K KA.
 *
 * @author Moxie Marlinspike
 *
 */
public class DH3KSecretCalculator extends SecretCalculator {

  @Override
  public byte[] calculateKeyAgreement(KeyPair localKey, byte[] publicKeyBytes) {
    try {
      Log.w("SecretCalculator", "Calculating DH secret...");
      DHPublicKeySpec keySpec = new DHPublicKeySpec(Conversions.byteArrayToBigInteger(publicKeyBytes),
                                                    ZRTPSocket.PRIME, ZRTPSocket.GENERATOR);
      KeyFactory keyFactory   = KeyFactory.getInstance("DH");
      PublicKey publicKey     = keyFactory.generatePublic(keySpec);

      KeyAgreement agreement = KeyAgreement.getInstance("DH");
      agreement.init(localKey.getPrivate());
      agreement.doPhase(publicKey, true);

      return agreement.generateSecret();
    } catch (NoSuchAlgorithmException e) {
      Log.w("SecretCalculator", e);
      throw new IllegalArgumentException(e);
    } catch (InvalidKeySpecException e) {
      Log.w("SecretCalculator", e);
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyException e) {
      Log.w("SecretCalculator", e);
      throw new IllegalArgumentException(e);
    }
  }

}

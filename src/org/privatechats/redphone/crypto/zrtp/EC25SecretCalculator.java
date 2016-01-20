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

package org.privatechats.redphone.crypto.zrtp;

import android.util.Log;

import org.spongycastle.jce.ECNamedCurveTable;
import org.spongycastle.jce.spec.ECParameterSpec;
import org.spongycastle.jce.spec.ECPublicKeySpec;
import org.spongycastle.math.ec.ECPoint;
import org.privatechats.redphone.util.Conversions;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.KeyAgreement;

/**
 * An instance of SecretCalculator that will do KA for EC25.
 *
 * @author Moxie Marlinspike
 *
 */

public class EC25SecretCalculator extends SecretCalculator {

  @Override
  public byte[] calculateKeyAgreement(KeyPair localKey, byte[] publicKeyBytes) {
    Log.w("EC25SecretCalculator", "Calculating EC25 Secret...");
    try {
      byte[] x = new byte[32];
      byte[] y = new byte[32];

      System.arraycopy(publicKeyBytes, 0, x, 0, x.length);
      System.arraycopy(publicKeyBytes, x.length, y, 0, y.length);

      ECParameterSpec params = ECNamedCurveTable.getParameterSpec("secp256r1");
      ECPoint point          = params.getCurve().createPoint(Conversions.byteArrayToBigInteger(x),
                                                             Conversions.byteArrayToBigInteger(y),
                                                             false);

      ECPublicKeySpec keySpec = new ECPublicKeySpec(point, params);
      KeyFactory keyFactory   = KeyFactory.getInstance("ECDH", "SC");
      PublicKey publicKey     = keyFactory.generatePublic(keySpec);

      KeyAgreement agreement = KeyAgreement.getInstance("ECDH", "SC");
      agreement.init(localKey.getPrivate());
      agreement.doPhase(publicKey, true);

      return agreement.generateSecret();
    } catch (NoSuchProviderException nspe) {
      throw new AssertionError(nspe);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeySpecException e) {
      throw new IllegalArgumentException(e);
    }
  }
}

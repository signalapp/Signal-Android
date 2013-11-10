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

package org.whispersystems.textsecure.crypto.ecc;

import android.util.Log;

import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.agreement.ECDHBasicAgreement;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.math.ec.ECCurve;
import org.spongycastle.math.ec.ECFieldElement;
import org.spongycastle.math.ec.ECPoint;
import org.whispersystems.textsecure.crypto.InvalidKeyException;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class CurveP256 {

  private static final BigInteger q  = new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16);
  private static final BigInteger a  = new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC", 16);
  private static final BigInteger b  = new BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16);
  private static final BigInteger n  = new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);

  private static final ECFieldElement x  = new ECFieldElement.Fp(q, new BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16));
  private static final ECFieldElement y  = new ECFieldElement.Fp(q, new BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16));

  private static final ECCurve curve = new ECCurve.Fp(q, a, b);
  private static final ECPoint g     = new ECPoint.Fp(curve, x, y, true);

  private static final ECDomainParameters domainParameters = new ECDomainParameters(curve, g, n);

  public static final int P256_POINT_SIZE = 33;

  static byte[] encodePoint(ECPoint point) {
    synchronized (curve) {
      return point.getEncoded();
    }
  }

  static ECPublicKey decodePoint(byte[] encoded, int offset)
      throws InvalidKeyException
  {
    byte[] pointBytes = new byte[P256_POINT_SIZE];
    System.arraycopy(encoded, offset, pointBytes, 0, pointBytes.length);

    synchronized (curve) {
      ECPoint Q;

      try {
        Q = curve.decodePoint(pointBytes);
      } catch (RuntimeException re) {
        throw new InvalidKeyException(re);
      }

      return new NistECPublicKey(new ECPublicKeyParameters(Q, domainParameters));
    }
  }

  static ECPrivateKey decodePrivatePoint(byte[] encoded) {
    BigInteger d = new BigInteger(encoded);
    return new NistECPrivateKey(new ECPrivateKeyParameters(d, domainParameters));
  }

  static byte[] calculateAgreement(ECPublicKey publicKey, ECPrivateKey privateKey) {
    ECDHBasicAgreement agreement = new ECDHBasicAgreement();
    agreement.init(((NistECPrivateKey)privateKey).getParameters());

    synchronized (curve) {
      return agreement.calculateAgreement(((NistECPublicKey)publicKey).getParameters()).toByteArray();
    }
  }

  public static ECKeyPair generateKeyPair() {
    try {
      synchronized (curve) {
        ECKeyGenerationParameters keyParamters = new ECKeyGenerationParameters(domainParameters, SecureRandom.getInstance("SHA1PRNG"));
        ECKeyPairGenerator generator           = new ECKeyPairGenerator();
        generator.init(keyParamters);

        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();
        keyPair = cloneKeyPairWithPointCompression(keyPair);

        return new ECKeyPair(new NistECPublicKey((ECPublicKeyParameters)keyPair.getPublic()),
                             new NistECPrivateKey((ECPrivateKeyParameters)keyPair.getPrivate()));
      }
    } catch (NoSuchAlgorithmException nsae) {
      Log.w("CurveP256", nsae);
      throw new AssertionError(nsae);
    }
  }

  // This is dumb, but the ECPublicKeys that the generator makes by default don't have point compression
  // turned on, and there's no setter.  Great.
  private static AsymmetricCipherKeyPair cloneKeyPairWithPointCompression(AsymmetricCipherKeyPair keyPair) {
    ECPublicKeyParameters publicKey = (ECPublicKeyParameters)keyPair.getPublic();
    ECPoint q                       = publicKey.getQ();

    return new AsymmetricCipherKeyPair(new ECPublicKeyParameters(new ECPoint.Fp(q.getCurve(), q.getX(), q.getY(), true),
                                                                 publicKey.getParameters()), keyPair.getPrivate());
  }
}

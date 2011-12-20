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

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;
import org.thoughtcrime.securesms.database.LocalKeyRecord;
import org.thoughtcrime.securesms.database.RemoteKeyRecord;
import org.thoughtcrime.securesms.database.SessionRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

import android.content.Context;
import android.util.Log;

/**
 * Helper class for generating key pairs and calculating ECDH agreements.
 * 
 * @author Moxie Marlinspike
 */

public class KeyUtil {

  public  static final BigInteger q  = new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16);	
  private static final BigInteger a  = new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC", 16);
  private static final BigInteger b  = new BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16);
  private static final BigInteger n  = new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);
	
  private static final ECFieldElement x  = new ECFieldElement.Fp(q, new BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16));
  private static final ECFieldElement y  = new ECFieldElement.Fp(q, new BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16));
	
  private static final ECCurve curve = new ECCurve.Fp(q, a, b);
  private static final ECPoint g     = new ECPoint.Fp(curve, x, y, true);
	
  public static final ECDomainParameters domainParameters = new ECDomainParameters(curve, g, n);
		
  public static ECPoint decodePoint(byte[] pointBytes) {
    synchronized (curve) {
      return curve.decodePoint(pointBytes);
    }
  }
	
  public static byte[] encodePoint(ECPoint point) {
    synchronized (curve) {
      return point.getEncoded();
    }
  }
	
  public static BigInteger calculateAgreement(ECDHBasicAgreement agreement, ECPublicKeyParameters remoteKey) {
    synchronized (curve) {
      return agreement.calculateAgreement(remoteKey);
    }
  }
	
  public static void abortSessionFor(Context context, Recipient recipient) {
    //XXX Obviously we should probably do something more thorough here eventually.
    Log.w("KeyUtil", "Aborting session, deleting keys...");
    LocalKeyRecord.delete(context, recipient);
    RemoteKeyRecord.delete(context, recipient);
    SessionRecord.delete(context, recipient);
  }
	
  public static boolean isSessionFor(Context context, Recipient recipient) {
    Log.w("KeyUtil", "Checking session...");
    return 
      (LocalKeyRecord.hasRecord(context, recipient))  &&
      (RemoteKeyRecord.hasRecord(context, recipient)) &&
      (SessionRecord.hasSession(context, recipient));
  }
	
  public static LocalKeyRecord initializeRecordFor(Recipient recipient, Context context, MasterSecret masterSecret) {
    Log.w("KeyUtil", "Initializing local key pairs...");
    try {
      SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
      int initialId             = secureRandom.nextInt(4094) + 1;
						
      KeyPair currentPair       = new KeyPair(initialId, KeyUtil.generateKeyPair(), masterSecret);
      KeyPair nextPair          = new KeyPair(initialId + 1, KeyUtil.generateKeyPair(), masterSecret);
      LocalKeyRecord record     = new LocalKeyRecord(context, masterSecret, recipient);
			
      record.setCurrentKeyPair(currentPair);
      record.setNextKeyPair(nextPair);
      record.save();
			
      return record;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static AsymmetricCipherKeyPair generateKeyPair() {
    try {
      synchronized (curve) {
        ECKeyGenerationParameters keyParamters = new ECKeyGenerationParameters(domainParameters, SecureRandom.getInstance("SHA1PRNG"));
        ECKeyPairGenerator generator           = new ECKeyPairGenerator();
        generator.init(keyParamters);
				
        AsymmetricCipherKeyPair keyPair        = generator.generateKeyPair();
				
        return cloneKeyPairWithPointCompression(keyPair);
      }
    } catch (NoSuchAlgorithmException nsae) {
      Log.w("keyutil", nsae);
      return null;
    }
  }
	
  // This is dumb, but the ECPublicKeys that the generator makes by default don't have point compression
  // turned on, and there's no setter.  Great.
  private static AsymmetricCipherKeyPair cloneKeyPairWithPointCompression(AsymmetricCipherKeyPair keyPair) {
    ECPublicKeyParameters publicKey = (ECPublicKeyParameters)keyPair.getPublic();
    ECPoint q                       = publicKey.getQ();
		
    return new AsymmetricCipherKeyPair(new ECPublicKeyParameters(new ECPoint.Fp(q.getCurve(), q.getX(), q.getY(), true), publicKey.getParameters()), keyPair.getPrivate());
  }
	
}

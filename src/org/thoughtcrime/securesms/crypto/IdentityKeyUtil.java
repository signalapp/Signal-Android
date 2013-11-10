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
import android.util.Log;

import org.spongycastle.asn1.ASN1Encoding;
import org.spongycastle.asn1.ASN1Primitive;
import org.spongycastle.asn1.ASN1Sequence;
import org.spongycastle.asn1.DERInteger;
import org.spongycastle.asn1.DERSequence;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.PublicKey;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPrivateKey;
import org.whispersystems.textsecure.crypto.ecc.NistECPrivateKey;
import org.whispersystems.textsecure.crypto.ecc.NistECPublicKey;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.Conversions;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for working with identity keys.
 * 
 * @author Moxie Marlinspike
 */

public class IdentityKeyUtil {

  private static final String IDENTITY_PUBLIC_KEY_NIST_PREF  = "pref_identity_public";
  private static final String IDENTITY_PRIVATE_KEY_NIST_PREF = "pref_identity_private";

  private static final String IDENTITY_PUBLIC_KEY_DJB_PREF  = "pref_identity_public_curve25519";
  private static final String IDENTITY_PRIVATE_KEY_DJB_PREF = "pref_identity_private_curve25519";
	
  public static boolean hasIdentityKey(Context context, int type) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);

    if (type == Curve.DJB_TYPE) {
      return
          preferences.contains(IDENTITY_PUBLIC_KEY_DJB_PREF) &&
          preferences.contains(IDENTITY_PRIVATE_KEY_DJB_PREF);
    } else if (type == Curve.NIST_TYPE) {
      return
          preferences.contains(IDENTITY_PUBLIC_KEY_NIST_PREF) &&
          preferences.contains(IDENTITY_PRIVATE_KEY_NIST_PREF);
    }

    return false;
  }
	
  public static IdentityKey getIdentityKey(Context context, int type) {
    if (!hasIdentityKey(context, type)) return null;
		
    try {
      String key;

      if      (type == Curve.DJB_TYPE)  key = IDENTITY_PUBLIC_KEY_DJB_PREF;
      else if (type == Curve.NIST_TYPE) key = IDENTITY_PUBLIC_KEY_NIST_PREF;
      else                              return null;

      byte[] publicKeyBytes = Base64.decode(retrieve(context, key));
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
                                                   MasterSecret masterSecret,
                                                   int type)
  {
    if (!hasIdentityKey(context, type))
      return null;

    try {
      String key;

      if      (type == Curve.DJB_TYPE)  key = IDENTITY_PRIVATE_KEY_DJB_PREF;
      else if (type == Curve.NIST_TYPE) key = IDENTITY_PRIVATE_KEY_NIST_PREF;
      else                              return null;

      MasterCipher masterCipher = new MasterCipher(masterSecret);
      IdentityKey  publicKey    = getIdentityKey(context, type);
      ECPrivateKey privateKey   = masterCipher.decryptKey(type, Base64.decode(retrieve(context, key)));

      return new IdentityKeyPair(publicKey, privateKey);
    } catch (IOException e) {
      throw new AssertionError(e);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }
	
  public static String getFingerprint(Context context, int type) {
    if (!hasIdentityKey(context, type)) return null;
		
    IdentityKey identityKey = getIdentityKey(context, type);
		
    if (identityKey == null) return null;
    else                     return identityKey.getFingerprint();
  }
	
  public static void generateIdentityKeys(Context context, MasterSecret masterSecret) {
    ECKeyPair    nistKeyPair     = Curve.generateKeyPairForType(Curve.NIST_TYPE);
    ECKeyPair    djbKeyPair      = Curve.generateKeyPairForType(Curve.DJB_TYPE);

    MasterCipher masterCipher    = new MasterCipher(masterSecret);
    IdentityKey  nistIdentityKey = new IdentityKey(nistKeyPair.getPublicKey());
    IdentityKey  djbIdentityKey  = new IdentityKey(djbKeyPair.getPublicKey());

    byte[]       nistPrivateKey  = masterCipher.encryptKey(nistKeyPair.getPrivateKey());
    byte[]       djbPrivateKey   = masterCipher.encryptKey(djbKeyPair.getPrivateKey());

    save(context, IDENTITY_PUBLIC_KEY_NIST_PREF, Base64.encodeBytes(nistIdentityKey.serialize()));
    save(context, IDENTITY_PUBLIC_KEY_DJB_PREF, Base64.encodeBytes(djbIdentityKey.serialize()));

    save(context, IDENTITY_PRIVATE_KEY_NIST_PREF, Base64.encodeBytes(nistPrivateKey));
    save(context, IDENTITY_PRIVATE_KEY_DJB_PREF, Base64.encodeBytes(djbPrivateKey));
  }

  public static boolean hasCurve25519IdentityKeys(Context context) {
    return
        retrieve(context, IDENTITY_PUBLIC_KEY_DJB_PREF) != null &&
        retrieve(context, IDENTITY_PRIVATE_KEY_DJB_PREF) != null;
  }

  public static void generateCurve25519IdentityKeys(Context context, MasterSecret masterSecret) {
    MasterCipher masterCipher    = new MasterCipher(masterSecret);
    ECKeyPair    djbKeyPair      = Curve.generateKeyPairForType(Curve.DJB_TYPE);
    IdentityKey  djbIdentityKey  = new IdentityKey(djbKeyPair.getPublicKey());
    byte[]       djbPrivateKey   = masterCipher.encryptKey(djbKeyPair.getPrivateKey());

    save(context, IDENTITY_PUBLIC_KEY_DJB_PREF, Base64.encodeBytes(djbIdentityKey.serialize()));
    save(context, IDENTITY_PRIVATE_KEY_DJB_PREF, Base64.encodeBytes(djbPrivateKey));
  }
	
  public static IdentityKey verifySignedKeyExchange(byte[] keyExchangeBytes)
      throws InvalidKeyException
  {
    try {
      byte[] messageBytes   = new byte[1 + PublicKey.KEY_SIZE];
      System.arraycopy(keyExchangeBytes, 0, messageBytes, 0, messageBytes.length);
			
      byte[] publicKeyBytes = new byte[IdentityKey.SIZE];
      System.arraycopy(keyExchangeBytes, messageBytes.length, publicKeyBytes, 0, publicKeyBytes.length);
			
      int signatureLength   = Conversions.byteArrayToShort(keyExchangeBytes, messageBytes.length + publicKeyBytes.length);
      byte[] signatureBytes = new byte[signatureLength];		
      System.arraycopy(keyExchangeBytes, messageBytes.length + publicKeyBytes.length + 2, signatureBytes, 0, signatureBytes.length);
			
      byte[] messageHash      = getMessageHash(messageBytes, publicKeyBytes);
      IdentityKey identityKey = new IdentityKey(publicKeyBytes, 0);
      ECDSASigner verifier    = new ECDSASigner();

      if (identityKey.getPublicKey().getType() != Curve.NIST_TYPE) {
        throw new InvalidKeyException("Signing only support on P256 keys!");
      }

      verifier.init(false, ((NistECPublicKey)identityKey.getPublicKey()).getParameters());
			
      ASN1Sequence sequence          = (ASN1Sequence) ASN1Primitive.fromByteArray(signatureBytes);
      BigInteger[] signatureIntegers = new BigInteger[]{
          ((DERInteger)sequence.getObjectAt(0)).getValue(),
          ((DERInteger)sequence.getObjectAt(1)).getValue()
      };
		
      if (!verifier.verifySignature(messageHash, signatureIntegers[0], signatureIntegers[1]))
        throw new InvalidKeyException("Invalid signature!");
      else
        return identityKey;
				
    } catch (IOException ioe) {
      throw new InvalidKeyException(ioe);
    }
		
  }

  public static byte[] getSignedKeyExchange(Context context, MasterSecret masterSecret,
                                            byte[] keyExchangeBytes)
  {
    try {
      MasterCipher masterCipher    = new MasterCipher(masterSecret);
      byte[]       publicKeyBytes  = getIdentityKey(context, Curve.NIST_TYPE).serialize();
      byte[]       messageHash     = getMessageHash(keyExchangeBytes, publicKeyBytes);
      byte[]       privateKeyBytes = Base64.decode(retrieve(context, IDENTITY_PRIVATE_KEY_NIST_PREF));
      ECPrivateKey privateKey      = masterCipher.decryptKey(Curve.NIST_TYPE, privateKeyBytes);
      ECDSASigner  signer          = new ECDSASigner();
			
      signer.init(true, ((NistECPrivateKey)privateKey).getParameters());
			
      BigInteger[] messageSignatureInts    = signer.generateSignature(messageHash);
      DERInteger[] derMessageSignatureInts = new DERInteger[]{ new DERInteger(messageSignatureInts[0]), new DERInteger(messageSignatureInts[1]) };
      byte[] messageSignatureBytes         = new DERSequence(derMessageSignatureInts).getEncoded(ASN1Encoding.DER);
      byte[] messageSignature              = new byte[2 + messageSignatureBytes.length];
	        
      Conversions.shortToByteArray(messageSignature, 0, messageSignatureBytes.length);	        
      System.arraycopy(messageSignatureBytes, 0, messageSignature, 2, messageSignatureBytes.length);

      return Util.combine(keyExchangeBytes, publicKeyBytes, messageSignature);
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }
	
  private static byte[] getMessageHash(byte[] messageBytes, byte[] publicKeyBytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA1");
      md.update(messageBytes);
      return md.digest(publicKeyBytes);
    } catch (NoSuchAlgorithmException nsae) {
      throw new AssertionError(nsae);
    }
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

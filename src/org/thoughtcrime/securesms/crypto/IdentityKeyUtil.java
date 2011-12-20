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

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.thoughtcrime.bouncycastle.asn1.ASN1Encodable;
import org.thoughtcrime.bouncycastle.asn1.ASN1Object;
import org.thoughtcrime.bouncycastle.asn1.ASN1Sequence;
import org.thoughtcrime.bouncycastle.asn1.DERInteger;
import org.thoughtcrime.bouncycastle.asn1.DERSequence;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Combiner;
import org.thoughtcrime.securesms.util.Conversions;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

/**
 * Utility class for working with identity keys.
 * 
 * @author Moxie Marlinspike
 */

public class IdentityKeyUtil {

  private static final String IDENTITY_PUBLIC_KEY_PREF  = "pref_identity_public";
  private static final String IDENTITY_PRIVATE_KEY_PREF = "pref_identity_private";
	
  public static boolean hasIdentityKey(Context context) {
    SharedPreferences preferences = context.getSharedPreferences(MasterSecretUtil.PREFERENCES_NAME, 0);
    return preferences.contains(IDENTITY_PUBLIC_KEY_PREF) && preferences.contains(IDENTITY_PRIVATE_KEY_PREF);
  }
	
  public static IdentityKey getIdentityKey(Context context) {
    if (!hasIdentityKey(context)) return null;
		
    try {
      byte[] publicKeyBytes = Base64.decode(retrieve(context, IDENTITY_PUBLIC_KEY_PREF));
      return new IdentityKey(publicKeyBytes, 0);
    } catch (IOException ioe) {
      Log.w("IdentityKeyUtil", ioe);
      return null;
    } catch (InvalidKeyException e) {
      Log.w("IdentityKeyUtil", e);
      return null;
    }
  }
	
  public static String getFingerprint(Context context) {
    if (!hasIdentityKey(context)) return null;
		
    IdentityKey identityKey = getIdentityKey(context);
		
    if (identityKey == null) return null;
    else                     return identityKey.getFingerprint();
  }
	
  public static void generateIdentityKeys(Context context, MasterSecret masterSecret) {
    MasterCipher masterCipher       = new MasterCipher(masterSecret);
    AsymmetricCipherKeyPair keyPair = KeyUtil.generateKeyPair();
    IdentityKey identityKey         = new IdentityKey((ECPublicKeyParameters)keyPair.getPublic());
    byte[] serializedPublicKey      = identityKey.serialize();
    byte[] serializedPrivateKey     = masterCipher.encryptKey((ECPrivateKeyParameters)keyPair.getPrivate());
		
    save(context, IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(serializedPublicKey));
    save(context, IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(serializedPrivateKey));
  }
	
  public static IdentityKey verifySignedKeyExchange(byte[] keyExchangeBytes) throws InvalidKeyException {
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
			
      verifier.init(false, identityKey.getPublicKeyParameters());
			
      ASN1Sequence sequence          = (ASN1Sequence)ASN1Object.fromByteArray(signatureBytes);
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

  public static byte[] getSignedKeyExchange(Context context, MasterSecret masterSecret, byte[] keyExchangeBytes) {
    try {
	
      MasterCipher masterCipher         = new MasterCipher(masterSecret);
      byte[] publicKeyBytes             = getIdentityKey(context).serialize();
      byte[] messageHash                = getMessageHash(keyExchangeBytes, publicKeyBytes);
      byte[] privateKeyBytes            = Base64.decode(retrieve(context, IDENTITY_PRIVATE_KEY_PREF));
      ECPrivateKeyParameters privateKey = masterCipher.decryptKey(privateKeyBytes);
      ECDSASigner signer                = new ECDSASigner();
			
      signer.init(true, privateKey);
			
      BigInteger[] messageSignatureInts    = signer.generateSignature(messageHash);
      DERInteger[] derMessageSignatureInts = new DERInteger[]{ new DERInteger(messageSignatureInts[0]), new DERInteger(messageSignatureInts[1]) };
      byte[] messageSignatureBytes         = new DERSequence(derMessageSignatureInts).getEncoded(ASN1Encodable.DER);
      byte[] messageSignature              = new byte[2 + messageSignatureBytes.length];
	        
      Conversions.shortToByteArray(messageSignature, 0, messageSignatureBytes.length);	        
      System.arraycopy(messageSignatureBytes, 0, messageSignature, 2, messageSignatureBytes.length);
	        
      byte[] combined = Combiner.combine(keyExchangeBytes, publicKeyBytes, messageSignature);
 	        
      return combined;
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
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

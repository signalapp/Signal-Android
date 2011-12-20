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
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Conversions;
import org.thoughtcrime.securesms.util.InvalidMessageException;

/**
 * This class is used to asymmetricly encrypt local data.  This is used in the case
 * where TextSecure receives an SMS, but the user's local encryption passphrase is
 * not cached (either because of a timeout, or because it hasn't yet been entered).
 * 
 * In this case, we have access to the public key of a local keypair.  We encrypt
 * the message with this, and put it into the DB.  When the user enters their passphrase,
 * we can get access to the private key of the local keypair, decrypt the message, and
 * replace it into the DB with symmetric encryption.
 * 
 * The encryption protocol is as follows:
 * 
 * 1) Generate an ephemeral keypair.
 * 2) Do ECDH with the public key of the local durable keypair.
 * 3) Do KMF with the ECDH result to obtain a master secret.
 * 4) Encrypt the message with that master secret.
 * 
 * @author Moxie Marlinspike
 *
 */
public class AsymmetricMasterCipher {

  private final AsymmetricMasterSecret asymmetricMasterSecret;
	
  public AsymmetricMasterCipher(AsymmetricMasterSecret asymmetricMasterSecret) {
    this.asymmetricMasterSecret = asymmetricMasterSecret;
  }
	
  public String decryptBody(String body) throws IOException, org.thoughtcrime.securesms.crypto.InvalidMessageException {
    try {
      byte[] combined           = Base64.decode(body);
      PublicKey theirPublicKey  = new PublicKey(combined, 0);
      byte[] encryptedBodyBytes = new byte[combined.length - PublicKey.KEY_SIZE];
      System.arraycopy(combined, PublicKey.KEY_SIZE, encryptedBodyBytes, 0, encryptedBodyBytes.length);
			
      ECDHBasicAgreement agreement = new ECDHBasicAgreement();
      agreement.init(asymmetricMasterSecret.getPrivateKey());
			
      BigInteger secret         = KeyUtil.calculateAgreement(agreement, theirPublicKey.getKey());
      MasterCipher masterCipher = getMasterCipherForSecret(secret);
      byte[] decryptedBodyBytes = masterCipher.decryptBytes(encryptedBodyBytes);
			
      return new String(decryptedBodyBytes);
    } catch (InvalidKeyException ike) {
      throw new org.thoughtcrime.securesms.crypto.InvalidMessageException(ike);
    } catch (InvalidMessageException e) {
      throw new org.thoughtcrime.securesms.crypto.InvalidMessageException(e);
    }		
  }
	
  public String encryptBody(String body) {
    ECDHBasicAgreement agreement    = new ECDHBasicAgreement();
    AsymmetricCipherKeyPair keyPair = KeyUtil.generateKeyPair();
		
    agreement.init(keyPair.getPrivate());
		
    BigInteger secret         = KeyUtil.calculateAgreement(agreement, asymmetricMasterSecret.getPublicKey().getKey());
    MasterCipher masterCipher = getMasterCipherForSecret(secret);
    byte[] encryptedBodyBytes = masterCipher.encryptBytes(body.getBytes());
    PublicKey publicKey       = new PublicKey(31337, (ECPublicKeyParameters)keyPair.getPublic());
    byte[] publicKeyBytes     = publicKey.serialize();
    byte[] combined           = new byte[publicKeyBytes.length + encryptedBodyBytes.length];
		
    System.arraycopy(publicKeyBytes, 0, combined, 0, publicKeyBytes.length);
    System.arraycopy(encryptedBodyBytes, 0, combined, publicKeyBytes.length, encryptedBodyBytes.length);
		
    return Base64.encodeBytes(combined);
  }
	
  private MasterCipher getMasterCipherForSecret(BigInteger secret) {
    byte[] secretBytes        = secret.toByteArray();
    SecretKeySpec cipherKey   = deriveCipherKey(secretBytes);
    SecretKeySpec macKey      = deriveMacKey(secretBytes);
    MasterSecret masterSecret = new MasterSecret(cipherKey, macKey);

    return new MasterCipher(masterSecret);		
  }
	
  private SecretKeySpec deriveMacKey(byte[] secretBytes) {
    byte[] digestedBytes = getDigestedBytes(secretBytes, 1);
    byte[] macKeyBytes   = new byte[20];
		
    System.arraycopy(digestedBytes, 0, macKeyBytes, 0, macKeyBytes.length);
    return new SecretKeySpec(macKeyBytes, "HmacSHA1");
  }
	
  private SecretKeySpec deriveCipherKey(byte[] secretBytes) {
    byte[] digestedBytes  = getDigestedBytes(secretBytes, 0);
    byte[] cipherKeyBytes = new byte[16];
		
    System.arraycopy(digestedBytes, 0, cipherKeyBytes, 0, cipherKeyBytes.length);		
    return new SecretKeySpec(cipherKeyBytes, "AES");
  }
	
  private byte[] getDigestedBytes(byte[] secretBytes, int iteration) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
      return mac.doFinal(Conversions.intToByteArray(iteration));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }
}

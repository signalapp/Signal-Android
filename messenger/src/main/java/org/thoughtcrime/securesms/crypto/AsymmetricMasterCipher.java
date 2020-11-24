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

import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPrivateKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.thoughtcrime.securesms.util.Conversions;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

  public byte[] encryptBytes(byte[] body) {
    try {
      ECPublicKey  theirPublic        = asymmetricMasterSecret.getDjbPublicKey();
      ECKeyPair    ourKeyPair         = Curve.generateKeyPair();
      byte[]       secret             = Curve.calculateAgreement(theirPublic, ourKeyPair.getPrivateKey());
      MasterCipher masterCipher       = getMasterCipherForSecret(secret);
      byte[]       encryptedBodyBytes = masterCipher.encryptBytes(body);

      PublicKey    ourPublicKey       = new PublicKey(31337, ourKeyPair.getPublicKey());
      byte[]       publicKeyBytes     = ourPublicKey.serialize();

      return Util.combine(publicKeyBytes, encryptedBodyBytes);
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public byte[] decryptBytes(byte[] combined) throws IOException, InvalidMessageException {
    try {
      byte[][]  parts          = Util.split(combined, PublicKey.KEY_SIZE, combined.length - PublicKey.KEY_SIZE);
      PublicKey theirPublicKey = new PublicKey(parts[0], 0);

      ECPrivateKey ourPrivateKey = asymmetricMasterSecret.getPrivateKey();
      byte[]       secret        = Curve.calculateAgreement(theirPublicKey.getKey(), ourPrivateKey);
      MasterCipher masterCipher  = getMasterCipherForSecret(secret);

      return masterCipher.decryptBytes(parts[1]);
    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

  public String decryptBody(String body) throws IOException, InvalidMessageException {
    byte[] combined = Base64.decode(body);
    return new String(decryptBytes(combined));
  }

  public String encryptBody(String body) {
    return Base64.encodeBytes(encryptBytes(body.getBytes()));
  }

  private MasterCipher getMasterCipherForSecret(byte[] secretBytes) {
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
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }
}

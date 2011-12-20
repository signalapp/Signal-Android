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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.thoughtcrime.securesms.database.InvalidKeyIdException;
import org.thoughtcrime.securesms.database.LocalKeyRecord;
import org.thoughtcrime.securesms.database.RemoteKeyRecord;
import org.thoughtcrime.securesms.database.SessionKey;
import org.thoughtcrime.securesms.database.SessionRecord;
import org.thoughtcrime.securesms.protocol.Message;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Conversions;

import android.content.Context;
import android.util.Log;

/**
 * This is where the session encryption magic happens.  Implements a compressed version of the OTR protocol.
 *
 * @author Moxie Marlinspike
 */

public class SessionCipher {
	
  public static Object CIPHER_LOCK = new Object();
	
  public static final int CIPHER_KEY_LENGTH = 16;
  public static final int MAC_KEY_LENGTH    = 20;
	
  public static final int ENCRYPTED_MESSAGE_OVERHEAD              = Message.HEADER_LENGTH + MessageMac.MAC_LENGTH;
  //	public static final int ENCRYPTED_SINGLE_MESSAGE_BODY_MAX_SIZE  = SmsTransportDetails.SINGLE_MESSAGE_MAX_BYTES - ENCRYPTED_MESSAGE_OVERHEAD;
	
  private final LocalKeyRecord   localRecord;
  private final RemoteKeyRecord  remoteRecord;
  private final SessionRecord    sessionRecord;
  private final MasterSecret     masterSecret;
  private final TransportDetails transportDetails;
	
  public SessionCipher(Context context, MasterSecret masterSecret, Recipient recipient, TransportDetails transportDetails) {
    Log.w("SessionCipher", "Constructing session cipher...");
    this.masterSecret     = masterSecret;
    this.localRecord      = new LocalKeyRecord(context, masterSecret, recipient);
    this.remoteRecord     = new RemoteKeyRecord(context, recipient);
    this.sessionRecord    = new SessionRecord(context, masterSecret, recipient);
    this.transportDetails = transportDetails;
  }
  
  public byte[] encryptMessage(byte[] messageText) {
    Log.w("SessionCipher", "Encrypting message...");
    try {
      SessionKey sessionKey   = getSessionKey(Cipher.ENCRYPT_MODE, localRecord.getCurrentKeyPair().getId(), remoteRecord.getCurrentRemoteKey().getId());
      byte[] paddedMessage    = transportDetails.getPaddedMessageBody(messageText);
      byte[] cipherText       = getCiphertext(paddedMessage, sessionKey.getCipherKey());
      byte[] message          = buildMessageFromCiphertext(cipherText);
      byte[] messageWithMac   = MessageMac.buildMessageWithMac(message, sessionKey.getMacKey());

      sessionRecord.setSessionKey(sessionKey);
      sessionRecord.incrementCounter();
      sessionRecord.save();
      
      return transportDetails.encodeMessage(messageWithMac);
    } catch (IllegalBlockSizeException e) {
      throw new IllegalArgumentException(e);
    } catch (BadPaddingException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyIdException e) {
      throw new IllegalArgumentException(e);
    }
  }
  
		
  public byte[] decryptMessage(byte[] messageText) throws InvalidMessageException {
    Log.w("SessionCipher", "Decrypting message...");
    try {
      byte[] decodedMessage       = transportDetails.decodeMessage(messageText);
      Message message             = new Message(MessageMac.getMessageWithoutMac(decodedMessage));
      SessionKey sessionKey       = getSessionKey(Cipher.DECRYPT_MODE, message.getReceiverKeyId(), message.getSenderKeyId());			
			
      MessageMac.verifyMac(decodedMessage, sessionKey.getMacKey());
			
      byte[] plaintextWithPadding = getPlaintext(message.getMessageText(), sessionKey.getCipherKey(), message.getCounter());
      byte[] plaintext            = transportDetails.stripPaddedMessage(plaintextWithPadding);
			
      remoteRecord.updateCurrentRemoteKey(message.getNextKey());
      remoteRecord.save();
			
      localRecord.advanceKeyIfNecessary(message.getReceiverKeyId());
      localRecord.save();
			
      sessionRecord.setSessionKey(sessionKey);
      sessionRecord.setSessionVersion(message.getHighestMutuallySupportedVersion());
      sessionRecord.save();
			
      return plaintext;
    } catch (IOException e) {
      throw new InvalidMessageException("Encoding Failure", e);
    } catch (InvalidKeyIdException e) {
      throw new InvalidMessageException("Bad Key ID", e);
    } catch (InvalidMacException e) {
      throw new InvalidMessageException("Bad MAC", e);
    } catch (IllegalBlockSizeException e) {
      throw new InvalidMessageException("assert", e);
    } catch (BadPaddingException e) {
      throw new InvalidMessageException("assert", e);
    }
  }
		
  private SecretKeySpec deriveMacSecret(SecretKeySpec key) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] secret    = md.digest(key.getEncoded());
		
      return new SecretKeySpec(secret, "HmacSHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("SHA-1 Not Supported!",e);
    }
  }
	
  private byte[] buildMessageFromCiphertext(byte[] cipherText) {
    Message message = new Message(localRecord.getCurrentKeyPair().getId(),
				  remoteRecord.getCurrentRemoteKey().getId(),
				  localRecord.getNextKeyPair().getPublicKey(),
				  sessionRecord.getCounter(),
				  cipherText, sessionRecord.getSessionVersion(), Message.SUPPORTED_VERSION);
		
    return message.serialize();
  }
		
 	
  private byte[] getPlaintext(byte[] cipherText, SecretKeySpec key, int counter) throws IllegalBlockSizeException, BadPaddingException {
    Cipher cipher = getCipher(Cipher.DECRYPT_MODE, key, counter);
    return cipher.doFinal(cipherText);
  }
	
  private byte[] getCiphertext(byte[] message, SecretKeySpec key) throws IllegalBlockSizeException, BadPaddingException {
    Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, key, sessionRecord.getCounter());
    return cipher.doFinal(message);
  }
	
  private Cipher getCipher(int mode, SecretKeySpec key, int counter)  {
    try {
      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");

      byte[] ivBytes = new byte[16];
      Conversions.mediumToByteArray(ivBytes, 0, counter);
		
      IvParameterSpec iv = new IvParameterSpec(ivBytes);
      cipher.init(mode, key, iv);
		
      return cipher;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("AES Not Supported!");
    } catch (NoSuchPaddingException e) {
      throw new IllegalArgumentException("NoPadding Not Supported!");
    } catch (InvalidKeyException e) {
      Log.w("SessionCipher", e);
      throw new IllegalArgumentException("Invaid Key?");
    } catch (InvalidAlgorithmParameterException e) {
      Log.w("SessionCipher", e);
      throw new IllegalArgumentException("Bad IV?");
    }
  }
	
  private SecretKeySpec deriveCipherSecret(int mode, BigInteger sharedSecret, int localKeyId, int remoteKeyId) throws InvalidKeyIdException {
    byte[] sharedSecretBytes = sharedSecret.toByteArray();
    byte[] derivedBytes      = deriveBytes(sharedSecretBytes, 16 * 2);
    byte[] cipherSecret      = new byte[16];
		
    boolean isLowEnd         = isLowEnd(localKeyId, remoteKeyId);
    isLowEnd                 = (mode == Cipher.ENCRYPT_MODE ? isLowEnd : !isLowEnd);
		
    if (isLowEnd)  {
      System.arraycopy(derivedBytes, 16, cipherSecret, 0, 16);
    } else {
      System.arraycopy(derivedBytes, 0, cipherSecret, 0, 16);
    }
		
    return new SecretKeySpec(cipherSecret, "AES");
  }
	
  private boolean isLowEnd(int localKeyId, int remoteKeyId) throws InvalidKeyIdException {
    ECPublicKeyParameters localPublic  = (ECPublicKeyParameters)localRecord.getKeyPairForId(localKeyId).getPublicKey().getKey();
    ECPublicKeyParameters remotePublic = (ECPublicKeyParameters)remoteRecord.getKeyForId(remoteKeyId).getKey();
    BigInteger local                   = localPublic.getQ().getX().toBigInteger();
    BigInteger remote                  = remotePublic.getQ().getX().toBigInteger();
		
    return local.compareTo(remote) < 0;
  }
	
  private byte[] deriveBytes(byte[] seed, int bytesNeeded) {
    MessageDigest md;

    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      Log.w("SessionCipher",e);
      throw new IllegalArgumentException("SHA-256 Not Supported!");
    }
		
    int rounds = bytesNeeded / md.getDigestLength();
		
    for (int i=1;i<=rounds;i++) {
      byte[] roundBytes = Conversions.intToByteArray(i);
      md.update(roundBytes);
      md.update(seed);
    }
		
    return md.digest();
  }
	
  private SessionKey getSessionKey(int mode, int localKeyId, int remoteKeyId) throws InvalidKeyIdException {
    Log.w("SessionCipher", "Getting session key for local: " + localKeyId + " remote: " + remoteKeyId);
    SessionKey sessionKey = sessionRecord.getSessionKey(localKeyId, remoteKeyId);
    if (sessionKey != null) return sessionKey;
		
    BigInteger sharedSecret = calculateSharedSecret(localKeyId, remoteKeyId);
    SecretKeySpec cipherKey = deriveCipherSecret(mode, sharedSecret, localKeyId, remoteKeyId);
    SecretKeySpec macKey    = deriveMacSecret(cipherKey);

    return new SessionKey(localKeyId, remoteKeyId, cipherKey, macKey, masterSecret);
  }
	
  private BigInteger calculateSharedSecret(int localKeyId, int remoteKeyId) throws InvalidKeyIdException {
    ECDHBasicAgreement agreement         = new ECDHBasicAgreement();
    AsymmetricCipherKeyPair localKeyPair = localRecord.getKeyPairForId(localKeyId).getKeyPair();
    ECPublicKeyParameters remoteKey      = remoteRecord.getKeyForId(remoteKeyId).getKey();
		
    agreement.init(localKeyPair.getPrivate());
    BigInteger secret = KeyUtil.calculateAgreement(agreement, remoteKey);
		
    return secret;
  }
}

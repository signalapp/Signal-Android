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
package org.whispersystems.textsecure.crypto;

import android.content.Context;
import android.util.Log;

import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.storage.CanonicalRecipientAddress;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.LocalKeyRecord;
import org.whispersystems.textsecure.storage.RemoteKeyRecord;
import org.whispersystems.textsecure.storage.SessionKey;
import org.whispersystems.textsecure.storage.SessionRecord;
import org.whispersystems.textsecure.util.Conversions;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This is where the session encryption magic happens.  Implements a compressed version of the OTR protocol.
 *
 * @author Moxie Marlinspike
 */

public class SessionCipher {
	
  public static final Object CIPHER_LOCK = new Object();
	
  public static final int CIPHER_KEY_LENGTH = 16;
  public static final int MAC_KEY_LENGTH    = 20;

  public SessionCipherContext getEncryptionContext(Context context,
                                                   MasterSecret masterSecret,
                                                   IdentityKeyPair localIdentityKey,
                                                   CanonicalRecipientAddress recipient)
  {
    try {
      KeyRecords records           = getKeyRecords(context, masterSecret, recipient);
      int        localKeyId        = records.getLocalKeyRecord().getCurrentKeyPair().getId();
      int        remoteKeyId       = records.getRemoteKeyRecord().getCurrentRemoteKey().getId();
      int        negotiatedVersion = records.getSessionRecord().getSessionVersion();
      SessionKey sessionKey        = getSessionKey(masterSecret, Cipher.ENCRYPT_MODE, negotiatedVersion, localIdentityKey, records, localKeyId, remoteKeyId);
      PublicKey  nextKey           = records.getLocalKeyRecord().getNextKeyPair().getPublicKey();
      int        counter           = records.getSessionRecord().getCounter();


      return new SessionCipherContext(records, sessionKey, localKeyId, remoteKeyId,
                                      nextKey, counter, negotiatedVersion, negotiatedVersion);
    } catch (InvalidKeyIdException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public SessionCipherContext getDecryptionContext(Context context, MasterSecret masterSecret,
                                                   IdentityKeyPair localIdentityKey,
                                                   CanonicalRecipientAddress recipient,
                                                   int senderKeyId, int recipientKeyId,
                                                   PublicKey nextKey, int counter,
                                                   int messageVersion, int negotiatedVersion)
      throws InvalidMessageException
  {
    try {
      KeyRecords records = getKeyRecords(context, masterSecret, recipient);

      if (messageVersion < records.getSessionRecord().getNegotiatedSessionVersion()) {
        throw new InvalidMessageException("Message version: " + messageVersion +
                                          " but negotiated session version: "  +
                                          records.getSessionRecord().getNegotiatedSessionVersion());
      }

      SessionKey sessionKey = getSessionKey(masterSecret, Cipher.DECRYPT_MODE, messageVersion, localIdentityKey, records, recipientKeyId, senderKeyId);
      return new SessionCipherContext(records, sessionKey, senderKeyId,
                                      recipientKeyId, nextKey, counter,
                                      messageVersion, negotiatedVersion);
    } catch (InvalidKeyIdException e) {
      throw new InvalidMessageException(e);
    }
  }

  public byte[] encrypt(SessionCipherContext context, byte[] paddedMessageBody) {
    Log.w("SessionCipher", "Encrypting message...");
    try {
      byte[]cipherText = getCiphertext(paddedMessageBody, context.getSessionKey().getCipherKey(), context.getSessionRecord().getCounter());

      context.getSessionRecord().setSessionKey(context.getSessionKey());
      context.getSessionRecord().incrementCounter();
      context.getSessionRecord().save();

      return cipherText;
    } catch (IllegalBlockSizeException e) {
      throw new IllegalArgumentException(e);
    } catch (BadPaddingException e) {
      throw new IllegalArgumentException(e);
    }
  }
		
  public byte[] decrypt(SessionCipherContext context, byte[] decodedCiphertext)
      throws InvalidMessageException
  {
    Log.w("SessionCipher", "Decrypting message...");
    try {
      byte[] plaintextWithPadding = getPlaintext(decodedCiphertext, context.getSessionKey().getCipherKey(), context.getCounter());

      context.getRemoteKeyRecord().updateCurrentRemoteKey(context.getNextKey());
      context.getRemoteKeyRecord().save();
			
      context.getLocalKeyRecord().advanceKeyIfNecessary(context.getRecipientKeyId());
      context.getLocalKeyRecord().save();
			
      context.getSessionRecord().setSessionKey(context.getSessionKey());
      context.getSessionRecord().setSessionVersion(context.getNegotiatedVersion());
      context.getSessionRecord().setPrekeyBundleRequired(false);
      context.getSessionRecord().save();
			
      return plaintextWithPadding;
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
 	
  private byte[] getPlaintext(byte[] cipherText, SecretKeySpec key, int counter) throws IllegalBlockSizeException, BadPaddingException {
    Cipher cipher = getCipher(Cipher.DECRYPT_MODE, key, counter);
    return cipher.doFinal(cipherText);
  }
	
  private byte[] getCiphertext(byte[] message, SecretKeySpec key, int counter) throws IllegalBlockSizeException, BadPaddingException {
    Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, key, counter);
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
	
  private SecretKeySpec deriveCipherSecret(int mode, List<BigInteger> sharedSecret,
                                           KeyRecords records, int localKeyId,
                                           int remoteKeyId)
      throws InvalidKeyIdException
  {
    byte[] sharedSecretBytes = concatenateSharedSecrets(sharedSecret);
    byte[] derivedBytes      = deriveBytes(sharedSecretBytes, 16 * 2);
    byte[] cipherSecret      = new byte[16];
		
    boolean isLowEnd         = isLowEnd(records, localKeyId, remoteKeyId);
    isLowEnd                 = (mode == Cipher.ENCRYPT_MODE ? isLowEnd : !isLowEnd);
		
    if (isLowEnd)  {
      System.arraycopy(derivedBytes, 16, cipherSecret, 0, 16);
    } else {
      System.arraycopy(derivedBytes, 0, cipherSecret, 0, 16);
    }
		
    return new SecretKeySpec(cipherSecret, "AES");
  }

  private byte[] concatenateSharedSecrets(List<BigInteger> sharedSecrets) {
    int          totalByteSize = 0;
    List<byte[]> byteValues    = new LinkedList<byte[]>();

    for (BigInteger sharedSecret : sharedSecrets) {
      byte[] byteValue = sharedSecret.toByteArray();
      totalByteSize += byteValue.length;
      byteValues.add(byteValue);
    }

    byte[] combined = new byte[totalByteSize];
    int offset      = 0;

    for (byte[] byteValue : byteValues) {
      System.arraycopy(byteValue, 0, combined, offset, byteValue.length);
      offset += byteValue.length;
    }

    return combined;
  }
	
  private boolean isLowEnd(KeyRecords records, int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException
  {
    ECPublicKeyParameters localPublic  = records.getLocalKeyRecord().getKeyPairForId(localKeyId).getPublicKey().getKey();
    ECPublicKeyParameters remotePublic = records.getRemoteKeyRecord().getKeyForId(remoteKeyId).getKey();

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
	
  private SessionKey getSessionKey(MasterSecret masterSecret, int mode,
                                   int messageVersion,
                                   IdentityKeyPair localIdentityKey,
                                   KeyRecords records,
                                   int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException
  {
    Log.w("SessionCipher", "Getting session key for local: " + localKeyId + " remote: " + remoteKeyId);
    SessionKey sessionKey = records.getSessionRecord().getSessionKey(localKeyId, remoteKeyId);

    if (sessionKey != null)
      return sessionKey;
		
    List<BigInteger> sharedSecret = calculateSharedSecret(messageVersion, localIdentityKey, records, localKeyId, remoteKeyId);
    SecretKeySpec    cipherKey    = deriveCipherSecret(mode, sharedSecret, records, localKeyId, remoteKeyId);
    SecretKeySpec    macKey       = deriveMacSecret(cipherKey);

    return new SessionKey(localKeyId, remoteKeyId, cipherKey, macKey, masterSecret);
  }
	
  private List<BigInteger> calculateSharedSecret(int messageVersion,
                                                 IdentityKeyPair localIdentityKey,
                                                 KeyRecords records,
                                                 int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException
  {
    KeyPair               localKeyPair      = records.getLocalKeyRecord().getKeyPairForId(localKeyId);
    ECPublicKeyParameters remoteKey         = records.getRemoteKeyRecord().getKeyForId(remoteKeyId).getKey();
    IdentityKey           remoteIdentityKey = records.getSessionRecord().getIdentityKey();

    if (isInitiallyExchangedKeys(records, localKeyId, remoteKeyId) &&
        messageVersion >= CiphertextMessage.CRADLE_AGREEMENT_VERSION)
    {
      return SharedSecretCalculator.calculateSharedSecret(localKeyPair, localIdentityKey,
                                                          remoteKey, remoteIdentityKey);
    } else {
      return SharedSecretCalculator.calculateSharedSecret(localKeyPair, remoteKey);
    }
  }

  private boolean isInitiallyExchangedKeys(KeyRecords records, int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException
  {
    byte[] localFingerprint  = records.getSessionRecord().getLocalFingerprint();
    byte[] remoteFingerprint = records.getSessionRecord().getRemoteFingerprint();

    return Arrays.equals(localFingerprint, records.getLocalKeyRecord().getKeyPairForId(localKeyId).getPublicKey().getFingerprintBytes()) &&
           Arrays.equals(remoteFingerprint, records.getRemoteKeyRecord().getKeyForId(remoteKeyId).getFingerprintBytes());
  }

  private KeyRecords getKeyRecords(Context context, MasterSecret masterSecret,
                                   CanonicalRecipientAddress recipient)
  {
    LocalKeyRecord  localKeyRecord  = new LocalKeyRecord(context, masterSecret, recipient);
    RemoteKeyRecord remoteKeyRecord = new RemoteKeyRecord(context, recipient);
    SessionRecord   sessionRecord   = new SessionRecord(context, masterSecret, recipient);
    return new KeyRecords(localKeyRecord, remoteKeyRecord, sessionRecord);
  }

  private static class KeyRecords {

    private final LocalKeyRecord  localKeyRecord;
    private final RemoteKeyRecord remoteKeyRecord;
    private final SessionRecord   sessionRecord;

    public KeyRecords(LocalKeyRecord localKeyRecord, RemoteKeyRecord remoteKeyRecord, SessionRecord sessionRecord) {
      this.localKeyRecord  = localKeyRecord;
      this.remoteKeyRecord = remoteKeyRecord;
      this.sessionRecord   = sessionRecord;
    }

    private LocalKeyRecord getLocalKeyRecord() {
      return localKeyRecord;
    }

    private RemoteKeyRecord getRemoteKeyRecord() {
      return remoteKeyRecord;
    }

    private SessionRecord getSessionRecord() {
      return sessionRecord;
    }
  }

  public static class SessionCipherContext {

    private final LocalKeyRecord  localKeyRecord;
    private final RemoteKeyRecord remoteKeyRecord;
    private final SessionRecord   sessionRecord;
    private final SessionKey      sessionKey;
    private final int             senderKeyId;
    private final int             recipientKeyId;
    private final PublicKey       nextKey;
    private final int             counter;
    private final int             messageVersion;
    private final int             negotiatedVersion;

    public SessionCipherContext(KeyRecords records,
                                SessionKey sessionKey,
                                int senderKeyId,
                                int receiverKeyId,
                                PublicKey nextKey,
                                int counter,
                                int messageVersion,
                                int negotiatedVersion)
    {
      this.localKeyRecord    = records.getLocalKeyRecord();
      this.remoteKeyRecord   = records.getRemoteKeyRecord();
      this.sessionRecord     = records.getSessionRecord();
      this.sessionKey        = sessionKey;
      this.senderKeyId       = senderKeyId;
      this.recipientKeyId    = receiverKeyId;
      this.nextKey           = nextKey;
      this.counter           = counter;
      this.messageVersion    = messageVersion;
      this.negotiatedVersion = negotiatedVersion;
    }

    public LocalKeyRecord getLocalKeyRecord() {
      return localKeyRecord;
    }

    public RemoteKeyRecord getRemoteKeyRecord() {
      return remoteKeyRecord;
    }

    public SessionRecord getSessionRecord() {
      return sessionRecord;
    }

    public SessionKey getSessionKey() {
      return sessionKey;
    }

    public PublicKey getNextKey() {
      return nextKey;
    }

    public int getCounter() {
      return counter;
    }

    public int getSenderKeyId() {
      return senderKeyId;
    }

    public int getRecipientKeyId() {
      return recipientKeyId;
    }

    public int getNegotiatedVersion() {
      return negotiatedVersion;
    }

    public int getMessageVersion() {
      return messageVersion;
    }
  }

}

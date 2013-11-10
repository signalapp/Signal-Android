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
package org.whispersystems.textsecure.crypto;

import android.content.Context;
import android.util.Log;

import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.kdf.DerivedSecrets;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.storage.CanonicalRecipientAddress;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.LocalKeyRecord;
import org.whispersystems.textsecure.storage.RemoteKeyRecord;
import org.whispersystems.textsecure.storage.SessionKey;
import org.whispersystems.textsecure.storage.SessionRecord;
import org.whispersystems.textsecure.util.Conversions;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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
      KeyRecords records        = getKeyRecords(context, masterSecret, recipient);
      int        localKeyId     = records.getLocalKeyRecord().getCurrentKeyPair().getId();
      int        remoteKeyId    = records.getRemoteKeyRecord().getCurrentRemoteKey().getId();
      int        sessionVersion = records.getSessionRecord().getSessionVersion();
      SessionKey sessionKey     = getSessionKey(masterSecret, Cipher.ENCRYPT_MODE, sessionVersion, localIdentityKey, records, localKeyId, remoteKeyId);
      PublicKey  nextKey        = records.getLocalKeyRecord().getNextKeyPair().getPublicKey();
      int        counter        = records.getSessionRecord().getCounter();


      return new SessionCipherContext(records, sessionKey, localKeyId, remoteKeyId,
                                      nextKey, counter, sessionVersion);
    } catch (InvalidKeyIdException e) {
      throw new IllegalArgumentException(e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public SessionCipherContext getDecryptionContext(Context context, MasterSecret masterSecret,
                                                   IdentityKeyPair localIdentityKey,
                                                   CanonicalRecipientAddress recipient,
                                                   int senderKeyId, int recipientKeyId,
                                                   PublicKey nextKey, int counter,
                                                   int messageVersion)
      throws InvalidMessageException
  {
    try {
      KeyRecords records = getKeyRecords(context, masterSecret, recipient);

      if (messageVersion < records.getSessionRecord().getNegotiatedSessionVersion()) {
        throw new InvalidMessageException("Message version: " + messageVersion +
                                          " but negotiated session version: "  +
                                          records.getSessionRecord().getNegotiatedSessionVersion());
      }

      SessionKey sessionKey = getSessionKey(masterSecret, Cipher.DECRYPT_MODE, messageVersion,
                                            localIdentityKey, records, recipientKeyId, senderKeyId);

      return new SessionCipherContext(records, sessionKey, senderKeyId,
                                      recipientKeyId, nextKey, counter,
                                      messageVersion);
    } catch (InvalidKeyIdException e) {
      throw new InvalidMessageException(e);
    } catch (InvalidKeyException e) {
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
      byte[] plaintextWithPadding = getPlaintext(decodedCiphertext,
                                                 context.getSessionKey().getCipherKey(),
                                                 context.getCounter());

      context.getRemoteKeyRecord().updateCurrentRemoteKey(context.getNextKey());
      context.getRemoteKeyRecord().save();
			
      context.getLocalKeyRecord().advanceKeyIfNecessary(context.getRecipientKeyId());
      context.getLocalKeyRecord().save();
			
      context.getSessionRecord().setSessionKey(context.getSessionKey());
      context.getSessionRecord().setPrekeyBundleRequired(false);
      context.getSessionRecord().save();
			
      return plaintextWithPadding;
    } catch (IllegalBlockSizeException e) {
      throw new InvalidMessageException("assert", e);
    } catch (BadPaddingException e) {
      throw new InvalidMessageException("assert", e);
    }
  }

  private byte[] getPlaintext(byte[] cipherText, SecretKeySpec key, int counter)
      throws IllegalBlockSizeException, BadPaddingException
  {
    Cipher cipher = getCipher(Cipher.DECRYPT_MODE, key, counter);
    return cipher.doFinal(cipherText);
  }
	
  private byte[] getCiphertext(byte[] message, SecretKeySpec key, int counter)
      throws IllegalBlockSizeException, BadPaddingException
  {
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
    } catch (java.security.InvalidKeyException e) {
      Log.w("SessionCipher", e);
      throw new IllegalArgumentException("Invaid Key?");
    } catch (InvalidAlgorithmParameterException e) {
      Log.w("SessionCipher", e);
      throw new IllegalArgumentException("Bad IV?");
    }
  }

  private SessionKey getSessionKey(MasterSecret masterSecret, int mode,
                                   int messageVersion,
                                   IdentityKeyPair localIdentityKey,
                                   KeyRecords records,
                                   int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException, InvalidKeyException
  {
    Log.w("SessionCipher", "Getting session key for local: " + localKeyId + " remote: " + remoteKeyId);
    SessionKey sessionKey = records.getSessionRecord().getSessionKey(mode, localKeyId, remoteKeyId);

    if (sessionKey != null)
      return sessionKey;

    DerivedSecrets derivedSecrets = calculateSharedSecret(messageVersion, mode, localIdentityKey,
                                                          records, localKeyId, remoteKeyId);

    return new SessionKey(mode, localKeyId, remoteKeyId, derivedSecrets.getCipherKey(),
                          derivedSecrets.getMacKey(), masterSecret);
  }
	
  private DerivedSecrets calculateSharedSecret(int messageVersion, int mode,
                                               IdentityKeyPair localIdentityKey,
                                               KeyRecords records,
                                               int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException, InvalidKeyException
  {
    KeyPair     localKeyPair      = records.getLocalKeyRecord().getKeyPairForId(localKeyId);
    ECPublicKey remoteKey         = records.getRemoteKeyRecord().getKeyForId(remoteKeyId).getKey();
    IdentityKey remoteIdentityKey = records.getSessionRecord().getIdentityKey();
    boolean     isLowEnd          = isLowEnd(records, localKeyId, remoteKeyId);

    isLowEnd = (mode == Cipher.ENCRYPT_MODE ? isLowEnd : !isLowEnd);

    if (isInitiallyExchangedKeys(records, localKeyId, remoteKeyId) &&
        messageVersion >= CiphertextMessage.DHE3_INTRODUCED_VERSION)
    {
      return SharedSecretCalculator.calculateSharedSecret(isLowEnd,
                                                          localKeyPair, localKeyId, localIdentityKey,
                                                          remoteKey, remoteKeyId, remoteIdentityKey);
    } else {
      return SharedSecretCalculator.calculateSharedSecret(messageVersion, isLowEnd,
                                                          localKeyPair, localKeyId,
                                                          remoteKey, remoteKeyId);
    }
  }

  private boolean isLowEnd(KeyRecords records, int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException
  {
    ECPublicKey localPublic  = records.getLocalKeyRecord().getKeyPairForId(localKeyId).getPublicKey().getKey();
    ECPublicKey remotePublic = records.getRemoteKeyRecord().getKeyForId(remoteKeyId).getKey();

    return localPublic.compareTo(remotePublic) < 0;
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

    public SessionCipherContext(KeyRecords records,
                                SessionKey sessionKey,
                                int senderKeyId,
                                int receiverKeyId,
                                PublicKey nextKey,
                                int counter,
                                int messageVersion)
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

    public int getMessageVersion() {
      return messageVersion;
    }
  }

}

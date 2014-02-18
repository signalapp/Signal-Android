package org.whispersystems.textsecure.crypto;

import android.content.Context;
import android.util.Log;

import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.kdf.DerivedSecrets;
import org.whispersystems.textsecure.crypto.kdf.NKDF;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.crypto.protocol.WhisperMessageV1;
import org.whispersystems.textsecure.storage.CanonicalRecipient;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.LocalKeyRecord;
import org.whispersystems.textsecure.storage.RemoteKeyRecord;
import org.whispersystems.textsecure.storage.SessionKey;
import org.whispersystems.textsecure.storage.SessionRecordV1;
import org.whispersystems.textsecure.util.Conversions;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SessionCipherV1 extends SessionCipher {

  private final Context            context;
  private final MasterSecret       masterSecret;
  private final CanonicalRecipient recipient;

  public SessionCipherV1(Context context,
                         MasterSecret masterSecret,
                         CanonicalRecipient recipient)
  {
    this.context      = context;
    this.masterSecret = masterSecret;
    this.recipient    = recipient;
  }

  public CiphertextMessage encrypt(byte[] paddedMessageBody) {
    synchronized (SESSION_LOCK) {
      SessionCipherContext encryptionContext = getEncryptionContext();
      byte[]               cipherText        = getCiphertext(paddedMessageBody,
                                                             encryptionContext.getSessionKey().getCipherKey(),
                                                             encryptionContext.getSessionRecord().getCounter());

      encryptionContext.getSessionRecord().setSessionKey(encryptionContext.getSessionKey());
      encryptionContext.getSessionRecord().incrementCounter();
      encryptionContext.getSessionRecord().save();

      return new WhisperMessageV1(encryptionContext, cipherText);
    }
  }

  public byte[] decrypt(byte[] decodedCiphertext) throws InvalidMessageException {
    synchronized (SESSION_LOCK) {
      WhisperMessageV1     message           = new WhisperMessageV1(decodedCiphertext);
      SessionCipherContext decryptionContext = getDecryptionContext(message);

      message.verifyMac(decryptionContext);

      byte[] plaintextWithPadding = getPlaintext(message.getBody(),
                                                 decryptionContext.getSessionKey().getCipherKey(),
                                                 decryptionContext.getCounter());

      decryptionContext.getRemoteKeyRecord().updateCurrentRemoteKey(decryptionContext.getNextKey());
      decryptionContext.getRemoteKeyRecord().save();

      decryptionContext.getLocalKeyRecord().advanceKeyIfNecessary(decryptionContext.getRecipientKeyId());
      decryptionContext.getLocalKeyRecord().save();

      decryptionContext.getSessionRecord().setSessionKey(decryptionContext.getSessionKey());
      decryptionContext.getSessionRecord().save();

      return plaintextWithPadding;
    }
  }

  @Override
  public int getRemoteRegistrationId() {
    return 0;
  }

  private SessionCipherContext getEncryptionContext() {
    try {
      KeyRecords records        = getKeyRecords(context, masterSecret, recipient);
      int        localKeyId     = records.getLocalKeyRecord().getCurrentKeyPair().getId();
      int        remoteKeyId    = records.getRemoteKeyRecord().getCurrentRemoteKey().getId();
      int        sessionVersion = records.getSessionRecord().getSessionVersion();
      SessionKey sessionKey     = getSessionKey(masterSecret, Cipher.ENCRYPT_MODE,
                                                records, localKeyId, remoteKeyId);
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

  public SessionCipherContext getDecryptionContext(WhisperMessageV1 message)
      throws InvalidMessageException
  {
    try {
      KeyRecords records        = getKeyRecords(context, masterSecret, recipient);
      int        messageVersion = message.getCurrentVersion();
      int        recipientKeyId = message.getReceiverKeyId();
      int        senderKeyId    = message.getSenderKeyId();
      PublicKey  nextKey        = new PublicKey(message.getNextKeyBytes());
      int        counter        = message.getCounter();

      if (messageVersion < records.getSessionRecord().getSessionVersion()) {
        throw new InvalidMessageException("Message version: " + messageVersion +
                                          " but negotiated session version: "  +
                                          records.getSessionRecord().getSessionVersion());
      }

      SessionKey sessionKey = getSessionKey(masterSecret, Cipher.DECRYPT_MODE,
                                            records, recipientKeyId, senderKeyId);

      return new SessionCipherContext(records, sessionKey, senderKeyId,
                                      recipientKeyId, nextKey, counter,
                                      messageVersion);
    } catch (InvalidKeyIdException e) {
      throw new InvalidMessageException(e);
    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

  private byte[] getCiphertext(byte[] message, SecretKeySpec key, int counter)  {
    try {
      Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, key, counter);
      return cipher.doFinal(message);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] getPlaintext(byte[] cipherText, SecretKeySpec key, int counter) {
    try {
      Cipher cipher = getCipher(Cipher.DECRYPT_MODE, key, counter);
      return cipher.doFinal(cipherText);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    }
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
                                   KeyRecords records,
                                   int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException, InvalidKeyException
  {
    Log.w("SessionCipher", "Getting session key for local: " + localKeyId + " remote: " + remoteKeyId);
    SessionKey sessionKey = records.getSessionRecord().getSessionKey(mode, localKeyId, remoteKeyId);

    if (sessionKey != null)
      return sessionKey;

    DerivedSecrets derivedSecrets = calculateSharedSecret(mode, records, localKeyId, remoteKeyId);

    return new SessionKey(mode, localKeyId, remoteKeyId, derivedSecrets.getCipherKey(),
                          derivedSecrets.getMacKey(), masterSecret);
  }

  private DerivedSecrets calculateSharedSecret(int mode, KeyRecords records,
                                               int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException, InvalidKeyException
  {
    NKDF        kdf          = new NKDF();
    KeyPair     localKeyPair = records.getLocalKeyRecord().getKeyPairForId(localKeyId);
    ECPublicKey remoteKey    = records.getRemoteKeyRecord().getKeyForId(remoteKeyId).getKey();
    byte[]      sharedSecret = Curve.calculateAgreement(remoteKey, localKeyPair.getPrivateKey());
    boolean     isLowEnd     = isLowEnd(records, localKeyId, remoteKeyId);

    isLowEnd = (mode == Cipher.ENCRYPT_MODE ? isLowEnd : !isLowEnd);

    return kdf.deriveSecrets(sharedSecret, isLowEnd);
  }

  private boolean isLowEnd(KeyRecords records, int localKeyId, int remoteKeyId)
      throws InvalidKeyIdException
  {
    ECPublicKey localPublic  = records.getLocalKeyRecord().getKeyPairForId(localKeyId).getPublicKey().getKey();
    ECPublicKey remotePublic = records.getRemoteKeyRecord().getKeyForId(remoteKeyId).getKey();

    return localPublic.compareTo(remotePublic) < 0;
  }

  private KeyRecords getKeyRecords(Context context, MasterSecret masterSecret,
                                   CanonicalRecipient recipient)
  {
    LocalKeyRecord  localKeyRecord  = new LocalKeyRecord(context, masterSecret, recipient);
    RemoteKeyRecord remoteKeyRecord = new RemoteKeyRecord(context, recipient);
    SessionRecordV1 sessionRecord   = new SessionRecordV1(context, masterSecret, recipient);
    return new KeyRecords(localKeyRecord, remoteKeyRecord, sessionRecord);
  }

  private static class KeyRecords {

    private final LocalKeyRecord  localKeyRecord;
    private final RemoteKeyRecord remoteKeyRecord;
    private final SessionRecordV1 sessionRecord;

    public KeyRecords(LocalKeyRecord localKeyRecord,
                      RemoteKeyRecord remoteKeyRecord,
                      SessionRecordV1 sessionRecord)
    {
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

    private SessionRecordV1 getSessionRecord() {
      return sessionRecord;
    }
  }

  public static class SessionCipherContext {

    private final LocalKeyRecord  localKeyRecord;
    private final RemoteKeyRecord remoteKeyRecord;
    private final SessionRecordV1 sessionRecord;
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

    public SessionRecordV1 getSessionRecord() {
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

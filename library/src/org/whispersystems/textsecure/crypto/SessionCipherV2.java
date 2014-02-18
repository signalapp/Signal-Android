package org.whispersystems.textsecure.crypto;

import android.content.Context;
import android.util.Pair;

import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.crypto.protocol.CiphertextMessage;
import org.whispersystems.textsecure.crypto.protocol.PreKeyWhisperMessage;
import org.whispersystems.textsecure.crypto.protocol.WhisperMessageV2;
import org.whispersystems.textsecure.crypto.ratchet.ChainKey;
import org.whispersystems.textsecure.crypto.ratchet.MessageKeys;
import org.whispersystems.textsecure.crypto.ratchet.RootKey;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.storage.SessionRecordV2;
import org.whispersystems.textsecure.util.Conversions;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SessionCipherV2 extends SessionCipher {

  private final Context         context;
  private final MasterSecret    masterSecret;
  private final RecipientDevice recipient;

  public SessionCipherV2(Context context,
                         MasterSecret masterSecret,
                         RecipientDevice recipient)
  {
    this.context      = context;
    this.masterSecret = masterSecret;
    this.recipient    = recipient;
  }

  @Override
  public CiphertextMessage encrypt(byte[] paddedMessage) {
    synchronized (SESSION_LOCK) {
      SessionRecordV2 sessionRecord   = getSessionRecord();
      ChainKey        chainKey        = sessionRecord.getSenderChainKey();
      MessageKeys     messageKeys     = chainKey.getMessageKeys();
      ECPublicKey     senderEphemeral = sessionRecord.getSenderEphemeral();
      int             previousCounter = sessionRecord.getPreviousCounter();

      byte[]            ciphertextBody    = getCiphertext(messageKeys, paddedMessage);
      CiphertextMessage ciphertextMessage = new WhisperMessageV2(messageKeys.getMacKey(),
                                                                 senderEphemeral, chainKey.getIndex(),
                                                                 previousCounter, ciphertextBody);

      if (sessionRecord.hasPendingPreKey()) {
        Pair<Integer, ECPublicKey> pendingPreKey       = sessionRecord.getPendingPreKey();
        int                        localRegistrationId = sessionRecord.getLocalRegistrationId();

        ciphertextMessage = new PreKeyWhisperMessage(localRegistrationId, pendingPreKey.first,
                                                     pendingPreKey.second,
                                                     sessionRecord.getLocalIdentityKey(),
                                                     (WhisperMessageV2) ciphertextMessage);
      }

      sessionRecord.setSenderChainKey(chainKey.getNextChainKey());
      sessionRecord.save();

      return ciphertextMessage;
    }
  }

  @Override
  public byte[] decrypt(byte[] decodedMessage) throws InvalidMessageException {
    synchronized (SESSION_LOCK) {
      SessionRecordV2  sessionRecord     = getSessionRecord();
      WhisperMessageV2 ciphertextMessage = new WhisperMessageV2(decodedMessage);
      ECPublicKey      theirEphemeral    = ciphertextMessage.getSenderEphemeral();
      int              counter           = ciphertextMessage.getCounter();
      ChainKey         chainKey          = getOrCreateChainKey(sessionRecord, theirEphemeral);
      MessageKeys      messageKeys       = getOrCreateMessageKeys(sessionRecord, theirEphemeral,
                                                                  chainKey, counter);

      ciphertextMessage.verifyMac(messageKeys.getMacKey());

      byte[] plaintext = getPlaintext(messageKeys, ciphertextMessage.getBody());

      sessionRecord.clearPendingPreKey();
      sessionRecord.save();

      return plaintext;
    }
  }

  @Override
  public int getRemoteRegistrationId() {
    synchronized (SESSION_LOCK) {
      SessionRecordV2 sessionRecord = getSessionRecord();
      return sessionRecord.getRemoteRegistrationId();
    }
  }

  private ChainKey getOrCreateChainKey(SessionRecordV2 sessionRecord, ECPublicKey theirEphemeral)
      throws InvalidMessageException
  {
    try {
      if (sessionRecord.hasReceiverChain(theirEphemeral)) {
        return sessionRecord.getReceiverChainKey(theirEphemeral);
      } else {
        RootKey                 rootKey         = sessionRecord.getRootKey();
        ECKeyPair               ourEphemeral    = sessionRecord.getSenderEphemeralPair();
        Pair<RootKey, ChainKey> receiverChain   = rootKey.createChain(theirEphemeral, ourEphemeral);
        ECKeyPair               ourNewEphemeral = Curve.generateKeyPairForType(Curve.DJB_TYPE);
        Pair<RootKey, ChainKey> senderChain     = receiverChain.first.createChain(theirEphemeral, ourNewEphemeral);

        sessionRecord.setRootKey(senderChain.first);
        sessionRecord.addReceiverChain(theirEphemeral, receiverChain.second);
        sessionRecord.setPreviousCounter(sessionRecord.getSenderChainKey().getIndex()-1);
        sessionRecord.setSenderChain(ourNewEphemeral, senderChain.second);

        return receiverChain.second;
      }
    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

  private MessageKeys getOrCreateMessageKeys(SessionRecordV2 sessionRecord,
                                             ECPublicKey theirEphemeral,
                                             ChainKey chainKey, int counter)
      throws InvalidMessageException
  {
    if (chainKey.getIndex() > counter) {
      if (sessionRecord.hasMessageKeys(theirEphemeral, counter)) {
        return sessionRecord.removeMessageKeys(theirEphemeral, counter);
      } else {
        throw new InvalidMessageException("Received message with old counter!");
      }
    }

    if (chainKey.getIndex() - counter > 500) {
      throw new InvalidMessageException("Over 500 messages into the future!");
    }

    while (chainKey.getIndex() < counter) {
      MessageKeys messageKeys = chainKey.getMessageKeys();
      sessionRecord.setMessageKeys(theirEphemeral, messageKeys);
      chainKey = chainKey.getNextChainKey();
    }

    sessionRecord.setReceiverChainKey(theirEphemeral, chainKey.getNextChainKey());
    return chainKey.getMessageKeys();
  }

  private byte[] getCiphertext(MessageKeys messageKeys, byte[] plaintext) {
    try {
      Cipher cipher = getCipher(Cipher.ENCRYPT_MODE,
                                messageKeys.getCipherKey(),
                                messageKeys.getCounter());

      return cipher.doFinal(plaintext);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] getPlaintext(MessageKeys messageKeys, byte[] cipherText) {
    try {
      Cipher cipher = getCipher(Cipher.DECRYPT_MODE,
                                messageKeys.getCipherKey(),
                                messageKeys.getCounter());
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
      Conversions.intToByteArray(ivBytes, 0, counter);

      IvParameterSpec iv = new IvParameterSpec(ivBytes);
      cipher.init(mode, key, iv);

      return cipher;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (NoSuchPaddingException e) {
      throw new AssertionError(e);
    } catch (java.security.InvalidKeyException e) {
      throw new AssertionError(e);
    } catch (InvalidAlgorithmParameterException e) {
      throw new AssertionError(e);
    }
  }


  private SessionRecordV2 getSessionRecord() {
    return new SessionRecordV2(context, masterSecret, recipient);
  }

}

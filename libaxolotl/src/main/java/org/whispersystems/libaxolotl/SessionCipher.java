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
package org.whispersystems.libaxolotl;

import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;
import org.whispersystems.libaxolotl.ratchet.ChainKey;
import org.whispersystems.libaxolotl.ratchet.MessageKeys;
import org.whispersystems.libaxolotl.ratchet.RootKey;
import org.whispersystems.libaxolotl.util.ByteUtil;
import org.whispersystems.libaxolotl.util.Pair;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SessionCipher {

  private static final Object SESSION_LOCK = new Object();

  private final SessionStore sessionStore;

  public SessionCipher(SessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  public CiphertextMessage encrypt(byte[] paddedMessage) {
    synchronized (SESSION_LOCK) {
      SessionState      sessionState      = sessionStore.getSessionState();
      ChainKey          chainKey          = sessionState.getSenderChainKey();
      MessageKeys       messageKeys       = chainKey.getMessageKeys();
      ECPublicKey       senderEphemeral   = sessionState.getSenderEphemeral();
      int               previousCounter   = sessionState.getPreviousCounter();

      byte[]            ciphertextBody    = getCiphertext(messageKeys, paddedMessage);
      CiphertextMessage ciphertextMessage = new WhisperMessage(messageKeys.getMacKey(),
                                                               senderEphemeral, chainKey.getIndex(),
                                                               previousCounter, ciphertextBody);

      if (sessionState.hasPendingPreKey()) {
        Pair<Integer, ECPublicKey> pendingPreKey       = sessionState.getPendingPreKey();
        int                        localRegistrationId = sessionState.getLocalRegistrationId();

        ciphertextMessage = new PreKeyWhisperMessage(localRegistrationId, pendingPreKey.first(),
                                                     pendingPreKey.second(),
                                                     sessionState.getLocalIdentityKey(),
                                                     (WhisperMessage) ciphertextMessage);
      }

      sessionState.setSenderChainKey(chainKey.getNextChainKey());
      sessionStore.save();
      return ciphertextMessage;
    }
  }

  public byte[] decrypt(byte[] decodedMessage)
      throws InvalidMessageException, DuplicateMessageException, LegacyMessageException
  {
    synchronized (SESSION_LOCK) {
      SessionState       sessionState   = sessionStore.getSessionState();
      List<SessionState> previousStates = sessionStore.getPreviousSessionStates();
      List<Exception>    exceptions     = new LinkedList<Exception>();

      try {
        byte[] plaintext = decrypt(sessionState, decodedMessage);
        sessionStore.save();

        return plaintext;
      } catch (InvalidMessageException e) {
        exceptions.add(e);
      }

      for (SessionState previousState : previousStates) {
        try {
          byte[] plaintext = decrypt(previousState, decodedMessage);
          sessionStore.save();

          return plaintext;
        } catch (InvalidMessageException e) {
          exceptions.add(e);
        }
      }

      throw new InvalidMessageException("No valid sessions.", exceptions);
    }
  }

  public byte[] decrypt(SessionState sessionState, byte[] decodedMessage)
      throws InvalidMessageException, DuplicateMessageException, LegacyMessageException
  {
    if (!sessionState.hasSenderChain()) {
      throw new InvalidMessageException("Uninitialized session!");
    }

    WhisperMessage ciphertextMessage = new WhisperMessage(decodedMessage);
    ECPublicKey    theirEphemeral    = ciphertextMessage.getSenderEphemeral();
    int            counter           = ciphertextMessage.getCounter();
    ChainKey       chainKey          = getOrCreateChainKey(sessionState, theirEphemeral);
    MessageKeys    messageKeys       = getOrCreateMessageKeys(sessionState, theirEphemeral,
                                                              chainKey, counter);

    ciphertextMessage.verifyMac(messageKeys.getMacKey());

    byte[] plaintext = getPlaintext(messageKeys, ciphertextMessage.getBody());

    sessionState.clearPendingPreKey();

    return plaintext;

  }

  public int getRemoteRegistrationId() {
    synchronized (SESSION_LOCK) {
      return sessionStore.getSessionState().getRemoteRegistrationId();
    }
  }

  private ChainKey getOrCreateChainKey(SessionState sessionState, ECPublicKey theirEphemeral)
      throws InvalidMessageException
  {
    try {
      if (sessionState.hasReceiverChain(theirEphemeral)) {
        return sessionState.getReceiverChainKey(theirEphemeral);
      } else {
        RootKey rootKey         = sessionState.getRootKey();
        ECKeyPair ourEphemeral    = sessionState.getSenderEphemeralPair();
        Pair<RootKey, ChainKey> receiverChain   = rootKey.createChain(theirEphemeral, ourEphemeral);
        ECKeyPair               ourNewEphemeral = Curve.generateKeyPair(true);
        Pair<RootKey, ChainKey> senderChain     = receiverChain.first().createChain(theirEphemeral, ourNewEphemeral);

        sessionState.setRootKey(senderChain.first());
        sessionState.addReceiverChain(theirEphemeral, receiverChain.second());
        sessionState.setPreviousCounter(sessionState.getSenderChainKey().getIndex()-1);
        sessionState.setSenderChain(ourNewEphemeral, senderChain.second());

        return receiverChain.second();
      }
    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

  private MessageKeys getOrCreateMessageKeys(SessionState sessionState,
                                             ECPublicKey theirEphemeral,
                                             ChainKey chainKey, int counter)
      throws InvalidMessageException, DuplicateMessageException
  {
    if (chainKey.getIndex() > counter) {
      if (sessionState.hasMessageKeys(theirEphemeral, counter)) {
        return sessionState.removeMessageKeys(theirEphemeral, counter);
      } else {
        throw new DuplicateMessageException("Received message with old counter: " +
                                                chainKey.getIndex() + " , " + counter);
      }
    }

    if (chainKey.getIndex() - counter > 2000) {
      throw new InvalidMessageException("Over 2000 messages into the future!");
    }

    while (chainKey.getIndex() < counter) {
      MessageKeys messageKeys = chainKey.getMessageKeys();
      sessionState.setMessageKeys(theirEphemeral, messageKeys);
      chainKey = chainKey.getNextChainKey();
    }

    sessionState.setReceiverChainKey(theirEphemeral, chainKey.getNextChainKey());
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
      ByteUtil.intToByteArray(ivBytes, 0, counter);

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
}
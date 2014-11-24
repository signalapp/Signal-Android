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
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;
import org.whispersystems.libaxolotl.util.ByteUtil;
import org.whispersystems.libaxolotl.util.Pair;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.whispersystems.libaxolotl.state.SessionState.UnacknowledgedPreKeyMessageItems;

/**
 * The main entry point for Axolotl encrypt/decrypt operations.
 *
 * Once a session has been established with {@link SessionBuilder},
 * this class can be used for all encrypt/decrypt operations within
 * that session.
 *
 * @author Moxie Marlinspike
 */
public class SessionCipher {

  public static final Object SESSION_LOCK = new Object();

  private final SessionStore   sessionStore;
  private final SessionBuilder sessionBuilder;
  private final PreKeyStore    preKeyStore;
  private final long           recipientId;
  private final int            deviceId;

  /**
   * Construct a SessionCipher for encrypt/decrypt operations on a session.
   * In order to use SessionCipher, a session must have already been created
   * and stored using {@link SessionBuilder}.
   *
   * @param  sessionStore The {@link SessionStore} that contains a session for this recipient.
   * @param  recipientId  The remote ID that messages will be encrypted to or decrypted from.
   * @param  deviceId     The device corresponding to the recipientId.
   */
  public SessionCipher(SessionStore sessionStore, PreKeyStore preKeyStore,
                       SignedPreKeyStore signedPreKeyStore, IdentityKeyStore identityKeyStore,
                       long recipientId, int deviceId)
  {
    this.sessionStore   = sessionStore;
    this.recipientId    = recipientId;
    this.deviceId       = deviceId;
    this.preKeyStore    = preKeyStore;
    this.sessionBuilder = new SessionBuilder(sessionStore, preKeyStore, signedPreKeyStore,
                                             identityKeyStore, recipientId, deviceId);
  }

  public SessionCipher(AxolotlStore store, long recipientId, int deviceId) {
    this(store, store, store, store, recipientId, deviceId);
  }

  /**
   * Encrypt a message.
   *
   * @param  paddedMessage The plaintext message bytes, optionally padded to a constant multiple.
   * @return A ciphertext message encrypted to the recipient+device tuple.
   */
  public CiphertextMessage encrypt(byte[] paddedMessage) {
    synchronized (SESSION_LOCK) {
      SessionRecord sessionRecord   = sessionStore.loadSession(recipientId, deviceId);
      SessionState  sessionState    = sessionRecord.getSessionState();
      ChainKey      chainKey        = sessionState.getSenderChainKey();
      MessageKeys   messageKeys     = chainKey.getMessageKeys();
      ECPublicKey   senderEphemeral = sessionState.getSenderRatchetKey();
      int           previousCounter = sessionState.getPreviousCounter();
      int           sessionVersion  = sessionState.getSessionVersion();

      byte[]            ciphertextBody    = getCiphertext(sessionVersion, messageKeys, paddedMessage);
      CiphertextMessage ciphertextMessage = new WhisperMessage(sessionVersion, messageKeys.getMacKey(),
                                                               senderEphemeral, chainKey.getIndex(),
                                                               previousCounter, ciphertextBody,
                                                               sessionState.getLocalIdentityKey(),
                                                               sessionState.getRemoteIdentityKey());

      if (sessionState.hasUnacknowledgedPreKeyMessage()) {
        UnacknowledgedPreKeyMessageItems items = sessionState.getUnacknowledgedPreKeyMessageItems();
        int localRegistrationId = sessionState.getLocalRegistrationId();

        ciphertextMessage = new PreKeyWhisperMessage(sessionVersion, localRegistrationId, items.getPreKeyId(),
                                                     items.getSignedPreKeyId(), items.getBaseKey(),
                                                     sessionState.getLocalIdentityKey(),
                                                     (WhisperMessage) ciphertextMessage);
      }

      sessionState.setSenderChainKey(chainKey.getNextChainKey());
      sessionStore.storeSession(recipientId, deviceId, sessionRecord);
      return ciphertextMessage;
    }
  }

  /**
   * Decrypt a message.
   *
   * @param  ciphertext The {@link PreKeyWhisperMessage} to decrypt.
   *
   * @return The plaintext.
   * @throws InvalidMessageException if the input is not valid ciphertext.
   * @throws DuplicateMessageException if the input is a message that has already been received.
   * @throws LegacyMessageException if the input is a message formatted by a protocol version that
   *                                is no longer supported.
   * @throws InvalidKeyIdException when there is no local {@link org.whispersystems.libaxolotl.state.PreKeyRecord}
   *                               that corresponds to the PreKey ID in the message.
   * @throws InvalidKeyException when the message is formatted incorrectly.
   * @throws UntrustedIdentityException when the {@link IdentityKey} of the sender is untrusted.
   */
  public byte[] decrypt(PreKeyWhisperMessage ciphertext)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException,
             InvalidKeyIdException, InvalidKeyException, UntrustedIdentityException
  {
    return decrypt(ciphertext, new NullDecryptionCallback());
  }

  /**
   * Decrypt a message.
   *
   * @param  ciphertext The {@link PreKeyWhisperMessage} to decrypt.
   * @param  callback   A callback that is triggered after decryption is complete,
   *                    but before the updated session state has been committed to the session
   *                    DB.  This allows some implementations to store the committed plaintext
   *                    to a DB first, in case they are concerned with a crash happening between
   *                    the time the session state is updated but before they're able to store
   *                    the plaintext to disk.
   *
   * @return The plaintext.
   * @throws InvalidMessageException if the input is not valid ciphertext.
   * @throws DuplicateMessageException if the input is a message that has already been received.
   * @throws LegacyMessageException if the input is a message formatted by a protocol version that
   *                                is no longer supported.
   * @throws InvalidKeyIdException when there is no local {@link org.whispersystems.libaxolotl.state.PreKeyRecord}
   *                               that corresponds to the PreKey ID in the message.
   * @throws InvalidKeyException when the message is formatted incorrectly.
   * @throws UntrustedIdentityException when the {@link IdentityKey} of the sender is untrusted.
   */
  public byte[] decrypt(PreKeyWhisperMessage ciphertext, DecryptionCallback callback)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException,
             InvalidKeyIdException, InvalidKeyException, UntrustedIdentityException
  {
    synchronized (SESSION_LOCK) {
      SessionRecord     sessionRecord    = sessionStore.loadSession(recipientId, deviceId);
      Optional<Integer> unsignedPreKeyId = sessionBuilder.process(sessionRecord, ciphertext);
      byte[]            plaintext        = decrypt(sessionRecord, ciphertext.getWhisperMessage());

      callback.handlePlaintext(plaintext);

      sessionStore.storeSession(recipientId, deviceId, sessionRecord);

      if (unsignedPreKeyId.isPresent()) {
        preKeyStore.removePreKey(unsignedPreKeyId.get());
      }

      return plaintext;
    }
  }

  /**
   * Decrypt a message.
   *
   * @param  ciphertext The {@link WhisperMessage} to decrypt.
   *
   * @return The plaintext.
   * @throws InvalidMessageException if the input is not valid ciphertext.
   * @throws DuplicateMessageException if the input is a message that has already been received.
   * @throws LegacyMessageException if the input is a message formatted by a protocol version that
   *                                is no longer supported.
   * @throws NoSessionException if there is no established session for this contact.
   */
  public byte[] decrypt(WhisperMessage ciphertext)
      throws InvalidMessageException, DuplicateMessageException, LegacyMessageException,
      NoSessionException
  {
    return decrypt(ciphertext, new NullDecryptionCallback());
  }

  /**
   * Decrypt a message.
   *
   * @param  ciphertext The {@link WhisperMessage} to decrypt.
   * @param  callback   A callback that is triggered after decryption is complete,
   *                    but before the updated session state has been committed to the session
   *                    DB.  This allows some implementations to store the committed plaintext
   *                    to a DB first, in case they are concerned with a crash happening between
   *                    the time the session state is updated but before they're able to store
   *                    the plaintext to disk.
   *
   * @return The plaintext.
   * @throws InvalidMessageException if the input is not valid ciphertext.
   * @throws DuplicateMessageException if the input is a message that has already been received.
   * @throws LegacyMessageException if the input is a message formatted by a protocol version that
   *                                is no longer supported.
   * @throws NoSessionException if there is no established session for this contact.
   */
  public byte[] decrypt(WhisperMessage ciphertext, DecryptionCallback callback)
      throws InvalidMessageException, DuplicateMessageException, LegacyMessageException,
             NoSessionException
  {
    synchronized (SESSION_LOCK) {

      if (!sessionStore.containsSession(recipientId, deviceId)) {
        throw new NoSessionException("No session for: " + recipientId + ", " + deviceId);
      }

      SessionRecord sessionRecord = sessionStore.loadSession(recipientId, deviceId);
      byte[]        plaintext     = decrypt(sessionRecord, ciphertext);

      callback.handlePlaintext(plaintext);

      sessionStore.storeSession(recipientId, deviceId, sessionRecord);

      return plaintext;
    }
  }

  private byte[] decrypt(SessionRecord sessionRecord, WhisperMessage ciphertext)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException
  {
    synchronized (SESSION_LOCK) {
      Iterator<SessionState> previousStates = sessionRecord.getPreviousSessionStates().iterator();
      List<Exception>        exceptions     = new LinkedList<>();

      try {
        SessionState sessionState = new SessionState(sessionRecord.getSessionState());
        byte[]       plaintext    = decrypt(sessionState, ciphertext);

        sessionRecord.setState(sessionState);
        return plaintext;
      } catch (InvalidMessageException e) {
        exceptions.add(e);
      }

      while (previousStates.hasNext()) {
        try {
          SessionState promotedState = new SessionState(previousStates.next());
          byte[]       plaintext     = decrypt(promotedState, ciphertext);

          previousStates.remove();
          sessionRecord.promoteState(promotedState);

          return plaintext;
        } catch (InvalidMessageException e) {
          exceptions.add(e);
        }
      }

      throw new InvalidMessageException("No valid sessions.", exceptions);
    }
  }

  private byte[] decrypt(SessionState sessionState, WhisperMessage ciphertextMessage)
      throws InvalidMessageException, DuplicateMessageException, LegacyMessageException
  {
    if (!sessionState.hasSenderChain()) {
      throw new InvalidMessageException("Uninitialized session!");
    }

    if (ciphertextMessage.getMessageVersion() != sessionState.getSessionVersion()) {
      throw new InvalidMessageException(String.format("Message version %d, but session version %d",
                                                      ciphertextMessage.getMessageVersion(),
                                                      sessionState.getSessionVersion()));
    }

    int            messageVersion    = ciphertextMessage.getMessageVersion();
    ECPublicKey    theirEphemeral    = ciphertextMessage.getSenderRatchetKey();
    int            counter           = ciphertextMessage.getCounter();
    ChainKey       chainKey          = getOrCreateChainKey(sessionState, theirEphemeral);
    MessageKeys    messageKeys       = getOrCreateMessageKeys(sessionState, theirEphemeral,
                                                              chainKey, counter);

    ciphertextMessage.verifyMac(messageVersion,
                                sessionState.getRemoteIdentityKey(),
                                sessionState.getLocalIdentityKey(),
                                messageKeys.getMacKey());

    byte[] plaintext = getPlaintext(messageVersion, messageKeys, ciphertextMessage.getBody());

    sessionState.clearUnacknowledgedPreKeyMessage();

    return plaintext;
  }

  public int getRemoteRegistrationId() {
    synchronized (SESSION_LOCK) {
      SessionRecord record = sessionStore.loadSession(recipientId, deviceId);
      return record.getSessionState().getRemoteRegistrationId();
    }
  }

  public int getSessionVersion() {
    synchronized (SESSION_LOCK) {
      if (!sessionStore.containsSession(recipientId, deviceId)) {
        throw new IllegalStateException(String.format("No session for (%d, %d)!", recipientId, deviceId));
      }

      SessionRecord record = sessionStore.loadSession(recipientId, deviceId);
      return record.getSessionState().getSessionVersion();
    }
  }

  private ChainKey getOrCreateChainKey(SessionState sessionState, ECPublicKey theirEphemeral)
      throws InvalidMessageException
  {
    try {
      if (sessionState.hasReceiverChain(theirEphemeral)) {
        return sessionState.getReceiverChainKey(theirEphemeral);
      } else {
        RootKey                 rootKey         = sessionState.getRootKey();
        ECKeyPair               ourEphemeral    = sessionState.getSenderRatchetKeyPair();
        Pair<RootKey, ChainKey> receiverChain   = rootKey.createChain(theirEphemeral, ourEphemeral);
        ECKeyPair               ourNewEphemeral = Curve.generateKeyPair();
        Pair<RootKey, ChainKey> senderChain     = receiverChain.first().createChain(theirEphemeral, ourNewEphemeral);

        sessionState.setRootKey(senderChain.first());
        sessionState.addReceiverChain(theirEphemeral, receiverChain.second());
        sessionState.setPreviousCounter(Math.max(sessionState.getSenderChainKey().getIndex()-1, 0));
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

    if (counter - chainKey.getIndex() > 2000) {
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

  private byte[] getCiphertext(int version, MessageKeys messageKeys, byte[] plaintext) {
    try {
      Cipher cipher;

      if (version >= 3) {
        cipher = getCipher(Cipher.ENCRYPT_MODE, messageKeys.getCipherKey(), messageKeys.getIv());
      } else {
        cipher = getCipher(Cipher.ENCRYPT_MODE, messageKeys.getCipherKey(), messageKeys.getCounter());
      }

      return cipher.doFinal(plaintext);
    } catch (IllegalBlockSizeException | BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] getPlaintext(int version, MessageKeys messageKeys, byte[] cipherText)
      throws InvalidMessageException
  {
    try {
      Cipher cipher;

      if (version >= 3) {
        cipher = getCipher(Cipher.DECRYPT_MODE, messageKeys.getCipherKey(), messageKeys.getIv());
      } else {
        cipher = getCipher(Cipher.DECRYPT_MODE, messageKeys.getCipherKey(), messageKeys.getCounter());
      }

      return cipher.doFinal(cipherText);
    } catch (IllegalBlockSizeException | BadPaddingException e) {
      throw new InvalidMessageException(e);
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
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | java.security.InvalidKeyException |
             InvalidAlgorithmParameterException e)
    {
      throw new AssertionError(e);
    }
  }

  private Cipher getCipher(int mode, SecretKeySpec key, IvParameterSpec iv) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(mode, key, iv);
      return cipher;
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | java.security.InvalidKeyException |
             InvalidAlgorithmParameterException e)
    {
      throw new AssertionError(e);
    }
  }

  public static interface DecryptionCallback {
    public void handlePlaintext(byte[] plaintext);
  }

  private static class NullDecryptionCallback implements DecryptionCallback {
    @Override
    public void handlePlaintext(byte[] plaintext) {}
  }
}
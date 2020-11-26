/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.libsignal;

import org.session.libsignal.libsignal.ecc.Curve;
import org.session.libsignal.libsignal.ecc.ECKeyPair;
import org.session.libsignal.libsignal.ecc.ECPublicKey;
import org.session.libsignal.libsignal.loki.FallbackSessionCipher;
import org.session.libsignal.libsignal.protocol.CiphertextMessage;
import org.session.libsignal.libsignal.protocol.PreKeySignalMessage;
import org.session.libsignal.libsignal.protocol.SignalMessage;
import org.session.libsignal.libsignal.ratchet.ChainKey;
import org.session.libsignal.libsignal.ratchet.MessageKeys;
import org.session.libsignal.libsignal.ratchet.RootKey;
import org.session.libsignal.libsignal.state.IdentityKeyStore;
import org.session.libsignal.libsignal.state.PreKeyStore;
import org.session.libsignal.libsignal.state.SessionRecord;
import org.session.libsignal.libsignal.state.SessionState;
import org.session.libsignal.libsignal.state.SessionStore;
import org.session.libsignal.libsignal.state.SignalProtocolStore;
import org.session.libsignal.libsignal.state.SignedPreKeyStore;
import org.session.libsignal.libsignal.util.Pair;
import org.session.libsignal.libsignal.util.guava.Optional;

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

import static org.session.libsignal.libsignal.state.SessionState.UnacknowledgedPreKeyMessageItems;

/**
 * The main entry point for Signal Protocol encrypt/decrypt operations.
 *
 * Once a session has been established with {@link SessionBuilder},
 * this class can be used for all encrypt/decrypt operations within
 * that session.
 *
 * @author Moxie Marlinspike
 */
public class SessionCipher {

  public static final Object SESSION_LOCK = new Object();

  private final SessionStore          sessionStore;
  private final IdentityKeyStore      identityKeyStore;
  private final SessionBuilder        sessionBuilder;
  private final PreKeyStore           preKeyStore;
  private final SignalProtocolAddress remoteAddress;

  /**
   * Construct a SessionCipher for encrypt/decrypt operations on a session.
   * In order to use SessionCipher, a session must have already been created
   * and stored using {@link SessionBuilder}.
   *
   * @param  sessionStore The {@link SessionStore} that contains a session for this recipient.
   * @param  remoteAddress  The remote address that messages will be encrypted to or decrypted from.
   */
  public SessionCipher(SessionStore sessionStore, PreKeyStore preKeyStore,
                       SignedPreKeyStore signedPreKeyStore, IdentityKeyStore identityKeyStore,
                       SignalProtocolAddress remoteAddress)
  {
    this.sessionStore     = sessionStore;
    this.preKeyStore      = preKeyStore;
    this.identityKeyStore = identityKeyStore;
    this.remoteAddress    = remoteAddress;
    this.sessionBuilder   = new SessionBuilder(sessionStore, preKeyStore, signedPreKeyStore,
                                               identityKeyStore, remoteAddress);
  }

  public SessionCipher(SignalProtocolStore store, SignalProtocolAddress remoteAddress) {
    this(store, store, store, store, remoteAddress);
  }

  /**
   * Encrypt a message.
   *
   * @param  paddedMessage The plaintext message bytes, optionally padded to a constant multiple.
   * @return A ciphertext message encrypted to the recipient+device tuple.
   */
  public CiphertextMessage encrypt(byte[] paddedMessage) throws UntrustedIdentityException {
    synchronized (SESSION_LOCK) {
      SessionRecord sessionRecord   = sessionStore.loadSession(remoteAddress);
      SessionState  sessionState    = sessionRecord.getSessionState();
      ChainKey      chainKey        = sessionState.getSenderChainKey();
      MessageKeys   messageKeys     = chainKey.getMessageKeys();
      ECPublicKey   senderEphemeral = sessionState.getSenderRatchetKey();
      int           previousCounter = sessionState.getPreviousCounter();
      int           sessionVersion  = sessionState.getSessionVersion();

      byte[]            ciphertextBody    = getCiphertext(messageKeys, paddedMessage);
      CiphertextMessage ciphertextMessage = new SignalMessage(sessionVersion, messageKeys.getMacKey(),
                                                              senderEphemeral, chainKey.getIndex(),
                                                              previousCounter, ciphertextBody,
                                                              sessionState.getLocalIdentityKey(),
                                                              sessionState.getRemoteIdentityKey());

      if (sessionState.hasUnacknowledgedPreKeyMessage()) {
        UnacknowledgedPreKeyMessageItems items = sessionState.getUnacknowledgedPreKeyMessageItems();
        int localRegistrationId = sessionState.getLocalRegistrationId();

        ciphertextMessage = new PreKeySignalMessage(sessionVersion, localRegistrationId, items.getPreKeyId(),
                                                    items.getSignedPreKeyId(), items.getBaseKey(),
                                                    sessionState.getLocalIdentityKey(),
                                                    (SignalMessage) ciphertextMessage);
      }

      sessionState.setSenderChainKey(chainKey.getNextChainKey());

      if (!identityKeyStore.isTrustedIdentity(remoteAddress, sessionState.getRemoteIdentityKey(), IdentityKeyStore.Direction.SENDING)) {
        throw new UntrustedIdentityException(remoteAddress.getName(), sessionState.getRemoteIdentityKey());
      }

      identityKeyStore.saveIdentity(remoteAddress, sessionState.getRemoteIdentityKey());
      sessionStore.storeSession(remoteAddress, sessionRecord);
      return ciphertextMessage;
    }
  }

  /**
   * Decrypt a message.
   *
   * @param  ciphertext The {@link PreKeySignalMessage} to decrypt.
   *
   * @return The plaintext.
   * @throws InvalidMessageException if the input is not valid ciphertext.
   * @throws DuplicateMessageException if the input is a message that has already been received.
   * @throws LegacyMessageException if the input is a message formatted by a protocol version that
   *                                is no longer supported.
   * @throws InvalidKeyIdException when there is no local {@link org.session.libsignal.libsignal.state.PreKeyRecord}
   *                               that corresponds to the PreKey ID in the message.
   * @throws InvalidKeyException when the message is formatted incorrectly.
   * @throws UntrustedIdentityException when the {@link IdentityKey} of the sender is untrusted.
   */
  public byte[] decrypt(PreKeySignalMessage ciphertext)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException,
             InvalidKeyIdException, InvalidKeyException, UntrustedIdentityException
  {
    return decrypt(ciphertext, new NullDecryptionCallback());
  }

  /**
   * Decrypt a message.
   *
   * @param  ciphertext The {@link PreKeySignalMessage} to decrypt.
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
   * @throws InvalidKeyIdException when there is no local {@link org.session.libsignal.libsignal.state.PreKeyRecord}
   *                               that corresponds to the PreKey ID in the message.
   * @throws InvalidKeyException when the message is formatted incorrectly.
   * @throws UntrustedIdentityException when the {@link IdentityKey} of the sender is untrusted.
   */
  public byte[] decrypt(PreKeySignalMessage ciphertext, DecryptionCallback callback)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException,
             InvalidKeyIdException, InvalidKeyException, UntrustedIdentityException
  {
    synchronized (SESSION_LOCK) {
      SessionRecord     sessionRecord    = sessionStore.loadSession(remoteAddress);
      Optional<Integer> unsignedPreKeyId = sessionBuilder.process(sessionRecord, ciphertext);
      byte[]            plaintext        = decrypt(sessionRecord, ciphertext.getWhisperMessage());

      callback.handlePlaintext(plaintext);

      sessionStore.storeSession(remoteAddress, sessionRecord);

      if (unsignedPreKeyId.isPresent()) {
        preKeyStore.removePreKey(unsignedPreKeyId.get());
      }

      return plaintext;
    }
  }

  /**
   * Decrypt a message.
   *
   * @param  ciphertext The {@link SignalMessage} to decrypt.
   *
   * @return The plaintext.
   * @throws InvalidMessageException if the input is not valid ciphertext.
   * @throws DuplicateMessageException if the input is a message that has already been received.
   * @throws LegacyMessageException if the input is a message formatted by a protocol version that
   *                                is no longer supported.
   * @throws NoSessionException if there is no established session for this contact.
   */
  public byte[] decrypt(SignalMessage ciphertext)
      throws InvalidMessageException, DuplicateMessageException, LegacyMessageException,
      NoSessionException, UntrustedIdentityException
  {
    return decrypt(ciphertext, new NullDecryptionCallback());
  }

  /**
   * Decrypt a message.
   *
   * @param  ciphertext The {@link SignalMessage} to decrypt.
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
  public byte[] decrypt(SignalMessage ciphertext, DecryptionCallback callback)
      throws InvalidMessageException, DuplicateMessageException, LegacyMessageException,
             NoSessionException, UntrustedIdentityException
  {
    synchronized (SESSION_LOCK) {

      if (!sessionStore.containsSession(remoteAddress)) {
        throw new NoSessionException("No session for: " + remoteAddress);
      }

      SessionRecord sessionRecord = sessionStore.loadSession(remoteAddress);
      byte[]        plaintext     = decrypt(sessionRecord, ciphertext);

      if (!identityKeyStore.isTrustedIdentity(remoteAddress, sessionRecord.getSessionState().getRemoteIdentityKey(), IdentityKeyStore.Direction.RECEIVING)) {
        throw new UntrustedIdentityException(remoteAddress.getName(), sessionRecord.getSessionState().getRemoteIdentityKey());
      }

      identityKeyStore.saveIdentity(remoteAddress, sessionRecord.getSessionState().getRemoteIdentityKey());

      callback.handlePlaintext(plaintext);

      sessionStore.storeSession(remoteAddress, sessionRecord);

      return plaintext;
    }
  }

  private byte[] decrypt(SessionRecord sessionRecord, SignalMessage ciphertext)
      throws DuplicateMessageException, LegacyMessageException, InvalidMessageException
  {
    synchronized (SESSION_LOCK) {
      Iterator<SessionState> previousStates = sessionRecord.getPreviousSessionStates().iterator();
      List<Exception>        exceptions     = new LinkedList<Exception>();

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

  private byte[] decrypt(SessionState sessionState, SignalMessage ciphertextMessage)
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

    ECPublicKey    theirEphemeral    = ciphertextMessage.getSenderRatchetKey();
    int            counter           = ciphertextMessage.getCounter();
    ChainKey       chainKey          = getOrCreateChainKey(sessionState, theirEphemeral);
    MessageKeys    messageKeys       = getOrCreateMessageKeys(sessionState, theirEphemeral,
                                                              chainKey, counter);

    ciphertextMessage.verifyMac(sessionState.getRemoteIdentityKey(),
                                sessionState.getLocalIdentityKey(),
                                messageKeys.getMacKey());

    byte[] plaintext = getPlaintext(messageKeys, ciphertextMessage.getBody());

    sessionState.clearUnacknowledgedPreKeyMessage();

    return plaintext;
  }

  public int getRemoteRegistrationId() {
    synchronized (SESSION_LOCK) {
      SessionRecord record = sessionStore.loadSession(remoteAddress);
      return record.getSessionState().getRemoteRegistrationId();
    }
  }

  public int getSessionVersion() {
    synchronized (SESSION_LOCK) {
      if (!sessionStore.containsSession(remoteAddress)) {
          // Loki - If we have no session then we must be using the FallbackSessionCipher
          return FallbackSessionCipher.getSessionVersion();
      }

      SessionRecord record = sessionStore.loadSession(remoteAddress);
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

  private byte[] getCiphertext(MessageKeys messageKeys, byte[] plaintext) {
    try {
      Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, messageKeys.getCipherKey(), messageKeys.getIv());
      return cipher.doFinal(plaintext);
    } catch (IllegalBlockSizeException e) {
      throw new AssertionError(e);
    } catch (BadPaddingException e) {
      throw new AssertionError(e);
    }
  }

  private byte[] getPlaintext(MessageKeys messageKeys, byte[] cipherText)
      throws InvalidMessageException
  {
    try {
      Cipher cipher = getCipher(Cipher.DECRYPT_MODE, messageKeys.getCipherKey(), messageKeys.getIv());
      return cipher.doFinal(cipherText);
    } catch (IllegalBlockSizeException e) {
      throw new InvalidMessageException(e);
    } catch (BadPaddingException e) {
      throw new InvalidMessageException(e);
    }
  }

  private Cipher getCipher(int mode, SecretKeySpec key, IvParameterSpec iv) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
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

  private static class NullDecryptionCallback implements DecryptionCallback {
    @Override
    public void handlePlaintext(byte[] plaintext) {}
  }
}

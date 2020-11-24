/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal;


import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.ratchet.AliceSignalProtocolParameters;
import org.whispersystems.libsignal.ratchet.BobSignalProtocolParameters;
import org.whispersystems.libsignal.ratchet.RatchetingSession;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.libsignal.util.guava.Optional;

/**
 * SessionBuilder is responsible for setting up encrypted sessions.
 * Once a session has been established, {@link org.whispersystems.libsignal.SessionCipher}
 * can be used to encrypt/decrypt messages in that session.
 * <p>
 * Sessions are built from one of three different possible vectors:
 * <ol>
 *   <li>A {@link org.whispersystems.libsignal.state.PreKeyBundle} retrieved from a server.</li>
 *   <li>A {@link PreKeySignalMessage} received from a client.</li>
 * </ol>
 *
 * Sessions are constructed per recipientId + deviceId tuple.  Remote logical users are identified
 * by their recipientId, and each logical recipientId can have multiple physical devices.
 *
 * @author Moxie Marlinspike
 */
public class SessionBuilder {

  private static final String TAG = SessionBuilder.class.getSimpleName();

  private final SessionStore          sessionStore;
  private final PreKeyStore           preKeyStore;
  private final SignedPreKeyStore     signedPreKeyStore;
  private final IdentityKeyStore      identityKeyStore;
  private final SignalProtocolAddress remoteAddress;

  /**
   * Constructs a SessionBuilder.
   *
   * @param sessionStore The {@link org.whispersystems.libsignal.state.SessionStore} to store the constructed session in.
   * @param preKeyStore The {@link  org.whispersystems.libsignal.state.PreKeyStore} where the client's local {@link org.whispersystems.libsignal.state.PreKeyRecord}s are stored.
   * @param identityKeyStore The {@link org.whispersystems.libsignal.state.IdentityKeyStore} containing the client's identity key information.
   * @param remoteAddress The address of the remote user to build a session with.
   */
  public SessionBuilder(SessionStore sessionStore,
                        PreKeyStore preKeyStore,
                        SignedPreKeyStore signedPreKeyStore,
                        IdentityKeyStore identityKeyStore,
                        SignalProtocolAddress remoteAddress)
  {
    this.sessionStore      = sessionStore;
    this.preKeyStore       = preKeyStore;
    this.signedPreKeyStore = signedPreKeyStore;
    this.identityKeyStore  = identityKeyStore;
    this.remoteAddress     = remoteAddress;
  }

  /**
   * Constructs a SessionBuilder
   * @param store The {@link SignalProtocolStore} to store all state information in.
   * @param remoteAddress The address of the remote user to build a session with.
   */
  public SessionBuilder(SignalProtocolStore store, SignalProtocolAddress remoteAddress) {
    this(store, store, store, store, remoteAddress);
  }

  /**
   * Build a new session from a received {@link PreKeySignalMessage}.
   *
   * After a session is constructed in this way, the embedded {@link SignalMessage}
   * can be decrypted.
   *
   * @param message The received {@link PreKeySignalMessage}.
   * @throws org.whispersystems.libsignal.InvalidKeyIdException when there is no local
   *                                                             {@link org.whispersystems.libsignal.state.PreKeyRecord}
   *                                                             that corresponds to the PreKey ID in
   *                                                             the message.
   * @throws org.whispersystems.libsignal.InvalidKeyException when the message is formatted incorrectly.
   * @throws org.whispersystems.libsignal.UntrustedIdentityException when the {@link IdentityKey} of the sender is untrusted.
   */
  /*package*/ Optional<Integer> process(SessionRecord sessionRecord, PreKeySignalMessage message)
      throws InvalidKeyIdException, InvalidKeyException, UntrustedIdentityException
  {
    IdentityKey theirIdentityKey = message.getIdentityKey();

    if (!identityKeyStore.isTrustedIdentity(remoteAddress, theirIdentityKey, IdentityKeyStore.Direction.RECEIVING)) {
      throw new UntrustedIdentityException(remoteAddress.getName(), theirIdentityKey);
    }

    Optional<Integer> unsignedPreKeyId = processV3(sessionRecord, message);

    identityKeyStore.saveIdentity(remoteAddress, theirIdentityKey);

    return unsignedPreKeyId;
  }

  private Optional<Integer> processV3(SessionRecord sessionRecord, PreKeySignalMessage message)
      throws UntrustedIdentityException, InvalidKeyIdException, InvalidKeyException
  {

    if (sessionRecord.hasSessionState(message.getMessageVersion(), message.getBaseKey().serialize())) {
      Log.w(TAG, "We've already setup a session for this V3 message, letting bundled message fall through...");
      return Optional.absent();
    }

    ECKeyPair ourSignedPreKey = signedPreKeyStore.loadSignedPreKey(message.getSignedPreKeyId()).getKeyPair();

    BobSignalProtocolParameters.Builder parameters = BobSignalProtocolParameters.newBuilder();

    parameters.setTheirBaseKey(message.getBaseKey())
              .setTheirIdentityKey(message.getIdentityKey())
              .setOurIdentityKey(identityKeyStore.getIdentityKeyPair())
              .setOurSignedPreKey(ourSignedPreKey)
              .setOurRatchetKey(ourSignedPreKey);

    if (message.getPreKeyId().isPresent()) {
      parameters.setOurOneTimePreKey(Optional.of(preKeyStore.loadPreKey(message.getPreKeyId().get()).getKeyPair()));
    } else {
      parameters.setOurOneTimePreKey(Optional.<ECKeyPair>absent());
    }

    if (!sessionRecord.isFresh()) sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(), parameters.create());

    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());
    sessionRecord.getSessionState().setAliceBaseKey(message.getBaseKey().serialize());

    if (message.getPreKeyId().isPresent()) {
      return message.getPreKeyId();
    } else {
      return Optional.absent();
    }
  }

  /**
   * Build a new session from a {@link org.whispersystems.libsignal.state.PreKeyBundle} retrieved from
   * a server.
   *
   * @param preKey A PreKey for the destination recipient, retrieved from a server.
   * @throws InvalidKeyException when the {@link org.whispersystems.libsignal.state.PreKeyBundle} is
   *                             badly formatted.
   * @throws org.whispersystems.libsignal.UntrustedIdentityException when the sender's
   *                                                                  {@link IdentityKey} is not
   *                                                                  trusted.
   */
  public void process(PreKeyBundle preKey) throws InvalidKeyException, UntrustedIdentityException {
    synchronized (SessionCipher.SESSION_LOCK) {
      if (!identityKeyStore.isTrustedIdentity(remoteAddress, preKey.getIdentityKey(), IdentityKeyStore.Direction.SENDING)) {
        throw new UntrustedIdentityException(remoteAddress.getName(), preKey.getIdentityKey());
      }

      if (preKey.getSignedPreKey() != null &&
          !Curve.verifySignature(preKey.getIdentityKey().getPublicKey(),
                                 preKey.getSignedPreKey().serialize(),
                                 preKey.getSignedPreKeySignature()))
      {
        throw new InvalidKeyException("Invalid signature on device key!");
      }

      if (preKey.getSignedPreKey() == null) {
        throw new InvalidKeyException("No signed prekey!");
      }

      SessionRecord         sessionRecord        = sessionStore.loadSession(remoteAddress);
      ECKeyPair             ourBaseKey           = Curve.generateKeyPair();
      ECPublicKey           theirSignedPreKey    = preKey.getSignedPreKey();
      Optional<ECPublicKey> theirOneTimePreKey   = Optional.fromNullable(preKey.getPreKey());
      Optional<Integer>     theirOneTimePreKeyId = theirOneTimePreKey.isPresent() ? Optional.of(preKey.getPreKeyId()) :
                                                                                    Optional.<Integer>absent();

      AliceSignalProtocolParameters.Builder parameters = AliceSignalProtocolParameters.newBuilder();

      parameters.setOurBaseKey(ourBaseKey)
                .setOurIdentityKey(identityKeyStore.getIdentityKeyPair())
                .setTheirIdentityKey(preKey.getIdentityKey())
                .setTheirSignedPreKey(theirSignedPreKey)
                .setTheirRatchetKey(theirSignedPreKey)
                .setTheirOneTimePreKey(theirOneTimePreKey);

      if (!sessionRecord.isFresh()) sessionRecord.archiveCurrentState();

      RatchetingSession.initializeSession(sessionRecord.getSessionState(), parameters.create());

      sessionRecord.getSessionState().setUnacknowledgedPreKeyMessage(theirOneTimePreKeyId, preKey.getSignedPreKeyId(), ourBaseKey.getPublicKey());
      sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
      sessionRecord.getSessionState().setRemoteRegistrationId(preKey.getRegistrationId());
      sessionRecord.getSessionState().setAliceBaseKey(ourBaseKey.getPublicKey().serialize());

      identityKeyStore.saveIdentity(remoteAddress, preKey.getIdentityKey());
      sessionStore.storeSession(remoteAddress, sessionRecord);
    }
  }

}

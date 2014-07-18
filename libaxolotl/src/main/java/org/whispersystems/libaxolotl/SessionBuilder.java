package org.whispersystems.libaxolotl;

import android.util.Log;

import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.ratchet.RatchetingSession;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;
import org.whispersystems.libaxolotl.util.KeyHelper;
import org.whispersystems.libaxolotl.util.Medium;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.security.MessageDigest;

import static org.whispersystems.libaxolotl.ratchet.RatchetingSession.AxolotlParameters;

/**
 * SessionBuilder is responsible for setting up encrypted sessions.
 * Once a session has been established, {@link org.whispersystems.libaxolotl.SessionCipher}
 * can be used to encrypt/decrypt messages in that session.
 * <p>
 * Sessions are built from one of three different possible vectors:
 * <ol>
 *   <li>A {@link org.whispersystems.libaxolotl.state.PreKeyBundle} retrieved from a server.</li>
 *   <li>A {@link org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage} received from a client.</li>
 *   <li>A {@link org.whispersystems.libaxolotl.protocol.KeyExchangeMessage} sent to or received from a client.</li>
 * </ol>
 *
 * Sessions are constructed per recipientId + deviceId tuple.  Remote logical users are identified
 * by their recipientId, and each logical recipientId can have multiple physical devices.
 *
 * @author Moxie Marlinspike
 */
public class SessionBuilder {

  private static final String TAG = SessionBuilder.class.getSimpleName();

  private final SessionStore      sessionStore;
  private final PreKeyStore       preKeyStore;
  private final SignedPreKeyStore signedPreKeyStore;
  private final IdentityKeyStore  identityKeyStore;
  private final long              recipientId;
  private final int               deviceId;

  /**
   * Constructs a SessionBuilder.
   *
   * @param sessionStore The {@link org.whispersystems.libaxolotl.state.SessionStore} to store the constructed session in.
   * @param preKeyStore The {@link  org.whispersystems.libaxolotl.state.PreKeyStore} where the client's local {@link org.whispersystems.libaxolotl.state.PreKeyRecord}s are stored.
   * @param identityKeyStore The {@link org.whispersystems.libaxolotl.state.IdentityKeyStore} containing the client's identity key information.
   * @param recipientId The recipient ID of the remote user to build a session with.
   * @param deviceId The device ID of the remote user's physical device.
   */
  public SessionBuilder(SessionStore sessionStore,
                        PreKeyStore preKeyStore,
                        SignedPreKeyStore signedPreKeyStore,
                        IdentityKeyStore identityKeyStore,
                        long recipientId, int deviceId)
  {
    this.sessionStore      = sessionStore;
    this.preKeyStore       = preKeyStore;
    this.signedPreKeyStore = signedPreKeyStore;
    this.identityKeyStore  = identityKeyStore;
    this.recipientId       = recipientId;
    this.deviceId          = deviceId;
  }

  /**
   * Build a new session from a received {@link org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage}.
   *
   * After a session is constructed in this way, the embedded {@link org.whispersystems.libaxolotl.protocol.WhisperMessage}
   * can be decrypted.
   *
   * @param message The received {@link org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage}.
   * @throws org.whispersystems.libaxolotl.InvalidKeyIdException when there is no local
   *                                                             {@link org.whispersystems.libaxolotl.state.PreKeyRecord}
   *                                                             that corresponds to the PreKey ID in
   *                                                             the message.
   * @throws org.whispersystems.libaxolotl.InvalidKeyException when the message is formatted incorrectly.
   * @throws org.whispersystems.libaxolotl.UntrustedIdentityException when the {@link IdentityKey} of the sender is untrusted.
   */
  public void process(PreKeyWhisperMessage message)
      throws InvalidKeyIdException, InvalidKeyException, UntrustedIdentityException
  {
    int         messageVersion   = message.getMessageVersion();
    IdentityKey theirIdentityKey = message.getIdentityKey();

    if (!identityKeyStore.isTrustedIdentity(recipientId, theirIdentityKey)) {
      throw new UntrustedIdentityException();
    }

    if      (messageVersion == 2) processV2(message);
    else if (messageVersion == 3) processV3(message);
    else                          throw new AssertionError("Unknown version: " + messageVersion);

    identityKeyStore.saveIdentity(recipientId, theirIdentityKey);
  }

  private void processV3(PreKeyWhisperMessage message)
      throws UntrustedIdentityException, InvalidKeyIdException, InvalidKeyException
  {
    SessionRecord sessionRecord = sessionStore.loadSession(recipientId, deviceId);

    if (sessionRecord.hasSessionState(message.getMessageVersion(), message.getBaseKey().serialize())) {
      Log.w(TAG, "We've already setup a session for this V3 message, letting bundled message fall through...");
      return;
    }

    boolean simultaneousInitiate = sessionRecord.getSessionState().hasUnacknowledgedPreKeyMessage();

    AxolotlParameters.Builder parameters = AxolotlParameters.newBuilder();

    parameters.setTheirBaseKey(message.getBaseKey());
    parameters.setTheirEphemeralKey(message.getWhisperMessage().getSenderEphemeral());
    parameters.setTheirPreKey(Optional.of(message.getBaseKey()));
    parameters.setTheirIdentityKey(message.getIdentityKey());

    parameters.setOurBaseKey(signedPreKeyStore.loadSignedPreKey(message.getSignedPreKeyId()).getKeyPair());
    parameters.setOurEphemeralKey(parameters.getOurBaseKey());
    parameters.setOurIdentityKey(identityKeyStore.getIdentityKeyPair());

    if (message.getPreKeyId() >= 0) parameters.setOurPreKey(Optional.of(preKeyStore.loadPreKey(message.getPreKeyId()).getKeyPair()));
    else                            parameters.setOurPreKey(Optional.<ECKeyPair>absent());

    if (!simultaneousInitiate) sessionRecord.reset();
    else                       sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(), message.getMessageVersion(), parameters.create());

    if (!MessageDigest.isEqual(sessionRecord.getSessionState().getVerification(), message.getVerification())) {
      throw new InvalidKeyException("Verification secret mismatch!");
    }

    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());
    sessionRecord.getSessionState().setAliceBaseKey(message.getBaseKey().serialize());

    if (simultaneousInitiate) sessionRecord.getSessionState().setNeedsRefresh(true);

    sessionStore.storeSession(recipientId, deviceId, sessionRecord);

    if (message.getPreKeyId() >= 0 && message.getPreKeyId() != Medium.MAX_VALUE) {
      preKeyStore.removePreKey(message.getPreKeyId());
    }
  }

  private void processV2(PreKeyWhisperMessage message)
      throws UntrustedIdentityException, InvalidKeyIdException, InvalidKeyException
  {

    if (!preKeyStore.containsPreKey(message.getPreKeyId()) &&
        sessionStore.containsSession(recipientId, deviceId))
    {
      Log.w(TAG, "We've already processed the prekey part of this V2 session, letting bundled message fall through...");
      return;
    }

    SessionRecord             sessionRecord        = sessionStore.loadSession(recipientId, deviceId);
    boolean                   simultaneousInitiate = sessionRecord.getSessionState().hasUnacknowledgedPreKeyMessage();
    AxolotlParameters.Builder parameters           = RatchetingSession.AxolotlParameters.newBuilder();

    parameters.setTheirBaseKey(message.getBaseKey());
    parameters.setTheirEphemeralKey(message.getWhisperMessage().getSenderEphemeral());
    parameters.setTheirPreKey(Optional.<ECPublicKey>absent());
    parameters.setTheirIdentityKey(message.getIdentityKey());

    parameters.setOurBaseKey(preKeyStore.loadPreKey(message.getPreKeyId()).getKeyPair());
    parameters.setOurEphemeralKey(parameters.getOurBaseKey());
    parameters.setOurPreKey(Optional.<ECKeyPair>absent());
    parameters.setOurIdentityKey(identityKeyStore.getIdentityKeyPair());

    if (!simultaneousInitiate) sessionRecord.reset();
    else                       sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(), message.getMessageVersion(), parameters.create());

    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());

    if (simultaneousInitiate) sessionRecord.getSessionState().setNeedsRefresh(true);

    sessionStore.storeSession(recipientId, deviceId, sessionRecord);

    if (message.getPreKeyId() != Medium.MAX_VALUE) {
      preKeyStore.removePreKey(message.getPreKeyId());
    }
  }

  /**
   * Build a new session from a {@link org.whispersystems.libaxolotl.state.PreKeyBundle} retrieved from
   * a server.
   *
   * @param preKey A PreKey for the destination recipient, retrieved from a server.
   * @throws InvalidKeyException when the {@link org.whispersystems.libaxolotl.state.PreKeyBundle} is
   *                             badly formatted.
   * @throws org.whispersystems.libaxolotl.UntrustedIdentityException when the sender's
   *                                                                  {@link IdentityKey} is not
   *                                                                  trusted.
   */
  public void process(PreKeyBundle preKey) throws InvalidKeyException, UntrustedIdentityException {

    if (!identityKeyStore.isTrustedIdentity(recipientId, preKey.getIdentityKey())) {
      throw new UntrustedIdentityException();
    }

    if (preKey.getSignedPreKey() != null &&
        !Curve.verifySignature(preKey.getIdentityKey().getPublicKey(),
                               preKey.getSignedPreKey().serialize(),
                               preKey.getSignedPreKeySignature()))
    {
      throw new InvalidKeyException("Invalid signature on device key!");
    }

    if (preKey.getSignedPreKey() == null && preKey.getPreKey() == null) {
      throw new InvalidKeyException("Both signed and unsigned prekeys are absent!");
    }

    SessionRecord             sessionRecord = sessionStore.loadSession(recipientId, deviceId);
    AxolotlParameters.Builder parameters    = AxolotlParameters.newBuilder();
    ECKeyPair                 ourBaseKey    = Curve.generateKeyPair(true);

    parameters.setOurIdentityKey(identityKeyStore.getIdentityKeyPair());
    parameters.setOurBaseKey(ourBaseKey);
    parameters.setOurEphemeralKey(Curve.generateKeyPair(true));
    parameters.setOurPreKey(Optional.of(parameters.getOurBaseKey()));

    parameters.setTheirIdentityKey(preKey.getIdentityKey());
    if (preKey.getSignedPreKey() == null) parameters.setTheirBaseKey(preKey.getPreKey());
    else                                  parameters.setTheirBaseKey(preKey.getSignedPreKey());
    parameters.setTheirEphemeralKey(parameters.getTheirBaseKey());
    parameters.setTheirPreKey(Optional.fromNullable(preKey.getPreKey()));

    if (sessionRecord.getSessionState().getNeedsRefresh()) sessionRecord.archiveCurrentState();
    else                                                   sessionRecord.reset();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        preKey.getSignedPreKey() == null ? 2 : 3,
                                        parameters.create());

    sessionRecord.getSessionState().setUnacknowledgedPreKeyMessage(preKey.getPreKeyId(), preKey.getSignedPreKeyId(), ourBaseKey.getPublicKey());
    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(preKey.getRegistrationId());

    sessionStore.storeSession(recipientId, deviceId, sessionRecord);
    identityKeyStore.saveIdentity(recipientId, preKey.getIdentityKey());
  }

  /**
   * Build a new session from a {@link org.whispersystems.libaxolotl.protocol.KeyExchangeMessage}
   * received from a remote client.
   *
   * @param message The received KeyExchangeMessage.
   * @return The KeyExchangeMessage to respond with, or null if no response is necessary.
   * @throws InvalidKeyException if the received KeyExchangeMessage is badly formatted.
   */
  public KeyExchangeMessage process(KeyExchangeMessage message)
      throws InvalidKeyException, UntrustedIdentityException, StaleKeyExchangeException
  {

    if (!identityKeyStore.isTrustedIdentity(recipientId, message.getIdentityKey())) {
      throw new UntrustedIdentityException();
    }

    KeyExchangeMessage responseMessage = null;

    if (message.isInitiate()) responseMessage = processInitiate(message);
    else                      processResponse(message);

    return responseMessage;
  }

  private KeyExchangeMessage processInitiate(KeyExchangeMessage message) throws InvalidKeyException {
    AxolotlParameters.Builder parameters    = AxolotlParameters.newBuilder();
    int                       flags         = KeyExchangeMessage.RESPONSE_FLAG;
    SessionRecord             sessionRecord = sessionStore.loadSession(recipientId, deviceId);

    if (message.getVersion() >= 3 &&
        !Curve.verifySignature(message.getIdentityKey().getPublicKey(),
                               message.getBaseKey().serialize(),
                               message.getBaseKeySignature()))
    {
      throw new InvalidKeyException("Bad signature!");
    }

    if (!sessionRecord.getSessionState().hasPendingKeyExchange()) {
      Log.w(TAG, "We don't have a pending initiate...");
      parameters.setOurBaseKey(Curve.generateKeyPair(true));
      parameters.setOurEphemeralKey(Curve.generateKeyPair(true));
      parameters.setOurIdentityKey(identityKeyStore.getIdentityKeyPair());
      parameters.setOurPreKey(Optional.<ECKeyPair>absent());
    } else {
      Log.w(TAG, "We already have a pending initiate, responding as simultaneous initiate...");
      parameters.setOurBaseKey(sessionRecord.getSessionState().getPendingKeyExchangeBaseKey());
      parameters.setOurEphemeralKey(sessionRecord.getSessionState().getPendingKeyExchangeEphemeralKey());
      parameters.setOurIdentityKey(sessionRecord.getSessionState().getPendingKeyExchangeIdentityKey());
      parameters.setOurPreKey(Optional.<ECKeyPair>absent());
      flags |= KeyExchangeMessage.SIMULTAENOUS_INITIATE_FLAG;
    }

    parameters.setTheirBaseKey(message.getBaseKey());
    parameters.setTheirEphemeralKey(message.getEphemeralKey());
    parameters.setTheirPreKey(Optional.<ECPublicKey>absent());
    parameters.setTheirIdentityKey(message.getIdentityKey());

    sessionRecord.reset();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        Math.min(message.getMaxVersion(), CiphertextMessage.CURRENT_VERSION),
                                        parameters.create());

    sessionStore.storeSession(recipientId, deviceId, sessionRecord);
    identityKeyStore.saveIdentity(recipientId, message.getIdentityKey());

    return new KeyExchangeMessage(sessionRecord.getSessionState().getSessionVersion(),
                                  message.getSequence(), flags,
                                  parameters.getOurBaseKey().getPublicKey(), null,
                                  parameters.getOurEphemeralKey().getPublicKey(),
                                  parameters.getOurIdentityKey().getPublicKey(),
                                  sessionRecord.getSessionState().getVerification());
  }

  private void processResponse(KeyExchangeMessage message)
      throws StaleKeyExchangeException, InvalidKeyException
  {
    SessionRecord sessionRecord                  = sessionStore.loadSession(recipientId, deviceId);
    SessionState  sessionState                   = sessionRecord.getSessionState();
    boolean       hasPendingKeyExchange          = sessionState.hasPendingKeyExchange();
    boolean       isSimultaneousInitiateResponse = message.isResponseForSimultaneousInitiate();

    if (!hasPendingKeyExchange || sessionState.getPendingKeyExchangeSequence() != message.getSequence()) {
      Log.w(TAG, "No matching sequence for response. Is simultaneous initiate response: " + isSimultaneousInitiateResponse);
      if (!isSimultaneousInitiateResponse) throw new StaleKeyExchangeException();
      else                                 return;
    }

    AxolotlParameters parameters =
        AxolotlParameters.newBuilder()
                         .setOurBaseKey(sessionRecord.getSessionState().getPendingKeyExchangeBaseKey())
                         .setOurEphemeralKey(sessionRecord.getSessionState().getPendingKeyExchangeEphemeralKey())
                         .setOurPreKey(Optional.<ECKeyPair>absent())
                         .setOurIdentityKey(sessionRecord.getSessionState().getPendingKeyExchangeIdentityKey())
                         .setTheirBaseKey(message.getBaseKey())
                         .setTheirEphemeralKey(message.getEphemeralKey())
                         .setTheirPreKey(Optional.<ECPublicKey>absent())
                         .setTheirIdentityKey(message.getIdentityKey())
                         .create();

    sessionRecord.reset();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        Math.min(message.getMaxVersion(), CiphertextMessage.CURRENT_VERSION),
                                        parameters);

    if (sessionRecord.getSessionState().getSessionVersion() >= 3 &&
        !MessageDigest.isEqual(message.getVerificationTag(),
                               sessionRecord.getSessionState().getVerification()))
    {
      throw new InvalidKeyException("Verification tag doesn't match!");
    }

    sessionStore.storeSession(recipientId, deviceId, sessionRecord);
    identityKeyStore.saveIdentity(recipientId, message.getIdentityKey());

  }

  /**
   * Initiate a new session by sending an initial KeyExchangeMessage to the recipient.
   *
   * @return the KeyExchangeMessage to deliver.
   */
  public KeyExchangeMessage process() {
    int             sequence      = KeyHelper.getRandomSequence(65534) + 1;
    int             flags         = KeyExchangeMessage.INITIATE_FLAG;
    ECKeyPair       baseKey       = Curve.generateKeyPair(true);
    ECKeyPair       ephemeralKey  = Curve.generateKeyPair(true);
    IdentityKeyPair identityKey   = identityKeyStore.getIdentityKeyPair();
    SessionRecord   sessionRecord = sessionStore.loadSession(recipientId, deviceId);

    sessionRecord.getSessionState().setPendingKeyExchange(sequence, baseKey, ephemeralKey, identityKey);
    sessionStore.storeSession(recipientId, deviceId, sessionRecord);

    return new KeyExchangeMessage(2, sequence, flags, baseKey.getPublicKey(), null,
                                  ephemeralKey.getPublicKey(), identityKey.getPublicKey(), null);
  }


}

package org.whispersystems.libaxolotl;

import android.util.Log;

import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.ratchet.RatchetingSession;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKey;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionState;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.util.KeyHelper;
import org.whispersystems.libaxolotl.util.Medium;

/**
 * SessionBuilder is responsible for setting up encrypted sessions.
 * Once a session has been established, {@link org.whispersystems.libaxolotl.SessionCipher}
 * can be used to encrypt/decrypt messages in that session.
 * <p>
 * Sessions are built from one of three different possible vectors:
 * <ol>
 *   <li>A {@link org.whispersystems.libaxolotl.state.PreKey} retrieved from a server.</li>
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

  private final SessionStore     sessionStore;
  private final PreKeyStore      preKeyStore;
  private final IdentityKeyStore identityKeyStore;
  private final long             recipientId;
  private final int              deviceId;

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
                        IdentityKeyStore identityKeyStore,
                        long recipientId, int deviceId)
  {
    this.sessionStore     = sessionStore;
    this.preKeyStore      = preKeyStore;
    this.identityKeyStore = identityKeyStore;
    this.recipientId      = recipientId;
    this.deviceId         = deviceId;
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
    int         preKeyId          = message.getPreKeyId();
    ECPublicKey theirBaseKey      = message.getBaseKey();
    ECPublicKey theirEphemeralKey = message.getWhisperMessage().getSenderEphemeral();
    IdentityKey theirIdentityKey  = message.getIdentityKey();

    if (!identityKeyStore.isTrustedIdentity(recipientId, theirIdentityKey)) {
      throw new UntrustedIdentityException();
    }

    if (!preKeyStore.contains(preKeyId) &&
        sessionStore.contains(recipientId, deviceId))
    {
      Log.w(TAG, "We've already processed the prekey part, letting bundled message fall through...");
      return;
    }

    if (!preKeyStore.contains(preKeyId))
      throw new InvalidKeyIdException("No such prekey: " + preKeyId);

    SessionRecord   sessionRecord        = sessionStore.load(recipientId, deviceId);
    PreKeyRecord    preKeyRecord         = preKeyStore.load(preKeyId);
    ECKeyPair       ourBaseKey           = preKeyRecord.getKeyPair();
    ECKeyPair       ourEphemeralKey      = ourBaseKey;
    IdentityKeyPair ourIdentityKey       = identityKeyStore.getIdentityKeyPair();
    boolean         simultaneousInitiate = sessionRecord.getSessionState().hasPendingPreKey();

    if (!simultaneousInitiate) sessionRecord.reset();
    else                       sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        ourBaseKey, theirBaseKey,
                                        ourEphemeralKey, theirEphemeralKey,
                                        ourIdentityKey, theirIdentityKey);

    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());

    if (simultaneousInitiate) sessionRecord.getSessionState().setNeedsRefresh(true);

    sessionStore.store(recipientId, deviceId, sessionRecord);

    if (preKeyId != Medium.MAX_VALUE) {
      preKeyStore.remove(preKeyId);
    }

    identityKeyStore.saveIdentity(recipientId, theirIdentityKey);
  }

  /**
   * Build a new session from a {@link org.whispersystems.libaxolotl.state.PreKey} retrieved from
   * a server.
   *
   * @param preKey A PreKey for the destination recipient, retrieved from a server.
   * @throws InvalidKeyException when the {@link org.whispersystems.libaxolotl.state.PreKey} is
   *                             badly formatted.
   * @throws org.whispersystems.libaxolotl.UntrustedIdentityException when the sender's
   *                                                                  {@link IdentityKey} is not
   *                                                                  trusted.
   */
  public void process(PreKey preKey) throws InvalidKeyException, UntrustedIdentityException {

    if (!identityKeyStore.isTrustedIdentity(recipientId, preKey.getIdentityKey())) {
      throw new UntrustedIdentityException();
    }

    SessionRecord   sessionRecord     = sessionStore.load(recipientId, deviceId);
    ECKeyPair       ourBaseKey        = Curve.generateKeyPair(true);
    ECKeyPair       ourEphemeralKey   = Curve.generateKeyPair(true);
    ECPublicKey     theirBaseKey      = preKey.getPublicKey();
    ECPublicKey     theirEphemeralKey = theirBaseKey;
    IdentityKey     theirIdentityKey  = preKey.getIdentityKey();
    IdentityKeyPair ourIdentityKey    = identityKeyStore.getIdentityKeyPair();

    if (sessionRecord.getSessionState().getNeedsRefresh()) sessionRecord.archiveCurrentState();
    else                                                   sessionRecord.reset();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        ourBaseKey, theirBaseKey, ourEphemeralKey,
                                        theirEphemeralKey, ourIdentityKey, theirIdentityKey);

    sessionRecord.getSessionState().setPendingPreKey(preKey.getKeyId(), ourBaseKey.getPublicKey());
    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(preKey.getRegistrationId());

    sessionStore.store(recipientId, deviceId, sessionRecord);

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
    SessionRecord      sessionRecord   = sessionStore.load(recipientId, deviceId);

    Log.w(TAG, "Received key exchange with sequence: " + message.getSequence());

    if (message.isInitiate()) {
      Log.w(TAG, "KeyExchange is an initiate.");
      responseMessage = processInitiate(sessionRecord, message);
    }

    if (message.isResponse()) {
      SessionState sessionState                   = sessionRecord.getSessionState();
      boolean      hasPendingKeyExchange          = sessionState.hasPendingKeyExchange();
      boolean      isSimultaneousInitiateResponse = message.isResponseForSimultaneousInitiate();

      if ((!hasPendingKeyExchange || sessionState.getPendingKeyExchangeSequence() != message.getSequence()) &&
          !isSimultaneousInitiateResponse)
      {
        throw new StaleKeyExchangeException();
      }
    }

    if (message.getSequence() != sessionRecord.getSessionState().getPendingKeyExchangeSequence()) {
      Log.w("KeyExchangeProcessor", "No matching sequence for response. " +
          "Is simultaneous initiate response: " + message.isResponseForSimultaneousInitiate());
      return responseMessage;
    }

    ECKeyPair       ourBaseKey      = sessionRecord.getSessionState().getPendingKeyExchangeBaseKey();
    ECKeyPair       ourEphemeralKey = sessionRecord.getSessionState().getPendingKeyExchangeEphemeralKey();
    IdentityKeyPair ourIdentityKey  = sessionRecord.getSessionState().getPendingKeyExchangeIdentityKey();

    sessionRecord.reset();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        ourBaseKey, message.getBaseKey(),
                                        ourEphemeralKey, message.getEphemeralKey(),
                                        ourIdentityKey, message.getIdentityKey());

    sessionRecord.getSessionState().setSessionVersion(message.getVersion());
    sessionStore.store(recipientId, deviceId, sessionRecord);

    identityKeyStore.saveIdentity(recipientId, message.getIdentityKey());

    return responseMessage;
  }

  private KeyExchangeMessage processInitiate(SessionRecord sessionRecord, KeyExchangeMessage message)
      throws InvalidKeyException
  {
    ECKeyPair       ourBaseKey, ourEphemeralKey;
    IdentityKeyPair ourIdentityKey;

    int flags = KeyExchangeMessage.RESPONSE_FLAG;

    if (!sessionRecord.getSessionState().hasPendingKeyExchange()) {
      Log.w(TAG, "We don't have a pending initiate...");
      ourBaseKey      = Curve.generateKeyPair(true);
      ourEphemeralKey = Curve.generateKeyPair(true);
      ourIdentityKey  = identityKeyStore.getIdentityKeyPair();

      sessionRecord.getSessionState().setPendingKeyExchange(message.getSequence(), ourBaseKey,
                                                            ourEphemeralKey, ourIdentityKey);
    } else {
      Log.w(TAG, "We already have a pending initiate, responding as simultaneous initiate...");
      ourBaseKey      = sessionRecord.getSessionState().getPendingKeyExchangeBaseKey();
      ourEphemeralKey = sessionRecord.getSessionState().getPendingKeyExchangeEphemeralKey();
      ourIdentityKey  = sessionRecord.getSessionState().getPendingKeyExchangeIdentityKey();
      flags          |= KeyExchangeMessage.SIMULTAENOUS_INITIATE_FLAG;

      sessionRecord.getSessionState().setPendingKeyExchange(message.getSequence(), ourBaseKey,
                                                            ourEphemeralKey, ourIdentityKey);
    }

    return new KeyExchangeMessage(message.getSequence(),
                                  flags, ourBaseKey.getPublicKey(),
                                  ourEphemeralKey.getPublicKey(),
                                  ourIdentityKey.getPublicKey());
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
    SessionRecord   sessionRecord = sessionStore.load(recipientId, deviceId);

    sessionRecord.getSessionState().setPendingKeyExchange(sequence, baseKey, ephemeralKey, identityKey);
    sessionStore.store(recipientId, deviceId, sessionRecord);

    return new KeyExchangeMessage(sequence, flags,
                                  baseKey.getPublicKey(),
                                  ephemeralKey.getPublicKey(),
                                  identityKey.getPublicKey());
  }


}

package org.whispersystems.libaxolotl;

import android.util.Log;

import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.ratchet.RatchetingSession;
import org.whispersystems.libaxolotl.state.DeviceKeyRecord;
import org.whispersystems.libaxolotl.state.DeviceKeyStore;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyBundle;
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

  private final SessionStore     sessionStore;
  private final PreKeyStore      preKeyStore;
  private final DeviceKeyStore   deviceKeyStore;
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
                        DeviceKeyStore deviceKeyStore,
                        IdentityKeyStore identityKeyStore,
                        long recipientId, int deviceId)
  {
    this.sessionStore     = sessionStore;
    this.preKeyStore      = preKeyStore;
    this.deviceKeyStore   = deviceKeyStore;
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
    SessionRecord sessionRecord     = sessionStore.loadSession(recipientId, deviceId);
    int           preKeyId          = message.getPreKeyId();
    int           deviceKeyId       = message.getDeviceKeyId();
    ECPublicKey   theirBaseKey      = message.getBaseKey();
    ECPublicKey   theirEphemeralKey = message.getWhisperMessage().getSenderEphemeral();
    IdentityKey   theirIdentityKey  = message.getIdentityKey();

    if (sessionRecord.hasSessionState(message.getMessageVersion(), theirBaseKey.serialize())) {
      Log.w(TAG, "We've already setup a session for this V3 message, letting bundled message fall through...");
      return;
    }

    if (preKeyId >=0 && !preKeyStore.containsPreKey(preKeyId))
      throw new InvalidKeyIdException("No such prekey: " + preKeyId);

    if (!deviceKeyStore.containsDeviceKey(deviceKeyId))
      throw new InvalidKeyIdException("No such device key: " + deviceKeyId);

    PreKeyRecord    preKeyRecord         = preKeyId >= 0 ? preKeyStore.loadPreKey(preKeyId) : null;
    DeviceKeyRecord deviceKeyRecord      = deviceKeyStore.loadDeviceKey(deviceKeyId);
    ECKeyPair       ourPreKey            = preKeyRecord != null ? preKeyRecord.getKeyPair() : null;
    ECPublicKey     theirPreKey          = theirBaseKey;
    ECKeyPair       ourBaseKey           = deviceKeyRecord.getKeyPair();
    ECKeyPair       ourEphemeralKey      = ourBaseKey;
    IdentityKeyPair ourIdentityKey       = identityKeyStore.getIdentityKeyPair();
    boolean         simultaneousInitiate = sessionRecord.getSessionState().hasPendingPreKey();

    if (!simultaneousInitiate) sessionRecord.reset();
    else                       sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        message.getMessageVersion(),
                                        ourBaseKey, theirBaseKey,
                                        ourEphemeralKey, theirEphemeralKey,
                                        ourPreKey, theirPreKey,
                                        ourIdentityKey, theirIdentityKey);

    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());
    sessionRecord.getSessionState().setAliceBaseKey(theirBaseKey.serialize());
    sessionRecord.getSessionState().setSessionVersion(message.getMessageVersion());

    if (simultaneousInitiate) sessionRecord.getSessionState().setNeedsRefresh(true);

    sessionStore.storeSession(recipientId, deviceId, sessionRecord);

    if (preKeyId >= 0 && preKeyId != Medium.MAX_VALUE) {
      preKeyStore.removePreKey(preKeyId);
    }
  }

  private void processV2(PreKeyWhisperMessage message)
      throws UntrustedIdentityException, InvalidKeyIdException, InvalidKeyException
  {
    int           preKeyId          = message.getPreKeyId();
    ECPublicKey   theirBaseKey      = message.getBaseKey();
    ECPublicKey   theirEphemeralKey = message.getWhisperMessage().getSenderEphemeral();
    IdentityKey   theirIdentityKey  = message.getIdentityKey();

    if (!preKeyStore.containsPreKey(preKeyId) &&
        sessionStore.containsSession(recipientId, deviceId))
    {
      Log.w(TAG, "We've already processed the prekey part of this V2 session, letting bundled message fall through...");
      return;
    }

    if (!preKeyStore.containsPreKey(preKeyId))
      throw new InvalidKeyIdException("No such prekey: " + preKeyId);

    SessionRecord   sessionRecord        = sessionStore.loadSession(recipientId, deviceId);
    PreKeyRecord    preKeyRecord         = preKeyStore.loadPreKey(preKeyId);
    ECKeyPair       ourBaseKey           = preKeyRecord.getKeyPair();
    ECKeyPair       ourEphemeralKey      = ourBaseKey;
    IdentityKeyPair ourIdentityKey       = identityKeyStore.getIdentityKeyPair();
    boolean         simultaneousInitiate = sessionRecord.getSessionState().hasPendingPreKey();

    if (!simultaneousInitiate) sessionRecord.reset();
    else                       sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        message.getMessageVersion(),
                                        ourBaseKey, theirBaseKey,
                                        ourEphemeralKey, theirEphemeralKey,
                                        null, null,
                                        ourIdentityKey, theirIdentityKey);

    sessionRecord.getSessionState().setLocalRegistrationId(identityKeyStore.getLocalRegistrationId());
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());
    sessionRecord.getSessionState().setSessionVersion(message.getMessageVersion());

    if (simultaneousInitiate) sessionRecord.getSessionState().setNeedsRefresh(true);

    sessionStore.storeSession(recipientId, deviceId, sessionRecord);

    if (preKeyId != Medium.MAX_VALUE) {
      preKeyStore.removePreKey(preKeyId);
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

    if (preKey.getDeviceKey() != null &&
        !Curve.verifySignature(preKey.getIdentityKey().getPublicKey(),
                               preKey.getDeviceKey().serialize(),
                               preKey.getDeviceKeySignature()))
    {
      throw new InvalidKeyException("Invalid signature on device key!");
    }

    SessionRecord   sessionRecord     = sessionStore.loadSession(recipientId, deviceId);
    IdentityKeyPair ourIdentityKey    = identityKeyStore.getIdentityKeyPair();
    ECKeyPair       ourBaseKey        = Curve.generateKeyPair(true);
    ECKeyPair       ourEphemeralKey   = Curve.generateKeyPair(true);
    ECKeyPair       ourPreKey         = ourBaseKey;

    IdentityKey     theirIdentityKey  = preKey.getIdentityKey();
    ECPublicKey     theirPreKey       = preKey.getPreKey();
    ECPublicKey     theirBaseKey      = preKey.getDeviceKey() == null ? preKey.getPreKey() : preKey.getDeviceKey();
    ECPublicKey     theirEphemeralKey = theirBaseKey;

    if (sessionRecord.getSessionState().getNeedsRefresh()) sessionRecord.archiveCurrentState();
    else                                                   sessionRecord.reset();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        preKey.getDeviceKey() == null ? 2 : 3,
                                        ourBaseKey, theirBaseKey, ourEphemeralKey,
                                        theirEphemeralKey, ourPreKey, theirPreKey,
                                        ourIdentityKey, theirIdentityKey);

    sessionRecord.getSessionState().setPendingPreKey(preKey.getPreKeyId(), preKey.getDeviceKeyId(), ourBaseKey.getPublicKey());
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
    SessionRecord      sessionRecord   = sessionStore.loadSession(recipientId, deviceId);

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
                                        2,
                                        ourBaseKey, message.getBaseKey(),
                                        ourEphemeralKey, message.getEphemeralKey(),
                                        null, null,
                                        ourIdentityKey, message.getIdentityKey());

    sessionRecord.getSessionState().setSessionVersion(message.getVersion());
    sessionStore.storeSession(recipientId, deviceId, sessionRecord);

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
    SessionRecord   sessionRecord = sessionStore.loadSession(recipientId, deviceId);

    sessionRecord.getSessionState().setPendingKeyExchange(sequence, baseKey, ephemeralKey, identityKey);
    sessionStore.storeSession(recipientId, deviceId, sessionRecord);

    return new KeyExchangeMessage(sequence, flags,
                                  baseKey.getPublicKey(),
                                  ephemeralKey.getPublicKey(),
                                  identityKey.getPublicKey());
  }


}

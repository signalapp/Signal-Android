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
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.util.Medium;

public class SessionBuilder {

  private static final String TAG = SessionBuilder.class.getSimpleName();

  private final SessionStore     sessionStore;
  private final PreKeyStore      preKeyStore;
  private final IdentityKeyStore identityKeyStore;
  private final long             recipientId;
  private final int              deviceId;

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

  public void process(PreKeyWhisperMessage message)
      throws InvalidKeyIdException, InvalidKeyException
  {
    int         preKeyId          = message.getPreKeyId();
    ECPublicKey theirBaseKey      = message.getBaseKey();
    ECPublicKey theirEphemeralKey = message.getWhisperMessage().getSenderEphemeral();
    IdentityKey theirIdentityKey  = message.getIdentityKey();

    Log.w(TAG, "Received pre-key with local key ID: " + preKeyId);

    if (!preKeyStore.contains(preKeyId) &&
        sessionStore.contains(recipientId, deviceId))
    {
      Log.w(TAG, "We've already processed the prekey part, letting bundled message fall through...");
      return;
    }

    if (!preKeyStore.contains(preKeyId))
      throw new InvalidKeyIdException("No such prekey: " + preKeyId);

    SessionRecord   sessionRecord        = sessionStore.get(recipientId, deviceId);
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

    sessionStore.put(recipientId, deviceId, sessionRecord);

    if (preKeyId != Medium.MAX_VALUE) {
      preKeyStore.remove(preKeyId);
    }

    identityKeyStore.saveIdentity(recipientId, theirIdentityKey);
  }

  public void process(PreKey preKey) throws InvalidKeyException {
    SessionRecord   sessionRecord     = sessionStore.get(recipientId, deviceId);
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

    sessionStore.put(recipientId, deviceId, sessionRecord);

    identityKeyStore.saveIdentity(recipientId, preKey.getIdentityKey());
  }

  public KeyExchangeMessage process(KeyExchangeMessage message) throws InvalidKeyException {
    KeyExchangeMessage responseMessage = null;
    SessionRecord      sessionRecord   = sessionStore.get(recipientId, deviceId);

    Log.w(TAG, "Received key exchange with sequence: " + message.getSequence());

    if (message.isInitiate()) {
      ECKeyPair       ourBaseKey, ourEphemeralKey;
      IdentityKeyPair ourIdentityKey;

      int flags = KeyExchangeMessage.RESPONSE_FLAG;

      Log.w(TAG, "KeyExchange is an initiate.");

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

      responseMessage = new KeyExchangeMessage(message.getSequence(),
                                               flags, ourBaseKey.getPublicKey(),
                                               ourEphemeralKey.getPublicKey(),
                                               ourIdentityKey.getPublicKey());
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
    sessionStore.put(recipientId, deviceId, sessionRecord);

    identityKeyStore.saveIdentity(recipientId, message.getIdentityKey());

    return responseMessage;
  }


}

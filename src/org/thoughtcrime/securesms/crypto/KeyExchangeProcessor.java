package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.protocol.KeyExchangeMessage;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.PreKeyService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingKeyExchangeMessage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECKeyPair;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.ratchet.RatchetingSession;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.PreKeyEntity;
import org.whispersystems.textsecure.storage.InvalidKeyIdException;
import org.whispersystems.textsecure.storage.PreKeyRecord;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.storage.TextSecureSessionStore;
import org.whispersystems.textsecure.util.Medium;

/**
 * This class processes key exchange interactions.
 *
 * @author Moxie Marlinspike
 */

public class KeyExchangeProcessor {

  public static final String SECURITY_UPDATE_EVENT = "org.thoughtcrime.securesms.KEY_EXCHANGE_UPDATE";

  private Context         context;
  private RecipientDevice recipientDevice;
  private MasterSecret    masterSecret;
  private SessionStore    sessionStore;

  public KeyExchangeProcessor(Context context, MasterSecret masterSecret, RecipientDevice recipientDevice)
  {
    this.context         = context;
    this.recipientDevice = recipientDevice;
    this.masterSecret    = masterSecret;
    this.sessionStore    = new TextSecureSessionStore(context, masterSecret);
  }

  public boolean isTrusted(PreKeyWhisperMessage message) {
    return isTrusted(message.getIdentityKey());
  }

  public boolean isTrusted(PreKeyEntity entity) {
    return isTrusted(entity.getIdentityKey());
  }

  public boolean isTrusted(KeyExchangeMessage message) {
    return message.hasIdentityKey() && isTrusted(message.getIdentityKey());
  }

  public boolean isTrusted(IdentityKey identityKey) {
    return DatabaseFactory.getIdentityDatabase(context).isValidIdentity(masterSecret,
                                                                        recipientDevice.getRecipientId(),
                                                                        identityKey);
  }

  public boolean isStale(KeyExchangeMessage message) {
    SessionRecord sessionRecord = sessionStore.get(recipientDevice.getRecipientId(),
                                                   recipientDevice.getDeviceId());

    return
        message.isResponse() &&
            (!sessionRecord.getSessionState().hasPendingKeyExchange() ||
              sessionRecord.getSessionState().getPendingKeyExchangeSequence() != message.getSequence()) &&
        !message.isResponseForSimultaneousInitiate();
  }

  public void processKeyExchangeMessage(PreKeyWhisperMessage message)
      throws InvalidKeyIdException, InvalidKeyException
  {
    int         preKeyId          = message.getPreKeyId();
    ECPublicKey theirBaseKey      = message.getBaseKey();
    ECPublicKey theirEphemeralKey = message.getWhisperMessage().getSenderEphemeral();
    IdentityKey theirIdentityKey  = message.getIdentityKey();

    Log.w("KeyExchangeProcessor", "Received pre-key with local key ID: " + preKeyId);

    if (!PreKeyRecord.hasRecord(context, preKeyId) &&
        sessionStore.contains(recipientDevice.getRecipientId(), recipientDevice.getDeviceId()))
    {
      Log.w("KeyExchangeProcessor", "We've already processed the prekey part, letting bundled message fall through...");
      return;
    }

    if (!PreKeyRecord.hasRecord(context, preKeyId))
      throw new InvalidKeyIdException("No such prekey: " + preKeyId);

    SessionRecord   sessionRecord        = sessionStore.get(recipientDevice.getRecipientId(),
                                                            recipientDevice.getDeviceId());
    PreKeyRecord    preKeyRecord         = new PreKeyRecord(context, masterSecret, preKeyId);
    ECKeyPair       ourBaseKey           = preKeyRecord.getKeyPair();
    ECKeyPair       ourEphemeralKey      = ourBaseKey;
    IdentityKeyPair ourIdentityKey       = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    boolean         simultaneousInitiate = sessionRecord.getSessionState().hasPendingPreKey();

    if (!simultaneousInitiate) sessionRecord.reset();
    else                       sessionRecord.archiveCurrentState();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        ourBaseKey, theirBaseKey,
                                        ourEphemeralKey, theirEphemeralKey,
                                        ourIdentityKey, theirIdentityKey);

    sessionRecord.getSessionState().setLocalRegistrationId(TextSecurePreferences.getLocalRegistrationId(context));
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());

    if (simultaneousInitiate) sessionRecord.getSessionState().setNeedsRefresh(true);

    sessionStore.put(recipientDevice.getRecipientId(), recipientDevice.getDeviceId(), sessionRecord);

    if (preKeyId != Medium.MAX_VALUE) {
      PreKeyRecord.delete(context, preKeyId);
    }

    PreKeyService.initiateRefresh(context, masterSecret);

    DatabaseFactory.getIdentityDatabase(context)
                   .saveIdentity(masterSecret, recipientDevice.getRecipientId(), theirIdentityKey);
  }

  public void processKeyExchangeMessage(PreKeyEntity message, long threadId)
      throws InvalidKeyException
  {
    SessionRecord   sessionRecord     = sessionStore.get(recipientDevice.getRecipientId(),
                                                         recipientDevice.getDeviceId());
    ECKeyPair       ourBaseKey        = Curve.generateKeyPair(true);
    ECKeyPair       ourEphemeralKey   = Curve.generateKeyPair(true);
    ECPublicKey     theirBaseKey      = message.getPublicKey();
    ECPublicKey     theirEphemeralKey = theirBaseKey;
    IdentityKey     theirIdentityKey  = message.getIdentityKey();
    IdentityKeyPair ourIdentityKey    = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);

    if (sessionRecord.getSessionState().getNeedsRefresh()) sessionRecord.archiveCurrentState();
    else                                                   sessionRecord.reset();

    RatchetingSession.initializeSession(sessionRecord.getSessionState(),
                                        ourBaseKey, theirBaseKey, ourEphemeralKey,
                                        theirEphemeralKey, ourIdentityKey, theirIdentityKey);

    sessionRecord.getSessionState().setPendingPreKey(message.getKeyId(), ourBaseKey.getPublicKey());
    sessionRecord.getSessionState().setLocalRegistrationId(TextSecurePreferences.getLocalRegistrationId(context));
    sessionRecord.getSessionState().setRemoteRegistrationId(message.getRegistrationId());

    sessionStore.put(recipientDevice.getRecipientId(), recipientDevice.getDeviceId(), sessionRecord);

    DatabaseFactory.getIdentityDatabase(context)
                   .saveIdentity(masterSecret, recipientDevice.getRecipientId(), message.getIdentityKey());

    if (threadId != -1) {
      broadcastSecurityUpdateEvent(context, threadId);
    }
  }

  public void processKeyExchangeMessage(KeyExchangeMessage message, long threadId)
      throws InvalidMessageException
  {
    try {
      SessionRecord        sessionRecord = sessionStore.get(recipientDevice.getRecipientId(),
                                                            recipientDevice.getDeviceId());
      Recipient            recipient     = RecipientFactory.getRecipientsForIds(context,
                                                                                String.valueOf(recipientDevice.getRecipientId()),
                                                                                false)
                                                           .getPrimaryRecipient();

      Log.w("KeyExchangeProcessor", "Received key exchange with sequence: " + message.getSequence());

      if (message.isInitiate()) {
        ECKeyPair       ourBaseKey, ourEphemeralKey;
        IdentityKeyPair ourIdentityKey;

        int flags = KeyExchangeMessage.RESPONSE_FLAG;

        Log.w("KeyExchangeProcessor", "KeyExchange is an initiate.");

        if (!sessionRecord.getSessionState().hasPendingKeyExchange()) {
          Log.w("KeyExchangeProcessor", "We don't have a pending initiate...");
          ourBaseKey      = Curve.generateKeyPair(true);
          ourEphemeralKey = Curve.generateKeyPair(true);
          ourIdentityKey  = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);

          sessionRecord.getSessionState().setPendingKeyExchange(message.getSequence(), ourBaseKey,
                                                                ourEphemeralKey, ourIdentityKey);
        } else {
          Log.w("KeyExchangeProcessor", "We alredy have a pending initiate, responding as simultaneous initiate...");
          ourBaseKey      = sessionRecord.getSessionState().getPendingKeyExchangeBaseKey();
          ourEphemeralKey = sessionRecord.getSessionState().getPendingKeyExchangeEphemeralKey();
          ourIdentityKey  = sessionRecord.getSessionState().getPendingKeyExchangeIdentityKey();
          flags          |= KeyExchangeMessage.SIMULTAENOUS_INITIATE_FLAG;

          sessionRecord.getSessionState().setPendingKeyExchange(message.getSequence(), ourBaseKey,
                                                                ourEphemeralKey, ourIdentityKey);
        }

        KeyExchangeMessage ourMessage = new KeyExchangeMessage(message.getSequence(),
                                                                   flags, ourBaseKey.getPublicKey(),
                                                                   ourEphemeralKey.getPublicKey(),
                                                                   ourIdentityKey.getPublicKey());

        OutgoingKeyExchangeMessage textMessage = new OutgoingKeyExchangeMessage(recipient,
                                                                                ourMessage.serialize());
        MessageSender.send(context, masterSecret, textMessage, threadId, true);
      }

      if (message.getSequence() != sessionRecord.getSessionState().getPendingKeyExchangeSequence()) {
        Log.w("KeyExchangeProcessor", "No matching sequence for response. " +
            "Is simultaneous initiate response: " + message.isResponseForSimultaneousInitiate());
        return;
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
      sessionStore.put(recipientDevice.getRecipientId(), recipientDevice.getDeviceId(), sessionRecord);

      DatabaseFactory.getIdentityDatabase(context)
                     .saveIdentity(masterSecret, recipientDevice.getRecipientId(), message.getIdentityKey());

      DecryptingQueue.scheduleRogueMessages(context, masterSecret, recipient);

      broadcastSecurityUpdateEvent(context, threadId);
    } catch (InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

  public static void broadcastSecurityUpdateEvent(Context context, long threadId) {
    Intent intent = new Intent(SECURITY_UPDATE_EVENT);
    intent.putExtra("thread_id", threadId);
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent, KeyCachingService.KEY_PERMISSION);
  }

}

package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.PreKeyService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingKeyExchangeMessage;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.SessionBuilder;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyStore;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.push.PreKeyEntity;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.storage.TextSecurePreKeyStore;
import org.whispersystems.textsecure.storage.TextSecureSessionStore;
import org.whispersystems.textsecure.util.Base64;

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
  private SessionBuilder  sessionBuilder;
  private SessionStore    sessionStore;

  public KeyExchangeProcessor(Context context, MasterSecret masterSecret, RecipientDevice recipientDevice)
  {
    this.context         = context;
    this.recipientDevice = recipientDevice;
    this.masterSecret    = masterSecret;

    IdentityKeyStore identityKeyStore = new TextSecureIdentityKeyStore(context, masterSecret);
    PreKeyStore      preKeyStore      = new TextSecurePreKeyStore(context, masterSecret);

    this.sessionStore   = new TextSecureSessionStore(context, masterSecret);
    this.sessionBuilder = new SessionBuilder(sessionStore, preKeyStore, identityKeyStore,
                                             recipientDevice.getRecipientId(),
                                             recipientDevice.getDeviceId());
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
    SessionRecord sessionRecord = sessionStore.load(recipientDevice.getRecipientId(),
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
    sessionBuilder.process(message);
    PreKeyService.initiateRefresh(context, masterSecret);
  }

  public void processKeyExchangeMessage(PreKeyEntity message, long threadId)
      throws InvalidKeyException
  {
    sessionBuilder.process(message);

    if (threadId != -1) {
      broadcastSecurityUpdateEvent(context, threadId);
    }
  }

  public void processKeyExchangeMessage(KeyExchangeMessage message, long threadId)
      throws InvalidKeyException
  {
    KeyExchangeMessage responseMessage = sessionBuilder.process(message);
    Recipient            recipient     = RecipientFactory.getRecipientsForIds(context,
                                                                              String.valueOf(recipientDevice.getRecipientId()),
                                                                              false)
                                                         .getPrimaryRecipient();

    if (responseMessage != null) {
      String                     serializedResponse = Base64.encodeBytesWithoutPadding(responseMessage.serialize());
      OutgoingKeyExchangeMessage textMessage        = new OutgoingKeyExchangeMessage(recipient, serializedResponse);
      MessageSender.send(context, masterSecret, textMessage, threadId, true);
    }

    DecryptingQueue.scheduleRogueMessages(context, masterSecret, recipient);

    broadcastSecurityUpdateEvent(context, threadId);
  }

  public static void broadcastSecurityUpdateEvent(Context context, long threadId) {
    Intent intent = new Intent(SECURITY_UPDATE_EVENT);
    intent.putExtra("thread_id", threadId);
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent, KeyCachingService.KEY_PERMISSION);
  }

}

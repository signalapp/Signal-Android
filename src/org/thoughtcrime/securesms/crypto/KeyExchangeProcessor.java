package org.thoughtcrime.securesms.crypto;

import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.PreKeyService;
import org.thoughtcrime.securesms.sms.OutgoingKeyExchangeMessage;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.SessionBuilder;
import org.whispersystems.libaxolotl.StaleKeyExchangeException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.protocol.KeyExchangeMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyStore;
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

  public KeyExchangeProcessor(Context context, MasterSecret masterSecret, RecipientDevice recipientDevice)
  {
    this.context         = context;
    this.recipientDevice = recipientDevice;
    this.masterSecret    = masterSecret;

    IdentityKeyStore identityKeyStore = new TextSecureIdentityKeyStore(context, masterSecret);
    PreKeyStore      preKeyStore      = new TextSecurePreKeyStore(context, masterSecret);
    SessionStore     sessionStore     = new TextSecureSessionStore(context, masterSecret);

    this.sessionBuilder = new SessionBuilder(sessionStore, preKeyStore, identityKeyStore,
                                             recipientDevice.getRecipientId(),
                                             recipientDevice.getDeviceId());
  }

  public void processKeyExchangeMessage(PreKeyWhisperMessage message)
      throws InvalidKeyIdException, InvalidKeyException, UntrustedIdentityException
  {
    sessionBuilder.process(message);
    PreKeyService.initiateRefresh(context, masterSecret);
  }

  public void processKeyExchangeMessage(PreKeyEntity message, long threadId)
      throws InvalidKeyException, UntrustedIdentityException
  {
    sessionBuilder.process(message);

    if (threadId != -1) {
      broadcastSecurityUpdateEvent(context, threadId);
    }
  }

  public OutgoingKeyExchangeMessage processKeyExchangeMessage(KeyExchangeMessage message, long threadId)
      throws InvalidKeyException, UntrustedIdentityException, StaleKeyExchangeException
  {
    KeyExchangeMessage responseMessage = sessionBuilder.process(message);
    Recipient          recipient       = RecipientFactory.getRecipientsForIds(context,
                                                                              String.valueOf(recipientDevice.getRecipientId()),
                                                                              false)
                                                         .getPrimaryRecipient();

    DecryptingQueue.scheduleRogueMessages(context, masterSecret, recipient);

    broadcastSecurityUpdateEvent(context, threadId);

    if (responseMessage != null) {
      String serializedResponse = Base64.encodeBytesWithoutPadding(responseMessage.serialize());
      return new OutgoingKeyExchangeMessage(recipient, serializedResponse);
    } else {
      return null;
    }
  }

  public static void broadcastSecurityUpdateEvent(Context context, long threadId) {
    Intent intent = new Intent(SECURITY_UPDATE_EVENT);
    intent.putExtra("thread_id", threadId);
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent, KeyCachingService.KEY_PERMISSION);
  }

}

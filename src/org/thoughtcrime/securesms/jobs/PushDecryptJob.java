package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.groups.GroupMessageProcessor;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.push.TextSecureMessageReceiverFactory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingEndSessionMessage;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.push.IncomingPushMessage;
import org.whispersystems.textsecure.util.Base64;

import ws.com.google.android.mms.MmsException;

public class PushDecryptJob extends MasterSecretJob {

  public static final String TAG = PushDecryptJob.class.getSimpleName();

  private final long messageId;

  public PushDecryptJob(Context context, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new MasterSecretRequirement(context))
                                .create());
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    if (KeyCachingService.getMasterSecret(context) == null) {
      MessageNotifier.updateNotification(context, null);
    }
  }

  @Override
  public void onRun() throws RequirementNotMetException {
    try {
      MasterSecret        masterSecret = getMasterSecret();
      PushDatabase        database     = DatabaseFactory.getPushDatabase(context);
      IncomingPushMessage push         = database.get(messageId);

      handleMessage(masterSecret, push);
      database.delete(messageId);

    } catch (PushDatabase.NoSuchMessageException e) {
      Log.w(TAG, e);
    }
  }

  @Override
  public void onCanceled() {

  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    if (throwable instanceof RequirementNotMetException) return true;
    return false;
  }

  private void handleMessage(MasterSecret masterSecret, IncomingPushMessage push) {
    try {
      Recipients                recipients      = RecipientFactory.getRecipientsFromMessage(context, push, false);
      long                      recipientId     = recipients.getPrimaryRecipient().getRecipientId();
      TextSecureMessageReceiver messageReceiver = TextSecureMessageReceiverFactory.create(context, masterSecret);

      TextSecureMessage message = messageReceiver.receiveMessage(recipientId, push);

      if      (message.isEndSession())               handleEndSessionMessage(masterSecret, recipientId, push, message);
      else if (message.isGroupUpdate())              handleGroupMessage(masterSecret, push, message);
      else if (message.getAttachments().isPresent()) handleMediaMessage(masterSecret, push, message);
      else                                           handleTextMessage(masterSecret, push, message);
    } catch (InvalidVersionException e) {
      Log.w(TAG, e);
      handleInvalidVersionMessage(masterSecret, push);
    } catch (InvalidMessageException | InvalidKeyIdException | InvalidKeyException | MmsException e) {
      Log.w(TAG, e);
      handleCorruptMessage(masterSecret, push);
    } catch (NoSessionException e) {
      Log.w(TAG, e);
      handleNoSessionMessage(masterSecret, push);
    } catch (LegacyMessageException e) {
      Log.w(TAG, e);
      handleLegacyMessage(masterSecret, push);
    } catch (DuplicateMessageException e) {
      Log.w(TAG, e);
      handleDuplicateMessage(masterSecret, push);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
      handleUntrustedIdentityMessage(masterSecret, push);
    }
  }

  private void handleEndSessionMessage(MasterSecret masterSecret, long recipientId, IncomingPushMessage push, TextSecureMessage message) {
    IncomingTextMessage incomingTextMessage = new IncomingTextMessage(push.getSource(),
                                                                      push.getSourceDevice(),
                                                                      message.getTimestamp(),
                                                                      "", Optional.<TextSecureGroup>absent());

    IncomingEndSessionMessage incomingEndSessionMessage = new IncomingEndSessionMessage(incomingTextMessage);
    EncryptingSmsDatabase     database                  = DatabaseFactory.getEncryptingSmsDatabase(context);
    Pair<Long, Long>          messageAndThreadId        = database.insertMessageInbox(masterSecret, incomingEndSessionMessage);

    SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);
    sessionStore.deleteAllSessions(recipientId);

    SecurityEvent.broadcastSecurityUpdateEvent(context, messageAndThreadId.second);
    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleGroupMessage(MasterSecret masterSecret, IncomingPushMessage push, TextSecureMessage message) {
    GroupMessageProcessor.process(context, masterSecret, push, message);
  }

  private void handleMediaMessage(MasterSecret masterSecret, IncomingPushMessage signal, TextSecureMessage message)
      throws MmsException
  {
    String               localNumber  = TextSecurePreferences.getLocalNumber(context);
    MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret, signal.getSource(),
                                                                 localNumber, message.getTimestamp(),
                                                                 Optional.fromNullable(signal.getRelay()),
                                                                 message.getBody(),
                                                                 message.getGroupInfo(),
                                                                 message.getAttachments());

    Pair<Long, Long> messageAndThreadId;

    if (message.isSecure()) {
      messageAndThreadId = database.insertSecureDecryptedMessageInbox(masterSecret, mediaMessage, -1);
    } else {
      messageAndThreadId = database.insertMessageInbox(masterSecret, mediaMessage, null, -1);
    }

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new AttachmentDownloadJob(context, messageAndThreadId.first));

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleTextMessage(MasterSecret masterSecret, IncomingPushMessage signal, TextSecureMessage message) {
    EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
    String                body        = message.getBody().isPresent() ? message.getBody().get() : "";
    IncomingTextMessage   textMessage = new IncomingTextMessage(signal.getSource(),
                                                                signal.getSourceDevice(),
                                                                message.getTimestamp(), body,
                                                                message.getGroupInfo());

    if (message.isSecure()) {
      textMessage = new IncomingEncryptedMessage(textMessage, body);
    }

    Pair<Long, Long> messageAndThreadId = database.insertMessageInbox(masterSecret, textMessage);
//    database.updateMessageBody(masterSecret, messageAndThreadId.first, body);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleInvalidVersionMessage(MasterSecret masterSecret, IncomingPushMessage push) {
    Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, push);
    DatabaseFactory.getEncryptingSmsDatabase(context).markAsInvalidVersionKeyExchange(messageAndThreadId.first);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleCorruptMessage(MasterSecret masterSecret, IncomingPushMessage push) {
    Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, push);
    DatabaseFactory.getEncryptingSmsDatabase(context).markAsDecryptFailed(messageAndThreadId.first);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleNoSessionMessage(MasterSecret masterSecret, IncomingPushMessage push) {
    Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, push);
    DatabaseFactory.getEncryptingSmsDatabase(context).markAsNoSession(messageAndThreadId.first);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleLegacyMessage(MasterSecret masterSecret, IncomingPushMessage push) {
    Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, push);
    DatabaseFactory.getEncryptingSmsDatabase(context).markAsLegacyVersion(messageAndThreadId.first);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleDuplicateMessage(MasterSecret masterSecret, IncomingPushMessage push) {
    Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, push);
    DatabaseFactory.getEncryptingSmsDatabase(context).markAsDecryptDuplicate(messageAndThreadId.first);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleUntrustedIdentityMessage(MasterSecret masterSecret, IncomingPushMessage push) {
    String              encoded     = Base64.encodeBytes(push.getBody());
    IncomingTextMessage textMessage = new IncomingTextMessage(push.getSource(), push.getSourceDevice(),
                                                              push.getTimestampMillis(), encoded,
                                                              Optional.<TextSecureGroup>absent());

    IncomingPreKeyBundleMessage bundleMessage      = new IncomingPreKeyBundleMessage(textMessage, encoded);
    Pair<Long, Long>            messageAndThreadId = DatabaseFactory.getEncryptingSmsDatabase(context)
                                                                    .insertMessageInbox(masterSecret, bundleMessage);

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private Pair<Long, Long> insertPlaceholder(MasterSecret masterSecret, IncomingPushMessage push) {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    IncomingTextMessage textMessage = new IncomingTextMessage(push.getSource(), push.getSourceDevice(),
                                                              push.getTimestampMillis(), "",
                                                              Optional.<TextSecureGroup>absent());

    textMessage = new IncomingEncryptedMessage(textMessage, "");

    return database.insertMessageInbox(masterSecret, textMessage);
  }
}

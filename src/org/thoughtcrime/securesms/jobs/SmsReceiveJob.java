package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.telephony.SmsMessage;
import android.util.Pair;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.MultipartSmsMessageHandler;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GService;
import de.gdata.messaging.util.GUtil;

public class SmsReceiveJob extends ContextJob {

  private static final String TAG = SmsReceiveJob.class.getSimpleName();

  private static MultipartSmsMessageHandler multipartMessageHandler = new MultipartSmsMessageHandler();

  private final Object[] pdus;

  public SmsReceiveJob(Context context, Object[] pdus) {
    super(context, JobParameters.newBuilder()
        .withPersistence()
        .create());
    this.pdus = pdus;
  }

  @Override
  public void onAdded() {
  }

  @Override
  public void onRun() {
    Optional<IncomingTextMessage> message = assembleMessageFragments(pdus);
    if (message.isPresent()) {
      if(!GUtil.isSMSCommand(message.get().getMessageBody())) {
        if (!GService.shallBeBlockedByFilter(message.get().getSender(), GService.TYPE_SMS, GService.INCOMING) && (!GService.shallBeBlockedByPrivacy(message.get().getSender()) || !new GDataPreferences(getContext()).isPrivacyActivated())) {
          Pair<Long, Long> messageAndThreadId = storeMessage(message.get(), false);
          MessageNotifier.updateNotification(context, KeyCachingService.getMasterSecret(context), messageAndThreadId.second);
        }
        if (GService.shallBeBlockedByPrivacy(message.get().getSender()) && !GService.shallBeBlockedByFilter(message.get().getSender(), GService.TYPE_SMS, GService.INCOMING)) {
          storeMessage(message.get(), true);
        }
      }
    }
  }

  @Override
  public void onCanceled() {

  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  private Pair<Long, Long> storeMessage(IncomingTextMessage message, boolean hidden) {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
    MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);

    Pair<Long, Long> messageAndThreadId;

    if (message.isSecureMessage()) {
      messageAndThreadId = database.insertMessageInbox((MasterSecret) null, message);
    } else if (masterSecret == null) {
      messageAndThreadId = database.insertMessageInbox(MasterSecretUtil.getAsymmetricMasterSecret(context, null), message);
    } else {
      messageAndThreadId = database.insertMessageInbox(masterSecret, message);
    }

    if (masterSecret == null || message.isSecureMessage() || message.isKeyExchange() || message.isEndSession()) {
      ApplicationContext.getInstance(context)
          .getJobManager()
          .add(new SmsDecryptJob(context, messageAndThreadId.first));
    } else {
      if(!hidden) {
        MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
      }
    }

    return messageAndThreadId;
  }

  private Optional<IncomingTextMessage> assembleMessageFragments(Object[] pdus) {
    List<IncomingTextMessage> messages = new LinkedList<>();

    for (Object pdu : pdus) {
      messages.add(new IncomingTextMessage(SmsMessage.createFromPdu((byte[]) pdu)));
    }

    if (messages.isEmpty()) {
      return Optional.absent();
    }

    IncomingTextMessage message = new IncomingTextMessage(messages);

    if (WirePrefix.isEncryptedMessage(message.getMessageBody()) ||
        WirePrefix.isKeyExchange(message.getMessageBody()) ||
        WirePrefix.isPreKeyBundle(message.getMessageBody()) ||
        WirePrefix.isEndSession(message.getMessageBody())) {
      return Optional.fromNullable(multipartMessageHandler.processPotentialMultipartMessage(message));
    } else {
      return Optional.of(message);
    }
  }
}

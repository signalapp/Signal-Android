package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MessageDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.RecipientDatabase.UnidentifiedAccessMode;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.transport.InsecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;

public class PushTextSendJob extends PushSendJob {

  public static final String KEY = "PushTextSendJob";

  private static final String TAG = PushTextSendJob.class.getSimpleName();

  private static final String KEY_MESSAGE_ID = "message_id";

  private long messageId;

  public PushTextSendJob(long messageId, @NonNull Recipient recipient) {
    this(constructParameters(recipient, false), messageId);
  }

  private PushTextSendJob(@NonNull Job.Parameters parameters, long messageId) {
    super(parameters);
    this.messageId = messageId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    DatabaseFactory.getSmsDatabase(context).markAsSending(messageId);
  }

  @Override
  public void onPushSend() throws NoSuchMessageException, RetryLaterException, UndeliverableMessageException {
    ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();
    MessageDatabase        database          = DatabaseFactory.getSmsDatabase(context);
    SmsMessageRecord       record            = database.getSmsMessage(messageId);

    if (!record.isPending() && !record.isFailed()) {
      warn(TAG, String.valueOf(record.getDateSent()), "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    try {
      log(TAG, String.valueOf(record.getDateSent()), "Sending message: " + messageId);

      RecipientUtil.shareProfileIfFirstSecureMessage(context, record.getRecipient());

      Recipient              recipient  = record.getRecipient().fresh();
      byte[]                 profileKey = recipient.getProfileKey();
      UnidentifiedAccessMode accessMode = recipient.getUnidentifiedAccessMode();

      boolean unidentified = deliver(record);

      database.markAsSent(messageId, true);
      database.markUnidentified(messageId, unidentified);

      if (recipient.isSelf()) {
        SyncMessageId id = new SyncMessageId(recipient.getId(), record.getDateSent());
        DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(id, System.currentTimeMillis());
        DatabaseFactory.getMmsSmsDatabase(context).incrementReadReceiptCount(id, System.currentTimeMillis());
      }

      if (unidentified && accessMode == UnidentifiedAccessMode.UNKNOWN && profileKey == null) {
        log(TAG, String.valueOf(record.getDateSent()), "Marking recipient as UD-unrestricted following a UD send.");
        DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.UNRESTRICTED);
      } else if (unidentified && accessMode == UnidentifiedAccessMode.UNKNOWN) {
        log(TAG, String.valueOf(record.getDateSent()), "Marking recipient as UD-enabled following a UD send.");
        DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.ENABLED);
      } else if (!unidentified && accessMode != UnidentifiedAccessMode.DISABLED) {
        log(TAG, String.valueOf(record.getDateSent()), "Marking recipient as UD-disabled following a non-UD send.");
        DatabaseFactory.getRecipientDatabase(context).setUnidentifiedAccessMode(recipient.getId(), UnidentifiedAccessMode.DISABLED);
      }

      if (record.getExpiresIn() > 0) {
        database.markExpireStarted(messageId);
        expirationManager.scheduleDeletion(record.getId(), record.isMms(), record.getExpiresIn());
      }

      log(TAG, String.valueOf(record.getDateSent()), "Sent message: " + messageId);

    } catch (InsecureFallbackApprovalException e) {
      warn(TAG, String.valueOf(record.getDateSent()), "Failure", e);
      database.markAsPendingInsecureSmsFallback(record.getId());
      ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, record.getRecipient(), record.getThreadId());
      ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
    } catch (UntrustedIdentityException e) {
      warn(TAG, String.valueOf(record.getDateSent()), "Failure", e);
      RecipientId recipientId = Recipient.external(context, e.getIdentifier()).getId();
      database.addMismatchedIdentity(record.getId(), recipientId, e.getIdentityKey());
      database.markAsSentFailed(record.getId());
      database.markAsPush(record.getId());
      RetrieveProfileJob.enqueue(recipientId);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof RetryLaterException) return true;

    return false;
  }

  @Override
  public void onFailure() {
    DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);

    long      threadId  = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageId);
    Recipient recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    if (threadId != -1 && recipient != null) {
      ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, recipient, threadId);
    }
  }

  private boolean deliver(SmsMessageRecord message)
      throws UntrustedIdentityException, InsecureFallbackApprovalException, RetryLaterException, UndeliverableMessageException
  {
    try {
      rotateSenderCertificateIfNecessary();

      Recipient                        messageRecipient   = message.getIndividualRecipient().fresh();
      SignalServiceMessageSender       messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
      SignalServiceAddress             address            = RecipientUtil.toSignalServiceAddress(context, messageRecipient);
      Optional<byte[]>                 profileKey         = getProfileKey(messageRecipient);
      Optional<UnidentifiedAccessPair> unidentifiedAccess = UnidentifiedAccessUtil.getAccessFor(context, messageRecipient);

      log(TAG, String.valueOf(message.getDateSent()), "Have access key to use: " + unidentifiedAccess.isPresent());

      SignalServiceDataMessage textSecureMessage = SignalServiceDataMessage.newBuilder()
                                                                           .withTimestamp(message.getDateSent())
                                                                           .withBody(message.getBody())
                                                                           .withExpiration((int)(message.getExpiresIn() / 1000))
                                                                           .withProfileKey(profileKey.orNull())
                                                                           .asEndSessionMessage(message.isEndSession())
                                                                           .build();

      if (Util.equals(TextSecurePreferences.getLocalUuid(context), address.getUuid().orNull())) {
        Optional<UnidentifiedAccessPair> syncAccess  = UnidentifiedAccessUtil.getAccessForSync(context);
        SignalServiceSyncMessage         syncMessage = buildSelfSendSyncMessage(context, textSecureMessage, syncAccess);

        messageSender.sendMessage(syncMessage, syncAccess);
        return syncAccess.isPresent();
      } else {
        return messageSender.sendMessage(address, unidentifiedAccess, textSecureMessage).getSuccess().isUnidentified();
      }
    } catch (UnregisteredUserException e) {
      warn(TAG, "Failure", e);
      throw new InsecureFallbackApprovalException(e);
    } catch (ServerRejectedException e) {
      throw new UndeliverableMessageException(e);
    } catch (IOException e) {
      warn(TAG, "Failure", e);
      throw new RetryLaterException(e);
    }
  }

  public static class Factory implements Job.Factory<PushTextSendJob> {
    @Override
    public @NonNull PushTextSendJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushTextSendJob(parameters, data.getLong(KEY_MESSAGE_ID));
    }
  }
}

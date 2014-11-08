package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.storage.TextSecureAxolotlStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.push.TextSecureMessageSenderFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.IncomingIdentityUpdateMessage;
import org.thoughtcrime.securesms.transport.InsecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.transport.SecureFallbackApprovalException;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.push.PushAddress;
import org.whispersystems.textsecure.push.UnregisteredUserException;
import org.whispersystems.textsecure.storage.RecipientDevice;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.io.IOException;

public class PushTextSendJob extends PushSendJob {

  private static final String TAG = PushTextSendJob.class.getSimpleName();

  private final long messageId;

  public PushTextSendJob(Context context, long messageId, String destination) {
    super(context, constructParameters(context, destination));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onRun() throws RequirementNotMetException, NoSuchMessageException, RetryLaterException
  {
    MasterSecret          masterSecret = getMasterSecret();
    EncryptingSmsDatabase database     = DatabaseFactory.getEncryptingSmsDatabase(context);
    SmsMessageRecord      record       = database.getMessage(masterSecret, messageId);
    String                destination  = record.getIndividualRecipient().getNumber();

    try {
      Log.w(TAG, "Sending message: " + messageId);

      deliver(masterSecret, record, destination);

      database.markAsPush(messageId);
      database.markAsSecure(messageId);
      database.markAsSent(messageId);
    } catch (InsecureFallbackApprovalException e) {
      Log.w(TAG, e);
      database.markAsPendingInsecureSmsFallback(record.getId());
      MessageNotifier.notifyMessageDeliveryFailed(context, record.getRecipients(), record.getThreadId());
    } catch (SecureFallbackApprovalException e) {
      Log.w(TAG, e);
      database.markAsPendingSecureSmsFallback(record.getId());
      MessageNotifier.notifyMessageDeliveryFailed(context, record.getRecipients(), record.getThreadId());
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
      IncomingIdentityUpdateMessage identityUpdateMessage = IncomingIdentityUpdateMessage.createFor(e.getE164Number(), e.getIdentityKey());
      database.insertMessageInbox(masterSecret, identityUpdateMessage);
      database.markAsSentFailed(record.getId());
    }
  }

  public void deliver(MasterSecret masterSecret, SmsMessageRecord message, String destination)
      throws UntrustedIdentityException, SecureFallbackApprovalException,
             InsecureFallbackApprovalException, RetryLaterException
  {
    boolean isSmsFallbackSupported = isSmsFallbackSupported(context, destination);

    try {
      PushAddress             address       = getPushAddress(message.getIndividualRecipient());
      TextSecureMessageSender messageSender = TextSecureMessageSenderFactory.create(context, masterSecret);

      if (message.isEndSession()) {
        messageSender.sendMessage(address, new TextSecureMessage(message.getDateSent(), null,
                                                                 null, null, true, true));
      } else {
        messageSender.sendMessage(address, new TextSecureMessage(message.getDateSent(), null,
                                                                 message.getBody().getBody()));
      }
    } catch (InvalidNumberException | UnregisteredUserException e) {
      Log.w(TAG, e);
      if (isSmsFallbackSupported) fallbackOrAskApproval(masterSecret, message, destination);
      else                        DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
    } catch (IOException e) {
      Log.w(TAG, e);
      if (isSmsFallbackSupported) fallbackOrAskApproval(masterSecret, message, destination);
      else                        throw new RetryLaterException(e);
    }
  }

  @Override
  public boolean onShouldRetry(Throwable throwable) {
    if (throwable instanceof RequirementNotMetException) return true;
    if (throwable instanceof RetryLaterException)        return true;

    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);

    long       threadId   = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageId);
    Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

    MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
  }

  private void fallbackOrAskApproval(MasterSecret masterSecret, SmsMessageRecord smsMessage, String destination)
      throws SecureFallbackApprovalException, InsecureFallbackApprovalException
  {
    Recipient    recipient                     = smsMessage.getIndividualRecipient();
    boolean      isSmsFallbackApprovalRequired = isSmsFallbackApprovalRequired(destination);
    AxolotlStore axolotlStore                  = new TextSecureAxolotlStore(context, masterSecret);

    if (!isSmsFallbackApprovalRequired) {
      Log.w(TAG, "Falling back to SMS");
      DatabaseFactory.getSmsDatabase(context).markAsForcedSms(smsMessage.getId());
      ApplicationContext.getInstance(context).getJobManager().add(new SmsSendJob(context, messageId, destination));
    } else if (!axolotlStore.containsSession(recipient.getRecipientId(), RecipientDevice.DEFAULT_DEVICE_ID)) {
      Log.w(TAG, "Marking message as pending insecure fallback.");
      throw new InsecureFallbackApprovalException("Pending user approval for fallback to insecure SMS");
    } else {
      Log.w(TAG, "Marking message as pending secure fallback.");
      throw new SecureFallbackApprovalException("Pending user approval for fallback to secure SMS");
    }
  }


}

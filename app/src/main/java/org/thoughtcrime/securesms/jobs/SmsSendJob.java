package org.thoughtcrime.securesms.jobs;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkOrCellServiceConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.phonenumbers.NumberUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.SmsDeliveryListener;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;

import java.util.ArrayList;

public class SmsSendJob extends SendJob {

  public static final String KEY = "SmsSendJob";

  private static final String TAG              = Log.tag(SmsSendJob.class);
  private static final int    MAX_ATTEMPTS     = 15;
  private static final String KEY_MESSAGE_ID   = "message_id";
  private static final String KEY_RUN_ATTEMPT  = "run_attempt";

  private final long messageId;
  private final int  runAttempt;

  public SmsSendJob(long messageId, @NonNull Recipient destination) {
    this(messageId, destination, 0);
  }

  public SmsSendJob(long messageId, @NonNull Recipient destination, int runAttempt) {
    this(constructParameters(destination), messageId, runAttempt);
  }

  private SmsSendJob(@NonNull Job.Parameters parameters, long messageId, int runAttempt) {
    super(parameters);

    this.messageId  = messageId;
    this.runAttempt = runAttempt;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_MESSAGE_ID, messageId)
                                    .putInt(KEY_RUN_ATTEMPT, runAttempt)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    SignalDatabase.messages().markAsSending(messageId);
  }

  @Override
  public void onSend() throws NoSuchMessageException, TooManyRetriesException, UndeliverableMessageException {
    if (runAttempt >= MAX_ATTEMPTS) {
      warn(TAG, "Hit the retry limit. Failing.");
      throw new TooManyRetriesException();
    }

    MessageTable  database = SignalDatabase.messages();
    MessageRecord record   = database.getMessageRecord(messageId);

    if (!record.isPending() && !record.isFailed()) {
      warn(TAG, "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    if (!record.getRecipient().hasSmsAddress()) {
      throw new UndeliverableMessageException("Recipient didn't have an SMS address! " + record.getRecipient().getId());
    }

    try {
      log(TAG, String.valueOf(record.getDateSent()), "Sending message: " + messageId + " (attempt " + runAttempt + ")");
      deliver(record);
      log(TAG, String.valueOf(record.getDateSent()), "Sent message: " + messageId);
    } catch (UndeliverableMessageException ude) {
      warn(TAG, ude);
      SignalDatabase.messages().markAsSentFailed(record.getId());
      ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, record.getRecipient(), ConversationId.fromMessageRecord(record));
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception throwable) {
    return false;
  }

  @Override
  public void onFailure() {
    warn(TAG, "onFailure() messageId: " + messageId);
    long      threadId  = SignalDatabase.messages().getThreadIdForMessage(messageId);
    Recipient recipient = SignalDatabase.threads().getRecipientForThreadId(threadId);

    SignalDatabase.messages().markAsSentFailed(messageId);

    if (threadId != -1 && recipient != null) {
      ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, recipient, ConversationId.forConversation(threadId));
    } else {
      Log.w(TAG, "Could not find message! threadId: " + threadId + ", recipient: " + (recipient != null ? recipient.getId().toString() : "null"));
    }
  }

  private void deliver(MessageRecord message)
      throws UndeliverableMessageException
  {
    if (message.isSecure() || message.isKeyExchange() || message.isEndSession()) {
      throw new UndeliverableMessageException("Trying to send a secure SMS?");
    }

    String recipient = message.getIndividualRecipient().requireSmsAddress();

    // See issue #1516 for bug report, and discussion on commits related to #4833 for problems
    // related to the original fix to #1516. This still may not be a correct fix if networks allow
    // SMS/MMS sending to alphanumeric recipients other than email addresses, but should also
    // help to fix issue #3099.
    if (!NumberUtil.isValidEmail(recipient)) {
      recipient = PhoneNumberUtils.stripSeparators(PhoneNumberUtils.convertKeypadLettersToDigits(recipient));
    }

    if (!NumberUtil.isValidSmsOrEmail(recipient)) {
      throw new UndeliverableMessageException("Not a valid SMS destination! " + recipient);
    }

    SmsManager               smsManager       = getSmsManagerFor(message.getSubscriptionId());
    ArrayList<String>        messages         = smsManager.divideMessage(message.getBody());
    ArrayList<PendingIntent> sentIntents      = constructSentIntents(message.getId(), message.getType(), messages);
    ArrayList<PendingIntent> deliveredIntents = constructDeliveredIntents(message.getId(), message.getType(), messages);

    // NOTE 11/04/14 -- There's apparently a bug where for some unknown recipients
    // and messages, this will throw an NPE.  We have no idea why, so we're just
    // catching it and marking the message as a failure.  That way at least it doesn't
    // repeatedly crash every time you start the app.
    try {
      smsManager.sendMultipartTextMessage(recipient, null, messages, sentIntents, deliveredIntents);
    } catch (NullPointerException | IllegalArgumentException npe) {
      warn(TAG, npe);
      log(TAG, String.valueOf(message.getDateSent()), "Recipient: " + recipient);
      log(TAG, String.valueOf(message.getDateSent()), "Message Parts: " + messages.size());

      try {
        for (int i=0;i<messages.size();i++) {
          smsManager.sendTextMessage(recipient, null, messages.get(i), sentIntents.get(i),
                                     deliveredIntents == null ? null : deliveredIntents.get(i));
        }
      } catch (NullPointerException | IllegalArgumentException npe2) {
        warn(TAG, npe);
        throw new UndeliverableMessageException(npe2);
      }
    } catch (SecurityException se) {
      warn(TAG, se);
      throw new UndeliverableMessageException(se);
    }
  }

  private ArrayList<PendingIntent> constructSentIntents(long messageId, long type, ArrayList<String> messages)
  {
    ArrayList<PendingIntent> sentIntents = new ArrayList<>(messages.size());
    boolean                  isMultipart = messages.size() > 1;

    for (String ignored : messages) {
      sentIntents.add(PendingIntent.getBroadcast(context, 0,
                                                 constructSentIntent(context, messageId, type, isMultipart),
                                                 PendingIntentFlags.mutable()));
    }

    return sentIntents;
  }

  private ArrayList<PendingIntent> constructDeliveredIntents(long messageId, long type, ArrayList<String> messages) {
    if (!SignalStore.settings().isSmsDeliveryReportsEnabled()) {
      return null;
    }

    ArrayList<PendingIntent> deliveredIntents = new ArrayList<>(messages.size());
    boolean                  isMultipart      = messages.size() > 1;

    for (String ignored : messages) {
      deliveredIntents.add(PendingIntent.getBroadcast(context, 0,
                                                      constructDeliveredIntent(context, messageId, type, isMultipart),
                                                      PendingIntentFlags.mutable()));
    }

    return deliveredIntents;
  }

  private Intent constructSentIntent(Context context, long messageId, long type, boolean isMultipart) {
    Intent pending = new Intent(SmsDeliveryListener.SENT_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsDeliveryListener.class);

    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);
    pending.putExtra("run_attempt", Math.max(runAttempt, getRunAttempt()));
    pending.putExtra("is_multipart", isMultipart);

    return pending;
  }

  private Intent constructDeliveredIntent(Context context, long messageId, long type, boolean isMultipart) {
    Intent pending = new Intent(SmsDeliveryListener.DELIVERED_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsDeliveryListener.class);
    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);
    pending.putExtra("is_multipart", isMultipart);

    return pending;
  }

  private SmsManager getSmsManagerFor(int subscriptionId) {
    if (Build.VERSION.SDK_INT >= 22 && subscriptionId != -1) {
      return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
    } else {
      return SmsManager.getDefault();
    }
  }

  private static Job.Parameters constructParameters(@NonNull Recipient destination) {
    return new Job.Parameters.Builder()
                             .setMaxAttempts(MAX_ATTEMPTS)
                             .setQueue(destination.getId().toQueueKey() + "::SMS")
                             .addConstraint(NetworkOrCellServiceConstraint.KEY)
                             .build();
  }

  private static class TooManyRetriesException extends Exception { }

  public static class Factory implements Job.Factory<SmsSendJob> {
    @Override
    public @NonNull SmsSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new SmsSendJob(parameters, data.getLong(KEY_MESSAGE_ID), data.getInt(KEY_RUN_ATTEMPT));
    }
  }
}

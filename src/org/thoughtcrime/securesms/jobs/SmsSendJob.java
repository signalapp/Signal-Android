package org.thoughtcrime.securesms.jobs;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirement;
import org.thoughtcrime.securesms.jobs.requirements.NetworkOrServiceRequirement;
import org.thoughtcrime.securesms.jobs.requirements.ServiceRequirement;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.SmsDeliveryListener;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.NumberUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.jobmanager.JobParameters;

import java.util.ArrayList;

public class SmsSendJob extends SendJob {

  private static final long   serialVersionUID = -5118520036244759718L;
  private static final String TAG              = SmsSendJob.class.getSimpleName();

  private final long messageId;

  public SmsSendJob(Context context, long messageId, String name) {
    super(context, constructParameters(context, name));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    Log.i(TAG, "onAdded() messageId: " + messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret) throws NoSuchMessageException {
    SmsDatabase      database = DatabaseFactory.getSmsDatabase(context);
    SmsMessageRecord record   = database.getMessage(messageId);

    try {
      Log.i(TAG, "Sending message: " + messageId);
      deliver(record);
      Log.i(TAG, "Sent message: " + messageId);
    } catch (UndeliverableMessageException ude) {
      Log.w(TAG, ude);
      DatabaseFactory.getSmsDatabase(context).markAsSentFailed(record.getId());
      MessageNotifier.notifyMessageDeliveryFailed(context, record.getRecipient(), record.getThreadId());
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception throwable) {
    return false;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "onCanceled() messageId: " + messageId);
    long      threadId  = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageId);
    Recipient recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
    MessageNotifier.notifyMessageDeliveryFailed(context, recipient, threadId);
  }

  private void deliver(SmsMessageRecord message)
      throws UndeliverableMessageException
  {
    if (message.isSecure() || message.isKeyExchange() || message.isEndSession()) {
      throw new UndeliverableMessageException("Trying to send a secure SMS?");
    }

    String recipient = message.getIndividualRecipient().getAddress().serialize();

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

    ArrayList<String> messages                = SmsManager.getDefault().divideMessage(message.getBody());
    ArrayList<PendingIntent> sentIntents      = constructSentIntents(message.getId(), message.getType(), messages, false);
    ArrayList<PendingIntent> deliveredIntents = constructDeliveredIntents(message.getId(), message.getType(), messages);

    // NOTE 11/04/14 -- There's apparently a bug where for some unknown recipients
    // and messages, this will throw an NPE.  We have no idea why, so we're just
    // catching it and marking the message as a failure.  That way at least it doesn't
    // repeatedly crash every time you start the app.
    try {
      getSmsManagerFor(message.getSubscriptionId()).sendMultipartTextMessage(recipient, null, messages, sentIntents, deliveredIntents);
    } catch (NullPointerException | IllegalArgumentException npe) {
      Log.w(TAG, npe);
      Log.i(TAG, "Recipient: " + recipient);
      Log.i(TAG, "Message Parts: " + messages.size());

      try {
        for (int i=0;i<messages.size();i++) {
          getSmsManagerFor(message.getSubscriptionId()).sendTextMessage(recipient, null, messages.get(i),
                                                                        sentIntents.get(i),
                                                                        deliveredIntents == null ? null : deliveredIntents.get(i));
        }
      } catch (NullPointerException | IllegalArgumentException npe2) {
        Log.w(TAG, npe);
        throw new UndeliverableMessageException(npe2);
      }
    } catch (SecurityException se) {
      Log.w(TAG, se);
      throw new UndeliverableMessageException(se);
    }
  }

  private ArrayList<PendingIntent> constructSentIntents(long messageId, long type,
                                                        ArrayList<String> messages, boolean secure)
  {
    ArrayList<PendingIntent> sentIntents = new ArrayList<>(messages.size());

    for (String ignored : messages) {
      sentIntents.add(PendingIntent.getBroadcast(context, 0,
                                                 constructSentIntent(context, messageId, type, secure, false),
                                                 0));
    }

    return sentIntents;
  }

  private ArrayList<PendingIntent> constructDeliveredIntents(long messageId, long type, ArrayList<String> messages) {
    if (!TextSecurePreferences.isSmsDeliveryReportsEnabled(context)) {
      return null;
    }

    ArrayList<PendingIntent> deliveredIntents = new ArrayList<>(messages.size());

    for (String ignored : messages) {
      deliveredIntents.add(PendingIntent.getBroadcast(context, 0,
                                                      constructDeliveredIntent(context, messageId, type),
                                                      0));
    }

    return deliveredIntents;
  }

  private Intent constructSentIntent(Context context, long messageId, long type,
                                       boolean upgraded, boolean push)
  {
    Intent pending = new Intent(SmsDeliveryListener.SENT_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsDeliveryListener.class);

    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);
    pending.putExtra("upgraded", upgraded);
    pending.putExtra("push", push);

    return pending;
  }

  private Intent constructDeliveredIntent(Context context, long messageId, long type) {
    Intent pending = new Intent(SmsDeliveryListener.DELIVERED_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsDeliveryListener.class);
    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);

    return pending;
  }

  private SmsManager getSmsManagerFor(int subscriptionId) {
    if (Build.VERSION.SDK_INT >= 22 && subscriptionId != -1) {
      return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
    } else {
      return SmsManager.getDefault();
    }
  }

  private static JobParameters constructParameters(Context context, String name) {
    JobParameters.Builder builder = JobParameters.newBuilder()
                                                 .withPersistence()
                                                 .withRequirement(new MasterSecretRequirement(context))
                                                 .withRetryCount(15)
                                                 .withGroupId(name);

    if (TextSecurePreferences.isWifiSmsEnabled(context)) {
      builder.withRequirement(new NetworkOrServiceRequirement(context));
    } else {
      builder.withRequirement(new ServiceRequirement(context));
    }

    return builder.create();
  }


}

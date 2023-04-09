package org.thoughtcrime.securesms.jobs;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.Status;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.MessageTable.InsertResult;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.SqlCipherMigrationConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.VerificationCodeParser;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class SmsReceiveJob extends BaseJob {

  public static final String KEY = "SmsReceiveJob";

  private static final String TAG = Log.tag(SmsReceiveJob.class);

  private static final String KEY_PDUS            = "pdus";
  private static final String KEY_SUBSCRIPTION_ID = "subscription_id";

  private @Nullable Object[] pdus;

  private int subscriptionId;

  public SmsReceiveJob(@Nullable Object[] pdus, int subscriptionId) {
    this(new Job.Parameters.Builder()
                           .addConstraint(SqlCipherMigrationConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .build(),
         pdus,
         subscriptionId);
  }

  private SmsReceiveJob(@NonNull Job.Parameters parameters, @Nullable Object[] pdus, int subscriptionId) {
    super(parameters);

    this.pdus           = pdus;
    this.subscriptionId = subscriptionId;
  }

  @Override
  public @Nullable byte[] serialize() {
    String[] encoded = new String[pdus.length];
    for (int i = 0; i < pdus.length; i++) {
      encoded[i] = Base64.encodeBytes((byte[]) pdus[i]);
    }

    return new JsonJobData.Builder().putStringArray(KEY_PDUS, encoded)
                                    .putInt(KEY_SUBSCRIPTION_ID, subscriptionId)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws MigrationPendingException, RetryLaterException {
    Optional<IncomingTextMessage> message = assembleMessageFragments(pdus, subscriptionId);

    if (SignalStore.account().getE164() == null) {
      Log.i(TAG, "Received an SMS before we're registered...");

      if (message.isPresent()) {
        Optional<String> token = VerificationCodeParser.parse(message.get().getMessageBody());

        if (token.isPresent()) {
          Log.i(TAG, "Received something that looks like a registration SMS. Posting a notification and broadcast.");

          NotificationManager manager      = ServiceUtil.getNotificationManager(context);
          Notification        notification = buildPreRegistrationNotification(context, message.get());
          manager.notify(NotificationIds.PRE_REGISTRATION_SMS, notification);

          Intent smsRetrieverIntent = buildSmsRetrieverIntent(message.get());
          context.sendBroadcast(smsRetrieverIntent);

          return;
        } else {
          Log.w(TAG, "Received an SMS before registration is complete. We'll try again later.");
          throw new RetryLaterException();
        }
      } else {
        Log.w(TAG, "Received an SMS before registration is complete, but couldn't assemble the message anyway. Ignoring.");
        return;
      }
    }

    if (message.isPresent() && SignalStore.account().getE164() != null && message.get().getSender().equals(Recipient.self().getId())) {
      Log.w(TAG, "Received an SMS from ourselves! Ignoring.");
    } else if (message.isPresent() && !isBlocked(message.get())) {
      Optional<InsertResult> insertResult = storeMessage(message.get());

      if (insertResult.isPresent()) {
        ApplicationDependencies.getMessageNotifier().updateNotification(context, ConversationId.forConversation(insertResult.get().getThreadId()));
      }
    } else if (message.isPresent()) {
      Log.w(TAG, "Received an SMS from a blocked user. Ignoring.");
    } else {
      Log.w(TAG, "Failed to assemble message fragments!");
    }
  }

  @Override
  public void onFailure() {

  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof MigrationPendingException ||
           exception instanceof RetryLaterException;
  }

  private boolean isBlocked(IncomingTextMessage message) {
    if (message.getSender() != null) {
      Recipient recipient = Recipient.resolved(message.getSender());
      return recipient.isBlocked();
    }

    return false;
  }

  private Optional<InsertResult> storeMessage(IncomingTextMessage message) throws MigrationPendingException {
    MessageTable database = SignalDatabase.messages();
    database.ensureMigration();

    if (TextSecurePreferences.getNeedsSqlCipherMigration(context)) {
      throw new MigrationPendingException();
    }

    if (message.isSecureMessage()) {
      IncomingTextMessage    placeholder  = new IncomingTextMessage(message, "");
      Optional<InsertResult> insertResult = database.insertMessageInbox(placeholder);
      database.markAsLegacyVersion(insertResult.get().getMessageId());

      return insertResult;
    } else {
      return database.insertMessageInbox(message);
    }
  }

  private Optional<IncomingTextMessage> assembleMessageFragments(@Nullable Object[] pdus, int subscriptionId) {
    if (pdus == null) {
      return Optional.empty();
    }

    List<IncomingTextMessage> messages = new LinkedList<>();

    for (Object pdu : pdus) {
      SmsMessage message   = SmsMessage.createFromPdu((byte[])pdu);
      Recipient  recipient = Recipient.external(context, message.getDisplayOriginatingAddress());
      messages.add(new IncomingTextMessage(recipient.getId(), message, subscriptionId));
    }

    if (messages.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(new IncomingTextMessage(messages));
  }

  private static Notification buildPreRegistrationNotification(@NonNull Context context, @NonNull IncomingTextMessage message) {
    Recipient sender = Recipient.resolved(message.getSender());

    return new NotificationCompat.Builder(context, NotificationChannels.getInstance().getMessagesChannel())
                                 .setStyle(new NotificationCompat.MessagingStyle(new Person.Builder()
                                                                 .setName(sender.getE164().orElse(""))
                                                                 .build())
                                                                 .addMessage(new NotificationCompat.MessagingStyle.Message(message.getMessageBody(),
                                                                                                                           message.getSentTimestampMillis(),
                                                                                                                           (Person) null)))
                                 .setSmallIcon(R.drawable.ic_notification)
                                 .build();
  }

  /**
   * @return An intent that is identical to the one the {@link SmsRetriever} API uses, so that
   *         we can auto-populate the SMS code on capable devices.
   */
  private static Intent buildSmsRetrieverIntent(@NonNull IncomingTextMessage message) {
    Intent intent = new Intent(SmsRetriever.SMS_RETRIEVED_ACTION);
    intent.putExtra(SmsRetriever.EXTRA_STATUS, Status.RESULT_SUCCESS);
    intent.putExtra(SmsRetriever.EXTRA_SMS_MESSAGE, message.getMessageBody());
    return intent;
  }

  public static final class Factory implements Job.Factory<SmsReceiveJob> {
    @Override
    public @NonNull SmsReceiveJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      try {
        int subscriptionId = data.getInt(KEY_SUBSCRIPTION_ID);
        String[] encoded   = data.getStringArray(KEY_PDUS);
        Object[] pdus      = new Object[encoded.length];

        for (int i = 0; i < encoded.length; i++) {
          pdus[i] = Base64.decode(encoded[i]);
        }

        return new SmsReceiveJob(parameters, pdus, subscriptionId);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }

  private class MigrationPendingException extends Exception {
  }
}

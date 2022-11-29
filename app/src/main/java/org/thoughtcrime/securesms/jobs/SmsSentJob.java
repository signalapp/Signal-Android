package org.thoughtcrime.securesms.jobs;

import android.app.Activity;
import android.telephony.SmsManager;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.service.SmsDeliveryListener;

public class SmsSentJob extends BaseJob {

  public static final String KEY = "SmsSentJob";

  private static final String TAG = Log.tag(SmsSentJob.class);

  private static final String KEY_MESSAGE_ID   = "message_id";
  private static final String KEY_IS_MULTIPART = "is_multipart";
  private static final String KEY_ACTION       = "action";
  private static final String KEY_RESULT       = "result";
  private static final String KEY_RUN_ATTEMPT  = "run_attempt";

  private final long    messageId;
  private final boolean isMultipart;
  private final String  action;
  private final int     result;
  private final int     runAttempt;

  public SmsSentJob(long messageId, boolean isMultipart, String action, int result, int runAttempt) {
    this(new Job.Parameters.Builder().build(),
         messageId,
         isMultipart,
         action,
         result,
         runAttempt);
  }

  private SmsSentJob(@NonNull Job.Parameters parameters, long messageId, boolean isMultipart, String action, int result, int runAttempt) {
    super(parameters);

    this.messageId   = messageId;
    this.isMultipart = isMultipart;
    this.action      = action;
    this.result      = result;
    this.runAttempt  = runAttempt;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putBoolean(KEY_IS_MULTIPART, isMultipart)
                             .putString(KEY_ACTION, action)
                             .putInt(KEY_RESULT, result)
                             .putInt(KEY_RUN_ATTEMPT, runAttempt)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() {
    Log.i(TAG, "Got SMS callback: " + action + " , " + result);

    switch (action) {
      case SmsDeliveryListener.SENT_SMS_ACTION:
        handleSentResult(messageId, result);
        break;
      case SmsDeliveryListener.DELIVERED_SMS_ACTION:
        handleDeliveredResult(messageId, result);
        break;
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception throwable) {
    return false;
  }

  @Override
  public void onFailure() {
  }

  private void handleDeliveredResult(long messageId, int result) {
    SignalDatabase.sms().markSmsStatus(messageId, result);
  }

  private void handleSentResult(long messageId, int result) {
    try {
      MessageTable     database = SignalDatabase.sms();
      SmsMessageRecord record   = database.getSmsMessage(messageId);

      switch (result) {
        case Activity.RESULT_OK:
          database.markAsSent(messageId, false);
          break;
        case SmsManager.RESULT_ERROR_NO_SERVICE:
        case SmsManager.RESULT_ERROR_RADIO_OFF:
          if (isMultipart) {
            Log.w(TAG, "Service connectivity problem, but not retrying due to multipart");
            database.markAsSentFailed(messageId);
            ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, record.getRecipient(), ConversationId.forConversation(record.getThreadId()));
          } else {
            Log.w(TAG, "Service connectivity problem, requeuing...");
            ApplicationDependencies.getJobManager().add(new SmsSendJob(messageId, record.getIndividualRecipient(), runAttempt + 1));
          }
          break;
        default:
          database.markAsSentFailed(messageId);
          ApplicationDependencies.getMessageNotifier().notifyMessageDeliveryFailed(context, record.getRecipient(), ConversationId.forConversation(record.getThreadId()));
      }
    } catch (NoSuchMessageException e) {
      Log.w(TAG, e);
    }
  }

  public static final class Factory implements Job.Factory<SmsSentJob> {
    @Override
    public @NonNull SmsSentJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new SmsSentJob(parameters,
                            data.getLong(KEY_MESSAGE_ID),
                            data.getBooleanOrDefault(KEY_IS_MULTIPART, true),
                            data.getString(KEY_ACTION),
                            data.getInt(KEY_RESULT),
                            data.getInt(KEY_RUN_ATTEMPT));
    }
  }
}

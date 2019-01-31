package org.thoughtcrime.securesms.jobs;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.telephony.SmsManager;

import org.thoughtcrime.securesms.jobmanager.SafeData;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.jobmanager.JobParameters;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.service.SmsDeliveryListener;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class SmsSentJob extends ContextJob {

  private static final long   serialVersionUID = -2624694558755317560L;
  private static final String TAG              = SmsSentJob.class.getSimpleName();

  private static final String KEY_MESSAGE_ID  = "message_id";
  private static final String KEY_ACTION      = "action";
  private static final String KEY_RESULT      = "result";
  private static final String KEY_RUN_ATTEMPT = "run_attempt";

  private long   messageId;
  private String action;
  private int    result;
  private int    runAttempt;

  public SmsSentJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public SmsSentJob(Context context, long messageId, String action, int result, int runAttempt) {
    super(context, JobParameters.newBuilder()
                                .create());

    this.messageId  = messageId;
    this.action     = action;
    this.result     = result;
    this.runAttempt = runAttempt;
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    messageId  = data.getLong(KEY_MESSAGE_ID);
    action     = data.getString(KEY_ACTION);
    result     = data.getInt(KEY_RESULT);
    runAttempt = data.getInt(KEY_RUN_ATTEMPT);
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putLong(KEY_MESSAGE_ID, messageId)
                      .putString(KEY_ACTION, action)
                      .putInt(KEY_RESULT, result)
                      .putInt(KEY_RUN_ATTEMPT, runAttempt)
                      .build();
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
  public boolean onShouldRetry(Exception throwable) {
    return false;
  }

  @Override
  public void onCanceled() {

  }

  private void handleDeliveredResult(long messageId, int result) {
    DatabaseFactory.getSmsDatabase(context).markStatus(messageId, result);
  }

  private void handleSentResult(long messageId, int result) {
    try {
      SmsDatabase      database = DatabaseFactory.getSmsDatabase(context);
      SmsMessageRecord record   = database.getMessage(messageId);

      switch (result) {
        case Activity.RESULT_OK:
          database.markAsSent(messageId, false);
          break;
        case SmsManager.RESULT_ERROR_NO_SERVICE:
        case SmsManager.RESULT_ERROR_RADIO_OFF:
          Log.w(TAG, "Service connectivity problem, requeuing...");
          ApplicationContext.getInstance(context)
              .getJobManager()
              .add(new SmsSendJob(context, messageId, record.getIndividualRecipient().getAddress().serialize(), runAttempt + 1));
          break;
        default:
          database.markAsSentFailed(messageId);
          MessageNotifier.notifyMessageDeliveryFailed(context, record.getRecipient(), record.getThreadId());
      }
    } catch (NoSuchMessageException e) {
      Log.w(TAG, e);
    }
  }
}

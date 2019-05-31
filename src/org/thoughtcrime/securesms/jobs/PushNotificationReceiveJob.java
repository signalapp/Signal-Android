package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

public class PushNotificationReceiveJob extends PushReceivedJob implements InjectableType {

  public static final String KEY = "PushNotificationReceiveJob";

  private static final String TAG = PushNotificationReceiveJob.class.getSimpleName();

  @Inject SignalServiceMessageReceiver receiver;

  public PushNotificationReceiveJob(Context context) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("__notification_received")
                           .setMaxAttempts(3)
                           .setMaxInstances(1)
                           .build());
    setContext(context);
  }

  private PushNotificationReceiveJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    pullAndProcessMessages(receiver, TAG, System.currentTimeMillis());
  }

  public void pullAndProcessMessages(SignalServiceMessageReceiver receiver, String tag, long startTime) throws IOException {
    synchronized (PushReceivedJob.RECEIVE_LOCK) {
      receiver.retrieveMessages(envelope -> {
        Log.i(tag, "Retrieved an envelope." + timeSuffix(startTime));
        processEnvelope(envelope);
        Log.i(tag, "Successfully processed an envelope." + timeSuffix(startTime));
      });
      TextSecurePreferences.setNeedsMessagePull(context, false);
      MessageNotifier.cancelMessagesPending(context);
    }
  }
  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    Log.w(TAG, e);
    return e instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "***** Failed to download pending message!");
//    MessageNotifier.notifyMessagesPending(getContext());
  }

  private static String timeSuffix(long startTime) {
    return " (" + (System.currentTimeMillis() - startTime) + " ms elapsed)";
  }

  public static final class Factory implements Job.Factory<PushNotificationReceiveJob> {
    @Override
    public @NonNull PushNotificationReceiveJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushNotificationReceiveJob(parameters);
    }
  }
}

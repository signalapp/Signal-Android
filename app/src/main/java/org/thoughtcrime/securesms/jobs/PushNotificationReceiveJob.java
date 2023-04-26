package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.messages.BackgroundMessageRetriever;
import org.thoughtcrime.securesms.messages.WebSocketStrategy;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public final class PushNotificationReceiveJob extends BaseJob {

  public static final String KEY = "PushNotificationReceiveJob";

  private static final String TAG = Log.tag(PushNotificationReceiveJob.class);

  private static final String KEY_FOREGROUND_SERVICE_DELAY = "foreground_delay";

  private final long foregroundServiceDelayMs;

  public PushNotificationReceiveJob() {
    this(BackgroundMessageRetriever.DO_NOT_SHOW_IN_FOREGROUND);
  }

  private PushNotificationReceiveJob(long foregroundServiceDelayMs) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("__notification_received")
                           .setMaxAttempts(3)
                           .setMaxInstancesForFactory(1)
                           .build(),
         foregroundServiceDelayMs);
  }

  private PushNotificationReceiveJob(@NonNull Job.Parameters parameters, long foregroundServiceDelayMs) {
    super(parameters);
    this.foregroundServiceDelayMs = foregroundServiceDelayMs;
  }

  public static Job withDelayedForegroundService(long foregroundServiceAfterMs) {
    return new PushNotificationReceiveJob(foregroundServiceAfterMs);
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_FOREGROUND_SERVICE_DELAY, foregroundServiceDelayMs)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    BackgroundMessageRetriever retriever = ApplicationDependencies.getBackgroundMessageRetriever();
    boolean                    result    = retriever.retrieveMessages(context, foregroundServiceDelayMs, new WebSocketStrategy());

    if (result) {
      Log.i(TAG, "Successfully pulled messages.");
    } else {
      throw new PushNetworkException("Failed to pull messages.");
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    Log.w(TAG, e);
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "***** Failed to download pending message!");
//    MessageNotifier.notifyMessagesPending(getContext());
  }

  public static final class Factory implements Job.Factory<PushNotificationReceiveJob> {
    @Override
    public @NonNull PushNotificationReceiveJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new PushNotificationReceiveJob(parameters,
                                            data.getLongOrDefault(KEY_FOREGROUND_SERVICE_DELAY, BackgroundMessageRetriever.DO_NOT_SHOW_IN_FOREGROUND));
    }
  }
}

package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.messages.WebSocketDrainer;
import org.thoughtcrime.securesms.service.DelayedNotificationController;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public final class PushNotificationReceiveJob extends BaseJob {

  public static final String KEY = "PushNotificationReceiveJob";

  private static final String TAG = Log.tag(PushNotificationReceiveJob.class);

  public PushNotificationReceiveJob() {
    this(new Job.Parameters.Builder()
             .addConstraint(NetworkConstraint.KEY)
             .setQueue("__notification_received")
             .setMaxAttempts(3)
             .setMaxInstancesForFactory(1)
             .build());
  }

  private PushNotificationReceiveJob(Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    boolean success;

    try (DelayedNotificationController unused = GenericForegroundService.startForegroundTaskDelayed(context, context.getString(R.string.BackgroundMessageRetriever_checking_for_messages), 300, R.drawable.ic_signal_refresh)) {
      success = WebSocketDrainer.blockUntilDrainedAndProcessed();
    }

    if (success) {
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
  }

  public static final class Factory implements Job.Factory<PushNotificationReceiveJob> {
    @Override
    public @NonNull PushNotificationReceiveJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new PushNotificationReceiveJob(parameters);
    }
  }
}

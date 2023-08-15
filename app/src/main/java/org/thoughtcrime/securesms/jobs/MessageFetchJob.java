package org.thoughtcrime.securesms.jobs;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.messages.WebSocketDrainer;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.service.DelayedNotificationController;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.service.NotificationController;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

/**
 * Fetches messages from the service, posting a foreground service if possible.
 */
public final class MessageFetchJob extends BaseJob {

  public static final String KEY = "PushNotificationReceiveJob";

  private static final String TAG = Log.tag(MessageFetchJob.class);

  public MessageFetchJob() {
    this(new Job.Parameters.Builder()
             .addConstraint(NetworkConstraint.KEY)
             .setQueue("__notification_received")
             .setMaxAttempts(3)
             .setMaxInstancesForFactory(1)
             .build());
  }

  private MessageFetchJob(Job.Parameters parameters) {
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

    if (Build.VERSION.SDK_INT < 31) {
      try (DelayedNotificationController unused = GenericForegroundService.startForegroundTaskDelayed(context, context.getString(R.string.BackgroundMessageRetriever_checking_for_messages), 300, R.drawable.ic_signal_refresh)) {
        success = WebSocketDrainer.blockUntilDrainedAndProcessed();
      }
    } else {
      try {
        try (NotificationController unused = GenericForegroundService.startForegroundTask(context, context.getString(R.string.BackgroundMessageRetriever_checking_for_messages), NotificationChannels.getInstance().OTHER, R.drawable.ic_signal_refresh)) {
          success = WebSocketDrainer.blockUntilDrainedAndProcessed();
        }
      } catch (UnableToStartException e) {
        Log.w(TAG, "Failed to start foreground service. Running in the background.");
        success = WebSocketDrainer.blockUntilDrainedAndProcessed();
      }
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

  public static final class Factory implements Job.Factory<MessageFetchJob> {
    @Override
    public @NonNull MessageFetchJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new MessageFetchJob(parameters);
    }
  }
}

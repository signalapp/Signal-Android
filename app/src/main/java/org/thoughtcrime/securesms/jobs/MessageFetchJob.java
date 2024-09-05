package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.messages.WebSocketDrainer;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches messages from the service, posting a foreground service if possible if the app is in the background.
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

    try (ForegroundServiceController controller = ForegroundServiceController.create(context)) {
      success = controller.awaitResult();
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

  /**
   * Controls whether or not the ForegroundService is started, based off of whether the application process is
   * in the foreground or background.
   */
  private static final class ForegroundServiceController implements AppForegroundObserver.Listener, AutoCloseable {

    private final @NonNull Context context;
    private final @NonNull AtomicReference<AutoCloseable> notificationController = new AtomicReference<>();

    private volatile boolean isRunning;

    private ForegroundServiceController(@NonNull Context context) {
      this.context = context;
    }

    @Override
    public void onForeground() {
      if (!isRunning) {
        return;
      }

      closeNotificationController();
    }

    @Override
    public void onBackground() {
      if (!isRunning) {
        return;
      }

      if (notificationController.get() != null) {
        Log.w(TAG, "Already displaying or displayed a foreground notification.");
        return;
      }

      if (Build.VERSION.SDK_INT < 31) {
        notificationController.set(GenericForegroundService.startForegroundTaskDelayed(context, context.getString(R.string.BackgroundMessageRetriever_checking_for_messages), 300, R.drawable.ic_signal_refresh));
      } else {
        try {
          notificationController.set(GenericForegroundService.startForegroundTask(context, context.getString(R.string.BackgroundMessageRetriever_checking_for_messages), NotificationChannels.getInstance().OTHER, R.drawable.ic_signal_refresh));
        } catch (UnableToStartException e) {
          Log.w(TAG, "Failed to start foreground service. Running without a foreground service.");
        }
      }
    }

    @Override
    public void close() {
      AppForegroundObserver.removeListener(this);
      closeNotificationController();
    }

    public boolean awaitResult() {
      isRunning = true;

      boolean success;
      try {
        success = WebSocketDrainer.blockUntilDrainedAndProcessed();
      } finally {
        isRunning = false;
      }

      return success;
    }

    private void closeNotificationController() {
      AutoCloseable controller = notificationController.get();
      if (controller == null) {
        return;
      }

      try {
        controller.close();
      } catch (Exception e) {
        Log.w(TAG, "Exception thrown while closing notification controller", e);
      }
    }

    static ForegroundServiceController create(@NonNull Context context) {
      ForegroundServiceController instance = new ForegroundServiceController(context);
      AppForegroundObserver.addListener(instance);

      return instance;
    }
  }

  public static final class Factory implements Job.Factory<MessageFetchJob> {
    @Override
    public @NonNull MessageFetchJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new MessageFetchJob(parameters);
    }
  }
}

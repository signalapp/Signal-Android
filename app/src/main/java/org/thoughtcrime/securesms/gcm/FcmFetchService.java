package org.thoughtcrime.securesms.gcm;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.messaging.RemoteMessage;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.messages.BackgroundMessageRetriever;
import org.thoughtcrime.securesms.messages.RestStrategy;
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This service does the actual network fetch in response to an FCM message.
 *
 * Our goals with FCM processing are as follows:
 * (1) Ensure some service is active for the duration of the fetch and processing stages.
 * (2) Do not make unnecessary network requests.
 *
 * To fulfill goal 1, this service will not call {@link #stopSelf()} until there is no more running
 * requests.
 *
 * To fulfill goal 2, this service will not enqueue a fetch if there are already 2 active fetches
 * (or rather, 1 active and 1 waiting, since we use a single thread executor).
 *
 * Unfortunately we can't do this all in {@link FcmReceiveService} because it won't let us process
 * the next FCM message until {@link FcmReceiveService#onMessageReceived(RemoteMessage)} returns,
 * but as soon as that method returns, it could also destroy the service. By not letting us control
 * when the service is destroyed, we can't accomplish both goals within that service.
 */
public class FcmFetchService extends Service {

  private static final String TAG = Log.tag(FcmFetchService.class);

  private static final SerialMonoLifoExecutor EXECUTOR = new SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED);

  private final AtomicInteger activeCount = new AtomicInteger(0);

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    boolean performedReplace = EXECUTOR.enqueue(this::fetch);

    if (performedReplace) {
      Log.i(TAG, "Already have one running and one enqueued. Ignoring.");
    } else {
      int count = activeCount.incrementAndGet();
      Log.i(TAG, "Incrementing active count to " + count);
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "onDestroy()");
  }

  @Override
  public @Nullable IBinder onBind(Intent intent) {
    return null;
  }

  private void fetch() {
    retrieveMessages(this);

    if (activeCount.decrementAndGet() == 0) {
      Log.d(TAG, "No more active. Stopping.");
      stopSelf();
    }
  }

  static void retrieveMessages(@NonNull Context context) {
    BackgroundMessageRetriever retriever = ApplicationDependencies.getBackgroundMessageRetriever();
    boolean                    success   = retriever.retrieveMessages(context, new RestStrategy(), new RestStrategy());

    if (success) {
      Log.i(TAG, "Successfully retrieved messages.");
    } else {
      if (Build.VERSION.SDK_INT >= 26) {
        Log.w(TAG, "Failed to retrieve messages. Scheduling on the system JobScheduler (API " + Build.VERSION.SDK_INT + ").");
        FcmJobService.schedule(context);
      } else {
        Log.w(TAG, "Failed to retrieve messages. Scheduling on JobManager (API " + Build.VERSION.SDK_INT + ").");
        ApplicationDependencies.getJobManager().add(new PushNotificationReceiveJob(context));
      }
    }
  }
}


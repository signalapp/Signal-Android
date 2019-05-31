package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobs.FcmRefreshJob;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.PowerManagerCompat;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.WakeLockUtil;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class FcmService extends FirebaseMessagingService implements InjectableType {

  private static final String TAG = FcmService.class.getSimpleName();

  private static final String   WAKE_LOCK_TAG  = "FcmMessageProcessing";
  private static final long     SOCKET_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

  @Inject SignalServiceMessageReceiver messageReceiver;

  private static int activeCount;

  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    Log.i(TAG, "FCM message... Original Priority: " + remoteMessage.getOriginalPriority() + ", Actual Priority: " + remoteMessage.getPriority());
    ApplicationContext.getInstance(getApplicationContext()).injectDependencies(this);

    WakeLockUtil.runWithLock(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK, 60000, WAKE_LOCK_TAG, () -> {
      handleReceivedNotification(getApplicationContext());
    });
  }

  @Override
  public void onNewToken(String token) {
    Log.i(TAG, "onNewToken()");

    if (!TextSecurePreferences.isPushRegistered(getApplicationContext())) {
      Log.i(TAG, "Got a new FCM token, but the user isn't registered.");
      return;
    }

    ApplicationContext.getInstance(getApplicationContext())
                      .getJobManager()
                      .add(new FcmRefreshJob());
  }

  private void handleReceivedNotification(Context context) {
    if (!incrementActiveGcmCount()) {
      Log.i(TAG, "Skipping FCM processing -- there's already one enqueued.");
      return;
    }

    TextSecurePreferences.setNeedsMessagePull(context, true);

    long         startTime    = System.currentTimeMillis();
    PowerManager powerManager = ServiceUtil.getPowerManager(getApplicationContext());
    boolean      doze         = PowerManagerCompat.isDeviceIdleMode(powerManager);
    boolean      network      = new NetworkConstraint.Factory(ApplicationContext.getInstance(context)).create().isMet();

    if (doze || !network) {
      Log.w(TAG, "We may be operating in a constrained environment. Doze: " + doze + " Network: " + network);
    }

    try {
      messageReceiver.setSoTimeoutMillis(SOCKET_TIMEOUT);
      new PushNotificationReceiveJob(context).pullAndProcessMessages(messageReceiver, TAG, startTime);
    } catch (IOException e) {
      if (Build.VERSION.SDK_INT >= 26) {
        Log.i(TAG, "Failed to retrieve the envelope. Scheduling on the system JobScheduler (API " + Build.VERSION.SDK_INT + ").", e);
        FcmJobService.schedule(context);
      } else {
        Log.i(TAG, "Failed to retrieve the envelope. Scheduling on JobManager (API " + Build.VERSION.SDK_INT + ").", e);
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new PushNotificationReceiveJob(context));
      }
    }

    decrementActiveGcmCount();
    Log.i(TAG, "Processing complete.");
  }

  private static synchronized boolean incrementActiveGcmCount() {
    if (activeCount < 2) {
      activeCount++;
      return true;
    }
    return false;
  }

  private static synchronized void decrementActiveGcmCount() {
    activeCount--;
  }
}

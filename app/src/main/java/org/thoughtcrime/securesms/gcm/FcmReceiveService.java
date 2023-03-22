package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.FcmRefreshJob;
import org.thoughtcrime.securesms.jobs.SubmitRateLimitPushChallengeJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.registration.PushChallengeRequest;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.NetworkUtil;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class FcmReceiveService extends FirebaseMessagingService {

  private static final String TAG = Log.tag(FcmReceiveService.class);

  private static final long FCM_FOREGROUND_INTERVAL = TimeUnit.MINUTES.toMillis(3);

  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    Log.i(TAG, String.format(Locale.US,
                             "onMessageReceived() ID: %s, Delay: %d, Priority: %d, Original Priority: %d, Network: %s",
                             remoteMessage.getMessageId(),
                             (System.currentTimeMillis() - remoteMessage.getSentTime()),
                             remoteMessage.getPriority(),
                             remoteMessage.getOriginalPriority(),
                             NetworkUtil.getNetworkStatus(this)));

    String registrationChallenge = remoteMessage.getData().get("challenge");
    String rateLimitChallenge    = remoteMessage.getData().get("rateLimitChallenge");

    if (registrationChallenge != null) {
      handleRegistrationPushChallenge(registrationChallenge);
    } else if (rateLimitChallenge != null) {
      handleRateLimitPushChallenge(rateLimitChallenge);
    } else {
      handleReceivedNotification(ApplicationDependencies.getApplication(), remoteMessage);
    }
  }

  @Override
  public void onDeletedMessages() {
    Log.w(TAG, "onDeleteMessages() -- Messages may have been dropped. Doing a normal message fetch.");
    handleReceivedNotification(ApplicationDependencies.getApplication(), null);
  }

  @Override
  public void onNewToken(String token) {
    Log.i(TAG, "onNewToken()");

    if (!SignalStore.account().isRegistered()) {
      Log.i(TAG, "Got a new FCM token, but the user isn't registered.");
      return;
    }

    ApplicationDependencies.getJobManager().add(new FcmRefreshJob());
  }

  @Override
  public void onMessageSent(@NonNull String s) {
    Log.i(TAG, "onMessageSent()" + s);
  }

  @Override
  public void onSendError(@NonNull String s, @NonNull Exception e) {
    Log.w(TAG, "onSendError()", e);
  }

  private static void handleReceivedNotification(Context context, @Nullable RemoteMessage remoteMessage) {
    boolean enqueueSuccessful = false;

    try {
      boolean highPriority         = remoteMessage != null && remoteMessage.getPriority() == RemoteMessage.PRIORITY_HIGH;
      long    timeSinceLastRefresh = System.currentTimeMillis() - SignalStore.misc().getLastFcmForegroundServiceTime();

      Log.d(TAG, String.format(Locale.US, "[handleReceivedNotification] API: %s, FeatureFlag: %s, RemoteMessagePriority: %s, TimeSinceLastRefresh: %s ms", Build.VERSION.SDK_INT, FeatureFlags.useFcmForegroundService(), remoteMessage != null ? remoteMessage.getPriority() : "n/a", timeSinceLastRefresh));

      if (highPriority && FeatureFlags.useFcmForegroundService()) {
        enqueueSuccessful = FcmFetchManager.enqueue(context, true);
        SignalStore.misc().setLastFcmForegroundServiceTime(System.currentTimeMillis());
      } else if (highPriority && Build.VERSION.SDK_INT >= 31 && timeSinceLastRefresh > FCM_FOREGROUND_INTERVAL) {
        enqueueSuccessful = FcmFetchManager.enqueue(context, true);
        SignalStore.misc().setLastFcmForegroundServiceTime(System.currentTimeMillis());
      } else if (highPriority || Build.VERSION.SDK_INT < 26 || remoteMessage == null) {
        enqueueSuccessful = FcmFetchManager.enqueue(context, false);
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to start service.", e);
      enqueueSuccessful = false;
    }

    if (!enqueueSuccessful) {
      Log.w(TAG, "Unable to start service. Falling back to legacy approach.");
      FcmFetchManager.retrieveMessages(context);
    }
  }

  private static void handleRegistrationPushChallenge(@NonNull String challenge) {
    Log.d(TAG, "Got a registration push challenge.");
    PushChallengeRequest.postChallengeResponse(challenge);
  }

  private static void handleRateLimitPushChallenge(@NonNull String challenge) {
    Log.d(TAG, "Got a rate limit push challenge.");
    ApplicationDependencies.getJobManager().add(new SubmitRateLimitPushChallengeJob(challenge));
  }
}
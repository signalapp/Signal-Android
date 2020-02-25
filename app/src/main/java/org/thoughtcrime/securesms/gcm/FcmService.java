package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.FcmRefreshJob;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.registration.PushChallengeRequest;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

public class FcmService extends FirebaseMessagingService {

  private static final String TAG = FcmService.class.getSimpleName();

  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    Log.i(TAG, "FCM message... Delay: " + (System.currentTimeMillis() - remoteMessage.getSentTime()));

    String challenge = remoteMessage.getData().get("challenge");
    if (challenge != null) {
      handlePushChallenge(challenge);
    } else {
      handleReceivedNotification(getApplicationContext());
    }
  }

  @Override
  public void onNewToken(String token) {
    Log.i(TAG, "onNewToken()");

    if (!TextSecurePreferences.isPushRegistered(getApplicationContext())) {
      Log.i(TAG, "Got a new FCM token, but the user isn't registered.");
      return;
    }

    ApplicationDependencies.getJobManager().add(new FcmRefreshJob());
  }

  private static void handleReceivedNotification(Context context) {
    MessageRetriever retriever = ApplicationDependencies.getMessageRetriever();
    boolean          success   = retriever.retrieveMessages(context, new RestStrategy(), new RestStrategy());

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

    Log.i(TAG, "Processing complete.");
  }

  private static void handlePushChallenge(@NonNull String challenge) {
    Log.d(TAG, String.format("Got a push challenge \"%s\"", challenge));

    PushChallengeRequest.postChallengeResponse(challenge);
  }
}

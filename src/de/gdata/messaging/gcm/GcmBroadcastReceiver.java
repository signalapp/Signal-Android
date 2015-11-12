package de.gdata.messaging.gcm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.jobs.PushReceiveJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import de.gdata.messaging.util.GService;

public class GcmBroadcastReceiver extends BroadcastReceiver {

  private static final String TAG = GcmBroadcastReceiver.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    GoogleCloudMessaging gcm         = GoogleCloudMessaging.getInstance(context);
    String               messageType = gcm.getMessageType(intent);

    if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
      Log.w(TAG, "GCM message...");

      Toast.makeText(GService.appContext, "pf got a message (could be profilupdate)", Toast.LENGTH_LONG).show();

      if (!TextSecurePreferences.isPushRegistered(context)) {
        Log.w(TAG, "Not push registered!");
        return;
      }

      String messageData = intent.getStringExtra("message");
      String receiptData = intent.getStringExtra("receipt");

      if      (!TextUtils.isEmpty(messageData)) handleReceivedMessage(context, messageData);
      else if (!TextUtils.isEmpty(receiptData)) handleReceivedMessage(context, receiptData);
      else if (intent.hasExtra("notification")) handleReceivedNotification(context);
    }
    setResultCode(Activity.RESULT_OK);
  }

  private void handleReceivedMessage(Context context, String data) {
    ApplicationContext.getInstance(context)
            .getJobManager()
                      .add(new PushReceiveJob(context, data));
  }

  private void handleReceivedNotification(Context context) {
    ApplicationContext.getInstance(context)
            .getJobManager()
            .add(new PushNotificationReceiveJob(context));

  }
}
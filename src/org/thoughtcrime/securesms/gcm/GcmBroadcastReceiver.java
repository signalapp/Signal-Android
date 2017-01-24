package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.thoughtcrime.redphone.RedPhoneService;
import org.thoughtcrime.redphone.crypto.EncryptedSignalMessage;
import org.thoughtcrime.redphone.crypto.InvalidEncryptedSignalException;
import org.thoughtcrime.redphone.signaling.SessionDescriptor;
import org.thoughtcrime.redphone.signaling.signals.CompressedInitiateSignalProtocol.CompressedInitiateSignal;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;

public class GcmBroadcastReceiver extends WakefulBroadcastReceiver {

  private static final String TAG = GcmBroadcastReceiver.class.getSimpleName();

  @Override
  public void onReceive(Context context, Intent intent) {
    GoogleCloudMessaging gcm         = GoogleCloudMessaging.getInstance(context);
    String               messageType = gcm.getMessageType(intent);

    if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
      Log.w(TAG, "GCM message...");

      if (!TextSecurePreferences.isPushRegistered(context)) {
        Log.w(TAG, "Not push registered!");
        return;
      }

      String messageData = intent.getStringExtra("message");
      String receiptData = intent.getStringExtra("receipt");
      String callData    = intent.getStringExtra("call");

      if      (!TextUtils.isEmpty(messageData)) handleReceivedMessage(context, messageData);
      else if (!TextUtils.isEmpty(receiptData)) handleReceivedMessage(context, receiptData);
      else if (intent.hasExtra("notification")) handleReceivedNotification(context);
      else if (!TextUtils.isEmpty(callData))    handleReceivedCall(context, callData);
    }
  }

  private void handleReceivedMessage(Context context, String data) {
    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new PushContentReceiveJob(context, data));
  }

  private void handleReceivedNotification(Context context) {
    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new PushNotificationReceiveJob(context));
  }

  private void handleReceivedCall(final Context context, final String data) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        try {
          String                   signalingKey           = TextSecurePreferences.getSignalingKey(context);
          EncryptedSignalMessage   encryptedSignalMessage = new EncryptedSignalMessage(data, signalingKey);
          CompressedInitiateSignal signal                 = CompressedInitiateSignal.parseFrom(encryptedSignalMessage.getPlaintext());
          Recipients               recipients             = RecipientFactory.getRecipientsFromString(context, signal.getInitiator(), false);

          if (!recipients.isBlocked() && !Util.numberShouldBeIgnored(context, signal.getInitiator())) {
            Intent intent = new Intent(context, RedPhoneService.class);
            intent.setAction(RedPhoneService.ACTION_INCOMING_CALL);
            intent.putExtra(RedPhoneService.EXTRA_REMOTE_NUMBER, signal.getInitiator());
            intent.putExtra(RedPhoneService.EXTRA_SESSION_DESCRIPTOR, new SessionDescriptor(signal.getServerName(),
                                                                                            signal.getPort(),
                                                                                            signal.getSessionId(),
                                                                                            signal.getVersion()));
            context.startService(intent);
          } else {
            Log.w(TAG, "*** Received incoming call from blocked number, ignoring...");
          }
        } catch (InvalidEncryptedSignalException | IOException e) {
          Log.w(TAG, e);
        }

        return null;
      }
    }.execute();
  }
}
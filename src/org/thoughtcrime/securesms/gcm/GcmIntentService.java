package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;
import com.google.thoughtcrimegson.Gson;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.service.RegistrationService;
import org.thoughtcrime.securesms.service.SendReceiveService;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.util.ArrayList;

public class GcmIntentService extends GCMBaseIntentService {

  public static final String GCM_SENDER_ID = "312334754206";

  @Override
  protected void onRegistered(Context context, String registrationId) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    if (!preferences.getBoolean(ApplicationPreferencesActivity.REGISTERED_GCM_PREF, false)) {
      Intent intent = new Intent(RegistrationService.GCM_REGISTRATION_EVENT);
      intent.putExtra(RegistrationService.GCM_REGISTRATION_ID, registrationId);
      sendBroadcast(intent);
    } else {
      try {
        getGcmSocket(context).registerGcmId(registrationId);
      } catch (IOException e) {
        Log.w("GcmIntentService", e);
      } catch (RateLimitException e) {
        Log.w("GcmIntentService", e);
      }
    }
  }

  @Override
  protected void onUnregistered(Context context, String registrationId) {
    try {
      getGcmSocket(context).unregisterGcmId(registrationId);
    } catch (IOException ioe) {
      Log.w("GcmIntentService", ioe);
    } catch (RateLimitException e) {
      Log.w("GcmIntentService", e);
    }
  }


  @Override
  protected void onMessage(Context context, Intent intent) {
    Log.w("GcmIntentService", "Got GCM message!");
    String data = intent.getStringExtra("message");
    Log.w("GcmIntentService", "GCM message: " + data);

    if (Util.isEmpty(data))
      return;

    IncomingGcmMessage message              = new Gson().fromJson(data, IncomingGcmMessage.class);
    ArrayList<IncomingTextMessage> messages = new ArrayList<IncomingTextMessage>();
    messages.add(new IncomingTextMessage(message));

    Intent receivedIntent = new Intent(context, SendReceiveService.class);
    receivedIntent.setAction(SendReceiveService.RECEIVE_SMS_ACTION);
    receivedIntent.putParcelableArrayListExtra("text_messages", messages);
    context.startService(receivedIntent);
  }

  @Override
  protected void onError(Context context, String s) {
    Log.w("GcmIntentService", "GCM Error: " + s);
  }

  private PushServiceSocket getGcmSocket(Context context) {
    String localNumber = TextSecurePreferences.getLocalNumber(context);
    String password    = TextSecurePreferences.getPushServerPassword(context);
    return new PushServiceSocket(context, localNumber, password);
  }
}

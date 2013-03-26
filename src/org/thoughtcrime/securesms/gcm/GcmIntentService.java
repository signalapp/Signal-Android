package org.thoughtcrime.securesms.gcm;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.android.gcm.GCMBaseIntentService;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.service.RegistrationService;

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
//
//      // Talk to the server directly.
//
    }
  }

  @Override
  protected void onMessage(Context context, Intent intent) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  protected void onError(Context context, String s) {
    //To change body of implemented methods use File | Settings | File Templates.
  }


  @Override
  protected void onUnregistered(Context context, String s) {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}

package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;

public class QuickResponseService extends Service {

  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!TelephonyManager.ACTION_RESPOND_VIA_MESSAGE.equals(intent.getAction())) {
      Log.w("QuickResponseService", "Received unknown intent: " + intent.getAction());
      return START_NOT_STICKY;
    }

    Toast.makeText(this,
                   getString(R.string.QuickResponseService_sorry_quick_response_is_not_yet_supported_by_textsecure),
                   Toast.LENGTH_LONG).show();

    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}

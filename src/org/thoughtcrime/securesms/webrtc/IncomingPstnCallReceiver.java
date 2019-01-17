package org.thoughtcrime.securesms.webrtc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.telephony.TelephonyManager;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.service.WebRtcCallService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Listens for incoming PSTN calls and rejects them if a RedPhone call is already in progress.
 *
 * Unstable use of reflection employed to gain access to ITelephony.
 *
 */
public class IncomingPstnCallReceiver extends BroadcastReceiver {

  private static final String TAG = IncomingPstnCallReceiver.class.getName();

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.i(TAG, "Checking incoming call...");

    if (intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) == null) {
      Log.w(TAG, "Telephony event does not contain number...");
      return;
    }

    if (!intent.getStringExtra(TelephonyManager.EXTRA_STATE).equals(TelephonyManager.EXTRA_STATE_RINGING)) {
      Log.w(TAG, "Telephony event is not state ringing...");
      return;
    }

    InCallListener listener = new InCallListener(context, new Handler());

    WebRtcCallService.isCallActive(context, listener);
  }

  private static class InCallListener extends ResultReceiver {

    private final Context context;

    InCallListener(Context context, Handler handler) {
      super(handler);
      this.context = context.getApplicationContext();
    }

    protected void onReceiveResult(int resultCode, Bundle resultData) {
      if (resultCode == 1) {
        Log.i(TAG, "Attempting to deny incoming PSTN call.");

        TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);

        try {
          Method getTelephony = tm.getClass().getDeclaredMethod("getITelephony");
          getTelephony.setAccessible(true);
          Object telephonyService = getTelephony.invoke(tm);
          Method endCall = telephonyService.getClass().getDeclaredMethod("endCall");
          endCall.invoke(telephonyService);
          Log.i(TAG, "Denied Incoming Call.");
        } catch (NoSuchMethodException e) {
          Log.w(TAG, "Unable to access ITelephony API", e);
        } catch (IllegalAccessException e) {
          Log.w(TAG, "Unable to access ITelephony API", e);
        } catch (InvocationTargetException e) {
          Log.w(TAG, "Unable to access ITelephony API", e);
        }
      }
    }
  }
}

package org.thoughtcrime.securesms.websocket;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class NetworkReceiver extends BroadcastReceiver {
  public static final String TAG = "WebSocket.NetworkReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    /**
     * If we are GCM registered, we don't need the Broadcast receiver
     * */
    if (TextSecurePreferences.isGcmRegistered(context)) {
      PackageManager pm      = context.getPackageManager();
      ComponentName compName = new ComponentName(context, NetworkReceiver.class);

      pm.setComponentEnabledSetting(compName,
                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                    PackageManager.DONT_KILL_APP);
    }
    /**
     * This checks if we are either GCM registered or not push registered
     * if either one is the case, don't proceed
     * */
    if (TextSecurePreferences.isGcmRegistered(context) || !TextSecurePreferences.isPushRegistered(context)) {
      return;
    }
    ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo networkInfo  = conn.getActiveNetworkInfo();
    if (networkInfo != null && networkInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
      context.startService(PushService.startIntent(context));
    } else {
      AlarmManager am         = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
      PendingIntent operation = PendingIntent.getService(context, 0, PushService.pingIntent(context),
                                                         PendingIntent.FLAG_NO_CREATE);
      if (operation != null) {
        am.cancel(operation);
        operation.cancel();
      }
      context.startService(PushService.closeIntent(context));
    }
  }
}

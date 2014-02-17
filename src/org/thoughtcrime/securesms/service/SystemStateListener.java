package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SystemStateListener {

  private final TelephonyListener    telephonyListener    = new TelephonyListener();
  private final ConnectivityListener connectivityListener = new ConnectivityListener();
  private final Context              context;
  private final TelephonyManager     telephonyManager;
  private final ConnectivityManager  connectivityManager;

  public SystemStateListener(Context context) {
    this.context             = context.getApplicationContext();
    this.telephonyManager    = (TelephonyManager)    context.getSystemService(Context.TELEPHONY_SERVICE);
    this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
  }

  public void registerForRadioChange() {
    Log.w("SystemStateListener", "Registering for radio changes...");
    unregisterForConnectivityChange();

    telephonyManager.listen(telephonyListener, PhoneStateListener.LISTEN_SERVICE_STATE);
  }

  public void registerForConnectivityChange() {
    Log.w("SystemStateListener", "Registering for any connectivity changes...");
    unregisterForConnectivityChange();

    telephonyManager.listen(telephonyListener, PhoneStateListener.LISTEN_SERVICE_STATE);
    context.registerReceiver(connectivityListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
  }

  public void unregisterForConnectivityChange() {
    telephonyManager.listen(telephonyListener, 0);

    try {
      context.unregisterReceiver(connectivityListener);
    } catch (IllegalArgumentException iae) {
      Log.w("SystemStateListener", iae);
    }
  }

  public boolean isConnected() {
    return
        connectivityManager.getActiveNetworkInfo() != null &&
            connectivityManager.getActiveNetworkInfo().isConnected();
  }

  private void sendSmsOutbox(Context context) {
    Intent smsSenderIntent = new Intent(SendReceiveService.SEND_SMS_ACTION, null, context,
                                        SendReceiveService.class);
    context.startService(smsSenderIntent);
  }

  private void sendMmsOutbox(Context context) {
    Intent mmsSenderIntent = new Intent(SendReceiveService.SEND_MMS_ACTION, null, context,
                                        SendReceiveService.class);
    context.startService(mmsSenderIntent);
  }

  private class TelephonyListener extends PhoneStateListener {
    @Override
    public void onServiceStateChanged(ServiceState state) {
      if (state.getState() == ServiceState.STATE_IN_SERVICE) {
        Log.w("SystemStateListener", "In service, sending sms/mms outboxes...");
        sendSmsOutbox(context);
        sendMmsOutbox(context);
      }
    }
  }

  private class ConnectivityListener extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent != null && ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
        if (connectivityManager.getActiveNetworkInfo() != null &&
            connectivityManager.getActiveNetworkInfo().isConnected())
        {
          Log.w("SystemStateListener", "Got connectivity action: " + intent.toString());
          sendSmsOutbox(context);
          sendMmsOutbox(context);
        }
      }
    }
  }
}

package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.ServiceState;

import org.thoughtcrime.securesms.mms.MmsRadio;

public class SystemStateListener extends BroadcastReceiver {

  public  static final String ACTION_SERVICE_STATE       = "android.intent.action.SERVICE_STATE";
  private static final String ACTION_CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE";

  private static final SystemStateListener instance = new SystemStateListener();

  public static SystemStateListener getInstance() {
    return instance;
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

  private void handleRadioServiceStateChange(Context context, Intent intent) {
    int state = intent.getIntExtra("state", -31337);

    if (state == ServiceState.STATE_IN_SERVICE) {
      sendSmsOutbox(context);
    }
  }

  private void handleDataServiceStateChange(Context context, Intent intent) {
    ConnectivityManager connectivityManager
      = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

    NetworkInfo networkInfo = connectivityManager.getNetworkInfo(MmsRadio.TYPE_MOBILE_MMS);

    if (networkInfo != null  && networkInfo.isAvailable()) {
      sendMmsOutbox(context);
    }
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) return;

    if (intent.getAction().equals(ACTION_SERVICE_STATE)) {
      handleRadioServiceStateChange(context, intent);
    } else if (intent.getAction().equals(ACTION_CONNECTIVITY_CHANGE)) {
      handleDataServiceStateChange(context, intent);
    }
  }

}

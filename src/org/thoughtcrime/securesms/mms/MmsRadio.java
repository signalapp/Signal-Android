package org.thoughtcrime.securesms.mms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.util.Log;

import org.thoughtcrime.securesms.util.Util;

public class MmsRadio {

  private static MmsRadio instance;

  public static synchronized MmsRadio getInstance(Context context) {
    if (instance == null)
      instance = new MmsRadio(context.getApplicationContext());

    return instance;
  }

  ///

  private static final String FEATURE_ENABLE_MMS = "enableMMS";
  private static final int APN_ALREADY_ACTIVE    = 0;
  public  static final int TYPE_MOBILE_MMS       = 2;

  private final Context context;

  private ConnectivityManager   connectivityManager;
  private ConnectivityListener  connectivityListener;
  private PowerManager.WakeLock wakeLock;
  private int connectedCounter = 0;

  private MmsRadio(Context context) {
    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    this.context             = context;
    this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    this.wakeLock            = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MMS Connection");
    this.wakeLock.setReferenceCounted(true);
  }

  public synchronized void disconnect() {
    Log.w("MmsRadio", "MMS Radio Disconnect Called...");
    wakeLock.release();
    connectedCounter--;

    Log.w("MmsRadio", "Reference count: " + connectedCounter);

    if (connectedCounter == 0) {
      Log.w("MmsRadio", "Turning off MMS radio...");
      connectivityManager.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, FEATURE_ENABLE_MMS);

      if (connectivityListener != null) {
        Log.w("MmsRadio", "Unregistering receiver...");
        context.unregisterReceiver(connectivityListener);
        connectivityListener = null;
      }
    }
  }

  public synchronized void connect() throws MmsRadioException {
    int status = connectivityManager.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE,
                                                              FEATURE_ENABLE_MMS);

    Log.w("MmsRadio", "startUsingNetworkFeature status: " + status);

    if (status == APN_ALREADY_ACTIVE) {
      wakeLock.acquire();
      connectedCounter++;
      return;
    } else {
      wakeLock.acquire();
      connectedCounter++;

      if (connectivityListener == null) {
        IntentFilter filter  = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        connectivityListener = new ConnectivityListener();
        context.registerReceiver(connectivityListener, filter);
      }

      Util.wait(this, 30000);

      if (!isConnected()) {
        Log.w("MmsRadio", "Got back from connectivity wait, and not connected...");
        disconnect();
        throw new MmsRadioException("Unable to successfully enable MMS radio.");
      }
    }
  }

  private boolean isConnected() {
    NetworkInfo info = connectivityManager.getNetworkInfo(TYPE_MOBILE_MMS);

    Log.w("MmsRadio", "Connected: " + info);

    if ((info == null) || (info.getType() != TYPE_MOBILE_MMS) || !info.isConnected())
      return false;

    return true;
  }

  private boolean isConnectivityPossible() {
    NetworkInfo networkInfo = connectivityManager.getNetworkInfo(TYPE_MOBILE_MMS);

    return networkInfo != null  && networkInfo.isAvailable();
  }

  private boolean isConnectivityFailure() {
    NetworkInfo networkInfo = connectivityManager.getNetworkInfo(TYPE_MOBILE_MMS);

    return networkInfo == null || networkInfo.getDetailedState() == NetworkInfo.DetailedState.FAILED;
  }

  private synchronized void issueConnectivityChange() {
    if (isConnected()) {
      Log.w("MmsRadio", "Notifying connected...");
      notifyAll();
      return;
    }

    if (!isConnected() && (isConnectivityFailure() || !isConnectivityPossible())) {
      Log.w("MmsRadio", "Notifying not connected...");
      notifyAll();
      return;
    }
  }

  private class ConnectivityListener extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w("MmsRadio", "Got connectivity change...");
      issueConnectivityChange();
    }
  }


}

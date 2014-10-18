package org.whispersystems.jobqueue.requirements;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkRequirementProvider implements RequirementProvider {

  private RequirementListener listener;

  public NetworkRequirementProvider(Context context) {
    context.getApplicationContext().registerReceiver(new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (listener == null) {
          return;
        }

        ConnectivityManager cm        = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo         netInfo   = cm.getActiveNetworkInfo();
        boolean             connected = netInfo != null && netInfo.isConnectedOrConnecting();

        if (connected) {
          listener.onRequirementStatusChanged();
        }
      }
    }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
  }

  @Override
  public void setListener(RequirementListener listener) {
    this.listener = listener;
  }

}

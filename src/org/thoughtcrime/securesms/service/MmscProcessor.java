/** 
 * Copyright (C) 2011 Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.service;

import java.util.LinkedList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public abstract class MmscProcessor {

  private static final String FEATURE_ENABLE_MMS = "enableMMS";
  private static final int APN_ALREADY_ACTIVE    = 0;
  public  static final int TYPE_MOBILE_MMS       = 2;

  private ConnectivityManager connectivityManager;
  private ConnectivityListener connectivityListener;
  private WakeLock wakeLock;	
	
  protected final Context context;
	
  public MmscProcessor(Context context) {
    this.context = context;
    PowerManager powerManager         = (PowerManager)context.getSystemService(Context.POWER_SERVICE);  
    this.connectivityManager          = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
    this.wakeLock                     = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MMS Connection");
    this.wakeLock.setReferenceCounted(false);
  }
	
  protected boolean isConnected() {
    NetworkInfo info = connectivityManager.getNetworkInfo(TYPE_MOBILE_MMS);
	
    Log.w("MmsService", "NetworkInfo: " + info);
        
    if ((info == null) || (info.getType() != TYPE_MOBILE_MMS) || !info.isConnected())
      return false;

    return true;
  }
	
  protected abstract String getConnectivityAction();
	
  protected void issueConnectivityRequest() {
    int status = connectivityManager.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, FEATURE_ENABLE_MMS);
		
    if (status == APN_ALREADY_ACTIVE) {
      issueConnectivityChange();
    } else if (connectivityListener == null) {
      IntentFilter filter  = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
      connectivityListener = new ConnectivityListener();
      context.registerReceiver(connectivityListener, filter);

      wakeLock.acquire();
    }
  }
	
  protected boolean isConnectivityPossible() {
    NetworkInfo networkInfo = connectivityManager.getNetworkInfo(TYPE_MOBILE_MMS);
    Log.w("MmsService", "Got network info: " + networkInfo);
    return networkInfo != null  && networkInfo.isAvailable();
  }

  protected void finishConnectivity() {
    Log.w("MmsService", "Calling stop using network feature!");
    connectivityManager.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, FEATURE_ENABLE_MMS);
		
    if (connectivityListener != null) {
      context.unregisterReceiver(connectivityListener);
      connectivityListener = null;
    }			
		
    if (this.wakeLock.isHeld())
      this.wakeLock.release();
  }
	
  private void issueConnectivityChange() {
    Intent intent = new Intent(context, SendReceiveService.class);
    intent.setAction(getConnectivityAction());
    context.startService(intent);
  }
	
  private class ConnectivityListener extends BroadcastReceiver {
    @Override
      public void onReceive(Context context, Intent intent) {
      Log.w("MmsService", "Dispatching connectivity change...");
      issueConnectivityChange();
      Log.w("MmsService", "Dispatched...");
    }
  }
}

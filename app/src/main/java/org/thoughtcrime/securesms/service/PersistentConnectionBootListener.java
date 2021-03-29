package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.signal.core.util.logging.Log;


public class PersistentConnectionBootListener extends BroadcastReceiver {

  private static final String TAG = Log.tag(PersistentConnectionBootListener.class);

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
      Log.i(TAG, "Received boot event. Application should be started, allowing non-GCM devices to start a foreground service.");
    }
  }
}

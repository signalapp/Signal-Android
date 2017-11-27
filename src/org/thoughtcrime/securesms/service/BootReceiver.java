package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
      Intent messageRetrievalService = new Intent(context, MessageRetrievalService.class);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(messageRetrievalService);
      else                                                context.startService(messageRetrievalService);
    }
  }

}

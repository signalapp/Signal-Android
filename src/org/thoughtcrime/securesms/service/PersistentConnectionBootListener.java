package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.util.TextSecurePreferences;


public class PersistentConnectionBootListener extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (TextSecurePreferences.isGcmDisabled(context)) {
      Intent serviceIntent = new Intent(context, MessageRetrievalService.class);
      serviceIntent.setAction(MessageRetrievalService.ACTION_INITIALIZE);
      context.startService(serviceIntent);
    }
  }
}

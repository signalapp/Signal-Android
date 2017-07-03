package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    Intent messageRetrievalService = new Intent(context, MessageRetrievalService.class);
    context.startService(messageRetrievalService);
  }

}

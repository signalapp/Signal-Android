package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.jobs.PushNotificationReceiveJob;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    ApplicationContext.getInstance(context).getJobManager().add(new PushNotificationReceiveJob(context));
  }
}

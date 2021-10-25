package org.thoughtcrime.securesms.service;

import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.SubscriptionKeepAliveJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.util.concurrent.TimeUnit;

/**
 * Manages the scheduling of jobs for keeping a subscription id alive.
 */
public class SubscriberIdKeepAliveListener extends PersistentAlarmManagerListener {

  private static final long INTERVAL = TimeUnit.DAYS.toMillis(3);

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return SignalStore.donationsValues().getLastKeepAliveLaunchTime() + INTERVAL;
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (SignalStore.donationsValues().getSubscriber() != null) {
      ApplicationDependencies.getJobManager().add(new SubscriptionKeepAliveJob());
    }

    long now = System.currentTimeMillis();
    SignalStore.donationsValues().setLastKeepAliveLaunchTime(now);

    return now + INTERVAL;
  }

  public static void schedule(Context context) {
    new SubscriberIdKeepAliveListener().onReceive(context, new Intent());
  }
}

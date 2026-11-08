package org.thoughtcrime.securesms.service;


import android.content.Context;

import androidx.annotation.VisibleForTesting;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.RotateCertificateJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.util.concurrent.TimeUnit;

public class RotateSenderCertificateListener extends PersistentAlarmManagerListener {

  private static final String TAG = Log.tag(RotateSenderCertificateListener.class);

  private static final long INTERVAL = TimeUnit.DAYS.toMillis(1);

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return resolveNextExecutionTime(SignalStore.certificate().getLastRotationTime(), System.currentTimeMillis());
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    AppDependencies.getJobManager().add(new RotateCertificateJob());

    return System.currentTimeMillis() + INTERVAL;
  }

  @VisibleForTesting
  static long resolveNextExecutionTime(long lastRotationTime, long now) {
    if (lastRotationTime > now) {
      Log.w(TAG, "Last rotation time is " + (lastRotationTime - now) + " ms in the future. Ignoring it and rotating now.");
      return 0;
    }

    return lastRotationTime + INTERVAL;
  }

  public static void schedule(Context context) {
    new RotateSenderCertificateListener().onReceive(context, getScheduleIntent());
  }

}

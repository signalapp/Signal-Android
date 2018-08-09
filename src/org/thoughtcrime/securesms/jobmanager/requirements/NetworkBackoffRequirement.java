package org.thoughtcrime.securesms.jobmanager.requirements;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.dependencies.ContextDependent;
import org.thoughtcrime.securesms.logging.Log;

import java.util.concurrent.TimeUnit;

/**
 * Uses exponential backoff to re-schedule network jobs to be retried in the future.
 */
public class NetworkBackoffRequirement implements Requirement, ContextDependent {

  private static final String TAG = NetworkBackoffRequirement.class.getSimpleName();

  private static final long MAX_WAIT = TimeUnit.SECONDS.toMillis(30);

  private transient Context context;

  public NetworkBackoffRequirement(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public boolean isPresent(@NonNull Job job) {
    return new NetworkRequirement(context).isPresent() && System.currentTimeMillis() >= calculateNextRunTime(job);
  }

  @Override
  public void onRetry(@NonNull Job job) {
  }

  @Override
  public void setContext(Context context) {
    this.context = context.getApplicationContext();
  }

  private static long calculateNextRunTime(@NonNull Job job) {
    return 0;
  }
}

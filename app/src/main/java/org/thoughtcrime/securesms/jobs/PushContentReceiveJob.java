package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;

public class PushContentReceiveJob extends PushReceivedJob {

  public static final String KEY = "PushContentReceiveJob";

  public PushContentReceiveJob(Context context) {
    this(new Job.Parameters.Builder().build());
    setContext(context);
  }

  private PushContentReceiveJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() { }

  @Override
  public void onCanceled() { }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return false;
  }

  public static final class Factory implements Job.Factory<PushContentReceiveJob> {
    @Override
    public @NonNull PushContentReceiveJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new PushContentReceiveJob(parameters);
    }
  }
}

package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.ProfileUtil;

import java.util.concurrent.TimeUnit;

public final class ProfileUploadJob extends BaseJob {

  private static final String TAG = Log.tag(ProfileUploadJob.class);

  public static final String KEY = "ProfileUploadJob";

  public static final String QUEUE = "ProfileAlteration";

  public ProfileUploadJob() {
    this(new Job.Parameters.Builder()
                            .addConstraint(NetworkConstraint.KEY)
                            .setQueue(QUEUE)
                            .setLifespan(TimeUnit.DAYS.toMillis(30))
                            .setMaxAttempts(Parameters.UNLIMITED)
                            .setMaxInstancesForFactory(2)
                            .build());
  }

  private ProfileUploadJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  protected void onRun() throws Exception {
    if (!SignalStore.account().isRegistered()) {
      Log.w(TAG, "Not registered. Skipping.");
      return;
    }

    ProfileUtil.uploadProfile(context);
    Log.i(TAG, "Profile uploaded.");
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return true;
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<ProfileUploadJob> {

    @Override
    public @NonNull ProfileUploadJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new ProfileUploadJob(parameters);
    }
  }
}

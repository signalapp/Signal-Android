package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.recipients.Recipient;

public class RotateProfileKeyJob extends BaseJob {

  public static String KEY = "RotateProfileKeyJob";

  public RotateProfileKeyJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("__ROTATE_PROFILE_KEY__")
                           .setMaxInstancesForFactory(2)
                           .build());
  }

  private RotateProfileKeyJob(@NonNull Job.Parameters parameters) {
    super(parameters);
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
  public void onRun() {
    ProfileKey newProfileKey = ProfileKeyUtil.createNew();
    Recipient  self          = Recipient.self();

    SignalDatabase.recipients().setProfileKey(self.getId(), newProfileKey);
  }

  @Override
  public void onFailure() {
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return false;
  }

  public static final class Factory implements Job.Factory<RotateProfileKeyJob> {
    @Override
    public @NonNull RotateProfileKeyJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new RotateProfileKeyJob(parameters);
    }
  }
}

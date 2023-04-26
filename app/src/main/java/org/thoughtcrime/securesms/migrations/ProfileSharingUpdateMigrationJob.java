package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

/**
 * Updates profile sharing flag to true if conversation is pre-message request enable time.
 */
public class ProfileSharingUpdateMigrationJob extends MigrationJob {

  public static final String KEY = "ProfileSharingUpdateMigrationJob";

  ProfileSharingUpdateMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private ProfileSharingUpdateMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return true;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    long messageRequestEnableTime = SignalStore.misc().getMessageRequestEnableTime();
    SignalDatabase.recipients().markPreMessageRequestRecipientsAsProfileSharingEnabled(messageRequestEnableTime);
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<ProfileSharingUpdateMigrationJob> {
    @Override
    public @NonNull ProfileSharingUpdateMigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new ProfileSharingUpdateMigrationJob(parameters);
    }
  }
}

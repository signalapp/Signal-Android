package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.util.UUID;

public class RotateProfileKeyJob extends BaseJob {

  public static String KEY = "RotateProfileKeyJob";

  public RotateProfileKeyJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("__ROTATE_PROFILE_KEY__")
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(25)
                           .setMaxInstances(1)
                           .build());
  }

  private RotateProfileKeyJob(@NonNull Job.Parameters parameters) {
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
  public void onRun() throws Exception {
    SignalServiceAccountManager accountManager    = ApplicationDependencies.getSignalServiceAccountManager();
    RecipientDatabase           recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    ProfileKey                  profileKey        = ProfileKeyUtil.createNew();
    Recipient                   self              = Recipient.self();

    recipientDatabase.setProfileKey(self.getId(), profileKey);
     try (StreamDetails avatarStream = AvatarHelper.getSelfProfileAvatarStream(context)) {
      if (FeatureFlags.VERSIONED_PROFILES) {
        accountManager.setVersionedProfile(self.getUuid().get(),
                                           profileKey,
                                           Recipient.self().getProfileName().serialize(),
                                           avatarStream);
      } else {
        accountManager.setProfileName(profileKey, Recipient.self().getProfileName().serialize());
        accountManager.setProfileAvatar(profileKey, avatarStream);
      }
    }

    ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
  }

  @Override
  public void onFailure() {

  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException;
  }

  public static final class Factory implements Job.Factory<RotateProfileKeyJob> {
    @Override
    public @NonNull RotateProfileKeyJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RotateProfileKeyJob(parameters);
    }
  }
}

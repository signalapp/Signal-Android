package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

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
    SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    byte[]                      profileKey     = ProfileKeyUtil.rotateProfileKey(context);

    accountManager.setProfileName(profileKey, TextSecurePreferences.getProfileName(context));
    accountManager.setProfileAvatar(profileKey, getProfileAvatar());

    ApplicationDependencies.getJobManager().add(new RefreshAttributesJob());
  }

  @Override
  public void onCanceled() {

  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException;
  }

  private @Nullable StreamDetails getProfileAvatar() {
    try {
      File avatarFile = AvatarHelper.getAvatarFile(context, Recipient.self().getId());

      if (avatarFile.exists()) {
        return new StreamDetails(new FileInputStream(avatarFile), "image/jpeg", avatarFile.length());
      }
    } catch (IOException e) {
      return null;
    }
    return null;
  }

  public static final class Factory implements Job.Factory<RotateProfileKeyJob> {
    @Override
    public @NonNull RotateProfileKeyJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RotateProfileKeyJob(parameters);
    }
  }
}

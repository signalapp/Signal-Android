package org.thoughtcrime.securesms.jobs;


import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.ProfileAvatarFileDetails;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class RetrieveProfileAvatarJob extends BaseJob {

  public static final String KEY = "RetrieveProfileAvatarJob";

  private static final String TAG = Log.tag(RetrieveProfileAvatarJob.class);

  private static final long MIN_TIME_BETWEEN_FORCE_RETRY = TimeUnit.DAYS.toMillis(1);

  private static final String KEY_PROFILE_AVATAR = "profile_avatar";
  private static final String KEY_RECIPIENT      = "recipient";
  private static final String KEY_FORCE_UPDATE   = "force";

  private final String    profileAvatar;
  private final Recipient recipient;
  private final boolean   forceUpdate;

  public static void enqueueForceUpdate(Recipient recipient) {
    SignalExecutors.BOUNDED.execute(() -> AppDependencies.getJobManager().add(new RetrieveProfileAvatarJob(recipient, recipient.resolve().getProfileAvatar(), true)));
  }

  public RetrieveProfileAvatarJob(Recipient recipient, String profileAvatar) {
    this(recipient, profileAvatar, false);
  }

  public RetrieveProfileAvatarJob(Recipient recipient, String profileAvatar, boolean forceUpdate) {
    this(new Job.Parameters.Builder().setQueue("RetrieveProfileAvatarJob::" + recipient.getId().toQueueKey())
                                     .addConstraint(NetworkConstraint.KEY)
                                     .setLifespan(TimeUnit.HOURS.toMillis(1))
                                     .build(),
         recipient,
         profileAvatar,
         forceUpdate);
  }

  private RetrieveProfileAvatarJob(@NonNull Job.Parameters parameters, @NonNull Recipient recipient, String profileAvatar, boolean forceUpdate) {
    super(parameters);

    this.recipient     = recipient;
    this.profileAvatar = profileAvatar;
    this.forceUpdate   = forceUpdate;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_PROFILE_AVATAR, profileAvatar)
                                    .putString(KEY_RECIPIENT, recipient.getId().serialize())
                                    .putBoolean(KEY_FORCE_UPDATE, forceUpdate)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    RecipientTable database   = SignalDatabase.recipients();
    ProfileKey     profileKey = ProfileKeyUtil.profileKeyOrNull(recipient.resolve().getProfileKey());

    if (profileKey == null) {
      Log.w(TAG, "Recipient profile key is gone!");
      return;
    }

    if (forceUpdate) {
      ProfileAvatarFileDetails details = recipient.getProfileAvatarFileDetails();
      if (!details.hasFile() || (details.getLastModified() > System.currentTimeMillis() || details.getLastModified() + MIN_TIME_BETWEEN_FORCE_RETRY < System.currentTimeMillis())) {
        Log.i(TAG, "Forcing re-download of avatar.");
      } else {
        Log.i(TAG, "Too early to force re-download avatar.");
        return;
      }
    } else if (profileAvatar != null && profileAvatar.equals(recipient.resolve().getProfileAvatar())) {
      Log.w(TAG, "Already retrieved profile avatar: " + profileAvatar);
      return;
    }

    if (TextUtils.isEmpty(profileAvatar)) {
      if (AvatarHelper.hasAvatar(context, recipient.getId())) {
        Log.w(TAG, "Removing profile avatar (no url) for: " + recipient.getId().serialize());
        AvatarHelper.delete(context, recipient.getId());
        database.setProfileAvatar(recipient.getId(), profileAvatar);
      }

      return;
    }

    File downloadDestination = File.createTempFile("avatar", "jpg", context.getCacheDir());

    try {
      SignalServiceMessageReceiver receiver = AppDependencies.getSignalServiceMessageReceiver();

      try (InputStream avatarStream = receiver.retrieveProfileAvatar(profileAvatar, downloadDestination, profileKey, AvatarHelper.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
        AvatarHelper.setAvatar(context, recipient.getId(), avatarStream);

        if (recipient.isSelf()) {
          SignalStore.misc().setHasEverHadAnAvatar(true);
        }
      } catch (AssertionError e) {
        throw new IOException("Failed to copy stream. Likely a Conscrypt issue.", e);
      }
    } catch (PushNetworkException e) {
      if (e.getCause() instanceof NonSuccessfulResponseCodeException) {
        Log.w(TAG, "Removing profile avatar (no image available) for: " + recipient.getId().serialize());
        AvatarHelper.delete(context, recipient.getId());
      } else {
        throw e;
      }
    } finally {
      if (downloadDestination != null) downloadDestination.delete();
    }

    database.setProfileAvatar(recipient.getId(), profileAvatar);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<RetrieveProfileAvatarJob> {

    @Override
    public @NonNull RetrieveProfileAvatarJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new RetrieveProfileAvatarJob(parameters,
                                          Recipient.resolved(RecipientId.from(data.getString(KEY_RECIPIENT))),
                                          data.getString(KEY_PROFILE_AVATAR),
                                          data.getBooleanOrDefault(KEY_FORCE_UPDATE, false));
    }
  }
}

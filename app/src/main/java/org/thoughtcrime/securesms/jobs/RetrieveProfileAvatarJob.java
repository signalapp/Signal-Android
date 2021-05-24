package org.thoughtcrime.securesms.jobs;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.session.libsession.avatars.AvatarHelper;
import org.session.libsession.messaging.utilities.Data;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.DownloadUtilities;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Util;
import org.session.libsignal.streams.ProfileCipherInputStream;
import org.session.libsignal.exceptions.PushNetworkException;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

public class RetrieveProfileAvatarJob extends BaseJob implements InjectableType {

  public static final String KEY = "RetrieveProfileAvatarJob";

  private static final String TAG = RetrieveProfileAvatarJob.class.getSimpleName();

  private static final int MAX_PROFILE_SIZE_BYTES = 10 * 1024 * 1024;

  private static final String KEY_PROFILE_AVATAR = "profile_avatar";
  private static final String KEY_ADDRESS        = "address";


  private String    profileAvatar;
  private Recipient recipient;

  public RetrieveProfileAvatarJob(Recipient recipient, String profileAvatar) {
    this(new Job.Parameters.Builder()
            .setQueue("RetrieveProfileAvatarJob" + recipient.getAddress().serialize())
            .addConstraint(NetworkConstraint.KEY)
            .setLifespan(TimeUnit.HOURS.toMillis(1))
            .setMaxAttempts(10)
            .build(),
        recipient,
        profileAvatar);
  }

  private RetrieveProfileAvatarJob(@NonNull Job.Parameters parameters, @NonNull Recipient recipient, String profileAvatar) {
    super(parameters);
    this.recipient     = recipient;
    this.profileAvatar = profileAvatar;
  }

  @Override
  public @NonNull
  Data serialize() {
    return new Data.Builder()
        .putString(KEY_PROFILE_AVATAR, profileAvatar)
        .putString(KEY_ADDRESS, recipient.getAddress().serialize())
        .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    RecipientDatabase database   = DatabaseFactory.getRecipientDatabase(context);
    byte[]            profileKey = recipient.resolve().getProfileKey();

    if (profileKey == null) {
      Log.w(TAG, "Recipient profile key is gone!");
      return;
    }

    if (Util.equals(profileAvatar, recipient.resolve().getProfileAvatar())) {
      Log.w(TAG, "Already retrieved profile avatar: " + profileAvatar);
      return;
    }

    if (TextUtils.isEmpty(profileAvatar)) {
      Log.w(TAG, "Removing profile avatar for: " + recipient.getAddress().serialize());
      AvatarHelper.delete(context, recipient.getAddress());
      database.setProfileAvatar(recipient, profileAvatar);
      return;
    }

    File downloadDestination = File.createTempFile("avatar", ".jpg", context.getCacheDir());

    try {
      DownloadUtilities.downloadFile(downloadDestination, profileAvatar);
      InputStream avatarStream       = new ProfileCipherInputStream(new FileInputStream(downloadDestination), profileKey);
      File        decryptDestination = File.createTempFile("avatar", ".jpg", context.getCacheDir());

      Util.copy(avatarStream, new FileOutputStream(decryptDestination));
      decryptDestination.renameTo(AvatarHelper.getAvatarFile(context, recipient.getAddress()));
    } finally {
      if (downloadDestination != null) downloadDestination.delete();
    }

    if (recipient.isLocalNumber()) {
      TextSecurePreferences.setProfileAvatarId(context, new SecureRandom().nextInt());
    }
    database.setProfileAvatar(recipient, profileAvatar);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
  }

  public static final class Factory implements Job.Factory<RetrieveProfileAvatarJob> {

    private final Application application;

    public Factory(Application application) {
      this.application = application;
    }

    @Override
    public @NonNull RetrieveProfileAvatarJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RetrieveProfileAvatarJob(parameters,
                                          Recipient.from(application, Address.fromSerialized(data.getString(KEY_ADDRESS)), true),
                                          data.getString(KEY_PROFILE_AVATAR));
    }
  }
}

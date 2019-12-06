package org.thoughtcrime.securesms.usernames;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.CreateProfileActivity;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.w3c.dom.Text;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.Executor;

class ProfileEditOverviewRepository {

  private static final String TAG = Log.tag(ProfileEditOverviewRepository.class);

  private final Application                 application;
  private final SignalServiceAccountManager accountManager;
  private final Executor                    executor;

  ProfileEditOverviewRepository() {
    this.application    = ApplicationDependencies.getApplication();
    this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    this.executor       = SignalExecutors.UNBOUNDED;
  }

  void getProfileAvatar(@NonNull Callback<Optional<byte[]>> callback) {
    executor.execute(() -> callback.onResult(getProfileAvatarInternal()));
  }

  void setProfileAvatar(@NonNull byte[] data, @NonNull Callback<ProfileAvatarResult> callback) {
    executor.execute(() -> callback.onResult(setProfileAvatarInternal(data)));
  }

  void deleteProfileAvatar(@NonNull Callback<ProfileAvatarResult> callback) {
    executor.execute(() -> callback.onResult(deleteProfileAvatarInternal()));
  }

  void getProfileName(@NonNull Callback<Optional<String>> callback) {
    executor.execute(() -> callback.onResult(getProfileNameInternal()));
  }

  void getUsername(@NonNull Callback<Optional<String>> callback) {
    executor.execute(() -> callback.onResult(getUsernameInternal()));
  }

  @WorkerThread
  private @NonNull Optional<byte[]> getProfileAvatarInternal() {
    RecipientId selfId = Recipient.self().getId();

    if (AvatarHelper.getAvatarFile(application, selfId).exists() && AvatarHelper.getAvatarFile(application, selfId).length() > 0) {
      try {
        return Optional.of(Util.readFully(AvatarHelper.getInputStreamFor(application, selfId)));
      } catch (IOException e) {
        Log.w(TAG, "Failed to read avatar!", e);
        return Optional.absent();
      }
    } else {
      return Optional.absent();
    }
  }

  @WorkerThread
  private @NonNull ProfileAvatarResult setProfileAvatarInternal(@NonNull byte[] data) {
    StreamDetails avatar = new StreamDetails(new ByteArrayInputStream(data), MediaUtil.IMAGE_JPEG, data.length);
    try {
      accountManager.setProfileAvatar(ProfileKeyUtil.getProfileKey(application), avatar);
      AvatarHelper.setAvatar(application, Recipient.self().getId(), data);
      TextSecurePreferences.setProfileAvatarId(application, new SecureRandom().nextInt());
      return ProfileAvatarResult.SUCCESS;
    } catch (IOException e) {
      return ProfileAvatarResult.NETWORK_FAILURE;
    }
  }

  @WorkerThread
  private @NonNull ProfileAvatarResult deleteProfileAvatarInternal() {
    try {
      accountManager.setProfileAvatar(ProfileKeyUtil.getProfileKey(application), null);
      AvatarHelper.delete(application, Recipient.self().getId());
      TextSecurePreferences.setProfileAvatarId(application, 0);
      return ProfileAvatarResult.SUCCESS;
    } catch (IOException e) {
      return ProfileAvatarResult.NETWORK_FAILURE;
    }
  }

  @WorkerThread
  private @NonNull Optional<String> getProfileNameInternal() {
    try {
      SignalServiceProfile profile              = retrieveOwnProfile();
      String               encryptedProfileName = profile.getName();
      String               plaintextProfileName = null;

      if (encryptedProfileName != null) {
        ProfileCipher profileCipher = new ProfileCipher(ProfileKeyUtil.getProfileKey(application));
        plaintextProfileName = new String(profileCipher.decryptName(Base64.decode(encryptedProfileName)));
      }

      TextSecurePreferences.setProfileName(application, plaintextProfileName);
      DatabaseFactory.getRecipientDatabase(application).setProfileName(Recipient.self().getId(), plaintextProfileName);
    } catch (IOException | InvalidCiphertextException e) {
      Log.w(TAG, "Failed to retrieve profile name remotely! Using locally-cached version.");
    }

    return Optional.fromNullable(TextSecurePreferences.getProfileName(application));
  }

  @WorkerThread
  private @NonNull Optional<String> getUsernameInternal() {
    try {
      SignalServiceProfile profile = retrieveOwnProfile();
      TextSecurePreferences.setLocalUsername(application, profile.getUsername());
      DatabaseFactory.getRecipientDatabase(application).setUsername(Recipient.self().getId(), profile.getUsername());
    } catch (IOException e) {
      Log.w(TAG, "Failed to retrieve username remotely! Using locally-cached version.");
    }
    return Optional.fromNullable(TextSecurePreferences.getLocalUsername(application));
  }


  private SignalServiceProfile retrieveOwnProfile() throws IOException {
    SignalServiceAddress         address  = new SignalServiceAddress(TextSecurePreferences.getLocalUuid(application), TextSecurePreferences.getLocalNumber(application));
    SignalServiceMessageReceiver receiver = ApplicationDependencies.getSignalServiceMessageReceiver();
    SignalServiceMessagePipe     pipe     = IncomingMessageObserver.getPipe();

    if (pipe != null) {
      try {
        return pipe.getProfile(address, Optional.absent());
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }

    return receiver.retrieveProfile(address, Optional.absent());
  }

  enum ProfileAvatarResult {
    SUCCESS, NETWORK_FAILURE
  }

  interface Callback<E> {
    void onResult(@NonNull E result);
  }
}

package org.thoughtcrime.securesms.usernames.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;

import java.io.IOException;
import java.util.concurrent.Executor;

class ProfileEditNameRepository {

  private final Application                 application;
  private final SignalServiceAccountManager accountManager;
  private final Executor                    executor;

  ProfileEditNameRepository() {
    this.application    = ApplicationDependencies.getApplication();
    this.accountManager = ApplicationDependencies.getSignalServiceAccountManager();
    this.executor       = SignalExecutors.UNBOUNDED;
  }

  void setProfileName(@NonNull String profileName, @NonNull Callback<ProfileNameResult> callback) {
    executor.execute(() -> callback.onResult(setProfileNameInternal(profileName)));
  }

  @WorkerThread
  private @NonNull ProfileNameResult setProfileNameInternal(@NonNull String profileName) {
    Util.sleep(1000);
    try {
      accountManager.setProfileName(ProfileKeyUtil.getProfileKey(application), profileName);
      TextSecurePreferences.setProfileName(application, profileName);
      DatabaseFactory.getRecipientDatabase(application).setProfileName(Recipient.self().getId(), profileName);
      return ProfileNameResult.SUCCESS;
    } catch (IOException e) {
      return ProfileNameResult.NETWORK_FAILURE;
    }
  }

  enum ProfileNameResult {
    SUCCESS, NETWORK_FAILURE
  }

  interface Callback<E> {
    void onResult(@NonNull E result);
  }
}

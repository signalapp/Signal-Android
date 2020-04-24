package org.thoughtcrime.securesms.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.AppCapabilities;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.KbsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;

import java.io.IOException;

public class RefreshAttributesJob extends BaseJob {

  public static final String KEY = "RefreshAttributesJob";

  private static final String TAG = RefreshAttributesJob.class.getSimpleName();

  public RefreshAttributesJob() {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("RefreshAttributesJob")
                           .build());
  }

  private RefreshAttributesJob(@NonNull Job.Parameters parameters) {
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
  public void onRun() throws IOException {
    if (!TextSecurePreferences.isPushRegistered(context) || TextSecurePreferences.getLocalNumber(context) == null) {
      Log.w(TAG, "Not yet registered. Skipping.");
      return;
    }

    int       registrationId              = TextSecurePreferences.getLocalRegistrationId(context);
    boolean   fetchesMessages             = TextSecurePreferences.isFcmDisabled(context);
    byte[]    unidentifiedAccessKey       = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());
    boolean   universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);
    String    registrationLockV1          = null;
    String    registrationLockV2          = null;
    KbsValues kbsValues                   = SignalStore.kbsValues();

    if (kbsValues.isV2RegistrationLockEnabled()) {
      registrationLockV2 = kbsValues.getRegistrationLockToken();
    } else if (TextSecurePreferences.isV1RegistrationLockEnabled(context)) {
      //noinspection deprecation Ok to read here as they have not migrated
      registrationLockV1 = TextSecurePreferences.getDeprecatedV1RegistrationLockPin(context);
    }

    Log.i(TAG, "Calling setAccountAttributes() reglockV1? " + !TextUtils.isEmpty(registrationLockV1) + ", reglockV2? " + !TextUtils.isEmpty(registrationLockV2) + ", pin? " + kbsValues.hasPin());

    SignalServiceAccountManager signalAccountManager = ApplicationDependencies.getSignalServiceAccountManager();
    signalAccountManager.setAccountAttributes(null, registrationId, fetchesMessages,
                                              registrationLockV1, registrationLockV2,
                                              unidentifiedAccessKey, universalUnidentifiedAccess,
                                              AppCapabilities.getCapabilities(kbsValues.hasPin()));
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to update account attributes!");
  }

  public static class Factory implements Job.Factory<RefreshAttributesJob> {
    @Override
    public @NonNull RefreshAttributesJob create(@NonNull Parameters parameters, @NonNull org.thoughtcrime.securesms.jobmanager.Data data) {
      return new RefreshAttributesJob(parameters);
    }
  }
}

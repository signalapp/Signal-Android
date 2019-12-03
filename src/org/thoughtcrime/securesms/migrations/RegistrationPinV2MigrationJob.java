package org.thoughtcrime.securesms.migrations;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobs.BaseJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.RegistrationLockData;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.registrationpin.InvalidPinException;

import java.io.IOException;

/**
 * Deliberately not a {@link MigrationJob} because it is not something that needs to run at app start.
 * This migration can run at anytime.
 */
public final class RegistrationPinV2MigrationJob extends BaseJob {

  private static final String TAG = Log.tag(RegistrationPinV2MigrationJob.class);

  public static final String KEY = "RegistrationPinV2MigrationJob";

  public RegistrationPinV2MigrationJob() {
    this(new Parameters.Builder()
                       .setQueue(KEY)
                       .setMaxInstances(1)
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(Job.Parameters.IMMORTAL)
                       .setMaxAttempts(Job.Parameters.UNLIMITED)
                       .build());
  }

  private RegistrationPinV2MigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  protected void onRun() throws IOException, UnauthenticatedResponseException {
    if (!FeatureFlags.KBS) {
      Log.i(TAG, "Not migrating pin to KBS");
      return;
    }

    if (!TextSecurePreferences.isRegistrationLockEnabled(context)) {
      Log.i(TAG, "Registration lock disabled");
      return;
    }

    if (!TextSecurePreferences.hasOldRegistrationLockPin(context)) {
      Log.i(TAG, "No old pin to migrate");
      return;
    }

    //noinspection deprecation Only acceptable place to read the old pin.
    String registrationLockPin = TextSecurePreferences.getDeprecatedRegistrationLockPin(context);

    if (registrationLockPin == null | TextUtils.isEmpty(registrationLockPin)) {
      Log.i(TAG, "No old pin to migrate");
      return;
    }

    Log.i(TAG, "Migrating pin to Key Backup Service");

    try {
      RegistrationLockData registrationPinV2Key = ApplicationDependencies.getKeyBackupService()
                                                                         .newPinChangeSession()
                                                                         .setPin(registrationLockPin);

      TextSecurePreferences.setRegistrationLockMasterKey(context, registrationPinV2Key, System.currentTimeMillis());
    } catch (InvalidPinException e) {
      Log.w(TAG, "The V1 pin cannot be migrated.", e);
      return;
    }

    Log.i(TAG, "Pin migrated to Key Backup Service");
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onCanceled() {
  }

  public static class Factory implements Job.Factory<RegistrationPinV2MigrationJob> {
    @Override
    public @NonNull RegistrationPinV2MigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RegistrationPinV2MigrationJob(parameters);
    }
  }
}

package org.thoughtcrime.securesms.migrations;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobs.BaseJob;
import org.thoughtcrime.securesms.pin.PinState;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Migrates an existing V1 registration lock user to a V2 registration lock that is backed by a
 * Signal PIN.
 *
 * Deliberately not a {@link MigrationJob} because it is not something that needs to run at app start.
 * This migration can run at anytime.
 */
public final class RegistrationPinV2MigrationJob extends BaseJob {

  private static final String TAG = Log.tag(RegistrationPinV2MigrationJob.class);

  public static final String KEY = "RegistrationPinV2MigrationJob";

  public RegistrationPinV2MigrationJob() {
    this(new Parameters.Builder()
                       .setQueue(KEY)
                       .setMaxInstancesForFactory(1)
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
  protected void onRun() throws IOException, UnauthenticatedResponseException, InvalidKeyException {
    if (!TextSecurePreferences.isV1RegistrationLockEnabled(context)) {
      Log.i(TAG, "Registration lock disabled");
      return;
    }

    //noinspection deprecation Only acceptable place to read the old pin.
    String pinValue = TextSecurePreferences.getDeprecatedV1RegistrationLockPin(context);

    if (pinValue == null | TextUtils.isEmpty(pinValue)) {
      Log.i(TAG, "No old pin to migrate");
      return;
    }

    Log.i(TAG, "Migrating pin to Key Backup Service");
    PinState.onMigrateToRegistrationLockV2(context, pinValue);
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
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<RegistrationPinV2MigrationJob> {
    @Override
    public @NonNull RegistrationPinV2MigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RegistrationPinV2MigrationJob(parameters);
    }
  }
}

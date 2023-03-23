package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.migrations.KbsEnclaveMigrationJob;
import org.thoughtcrime.securesms.pin.PinState;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;

/**
 * Should only be enqueued by {@link KbsEnclaveMigrationJob}. Does the actual work of migrating KBS
 * data to the new enclave and deleting it from the old enclave(s).
 */
public class KbsEnclaveMigrationWorkerJob extends BaseJob {

  public static final String KEY = "KbsEnclaveMigrationWorkerJob";

  private static final String TAG = Log.tag(KbsEnclaveMigrationWorkerJob.class);

  public KbsEnclaveMigrationWorkerJob() {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(Parameters.IMMORTAL)
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .setQueue("KbsEnclaveMigrationWorkerJob")
                       .setMaxInstancesForFactory(1)
                       .build());
  }

  private KbsEnclaveMigrationWorkerJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public void onRun() throws IOException, UnauthenticatedResponseException {
    String pin = SignalStore.kbsValues().getPin();

    if (SignalStore.kbsValues().hasOptedOut()) {
      Log.w(TAG, "Opted out of KBS! Nothing to migrate.");
      return;
    }

    if (pin == null) {
      Log.w(TAG, "No PIN available! Can't migrate!");
      return;
    }

    PinState.onMigrateToNewEnclave(pin);
    Log.i(TAG, "Migration successful!");
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException ||
           e instanceof UnauthenticatedResponseException;
  }

  @Override
  public long getNextRunAttemptBackoff(int pastAttemptCount, @NonNull Exception exception) {
    if (exception instanceof NonSuccessfulResponseCodeException) {
      if (((NonSuccessfulResponseCodeException) exception).is5xx()) {
        return BackoffUtil.exponentialBackoff(pastAttemptCount, FeatureFlags.getServerErrorMaxBackoff());
      }
    }

    return super.getNextRunAttemptBackoff(pastAttemptCount, exception);
  }

  @Override
  public void onFailure() {
    throw new AssertionError("This job should never fail. " + getClass().getSimpleName());
  }

  public static class Factory implements Job.Factory<KbsEnclaveMigrationWorkerJob> {
    @Override
    public @NonNull KbsEnclaveMigrationWorkerJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new KbsEnclaveMigrationWorkerJob(parameters);
    }
  }
}

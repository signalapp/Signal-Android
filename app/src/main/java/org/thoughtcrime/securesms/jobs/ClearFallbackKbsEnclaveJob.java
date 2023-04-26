package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.KbsEnclave;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.pin.KbsEnclaves;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Clears data from an old KBS enclave.
 */
public class ClearFallbackKbsEnclaveJob extends BaseJob {

  public static final String KEY = "ClearFallbackKbsEnclaveJob";

  private static final String TAG = Log.tag(ClearFallbackKbsEnclaveJob.class);

  private static final String KEY_ENCLAVE_NAME = "enclaveName";
  private static final String KEY_SERVICE_ID   = "serviceId";
  private static final String KEY_MR_ENCLAVE   = "mrEnclave";

  private final KbsEnclave enclave;

  ClearFallbackKbsEnclaveJob(@NonNull KbsEnclave enclave) {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(90))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .setQueue("ClearFallbackKbsEnclaveJob")
                       .build(),
        enclave);
  }

  public static void clearAll() {
    if (KbsEnclaves.fallbacks().isEmpty()) {
      Log.i(TAG, "No fallbacks!");
      return;
    }

    JobManager jobManager = ApplicationDependencies.getJobManager();

    for (KbsEnclave enclave : KbsEnclaves.fallbacks()) {
      jobManager.add(new ClearFallbackKbsEnclaveJob(enclave));
    }
  }

  private ClearFallbackKbsEnclaveJob(@NonNull Parameters parameters, @NonNull KbsEnclave enclave) {
    super(parameters);
    this.enclave = enclave;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_ENCLAVE_NAME, enclave.getEnclaveName())
                                    .putString(KEY_SERVICE_ID, enclave.getServiceId())
                                    .putString(KEY_MR_ENCLAVE, enclave.getMrEnclave())
                                    .serialize();
  }

  @Override
  public void onRun() throws IOException, UnauthenticatedResponseException {
    Log.i(TAG, "Preparing to delete data from " + enclave.getEnclaveName());
    ApplicationDependencies.getKeyBackupService(enclave).newPinChangeSession().removePin();
    Log.i(TAG, "Successfully deleted the data from " + enclave.getEnclaveName());
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof NonSuccessfulResponseCodeException) {
      switch (((NonSuccessfulResponseCodeException) e).getCode()) {
        case 404:
          return getRunAttempt() < 3;
        case 508:
          return false;
      }
    }

    return true;
  }

  @Override
  public long getNextRunAttemptBackoff(int pastAttemptCount, @NonNull Exception e) {
    if (e instanceof NonSuccessfulResponseCodeException && ((NonSuccessfulResponseCodeException) e).getCode() == 404) {
      return TimeUnit.DAYS.toMillis(1);
    } else {
      return super.getNextRunAttemptBackoff(pastAttemptCount, e);
    }
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Job failed! It is likely that the old enclave is offline.");
  }

  public static class Factory implements Job.Factory<ClearFallbackKbsEnclaveJob> {
    @Override
    public @NonNull ClearFallbackKbsEnclaveJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      KbsEnclave enclave = new KbsEnclave(data.getString(KEY_ENCLAVE_NAME),
                                          data.getString(KEY_SERVICE_ID),
                                          data.getString(KEY_MR_ENCLAVE));

      return new ClearFallbackKbsEnclaveJob(parameters, enclave);
    }
  }
}

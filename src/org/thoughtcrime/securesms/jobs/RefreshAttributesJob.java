package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
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
    int     registrationId              = TextSecurePreferences.getLocalRegistrationId(context);
    boolean fetchesMessages             = TextSecurePreferences.isFcmDisabled(context);
    byte[]  unidentifiedAccessKey       = UnidentifiedAccessUtil.getSelfUnidentifiedAccessKey(context);
    boolean universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);
    String  pin                         = null;
    String  registrationLockToken       = null;

    if (TextSecurePreferences.isRegistrationLockEnabled(context)) {
      if (TextSecurePreferences.hasOldRegistrationLockPin(context)) {
        //noinspection deprecation Ok to read here as they have not migrated
        pin = TextSecurePreferences.getDeprecatedRegistrationLockPin(context);
      } else {
        registrationLockToken = TextSecurePreferences.getRegistrationLockToken(context);
      }
    }

    SignalServiceAccountManager signalAccountManager = ApplicationDependencies.getSignalServiceAccountManager();
    signalAccountManager.setAccountAttributes(null, registrationId, fetchesMessages,
                                              pin, registrationLockToken,
                                              unidentifiedAccessKey, universalUnidentifiedAccess);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to update account attributes!");
  }

  public static class Factory implements Job.Factory<RefreshAttributesJob> {
    @Override
    public @NonNull RefreshAttributesJob create(@NonNull Parameters parameters, @NonNull org.thoughtcrime.securesms.jobmanager.Data data) {
      return new RefreshAttributesJob(parameters);
    }
  }
}

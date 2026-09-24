package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.network.exceptions.NonSuccessfulResponseCodeException;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint;
import org.thoughtcrime.securesms.keyvalue.CertificateType;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.net.SignalNetwork;
import org.thoughtcrime.securesms.util.ExceptionHelper;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.NetworkResultUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public final class RotateCertificateJob extends BaseJob {

  public static final String KEY = "RotateCertificateJob";

  private static final String TAG = Log.tag(RotateCertificateJob.class);

  public RotateCertificateJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("__ROTATE_SENDER_CERTIFICATE__")
                           .setMaxInstancesForFactory(1)
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build());
  }

  private RotateCertificateJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException {
    if (!SignalStore.account().isRegistered()) {
      Log.w(TAG, "Not yet registered. Ignoring.");
      return;
    }

    if (TextSecurePreferences.isUnauthorizedReceived(context)) {
      Log.i(TAG, "No longer authorized. Ignoring.");
      return;
    }

    synchronized (RotateCertificateJob.class) {
      Collection<CertificateType> certificateTypes = SignalStore.phoneNumberPrivacy()
                                                                .getAllCertificateTypes();
      Collection<CertificateType> requiredTypes    = SignalStore.phoneNumberPrivacy()
                                                                .getRequiredCertificateTypes();

      Log.i(TAG, "Rotating these certificates " + certificateTypes);

      for (CertificateType certificateType: certificateTypes) {
        byte[] certificate;

        try {
          switch (certificateType) {
            case ACI_AND_E164: certificate = NetworkResultUtil.toBasicLegacy(SignalNetwork.certificateApi().getSenderCertificate()); break;
            case ACI_ONLY    : certificate = NetworkResultUtil.toBasicLegacy(SignalNetwork.certificateApi().getSenderCertificateForPhoneNumberPrivacy()); break;
            default          : throw new AssertionError();
          }
        } catch (NonSuccessfulResponseCodeException e) {
          if (requiredTypes.contains(certificateType)) {
            throw e;
          }
          Log.w(TAG, String.format("The server rejected the request for the non-required %s certificate. Skipping it.", certificateType), e);
          continue;
        }

        Log.i(TAG, String.format("Successfully got %s certificate", certificateType));
        SignalStore.certificate()
                   .setUnidentifiedAccessCertificate(certificateType, certificate);
      }
    }

    markRotated();
  }

  @WorkerThread
  public static void markRotated() {
    SignalStore.certificate().setLastRotationTime(System.currentTimeMillis());
    SealedSenderConstraint.refresh();
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return ExceptionHelper.isRetryableIOException(e);
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to rotate sender certificate!");
  }

  public static final class Factory implements Job.Factory<RotateCertificateJob> {
    @Override
    public @NonNull RotateCertificateJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new RotateCertificateJob(parameters);
    }
  }
}

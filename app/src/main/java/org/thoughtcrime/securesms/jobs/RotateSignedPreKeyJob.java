package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Forces the creation + upload of new signed prekeys for both the ACI and PNI identities.
 */
public class RotateSignedPreKeyJob extends BaseJob {

  public static final String KEY = "RotateSignedPreKeyJob";

  private static final String TAG = Log.tag(RotateSignedPreKeyJob.class);

  public RotateSignedPreKeyJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("RotateSignedPreKeyJob")
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxInstancesForFactory(1)
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .setLifespan(TimeUnit.DAYS.toMillis(2))
                           .build());
  }

  private RotateSignedPreKeyJob(@NonNull Job.Parameters parameters) {
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
  public void onRun() throws Exception {
    if (!SignalStore.account().isRegistered() || SignalStore.account().getAci() == null || SignalStore.account().getPni() == null) {
      Log.w(TAG, "Not registered. Skipping.");
      return;
    }

    Log.i(TAG, "Rotating signed prekey...");

    ACI aci = SignalStore.account().getAci();
    PNI pni = SignalStore.account().getPni();

    if (aci == null) {
      Log.w(TAG, "ACI is unset!");
      return;
    }

    if (pni == null) {
      Log.w(TAG, "PNI is unset!");
      return;
    }

    rotate(ServiceIdType.ACI, ApplicationDependencies.getProtocolStore().aci(), SignalStore.account().aciPreKeys());
    rotate(ServiceIdType.PNI, ApplicationDependencies.getProtocolStore().pni(), SignalStore.account().pniPreKeys());
  }

  private void rotate(@NonNull ServiceIdType serviceIdType, @NonNull SignalProtocolStore protocolStore, @NonNull PreKeyMetadataStore metadataStore)
      throws IOException
  {
    SignalServiceAccountManager accountManager     = ApplicationDependencies.getSignalServiceAccountManager();
    SignedPreKeyRecord          signedPreKeyRecord = PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore, false);

    accountManager.setSignedPreKey(serviceIdType, signedPreKeyRecord);

    metadataStore.setActiveSignedPreKeyId(signedPreKeyRecord.getId());
    metadataStore.setSignedPreKeyRegistered(true);
    metadataStore.setSignedPreKeyFailureCount(0);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    PreKeyMetadataStore aciStore = SignalStore.account().aciPreKeys();
    PreKeyMetadataStore pniStore = SignalStore.account().pniPreKeys();

    aciStore.setSignedPreKeyFailureCount(aciStore.getSignedPreKeyFailureCount() + 1);
    pniStore.setSignedPreKeyFailureCount(pniStore.getSignedPreKeyFailureCount() + 1);
  }

  public static final class Factory implements Job.Factory<RotateSignedPreKeyJob> {
    @Override
    public @NonNull RotateSignedPreKeyJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RotateSignedPreKeyJob(parameters);
    }
  }
}

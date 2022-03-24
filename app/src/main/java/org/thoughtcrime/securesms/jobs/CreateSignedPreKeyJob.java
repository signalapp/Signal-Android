package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Creates and uploads a new signed prekey for an identity if one hasn't been uploaded yet.
 */
public class CreateSignedPreKeyJob extends BaseJob {

  public static final String KEY = "CreateSignedPreKeyJob";

  private static final String TAG = Log.tag(CreateSignedPreKeyJob.class);

  private CreateSignedPreKeyJob() {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxInstancesForFactory(1)
                           .setQueue("CreateSignedPreKeyJob")
                           .setLifespan(TimeUnit.DAYS.toMillis(30))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build());
  }

  private CreateSignedPreKeyJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  /**
   * Enqueues an instance of this job if we not yet created + uploaded signed prekeys for one of our identities.
   */
  public static void enqueueIfNeeded() {
    if (!SignalStore.account().aciPreKeys().isSignedPreKeyRegistered() || !SignalStore.account().pniPreKeys().isSignedPreKeyRegistered()) {
      Log.i(TAG, "Some signed prekeys aren't registered yet. Enqueuing a job.");
      ApplicationDependencies.getJobManager().add(new CreateSignedPreKeyJob());
    }
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
    if (!SignalStore.account().isRegistered() || SignalStore.account().getAci() == null || SignalStore.account().getPni() == null) {
      Log.w(TAG, "Not yet registered...");
      return;
    }

    createPreKeys(ServiceIdType.ACI, SignalStore.account().getAci(), ApplicationDependencies.getProtocolStore().aci(), SignalStore.account().aciPreKeys());
    createPreKeys(ServiceIdType.PNI, SignalStore.account().getPni(), ApplicationDependencies.getProtocolStore().pni(), SignalStore.account().pniPreKeys());
  }

  private void createPreKeys(@NonNull ServiceIdType serviceIdType, @Nullable ServiceId serviceId, @NonNull SignalProtocolStore protocolStore, @NonNull PreKeyMetadataStore metadataStore)
      throws IOException
  {
    if (serviceId == null) {
      warn(TAG, "AccountId not set!");
      return;
    }

    if (metadataStore.isSignedPreKeyRegistered()) {
      warn(TAG, "Signed prekey for " + serviceIdType + " already registered...");
      return;
    }

    SignalServiceAccountManager accountManager     = ApplicationDependencies.getSignalServiceAccountManager();
    SignedPreKeyRecord          signedPreKeyRecord = PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore, true);

    accountManager.setSignedPreKey(serviceIdType, signedPreKeyRecord);
    metadataStore.setSignedPreKeyRegistered(true);
  }

  @Override
  public void onFailure() {}

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  public static final class Factory implements Job.Factory<CreateSignedPreKeyJob> {
    @Override
    public @NonNull CreateSignedPreKeyJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new CreateSignedPreKeyJob(parameters);
    }
  }
}

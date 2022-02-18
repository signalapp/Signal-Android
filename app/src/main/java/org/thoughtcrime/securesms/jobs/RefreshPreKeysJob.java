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
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ensures that our prekeys are up to date for both our ACI and PNI identities.
 * Specifically, if we have less than {@link #PREKEY_MINIMUM} one-time prekeys, we will generate and upload
 * a new batch of one-time prekeys, as well as a new signed prekey.
 */
public class RefreshPreKeysJob extends BaseJob {

  public static final String KEY = "RefreshPreKeysJob";

  private static final String TAG = Log.tag(RefreshPreKeysJob.class);

  private static final int PREKEY_MINIMUM = 10;

  private static final long REFRESH_INTERVAL = TimeUnit.DAYS.toMillis(3);

  public RefreshPreKeysJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("RefreshPreKeysJob")
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxInstancesForFactory(1)
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .setLifespan(TimeUnit.DAYS.toMillis(30))
                           .build());
  }

  public static void scheduleIfNecessary() {
    long timeSinceLastRefresh = System.currentTimeMillis() - SignalStore.misc().getLastPrekeyRefreshTime();

    if (timeSinceLastRefresh > REFRESH_INTERVAL) {
      Log.i(TAG, "Scheduling a prekey refresh. Time since last schedule: " + timeSinceLastRefresh + " ms");
      ApplicationDependencies.getJobManager().add(new RefreshPreKeysJob());
    }
  }

  private RefreshPreKeysJob(@NonNull Job.Parameters parameters) {
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
    if (!SignalStore.account().isRegistered() || SignalStore.account().getAci() == null || SignalStore.account().getPni() == null) {
      Log.w(TAG, "Not registered. Skipping.");
      return;
    }

    SignalProtocolStore aciProtocolStore = ApplicationDependencies.getProtocolStore().aci();
    PreKeyMetadataStore aciPreKeyStore   = SignalStore.account().aciPreKeys();
    
    SignalProtocolStore pniProtocolStore = ApplicationDependencies.getProtocolStore().pni();
    PreKeyMetadataStore pniPreKeyStore   = SignalStore.account().pniPreKeys();

    if (refreshKeys(ServiceIdType.ACI, aciProtocolStore, aciPreKeyStore)) {
      PreKeyUtil.cleanSignedPreKeys(aciProtocolStore, aciPreKeyStore);
    }
    
    if (refreshKeys(ServiceIdType.PNI, pniProtocolStore, pniPreKeyStore)) {
      PreKeyUtil.cleanSignedPreKeys(pniProtocolStore, pniPreKeyStore);
    }

    SignalStore.misc().setLastPrekeyRefreshTime(System.currentTimeMillis());
    Log.i(TAG, "Successfully refreshed prekeys.");
  }

  /**
   * @return True if we need to clean prekeys, otherwise false.
   */
  private boolean refreshKeys(@NonNull ServiceIdType serviceIdType, @NonNull SignalProtocolStore protocolStore, @NonNull PreKeyMetadataStore metadataStore) throws IOException {
    String logPrefix = "[" + serviceIdType + "] ";

    SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();

    int availableKeys = accountManager.getPreKeysCount(serviceIdType);
    log(TAG, logPrefix + "Available keys: " + availableKeys);

    if (availableKeys >= PREKEY_MINIMUM && metadataStore.isSignedPreKeyRegistered()) {
      log(TAG, logPrefix + "Available keys sufficient.");
      return false;
    }

    List<PreKeyRecord> preKeyRecords      = PreKeyUtil.generateAndStoreOneTimePreKeys(protocolStore, metadataStore);
    SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore, false);
    IdentityKeyPair    identityKey        = protocolStore.getIdentityKeyPair();

    log(TAG, logPrefix + "Registering new prekeys...");

    accountManager.setPreKeys(serviceIdType, identityKey.getPublicKey(), signedPreKeyRecord, preKeyRecords);

    metadataStore.setActiveSignedPreKeyId(signedPreKeyRecord.getId());
    metadataStore.setSignedPreKeyRegistered(true);

    log(TAG, logPrefix + "Need to clean prekeys.");
    return true;
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof NonSuccessfulResponseCodeException) return false;
    if (exception instanceof PushNetworkException)               return true;

    return false;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<RefreshPreKeysJob> {
    @Override
    public @NonNull RefreshPreKeysJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RefreshPreKeysJob(parameters);
    }
  }
}

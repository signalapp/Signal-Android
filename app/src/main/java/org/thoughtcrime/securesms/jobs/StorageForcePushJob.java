package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.contacts.sync.StorageSyncHelper;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.StorageKeyDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Forces remote storage to match our local state. This should only be done after a key change or
 * when we detect that the remote data is badly-encrypted.
 */
public class StorageForcePushJob extends BaseJob {

  public static final String KEY = "StorageForcePushJob";

  private static final String TAG = Log.tag(StorageForcePushJob.class);

  public StorageForcePushJob() {
    this(new Parameters.Builder().addConstraint(NetworkConstraint.KEY)
                                     .setQueue(StorageSyncJob.QUEUE_KEY)
                                     .setMaxInstances(1)
                                     .setLifespan(TimeUnit.DAYS.toMillis(1))
                                     .build());
  }

  private StorageForcePushJob(@NonNull Parameters parameters) {
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
  protected void onRun() throws IOException, RetryLaterException {
    if (!FeatureFlags.storageService()) throw new AssertionError();

    MasterKey kbsMasterKey = SignalStore.kbsValues().getPinBackedMasterKey();

    if (kbsMasterKey == null) {
      Log.w(TAG, "No KBS master key is set! Must abort.");
      return;
    }

    byte[]                      storageServiceKey  = kbsMasterKey.deriveStorageServiceKey();
    SignalServiceAccountManager accountManager     = ApplicationDependencies.getSignalServiceAccountManager();
    RecipientDatabase           recipientDatabase  = DatabaseFactory.getRecipientDatabase(context);
    StorageKeyDatabase          storageKeyDatabase = DatabaseFactory.getStorageKeyDatabase(context);

    long                     currentVersion = accountManager.getStorageManifestVersion();
    Map<RecipientId, byte[]> oldContactKeys = recipientDatabase.getAllStorageSyncKeysMap();
    List<byte[]>             oldUnknownKeys = storageKeyDatabase.getAllKeys();

    long                      newVersion     = currentVersion + 1;
    Map<RecipientId, byte[]>  newContactKeys = generateNewKeys(oldContactKeys);
    List<byte[]>              keysToDelete   = Util.concatenatedList(new ArrayList<>(oldContactKeys.values()), oldUnknownKeys);
    List<SignalStorageRecord> inserts        = Stream.of(oldContactKeys.keySet())
                                                     .map(recipientDatabase::getRecipientSettings)
                                                     .withoutNulls()
                                                     .map(StorageSyncHelper::localToRemoteContact)
                                                     .map(r -> SignalStorageRecord.forContact(r.getKey(), r))
                                                     .toList();

    SignalStorageManifest manifest = new SignalStorageManifest(newVersion, new ArrayList<>(newContactKeys.values()));

    try {
      accountManager.writeStorageRecords(storageServiceKey, manifest, inserts, keysToDelete);
    } catch (InvalidKeyException e) {
      Log.w(TAG, "Hit an invalid key exception, which likely indicates a conflict.");
      throw new RetryLaterException();
    }

    TextSecurePreferences.setStorageManifestVersion(context, newVersion);
    recipientDatabase.applyStorageSyncKeyUpdates(newContactKeys);
    storageKeyDatabase.deleteAll();
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException || e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  private static @NonNull Map<RecipientId, byte[]> generateNewKeys(@NonNull Map<RecipientId, byte[]> oldKeys) {
    Map<RecipientId, byte[]> out = new HashMap<>();

    for (Map.Entry<RecipientId, byte[]> entry : oldKeys.entrySet()) {
      out.put(entry.getKey(), StorageSyncHelper.generateKey());
    }

    return out;
  }

  public static final class Factory implements Job.Factory<StorageForcePushJob> {

    @Override
    public @NonNull
    StorageForcePushJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new StorageForcePushJob(parameters);
    }
  }
}

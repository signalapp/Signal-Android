package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.UnknownStorageIdTable;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.storage.StorageSyncModels;
import org.thoughtcrime.securesms.storage.StorageSyncValidations;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Forces remote storage to match our local state. This should only be done when we detect that the
 * remote data is badly-encrypted (which should only happen after re-registering without a PIN).
 */
public class StorageForcePushJob extends BaseJob {

  public static final String KEY = "StorageForcePushJob";

  private static final String TAG = Log.tag(StorageForcePushJob.class);

  public StorageForcePushJob() {
    this(new Parameters.Builder().addConstraint(NetworkConstraint.KEY)
                                 .setQueue(StorageSyncJob.QUEUE_KEY)
                                 .setMaxInstancesForFactory(1)
                                 .setLifespan(TimeUnit.DAYS.toMillis(1))
                                 .build());
  }

  private StorageForcePushJob(@NonNull Parameters parameters) {
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
  protected void onRun() throws IOException, RetryLaterException {
    if (SignalStore.account().isLinkedDevice()) {
      Log.i(TAG, "Only the primary device can force push");
      return;
    }

    if (!SignalStore.account().isRegistered() || SignalStore.account().getE164() == null) {
      Log.w(TAG, "User not registered. Skipping.");
      return;
    }

    if (Recipient.self().getStorageId() == null) {
      Log.w(TAG, "No storage ID set for self! Skipping.");
      return;
    }

    StorageKey                  storageServiceKey = SignalStore.storageService().getOrCreateStorageKey();
    SignalServiceAccountManager accountManager    = AppDependencies.getSignalServiceAccountManager();
    RecipientTable              recipientTable    = SignalDatabase.recipients();
    UnknownStorageIdTable       storageIdTable    = SignalDatabase.unknownStorageIds();

    long                        currentVersion       = accountManager.getStorageManifestVersion();
    Map<RecipientId, StorageId> oldContactStorageIds = recipientTable.getContactStorageSyncIdsMap();

    long                        newVersion           = currentVersion + 1;
    Map<RecipientId, StorageId> newContactStorageIds = generateContactStorageIds(oldContactStorageIds);
    List<SignalStorageRecord>   inserts              = Stream.of(oldContactStorageIds.keySet())
                                                             .map(recipientTable::getRecordForSync)
                                                             .withoutNulls()
                                                             .map(s -> StorageSyncModels.localToRemoteRecord(s, Objects.requireNonNull(newContactStorageIds.get(s.getId())).getRaw()))
                                                             .toList();

    SignalStorageRecord accountRecord    = StorageSyncHelper.buildAccountRecord(context, Recipient.self().fresh());
    List<StorageId>     allNewStorageIds = new ArrayList<>(newContactStorageIds.values());

    inserts.add(accountRecord);
    allNewStorageIds.add(accountRecord.getId());

    SignalStorageManifest manifest = new SignalStorageManifest(newVersion, SignalStore.account().getDeviceId(), allNewStorageIds);
    StorageSyncValidations.validateForcePush(manifest, inserts, Recipient.self().fresh());

    try {
      if (newVersion > 1) {
        Log.i(TAG, String.format(Locale.ENGLISH, "Force-pushing data. Inserting %d IDs.", inserts.size()));
        if (accountManager.resetStorageRecords(storageServiceKey, manifest, inserts).isPresent()) {
          Log.w(TAG, "Hit a conflict. Trying again.");
          throw new RetryLaterException();
        }
      } else {
        Log.i(TAG, String.format(Locale.ENGLISH, "First version, normal push. Inserting %d IDs.", inserts.size()));
        if (accountManager.writeStorageRecords(storageServiceKey, manifest, inserts, Collections.emptyList()).isPresent()) {
          Log.w(TAG, "Hit a conflict. Trying again.");
          throw new RetryLaterException();
        }
      }
    } catch (InvalidKeyException e) {
      Log.w(TAG, "Hit an invalid key exception, which likely indicates a conflict.");
      throw new RetryLaterException(e);
    }

    Log.i(TAG, "Force push succeeded. Updating local manifest version to: " + newVersion);
    SignalStore.storageService().setManifest(manifest);
    recipientTable.applyStorageIdUpdates(newContactStorageIds);
    recipientTable.applyStorageIdUpdates(Collections.singletonMap(Recipient.self().getId(), accountRecord.getId()));
    storageIdTable.deleteAll();
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException || e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  private static @NonNull Map<RecipientId, StorageId> generateContactStorageIds(@NonNull Map<RecipientId, StorageId> oldKeys) {
    Map<RecipientId, StorageId> out = new HashMap<>();

    for (Map.Entry<RecipientId, StorageId> entry : oldKeys.entrySet()) {
      out.put(entry.getKey(), entry.getValue().withNewBytes(StorageSyncHelper.generateKey()));
    }

    return out;
  }

  public static final class Factory implements Job.Factory<StorageForcePushJob> {
    @Override
    public @NonNull StorageForcePushJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new StorageForcePushJob(parameters);
    }
  }
}

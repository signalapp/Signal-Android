package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.contacts.sync.StorageSyncHelper;
import org.thoughtcrime.securesms.contacts.sync.StorageSyncHelper.KeyDifferenceResult;
import org.thoughtcrime.securesms.contacts.sync.StorageSyncHelper.LocalWriteResult;
import org.thoughtcrime.securesms.contacts.sync.StorageSyncHelper.MergeResult;
import org.thoughtcrime.securesms.contacts.sync.StorageSyncHelper.WriteOperationResult;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
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
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Does a full sync of our local storage state with the remote storage state. Will write any pending
 * local changes and resolve any conflicts with remote storage.
 *
 * This should be performed whenever a change is made locally, or whenever we want to retrieve
 * changes that have been made remotely.
 */
public class StorageSyncJob extends BaseJob {

  public static final String KEY       = "StorageSyncJob";
  public static final String QUEUE_KEY = "StorageSyncingJobs";

  private static final String TAG = Log.tag(StorageSyncJob.class);

  public StorageSyncJob() {
    this(new Job.Parameters.Builder().addConstraint(NetworkConstraint.KEY)
                                     .setQueue(QUEUE_KEY)
                                     .setMaxInstances(1)
                                     .setLifespan(TimeUnit.DAYS.toMillis(1))
                                     .build());
  }

  private StorageSyncJob(@NonNull Parameters parameters) {
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

    try {
      boolean needsMultiDeviceSync = performSync();

      if (TextSecurePreferences.isMultiDevice(context) && needsMultiDeviceSync) {
        ApplicationDependencies.getJobManager().add(new MultiDeviceStorageSyncRequestJob());
      }
    } catch (InvalidKeyException e) {
      Log.w(TAG, "Failed to decrypt remote storage! Force-pushing and syncing the storage key to linked devices.", e);

      ApplicationDependencies.getJobManager().startChain(new MultiDeviceKeysUpdateJob())
                                             .then(new StorageForcePushJob())
                                             .then(new MultiDeviceStorageSyncRequestJob())
                                             .enqueue();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException || e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  private boolean performSync() throws IOException, RetryLaterException, InvalidKeyException {
    SignalServiceAccountManager accountManager     = ApplicationDependencies.getSignalServiceAccountManager();
    RecipientDatabase           recipientDatabase  = DatabaseFactory.getRecipientDatabase(context);
    StorageKeyDatabase          storageKeyDatabase = DatabaseFactory.getStorageKeyDatabase(context);
    MasterKey                   kbsMasterKey       = SignalStore.kbsValues().getPinBackedMasterKey();

    if (kbsMasterKey == null) {
      Log.w(TAG, "No KBS master key is set! Must abort.");
      return false;
    }

    byte[]                storageServiceKey    = kbsMasterKey.deriveStorageServiceKey();
    boolean               needsMultiDeviceSync = false;
    long                  localManifestVersion = TextSecurePreferences.getStorageManifestVersion(context);
    SignalStorageManifest remoteManifest       = accountManager.getStorageManifest(storageServiceKey).or(new SignalStorageManifest(0, Collections.emptyList()));

    if (remoteManifest.getVersion() > localManifestVersion) {
      Log.i(TAG, "Newer manifest version found! Our version: " + localManifestVersion + ",  their version: " + remoteManifest.getVersion());

      List<byte[]>        allLocalStorageKeys = getAllLocalStorageKeys(context);
      KeyDifferenceResult keyDifference       = StorageSyncHelper.findKeyDifference(remoteManifest.getStorageKeys(), allLocalStorageKeys);

      if (!keyDifference.isEmpty()) {
        List<SignalStorageRecord> localOnly            = buildLocalStorageRecords(context, keyDifference.getLocalOnlyKeys());
        List<SignalStorageRecord> remoteOnly           = accountManager.readStorageRecords(storageServiceKey, keyDifference.getRemoteOnlyKeys());
        MergeResult               mergeResult          = StorageSyncHelper.resolveConflict(remoteOnly, localOnly);
        WriteOperationResult      writeOperationResult = StorageSyncHelper.createWriteOperation(remoteManifest.getVersion(), allLocalStorageKeys, mergeResult);

        Optional<SignalStorageManifest> conflict = accountManager.writeStorageRecords(storageServiceKey, writeOperationResult.getManifest(), writeOperationResult.getInserts(), writeOperationResult.getDeletes());

        if (conflict.isPresent()) {
          Log.w(TAG, "Hit a conflict when trying to resolve the conflict! Retrying.");
          throw new RetryLaterException();
        }

        recipientDatabase.applyStorageSyncUpdates(mergeResult.getLocalContactInserts(), mergeResult.getLocalContactUpdates());
        storageKeyDatabase.applyStorageSyncUpdates(mergeResult.getLocalUnknownInserts(), mergeResult.getLocalUnknownDeletes());
        needsMultiDeviceSync = true;

        Log.i(TAG, "[Post-Conflict] Updating local manifest version to: " + writeOperationResult.getManifest().getVersion());
        TextSecurePreferences.setStorageManifestVersion(context, writeOperationResult.getManifest().getVersion());
      } else {
        Log.i(TAG, "Remote version was newer, but our local data matched.");
        Log.i(TAG, "[Post-Empty-Conflict] Updating local manifest version to: " + remoteManifest.getVersion());
        TextSecurePreferences.setStorageManifestVersion(context, remoteManifest.getVersion());
      }
    }

    localManifestVersion = TextSecurePreferences.getStorageManifestVersion(context);

    List<byte[]>               allLocalStorageKeys = recipientDatabase.getAllStorageSyncKeys();
    List<RecipientSettings>    pendingUpdates      = recipientDatabase.getPendingRecipientSyncUpdates();
    List<RecipientSettings>    pendingInsertions   = recipientDatabase.getPendingRecipientSyncInsertions();
    List<RecipientSettings>    pendingDeletions    = recipientDatabase.getPendingRecipientSyncDeletions();
    Optional<LocalWriteResult> localWriteResult    = StorageSyncHelper.buildStorageUpdatesForLocal(localManifestVersion,
                                                                                                   allLocalStorageKeys,
                                                                                                   pendingUpdates,
                                                                                                   pendingInsertions,
                                                                                                   pendingDeletions);

    if (localWriteResult.isPresent()) {
      WriteOperationResult            localWrite = localWriteResult.get().getWriteResult();
      Optional<SignalStorageManifest> conflict   = accountManager.writeStorageRecords(storageServiceKey, localWrite.getManifest(), localWrite.getInserts(), localWrite.getDeletes());

      if (conflict.isPresent()) {
        Log.w(TAG, "Hit a conflict when trying to upload our local writes! Retrying.");
        throw new RetryLaterException();
      }

      List<RecipientId> clearIds = new ArrayList<>(pendingUpdates.size() + pendingInsertions.size() + pendingDeletions.size());

      clearIds.addAll(Stream.of(pendingUpdates).map(RecipientSettings::getId).toList());
      clearIds.addAll(Stream.of(pendingInsertions).map(RecipientSettings::getId).toList());
      clearIds.addAll(Stream.of(pendingDeletions).map(RecipientSettings::getId).toList());

      recipientDatabase.clearDirtyState(clearIds);
      recipientDatabase.updateStorageKeys(localWriteResult.get().getStorageKeyUpdates());

      needsMultiDeviceSync = true;

      Log.i(TAG, "[Post Write] Updating local manifest version to: " + localWriteResult.get().getWriteResult().getManifest().getVersion());
      TextSecurePreferences.setStorageManifestVersion(context, localWriteResult.get().getWriteResult().getManifest().getVersion());
    } else {
      Log.i(TAG, "Nothing locally to write.");
    }

    return needsMultiDeviceSync;
  }

  public static @NonNull List<byte[]> getAllLocalStorageKeys(@NonNull Context context) {
    return Util.concatenatedList(DatabaseFactory.getRecipientDatabase(context).getAllStorageSyncKeys(),
                                 DatabaseFactory.getStorageKeyDatabase(context).getAllKeys());
  }

  public static @NonNull List<SignalStorageRecord> buildLocalStorageRecords(@NonNull Context context, @NonNull List<byte[]> keys) {
    RecipientDatabase  recipientDatabase  = DatabaseFactory.getRecipientDatabase(context);
    StorageKeyDatabase storageKeyDatabase = DatabaseFactory.getStorageKeyDatabase(context);

    List<SignalStorageRecord> records = new ArrayList<>(keys.size());

    for (byte[] key : keys) {
      SignalStorageRecord record = Optional.fromNullable(recipientDatabase.getByStorageSyncKey(key))
                                           .transform(recipient -> {
                                             SignalContactRecord contact = StorageSyncHelper.localToRemoteContact(recipient);
                                             return SignalStorageRecord.forContact(key, contact);
                                           })
                                           .or(() -> storageKeyDatabase.getByKey(key));
      records.add(record);
    }

    return records;
  }

  public static final class Factory implements Job.Factory<StorageSyncJob> {

    @Override
    public @NonNull StorageSyncJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new StorageSyncJob(parameters);
    }
  }
}

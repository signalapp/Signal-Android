package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.database.StorageKeyDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupsV1MigrationUtil;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.storage.GroupV2ExistenceChecker;
import org.thoughtcrime.securesms.storage.StaticGroupV2ExistenceChecker;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.KeyDifferenceResult;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.LocalWriteResult;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.MergeResult;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.WriteOperationResult;
import org.thoughtcrime.securesms.storage.StorageSyncModels;
import org.thoughtcrime.securesms.storage.StorageSyncValidations;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                                     .setMaxInstancesForFactory(2)
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
    if (!SignalStore.kbsValues().hasPin() && !SignalStore.kbsValues().hasOptedOut()) {
      Log.i(TAG, "Doesn't have a PIN. Skipping.");
      return;
    }

    if (!TextSecurePreferences.isPushRegistered(context)) {
      Log.i(TAG, "Not registered. Skipping.");
      return;
    }

    try {
      boolean needsMultiDeviceSync = performSync();

      if (TextSecurePreferences.isMultiDevice(context) && needsMultiDeviceSync) {
        ApplicationDependencies.getJobManager().add(new MultiDeviceStorageSyncRequestJob());
      }

      SignalStore.storageServiceValues().onSyncCompleted();
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
    StorageKey                  storageServiceKey  = SignalStore.storageServiceValues().getOrCreateStorageKey();

    boolean                         needsMultiDeviceSync  = false;
    boolean                         needsForcePush        = false;
    long                            localManifestVersion  = TextSecurePreferences.getStorageManifestVersion(context);
    Optional<SignalStorageManifest> remoteManifest        = accountManager.getStorageManifestIfDifferentVersion(storageServiceKey, localManifestVersion);
    long                            remoteManifestVersion = remoteManifest.transform(SignalStorageManifest::getVersion).or(localManifestVersion);

    Log.i(TAG, "Our version: " + localManifestVersion + ", their version: " + remoteManifestVersion);

    if (remoteManifest.isPresent() && remoteManifestVersion > localManifestVersion) {
      Log.i(TAG, "[Remote Newer] Newer manifest version found!");

      List<StorageId>     allLocalStorageKeys = getAllLocalStorageIds(context, Recipient.self().fresh());
      KeyDifferenceResult keyDifference       = StorageSyncHelper.findKeyDifference(remoteManifest.get().getStorageIds(), allLocalStorageKeys);

      if (keyDifference.hasTypeMismatches()) {
        Log.w(TAG, "Found type mismatches in the key sets! Scheduling a force push after this sync completes.");
        needsForcePush = true;
      }

      if (!keyDifference.isEmpty()) {
        Log.i(TAG, "[Remote Newer] There's a difference in keys. Local-only: " + keyDifference.getLocalOnlyKeys().size() + ", Remote-only: " + keyDifference.getRemoteOnlyKeys().size());

        List<SignalStorageRecord> localOnly            = buildLocalStorageRecords(context, keyDifference.getLocalOnlyKeys());
        List<SignalStorageRecord> remoteOnly           = accountManager.readStorageRecords(storageServiceKey, keyDifference.getRemoteOnlyKeys());
        GroupV2ExistenceChecker   gv2ExistenceChecker  = new StaticGroupV2ExistenceChecker(DatabaseFactory.getGroupDatabase(context).getAllGroupV2Ids());
        MergeResult               mergeResult          = StorageSyncHelper.resolveConflict(remoteOnly, localOnly, gv2ExistenceChecker);
        WriteOperationResult      writeOperationResult = StorageSyncHelper.createWriteOperation(remoteManifest.get().getVersion(), allLocalStorageKeys, mergeResult);

        if (remoteOnly.size() != keyDifference.getRemoteOnlyKeys().size()) {
          Log.w(TAG, "Could not find all remote-only records! Requested: " + keyDifference.getRemoteOnlyKeys().size() + ", Found: " + remoteOnly.size() + ". Scheduling a force push after this sync completes.");
          needsForcePush = true;
        }

        StorageSyncValidations.validate(writeOperationResult);

        Log.i(TAG, "[Remote Newer] MergeResult :: " + mergeResult);

        if (!writeOperationResult.isEmpty()) {
          Log.i(TAG, "[Remote Newer] WriteOperationResult :: " + writeOperationResult);
          Log.i(TAG, "[Remote Newer] We have something to write remotely.");

          if (writeOperationResult.getManifest().getStorageIds().size() != remoteManifest.get().getStorageIds().size() + writeOperationResult.getInserts().size() - writeOperationResult.getDeletes().size()) {
            Log.w(TAG, String.format(Locale.ENGLISH, "Bad storage key management! originalRemoteKeys: %d, newRemoteKeys: %d, insertedKeys: %d, deletedKeys: %d",
                                                     remoteManifest.get().getStorageIds().size(), writeOperationResult.getManifest().getStorageIds().size(), writeOperationResult.getInserts().size(), writeOperationResult.getDeletes().size()));
          }

          Optional<SignalStorageManifest> conflict = accountManager.writeStorageRecords(storageServiceKey, writeOperationResult.getManifest(), writeOperationResult.getInserts(), writeOperationResult.getDeletes());

          if (conflict.isPresent()) {
            Log.w(TAG, "[Remote Newer] Hit a conflict when trying to resolve the conflict! Retrying.");
            throw new RetryLaterException();
          }

          remoteManifestVersion = writeOperationResult.getManifest().getVersion();

          needsMultiDeviceSync = true;
        } else {
          Log.i(TAG, "[Remote Newer] After resolving the conflict, all changes are local. No remote writes needed.");
        }

        migrateToGv2IfNecessary(context, mergeResult.getLocalGroupV2Inserts());
        recipientDatabase.applyStorageSyncUpdates(mergeResult.getLocalContactInserts(), mergeResult.getLocalContactUpdates(), mergeResult.getLocalGroupV1Inserts(), mergeResult.getLocalGroupV1Updates(), mergeResult.getLocalGroupV2Inserts(), mergeResult.getLocalGroupV2Updates());
        storageKeyDatabase.applyStorageSyncUpdates(mergeResult.getLocalUnknownInserts(), mergeResult.getLocalUnknownDeletes());
        StorageSyncHelper.applyAccountStorageSyncUpdates(context, mergeResult.getLocalAccountUpdate());

        Log.i(TAG, "[Remote Newer] Updating local manifest version to: " + remoteManifestVersion);
        TextSecurePreferences.setStorageManifestVersion(context, remoteManifestVersion);
      } else {
        Log.i(TAG, "[Remote Newer] Remote version was newer, but our local data matched.");
        Log.i(TAG, "[Remote Newer] Updating local manifest version to: " + remoteManifest.get().getVersion());
        TextSecurePreferences.setStorageManifestVersion(context, remoteManifest.get().getVersion());
      }
    }

    localManifestVersion = TextSecurePreferences.getStorageManifestVersion(context);

    Recipient self = Recipient.self().fresh();

    List<StorageId>               allLocalStorageKeys  = getAllLocalStorageIds(context, self);
    List<RecipientSettings>       pendingUpdates       = recipientDatabase.getPendingRecipientSyncUpdates();
    List<RecipientSettings>       pendingInsertions    = recipientDatabase.getPendingRecipientSyncInsertions();
    List<RecipientSettings>       pendingDeletions     = recipientDatabase.getPendingRecipientSyncDeletions();
    Optional<SignalAccountRecord> pendingAccountInsert = StorageSyncHelper.getPendingAccountSyncInsert(context, self);
    Optional<SignalAccountRecord> pendingAccountUpdate = StorageSyncHelper.getPendingAccountSyncUpdate(context, self);
    Optional<LocalWriteResult>    localWriteResult     = StorageSyncHelper.buildStorageUpdatesForLocal(localManifestVersion,
                                                                                                       allLocalStorageKeys,
                                                                                                       pendingUpdates,
                                                                                                       pendingInsertions,
                                                                                                       pendingDeletions,
                                                                                                       pendingAccountUpdate,
                                                                                                       pendingAccountInsert);

    if (localWriteResult.isPresent()) {
      Log.i(TAG, String.format(Locale.ENGLISH, "[Local Changes] Local changes present. %d updates, %d inserts, %d deletes, account update: %b, account insert: %b.", pendingUpdates.size(), pendingInsertions.size(), pendingDeletions.size(), pendingAccountUpdate.isPresent(), pendingAccountInsert.isPresent()));

      WriteOperationResult localWrite = localWriteResult.get().getWriteResult();
      StorageSyncValidations.validate(localWrite);

      Log.i(TAG, "[Local Changes] WriteOperationResult :: " + localWrite);

      if (localWrite.isEmpty()) {
        throw new AssertionError("Decided there were local writes, but our write result was empty!");
      }

      Optional<SignalStorageManifest> conflict = accountManager.writeStorageRecords(storageServiceKey, localWrite.getManifest(), localWrite.getInserts(), localWrite.getDeletes());

      if (conflict.isPresent()) {
        Log.w(TAG, "[Local Changes] Hit a conflict when trying to upload our local writes! Retrying.");
        throw new RetryLaterException();
      }

      List<RecipientId> clearIds = new ArrayList<>(pendingUpdates.size() + pendingInsertions.size() + pendingDeletions.size() + 1);

      clearIds.addAll(Stream.of(pendingUpdates).map(RecipientSettings::getId).toList());
      clearIds.addAll(Stream.of(pendingInsertions).map(RecipientSettings::getId).toList());
      clearIds.addAll(Stream.of(pendingDeletions).map(RecipientSettings::getId).toList());
      clearIds.add(Recipient.self().getId());

      recipientDatabase.clearDirtyState(clearIds);
      recipientDatabase.updateStorageKeys(localWriteResult.get().getStorageKeyUpdates());

      needsMultiDeviceSync = true;

      Log.i(TAG, "[Local Changes] Updating local manifest version to: " + localWriteResult.get().getWriteResult().getManifest().getVersion());
      TextSecurePreferences.setStorageManifestVersion(context, localWriteResult.get().getWriteResult().getManifest().getVersion());
    } else {
      Log.i(TAG, "[Local Changes] No local changes.");
    }

    if (needsForcePush) {
      Log.w(TAG, "Scheduling a force push.");
      ApplicationDependencies.getJobManager().add(new StorageForcePushJob());
    }

    return needsMultiDeviceSync;
  }

  /**
   * Migrates any of the provided V2 IDs that map a local V1 ID. If a match is found, we remove the
   * record from the collection of V2 IDs.
   */
  private static void migrateToGv2IfNecessary(@NonNull Context context, @NonNull Collection<SignalGroupV2Record> inserts)
      throws IOException
  {
    Map<GroupId.V2, GroupId.V1>   idMap          = DatabaseFactory.getGroupDatabase(context).getAllExpectedV2Ids();
    Iterator<SignalGroupV2Record> recordIterator = inserts.iterator();

    while (recordIterator.hasNext()) {
      GroupId.V2 id = GroupId.v2(GroupUtil.requireMasterKey(recordIterator.next().getMasterKeyBytes()));

      if (idMap.containsKey(id)) {
        Log.i(TAG, "Discovered a new GV2 ID that is actually a migrated V1 group! Migrating now.");
        GroupsV1MigrationUtil.performLocalMigration(context, idMap.get(id));
        recordIterator.remove();
      }
    }
  }

  private static @NonNull List<StorageId> getAllLocalStorageIds(@NonNull Context context, @NonNull Recipient self) {
    return Util.concatenatedList(DatabaseFactory.getRecipientDatabase(context).getContactStorageSyncIds(),
                                 Collections.singletonList(StorageId.forAccount(self.getStorageServiceId())),
                                 DatabaseFactory.getStorageKeyDatabase(context).getAllKeys());
  }

  private static @NonNull List<SignalStorageRecord> buildLocalStorageRecords(@NonNull Context context, @NonNull List<StorageId> ids) {
    Recipient          self               = Recipient.self().fresh();
    RecipientDatabase  recipientDatabase  = DatabaseFactory.getRecipientDatabase(context);
    StorageKeyDatabase storageKeyDatabase = DatabaseFactory.getStorageKeyDatabase(context);

    List<SignalStorageRecord> records = new ArrayList<>(ids.size());

    for (StorageId id : ids) {
      switch (id.getType()) {
        case ManifestRecord.Identifier.Type.CONTACT_VALUE:
        case ManifestRecord.Identifier.Type.GROUPV1_VALUE:
        case ManifestRecord.Identifier.Type.GROUPV2_VALUE:
          RecipientSettings settings = recipientDatabase.getByStorageId(id.getRaw());
          if (settings != null) {
            if (settings.getGroupType() == RecipientDatabase.GroupType.SIGNAL_V2 && settings.getSyncExtras().getGroupMasterKey() == null) {
              Log.w(TAG, "Missing master key on gv2 recipient");
            } else {
              records.add(StorageSyncModels.localToRemoteRecord(settings));
            }
          } else {
            Log.w(TAG, "Missing local recipient model! Type: " + id.getType());
          }
          break;
        case ManifestRecord.Identifier.Type.ACCOUNT_VALUE:
          if (!Arrays.equals(self.getStorageServiceId(), id.getRaw())) {
            throw new AssertionError("Local storage ID doesn't match self!");
          }
          records.add(StorageSyncHelper.buildAccountRecord(context, self));
          break;
        default:
          SignalStorageRecord unknown = storageKeyDatabase.getById(id.getRaw());
          if (unknown != null) {
            records.add(unknown);
          } else {
            Log.w(TAG, "Missing local unknown model! Type: " + id.getType());
          }
          break;
      }
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

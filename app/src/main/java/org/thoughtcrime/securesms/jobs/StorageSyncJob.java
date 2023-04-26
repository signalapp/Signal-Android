package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.UnknownStorageIdTable;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.migrations.StorageServiceMigrationJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.storage.AccountRecordProcessor;
import org.thoughtcrime.securesms.storage.ContactRecordProcessor;
import org.thoughtcrime.securesms.storage.GroupV1RecordProcessor;
import org.thoughtcrime.securesms.storage.GroupV2RecordProcessor;
import org.thoughtcrime.securesms.storage.StorageRecordUpdate;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.IdDifferenceResult;
import org.thoughtcrime.securesms.storage.StorageSyncHelper.WriteOperationResult;
import org.thoughtcrime.securesms.storage.StorageSyncModels;
import org.thoughtcrime.securesms.storage.StorageSyncValidations;
import org.thoughtcrime.securesms.storage.StoryDistributionListRecordProcessor;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.signal.core.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.storage.SignalAccountRecord;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record;
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record;
import org.whispersystems.signalservice.api.storage.SignalRecord;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Does a full sync of our local storage state with the remote storage state. Will write any pending
 * local changes and resolve any conflicts with remote storage.
 *
 * This should be performed whenever a change is made locally, or whenever we want to retrieve
 * changes that have been made remotely.
 *
 * == Important Implementation Notes ==
 *
 * - We want to use a transaction to guarantee atomicity of our changes and to prevent other threads
 *   from writing while the sync is happening. But that means we also need to be very careful with
 *   what happens inside the transaction. Namely, we *cannot* perform network activity inside the
 *   transaction.
 *
 * - This puts us in a funny situation where we have to get remote data, begin a transaction to
 *   resolve the sync, and then end the transaction (and therefore commit our changes) *before*
 *   we write the data remotely. Normally, this would be dangerous, as our view of the data could
 *   fall out of sync if the network request fails. However, because of how the sync works, as long
 *   as we don't update our local manifest version until after the network request succeeds, it
 *   should all sort itself out in the retry. Because if our network request failed, then we
 *   wouldn't have written all of the new IDs, and we'll still see a bunch of remote-only IDs that
 *   we'll merge with local data to generate another equally-valid set of remote changes.
 *
 *
 * == Technical Overview ==
 *
 * The Storage Service is, at it's core, a dumb key-value store. It stores various types of records,
 * each of which is given an ID. It also stores a manifest, which has the complete list of all IDs.
 * The manifest has a monotonically-increasing version associated with it. Whenever a change is
 * made to the stored data, you upload a new manifest with the updated ID set.
 *
 * An ID corresponds to an unchanging snapshot of a record. That is, if the underlying record is
 * updated, that update is performed by deleting the old ID/record and inserting a new one. This
 * makes it easy to determine what's changed in a given version of a manifest -- simply diff the
 * list of IDs in the manifest with the list of IDs we have locally.
 *
 * So, at it's core, syncing isn't all that complicated.
 * - If we see the remote manifest version is newer than ours, then we grab the manifest and compute
 *   the diff in IDs.
 * - Then, we fetch the actual records that correspond to the remote-only IDs.
 * - Afterwards, we take those records and merge them into our local data store.
 * - Next, we assume that our local state represents the most up-to-date information, and so we
 *   calculate and write a change set that represents the diff between our state and the remote
 *   state.
 * - Finally, handle any possible records in our "unknown ID store" that might have become known to us.
 *
 * Of course, you'll notice that there's a lot of code to support that goal. That's mostly because
 * converting local data into a format that can be compared with, merged, and eventually written
 * back to both local and remote data stores is tiresome. There's also lots of general bookkeeping,
 * error handling, cleanup scenarios, logging, etc.
 *
 * == Syncing a new field on an existing record ==
 *
 * - Add the field the the respective proto
 * - Update the respective model (i.e. {@link SignalContactRecord})
 *     - Add getters
 *     - Update the builder
 *     - Update {@link SignalRecord#describeDiff(SignalRecord)}.
 * - Update the respective record processor (i.e {@link ContactRecordProcessor}). You need to make
 *   sure that you're:
 *     - Merging the attributes, likely preferring remote
 *     - Adding to doParamsMatch()
 *     - Adding the parameter to the builder chain when creating a merged model
 * - Update builder usage in StorageSyncModels
 * - Handle the new data when writing to the local storage
 *   (i.e. {@link RecipientTable#applyStorageSyncContactUpdate(StorageRecordUpdate)}).
 * - Make sure that whenever you change the field in the UI, we rotate the storageId for that row
 *   and call {@link StorageSyncHelper#scheduleSyncForDataChange()}.
 * - If you're syncing a field that was otherwise already present in the UI, you'll probably want
 *   to enqueue a {@link StorageServiceMigrationJob} as an app migration to make sure it gets
 *   synced.
 */
public class StorageSyncJob extends BaseJob {

  public static final String KEY       = "StorageSyncJobV2";
  public static final String QUEUE_KEY = "StorageSyncingJobs";

  private static final String TAG = Log.tag(StorageSyncJob.class);

  public StorageSyncJob() {
    this(new Parameters.Builder().addConstraint(NetworkConstraint.KEY)
                                 .setQueue(QUEUE_KEY)
                                 .setMaxInstancesForFactory(2)
                                 .setLifespan(TimeUnit.DAYS.toMillis(1))
                                 .setMaxAttempts(3)
                                 .build());
  }

  private StorageSyncJob(@NonNull Parameters parameters) {
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
  protected void onRun() throws IOException, RetryLaterException, UntrustedIdentityException {
    if (!SignalStore.kbsValues().hasPin() && !SignalStore.kbsValues().hasOptedOut()) {
      Log.i(TAG, "Doesn't have a PIN. Skipping.");
      return;
    }

    if (!SignalStore.account().isRegistered()) {
      Log.i(TAG, "Not registered. Skipping.");
      return;
    }

    if (!Recipient.self().hasE164() || !Recipient.self().hasServiceId()) {
      Log.w(TAG, "Missing E164 or ACI!");
      return;
    }

    if (SignalStore.internalValues().storageServiceDisabled()) {
      Log.w(TAG, "Storage service has been manually disabled. Skipping.");
      return;
    }

    try {
      boolean needsMultiDeviceSync = performSync();

      if (TextSecurePreferences.isMultiDevice(context) && needsMultiDeviceSync) {
        ApplicationDependencies.getJobManager().add(new MultiDeviceStorageSyncRequestJob());
      }

      SignalStore.storageService().onSyncCompleted();
    } catch (InvalidKeyException e) {
      if (SignalStore.account().isPrimaryDevice()) {
        Log.w(TAG, "Failed to decrypt remote storage! Force-pushing and syncing the storage key to linked devices.", e);

        ApplicationDependencies.getJobManager().startChain(new MultiDeviceKeysUpdateJob())
                               .then(new StorageForcePushJob())
                               .then(new MultiDeviceStorageSyncRequestJob())
                               .enqueue();
      } else {
        Log.w(TAG, "Failed to decrypt remote storage! Requesting new keys from primary.", e);
        SignalStore.storageService().clearStorageKeyFromPrimary();
        ApplicationDependencies.getSignalServiceMessageSender().sendSyncMessage(SignalServiceSyncMessage.forRequest(RequestMessage.forType(SignalServiceProtos.SyncMessage.Request.Type.KEYS)), UnidentifiedAccessUtil.getAccessForSync(context));
      }
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
    final Stopwatch                   stopwatch         = new Stopwatch("StorageSync");
    final SQLiteDatabase              db                = SignalDatabase.getRawDatabase();
    final SignalServiceAccountManager accountManager    = ApplicationDependencies.getSignalServiceAccountManager();
    final UnknownStorageIdTable       storageIdDatabase = SignalDatabase.unknownStorageIds();
    final StorageKey                  storageServiceKey = SignalStore.storageService().getOrCreateStorageKey();

    final SignalStorageManifest localManifest  = SignalStore.storageService().getManifest();
    final SignalStorageManifest remoteManifest = accountManager.getStorageManifestIfDifferentVersion(storageServiceKey, localManifest.getVersion()).orElse(localManifest);

    stopwatch.split("remote-manifest");

    Recipient self                 = freshSelf();
    boolean   needsMultiDeviceSync = false;
    boolean   needsForcePush       = false;

    if (self.getStorageServiceId() == null) {
      Log.w(TAG, "No storageId for self. Generating.");
      SignalDatabase.recipients().updateStorageId(self.getId(), StorageSyncHelper.generateKey());
      self = freshSelf();
    }

    Log.i(TAG, "Our version: " + localManifest.getVersionString() + ", their version: " + remoteManifest.getVersionString());

    if (remoteManifest.getVersion() > localManifest.getVersion()) {
      Log.i(TAG, "[Remote Sync] Newer manifest version found!");

      List<StorageId>    localStorageIdsBeforeMerge = getAllLocalStorageIds(self);
      IdDifferenceResult idDifference               = StorageSyncHelper.findIdDifference(remoteManifest.getStorageIds(), localStorageIdsBeforeMerge);

      if (idDifference.hasTypeMismatches() && SignalStore.account().isPrimaryDevice()) {
        Log.w(TAG, "[Remote Sync] Found type mismatches in the ID sets! Scheduling a force push after this sync completes.");
        needsForcePush = true;
      }

      Log.i(TAG, "[Remote Sync] Pre-Merge ID Difference :: " + idDifference);

      if (idDifference.getLocalOnlyIds().size() > 0) {
        int updated = SignalDatabase.recipients().removeStorageIdsFromLocalOnlyUnregisteredRecipients(idDifference.getLocalOnlyIds());

        if (updated > 0) {
          Log.w(TAG, "Found " + updated + " records that were deleted remotely but only marked unregistered locally. Removed those from local store. Recalculating diff.");

          localStorageIdsBeforeMerge = getAllLocalStorageIds(self);
          idDifference               = StorageSyncHelper.findIdDifference(remoteManifest.getStorageIds(), localStorageIdsBeforeMerge);
        }
      }

      stopwatch.split("remote-id-diff");

      if (!idDifference.isEmpty()) {
        Log.i(TAG, "[Remote Sync] Retrieving records for key difference.");

        List<SignalStorageRecord> remoteOnlyRecords = accountManager.readStorageRecords(storageServiceKey, idDifference.getRemoteOnlyIds());

        stopwatch.split("remote-records");

        if (remoteOnlyRecords.size() != idDifference.getRemoteOnlyIds().size()) {
          Log.w(TAG, "[Remote Sync] Could not find all remote-only records! Requested: " + idDifference.getRemoteOnlyIds().size() + ", Found: " + remoteOnlyRecords.size() + ". These stragglers should naturally get deleted during the sync.");
        }

        StorageRecordCollection remoteOnly = new StorageRecordCollection(remoteOnlyRecords);

        db.beginTransaction();
        try {
          Log.i(TAG, "[Remote Sync] Remote-Only :: Contacts: " + remoteOnly.contacts.size() + ", GV1: " + remoteOnly.gv1.size() + ", GV2: " + remoteOnly.gv2.size() + ", Account: " + remoteOnly.account.size() + ", DLists: " + remoteOnly.storyDistributionLists.size());

          processKnownRecords(context, remoteOnly);

          List<SignalStorageRecord> unknownInserts = remoteOnly.unknown;
          List<StorageId>           unknownDeletes = Stream.of(idDifference.getLocalOnlyIds()).filter(StorageId::isUnknown).toList();

          Log.i(TAG, "[Remote Sync] Unknowns :: " + unknownInserts.size() + " inserts, " + unknownDeletes.size() + " deletes");

          storageIdDatabase.insert(unknownInserts);
          storageIdDatabase.delete(unknownDeletes);

          db.setTransactionSuccessful();
        } finally {
          db.endTransaction();
          ApplicationDependencies.getDatabaseObserver().notifyConversationListListeners();
          stopwatch.split("remote-merge-transaction");
        }
      } else {
        Log.i(TAG, "[Remote Sync] Remote version was newer, but there were no remote-only IDs.");
      }
    } else if (remoteManifest.getVersion() < localManifest.getVersion()) {
      Log.w(TAG, "[Remote Sync] Remote version was older. User might have switched accounts.");
    }

    if (remoteManifest != localManifest) {
      Log.i(TAG, "[Remote Sync] Saved new manifest. Now at version: " + remoteManifest.getVersionString());
      SignalStore.storageService().setManifest(remoteManifest);
    }

    Log.i(TAG, "We are up-to-date with the remote storage state.");

    final WriteOperationResult remoteWriteOperation;

    db.beginTransaction();
    try {
      self = freshSelf();

      int removedUnregistered = SignalDatabase.recipients().removeStorageIdsFromOldUnregisteredRecipients(System.currentTimeMillis());
      if (removedUnregistered > 0) {
        Log.i(TAG, "Removed " + removedUnregistered + " recipients from storage service that have been unregistered for longer than 30 days.");
      }

      List<StorageId>           localStorageIds = getAllLocalStorageIds(self).stream().filter(it -> !it.isUnknown()).collect(Collectors.toList());
      IdDifferenceResult        idDifference    = StorageSyncHelper.findIdDifference(remoteManifest.getStorageIds(), localStorageIds);
      List<SignalStorageRecord> remoteInserts   = buildLocalStorageRecords(context, self, idDifference.getLocalOnlyIds());
      List<byte[]>              remoteDeletes   = Stream.of(idDifference.getRemoteOnlyIds()).map(StorageId::getRaw).toList();

      Log.i(TAG, "ID Difference :: " + idDifference);

      remoteWriteOperation = new WriteOperationResult(new SignalStorageManifest(remoteManifest.getVersion() + 1, SignalStore.account().getDeviceId(), localStorageIds),
                                                      remoteInserts,
                                                      remoteDeletes);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
      stopwatch.split("local-data-transaction");
    }

    if (!remoteWriteOperation.isEmpty()) {
      Log.i(TAG, "We have something to write remotely.");
      Log.i(TAG, "WriteOperationResult :: " + remoteWriteOperation);

      StorageSyncValidations.validate(remoteWriteOperation, remoteManifest, needsForcePush, self);

      Optional<SignalStorageManifest> conflict = accountManager.writeStorageRecords(storageServiceKey, remoteWriteOperation.getManifest(), remoteWriteOperation.getInserts(), remoteWriteOperation.getDeletes());

      if (conflict.isPresent()) {
        Log.w(TAG, "Hit a conflict when trying to resolve the conflict! Retrying.");
        throw new RetryLaterException();
      }

      Log.i(TAG, "Saved new manifest. Now at version: " + remoteWriteOperation.getManifest().getVersionString());
      SignalStore.storageService().setManifest(remoteWriteOperation.getManifest());

      stopwatch.split("remote-write");

      needsMultiDeviceSync = true;
    } else {
      Log.i(TAG, "No remote writes needed. Still at version: " + remoteManifest.getVersionString());
    }

    List<Integer>   knownTypes      = getKnownTypes();
    List<StorageId> knownUnknownIds = SignalDatabase.unknownStorageIds().getAllWithTypes(knownTypes);

    if (knownUnknownIds.size() > 0) {
      Log.i(TAG, "We have " + knownUnknownIds.size() + " unknown records that we can now process.");

      List<SignalStorageRecord> remote  = accountManager.readStorageRecords(storageServiceKey, knownUnknownIds);
      StorageRecordCollection   records = new StorageRecordCollection(remote);

      Log.i(TAG, "Found " + remote.size() + " of the known-unknowns remotely.");

      db.beginTransaction();
      try {
        processKnownRecords(context, records);
        SignalDatabase.unknownStorageIds().getAllWithTypes(knownTypes);
        db.setTransactionSuccessful();
      } finally {
        db.endTransaction();
      }

      Log.i(TAG, "Enqueueing a storage sync job to handle any possible merges after applying unknown records.");
      ApplicationDependencies.getJobManager().add(new StorageSyncJob());
    }

    stopwatch.split("known-unknowns");

    if (needsForcePush && SignalStore.account().isPrimaryDevice()) {
      Log.w(TAG, "Scheduling a force push.");
      ApplicationDependencies.getJobManager().add(new StorageForcePushJob());
    }

    stopwatch.stop(TAG);
    return needsMultiDeviceSync;
  }

  private static void processKnownRecords(@NonNull Context context, @NonNull StorageRecordCollection records) throws IOException {
    new ContactRecordProcessor().process(records.contacts, StorageSyncHelper.KEY_GENERATOR);
    new GroupV1RecordProcessor(context).process(records.gv1, StorageSyncHelper.KEY_GENERATOR);
    new GroupV2RecordProcessor(context).process(records.gv2, StorageSyncHelper.KEY_GENERATOR);
    new AccountRecordProcessor(context, freshSelf()).process(records.account, StorageSyncHelper.KEY_GENERATOR);

    if (getKnownTypes().contains(ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST_VALUE)) {
      new StoryDistributionListRecordProcessor().process(records.storyDistributionLists, StorageSyncHelper.KEY_GENERATOR);
    }
  }

  private static @NonNull List<StorageId> getAllLocalStorageIds(@NonNull Recipient self) {
    return Util.concatenatedList(SignalDatabase.recipients().getContactStorageSyncIds(),
                                 Collections.singletonList(StorageId.forAccount(self.getStorageServiceId())),
                                 SignalDatabase.unknownStorageIds().getAllUnknownIds());
  }

  private static @NonNull List<SignalStorageRecord> buildLocalStorageRecords(@NonNull Context context, @NonNull Recipient self, @NonNull Collection<StorageId> ids) {
    if (ids.isEmpty()) {
      return Collections.emptyList();
    }

    RecipientTable        recipientTable    = SignalDatabase.recipients();
    UnknownStorageIdTable storageIdDatabase = SignalDatabase.unknownStorageIds();

    List<SignalStorageRecord> records = new ArrayList<>(ids.size());

    for (StorageId id : ids) {
      switch (id.getType()) {
        case ManifestRecord.Identifier.Type.CONTACT_VALUE:
        case ManifestRecord.Identifier.Type.GROUPV1_VALUE:
        case ManifestRecord.Identifier.Type.GROUPV2_VALUE:
          RecipientRecord settings = recipientTable.getByStorageId(id.getRaw());
          if (settings != null) {
            if (settings.getGroupType() == RecipientTable.GroupType.SIGNAL_V2 && settings.getSyncExtras().getGroupMasterKey() == null) {
              throw new MissingGv2MasterKeyError();
            } else {
              records.add(StorageSyncModels.localToRemoteRecord(settings));
            }
          } else {
            throw new MissingRecipientModelError("Missing local recipient model! Type: " + id.getType());
          }
          break;
        case ManifestRecord.Identifier.Type.ACCOUNT_VALUE:
          if (!Arrays.equals(self.getStorageServiceId(), id.getRaw())) {
            throw new AssertionError("Local storage ID doesn't match self!");
          }
          records.add(StorageSyncHelper.buildAccountRecord(context, self));
          break;
        case ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST_VALUE:
          RecipientRecord record = recipientTable.getByStorageId(id.getRaw());
          if (record != null) {
            if (record.getDistributionListId() != null) {
              records.add(StorageSyncModels.localToRemoteRecord(record));
            } else {
              throw new MissingRecipientModelError("Missing local recipient model (no DistributionListId)! Type: " + id.getType());
            }
          } else {
            throw new MissingRecipientModelError("Missing local recipient model! Type: " + id.getType());
          }
          break;
        default:
          SignalStorageRecord unknown = storageIdDatabase.getById(id.getRaw());
          if (unknown != null) {
            records.add(unknown);
          } else {
            throw new MissingUnknownModelError("Missing local unknown model! Type: " + id.getType());
          }
          break;
      }
    }

    return records;
  }

  private static @NonNull Recipient freshSelf() {
    Recipient.self().live().refresh();
    return Recipient.self();
  }

  private static List<Integer> getKnownTypes() {
    return Arrays.stream(ManifestRecord.Identifier.Type.values())
                 .filter(it -> !it.equals(ManifestRecord.Identifier.Type.UNKNOWN) && !it.equals(ManifestRecord.Identifier.Type.UNRECOGNIZED))
                 .filter(it -> Recipient.self().getStoriesCapability() == Recipient.Capability.SUPPORTED || !it.equals(ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST))
                 .map(it -> it.getNumber())
                 .collect(Collectors.toList());
  }

  private static final class StorageRecordCollection {
    final List<SignalContactRecord>               contacts               = new LinkedList<>();
    final List<SignalGroupV1Record>               gv1                    = new LinkedList<>();
    final List<SignalGroupV2Record>               gv2                    = new LinkedList<>();
    final List<SignalAccountRecord>               account                = new LinkedList<>();
    final List<SignalStorageRecord>               unknown                = new LinkedList<>();
    final List<SignalStoryDistributionListRecord> storyDistributionLists = new LinkedList<>();

    StorageRecordCollection(Collection<SignalStorageRecord> records) {
      for (SignalStorageRecord record : records) {
        if (record.getContact().isPresent()) {
          contacts.add(record.getContact().get());
        } else if (record.getGroupV1().isPresent()) {
          gv1.add(record.getGroupV1().get());
        } else if (record.getGroupV2().isPresent()) {
          gv2.add(record.getGroupV2().get());
        } else if (record.getAccount().isPresent()) {
          account.add(record.getAccount().get());
        } else if (record.getStoryDistributionList().isPresent()) {
          storyDistributionLists.add(record.getStoryDistributionList().get());
        } else if (record.getId().isUnknown()) {
          unknown.add(record);
        } else {
          Log.w(TAG, "Bad record! Type is a known value (" + record.getId().getType() + "), but doesn't have a matching inner record. Dropping it.");
        }
      }
    }
  }

  private static final class MissingGv2MasterKeyError extends Error {}

  private static final class MissingRecipientModelError extends Error {
    public MissingRecipientModelError(String message) {
      super(message);
    }
  }

  private static final class MissingUnknownModelError extends Error {
    public MissingUnknownModelError(String message) {
      super(message);
    }
  }

  public static final class Factory implements Job.Factory<StorageSyncJob> {
    @Override
    public @NonNull StorageSyncJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new StorageSyncJob(parameters);
    }
  }
}

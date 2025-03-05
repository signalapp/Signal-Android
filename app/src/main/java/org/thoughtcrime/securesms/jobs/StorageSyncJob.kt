package org.thoughtcrime.securesms.jobs

import android.content.Context
import com.annimon.stream.Stream
import org.signal.core.util.Stopwatch
import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.signal.libsignal.protocol.InvalidKeyException
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.AccountRecordProcessor
import org.thoughtcrime.securesms.storage.CallLinkRecordProcessor
import org.thoughtcrime.securesms.storage.ContactRecordProcessor
import org.thoughtcrime.securesms.storage.GroupV1RecordProcessor
import org.thoughtcrime.securesms.storage.GroupV2RecordProcessor
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.storage.StorageSyncHelper.WriteOperationResult
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.storage.StorageSyncValidations
import org.thoughtcrime.securesms.storage.StoryDistributionListRecordProcessor
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.storage.SignalCallLinkRecord
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.SignalGroupV1Record
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.StorageKey
import org.whispersystems.signalservice.api.storage.StorageServiceRepository
import org.whispersystems.signalservice.api.storage.StorageServiceRepository.ManifestIfDifferentVersionResult
import org.whispersystems.signalservice.api.storage.toSignalAccountRecord
import org.whispersystems.signalservice.api.storage.toSignalCallLinkRecord
import org.whispersystems.signalservice.api.storage.toSignalContactRecord
import org.whispersystems.signalservice.api.storage.toSignalGroupV1Record
import org.whispersystems.signalservice.api.storage.toSignalGroupV2Record
import org.whispersystems.signalservice.api.storage.toSignalStoryDistributionListRecord
import org.whispersystems.signalservice.internal.push.SyncMessage
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

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
 * from writing while the sync is happening. But that means we also need to be very careful with
 * what happens inside the transaction. Namely, we *cannot* perform network activity inside the
 * transaction.
 *
 * - This puts us in a funny situation where we have to get remote data, begin a transaction to
 * resolve the sync, and then end the transaction (and therefore commit our changes) *before*
 * we write the data remotely. Normally, this would be dangerous, as our view of the data could
 * fall out of sync if the network request fails. However, because of how the sync works, as long
 * as we don't update our local manifest version until after the network request succeeds, it
 * should all sort itself out in the retry. Because if our network request failed, then we
 * wouldn't have written all of the new IDs, and we'll still see a bunch of remote-only IDs that
 * we'll merge with local data to generate another equally-valid set of remote changes.
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
 * the diff in IDs.
 * - Then, we fetch the actual records that correspond to the remote-only IDs.
 * - Afterwards, we take those records and merge them into our local data store.
 * - Next, we assume that our local state represents the most up-to-date information, and so we
 * calculate and write a change set that represents the diff between our state and the remote
 * state.
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
 * - Update [StorageSyncModels]
 * - Update the respective record processor (i.e [ContactRecordProcessor]). You need to make sure that you're:
 *   - Merging the attributes, likely preferring remote
 *   - Adding to doParamsMatch()
 *   - Adding the parameter to the builder chain when creating a merged model
 *   - Update builder usage in StorageSyncModels
 * - Handle the new data when writing to the local storage
 * (i.e. [RecipientTable.applyStorageSyncContactUpdate]).
 * - Make sure that whenever you change the field in the UI, we rotate the storageId for that row
 * and call [StorageSyncHelper.scheduleSyncForDataChange].
 * - If you're syncing a field that was otherwise already present in the UI, you'll probably want
 * to enqueue a [StorageServiceMigrationJob] as an app migration to make sure it gets
 * synced.
 */
class StorageSyncJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY: String = "StorageSyncJobV2"
    const val QUEUE_KEY: String = "StorageSyncingJobs"

    private val TAG = Log.tag(StorageSyncJob::class.java)
  }

  constructor() : this(
    Parameters.Builder().addConstraint(NetworkConstraint.KEY)
      .setQueue(QUEUE_KEY)
      .setMaxInstancesForFactory(2)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(3)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  @Throws(IOException::class, RetryLaterException::class, UntrustedIdentityException::class)
  override fun onRun() {
    if (!SignalStore.svr.hasOptedInWithAccess() && !SignalStore.svr.hasOptedOut()) {
      Log.i(TAG, "Doesn't have a PIN. Skipping.")
      return
    }

    if (!SignalStore.account.isRegistered) {
      Log.i(TAG, "Not registered. Skipping.")
      return
    }

    if (!Recipient.self().hasE164 || !Recipient.self().hasServiceId) {
      Log.w(TAG, "Missing E164 or ACI!")
      return
    }

    if (SignalStore.internal.storageServiceDisabled) {
      Log.w(TAG, "Storage service has been manually disabled. Skipping.")
      return
    }

    val (storageServiceKey, usingTempKey) = SignalStore.storageService.storageKeyForInitialDataRestore?.let {
      Log.i(TAG, "Using temporary storage key.")
      it to true
    } ?: run {
      SignalStore.storageService.storageKey to false
    }

    try {
      val needsMultiDeviceSync = performSync(storageServiceKey)

      if (usingTempKey) {
        Log.i(TAG, "Used a temp key. Scheduling a job to rotate the manifest.")
        AppDependencies.jobManager.add(StorageRotateManifestJob())
      }

      if (SignalStore.account.hasLinkedDevices && needsMultiDeviceSync) {
        AppDependencies.jobManager.add(MultiDeviceStorageSyncRequestJob())
      }

      SignalStore.storageService.lastSyncTime = System.currentTimeMillis()
    } catch (e: InvalidKeyException) {
      if (SignalStore.account.isPrimaryDevice) {
        Log.w(TAG, "Failed to decrypt remote storage! Force-pushing and syncing the storage key to linked devices.", e)

        AppDependencies.jobManager
          .startChain(MultiDeviceKeysUpdateJob())
          .then(StorageForcePushJob())
          .then(MultiDeviceStorageSyncRequestJob())
          .enqueue()
      } else {
        Log.w(TAG, "Failed to decrypt remote storage! Requesting new keys from primary.", e)
        SignalStore.storageService.clearStorageKeyFromPrimary()
        AppDependencies.signalServiceMessageSender.sendSyncMessage(SignalServiceSyncMessage.forRequest(RequestMessage.forType(SyncMessage.Request.Type.KEYS)))
      }
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is PushNetworkException || e is RetryLaterException
  }

  override fun onFailure() {
  }

  @Throws(IOException::class, RetryLaterException::class, InvalidKeyException::class)
  private fun performSync(storageServiceKey: StorageKey): Boolean {
    val stopwatch = Stopwatch("StorageSync")
    val db = SignalDatabase.rawDatabase
    val repository = StorageServiceRepository(SignalNetwork.storageService)

    val localManifest = SignalStore.storageService.manifest
    val remoteManifest = when (val result = repository.getStorageManifestIfDifferentVersion(storageServiceKey, localManifest.version)) {
      is ManifestIfDifferentVersionResult.DifferentVersion -> result.manifest
      ManifestIfDifferentVersionResult.SameVersion -> localManifest
      is ManifestIfDifferentVersionResult.DecryptionError -> throw result.exception
      is ManifestIfDifferentVersionResult.NetworkError -> throw result.exception
      is ManifestIfDifferentVersionResult.StatusCodeError -> throw result.exception
    }
    stopwatch.split("remote-manifest")

    var self = freshSelf()
    var needsMultiDeviceSync = false
    var needsForcePush = false

    if (self.storageId == null) {
      Log.w(TAG, "No storageId for self. Generating.")
      SignalDatabase.recipients.updateStorageId(self.id, StorageSyncHelper.generateKey())
      self = freshSelf()
    }

    Log.i(TAG, "Our version: ${localManifest.versionString}, their version: ${remoteManifest.versionString}")

    if (remoteManifest.version > localManifest.version) {
      Log.i(TAG, "[Remote Sync] Newer manifest version found!")

      var localStorageIdsBeforeMerge = getAllLocalStorageIds(self)
      var idDifference = StorageSyncHelper.findIdDifference(remoteManifest.storageIds, localStorageIdsBeforeMerge)

      if (idDifference.hasTypeMismatches && SignalStore.account.isPrimaryDevice) {
        Log.w(TAG, "[Remote Sync] Found type mismatches in the ID sets! Scheduling a force push after this sync completes.")
        needsForcePush = true
      }

      Log.i(TAG, "[Remote Sync] Pre-Merge ID Difference :: $idDifference")

      if (idDifference.localOnlyIds.isNotEmpty()) {
        val updated = SignalDatabase.recipients.removeStorageIdsFromLocalOnlyUnregisteredRecipients(idDifference.localOnlyIds)

        if (updated > 0) {
          Log.w(TAG, "Found $updated records that were deleted remotely but only marked unregistered locally. Removed those from local store. Recalculating diff.")

          localStorageIdsBeforeMerge = getAllLocalStorageIds(self)
          idDifference = StorageSyncHelper.findIdDifference(remoteManifest.storageIds, localStorageIdsBeforeMerge)
        }
      }

      stopwatch.split("remote-id-diff")

      if (!idDifference.isEmpty) {
        Log.i(TAG, "[Remote Sync] Retrieving records for key difference.")

        val remoteOnlyRecords = when (val result = repository.readStorageRecords(storageServiceKey, remoteManifest.recordIkm, idDifference.remoteOnlyIds)) {
          is StorageServiceRepository.StorageRecordResult.Success -> result.records
          is StorageServiceRepository.StorageRecordResult.DecryptionError -> throw result.exception
          is StorageServiceRepository.StorageRecordResult.NetworkError -> throw result.exception
          is StorageServiceRepository.StorageRecordResult.StatusCodeError -> throw result.exception
        }

        stopwatch.split("remote-records")

        if (remoteOnlyRecords.size != idDifference.remoteOnlyIds.size) {
          Log.w(TAG, "[Remote Sync] Could not find all remote-only records! Requested: ${idDifference.remoteOnlyIds.size}, Found: ${remoteOnlyRecords.size}. These stragglers should naturally get deleted during the sync.")
        }

        val remoteOnly = StorageRecordCollection(remoteOnlyRecords)

        db.beginTransaction()
        try {
          Log.i(TAG, "[Remote Sync] Remote-Only :: Contacts: ${remoteOnly.contacts.size}, GV1: ${remoteOnly.gv1.size}, GV2: ${remoteOnly.gv2.size}, Account: ${remoteOnly.account.size}, DLists: ${remoteOnly.storyDistributionLists.size}, call links: ${remoteOnly.callLinkRecords.size}")

          processKnownRecords(context, remoteOnly)

          val unknownInserts: List<SignalStorageRecord> = remoteOnly.unknown
          val unknownDeletes = Stream.of(idDifference.localOnlyIds).filter { obj: StorageId -> obj.isUnknown }.toList()

          Log.i(TAG, "[Remote Sync] Unknowns :: " + unknownInserts.size + " inserts, " + unknownDeletes.size + " deletes")

          SignalDatabase.unknownStorageIds.insert(unknownInserts)
          SignalDatabase.unknownStorageIds.delete(unknownDeletes)

          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
          AppDependencies.databaseObserver.notifyConversationListListeners()
          stopwatch.split("remote-merge-transaction")
        }
      } else {
        Log.i(TAG, "[Remote Sync] Remote version was newer, but there were no remote-only IDs.")
      }
    } else if (remoteManifest.version < localManifest.version) {
      Log.w(TAG, "[Remote Sync] Remote version was older. User might have switched accounts.")
    }

    if (remoteManifest !== localManifest) {
      Log.i(TAG, "[Remote Sync] Saved new manifest. Now at version: ${remoteManifest.versionString}")
      SignalStore.storageService.manifest = remoteManifest
    }

    Log.i(TAG, "We are up-to-date with the remote storage state.")

    if (remoteManifest.recordIkm == null && Recipient.self().storageServiceEncryptionV2Capability.isSupported) {
      Log.w(TAG, "The SSRE2 capability is supported, but no recordIkm is set! Force pushing.")
      AppDependencies.jobManager.add(StorageForcePushJob())
      return false
    }

    val remoteWriteOperation: WriteOperationResult = db.withinTransaction {
      self = freshSelf()

      val removedUnregistered = SignalDatabase.recipients.removeStorageIdsFromOldUnregisteredRecipients(System.currentTimeMillis())
      if (removedUnregistered > 0) {
        Log.i(TAG, "Removed $removedUnregistered recipients from storage service that have been unregistered for longer than 30 days.")
      }

      val localStorageIds = getAllLocalStorageIds(self)
      val idDifference = StorageSyncHelper.findIdDifference(remoteManifest.storageIds, localStorageIds)
      val remoteInserts = buildLocalStorageRecords(context, self, idDifference.localOnlyIds.stream().filter { it: StorageId -> !it.isUnknown }.collect(Collectors.toList()))
      val remoteDeletes = Stream.of(idDifference.remoteOnlyIds).map { obj: StorageId -> obj.raw }.toList()

      Log.i(TAG, "ID Difference :: $idDifference")

      WriteOperationResult(
        manifest = SignalStorageManifest(
          version = remoteManifest.version + 1,
          sourceDeviceId = SignalStore.account.deviceId,
          recordIkm = remoteManifest.recordIkm,
          storageIds = localStorageIds
        ),
        inserts = remoteInserts,
        deletes = remoteDeletes
      )
    }
    stopwatch.split("local-data-transaction")

    if (!remoteWriteOperation.isEmpty) {
      Log.i(TAG, "We have something to write remotely.")
      Log.i(TAG, "WriteOperationResult :: $remoteWriteOperation")

      StorageSyncValidations.validate(remoteWriteOperation, remoteManifest, needsForcePush, self)

      when (val result = repository.writeStorageRecords(storageServiceKey, remoteWriteOperation.manifest, remoteWriteOperation.inserts, remoteWriteOperation.deletes)) {
        StorageServiceRepository.WriteStorageRecordsResult.Success -> Unit
        is StorageServiceRepository.WriteStorageRecordsResult.StatusCodeError -> throw result.exception
        is StorageServiceRepository.WriteStorageRecordsResult.NetworkError -> throw result.exception
        StorageServiceRepository.WriteStorageRecordsResult.ConflictError -> {
          Log.w(TAG, "Hit a conflict when trying to resolve the conflict! Retrying.")
          throw RetryLaterException()
        }
      }

      Log.i(TAG, "Saved new manifest. Now at version: ${remoteWriteOperation.manifest.versionString}")
      SignalStore.storageService.manifest = remoteWriteOperation.manifest
      SignalStore.svr.masterKeyForInitialDataRestore = null

      stopwatch.split("remote-write")

      needsMultiDeviceSync = true
    } else {
      Log.i(TAG, "No remote writes needed. Still at version: " + remoteManifest.versionString)
    }

    val knownTypes = getKnownTypes()
    val knownUnknownIds = SignalDatabase.unknownStorageIds.getAllWithTypes(knownTypes)

    if (knownUnknownIds.isNotEmpty()) {
      Log.i(TAG, "We have ${knownUnknownIds.size} unknown records that we can now process.")

      val remote = when (val result = repository.readStorageRecords(storageServiceKey, remoteManifest.recordIkm, knownUnknownIds)) {
        is StorageServiceRepository.StorageRecordResult.Success -> result.records
        is StorageServiceRepository.StorageRecordResult.DecryptionError -> throw result.exception
        is StorageServiceRepository.StorageRecordResult.NetworkError -> throw result.exception
        is StorageServiceRepository.StorageRecordResult.StatusCodeError -> throw result.exception
      }
      val records = StorageRecordCollection(remote)

      Log.i(TAG, "Found ${remote.size} of the known-unknowns remotely.")

      db.withinTransaction {
        processKnownRecords(context, records)
        SignalDatabase.unknownStorageIds.deleteAllWithTypes(knownTypes)
      }

      Log.i(TAG, "Enqueueing a storage sync job to handle any possible merges after applying unknown records.")
      AppDependencies.jobManager.add(StorageSyncJob())
    }

    stopwatch.split("known-unknowns")

    if (needsForcePush && SignalStore.account.isPrimaryDevice) {
      Log.w(TAG, "Scheduling a force push.")
      AppDependencies.jobManager.add(StorageForcePushJob())
    }

    stopwatch.stop(TAG)
    return needsMultiDeviceSync
  }

  @Throws(IOException::class)
  private fun processKnownRecords(context: Context, records: StorageRecordCollection) {
    ContactRecordProcessor().process(records.contacts, StorageSyncHelper.KEY_GENERATOR)
    GroupV1RecordProcessor().process(records.gv1, StorageSyncHelper.KEY_GENERATOR)
    GroupV2RecordProcessor().process(records.gv2, StorageSyncHelper.KEY_GENERATOR)
    AccountRecordProcessor(context, freshSelf()).process(records.account, StorageSyncHelper.KEY_GENERATOR)
    StoryDistributionListRecordProcessor().process(records.storyDistributionLists, StorageSyncHelper.KEY_GENERATOR)
    CallLinkRecordProcessor().process(records.callLinkRecords, StorageSyncHelper.KEY_GENERATOR)
  }

  private fun getAllLocalStorageIds(self: Recipient): List<StorageId> {
    return SignalDatabase.recipients.getContactStorageSyncIds() +
      listOf(StorageId.forAccount(self.storageId)) +
      SignalDatabase.unknownStorageIds.allUnknownIds
  }

  private fun buildLocalStorageRecords(context: Context, self: Recipient, ids: Collection<StorageId>): List<SignalStorageRecord> {
    if (ids.isEmpty()) {
      return emptyList()
    }

    val records: MutableList<SignalStorageRecord> = ArrayList(ids.size)

    for (id in ids) {
      var type = ManifestRecord.Identifier.Type.fromValue(id.type)
      if (type == null) {
        type = ManifestRecord.Identifier.Type.UNKNOWN
      }

      when (type) {
        ManifestRecord.Identifier.Type.CONTACT, ManifestRecord.Identifier.Type.GROUPV1, ManifestRecord.Identifier.Type.GROUPV2 -> {
          val settings = SignalDatabase.recipients.getByStorageId(id.raw)
          if (settings != null) {
            if (settings.recipientType == RecipientTable.RecipientType.GV2 && settings.syncExtras.groupMasterKey == null) {
              throw MissingGv2MasterKeyError()
            } else {
              records.add(StorageSyncModels.localToRemoteRecord(settings))
            }
          } else {
            throw MissingRecipientModelError("Missing local recipient model! Type: " + id.type)
          }
        }

        ManifestRecord.Identifier.Type.ACCOUNT -> {
          if (!self.storageId.contentEquals(id.raw)) {
            throw AssertionError("Local storage ID doesn't match self!")
          }
          records.add(StorageSyncHelper.buildAccountRecord(context, self))
        }

        ManifestRecord.Identifier.Type.STORY_DISTRIBUTION_LIST -> {
          val record = SignalDatabase.recipients.getByStorageId(id.raw)
          if (record != null) {
            if (record.distributionListId != null) {
              records.add(StorageSyncModels.localToRemoteRecord(record))
            } else {
              throw MissingRecipientModelError("Missing local recipient model (no DistributionListId)! Type: " + id.type)
            }
          } else {
            throw MissingRecipientModelError("Missing local recipient model! Type: " + id.type)
          }
        }

        ManifestRecord.Identifier.Type.CALL_LINK -> {
          val callLinkRecord = SignalDatabase.recipients.getByStorageId(id.raw)
          if (callLinkRecord != null) {
            if (callLinkRecord.callLinkRoomId != null) {
              records.add(StorageSyncModels.localToRemoteRecord(callLinkRecord))
            } else {
              throw MissingRecipientModelError("Missing local recipient model (no CallLinkRoomId)! Type: " + id.type)
            }
          } else {
            throw MissingRecipientModelError("Missing local recipient model! Type: " + id.type)
          }
        }

        else -> {
          val unknown = SignalDatabase.unknownStorageIds.getById(id.raw)
          if (unknown != null) {
            records.add(unknown)
          } else {
            throw MissingUnknownModelError("Missing local unknown model! Type: " + id.type)
          }
        }
      }
    }

    return records
  }

  private fun freshSelf(): Recipient {
    Recipient.self().live().refresh()
    return Recipient.self()
  }

  private fun getKnownTypes(): List<Int> {
    return ManifestRecord.Identifier.Type.entries
      .filter { it != ManifestRecord.Identifier.Type.UNKNOWN }
      .map { it.value }
  }

  private class StorageRecordCollection(records: Collection<SignalStorageRecord>) {
    val contacts: MutableList<SignalContactRecord> = mutableListOf()
    val gv1: MutableList<SignalGroupV1Record> = mutableListOf()
    val gv2: MutableList<SignalGroupV2Record> = mutableListOf()
    val account: MutableList<SignalAccountRecord> = mutableListOf()
    val unknown: MutableList<SignalStorageRecord> = mutableListOf()
    val storyDistributionLists: MutableList<SignalStoryDistributionListRecord> = mutableListOf()
    val callLinkRecords: MutableList<SignalCallLinkRecord> = mutableListOf()

    init {
      for (record in records) {
        if (record.proto.contact != null) {
          contacts += record.proto.contact!!.toSignalContactRecord(record.id)
        } else if (record.proto.groupV1 != null) {
          gv1 += record.proto.groupV1!!.toSignalGroupV1Record(record.id)
        } else if (record.proto.groupV2 != null) {
          gv2 += record.proto.groupV2!!.toSignalGroupV2Record(record.id)
        } else if (record.proto.account != null) {
          account += record.proto.account!!.toSignalAccountRecord(record.id)
        } else if (record.proto.storyDistributionList != null) {
          storyDistributionLists += record.proto.storyDistributionList!!.toSignalStoryDistributionListRecord(record.id)
        } else if (record.proto.callLink != null) {
          callLinkRecords += record.proto.callLink!!.toSignalCallLinkRecord(record.id)
        } else if (record.id.isUnknown) {
          unknown += record
        } else {
          Log.w(TAG, "Bad record! Type is a known value (${record.id.type}), but doesn't have a matching inner record. Dropping it.")
        }
      }
    }
  }

  private class MissingGv2MasterKeyError : Error()

  private class MissingRecipientModelError(message: String?) : Error(message)

  private class MissingUnknownModelError(message: String?) : Error(message)

  class Factory : Job.Factory<StorageSyncJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): StorageSyncJob {
      return StorageSyncJob(parameters)
    }
  }
}

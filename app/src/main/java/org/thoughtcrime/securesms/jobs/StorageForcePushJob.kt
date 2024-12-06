package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.storage.StorageSyncValidations
import org.thoughtcrime.securesms.transport.RetryLaterException
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.storage.RecordIkm
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.api.storage.StorageServiceRepository
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Forces remote storage to match our local state. This should only be done when we detect that the
 * remote data is badly-encrypted (which should only happen after re-registering without a PIN).
 */
class StorageForcePushJob private constructor(parameters: Parameters) : BaseJob(parameters) {
  companion object {
    const val KEY: String = "StorageForcePushJob"

    private val TAG = Log.tag(StorageForcePushJob::class.java)
  }

  constructor() : this(
    Parameters.Builder().addConstraint(NetworkConstraint.KEY)
      .setQueue(StorageSyncJob.QUEUE_KEY)
      .setMaxInstancesForFactory(1)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  @Throws(IOException::class, RetryLaterException::class)
  override fun onRun() {
    if (SignalStore.account.isLinkedDevice) {
      Log.i(TAG, "Only the primary device can force push")
      return
    }

    if (!SignalStore.account.isRegistered || SignalStore.account.e164 == null) {
      Log.w(TAG, "User not registered. Skipping.")
      return
    }

    if (Recipient.self().storageId == null) {
      Log.w(TAG, "No storage ID set for self! Skipping.")
      return
    }

    val storageServiceKey = SignalStore.storageService.storageKey
    val repository = StorageServiceRepository(AppDependencies.storageServiceApi)

    val currentVersion = when (val result = repository.getManifestVersion()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> throw result.exception
    }
    val oldContactStorageIds: Map<RecipientId, StorageId> = SignalDatabase.recipients.getContactStorageSyncIdsMap()

    val newVersion = currentVersion + 1
    val newContactStorageIds = generateContactStorageIds(oldContactStorageIds)
    val inserts: MutableList<SignalStorageRecord> = oldContactStorageIds.keys
      .mapNotNull { SignalDatabase.recipients.getRecordForSync(it) }
      .map { record -> StorageSyncModels.localToRemoteRecord(record, newContactStorageIds[record.id]!!.raw) }
      .toMutableList()

    val accountRecord = StorageSyncHelper.buildAccountRecord(context, Recipient.self().fresh())
    val allNewStorageIds: MutableList<StorageId> = ArrayList(newContactStorageIds.values)

    inserts.add(accountRecord)
    allNewStorageIds.add(accountRecord.id)

    val recordIkm: RecordIkm? = if (Recipient.self().storageServiceEncryptionV2Capability.isSupported) {
      Log.i(TAG, "Generating and including a new recordIkm.")
      RecordIkm.generate()
    } else {
      Log.i(TAG, "SSRE2 not yet supported. Not including recordIkm.")
      null
    }

    val manifest = SignalStorageManifest(newVersion, SignalStore.account.deviceId, recordIkm, allNewStorageIds)
    StorageSyncValidations.validateForcePush(manifest, inserts, Recipient.self().fresh())

    if (newVersion > 1) {
      Log.i(TAG, "Force-pushing data. Inserting ${inserts.size} IDs.")
      when (val result = repository.resetAndWriteStorageRecords(storageServiceKey, manifest, inserts)) {
        StorageServiceRepository.WriteStorageRecordsResult.Success -> Unit
        is StorageServiceRepository.WriteStorageRecordsResult.StatusCodeError -> throw result.exception
        is StorageServiceRepository.WriteStorageRecordsResult.NetworkError -> throw result.exception
        StorageServiceRepository.WriteStorageRecordsResult.ConflictError -> {
          Log.w(TAG, "Hit a conflict. Trying again.")
          throw RetryLaterException()
        }
      }
    } else {
      Log.i(TAG, "First version, normal push. Inserting ${inserts.size} IDs.")
      when (val result = repository.writeStorageRecords(storageServiceKey, manifest, inserts, emptyList())) {
        StorageServiceRepository.WriteStorageRecordsResult.Success -> Unit
        is StorageServiceRepository.WriteStorageRecordsResult.StatusCodeError -> throw result.exception
        is StorageServiceRepository.WriteStorageRecordsResult.NetworkError -> throw result.exception
        is StorageServiceRepository.WriteStorageRecordsResult.ConflictError -> {
          Log.w(TAG, "Hit a conflict. Trying again.")
          throw RetryLaterException()
        }
      }
    }

    Log.i(TAG, "Force push succeeded. Updating local manifest version to: $newVersion")
    SignalStore.storageService.manifest = manifest
    SignalStore.svr.masterKeyForInitialDataRestore = null
    SignalDatabase.recipients.applyStorageIdUpdates(newContactStorageIds)
    SignalDatabase.recipients.applyStorageIdUpdates(Collections.singletonMap(Recipient.self().id, accountRecord.id))
    SignalDatabase.unknownStorageIds.deleteAll()
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is PushNetworkException || e is RetryLaterException
  }

  override fun onFailure() = Unit

  private fun generateContactStorageIds(oldKeys: Map<RecipientId, StorageId>): Map<RecipientId, StorageId> {
    val out: MutableMap<RecipientId, StorageId> = mutableMapOf()

    for ((key, value) in oldKeys) {
      out[key] = value.withNewBytes(StorageSyncHelper.generateKey())
    }

    return out
  }

  class Factory : Job.Factory<StorageForcePushJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): StorageForcePushJob {
      return StorageForcePushJob(parameters)
    }
  }
}

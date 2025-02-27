package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.StorageKey
import org.whispersystems.signalservice.api.storage.StorageServiceRepository
import java.util.concurrent.TimeUnit

/**
 * After registration, if the user did not restore their AEP, they'll have a new master key and need to write a newly-encrypted manifest.
 * If the account is SSRE2-capable, that's all we have to upload.
 * If they're not, this job will recognize it and schedule a [StorageForcePushJob] instead.
 */
class StorageRotateManifestJob private constructor(parameters: Parameters) : Job(parameters) {
  companion object {
    const val KEY: String = "StorageRotateManifestJob"

    private val TAG = Log.tag(StorageRotateManifestJob::class.java)
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

  override fun run(): Result {
    if (SignalStore.account.isLinkedDevice) {
      Log.i(TAG, "Only the primary device can rotate the manifest.")
      return Result.failure()
    }

    if (!SignalStore.account.isRegistered || SignalStore.account.e164 == null) {
      Log.w(TAG, "User not registered. Skipping.")
      return Result.failure()
    }

    val restoreKey: StorageKey? = SignalStore.storageService.storageKeyForInitialDataRestore
    if (restoreKey == null) {
      Log.w(TAG, "There was no restore key present! Someone must have written to storage service in the meantime.")
      return Result.failure()
    }

    val storageServiceKey = SignalStore.storageService.storageKey
    val repository = StorageServiceRepository(AppDependencies.storageServiceApi)

    val currentManifest: SignalStorageManifest = when (val result = repository.getStorageManifest(restoreKey)) {
      is StorageServiceRepository.ManifestResult.Success -> {
        result.manifest
      }
      is StorageServiceRepository.ManifestResult.DecryptionError -> {
        Log.w(TAG, "Failed to decrypt the manifest! Only recourse is to force push.", result.exception)
        AppDependencies.jobManager.add(StorageForcePushJob())
        return Result.failure()
      }
      is StorageServiceRepository.ManifestResult.NetworkError -> {
        Log.w(TAG, "Encountered a network error during read, retrying.", result.exception)
        return Result.retry(defaultBackoff())
      }
      StorageServiceRepository.ManifestResult.NotFoundError -> {
        Log.w(TAG, "No existing manifest was found! Force pushing.")
        AppDependencies.jobManager.add(StorageForcePushJob())
        return Result.failure()
      }
      is StorageServiceRepository.ManifestResult.StatusCodeError -> {
        Log.w(TAG, "Encountered a status code error during read, retrying.", result.exception)
        return Result.retry(defaultBackoff())
      }
    }

    if (currentManifest.recordIkm == null) {
      Log.w(TAG, "No recordIkm set! Can't just rotate the manifest -- we need to re-encrypt all fo the records, too. Force pushing.")
      AppDependencies.jobManager.add(StorageForcePushJob())
      return Result.failure()
    }

    val manifestWithNewVersion = currentManifest.copy(version = currentManifest.version + 1)

    return when (val result = repository.writeUnchangedManifest(storageServiceKey, manifestWithNewVersion)) {
      StorageServiceRepository.WriteStorageRecordsResult.Success -> {
        Log.i(TAG, "Successfully rotated the manifest as version ${manifestWithNewVersion.version}.${manifestWithNewVersion.sourceDeviceId}. Clearing restore key.")
        SignalStore.svr.masterKeyForInitialDataRestore = null

        Log.i(TAG, "Saved new manifest. Now at version: ${manifestWithNewVersion.versionString}")
        SignalStore.storageService.manifest = manifestWithNewVersion

        Result.success()
      }
      StorageServiceRepository.WriteStorageRecordsResult.ConflictError -> {
        Log.w(TAG, "Hit a conflict! Enqueuing a sync followed by another rotation.")
        AppDependencies.jobManager.add(StorageSyncJob())
        AppDependencies.jobManager.add(StorageRotateManifestJob())
        Result.failure()
      }
      is StorageServiceRepository.WriteStorageRecordsResult.StatusCodeError -> {
        Log.w(TAG, "Encountered a non-conflict status code error during write. Failing.", result.exception)
        Result.failure()
      }

      is StorageServiceRepository.WriteStorageRecordsResult.NetworkError -> {
        Log.w(TAG, "Encountered a network error during write, retrying.", result.exception)
        Result.retry(defaultBackoff())
      }
    }
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<StorageRotateManifestJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): StorageRotateManifestJob {
      return StorageRotateManifestJob(parameters)
    }
  }
}

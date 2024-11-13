package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.reclaimUsernameIfNecessary
import org.thoughtcrime.securesms.recipients.Recipient.Companion.self
import org.thoughtcrime.securesms.storage.StorageSyncHelper.applyAccountStorageSyncUpdates
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.storage.SignalAccountRecord
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import java.util.concurrent.TimeUnit

/**
 * Restored the AccountRecord present in the storage service, if any. This will overwrite any local
 * data that is stored in AccountRecord, so this should only be done immediately after registration.
 */
class StorageAccountRestoreJob private constructor(parameters: Parameters) : BaseJob(parameters) {
  companion object {
    const val KEY: String = "StorageAccountRestoreJob"

    val LIFESPAN: Long = TimeUnit.SECONDS.toMillis(20)

    private val TAG = Log.tag(StorageAccountRestoreJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue(StorageSyncJob.QUEUE_KEY)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(1)
      .setLifespan(LIFESPAN)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  @Throws(Exception::class)
  override fun onRun() {
    val accountManager = AppDependencies.signalServiceAccountManager
    val storageServiceKey = SignalStore.storageService.storageKey

    Log.i(TAG, "Retrieving manifest...")
    val manifest = accountManager.getStorageManifest(storageServiceKey)

    if (!manifest.isPresent) {
      Log.w(TAG, "Manifest did not exist or was undecryptable (bad key). Not restoring. Force-pushing.")
      AppDependencies.jobManager.add(StorageForcePushJob())
      return
    }

    Log.i(TAG, "Resetting the local manifest to an empty state so that it will sync later.")
    SignalStore.storageService.manifest = SignalStorageManifest.EMPTY

    val accountId = manifest.get().accountStorageId

    if (!accountId.isPresent) {
      Log.w(TAG, "Manifest had no account record! Not restoring.")
      return
    }

    Log.i(TAG, "Retrieving account record...")
    val records = accountManager.readStorageRecords(storageServiceKey, listOf(accountId.get()))
    val record = if (records.size > 0) records[0] else null

    if (record == null) {
      Log.w(TAG, "Could not find account record, even though we had an ID! Not restoring.")
      return
    }

    if (record.proto.account == null) {
      Log.w(TAG, "The storage record didn't actually have an account on it! Not restoring.")
      return
    }

    val accountRecord = SignalAccountRecord(record.id, record.proto.account!!)

    Log.i(TAG, "Applying changes locally...")
    SignalDatabase.rawDatabase.beginTransaction()
    try {
      applyAccountStorageSyncUpdates(context, self().fresh(), accountRecord, false)
      SignalDatabase.rawDatabase.setTransactionSuccessful()
    } finally {
      SignalDatabase.rawDatabase.endTransaction()
    }

    // We will try to reclaim the username here, as early as possible, but the registration flow also enqueues a username restore job,
    // so failing here isn't a huge deal
    if (SignalStore.account.username != null) {
      Log.i(TAG, "Attempting to reclaim username...")
      val result = reclaimUsernameIfNecessary()
      Log.i(TAG, "Username reclaim result: " + result.name)
    } else {
      Log.i(TAG, "No username to reclaim.")
    }

    if (accountRecord.proto.avatarUrlPath.isNotEmpty()) {
      Log.i(TAG, "Fetching avatar...")
      val state = AppDependencies.jobManager.runSynchronously(RetrieveProfileAvatarJob(self(), accountRecord.proto.avatarUrlPath), LIFESPAN / 2)

      if (state.isPresent) {
        Log.i(TAG, "Avatar retrieved successfully. ${state.get()}")
      } else {
        Log.w(TAG, "Avatar retrieval did not complete in time (or otherwise failed).")
      }
    } else {
      Log.i(TAG, "No avatar present. Not fetching.")
    }

    Log.i(TAG, "Refreshing attributes...")
    val state = AppDependencies.jobManager.runSynchronously(RefreshAttributesJob(), LIFESPAN / 2)

    if (state.isPresent) {
      Log.i(TAG, "Attributes refreshed successfully. ${state.get()}")
    } else {
      Log.w(TAG, "Attribute refresh did not complete in time (or otherwise failed).")
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is PushNetworkException
  }

  override fun onFailure() {
  }

  class Factory : Job.Factory<StorageAccountRestoreJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): StorageAccountRestoreJob {
      return StorageAccountRestoreJob(parameters)
    }
  }
}

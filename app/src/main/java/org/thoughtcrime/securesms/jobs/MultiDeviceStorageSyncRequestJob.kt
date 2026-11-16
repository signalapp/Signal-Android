package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage

class MultiDeviceStorageSyncRequestJob private constructor(parameters: Parameters) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceStorageSyncRequestJob"

    private val TAG = Log.tag(MultiDeviceStorageSyncRequestJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue("MultiDeviceStorageSyncRequestJob")
      .setMaxInstancesForFactory(2)
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setMaxAttempts(10)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered, aborting...")
      return Result.failure()
    }

    if (!SignalStore.account.isMultiDevice) {
      Log.i(TAG, "Not multi device, aborting...")
      return Result.success()
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncFetchLatest(SyncMessage.FetchLatest.Type.STORAGE_MANIFEST),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "storage fetch sync", defaultBackoff()) }
    )
  }

  override fun onFailure() {
    Log.w(TAG, "Did not succeed!")
  }

  class Factory : Job.Factory<MultiDeviceStorageSyncRequestJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceStorageSyncRequestJob {
      return MultiDeviceStorageSyncRequestJob(parameters)
    }
  }
}

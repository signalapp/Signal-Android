package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.RecipientRecord
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.concurrent.TimeUnit

class MultiDeviceBlockedUpdateJob private constructor(parameters: Parameters) : CoroutineJob(parameters) {
  companion object {
    const val KEY: String = "MultiDeviceBlockedUpdateJob"

    private val TAG = Log.tag(MultiDeviceBlockedUpdateJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setQueue("MultiDeviceBlockedUpdateJob")
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
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

    val blocked: List<RecipientRecord> = SignalDatabase.recipients.getBlocked()
    val blockedGroups = blocked.mapNotNull { record -> record.groupId?.decodedId?.let { record to it.toByteString() } }
    val blockedAcis = blocked.mapNotNull { record -> record.aci?.let { record to it.toByteString() } }
    val blockedE164s = blocked.mapNotNull { record -> record.e164?.let { record to it } }

    val blockedMessage = SyncMessage.Blocked(
      acisBinary = blockedAcis.map { (_, aci) -> aci },
      numbers = blockedE164s.map { (_, e164) -> e164 },
      groupIds = blockedGroups.map { (_, groupId) -> groupId },
      blockedE164s = blockedE164s.map { (record, e164) -> SyncMessage.Blocked.BlockedE164(e164 = e164, timestamp = record.blockedAt) },
      blockedAcis = blockedAcis.map { (record, aci) -> SyncMessage.Blocked.BlockedAci(aciBinary = aci, timestamp = record.blockedAt) },
      blockedGroups = blockedGroups.map { (record, groupId) -> SyncMessage.Blocked.BlockedGroup(groupId = groupId, timestamp = record.blockedAt) }
    )

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncBlocked(blockedMessage),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error ->
        error.toSyncJobResult(TAG, "blocked list sync", defaultBackoff())
      }
    )
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceBlockedUpdateJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceBlockedUpdateJob {
      return MultiDeviceBlockedUpdateJob(parameters)
    }
  }
}

package org.thoughtcrime.securesms.jobs

import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.database.IdentityTable.VerifiedStatus
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.Verified
import java.io.IOException
import kotlin.time.Duration.Companion.days

class MultiDeviceVerifiedUpdateJob private constructor(
  parameters: Parameters,
  private val destination: RecipientId,
  private val identityKey: ByteArray,
  private val verifiedStatus: VerifiedStatus,
  private val timestamp: Long
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceVerifiedUpdateJob"

    private val TAG = Log.tag(MultiDeviceVerifiedUpdateJob::class.java)

    private const val KEY_DESTINATION = "destination"
    private const val KEY_IDENTITY_KEY = "identity_key"
    private const val KEY_VERIFIED_STATUS = "verified_status"
    private const val KEY_TIMESTAMP = "timestamp"
  }

  constructor(destination: RecipientId, identityKey: IdentityKey, verifiedStatus: VerifiedStatus) : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setQueue("__MULTI_DEVICE_VERIFIED_UPDATE__")
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    destination,
    identityKey.serialize(),
    verifiedStatus,
    System.currentTimeMillis()
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_DESTINATION, destination.serialize())
      .putString(KEY_IDENTITY_KEY, Base64.encodeWithPadding(identityKey))
      .putInt(KEY_VERIFIED_STATUS, verifiedStatus.toInt())
      .putLong(KEY_TIMESTAMP, timestamp)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!Recipient.self().isRegistered) {
      Log.w(TAG, "Not registered, aborting...")
      return Result.failure()
    }

    if (!SignalStore.account.isMultiDevice) {
      Log.i(TAG, "Not multi device...")
      return Result.success()
    }

    val recipient = Recipient.resolved(destination)

    if (recipient.isUnregistered) {
      Log.w(TAG, "${recipient.id} not registered!")
      return Result.failure()
    }

    val aci = recipient.aci.orNull()
    if (aci == null) {
      Log.w(TAG, "${recipient.id} has no ACI, skipping.")
      return Result.success()
    }

    val verifiedState = when (verifiedStatus) {
      VerifiedStatus.DEFAULT -> Verified.State.DEFAULT
      VerifiedStatus.VERIFIED -> Verified.State.VERIFIED
      VerifiedStatus.UNVERIFIED -> Verified.State.UNVERIFIED
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = timestamp,
      envelopeContent = OutgoingContent.syncVerified(aci, identityKey, verifiedState),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "verified sync", defaultBackoff()) }
    )
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<MultiDeviceVerifiedUpdateJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceVerifiedUpdateJob {
      val data = JsonJobData.deserialize(serializedData)

      try {
        val destination = RecipientId.from(data.getString(KEY_DESTINATION))
        val verifiedStatus = VerifiedStatus.forState(data.getInt(KEY_VERIFIED_STATUS))
        val timestamp = data.getLong(KEY_TIMESTAMP)
        val identityKey = Base64.decode(data.getString(KEY_IDENTITY_KEY))

        return MultiDeviceVerifiedUpdateJob(parameters, destination, identityKey, verifiedStatus, timestamp)
      } catch (e: IOException) {
        throw AssertionError(e)
      }
    }
  }
}

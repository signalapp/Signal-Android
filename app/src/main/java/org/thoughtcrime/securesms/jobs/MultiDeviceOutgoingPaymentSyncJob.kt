package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.util.UUID
import kotlin.time.Duration.Companion.days

/**
 * Tells a linked device about sent payments.
 */
class MultiDeviceOutgoingPaymentSyncJob private constructor(
  parameters: Parameters,
  private val uuid: UUID
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(MultiDeviceOutgoingPaymentSyncJob::class.java)

    const val KEY = "MultiDeviceOutgoingPaymentSyncJob"

    private const val KEY_UUID = "uuid"
  }

  constructor(sentPaymentId: UUID) : this(
    Parameters.Builder()
      .setQueue("MultiDeviceOutgoingPaymentSyncJob")
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
      .build(),
    sentPaymentId
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_UUID, uuid.toString())
      .serialize()
  }

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

    val payment = SignalDatabase.payments.getPayment(uuid)

    if (payment == null) {
      Log.w(TAG, "Payment not found $uuid")
      return Result.success()
    }

    val txoIdentification = payment.paymentMetaData.mobileCoinTxoIdentification!!
    val defrag = payment.isDefrag

    val recipientServiceId = if (!defrag && payment.payee.hasRecipientId()) {
      SignalDatabase.recipients.getRecord(payment.payee.requireRecipientId()).serviceId
    } else {
      null
    }

    val receipt = payment.receipt ?: throw AssertionError("Trying to sync payment before sent?")

    val outgoingPayment = SyncMessage.OutgoingPayment(
      recipientServiceId = recipientServiceId?.toString(),
      note = if (defrag) null else payment.note,
      mobileCoin = SyncMessage.OutgoingPayment.MobileCoin(
        recipientAddress = if (defrag) null else payment.payee.requirePublicAddress().serialize().toByteString(),
        amountPicoMob = payment.amount.requireMobileCoin().toPicoMobUint64(),
        feePicoMob = payment.fee.requireMobileCoin().toPicoMobUint64(),
        receipt = receipt.toByteString(),
        ledgerBlockTimestamp = payment.timestamp,
        ledgerBlockIndex = payment.blockIndex,
        outputPublicKeys = txoIdentification.publicKey,
        spentKeyImages = txoIdentification.keyImages
      )
    )

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncOutgoingPayment(outgoingPayment),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "outgoing payment sync", defaultBackoff()) }
    )
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to sync sent payment!")
  }

  class Factory : Job.Factory<MultiDeviceOutgoingPaymentSyncJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceOutgoingPaymentSyncJob {
      val data = JsonJobData.deserialize(serializedData)
      return MultiDeviceOutgoingPaymentSyncJob(parameters, UUID.fromString(data.getString(KEY_UUID)))
    }
  }
}

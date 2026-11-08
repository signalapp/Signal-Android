package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
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

class MultiDeviceConfigurationUpdateJob private constructor(
  parameters: Parameters,
  private val readReceiptsEnabled: Boolean,
  private val typingIndicatorsEnabled: Boolean,
  private val unidentifiedDeliveryIndicatorsEnabled: Boolean,
  private val linkPreviewsEnabled: Boolean
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "MultiDeviceConfigurationUpdateJob"
    const val QUEUE = "__MULTI_DEVICE_CONFIGURATION_UPDATE_JOB__"

    private val TAG = Log.tag(MultiDeviceConfigurationUpdateJob::class.java)

    private const val KEY_READ_RECEIPTS_ENABLED = "read_receipts_enabled"
    private const val KEY_TYPING_INDICATORS_ENABLED = "typing_indicators_enabled"
    private const val KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED = "unidentified_delivery_indicators_enabled"
    private const val KEY_LINK_PREVIEWS_ENABLED = "link_previews_enabled"
  }

  constructor(
    readReceiptsEnabled: Boolean,
    typingIndicatorsEnabled: Boolean,
    unidentifiedDeliveryIndicatorsEnabled: Boolean,
    linkPreviewsEnabled: Boolean
  ) : this(
    Parameters.Builder()
      .setQueue(QUEUE)
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setMaxAttempts(10)
      .build(),
    readReceiptsEnabled,
    typingIndicatorsEnabled,
    unidentifiedDeliveryIndicatorsEnabled,
    linkPreviewsEnabled
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putBoolean(KEY_READ_RECEIPTS_ENABLED, readReceiptsEnabled)
      .putBoolean(KEY_TYPING_INDICATORS_ENABLED, typingIndicatorsEnabled)
      .putBoolean(KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED, unidentifiedDeliveryIndicatorsEnabled)
      .putBoolean(KEY_LINK_PREVIEWS_ENABLED, linkPreviewsEnabled)
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

    if (SignalStore.account.isLinkedDevice) {
      Log.i(TAG, "Not primary device, aborting...")
      return Result.success()
    }

    val configuration = SyncMessage.Configuration(
      readReceipts = readReceiptsEnabled,
      unidentifiedDeliveryIndicators = unidentifiedDeliveryIndicatorsEnabled,
      typingIndicators = typingIndicatorsEnabled,
      linkPreviews = linkPreviewsEnabled
    )

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncConfiguration(configuration),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "configuration sync", defaultBackoff()) }
    )
  }

  override fun onFailure() {
    Log.w(TAG, "**** Failed to synchronize read receipts state!")
  }

  class Factory : Job.Factory<MultiDeviceConfigurationUpdateJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceConfigurationUpdateJob {
      val data = JsonJobData.deserialize(serializedData)

      return MultiDeviceConfigurationUpdateJob(
        parameters,
        data.getBooleanOrDefault(KEY_READ_RECEIPTS_ENABLED, false),
        data.getBooleanOrDefault(KEY_TYPING_INDICATORS_ENABLED, false),
        data.getBooleanOrDefault(KEY_UNIDENTIFIED_DELIVERY_INDICATORS_ENABLED, false),
        data.getBooleanOrDefault(KEY_LINK_PREVIEWS_ENABLED, false)
      )
    }
  }
}

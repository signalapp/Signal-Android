package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage
import kotlin.time.Duration.Companion.days

class MultiDeviceStickerPackOperationJob(
  parameters: Parameters,
  private val packId: String,
  private val packKey: String,
  private val type: Type
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(MultiDeviceStickerPackOperationJob::class.java)

    const val KEY = "MultiDeviceStickerPackOperationJob"

    private const val KEY_PACK_ID = "pack_id"
    private const val KEY_PACK_KEY = "pack_key"
    private const val KEY_TYPE = "type"
  }

  constructor(
    packId: String,
    packKey: String,
    type: Type
  ) : this(
    Parameters.Builder()
      .setQueue("MultiDeviceStickerPackOperationJob")
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
      .build(),
    packId,
    packKey,
    type
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_PACK_ID, packId)
      .putString(KEY_PACK_KEY, packKey)
      .putString(KEY_TYPE, type.name)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!SignalStore.account.isMultiDevice) {
      Log.i(TAG, "Not multi device, aborting...")
      return Result.success()
    }

    val remoteType = when (type) {
      Type.INSTALL -> SyncMessage.StickerPackOperation.Type.INSTALL
      Type.REMOVE -> SyncMessage.StickerPackOperation.Type.REMOVE
    }

    val stickerPackOperation = SyncMessage.StickerPackOperation(
      packId = Hex.fromStringCondensed(packId).toByteString(),
      packKey = Hex.fromStringCondensed(packKey).toByteString(),
      type = remoteType
    )

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncStickerPackOperations(listOf(stickerPackOperation)),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "sticker pack operation sync", defaultBackoff()) }
    )
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to sync sticker pack operation!")
  }

  /** NEVER rename these, they're persisted by name. */
  enum class Type {
    INSTALL,
    REMOVE
  }

  class Factory : Job.Factory<MultiDeviceStickerPackOperationJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceStickerPackOperationJob {
      val data = JsonJobData.deserialize(serializedData)

      return MultiDeviceStickerPackOperationJob(
        parameters,
        data.getString(KEY_PACK_ID),
        data.getString(KEY_PACK_KEY),
        Type.valueOf(data.getString(KEY_TYPE))
      )
    }
  }
}

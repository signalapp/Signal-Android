package org.thoughtcrime.securesms.jobs

import okio.ByteString.Companion.toByteString
import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.StickerTables.StickerPackRecordReader
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.OutgoingContent
import org.whispersystems.signalservice.internal.push.SyncMessage
import kotlin.time.Duration.Companion.days

/**
 * Tells linked devices about all installed sticker packs.
 */
class MultiDeviceStickerPackSyncJob(parameters: Parameters) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(MultiDeviceStickerPackSyncJob::class.java)

    const val KEY = "MultiDeviceStickerPackSyncJob"
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue("MultiDeviceStickerPackSyncJob")
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
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

    val operations = StickerPackRecordReader(SignalDatabase.stickers.getInstalledStickerPacks()).use { reader ->
      reader.asSequence()
        .map { pack ->
          SyncMessage.StickerPackOperation(
            packId = Hex.fromStringCondensed(pack.packId).toByteString(),
            packKey = Hex.fromStringCondensed(pack.packKey).toByteString(),
            type = SyncMessage.StickerPackOperation.Type.INSTALL
          )
        }
        .toList()
    }

    val result = SignalNetwork.messageService.sendSyncMessage(
      timestamp = System.currentTimeMillis(),
      envelopeContent = OutgoingContent.syncStickerPackOperations(operations),
      urgent = false,
      onEncrypted = null
    )

    return result.fold(
      ifRight = { Result.success() },
      ifLeft = { error -> error.toSyncJobResult(TAG, "sticker pack sync", defaultBackoff()) }
    )
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to sync sticker pack operation!")
  }

  class Factory : Job.Factory<MultiDeviceStickerPackSyncJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MultiDeviceStickerPackSyncJob {
      return MultiDeviceStickerPackSyncJob(parameters)
    }
  }
}

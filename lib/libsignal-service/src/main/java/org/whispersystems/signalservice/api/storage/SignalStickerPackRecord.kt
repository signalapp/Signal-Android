package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.StickerPackRecord
import java.io.IOException

/**
 * Wrapper around a [StickerPackRecord] to pair it with a [StorageId].
 */
data class SignalStickerPackRecord(
  override val id: StorageId,
  override val proto: StickerPackRecord
) : SignalRecord<StickerPackRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): StickerPackRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: StickerPackRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): StickerPackRecord.Builder {
      return try {
        StickerPackRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        StickerPackRecord.Builder()
      }
    }
  }
}

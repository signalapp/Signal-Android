package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.InstalledStickerPackRecord
import java.io.IOException

/**
 * Wrapper around a [InstalledStickerPackRecord] to pair it with a [StorageId].
 */
data class SignalInstalledStickerPackRecord(
  override val id: StorageId,
  override val proto: InstalledStickerPackRecord
) : SignalRecord<InstalledStickerPackRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): InstalledStickerPackRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: InstalledStickerPackRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): InstalledStickerPackRecord.Builder {
      return try {
        InstalledStickerPackRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        InstalledStickerPackRecord.Builder()
      }
    }
  }
}

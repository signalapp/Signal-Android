package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record
import java.io.IOException

/**
 * Wrapper around a [GroupV2Record] to pair it with a [StorageId].
 */
data class SignalGroupV2Record(
  override val id: StorageId,
  override val proto: GroupV2Record
) : SignalRecord<GroupV2Record> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): GroupV2Record.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: GroupV2Record.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): GroupV2Record.Builder {
      return try {
        GroupV2Record.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        GroupV2Record.Builder()
      }
    }
  }
}

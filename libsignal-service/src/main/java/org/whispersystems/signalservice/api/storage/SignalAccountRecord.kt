package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import java.io.IOException

/**
 * Wrapper around a [AccountRecord] to pair it with a [StorageId].
 */
data class SignalAccountRecord(
  override val id: StorageId,
  override val proto: AccountRecord
) : SignalRecord<AccountRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): AccountRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: AccountRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): AccountRecord.Builder {
      return try {
        AccountRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        AccountRecord.Builder()
      }
    }
  }
}

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import java.io.IOException

/**
 * Wrapper around a [ContactRecord] to pair it with a [StorageId].
 */
data class SignalContactRecord(
  override val id: StorageId,
  override val proto: ContactRecord
) : SignalRecord<ContactRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): ContactRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: ContactRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): ContactRecord.Builder {
      return try {
        ContactRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        ContactRecord.Builder()
      }
    }
  }
}

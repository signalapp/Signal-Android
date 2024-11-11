package org.whispersystems.signalservice.api.storage

import org.signal.core.util.hasUnknownFields
import org.signal.libsignal.protocol.logging.Log
import org.whispersystems.signalservice.internal.storage.protos.AccountRecord
import java.io.IOException

class SignalAccountRecord(
  override val id: StorageId,
  override val proto: AccountRecord
) : SignalRecord<AccountRecord> {

  companion object {
    private val TAG: String = SignalAccountRecord::class.java.simpleName

    fun newBuilder(serializedUnknowns: ByteArray?): AccountRecord.Builder {
      return if (serializedUnknowns != null) {
        parseUnknowns(serializedUnknowns)
      } else {
        AccountRecord.Builder()
      }
    }

    private fun parseUnknowns(serializedUnknowns: ByteArray): AccountRecord.Builder {
      try {
        return AccountRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        Log.w(TAG, "Failed to combine unknown fields!", e)
        return AccountRecord.Builder()
      }
    }
  }

  fun serializeUnknownFields(): ByteArray? {
    return if (proto.hasUnknownFields()) proto.encode() else null
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SignalAccountRecord

    if (id != other.id) return false
    if (proto != other.proto) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + proto.hashCode()
    return result
  }
}

package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.storage.protos.ChatFolderRecord
import java.io.IOException

/**
 * Wrapper around a [ChatFolderRecord] to pair it with a [StorageId].
 */
data class SignalChatFolderRecord(
  override val id: StorageId,
  override val proto: ChatFolderRecord
) : SignalRecord<ChatFolderRecord> {

  companion object {
    fun newBuilder(serializedUnknowns: ByteArray?): ChatFolderRecord.Builder {
      return serializedUnknowns?.let { builderFromUnknowns(it) } ?: ChatFolderRecord.Builder()
    }

    private fun builderFromUnknowns(serializedUnknowns: ByteArray): ChatFolderRecord.Builder {
      return try {
        ChatFolderRecord.ADAPTER.decode(serializedUnknowns).newBuilder()
      } catch (e: IOException) {
        ChatFolderRecord.Builder()
      }
    }
  }
}

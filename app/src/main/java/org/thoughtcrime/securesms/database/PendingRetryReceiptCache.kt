package org.thoughtcrime.securesms.database

import androidx.annotation.VisibleForTesting
import org.thoughtcrime.securesms.database.model.PendingRetryReceiptModel
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * A write-through cache for [PendingRetryReceiptTable].
 *
 * We have to read from this cache every time we process an incoming message. As a result, it's a very performance-sensitive operation.
 *
 * This cache is very similar to our job storage cache or our key-value store, in the sense that the first access of it will fetch all data from disk so all
 * future reads can happen in memory.
 */
class PendingRetryReceiptCache @VisibleForTesting constructor(
  private val database: PendingRetryReceiptTable = SignalDatabase.pendingRetryReceipts
) {

  private val pendingRetries: MutableMap<RemoteMessageId, PendingRetryReceiptModel> = HashMap()
  private var populated: Boolean = false

  fun insert(author: RecipientId, authorDevice: Int, sentTimestamp: Long, receivedTimestamp: Long, threadId: Long) {
    if (!RemoteConfig.retryReceipts) return
    ensurePopulated()
    val model: PendingRetryReceiptModel = database.insert(author, authorDevice, sentTimestamp, receivedTimestamp, threadId)
    synchronized(pendingRetries) {
      val key = RemoteMessageId(author, sentTimestamp)
      val existing: PendingRetryReceiptModel? = pendingRetries[key]

      // We rely on db unique constraint and auto-incrementing ids for conflict resolution here.
      if (existing == null || existing.id < model.id) {
        pendingRetries[key] = model
      }
    }
  }

  fun get(author: RecipientId, sentTimestamp: Long): PendingRetryReceiptModel? {
    if (!RemoteConfig.retryReceipts) return null
    ensurePopulated()

    synchronized(pendingRetries) {
      return pendingRetries[RemoteMessageId(author, sentTimestamp)]
    }
  }

  fun getOldest(): PendingRetryReceiptModel? {
    if (!RemoteConfig.retryReceipts) return null
    ensurePopulated()

    synchronized(pendingRetries) {
      return pendingRetries.values.minByOrNull { it.receivedTimestamp }
    }
  }

  fun delete(model: PendingRetryReceiptModel) {
    if (!RemoteConfig.retryReceipts) return
    ensurePopulated()

    synchronized(pendingRetries) {
      pendingRetries.remove(RemoteMessageId(model.author, model.sentTimestamp))
    }
    database.delete(model)
  }

  fun clear() {
    if (!RemoteConfig.retryReceipts) return

    synchronized(pendingRetries) {
      pendingRetries.clear()
      populated = false
    }
  }

  private fun ensurePopulated() {
    if (!populated) {
      synchronized(pendingRetries) {
        if (!populated) {
          database.all.forEach { model ->
            pendingRetries[RemoteMessageId(model.author, model.sentTimestamp)] = model
          }

          populated = true
        }
      }
    }
  }

  data class RemoteMessageId(val author: RecipientId, val sentTimestamp: Long)
}

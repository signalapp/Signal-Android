package org.thoughtcrime.securesms.util

import androidx.annotation.VisibleForTesting
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.mebiBytes
import org.thoughtcrime.securesms.database.model.ServiceMessageId
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope

/**
 * Sometimes a message that is referencing another message can arrive out of order. In these cases,
 * we want to temporarily hold on (i.e. keep a memory cache) to these messages and apply them after
 * we receive the referenced message.
 */
class EarlyMessageCache @VisibleForTesting constructor(private val maxBytes: Long) {

  companion object {
    private const val MAX_KEYS = 100
    private val DEFAULT_MAX_BYTES = 5.mebiBytes.inWholeBytes
  }

  constructor() : this(DEFAULT_MAX_BYTES)

  private val cache = LinkedHashMap<ServiceMessageId, Bucket>(MAX_KEYS / 2, 0.75f, true)
  private var totalBytes = 0L

  /**
   * Returns a snapshot of all of the [ServiceMessageId]s referenced in the cache at the moment of inquiry.
   * There is no guarantee that this will be relevant for any amount of time afterwards.
   */
  val allReferencedIds: Set<ServiceMessageId>
    @Synchronized get() = cache.keys.toSet()

  /**
   * @param targetSender The sender of the message this message depends on.
   * @param targetSentTimestamp The sent timestamp of the message this message depends on.
   */
  @Synchronized
  fun store(targetSender: RecipientId, targetSentTimestamp: Long, cacheEntry: EarlyMessageCacheEntry) {
    val bucket = cache.getOrPut(ServiceMessageId(targetSender, targetSentTimestamp)) { Bucket() }
    val size = cacheEntry.estimateSize()

    bucket.entries += cacheEntry
    bucket.bytes += size
    totalBytes += size

    trim()
  }

  /**
   * Returns and removes any content that is dependent on the provided message id.
   *
   * @param sender The sender of the message in question.
   * @param sentTimestamp The sent timestamp of the message in question.
   */
  @Synchronized
  fun retrieve(sender: RecipientId, sentTimestamp: Long): List<EarlyMessageCacheEntry>? {
    val bucket = cache.remove(ServiceMessageId(sender, sentTimestamp)) ?: return null
    totalBytes -= bucket.bytes
    return bucket.entries
  }

  private fun trim() {
    val iterator = cache.values.iterator()

    while (iterator.hasNext() && (cache.size > MAX_KEYS || totalBytes > maxBytes)) {
      totalBytes -= iterator.next().bytes
      iterator.remove()
    }
  }

  private fun EarlyMessageCacheEntry.estimateSize(): Long {
    return Envelope.ADAPTER.encodedSize(envelope).toLong() + Content.ADAPTER.encodedSize(content).toLong()
  }

  private class Bucket {
    val entries: MutableList<EarlyMessageCacheEntry> = mutableListOf()
    var bytes: Long = 0
  }
}

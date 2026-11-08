package org.thoughtcrime.securesms.util

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.mockk
import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.database.model.ServiceMessageId
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.Envelope

class EarlyMessageCacheTest {

  private val sender = RecipientId.from(1)

  @Test
  fun `retrieve returns stored entries and removes them`() {
    val cache = EarlyMessageCache()
    cache.store(sender, 1, entry(10))
    cache.store(sender, 1, entry(10))

    assertThat(cache.retrieve(sender, 1)).isNotNull().hasSize(2)
    assertThat(cache.retrieve(sender, 1)).isNull()
  }

  @Test
  fun `a key is evicted entirely when its entries exceed the byte cap`() {
    val cache = EarlyMessageCache(1_000)

    cache.store(sender, 1, entry(400))
    cache.store(sender, 1, entry(400))
    cache.store(sender, 1, entry(400))

    assertThat(cache.retrieve(sender, 1)).isNull()

    cache.store(sender, 1, entry(400))

    assertThat(cache.retrieve(sender, 1)).isNotNull().hasSize(1)
  }

  @Test
  fun `least recently used keys are evicted first when over the byte cap`() {
    val cache = EarlyMessageCache(1_000)

    cache.store(sender, 1, entry(400))
    cache.store(sender, 2, entry(400))
    cache.store(sender, 3, entry(400))

    assertThat(cache.allReferencedIds).containsExactlyInAnyOrder(ServiceMessageId(sender, 2), ServiceMessageId(sender, 3))
  }

  @Test
  fun `retrieving frees space for new entries`() {
    val cache = EarlyMessageCache(1_000)

    cache.store(sender, 1, entry(400))
    cache.store(sender, 2, entry(400))
    cache.retrieve(sender, 1)
    cache.retrieve(sender, 2)
    cache.store(sender, 3, entry(400))
    cache.store(sender, 4, entry(400))

    assertThat(cache.allReferencedIds).containsExactlyInAnyOrder(ServiceMessageId(sender, 3), ServiceMessageId(sender, 4))
  }

  @Test
  fun `key count is still capped`() {
    val cache = EarlyMessageCache()

    repeat(150) { cache.store(sender, it.toLong(), entry(1)) }

    assertThat(cache.allReferencedIds).hasSize(100)
    assertThat(cache.retrieve(sender, 0)).isNull()
    assertThat(cache.retrieve(sender, 149)).isNotNull()
  }

  @Test
  fun `empty cache has no ids`() {
    assertThat(EarlyMessageCache().allReferencedIds).isEmpty()
  }

  private fun entry(approxBytes: Int): EarlyMessageCacheEntry {
    return EarlyMessageCacheEntry(
      envelope = Envelope(content = ByteArray(approxBytes / 2).toByteString()),
      content = Content(senderKeyDistributionMessage = ByteArray(approxBytes / 2).toByteString()),
      metadata = mockk(),
      serverDeliveredTimestamp = 0
    )
  }
}

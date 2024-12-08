package org.thoughtcrime.securesms.recipients

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.LogRecorder
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.Optional
import java.util.UUID

class RecipientIdCacheTest {
  private val recipientIdCache = RecipientIdCache(TEST_CACHE_LIMIT)
  private val logRecorder = LogRecorder().apply { Log.initialize(this) }

  @Test
  fun empty_access_by_nulls() {
    val recipientId = recipientIdCache[null, null]

    assertNull(recipientId)
  }

  @Test
  fun empty_access_by_uuid() {
    val recipientId = recipientIdCache[randomAci(), null]

    assertNull(recipientId)
  }

  @Test
  fun empty_access_by_e164() {
    val recipientId = recipientIdCache[null, "+155512345"]

    assertNull(recipientId)
  }

  @Test
  fun cache_hit_by_uuid() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()

    recipientIdCache.put(recipient(recipientId1, sid1, null))

    val recipientId = recipientIdCache[sid1, null]

    assertEquals(recipientId1, recipientId)
  }

  @Test
  fun cache_miss_by_uuid() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()
    val sid2 = randomAci()

    recipientIdCache.put(recipient(recipientId1, sid1, null))

    val recipientId = recipientIdCache[sid2, null]

    assertNull(recipientId)
  }

  @Test
  fun cache_hit_by_uuid_e164_not_supplied_on_get() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()

    recipientIdCache.put(recipient(recipientId1, sid1, "+15551234567"))

    val recipientId = recipientIdCache[sid1, null]

    assertEquals(recipientId1, recipientId)
  }

  @Test
  fun cache_miss_by_uuid_e164_not_supplied_on_put() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()

    recipientIdCache.put(recipient(recipientId1, sid1, null))

    val recipientId = recipientIdCache[sid1, "+15551234567"]

    assertNull(recipientId)
  }

  @Test
  fun cache_hit_by_e164() {
    val recipientId1 = recipientId()
    val e164 = "+1555123456"

    recipientIdCache.put(recipient(recipientId1, null, e164))

    val recipientId = recipientIdCache[null, e164]

    assertEquals(recipientId1, recipientId)
  }

  @Test
  fun cache_miss_by_e164() {
    val recipientId1 = recipientId()
    val e164a = "+1555123456"
    val e164b = "+1555123457"

    recipientIdCache.put(recipient(recipientId1, null, e164a))

    val recipientId = recipientIdCache[null, e164b]

    assertNull(recipientId)
  }

  @Test
  fun cache_hit_by_e164_uuid_not_supplied_on_get() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()

    recipientIdCache.put(recipient(recipientId1, sid1, "+15551234567"))

    val recipientId = recipientIdCache[null, "+15551234567"]

    assertEquals(recipientId1, recipientId)
  }

  @Test
  fun cache_miss_by_e164_uuid_not_supplied_on_put() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()
    val e164 = "+1555123456"

    recipientIdCache.put(recipient(recipientId1, null, e164))

    val recipientId = recipientIdCache[sid1, e164]

    assertNull(recipientId)
  }

  @Test
  fun cache_hit_by_both() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()
    val e164 = "+1555123456"

    recipientIdCache.put(recipient(recipientId1, sid1, e164))

    val recipientId = recipientIdCache[sid1, e164]

    assertEquals(recipientId1, recipientId)
  }

  @Test
  fun full_recipient_id_learned_by_two_puts() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()
    val e164 = "+1555123456"

    recipientIdCache.put(recipient(recipientId1, sid1, null))
    recipientIdCache.put(recipient(recipientId1, null, e164))

    val recipientId = recipientIdCache[sid1, e164]

    assertEquals(recipientId1, recipientId)
  }

  @Test
  fun if_cache_state_disagrees_returns_null() {
    val recipientId1 = recipientId()
    val recipientId2 = recipientId()
    val sid = randomAci()
    val e164 = "+1555123456"

    recipientIdCache.put(recipient(recipientId1, null, e164))
    recipientIdCache.put(recipient(recipientId2, sid, null))

    val recipientId = recipientIdCache[sid, e164]

    assertNull(recipientId)

    assertEquals(1, logRecorder.warnings.size)
    assertEquals("Seen invalid RecipientIdCacheState", logRecorder.warnings.single().message)
  }

  @Test
  fun after_invalid_cache_hit_entries_are_cleared_up() {
    val recipientId1 = recipientId()
    val recipientId2 = recipientId()
    val sid = randomAci()
    val e164 = "+1555123456"

    recipientIdCache.put(recipient(recipientId1, null, e164))
    recipientIdCache.put(recipient(recipientId2, sid, null))

    recipientIdCache[sid, e164]

    assertNull(recipientIdCache[sid, null])
    assertNull(recipientIdCache[null, e164])
  }

  @Test
  fun multiple_entries() {
    val recipientId1 = recipientId()
    val recipientId2 = recipientId()
    val sid1 = randomAci()
    val sid2 = randomAci()

    recipientIdCache.put(recipient(recipientId1, sid1, null))
    recipientIdCache.put(recipient(recipientId2, sid2, null))

    assertEquals(recipientId1, recipientIdCache[sid1, null])
    assertEquals(recipientId2, recipientIdCache[sid2, null])
  }

  @Test
  fun drops_oldest_when_reaches_cache_limit() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()

    recipientIdCache.put(recipient(recipientId1, sid1, null))

    for (i in 0 until TEST_CACHE_LIMIT) {
      recipientIdCache.put(recipient(recipientId(), randomAci(), null))
    }

    assertNull(recipientIdCache[sid1, null])
  }

  @Test
  fun remains_in_cache_when_used_before_reaching_cache_limit() {
    val recipientId1 = recipientId()
    val sid1 = randomAci()

    recipientIdCache.put(recipient(recipientId1, sid1, null))

    for (i in 0 until TEST_CACHE_LIMIT - 1) {
      recipientIdCache.put(recipient(recipientId(), randomAci(), null))
    }

    assertEquals(recipientId1, recipientIdCache[sid1, null])

    recipientIdCache.put(recipient(recipientId(), randomAci(), null))

    assertEquals(recipientId1, recipientIdCache[sid1, null])
  }

  companion object {
    private const val TEST_CACHE_LIMIT = 5

    private fun recipientId(): RecipientId {
      return mockk<RecipientId>()
    }

    private fun randomAci(): ServiceId {
      return ServiceId.ACI.from(UUID.randomUUID())
    }

    private fun recipient(recipientId: RecipientId, serviceId: ServiceId?, e164: String?): Recipient {
      return mockk<Recipient> {
        every { this@mockk.id } returns recipientId
        every { this@mockk.serviceId } returns Optional.ofNullable(serviceId)
        every { this@mockk.e164 } returns Optional.ofNullable(e164)
      }
    }
  }
}

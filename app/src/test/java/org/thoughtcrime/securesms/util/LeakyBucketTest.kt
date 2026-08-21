/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class LeakyBucketTest {

  companion object {
    private val NOW = 1_700_000_000_000L.milliseconds
    private val DRIP = 15.minutes
  }

  private val state = LeakyBucket.InMemoryState()
  private val bucket = LeakyBucket(capacity = 3, dripInterval = DRIP, state = state)

  @Test
  fun `starts empty`() {
    assertEquals(0, bucket.level(NOW))
    assertTrue(bucket.hasRoom(NOW))
  }

  @Test
  fun `fills to capacity and then has no room`() {
    repeat(3) {
      assertTrue(bucket.hasRoom(NOW))
      bucket.use(NOW)
    }

    assertEquals(3, bucket.level(NOW))
    assertFalse(bucket.hasRoom(NOW))
  }

  @Test
  fun `drips one level per interval`() {
    fill()

    assertEquals(2, bucket.level(NOW + DRIP))
    assertTrue(bucket.hasRoom(NOW + DRIP))
  }

  @Test
  fun `drips several levels at once when enough time has passed`() {
    fill()

    assertEquals(1, bucket.level(NOW + DRIP * 2))
    assertEquals(0, bucket.level(NOW + DRIP * 3))
  }

  @Test
  fun `never drips below empty`() {
    fill()

    assertEquals(0, bucket.level(NOW + DRIP * 500))
  }

  @Test
  fun `does not drip for a partially elapsed interval`() {
    fill()

    assertEquals(3, bucket.level(NOW + DRIP - 1.milliseconds))
  }

  @Test
  fun `carries a partially elapsed interval toward the next drip`() {
    fill()

    // A drip and most of a second one. Using the bucket banks the drip and keeps the remainder.
    bucket.use(NOW + DRIP + 14.minutes)

    assertEquals(NOW.inWholeMilliseconds + DRIP.inWholeMilliseconds, state.levelUpdatedAt)
    assertEquals(3, state.level)

    // One more minute crosses the second interval, rather than restarting from the last use.
    assertEquals(2, bucket.level(NOW + DRIP + 15.minutes))
  }

  @Test
  fun `reading the level does not record the drip`() {
    fill()

    bucket.level(NOW + DRIP)

    assertEquals(3, state.level)
    assertEquals(NOW.inWholeMilliseconds, state.levelUpdatedAt)
  }

  @Test
  fun `using the bucket records the drip`() {
    fill()

    bucket.use(NOW + DRIP)

    assertEquals(3, state.level)
    assertEquals(NOW.inWholeMilliseconds + DRIP.inWholeMilliseconds, state.levelUpdatedAt)
  }

  @Test
  fun `empties rather than wedging when the clock moves backwards`() {
    fill()

    assertEquals(0, bucket.level(NOW - 5.hours))
    assertTrue(bucket.hasRoom(NOW - 5.hours))
  }

  @Test
  fun `throttles again after a backwards clock use`() {
    fill()

    val past = NOW - 5.hours

    repeat(3) {
      assertTrue(bucket.hasRoom(past))
      bucket.use(past)
    }

    assertFalse(bucket.hasRoom(past))
  }

  @Test
  fun `clear empties the bucket`() {
    fill()

    bucket.clear()

    assertEquals(0, bucket.level(NOW))
    assertEquals(0, state.level)
  }

  @Test
  fun `fills when uses outpace the drip rate`() {
    var now = NOW

    // Twice the drip rate, so the level nets up one per interval and a capacity of three fills on the fifth use.
    repeat(5) {
      assertTrue(bucket.hasRoom(now))
      bucket.use(now)
      now += DRIP / 2
    }

    assertFalse(bucket.hasRoom(now))
  }

  @Test
  fun `never fills when uses match the drip rate`() {
    var now = NOW

    repeat(50) {
      assertTrue(bucket.hasRoom(now))
      bucket.use(now)
      now += DRIP
    }
  }

  @Test
  fun `reads its level from the state it was given`() {
    state.update(level = 2, levelAsOf = NOW.inWholeMilliseconds)

    assertEquals(2, bucket.level(NOW))
    assertTrue(bucket.hasRoom(NOW))

    bucket.use(NOW)

    assertFalse(bucket.hasRoom(NOW))
  }

  private fun fill(now: Duration = NOW) {
    repeat(3) {
      bucket.use(now)
    }
  }
}

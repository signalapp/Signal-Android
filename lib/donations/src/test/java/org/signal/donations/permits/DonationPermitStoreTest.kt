/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations.permits

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DonationPermitStoreTest {

  private val now = Instant.ofEpochSecond(1_700_000_000)
  private val store = DonationPermitStore
  private val server = TestDonationPermitServer()

  @Before
  fun setUp() {
    store.clear()
  }

  @After
  fun tearDown() {
    store.clear()
  }

  @Test
  fun `take returns permits in FIFO order then null`() {
    val permits = server.mint(3, now)
    store.addAll(permits)

    assertArrayEquals(permits[0].serialize(), store.take(now)!!.serialize())
    assertArrayEquals(permits[1].serialize(), store.take(now)!!.serialize())
    assertArrayEquals(permits[2].serialize(), store.take(now)!!.serialize())
    assertNull(store.take(now))
  }

  @Test
  fun `take drops expired permits and returns the next valid one`() {
    val early = server.mint(1, now)
    val later = server.mint(1, now.plus(7, ChronoUnit.DAYS))
    store.addAll(early + later)

    val afterEarlyExpiry = early.single().expiration.plusSeconds(1)
    assertArrayEquals(later.single().serialize(), store.take(afterEarlyExpiry)!!.serialize())
    assertNull(store.take(afterEarlyExpiry))
  }

  @Test
  fun `size counts only unexpired permits`() {
    val permits = server.mint(2, now)
    store.addAll(permits)

    assertEquals(2, store.size(now))
    assertEquals(0, store.size(permits.first().expiration))
  }

  @Test
  fun `clear removes all permits`() {
    store.addAll(server.mint(2, now))

    store.clear()

    assertEquals(0, store.size(now))
    assertNull(store.take(now))
  }

  @Test
  fun `addAllIfGeneration stores permits when the generation is unchanged`() {
    val generation = store.generation()

    assertTrue(store.addAllIfGeneration(generation, server.mint(2, now)))
    assertEquals(2, store.size(now))
  }

  @Test
  fun `addAllIfGeneration discards permits when a clear changed the generation`() {
    val generation = store.generation()
    store.clear()

    assertFalse(store.addAllIfGeneration(generation, server.mint(2, now)))
    assertEquals(0, store.size(now))
  }

  @Test
  fun `clear advances the generation`() {
    val generation = store.generation()

    store.clear()

    assertFalse(generation == store.generation())
  }

  @Test
  fun `permit is expired at its expiration instant`() {
    val permit = server.mint(1, now).single()
    store.addAll(listOf(permit))

    assertNull(store.take(permit.expiration))
  }
}

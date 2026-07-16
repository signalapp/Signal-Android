/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations.permits

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class DonationPermitsRepositoryTest {

  private val now = Instant.ofEpochSecond(1_700_000_000)
  private val server = TestDonationPermitServer()
  private val issuer = TestDonationPermitIssuer(server, now)
  private val repository = DonationPermitsRepository(issuer, server.publicParams)

  @Before
  fun setUp() {
    DonationPermitStore.clear()
  }

  @After
  fun tearDown() {
    DonationPermitStore.clear()
  }

  @Test
  fun `acquirePermits stores the requested number of permits`() {
    repository.acquirePermits(3, now)

    assertEquals(3, repository.availablePermitCount(now))
    assertEquals(1, issuer.issueCount)
  }

  @Test
  fun `acquirePermits with non-positive count throws and does not contact the issuer`() {
    assertThrows(IllegalArgumentException::class.java) { repository.acquirePermits(0, now) }
    assertEquals(0, issuer.issueCount)
  }

  @Test
  fun `spendOrAcquirePermit returns an error when the issuer fails`() {
    issuer.error = DonationPermitError.IssuerUnavailable(statusCode = 429)

    assertTrue(repository.spendOrAcquirePermit(now).isLeft())
    assertEquals(0, repository.availablePermitCount(now))
  }

  @Test
  fun `spendOrAcquirePermit returns an error when verification fails`() {
    val pinnedToDifferentServer = DonationPermitsRepository(issuer, TestDonationPermitServer().publicParams)

    assertTrue(pinnedToDifferentServer.spendOrAcquirePermit(now).isLeft())
    assertEquals(0, repository.availablePermitCount(now))
  }

  @Test
  fun `spendOrAcquirePermit returns an error when the response is malformed`() {
    issuer.returnMalformed = true

    assertTrue(repository.spendOrAcquirePermit(now).isLeft())
    assertEquals(0, repository.availablePermitCount(now))
  }

  @Test
  fun `spendPermit returns distinct permits then null`() {
    repository.acquirePermits(2, now)

    val first = repository.spendPermit(now)
    assertNotNull(first)
    assertEquals(1, repository.availablePermitCount(now))

    val second = repository.spendPermit(now)
    assertNotNull(second)
    assertEquals(0, repository.availablePermitCount(now))

    assertFalse(first!!.serialize().contentEquals(second!!.serialize()))
    assertNull(repository.spendPermit(now))
  }

  @Test
  fun `spendPermit skips expired permits`() {
    repository.acquirePermits(2, now)

    val afterExpiry = now.plus(30, ChronoUnit.DAYS)
    assertNull(repository.spendPermit(afterExpiry))
    assertEquals(0, repository.availablePermitCount(afterExpiry))
  }

  @Test
  fun `spendOrAcquirePermit returns a stored permit without contacting the issuer`() {
    repository.acquirePermits(2, now)
    assertEquals(1, issuer.issueCount)

    assertNotNull(repository.spendOrAcquirePermit(now).getOrNull())
    assertEquals(1, issuer.issueCount)
    assertEquals(1, repository.availablePermitCount(now))
  }

  @Test
  fun `spendOrAcquirePermit acquires a batch when none are stored`() {
    assertNotNull(repository.spendOrAcquirePermit(now).getOrNull())
    assertEquals(1, issuer.issueCount)
    assertEquals(DonationPermitsRepository.DEFAULT_PERMIT_BATCH - 1, repository.availablePermitCount(now))
  }

  @Test
  fun `clearPermits is not blocked while an acquire network call is in flight`() {
    val cleared = CountDownLatch(1)
    issuer.onIssue = {
      // Simulate logout landing on another thread while the issuer request is in flight.
      thread {
        repository.clearPermits()
        cleared.countDown()
      }
      // If a lock were held across the network call, this clear would block until issue() returns.
      assertTrue("clearPermits() blocked during an in-flight acquire", cleared.await(5, TimeUnit.SECONDS))
    }

    val result = repository.spendOrAcquirePermit(now)

    assertTrue(result.isLeft())
    assertEquals(0, repository.availablePermitCount(now))
  }

  @Test
  fun `clearPermits drops all held permits`() {
    repository.acquirePermits(3, now)
    assertEquals(3, repository.availablePermitCount(now))

    repository.clearPermits()

    assertEquals(0, repository.availablePermitCount(now))
  }

  @Test
  fun `default batch size is used when count omitted`() {
    repository.acquirePermits(now = now)

    assertEquals(DonationPermitsRepository.DEFAULT_PERMIT_BATCH, repository.availablePermitCount(now))
  }
}

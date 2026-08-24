/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.Util
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.StorageSyncLoopState
import org.thoughtcrime.securesms.storage.StorageSyncHelper.WriteOperationResult
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import org.thoughtcrime.securesms.testutil.TestHelpers
import org.whispersystems.signalservice.api.storage.SignalStorageManifest
import org.whispersystems.signalservice.api.storage.SignalStorageRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import org.whispersystems.signalservice.internal.storage.protos.StorageRecord
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StorageSyncLoopDetectorTest {

  companion object {
    private val NOW = 1_700_000_000_000L.milliseconds
  }

  @get:Rule
  val signalStore = SignalStoreRule()

  @Before
  fun setup() {
    SignalStore.storageService.syncLoopState = StorageSyncLoopState()

    // Throttling only applies when another device could be undoing our writes.
    SignalStore.account.isMultiDevice = true
  }

  @Test
  fun `given no linked devices, when I repeat the same write, then I expect it to always be allowed`() {
    SignalStore.account.isMultiDevice = false

    repeat(20) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1)))
    }
  }

  @Test
  fun `given no linked devices, when I repeat the same write, then I expect nothing to be charged`() {
    SignalStore.account.isMultiDevice = false

    repeat(20) { attempt(writeOf(1)) }

    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
    assertEquals(0, SignalStore.storageService.syncLoopState.rateLevel)
  }

  @Test
  fun `given a write that failed, when I refund it, then I expect the charge to come back`() {
    attempt(writeOf(1))
    attempt(writeOf(1))
    val chargedLevel = SignalStore.storageService.syncLoopState.contentLevel

    StorageSyncLoopDetector.onWriteFailed(NOW)

    assertEquals(chargedLevel - 1, SignalStore.storageService.syncLoopState.contentLevel)
  }

  @Test
  fun `given writes that keep failing, when I retry forever, then I expect to never be throttled`() {
    repeat(20) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1)))
      StorageSyncLoopDetector.onWriteFailed(NOW)
    }

    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
    assertEquals(0, SignalStore.storageService.syncLoopState.rateLevel)
  }

  @Test
  fun `given nothing charged, when I refund, then I expect the level to stay at zero`() {
    StorageSyncLoopDetector.onWriteFailed(NOW)

    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
    assertEquals(0, SignalStore.storageService.syncLoopState.rateLevel)
  }

  @Test
  fun `allows a write nothing like the ones before it`() {
    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1)))
  }

  @Test
  fun `never charges the content bucket for novel payloads`() {
    repeat(50) { index ->
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(index), fetchedRemoteManifest = false))
    }

    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
  }

  @Test
  fun `charges the content bucket for a repeated payload and denies once it is dry`() {
    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1)))

    repeat(3) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1)))
    }

    assertEquals(
      StorageSyncLoopDetector.Decision.Denied(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, 3),
      attempt(writeOf(1))
    )
  }

  @Test
  fun `compares against the last three payloads, not just the previous one`() {
    attempt(writeOf(1))
    attempt(writeOf(2))
    attempt(writeOf(3))

    repeat(3) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1)))
    }

    assertEquals(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, denialCause(attempt(writeOf(1))))
  }

  @Test
  fun `forgets a payload once three newer ones have been written`() {
    attempt(writeOf(1))
    attempt(writeOf(2))
    attempt(writeOf(3))
    attempt(writeOf(4))

    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1)))
    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
  }

  @Test
  fun `restores one content permit per hour`() {
    exhaustContentBucket()

    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), now = NOW + 1.hours))
    assertEquals(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, denialCause(attempt(writeOf(1), now = NOW + 1.hours)))
  }

  @Test
  fun `restores several content permits at once when enough time has passed`() {
    exhaustContentBucket()

    repeat(3) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), now = NOW + 3.hours))
    }

    assertEquals(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, denialCause(attempt(writeOf(1), now = NOW + 3.hours)))
  }

  @Test
  fun `never restores more permits than the bucket holds`() {
    exhaustContentBucket()

    repeat(3) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), now = NOW + 500.hours))
    }

    assertEquals(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, denialCause(attempt(writeOf(1), now = NOW + 500.hours)))
  }

  @Test
  fun `refills rather than wedging when the clock moves backwards`() {
    exhaustContentBucket()

    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), now = NOW - 5.hours))
  }

  /** The rate bucket has no convergence reset, so a wedge here would deny manifest-fetching writes indefinitely. */
  @Test
  fun `refills the rate bucket when the clock moves backwards`() {
    exhaustRateBucket()

    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(100), now = NOW - 5.hours))
  }

  @Test
  fun `recovers normal throttling after a backwards clock write`() {
    exhaustContentBucket()

    val past = NOW - 5.hours

    repeat(3) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), now = past))
    }

    assertEquals(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, denialCause(attempt(writeOf(1), now = past)))
  }

  /** A partially elapsed interval carries toward the next drip instead of restarting, unlike a strike counter. */
  @Test
  fun `keeps a partially elapsed interval toward the next drip`() {
    exhaustContentBucket()

    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), now = NOW + 1.hours + 59.minutes))
    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), now = NOW + 2.hours + 1.minutes))
  }

  /** Twice the drip rate accumulates a level of one per hour, which a strike counter reset by any gap would not. */
  @Test
  fun `fills the bucket when writes outpace the drip rate`() {
    repeat(6) { index ->
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), now = NOW + (index * 30).minutes))
    }

    assertEquals(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, denialCause(attempt(writeOf(1), now = NOW + 180.minutes)))
  }

  @Test
  fun `does not credit the same elapsed time twice`() {
    exhaustContentBucket()

    repeat(4) {
      assertEquals(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, denialCause(attempt(writeOf(1), now = NOW + 59.minutes)))
    }
  }

  @Test
  fun `never charges the content bucket for a delete-only write`() {
    repeat(30) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(deletes = intArrayOf(1)), fetchedRemoteManifest = false))
    }

    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
  }

  @Test
  fun `never charges the content bucket for a repeated payload when the local manifest was reused`() {
    repeat(30) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), fetchedRemoteManifest = false))
    }

    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
  }

  /** Toggling a setting back and forth reproduces a previous payload, but it is a genuine local change rather than a loop. */
  @Test
  fun `never throttles a setting toggled back and forth`() {
    repeat(20) { index ->
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(index % 2), fetchedRemoteManifest = false))
    }

    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
  }

  /** Local writes still populate the history, so a peer that starts undoing them is caught on the first repeat we see. */
  @Test
  fun `builds fingerprint history from writes that reused the local manifest`() {
    repeat(4) {
      attempt(writeOf(1), fetchedRemoteManifest = false)
    }

    repeat(3) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1)))
    }

    assertEquals(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, denialCause(attempt(writeOf(1))))
  }

  @Test
  fun `does not charge the rate bucket when the local manifest was reused`() {
    repeat(50) { index ->
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(index), fetchedRemoteManifest = false))
    }

    assertEquals(0, SignalStore.storageService.syncLoopState.rateLevel)
  }

  @Test
  fun `charges the rate bucket for every write on a manifest-fetching run`() {
    repeat(100) { index ->
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(index)))
    }

    assertEquals(100, SignalStore.storageService.syncLoopState.rateLevel)
    assertEquals(StorageSyncLoopDetector.Cause.WRITE_RATE, denialCause(attempt(writeOf(999))))
  }

  /** The rate bucket is content-blind, so a run of delete-only writes drains it just the same. */
  @Test
  fun `charges the rate bucket for delete-only writes too`() {
    repeat(100) { index ->
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(deletes = intArrayOf(index))))
    }

    assertEquals(StorageSyncLoopDetector.Cause.WRITE_RATE, denialCause(attempt(writeOf(deletes = intArrayOf(99)))))
  }

  @Test
  fun `drips one rate level every ten minutes`() {
    exhaustRateBucket()

    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1000), now = NOW + 10.minutes))
    assertEquals(StorageSyncLoopDetector.Cause.WRITE_RATE, denialCause(attempt(writeOf(1001), now = NOW + 10.minutes)))
  }

  @Test
  fun `converging resets the content bucket`() {
    exhaustContentBucket()

    StorageSyncLoopDetector.onConverged()

    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
  }

  @Test
  fun `converging leaves the rate bucket alone`() {
    exhaustRateBucket()

    StorageSyncLoopDetector.onConverged()

    assertEquals(100, SignalStore.storageService.syncLoopState.rateLevel)
    assertEquals(StorageSyncLoopDetector.Cause.WRITE_RATE, denialCause(attempt(writeOf(999))))
  }

  @Test
  fun `converging lets a throttled write through again`() {
    exhaustContentBucket()
    check(attempt(writeOf(1)) is StorageSyncLoopDetector.Decision.Denied) { "Should be throttled before converging!" }

    StorageSyncLoopDetector.onConverged()

    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1)))
  }

  @Test
  fun `persists bucket state so it survives a process restart`() {
    exhaustContentBucket()

    assertEquals(3, SignalStore.storageService.syncLoopState.contentLevel)
    assertEquals(NOW.inWholeMilliseconds, SignalStore.storageService.syncLoopState.contentLevelAsOf)
  }

  /** Spends every content permit, leaving [writeOf] 1 as a repeat that needs one. */
  private fun exhaustContentBucket() {
    repeat(4) {
      check(attempt(writeOf(1)) is StorageSyncLoopDetector.Decision.Allowed) { "Content bucket drained early!" }
    }
  }

  @Test
  fun `never charges a retry`() {
    repeat(50) {
      assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), isRetry = true))
    }

    assertEquals(0, SignalStore.storageService.syncLoopState.contentLevel)
    assertEquals(0, SignalStore.storageService.syncLoopState.rateLevel)
  }

  @Test
  fun `a retry does not build up fingerprint history`() {
    repeat(5) {
      attempt(writeOf(1), isRetry = true)
    }

    assertEquals(emptyList<Int>(), SignalStore.storageService.syncLoopState.recentFingerprints)
  }

  @Test
  fun `allows a retry of a repeated payload even when the content bucket is full`() {
    repeat(4) {
      attempt(writeOf(1))
    }

    assertEquals(StorageSyncLoopDetector.Cause.REPEATED_PAYLOAD, denialCause(attempt(writeOf(1))))
    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(1), isRetry = true))
  }

  @Test
  fun `allows a retry even when the rate bucket is exhausted`() {
    exhaustRateBucket()

    assertEquals(StorageSyncLoopDetector.Cause.WRITE_RATE, denialCause(attempt(writeOf(500))))
    assertEquals(StorageSyncLoopDetector.Decision.Allowed, attempt(writeOf(500), isRetry = true))
  }

  private fun exhaustRateBucket() {
    repeat(100) { index ->
      check(attempt(writeOf(index)) is StorageSyncLoopDetector.Decision.Allowed) { "Rate bucket drained early!" }
    }
  }

  private fun attempt(
    write: WriteOperationResult,
    fetchedRemoteManifest: Boolean = true,
    isRetry: Boolean = false,
    now: Duration = NOW
  ): StorageSyncLoopDetector.Decision {
    return StorageSyncLoopDetector.onWriteAttempt(write, fetchedRemoteManifest, isRetry, now)
  }

  private fun denial(decision: StorageSyncLoopDetector.Decision): StorageSyncLoopDetector.Decision.Denied {
    return decision as? StorageSyncLoopDetector.Decision.Denied ?: throw AssertionError("Expected a denial, got $decision")
  }

  private fun denialCause(decision: StorageSyncLoopDetector.Decision): StorageSyncLoopDetector.Cause {
    return denial(decision).cause
  }

  private fun writeOf(vararg payloads: Int, deletes: IntArray = IntArray(0)): WriteOperationResult {
    return WriteOperationResult(
      manifest = SignalStorageManifest.EMPTY,
      inserts = payloads.map { contactRecord(it) },
      deletes = deletes.map { TestHelpers.byteArray(it) }
    )
  }

  /** One distinct payload per [payload], under a storage id that rotates on every call the way a real write does. */
  private fun contactRecord(payload: Int): SignalStorageRecord {
    return SignalStorageRecord(
      id = StorageId.forContact(Util.getSecretBytes(16)),
      proto = StorageRecord(contact = ContactRecord(e164 = "+1555000$payload"))
    )
  }
}

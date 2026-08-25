/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.storage.StorageSyncHelper.WriteOperationResult
import org.thoughtcrime.securesms.util.LeakyBucket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Throttles storage service writes when another device keeps undoing them. Left alone, the two devices ping-pong
 * manifest versions indefinitely, which burns a large amount of battery and network.
 *
 * Declining the write is what actually breaks the loop: we only ask other devices to sync after a successful write, so
 * not writing stops us from prodding the other device, which stops it from prodding us back.
 *
 * Two [LeakyBucket]s, both persisted so a loop can't shake them off by killing the process:
 *
 * - The content bucket is only charged when a write repeats one of the last [FINGERPRINT_HISTORY] payload fingerprints
 *   on a run that fetched the remote manifest. Novel content is always free, and so is a run that reused the local
 *   manifest, because a loop only ever writes in reaction to a peer's manifest. Storage ids and deletes are left out of the
 *   fingerprint because they rotate on every write, loop or not.
 * - The rate bucket is charged for every write on a run that fetched the remote manifest, regardless of content. It is
 *   much larger, and exists to bound loops the content bucket can't see -- ones whose payloads aren't stable.
 */
object StorageSyncLoopDetector {

  /** How many past payload fingerprints a write is compared against. */
  private const val FINGERPRINT_HISTORY = 3

  private val contentBucket = LeakyBucket(
    capacity = 3,
    dripInterval = 1.hours,
    state = ContentBucketState
  )

  private val rateBucket = LeakyBucket(
    capacity = 100,
    dripInterval = 10.minutes,
    state = RateBucketState
  )

  /**
   * Charges a write against whichever buckets apply and reports whether it may proceed. Call once per write attempt:
   * levels rise here, not on success, so a write that fails still costs what it would have.
   *
   * Job retries are exempt entirely, which keeps that cost at once per failure rather than once per attempt.
   */
  @Synchronized
  fun onWriteAttempt(write: WriteOperationResult, fetchedRemoteManifest: Boolean, isRetry: Boolean, now: Duration = System.currentTimeMillis().milliseconds): Decision {
    if (!SignalStore.account.isMultiDevice) {
      return Decision.Allowed
    }

    if (isRetry) {
      return Decision.Allowed
    }

    val fingerprint = fingerprint(write)
    val chargeContent = fetchedRemoteManifest && fingerprint != null && SignalStore.storageService.syncLoopState.recentFingerprints.contains(fingerprint)

    if (chargeContent && !contentBucket.hasRoom(now)) {
      return Decision.Denied(Cause.REPEATED_PAYLOAD, contentBucket.level(now))
    }

    if (fetchedRemoteManifest && !rateBucket.hasRoom(now)) {
      return Decision.Denied(Cause.WRITE_RATE, rateBucket.level(now))
    }

    if (chargeContent) {
      contentBucket.use(now)
    }

    if (fetchedRemoteManifest) {
      rateBucket.use(now)
    }

    if (fingerprint != null) {
      remember(fingerprint)
    }

    return Decision.Allowed
  }

  /**
   * Refunds a write that likely never landed.
   */
  @Synchronized
  fun onWriteFailed(now: Duration = System.currentTimeMillis().milliseconds) {
    contentBucket.refund(now)
    rateBucket.refund(now)
  }

  /**
   * Called when a sync had nothing to write, meaning we agree with the remote state. Only the content bucket clears:
   * agreement says the payload disagreement resolved, but says nothing about the write volume the rate bucket tracks.
   */
  @Synchronized
  fun onConverged() {
    contentBucket.clear()
  }

  private fun remember(fingerprint: Int) {
    val state = SignalStore.storageService.syncLoopState

    SignalStore.storageService.syncLoopState = state.copy(
      recentFingerprints = (listOf(fingerprint) + state.recentFingerprints).distinct().take(FINGERPRINT_HISTORY)
    )
  }

  /** Null when there's nothing content-addressable to compare, i.e. a write of nothing but deletes. */
  private fun fingerprint(write: WriteOperationResult): Int? {
    if (write.inserts.isEmpty()) {
      return null
    }

    return write.inserts
      .map { it.proto.encode().contentHashCode() }
      .sorted()
      .hashCode()
  }

  private object ContentBucketState : LeakyBucket.State {
    override val level: Int
      get() = SignalStore.storageService.syncLoopState.contentLevel

    override val levelUpdatedAt: Long
      get() = SignalStore.storageService.syncLoopState.contentLevelAsOf

    override fun update(level: Int, levelAsOf: Long) {
      SignalStore.storageService.syncLoopState = SignalStore.storageService.syncLoopState.copy(
        contentLevel = level,
        contentLevelAsOf = levelAsOf
      )
    }
  }

  private object RateBucketState : LeakyBucket.State {
    override val level: Int
      get() = SignalStore.storageService.syncLoopState.rateLevel

    override val levelUpdatedAt: Long
      get() = SignalStore.storageService.syncLoopState.rateLevelAsOf

    override fun update(level: Int, levelAsOf: Long) {
      SignalStore.storageService.syncLoopState = SignalStore.storageService.syncLoopState.copy(
        rateLevel = level,
        rateLevelAsOf = levelAsOf
      )
    }
  }

  enum class Cause {
    REPEATED_PAYLOAD,
    WRITE_RATE
  }

  sealed interface Decision {
    /** The write should proceed as normal. */
    data object Allowed : Decision

    /** The write found no room in a bucket and should be skipped. */
    data class Denied(val cause: Cause, val level: Int) : Decision
  }
}

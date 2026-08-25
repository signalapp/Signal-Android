/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A leaky bucket, in the same spirit as [LeakyBucketLimiter]: each use raises the level, and the level drips back down
 * one per [dripInterval]. Once the level reaches [capacity] there is no room until enough has dripped away.
 *
 * Not thread safe. [hasRoom] and [use] are separate calls so a caller can check several buckets before charging any of
 * them, so callers need to serialize access themselves anyway.
 */
class LeakyBucket(
  private val capacity: Int,
  private val dripInterval: Duration,
  private val state: State
) {

  /** The level after everything that has dripped away is credited, without recording that drip. */
  fun level(now: Duration = System.currentTimeMillis().milliseconds): Int {
    return calculateStateForCurrentTime(now).level
  }

  fun hasRoom(now: Duration = System.currentTimeMillis().milliseconds): Boolean {
    return level(now) < capacity
  }

  /** Raises the level by one, recording any drip along the way. Only call when [hasRoom]. */
  fun use(now: Duration = System.currentTimeMillis().milliseconds) {
    val currentState = calculateStateForCurrentTime(now)

    state.update(currentState.level + 1, currentState.levelUpdatedAt)
  }

  /** Lowers the level by one, recording any drip along the way. Pairs with [use] for work that ended up not happening. */
  fun refund(now: Duration = System.currentTimeMillis().milliseconds) {
    val currentState = calculateStateForCurrentTime(now)

    state.update((currentState.level - 1).coerceAtLeast(0), currentState.levelUpdatedAt)
  }

  fun clear() {
    state.update(0, 0)
  }

  /**
   * [Snapshot.levelUpdatedAt] advances by whole drip intervals only, so a partially elapsed one carries toward the next drip
   * rather than being discarded.
   */
  private fun calculateStateForCurrentTime(now: Duration): Snapshot {
    val level = state.level
    val levelAsOf = state.levelUpdatedAt
    val elapsed = now - levelAsOf.milliseconds

    // The level only rises when there was room, so a clock behind levelAsOf would report a full bucket forever with
    // nothing left to advance the timestamp that drains it. Empty it rather than wedge until the clock catches up.
    if (level <= 0 || elapsed < Duration.ZERO) {
      return Snapshot(0, now.inWholeMilliseconds)
    }

    val drips = (elapsed / dripInterval).toInt()

    return Snapshot((level - drips).coerceAtLeast(0), levelAsOf + (dripInterval * drips).inWholeMilliseconds)
  }

  private data class Snapshot(val level: Int, val levelUpdatedAt: Long)

  interface State {
    val level: Int
    val levelUpdatedAt: Long

    fun update(level: Int, levelAsOf: Long)
  }

  class InMemoryState : State {
    override var level: Int = 0
      private set

    override var levelUpdatedAt: Long = 0
      private set

    override fun update(level: Int, levelAsOf: Long) {
      this.level = level
      this.levelUpdatedAt = levelAsOf
    }
  }
}

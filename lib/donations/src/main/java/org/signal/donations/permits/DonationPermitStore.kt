/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations.permits

import org.signal.libsignal.zkgroup.donation.DonationPermit
import java.time.Instant

/**
 * The single, process-wide store of unspent permits, backed by an in-memory FIFO queue. Permits are
 * short-lived bearer secrets and are never written to disk.
 */
object DonationPermitStore {

  private val permits = ArrayDeque<DonationPermit>()

  /** Bumped on every [clear] so an in-flight acquisition can detect an intervening clear via [generation]. */
  private var generation = 0

  /** The current store generation; pass back to [addAllIfGeneration]. */
  @Synchronized
  fun generation(): Int = generation

  /** Adds freshly received permits to the store. */
  @Synchronized
  fun addAll(permits: List<DonationPermit>) {
    this.permits.addAll(permits)
  }

  /** Adds [permits] only if the generation still matches [expectedGeneration]; returns true if stored. */
  @Synchronized
  fun addAllIfGeneration(expectedGeneration: Int, permits: List<DonationPermit>): Boolean {
    if (generation != expectedGeneration) {
      return false
    }
    this.permits.addAll(permits)
    return true
  }

  /** Removes and returns the oldest unexpired permit, or null if none remain. Expired permits are dropped. */
  @Synchronized
  fun take(now: Instant = Instant.now()): DonationPermit? {
    while (permits.isNotEmpty()) {
      val permit = permits.removeFirst()
      if (now.isBefore(permit.expiration)) {
        return permit
      }
    }
    return null
  }

  /** The number of unexpired permits currently held. */
  @Synchronized
  fun size(now: Instant = Instant.now()): Int = permits.count { now.isBefore(it.expiration) }

  /** Drops all held permits and bumps [generation]. Permits are account-linked secrets; call on logout/deregister. */
  @Synchronized
  fun clear() {
    generation++
    permits.clear()
  }
}

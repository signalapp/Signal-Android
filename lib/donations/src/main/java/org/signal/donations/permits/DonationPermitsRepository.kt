/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations.permits

import androidx.annotation.WorkerThread
import arrow.core.Either
import arrow.core.raise.either
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.ServerPublicParams
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.donation.DonationPermit
import org.signal.libsignal.zkgroup.donation.DonationPermitRequestContext
import org.signal.libsignal.zkgroup.donation.DonationPermitResponse
import java.time.Instant

/**
 * Orchestrates the client side of the donation-permit flow: build a blinded request, exchange it with the
 * issuing server, unblind the response into permits, store them, and hand them out one at a time for redemption.
 *
 * @param serverPublicParams the audited, app-pinned root public key the issuer's response is verified against.
 */
class DonationPermitsRepository(
  private val issuer: DonationPermitIssuer,
  private val serverPublicParams: ServerPublicParams
) {

  private val store = DonationPermitStore

  companion object {
    private val TAG = Log.tag(DonationPermitsRepository::class.java)

    /** Default batch size: spend one and keep spares so a retry need not return to the issuer. */
    const val DEFAULT_PERMIT_BATCH = 3
  }

  /** Requests a fresh batch of [count] permits and stores them, or returns the reason it could not. */
  @WorkerThread
  internal fun acquirePermits(count: Int = DEFAULT_PERMIT_BATCH, now: Instant = Instant.now()): Either<DonationPermitError, Unit> = either {
    require(count > 0) { "count must be greater than zero" }

    val generation = store.generation()

    val context = DonationPermitRequestContext.forCount(count)
    val responseBytes = issuer.issue(context.request().serialize()).bind()
    val permits = try {
      context.receive(DonationPermitResponse(responseBytes), serverPublicParams, now)
    } catch (e: VerificationFailedException) {
      raise(DonationPermitError.VerificationFailed)
    } catch (e: InvalidInputException) {
      raise(DonationPermitError.MalformedResponse)
    }

    if (store.addAllIfGeneration(generation, permits)) {
      Log.i(TAG, "Acquired ${permits.size} donation permit(s).", null, true)
    } else {
      Log.w(TAG, "Permits were cleared while acquiring; discarded ${permits.size}.")
    }
  }

  @WorkerThread
  internal fun spendPermit(now: Instant = Instant.now()): DonationPermit? {
    val permit = store.take(now)

    if (permit != null) {
      Log.i(TAG, "Spent a permit. ${store.size(now)} remaining.", null, true)
    } else {
      Log.i(TAG, "No permits.", null, true)
    }

    return permit
  }

  /**
   * Retrieves a permit to present at a donation endpoint: spends a stored permit, acquiring a fresh batch first
   * if none remain. Returns the reason it could not be obtained (issuer, verification, decode) so the caller can
   * surface the failure rather than silently proceeding without a required permit.
   */
  @WorkerThread
  fun spendOrAcquirePermit(now: Instant = Instant.now()): Either<DonationPermitError, DonationPermit> = either {
    val existing = spendPermit(now)
    if (existing != null) {
      existing
    } else {
      acquirePermits(now = now).bind()
      spendPermit(now) ?: raise(DonationPermitError.MalformedResponse)
    }
  }

  /** The number of unexpired permits currently available to spend. */
  internal fun availablePermitCount(now: Instant = Instant.now()): Int = store.size(now)

  /**
   * Drops all held permits. Permits are account-linked bearer secrets, so this must be called on
   * logout/deregistration. A clear that races an in-flight acquire is handled by [DonationPermitStore]'s
   * generation check, so neither path holds a lock across the network call.
   */
  fun clearPermits() = store.clear()
}

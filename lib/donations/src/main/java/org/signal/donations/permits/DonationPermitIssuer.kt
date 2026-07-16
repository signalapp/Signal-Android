/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations.permits

import androidx.annotation.WorkerThread
import arrow.core.Either

/**
 * The network seam for obtaining permits from the issuing server, keeping `:lib:donations` free of the
 * websocket/network stack.
 */
interface DonationPermitIssuer {

  /**
   * Submits the serialized, blinded [requestBytes] to the issuing server, returning either the serialized
   * `DonationPermitResponse` bytes or the reason the request could not be fulfilled.
   */
  @WorkerThread
  fun issue(requestBytes: ByteArray): Either<DonationPermitError, ByteArray>
}

/** The reasons a donation-permit acquisition can fail, surfaced via [Either] rather than thrown. */
sealed interface DonationPermitError {

  /** The issuing server could not fulfil the request (non-success status, network error, or transport failure). */
  data class IssuerUnavailable(val statusCode: Int? = null, val cause: Throwable? = null) : DonationPermitError

  /** The issuer's response failed zkgroup verification against the pinned public params. */
  data object VerificationFailed : DonationPermitError

  /** The issuer's response could not be decoded. */
  data object MalformedResponse : DonationPermitError
}

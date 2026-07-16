/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

import org.signal.libsignal.net.RequestResult
import org.signal.network.rest.RestStatusCodeError

/**
 * Supplies the required `Donation-Permit` header value for the donation endpoints that need it, injected into
 * [org.whispersystems.signalservice.api.services.DonationsService].
 */
fun interface DonationPermitProvider {
  /**
   * The base64-encoded single-use donation permit for the `Donation-Permit` header, or a non-success
   * [RequestResult] describing why one could not be obtained (e.g. a network error vs. another failure). May
   * perform network I/O, so call off the main thread.
   */
  fun getDonationPermit(): RequestResult<String, RestStatusCodeError>
}

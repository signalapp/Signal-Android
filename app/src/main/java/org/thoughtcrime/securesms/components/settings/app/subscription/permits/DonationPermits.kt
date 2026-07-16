/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.permits

import androidx.annotation.WorkerThread
import org.signal.core.util.Base64
import org.signal.donations.permits.DonationPermitError
import org.signal.libsignal.net.RequestResult
import org.signal.network.rest.RestStatusCodeError
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.whispersystems.signalservice.api.donations.DonationPermitProvider
import java.io.IOException

/**
 * App-side [DonationPermitProvider]: spends a permit and base64-encodes it for the `Donation-Permit` header,
 * translating an acquisition failure into a [RequestResult] the donations service can surface.
 */
object DonationPermits : DonationPermitProvider {

  @WorkerThread
  override fun getDonationPermit(): RequestResult<String, RestStatusCodeError> {
    return AppDependencies.donationPermitsRepository
      .spendOrAcquirePermit()
      .fold(
        ifLeft = { it.toRequestResult() },
        ifRight = { RequestResult.Success(Base64.encodeWithPadding(it.serialize())) }
      )
  }

  private fun DonationPermitError.toRequestResult(): RequestResult<String, RestStatusCodeError> {
    return when (this) {
      is DonationPermitError.IssuerUnavailable -> {
        val statusCode = statusCode
        val cause = cause
        when {
          statusCode != null -> RequestResult.NonSuccess(RestStatusCodeError(statusCode, emptyMap(), null))
          cause is IOException -> RequestResult.RetryableNetworkError(cause)
          else -> RequestResult.ApplicationError(cause ?: IllegalStateException("Donation permit issuer unavailable"))
        }
      }
      DonationPermitError.VerificationFailed -> RequestResult.ApplicationError(IllegalStateException("Donation permit verification failed"))
      DonationPermitError.MalformedResponse -> RequestResult.ApplicationError(IllegalStateException("Malformed donation permit response"))
    }
  }
}

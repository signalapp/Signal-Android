/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.permits

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.signal.donations.permits.DonationPermitError
import org.signal.donations.permits.DonationPermitIssuer
import org.signal.libsignal.net.RequestResult
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * [DonationPermitIssuer] backed by [org.whispersystems.signalservice.api.donations.DonationsApi], translating a
 * non-success [RequestResult] into a [DonationPermitError].
 */
object NetworkDonationPermitIssuer : DonationPermitIssuer {

  override fun issue(requestBytes: ByteArray): Either<DonationPermitError, ByteArray> {
    return when (val result = AppDependencies.donationsApi.createDonationPermits(requestBytes)) {
      is RequestResult.Success -> result.result.right()
      is RequestResult.NonSuccess -> DonationPermitError.IssuerUnavailable(statusCode = result.error.statusCode).left()
      is RequestResult.RetryableNetworkError -> DonationPermitError.IssuerUnavailable(cause = result.networkError).left()
      is RequestResult.ApplicationError -> DonationPermitError.IssuerUnavailable(cause = result.cause).left()
    }
  }
}

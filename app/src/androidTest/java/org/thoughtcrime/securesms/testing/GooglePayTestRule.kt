/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import com.google.android.gms.wallet.PaymentData
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.reactivex.rxjava3.core.Completable
import org.junit.rules.ExternalResource
import org.signal.donations.GooglePayApi

/**
 * Makes Google Pay appear available and return a fake [com.google.android.gms.wallet.PaymentData] without
 * launching the real Google Pay sheet, allowing checkout to be driven to the payment pipeline in instrumentation.
 */
class GooglePayTestRule : ExternalResource() {
  override fun before() {
    val paymentData = mockk<PaymentData> {
      every { toJson() } returns GOOGLE_PAY_PAYMENT_DATA_JSON
    }

    mockkConstructor(GooglePayApi::class)
    every { anyConstructed<GooglePayApi>().queryIsReadyToPay() } returns Completable.complete()
    every { anyConstructed<GooglePayApi>().requestPayment(any(), any(), any()) } just Runs
    every { anyConstructed<GooglePayApi>().onActivityResult(any(), any(), any(), any(), any()) } answers {
      arg<GooglePayApi.PaymentRequestCallback>(4).onSuccess(paymentData)
    }
  }

  override fun after() {
    unmockkConstructor(GooglePayApi::class)
  }

  companion object {
    /**
     * Minimal but well-formed Google Pay payload. [org.signal.donations.GooglePayPaymentSource] parses
     * `paymentMethodData.tokenizationData.token` (itself a JSON object with an `id`) when the source is
     * serialized into the setup job, so a relaxed mock that returns an empty body fails before the job runs.
     */
    const val GOOGLE_PAY_PAYMENT_DATA_JSON = """{"paymentMethodData":{"tokenizationData":{"token":"{\"id\":\"tok_test\"}"}},"email":"test@signal.org"}"""
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.donations

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.core.util.money.FiatMoney
import org.signal.donations.json.StripeIntentStatus
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.net.ServerSideErrorException
import java.net.SocketTimeoutException

class StripeApiGetIntentTest {

  private val paymentIntentAccessor = StripeIntentAccessor(
    objectType = StripeIntentAccessor.ObjectType.PAYMENT_INTENT,
    intentId = "pi_test",
    intentClientSecret = "pi_test_secret"
  )

  @Test
  fun `Given a 200 response, when I getPaymentIntent, then I expect success`() {
    val result = stripeApi { respond(it, 200, """{"id":"pi_test","client_secret":"pi_test_secret","status":"succeeded","payment_method":"pm_test"}""") }
      .getPaymentIntent(paymentIntentAccessor)

    assertTrue(result is RequestResult.Success)
    assertEquals(StripeIntentStatus.SUCCEEDED, (result as RequestResult.Success).result.status)
  }

  @Test
  fun `Given a socket timeout, when I getPaymentIntent, then I expect a retryable network error`() {
    val result = stripeApi { throw SocketTimeoutException("timeout") }
      .getPaymentIntent(paymentIntentAccessor)

    assertTrue(result is RequestResult.RetryableNetworkError)
  }

  @Test
  fun `Given a 503 response, when I getPaymentIntent, then I expect a retryable server-side error`() {
    val result = stripeApi { respond(it, 503, "{}") }
      .getPaymentIntent(paymentIntentAccessor)

    assertTrue(result is RequestResult.RetryableNetworkError)
    assertTrue((result as RequestResult.RetryableNetworkError).networkError is ServerSideErrorException)
  }

  @Test
  fun `Given a 404 response, when I getPaymentIntent, then I expect a non-success with the status code`() {
    val result = stripeApi { respond(it, 404, """{"error":{"code":"resource_missing"}}""") }
      .getPaymentIntent(paymentIntentAccessor)

    assertTrue(result is RequestResult.NonSuccess)
    assertEquals(404, (result as RequestResult.NonSuccess).error.statusCode)
  }

  @Test
  fun `Given an unparseable 200 response, when I getPaymentIntent, then I expect an application error`() {
    val result = stripeApi { respond(it, 200, "not json") }
      .getPaymentIntent(paymentIntentAccessor)

    assertTrue(result is RequestResult.ApplicationError)
    assertTrue((result as RequestResult.ApplicationError).cause is StripeError.FailedToParsePaymentIntentResponseError)
  }

  @Test
  fun `Given a 403 response, when I getSetupIntent, then I expect a non-success with the status code`() {
    val setupIntentAccessor = StripeIntentAccessor(
      objectType = StripeIntentAccessor.ObjectType.SETUP_INTENT,
      intentId = "seti_test",
      intentClientSecret = "seti_test_secret"
    )

    val result = stripeApi { respond(it, 403, "{}") }
      .getSetupIntent(setupIntentAccessor)

    assertTrue(result is RequestResult.NonSuccess)
    assertEquals(403, (result as RequestResult.NonSuccess).error.statusCode)
  }

  private fun stripeApi(interceptor: Interceptor): StripeApi {
    return StripeApi(
      configuration = StripeApi.Configuration(publishableKey = "pk_test"),
      paymentIntentFetcher = object : StripeApi.PaymentIntentFetcher {
        override fun fetchPaymentIntent(price: FiatMoney, level: Long, sourceType: PaymentSourceType.Stripe): StripeIntentAccessor = error("Unused")
      },
      setupIntentHelper = object : StripeApi.SetupIntentHelper {
        override fun fetchSetupIntent(inAppPaymentType: InAppPaymentType, sourceType: PaymentSourceType.Stripe): StripeIntentAccessor = error("Unused")
      },
      okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
    )
  }

  private fun respond(chain: Interceptor.Chain, code: Int, body: String): Response {
    return Response.Builder()
      .request(chain.request())
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message("")
      .body(body.toResponseBody("application/json".toMediaType()))
      .build()
  }
}

/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing.endpoints

/**
 * Canned JSON bodies for the Stripe HTTP edge (`api.stripe.com`), matching the shapes parsed by
 * [org.signal.donations.StripeApi] / `org.signal.donations.json.*`. Used by handlers registered on
 * an [EndpointResponder] to model "what Stripe returns" for a scenario.
 */
object StripeResponses {

  const val DEFAULT_PAYMENT_INTENT_ID = "pi_test"
  const val DEFAULT_SETUP_INTENT_ID = "seti_test"
  const val DEFAULT_PAYMENT_METHOD_ID = "pm_test"
  const val DEFAULT_TOKEN_ID = "tok_test"

  /** A Stripe PaymentIntent object (GET /v1/payment_intents/{id}). */
  fun paymentIntent(
    id: String = DEFAULT_PAYMENT_INTENT_ID,
    status: String = "succeeded",
    paymentMethod: String = DEFAULT_PAYMENT_METHOD_ID
  ): String {
    return """{"id":"$id","client_secret":"${id}_secret_test","status":"$status","payment_method":"$paymentMethod"}"""
  }

  /** A Stripe SetupIntent object (GET /v1/setup_intents/{id}). */
  fun setupIntent(
    id: String = DEFAULT_SETUP_INTENT_ID,
    status: String = "succeeded",
    paymentMethod: String = DEFAULT_PAYMENT_METHOD_ID
  ): String {
    return """{"id":"$id","client_secret":"${id}_secret_test","status":"$status","payment_method":"$paymentMethod","customer":"cus_test"}"""
  }

  /** Response to POST /v1/payment_methods and POST /v1/tokens — the id is all the client parses. */
  fun paymentMethod(id: String = DEFAULT_PAYMENT_METHOD_ID): String = """{"id":"$id"}"""

  fun token(id: String = DEFAULT_TOKEN_ID): String = """{"id":"$id"}"""

  /** A confirm response with no `next_action` — the intent needs no 3DS. */
  fun confirmNoAction(): String = "{}"

  /** A confirm response carrying a 3DS `next_action.redirect_to_url`. */
  fun confirm3dsRequired(
    url: String = "https://api.stripe.com/v1/3ds/test",
    returnUrl: String = "sgnlpay://3DS"
  ): String {
    return """{"next_action":{"type":"redirect_to_url","redirect_to_url":{"url":"$url","return_url":"$returnUrl"}}}"""
  }

  /** A Stripe error body carrying a decline code (e.g. `card_declined`, `insufficient_funds`). */
  fun declineError(declineCode: String = "card_declined"): String {
    return """{"error":{"code":"card_declined","decline_code":"$declineCode","message":"Your card was declined."}}"""
  }

  /** A Stripe error body carrying a bank-transfer failure code. */
  fun failureError(failureCode: String): String {
    return """{"error":{"code":"$failureCode","failure_code":"$failureCode","message":"The payment failed."}}"""
  }

  /** A generic Stripe error body carrying only an error code. */
  fun genericError(code: String = "processing_error"): String {
    return """{"error":{"code":"$code","message":"An error occurred."}}"""
  }
}

/**
 * Registers the Stripe HTTP handlers for a fully successful, no-3DS payment/setup: create payment
 * method + token, confirm with no `next_action`, and a succeeded intent on lookup. A scenario can
 * override any of these by registering a more specific handler afterwards (e.g. a decline).
 */
fun EndpointResponder.registerStripeHappyPath() {
  post("/v1/payment_methods") { ok(StripeResponses.paymentMethod()) }
  post("/v1/tokens") { ok(StripeResponses.token()) }
  register({ it.method == "POST" && it.path.contains("/payment_intents/") && it.path.endsWith("/confirm") }) { ok(StripeResponses.confirmNoAction()) }
  register({ it.method == "POST" && it.path.contains("/setup_intents/") && it.path.endsWith("/confirm") }) { ok(StripeResponses.confirmNoAction()) }
  register({ it.method == "GET" && it.path.contains("/payment_intents/") }) { ok(StripeResponses.paymentIntent()) }
  register({ it.method == "GET" && it.path.contains("/setup_intents/") }) { ok(StripeResponses.setupIntent()) }
}

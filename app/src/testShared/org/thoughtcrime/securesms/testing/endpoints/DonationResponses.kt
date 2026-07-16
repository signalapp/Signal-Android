/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing.endpoints

/**
 * Canned JSON bodies for the Signal-service (websocket) edge, matching the shapes parsed by
 * [org.whispersystems.signalservice.api.donations.DonationsApi]. Used by handlers registered on an
 * [EndpointResponder] to model "what the Signal service returns" for a scenario.
 */
object DonationResponses {

  /**
   * A [org.whispersystems.signalservice.api.subscriptions.StripeClientSecret] body. The client
   * derives the intent id by stripping `_secret...`, so the id embedded here must line up with the
   * Stripe intent the [StripeResponses] handlers serve.
   */
  fun stripeClientSecret(clientSecret: String = "${StripeResponses.DEFAULT_PAYMENT_INTENT_ID}_secret_test"): String {
    return """{"clientSecret":"$clientSecret"}"""
  }

  /** A setup-intent client secret keyed to the default Stripe setup intent. */
  fun setupIntentClientSecret(clientSecret: String = "${StripeResponses.DEFAULT_SETUP_INTENT_ID}_secret_test"): String {
    return """{"clientSecret":"$clientSecret"}"""
  }

  /**
   * A [org.whispersystems.signalservice.api.donations.ReceiptCredentialResponseJson] body. The
   * [base64Credential] must be a valid credential minted by the test zk server against the same
   * params the client validates with.
   */
  fun receiptCredential(base64Credential: String): String {
    return """{"receiptCredentialResponse":"$base64Credential"}"""
  }

  /**
   * A [org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentIntentResponse]. The
   * approval URL deliberately does NOT match PayPal's return URLs, so the confirmation webview stays
   * up (rather than auto-dismissing) until the test injects the result.
   */
  fun payPalCreateIntent(approvalUrl: String = "https://example.com/paypal/approve", paymentId: String = "PAYID-test"): String {
    return """{"approvalUrl":"$approvalUrl","paymentId":"$paymentId"}"""
  }

  /** A [org.whispersystems.signalservice.api.subscriptions.PayPalConfirmPaymentIntentResponse]. */
  fun payPalConfirmIntent(paymentId: String = "PAYID-test"): String = """{"paymentId":"$paymentId"}"""

  /** A [org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentMethodResponse]. */
  fun payPalCreatePaymentMethod(approvalUrl: String = "https://example.com/paypal/approve", token: String = "BA-test"): String {
    return """{"approvalUrl":"$approvalUrl","token":"$token"}"""
  }

  /** A [org.whispersystems.signalservice.internal.push.BankMandate] (SEPA legal text). */
  fun bankMandate(text: String = "Test SEPA mandate."): String = """{"mandate":"$text"}"""

  /** An empty [org.whispersystems.signalservice.api.subscriptions.ActiveSubscription] (no subscription). */
  fun emptySubscription(): String = """{"subscription":null,"chargeFailure":null}"""

  /**
   * An [org.whispersystems.signalservice.api.subscriptions.ActiveSubscription] with an active or
   * incomplete subscription. [status] is the Stripe subscription status ("active", "incomplete", ...);
   * [active] whether the subscription is currently active.
   */
  fun activeSubscription(
    level: Int = 200,
    currency: String = "USD",
    amount: Long = 1,
    status: String = "active",
    active: Boolean = true,
    endOfCurrentPeriodSeconds: Long = (System.currentTimeMillis() / 1000) + 30L * 24 * 60 * 60,
    processor: String = "STRIPE",
    paymentMethod: String = "CARD",
    paymentPending: Boolean = false
  ): String {
    return """
      {"subscription":{
        "level":$level,
        "currency":"$currency",
        "amount":$amount,
        "endOfCurrentPeriod":$endOfCurrentPeriodSeconds,
        "active":$active,
        "billingCycleAnchor":$endOfCurrentPeriodSeconds,
        "cancelAtPeriodEnd":false,
        "status":"$status",
        "processor":"$processor",
        "paymentMethod":"$paymentMethod",
        "paymentPending":$paymentPending
      },"chargeFailure":null}
    """.trimIndent()
  }
}

/**
 * Registers the Signal-service PayPal handlers for a successful flow: create/confirm one-time intent
 * and create recurring payment method. (The default-payment-method and redeem handlers are covered by
 * the [org.thoughtcrime.securesms.testing.InAppPaymentsRule] defaults.)
 */
fun EndpointResponder.registerPayPalHappyPath() {
  post("/v1/subscription/boost/paypal/create") { ok(DonationResponses.payPalCreateIntent()) }
  post("/v1/subscription/boost/paypal/confirm") { ok(DonationResponses.payPalConfirmIntent()) }
  register({ it.method == "POST" && it.path.contains("/create_payment_method/paypal") }) { ok(DonationResponses.payPalCreatePaymentMethod()) }
}

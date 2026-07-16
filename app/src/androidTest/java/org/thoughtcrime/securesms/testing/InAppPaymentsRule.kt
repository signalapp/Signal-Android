/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import org.json.JSONObject
import org.junit.rules.ExternalResource
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testing.endpoints.DonationResponses
import org.thoughtcrime.securesms.testing.endpoints.DonationTestServer
import org.thoughtcrime.securesms.testing.endpoints.EndpointResponse
import org.thoughtcrime.securesms.testing.endpoints.MockEndpoints
import org.thoughtcrime.securesms.testing.endpoints.delete
import org.thoughtcrime.securesms.testing.endpoints.get
import org.thoughtcrime.securesms.testing.endpoints.ok
import org.thoughtcrime.securesms.testing.endpoints.post
import org.thoughtcrime.securesms.testing.endpoints.put
import org.whispersystems.signalservice.internal.push.WhoAmIResponse

/**
 * Sets up common infrastructure for on-device InAppPayment testing.
 *
 * The Signal-service edge is driven through the shared [MockEndpoints.responder] (the real
 * [org.whispersystems.signalservice.api.donations.DonationsApi] runs against these handlers), while
 * account/archive lookups that are not websocket-backed here stay mocked via mockk. A scenario can
 * override any default by registering a more specific handler after this rule runs (last-registered
 * wins).
 */
class InAppPaymentsRule : ExternalResource() {

  override fun before() {
    MockEndpoints.responder.clear()
    registerDonationServiceDefaults()
    initialiseSetArchiveBackupId()
    initialiseSetAccountAttributes()
    initialiseAccountLookups()
  }

  override fun after() {
    MockEndpoints.responder.clear()
  }

  /**
   * Default happy-path responses for the Signal donations endpoints. Registered generic-to-specific
   * so more specific paths registered later win.
   */
  private fun registerDonationServiceDefaults() {
    val responder = MockEndpoints.responder

    // Subscriber lifecycle: create/put/update-level (PUT), cancel (DELETE) all succeed by default.
    responder.put("/v1/subscription/") { ok() }
    responder.delete("/v1/subscription/") { ok() }

    // getSubscription defaults to "no active subscription".
    responder.get("/v1/subscription/") { ok(DonationResponses.emptySubscription()) }

    // Donations configuration (registered after the generic GET so it wins for its path).
    val configuration = InstrumentationRegistry.getInstrumentation().context.resources.assets
      .open("inAppPaymentsTests/configuration.json")
      .use { it.readBytes().decodeToString() }
    responder.get("/v1/subscription/configuration") { ok(configuration) }

    // SEPA bank-transfer mandate text (registered after the generic GET so it wins for its path).
    responder.get("/v1/subscription/bank_mandate/") { ok(DonationResponses.bankMandate()) }

    // One-time (boost) payment intent.
    responder.post("/v1/subscription/boost/create") { ok(DonationResponses.stripeClientSecret()) }

    // Recurring payment method setup + default payment method + level are keyed by subscriber id in
    // the middle of the path, so match on a distinctive suffix.
    responder.register({ it.method == "POST" && it.path.contains("/create_payment_method") && !it.path.contains("paypal") }) {
      ok(DonationResponses.setupIntentClientSecret())
    }
    responder.register({ it.method == "POST" && it.path.contains("/default_payment_method") }) { ok() }

    // Receipt credential submission (boost + recurring) — mint a valid credential from the client's request.
    responder.register({ it.method == "POST" && it.path.contains("/receipt_credentials") }) { request ->
      mintReceiptCredential(request.bodyString)
    }

    // Redemption.
    responder.post("/v1/donation/redeem-receipt") { ok() }
  }

  private fun mintReceiptCredential(requestBody: String): EndpointResponse {
    val requestBase64 = JSONObject(requestBody).getString("receiptCredentialRequest")
    val minted = DonationTestServer.issueReceiptCredential(requestBase64)
    return ok(DonationResponses.receiptCredential(minted))
  }

  private fun initialiseSetArchiveBackupId() {
    AppDependencies.archiveApi.apply {
      every { triggerBackupIdReservation(any(), any(), any()) } returns NetworkResult.Success(Unit)
    }
  }

  private fun initialiseSetAccountAttributes() {
    AppDependencies.accountApi.apply {
      every { setAccountAttributes(any()) } returns NetworkResult.Success(Unit)
    }
  }

  /**
   * whoAmI is served by the (still mocked) account API rather than the websocket responder, so
   * background jobs that resolve the account during a test hit a handled path instead of throwing.
   */
  private fun initialiseAccountLookups() {
    AppDependencies.accountApi.apply {
      every { whoAmI() } returns NetworkResult.Success(WhoAmIResponse(number = "+15555550123"))
    }
  }
}

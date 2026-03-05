/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations

import org.signal.core.util.Base64
import org.signal.core.util.urlEncode
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialResponse
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.PayPalConfirmPaymentIntentResponse
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentIntentResponse
import org.whispersystems.signalservice.api.subscriptions.PayPalCreatePaymentMethodResponse
import org.whispersystems.signalservice.api.subscriptions.StripeClientSecret
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.delete
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.post
import org.whispersystems.signalservice.internal.push.BankMandate
import org.whispersystems.signalservice.internal.push.DonationProcessor
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import org.whispersystems.signalservice.internal.put
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import java.util.Locale

/**
 * One-stop shop for Signal service calls related to in-app payments.
 *
 * Be sure to check for cached versions of these methods in DonationsService before calling these methods elsewhere.
 */
class DonationsApi(private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket, private val unauthWebSocket: SignalWebSocket.UnauthenticatedWebSocket) {

  /**
   * Get configuration data associated with donations, like gift, one-time, and monthly levels, etc.
   *
   * Note, this will skip cached values, causing us to hit the network more than necessary. Consider accessing this method via the DonationsService instead.
   *
   * GET /v1/subscription/configuration
   * - 200: Success
   */
  fun getDonationsConfiguration(locale: Locale): NetworkResult<SubscriptionsConfiguration> {
    val request = WebSocketRequestMessage.get("/v1/subscription/configuration", locale.toAcceptLanguageHeaders())
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, SubscriptionsConfiguration::class)
  }

  /**
   * Get localized bank mandate text for the given [bankTransferType].
   *
   * GET /v1/subscription/bank_mandate/[bankTransferType]
   * - 200: Success
   *
   * @param bankTransferType Valid values for bankTransferType are 'SEPA_DEBIT'.
   */
  fun getBankMandate(locale: Locale, bankTransferType: String): NetworkResult<BankMandate> {
    val request = WebSocketRequestMessage.get("/v1/subscription/bank_mandate/$bankTransferType", locale.toAcceptLanguageHeaders())
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, BankMandate::class)
  }

  /**
   * GET /v1/subscription/[subscriberId]
   * - 200: Success
   * - 403: Invalid or malformed [subscriberId]
   * - 404: [subscriberId] not found
   */
  fun getSubscription(subscriberId: SubscriberId): NetworkResult<ActiveSubscription> {
    val request = WebSocketRequestMessage.get("/v1/subscription/${subscriberId.serialize()}")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, ActiveSubscription::class)
  }

  /**
   * Creates a subscriber record on the signal server.
   *
   * PUT /v1/subscription/[subscriberId]
   * - 200: Success
   * - 403, 404: Invalid or malformed [subscriberId]
   */
  fun putSubscription(subscriberId: SubscriberId): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.put("/v1/subscription/${subscriberId.serialize()}", body = "")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
  }

  /**
   * DELETE /v1/subscription/[subscriberId]
   * - 204: Success
   * - 403: Invalid or malformed [subscriberId]
   * - 404: [subscriberId] not found
   */
  fun deleteSubscription(subscriberId: SubscriberId): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.delete("/v1/subscription/${subscriberId.serialize()}")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
  }

  /**
   * Updates the current subscription to the given level and currency.
   * - 200: Success
   */
  fun updateSubscriptionLevel(subscriberId: SubscriberId, level: String, currencyCode: String, idempotencyKey: String): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.put("/v1/subscription/${subscriberId.serialize()}/level/${level.urlEncode()}/${currencyCode.urlEncode()}/$idempotencyKey", "")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
  }

  /**
   * Submits price information to the server to generate a payment intent via the payment gateway.
   *
   * POST /v1/subscription/boost/create
   *
   * @param amount Price, in the minimum currency unit (e.g. cents or yen)
   * @param currencyCode The currency code for the amount
   */
  fun createStripeOneTimePaymentIntent(currencyCode: String, paymentMethod: String, amount: Long, level: Long): NetworkResult<StripeClientSecret> {
    val body = StripeOneTimePaymentIntentPayload(amount, currencyCode, level, paymentMethod)
    val request = WebSocketRequestMessage.post("/v1/subscription/boost/create", body)
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, StripeClientSecret::class)
  }

  /**
   * PUT /v1/subscription/[subscriberId]/create_payment_method?type=[type]
   * - 200: Success
   *
   * @param type One of CARD or SEPA_DEBIT
   */
  fun createStripeSubscriptionPaymentMethod(subscriberId: SubscriberId, type: String): NetworkResult<StripeClientSecret> {
    val request = WebSocketRequestMessage.post("/v1/subscription/${subscriberId.serialize()}/create_payment_method?type=${type.urlEncode()}", "")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, StripeClientSecret::class)
  }

  /**
   * Creates a PayPal one-time payment and returns the approval URL.
   *
   * POST /v1/subscription/boost/paypal/create
   * - 200: Success
   * - 400: Request error
   * - 409: Level requires a valid currency/amount combination that does not match
   *
   * @param locale       User locale for proper language presentation
   * @param currencyCode 3 letter currency code of the desired currency
   * @param amount       Stringified minimum precision amount
   * @param level        The badge level to purchase
   * @param returnUrl    The 'return' url after a successful login and confirmation
   * @param cancelUrl    The 'cancel' url for a cancelled confirmation
   */
  fun createPayPalOneTimePaymentIntent(
    locale: Locale,
    currencyCode: String,
    amount: Long,
    level: Long,
    returnUrl: String,
    cancelUrl: String
  ): NetworkResult<PayPalCreatePaymentIntentResponse> {
    val body = PayPalCreateOneTimePaymentIntentPayload(amount, currencyCode, level, returnUrl, cancelUrl)
    val request = WebSocketRequestMessage.post("/v1/subscription/boost/paypal/create", body, locale.toAcceptLanguageHeaders())
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, PayPalCreatePaymentIntentResponse::class)
  }

  /**
   * Confirms a PayPal one-time payment and returns the paymentId for receipt credentials
   *
   * POST /v1/subscription/boost/paypal/confirm
   * - 200: Success
   * - 400: Request error
   * - 409: Level requires a valid currency/amount combination that does not match
   *
   * @param currency     3 letter currency code of the desired currency
   * @param amount       Stringified minimum precision amount
   * @param level        The badge level to purchase
   * @param payerId      Passed as a URL parameter back to returnUrl
   * @param paymentId    Passed as a URL parameter back to returnUrl
   * @param paymentToken Passed as a URL parameter back to returnUrl
   */
  fun confirmPayPalOneTimePaymentIntent(
    currency: String,
    amount: String,
    level: Long,
    payerId: String,
    paymentId: String,
    paymentToken: String
  ): NetworkResult<PayPalConfirmPaymentIntentResponse> {
    val body = PayPalConfirmOneTimePaymentIntentPayload(amount, currency, level, payerId, paymentId, paymentToken)
    val request = WebSocketRequestMessage.post("/v1/subscription/boost/paypal/confirm", body)
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, PayPalConfirmPaymentIntentResponse::class)
  }

  /**
   * Sets up a payment method via PayPal for recurring charges.
   *
   * POST /v1/subscription/[subscriberId]/create_payment_method/paypal
   * - 200: success
   * - 403: subscriberId password mismatches OR account authentication is present
   * - 404: subscriberId is not found or malformed
   *
   * @param locale       User locale
   * @param subscriberId User subscriber id
   * @param returnUrl    A success URL
   * @param cancelUrl    A cancel URL
   * @return A response with an approval url and token
   */
  fun createPayPalPaymentMethod(
    locale: Locale,
    subscriberId: SubscriberId,
    returnUrl: String,
    cancelUrl: String
  ): NetworkResult<PayPalCreatePaymentMethodResponse> {
    val body = PayPalCreatePaymentMethodPayload(returnUrl, cancelUrl)
    val request = WebSocketRequestMessage.post("/v1/subscription/${subscriberId.serialize()}/create_payment_method/paypal", body, locale.toAcceptLanguageHeaders())
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, PayPalCreatePaymentMethodResponse::class)
  }

  /**
   * POST /v1/subscription/[subscriberId]/receipt_credentials
   */
  fun submitReceiptCredentials(subscriberId: SubscriberId, receiptCredentialRequest: ReceiptCredentialRequest): NetworkResult<ReceiptCredentialResponse> {
    val body = ReceiptCredentialRequestJson(receiptCredentialRequest)
    val request = WebSocketRequestMessage.post("/v1/subscription/${subscriberId.serialize()}/receipt_credentials", body)
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, webSocketResponseConverter = NetworkResult.LongPollingWebSocketConverter(ReceiptCredentialResponseJson::class))
      .map { it.receiptCredentialResponse }
      .then {
        if (it != null) {
          NetworkResult.Success(it)
        } else {
          NetworkResult.NetworkError(MalformedResponseException("Unable to parse response"))
        }
      }
  }

  /**
   * Given a completed payment intent and a receipt credential request produces a receipt credential response. Clients
   * should always use the same ReceiptCredentialRequest with the same payment intent id. This request is repeatable so
   * long as the two values are reused.
   *
   * POST /v1/subscription/boost/receipt_credentials
   *
   * @param paymentIntentId          PaymentIntent ID from a boost donation intent response.
   * @param receiptCredentialRequest Client-generated request token
   */
  fun submitBoostReceiptCredentials(paymentIntentId: String, receiptCredentialRequest: ReceiptCredentialRequest, processor: DonationProcessor): NetworkResult<ReceiptCredentialResponse> {
    val body = BoostReceiptCredentialRequestJson(paymentIntentId, receiptCredentialRequest, processor)
    val request = WebSocketRequestMessage.post("/v1/subscription/boost/receipt_credentials", body)
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request, webSocketResponseConverter = NetworkResult.LongPollingWebSocketConverter(ReceiptCredentialResponseJson::class))
      .map { it.receiptCredentialResponse }
      .then {
        if (it != null) {
          NetworkResult.Success(it)
        } else {
          NetworkResult.NetworkError(MalformedResponseException("Unable to parse response"))
        }
      }
  }

  /**
   * POST /v1/subscription/[subscriberId]/default_payment_method/stripe/[paymentMethodId]
   * - 200: Success
   */
  fun setDefaultStripeSubscriptionPaymentMethod(subscriberId: SubscriberId, paymentMethodId: String): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.post("/v1/subscription/${subscriberId.serialize()}/default_payment_method/stripe/${paymentMethodId.urlEncode()}", "")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
  }

  /**
   * POST /v1/subscription/[subscriberId]/default_payment_method_for_ideal/[setupIntentId]
   * - 200: Success
   */
  fun setDefaultIdealSubscriptionPaymentMethod(subscriberId: SubscriberId, setupIntentId: String): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.post("/v1/subscription/${subscriberId.serialize()}/default_payment_method_for_ideal/${setupIntentId.urlEncode()}", "")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
  }

  /**
   * POST /v1/subscription/[subscriberId]/default_payment_method/braintree/[paymentMethodId]
   * - 200: Success
   */
  fun setDefaultPaypalSubscriptionPaymentMethod(subscriberId: SubscriberId, paymentMethodId: String): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.post("/v1/subscription/${subscriberId.serialize()}/default_payment_method/braintree/${paymentMethodId.urlEncode()}", "")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
  }

  /**
   * POST /v1/subscription/[subscriberId]/playbilling/[purchaseToken]
   * - 200: Success
   */
  fun linkPlayBillingPurchaseToken(subscriberId: SubscriberId, purchaseToken: String): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.post("/v1/subscription/${subscriberId.serialize()}/playbilling/${purchaseToken.urlEncode()}", "")
    return NetworkResult.fromWebSocketRequest(unauthWebSocket, request)
  }

  /**
   * Allows a user to redeem a given receipt they were given after submitting a donation successfully.
   *
   * POST /v1/donation/redeem-receipt
   * - 200: Success
   *
   * @param receiptCredentialPresentation Receipt
   * @param visible                       Whether the badge will be visible on the user's profile immediately after redemption
   * @param primary                       Whether the badge will be made primary immediately after redemption
   */
  fun redeemDonationReceipt(receiptCredentialPresentation: ReceiptCredentialPresentation, visible: Boolean, primary: Boolean): NetworkResult<Unit> {
    val body = RedeemDonationReceiptRequest(Base64.encodeWithPadding(receiptCredentialPresentation.serialize()), visible, primary)
    val request = WebSocketRequestMessage.post("/v1/donation/redeem-receipt", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Allows a user to redeem a given receipt they were given after submitting a donation successfully.
   *
   * POST /v1/archives/redeem-receipt
   * - 200: Success
   *
   * @param receiptCredentialPresentation Receipt
   */
  fun redeemArchivesReceipt(receiptCredentialPresentation: ReceiptCredentialPresentation): NetworkResult<Unit> {
    val body = RedeemArchivesReceiptRequest(Base64.encodeWithPadding(receiptCredentialPresentation.serialize()))
    val request = WebSocketRequestMessage.post("/v1/archives/redeem-receipt", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  private fun Locale.toAcceptLanguageHeaders(): Map<String, String> {
    return mapOf("Accept-Language" to "${this.language}${if (this.country.isNotEmpty()) "-${this.country}" else ""}")
  }
}

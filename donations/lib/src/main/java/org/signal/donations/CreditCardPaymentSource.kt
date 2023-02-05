package org.signal.donations

import org.json.JSONObject

/**
 * Stripe payment source based off a manually entered credit card.
 */
class CreditCardPaymentSource(
  private val payload: JSONObject
) : StripeApi.PaymentSource {
  override val type = PaymentSourceType.Stripe.CreditCard
  override fun parameterize(): JSONObject = payload
  override fun getTokenId(): String = parameterize().getString("id")
  override fun email(): String? = null
}
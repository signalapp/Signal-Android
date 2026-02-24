package org.signal.donations.json

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a Stripe API PaymentIntent object.
 *
 * See: https://stripe.com/docs/api/payment_intents/object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StripePaymentIntent @JsonCreator constructor(
  @JsonProperty("id") val id: String,
  @JsonProperty("client_secret") val clientSecret: String,
  @JsonProperty("status") val status: StripeIntentStatus?,
  @JsonProperty("payment_method") val paymentMethod: String?
)

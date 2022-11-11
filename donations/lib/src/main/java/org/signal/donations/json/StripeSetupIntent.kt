package org.signal.donations.json

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a Stripe API SetupIntent object.
 *
 * See: https://stripe.com/docs/api/setup_intents/object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StripeSetupIntent(
  @JsonProperty("id") val id: String,
  @JsonProperty("client_secret") val clientSecret: String,
  @JsonProperty("status") val status: StripeIntentStatus,
  @JsonProperty("payment_method") val paymentMethod: String?,
  @JsonProperty("customer") val customer: String?
)
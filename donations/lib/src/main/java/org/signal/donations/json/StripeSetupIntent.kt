package org.signal.donations.json

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a Stripe API SetupIntent object.
 *
 * See: https://stripe.com/docs/api/setup_intents/object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StripeSetupIntent @JsonCreator constructor(
  @JsonProperty("id") val id: String,
  @JsonProperty("client_secret") val clientSecret: String,
  @JsonProperty("status") val status: StripeIntentStatus,
  @JsonProperty("payment_method") val paymentMethod: String?,
  @JsonProperty("customer") val customer: String?,
  @JsonProperty("latest_attempt") val latestAttempt: LatestAttempt?
) {

  fun requireGeneratedSepaDebit(): String = latestAttempt!!.paymentMethodDetails!!.ideal!!.generatedSepaDebit!!

  @JsonIgnoreProperties
  data class LatestAttempt @JsonCreator constructor(
    @JsonProperty("payment_method_details") val paymentMethodDetails: PaymentMethodDetails?
  )

  @JsonIgnoreProperties
  data class PaymentMethodDetails @JsonCreator constructor(
    @JsonProperty("ideal") val ideal: Ideal?
  )

  @JsonIgnoreProperties
  data class Ideal @JsonCreator constructor(
    @JsonProperty("generated_sepa_debit") val generatedSepaDebit: String?
  )
}

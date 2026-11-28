/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.subscriptions

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

class ActiveSubscription(
  @JsonProperty("subscription") val activeSubscription: Subscription? = null,
  val chargeFailure: ChargeFailure? = null
) {

  val isActive: Boolean
    get() = activeSubscription != null && activeSubscription.isActive

  val isPendingBankTransfer: Boolean
    get() = activeSubscription != null && activeSubscription.paymentMethod == PaymentMethod.SEPA_DEBIT && activeSubscription.paymentPending

  val isInProgress: Boolean
    get() = activeSubscription != null && !isActive && (!isFailedPayment || isPastDue) && !isCanceled

  val isPastDue: Boolean
    get() = activeSubscription != null && activeSubscription.isPastDue

  val isFailedPayment: Boolean
    get() = chargeFailure != null || (activeSubscription != null && !isActive && activeSubscription.isFailedPayment)

  val isCanceled: Boolean
    get() = activeSubscription != null && activeSubscription.isCanceled

  /**
   * Backups-specific call that gives us a value that should align with autoRenew from the GPB payment.
   */
  val willCancelAtPeriodEnd: Boolean
    get() = activeSubscription == null || activeSubscription.willCancelAtPeriodEnd

  enum class Processor(val code: String) {
    STRIPE("STRIPE"),
    BRAINTREE("BRAINTREE"),
    GOOGLE_PLAY_BILLING("GOOGLE_PLAY_BILLING");

    companion object {
      fun fromCode(code: String?): Processor = entries.firstOrNull { it.code == code } ?: STRIPE
    }
  }

  /**
   * As per API documentation
   */
  enum class PaymentMethod(val code: String) {
    UNKNOWN("UNKNOWN"),
    CARD("CARD"),
    PAYPAL("PAYPAL"),
    SEPA_DEBIT("SEPA_DEBIT"),
    IDEAL("IDEAL"),
    GOOGLE_PLAY_BILLING("GOOGLE_PLAY_BILLING"),
    APPLE_APP_STORE("APPLE_APP_STORE");

    companion object {
      fun fromCode(code: String?): PaymentMethod = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
  }

  enum class Status(val code: String) {
    /**
     * The subscription is currently in a trial period and it's safe to provision your product for your customer.
     * The subscription transitions automatically to active when the first payment is made.
     */
    TRIALING("trialing"),

    /**
     * The subscription is in good standing and the most recent payment was successful. It's safe to provision your product for your customer.
     */
    ACTIVE("active"),

    /**
     * Payment failed when you created the subscription. A successful payment needs to be made within 23 hours to activate the subscription.
     */
    INCOMPLETE("incomplete"),

    /**
     * The initial payment on the subscription failed and no successful payment was made within 23 hours of creating the subscription.
     * These subscriptions don't bill customers. This status exists so you can track customers that failed to activate their subscriptions.
     */
    INCOMPLETE_EXPIRED("incomplete_expired"),

    /**
     * Payment on the latest invoice either failed or wasn't attempted.
     */
    PAST_DUE("past_due"),

    /**
     * The subscription has been canceled. During cancellation, automatic collection for all unpaid invoices is disabled (auto_advance=false).
     */
    CANCELED("canceled"),

    /**
     * The latest invoice hasn't been paid but the subscription remains in place.
     * The latest invoice remains open and invoices continue to be generated but payments aren't attempted.
     */
    UNPAID("unpaid");

    companion object {
      private val FAILURE_STATUSES = setOf(INCOMPLETE_EXPIRED, PAST_DUE, UNPAID)

      @JvmStatic
      fun getStatus(status: String?): Status {
        return entries.firstOrNull { it.code == status } ?: throw IllegalArgumentException("Unknown status $status")
      }

      internal fun isPaymentFailed(status: String?): Boolean = FAILURE_STATUSES.contains(getStatus(status))
    }
  }

  data class Subscription(
    val level: Int = 0,
    val currency: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    /** UNIX Epoch Timestamp in seconds */
    val endOfCurrentPeriod: Long = 0,
    /** Whether this subscription is currently active. */
    @JsonProperty("active") val isActive: Boolean = false,
    /**
     * UNIX Epoch Timestamp in seconds, can be used to calculate next billing date per
     * https://stripe.com/docs/billing/subscriptions/billing-cycle
     */
    val billingCycleAnchor: Long = 0,
    /** Whether this subscription is set to end at the end of the current period. */
    @JsonProperty("cancelAtPeriodEnd") val willCancelAtPeriodEnd: Boolean = false,
    /** The Stripe status of this subscription (see https://stripe.com/docs/billing/subscriptions/overview#subscription-statuses) */
    val status: String = "",
    @JsonProperty("processor") private val processorCode: String? = null,
    @JsonProperty("paymentMethod") private val paymentMethodCode: String? = null,
    /** Whether the latest invoice for the subscription is in a non-terminal state. */
    val paymentPending: Boolean = false
  ) {
    val processor: Processor
      get() = Processor.fromCode(processorCode)

    val paymentMethod: PaymentMethod
      get() = PaymentMethod.fromCode(paymentMethodCode)

    val isFailedPayment: Boolean
      get() = Status.isPaymentFailed(status)

    val isPastDue: Boolean
      get() = Status.getStatus(status) == Status.PAST_DUE

    val isCanceled: Boolean
      get() = Status.getStatus(status) == Status.CANCELED
  }

  data class ChargeFailure(
    /**
     * Error code explaining reason for charge failure if available (see the errors section for a list of codes).
     *
     * See: [https://stripe.com/docs/api/charges/object#charge_object-failure_code]
     */
    val code: String? = null,

    /**
     * Message to user further explaining reason for charge failure if available.
     *
     * See: [https://stripe.com/docs/api/charges/object#charge_object-failure_message]
     */
    val message: String? = null,

    /**
     * Possible values are approved_by_network, declined_by_network, not_sent_to_network, and reversed_after_approval.
     * The value reversed_after_approval indicates the payment was blocked by Stripe after bank authorization,
     * and may temporarily appear as "pending" on a cardholder's statement.
     *
     * See: [https://stripe.com/docs/api/charges/object#charge_object-outcome-network_status]
     */
    val outcomeNetworkStatus: String? = null,

    /**
     * An enumerated value providing a more detailed explanation of the outcome's type. Charges blocked by Radar's default block rule have the value
     * highest_risk_level. Charges placed in review by Radar's default review rule have the value elevated_risk_level. Charges authorized, blocked, or placed
     * in review by custom rules have the value rule. See understanding declines for more details.
     *
     * See: [https://stripe.com/docs/api/charges/object#charge_object-outcome-reason]
     */
    val outcomeNetworkReason: String? = null,

    /**
     * Possible values are authorized, manual_review, issuer_declined, blocked, and invalid. See understanding declines and Radar reviews for details.
     *
     * See: [https://stripe.com/docs/api/charges/object#charge_object-outcome-type]
     */
    val outcomeType: String? = null
  ) {
    override fun toString(): String {
      return "ChargeFailure{code='$code', outcomeNetworkStatus='$outcomeNetworkStatus', outcomeNetworkReason='$outcomeNetworkReason', outcomeType='$outcomeType'}"
    }
  }

  companion object {
    const val PAYMENT_METHOD_SEPA_DEBIT = "SEPA_DEBIT"

    @JvmField
    val EMPTY = ActiveSubscription(null, null)
  }
}

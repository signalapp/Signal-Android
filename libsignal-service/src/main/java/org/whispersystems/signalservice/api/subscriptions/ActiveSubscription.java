package org.whispersystems.signalservice.api.subscriptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

public final class ActiveSubscription {

  public static final String PAYMENT_METHOD_SEPA_DEBIT = "SEPA_DEBIT";

  public static final ActiveSubscription EMPTY = new ActiveSubscription(null, null);

  public enum Processor {
    STRIPE("STRIPE"),
    BRAINTREE("BRAINTREE"),
    GOOGLE_PLAY_BILLING("GOOGLE_PLAY_BILLING");

    private final String code;

    Processor(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    static Processor fromCode(String code) {
      for (Processor value : Processor.values()) {
        if (value.code.equals(code)) {
          return value;
        }
      }

      return STRIPE;
    }
  }

  /**
   * As per API documentation
   */
  public enum PaymentMethod {
    UNKNOWN("UNKNOWN"),
    CARD("CARD"),
    PAYPAL("PAYPAL"),
    SEPA_DEBIT("SEPA_DEBIT"),
    IDEAL("IDEAL"),
    GOOGLE_PLAY_BILLING("GOOGLE_PLAY_BILLING"),
    APPLE_APP_STORE("APPLE_APP_STORE");

    private String code;

    PaymentMethod(String code) {
      this.code = code;
    }

    static PaymentMethod fromCode(String code) {
      for (PaymentMethod method : PaymentMethod.values()) {
        if (Objects.equals(method.code, code)) {
          return method;
        }
      }

      return PaymentMethod.UNKNOWN;
    }
  }

  private enum Status {
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

    private final String status;

    private static final Set<Status> FAILURE_STATUSES = new HashSet<>(Arrays.asList(
        INCOMPLETE_EXPIRED,
        PAST_DUE,
        UNPAID
    ));

    Status(String status) {
      this.status = status;
    }

    private static Status getStatus(String status) {
      for (Status s : Status.values()) {
        if (Objects.equals(status, s.status)) {
          return s;
        }
      }

      throw new IllegalArgumentException("Unknown status " + status);
    }

    static boolean isPaymentFailed(String status) {
      return FAILURE_STATUSES.contains(getStatus(status));
    }
  }

  private final Subscription  activeSubscription;
  private final ChargeFailure chargeFailure;

  @JsonCreator
  public ActiveSubscription(@JsonProperty("subscription") Subscription activeSubscription,
                            @JsonProperty("chargeFailure") ChargeFailure chargeFailure)
  {
    this.activeSubscription = activeSubscription;
    this.chargeFailure      = chargeFailure;
  }

  public Subscription getActiveSubscription() {
    return activeSubscription;
  }

  public ChargeFailure getChargeFailure() {
    return chargeFailure;
  }

  public boolean isActive() {
    return activeSubscription != null && activeSubscription.isActive();
  }

  public boolean isPendingBankTransfer() {
    return activeSubscription != null && Objects.equals(activeSubscription.paymentMethod, PAYMENT_METHOD_SEPA_DEBIT) && activeSubscription.paymentPending;
  }

  public boolean isInProgress() {
    return activeSubscription != null && !isActive() && (!isFailedPayment() || isPastDue()) && !isCanceled();
  }

  public boolean isPastDue() {
    return activeSubscription != null && activeSubscription.isPastDue();
  }

  public boolean isFailedPayment() {
    return chargeFailure != null || (activeSubscription != null && !isActive() && activeSubscription.isFailedPayment());
  }

  public boolean isCanceled() {
    return activeSubscription != null && activeSubscription.isCanceled();
  }

  public static final class Subscription {
    private final int           level;
    private final String        currency;
    private final BigDecimal    amount;
    private final long          endOfCurrentPeriod;
    private final boolean       isActive;
    private final long          billingCycleAnchor;
    private final boolean       willCancelAtPeriodEnd;
    private final String        status;
    private final Processor     processor;
    private final PaymentMethod paymentMethod;
    private final boolean       paymentPending;

    @JsonCreator
    public Subscription(@JsonProperty("level") int level,
                        @JsonProperty("currency") String currency,
                        @JsonProperty("amount") BigDecimal amount,
                        @JsonProperty("endOfCurrentPeriod") long endOfCurrentPeriod,
                        @JsonProperty("active") boolean isActive,
                        @JsonProperty("billingCycleAnchor") long billingCycleAnchor,
                        @JsonProperty("cancelAtPeriodEnd") boolean willCancelAtPeriodEnd,
                        @JsonProperty("status") String status,
                        @JsonProperty("processor") String processor,
                        @JsonProperty("paymentMethod") String paymentMethod,
                        @JsonProperty("paymentPending") boolean paymentPending)
    {
      this.level                 = level;
      this.currency              = currency;
      this.amount                = amount;
      this.endOfCurrentPeriod    = endOfCurrentPeriod;
      this.isActive              = isActive;
      this.billingCycleAnchor    = billingCycleAnchor;
      this.willCancelAtPeriodEnd = willCancelAtPeriodEnd;
      this.status                = status;
      this.processor             = Processor.fromCode(processor);
      this.paymentMethod         = PaymentMethod.fromCode(paymentMethod);
      this.paymentPending        = paymentPending;
    }

    public int getLevel() {
      return level;
    }

    public String getCurrency() {
      return currency;
    }

    public BigDecimal getAmount() {
      return amount;
    }

    /**
     * UNIX Epoch Timestamp in seconds, can be used to calculate next billing date per
     * https://stripe.com/docs/billing/subscriptions/billing-cycle
     */
    public long getBillingCycleAnchor() {
      return billingCycleAnchor;
    }

    /**
     * Whether this subscription is currently active.
     */
    public boolean isActive() {
      return isActive;
    }

    /**
     * UNIX Epoch Timestamp in seconds
     */
    public long getEndOfCurrentPeriod() {
      return endOfCurrentPeriod;
    }

    /**
     * Whether this subscription is set to end at the end of the current period.
     */
    public boolean willCancelAtPeriodEnd() {
      return willCancelAtPeriodEnd;
    }

    /**
     * The Stripe status of this subscription (see https://stripe.com/docs/billing/subscriptions/overview#subscription-statuses)
     */
    public String getStatus() {
      return status;
    }

    public Processor getProcessor() {
      return processor;
    }

    public PaymentMethod getPaymentMethod() {
      return paymentMethod;
    }

    /**
     * @return Whether the latest invoice for the subscription is in a non-terminal state
     */
    public boolean isPaymentPending() {
      return paymentPending;
    }

    public boolean isFailedPayment() {
      return Status.isPaymentFailed(getStatus());
    }

    public boolean isPastDue() {
      return Status.getStatus(getStatus()) == Status.PAST_DUE;
    }

    public boolean isCanceled() {
      return Status.getStatus(getStatus()) == Status.CANCELED;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Subscription that = (Subscription) o;
      return level == that.level && endOfCurrentPeriod == that.endOfCurrentPeriod && isActive == that.isActive && billingCycleAnchor == that.billingCycleAnchor && willCancelAtPeriodEnd == that.willCancelAtPeriodEnd && currency
          .equals(that.currency) && amount.equals(that.amount) && status.equals(that.status) && Objects.equals(paymentMethod, that.paymentMethod) && paymentPending == that.paymentPending;
    }

    @Override
    public int hashCode() {
      return Objects.hash(level, currency, amount, endOfCurrentPeriod, isActive, billingCycleAnchor, willCancelAtPeriodEnd, status, paymentMethod, paymentPending);
    }
  }

  public static final class ChargeFailure {
    private final String code;
    private final String message;
    private final String outcomeNetworkStatus;
    private final String outcomeNetworkReason;
    private final String outcomeType;

    @JsonCreator
    public ChargeFailure(@JsonProperty("code") String code,
                         @JsonProperty("message") String message,
                         @JsonProperty("outcomeNetworkStatus") String outcomeNetworkStatus,
                         @JsonProperty("outcomeNetworkReason") String outcomeNetworkReason,
                         @JsonProperty("outcomeType") String outcomeType)
    {
      this.code                 = code;
      this.message              = message;
      this.outcomeNetworkStatus = outcomeNetworkStatus;
      this.outcomeNetworkReason = outcomeNetworkReason;
      this.outcomeType          = outcomeType;
    }

    /**
     * Error code explaining reason for charge failure if available (see the errors section for a list of codes).
     * <p>
     * See: <a href="https://stripe.com/docs/api/charges/object#charge_object-failure_code">https://stripe.com/docs/api/charges/object#charge_object-failure_code</a>
     */
    public String getCode() {
      return code;
    }

    /**
     * Message to user further explaining reason for charge failure if available.
     * <p>
     * See: <a href="https://stripe.com/docs/api/charges/object#charge_object-failure_message">https://stripe.com/docs/api/charges/object#charge_object-failure_message</a>
     */
    public String getMessage() {
      return message;
    }

    /**
     * Possible values are approved_by_network, declined_by_network, not_sent_to_network, and reversed_after_approval.
     * The value reversed_after_approval indicates the payment was blocked by Stripe after bank authorization,
     * and may temporarily appear as "pending" on a cardholder's statement.
     * <p>
     * See: <a href="https://stripe.com/docs/api/charges/object#charge_object-outcome-network_status">https://stripe.com/docs/api/charges/object#charge_object-outcome-network_status</a>
     */
    public String getOutcomeNetworkStatus() {
      return outcomeNetworkStatus;
    }

    /**
     * An enumerated value providing a more detailed explanation of the outcome's type. Charges blocked by Radar's default block rule have the value
     * highest_risk_level. Charges placed in review by Radar's default review rule have the value elevated_risk_level. Charges authorized, blocked, or placed
     * in review by custom rules have the value rule. See understanding declines for more details.
     * <p>
     * See: <a href="https://stripe.com/docs/api/charges/object#charge_object-outcome-reason">https://stripe.com/docs/api/charges/object#charge_object-outcome-reason</a>
     */
    public @Nullable String getOutcomeNetworkReason() {
      return outcomeNetworkReason;
    }

    /**
     * Possible values are authorized, manual_review, issuer_declined, blocked, and invalid. See understanding declines and Radar reviews for details.
     * <p>
     * See: <a href="https://stripe.com/docs/api/charges/object#charge_object-outcome-type">https://stripe.com/docs/api/charges/object#charge_object-outcome-type</a>
     */
    public String getOutcomeType() {
      return outcomeType;
    }

    @Override public String toString() {
      return "ChargeFailure{" +
             "code='" + code + '\'' +
             ", outcomeNetworkStatus='" + outcomeNetworkStatus + '\'' +
             ", outcomeNetworkReason='" + outcomeNetworkReason + '\'' +
             ", outcomeType='" + outcomeType + '\'' +
             '}';
    }
  }
}

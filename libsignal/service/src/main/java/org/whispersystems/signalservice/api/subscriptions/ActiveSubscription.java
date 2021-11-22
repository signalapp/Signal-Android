package org.whispersystems.signalservice.api.subscriptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class ActiveSubscription {

  private enum Status {
    /**
     * The subscription is currently in a trial period and it’s safe to provision your product for your customer.
     * The subscription transitions automatically to active when the first payment is made.
     */
    TRIALING("trialing"),

    /**
     * The subscription is in good standing and the most recent payment was successful. It’s safe to provision your product for your customer.
     */
    ACTIVE("active"),

    /**
     * Payment failed when you created the subscription. A successful payment needs to be made within 23 hours to activate the subscription.
     */
    INCOMPLETE("incomplete"),

    /**
     * The initial payment on the subscription failed and no successful payment was made within 23 hours of creating the subscription.
     * These subscriptions don’t bill customers. This status exists so you can track customers that failed to activate their subscriptions.
     */
    INCOMPLETE_EXPIRED("incomplete_expired"),

    /**
     * 	Payment on the latest invoice either failed or wasn’t attempted.
     */
    PAST_DUE("past_due"),

    /**
     * The subscription has been canceled. During cancellation, automatic collection for all unpaid invoices is disabled (auto_advance=false).
     */
    CANCELED("canceled"),

    /**
     * The latest invoice hasn’t been paid but the subscription remains in place.
     * The latest invoice remains open and invoices continue to be generated but payments aren’t attempted.
     */
    UNPAID("unpaid");

    private final String status;

    private static final Set<Status> FAILURE_STATUSES = new HashSet<>(Arrays.asList(
        INCOMPLETE_EXPIRED,
        PAST_DUE,
        CANCELED,
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

  private final Subscription activeSubscription;

  @JsonCreator
  public ActiveSubscription(@JsonProperty("subscription") Subscription activeSubscription) {
    this.activeSubscription = activeSubscription;
  }

  public Subscription getActiveSubscription() {
    return activeSubscription;
  }

  public boolean isActive() {
    return activeSubscription != null && activeSubscription.isActive();
  }

  public boolean isInProgress() {
    return activeSubscription != null && !isActive() && !activeSubscription.isFailedPayment();
  }

  public boolean isFailedPayment() {
    return activeSubscription != null && !isActive() && activeSubscription.isFailedPayment();
  }

  public static final class Subscription {
    private final int        level;
    private final String     currency;
    private final BigDecimal amount;
    private final long       endOfCurrentPeriod;
    private final boolean    isActive;
    private final long       billingCycleAnchor;
    private final boolean    willCancelAtPeriodEnd;
    private final String     status;

    @JsonCreator
    public Subscription(@JsonProperty("level") int level,
                        @JsonProperty("currency") String currency,
                        @JsonProperty("amount") BigDecimal amount,
                        @JsonProperty("endOfCurrentPeriod") long endOfCurrentPeriod,
                        @JsonProperty("active") boolean isActive,
                        @JsonProperty("billingCycleAnchor") long billingCycleAnchor,
                        @JsonProperty("cancelAtPeriodEnd") boolean willCancelAtPeriodEnd,
                        @JsonProperty("status") String status)
    {
      this.level                 = level;
      this.currency              = currency;
      this.amount                = amount;
      this.endOfCurrentPeriod    = endOfCurrentPeriod;
      this.isActive              = isActive;
      this.billingCycleAnchor    = billingCycleAnchor;
      this.willCancelAtPeriodEnd = willCancelAtPeriodEnd;
      this.status                = status;
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

    public boolean isInProgress() {
      return !isActive() && !Status.isPaymentFailed(getStatus());
    }

    public boolean isFailedPayment() {
      return Status.isPaymentFailed(getStatus());
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final Subscription that = (Subscription) o;
      return level == that.level && endOfCurrentPeriod == that.endOfCurrentPeriod && isActive == that.isActive && billingCycleAnchor == that.billingCycleAnchor && willCancelAtPeriodEnd == that.willCancelAtPeriodEnd && currency
          .equals(that.currency) && amount.equals(that.amount) && status.equals(that.status);
    }

    @Override public int hashCode() {
      return Objects.hash(level, currency, amount, endOfCurrentPeriod, isActive, billingCycleAnchor, willCancelAtPeriodEnd, status);
    }
  }
}

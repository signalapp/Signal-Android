package org.whispersystems.signalservice.api.subscriptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public final class ActiveSubscription {

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

  public static final class Subscription {
    private final int        level;
    private final String     currency;
    private final BigDecimal amount;
    private final long       endOfCurrentPeriod;
    private final boolean    isActive;
    private final long       billingCycleAnchor;
    private final boolean    willCancelAtPeriodEnd;

    @JsonCreator
    public Subscription(@JsonProperty("level") int level,
                        @JsonProperty("currency") String currency,
                        @JsonProperty("amount") BigDecimal amount,
                        @JsonProperty("endOfCurrentPeriod") long endOfCurrentPeriod,
                        @JsonProperty("active") boolean isActive,
                        @JsonProperty("billingCycleAnchor") long billingCycleAnchor,
                        @JsonProperty("cancelAtPeriodEnd") boolean willCancelAtPeriodEnd)
    {
      this.level                 = level;
      this.currency              = currency;
      this.amount                = amount;
      this.endOfCurrentPeriod    = endOfCurrentPeriod;
      this.isActive              = isActive;
      this.billingCycleAnchor    = billingCycleAnchor;
      this.willCancelAtPeriodEnd = willCancelAtPeriodEnd;
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
  }
}

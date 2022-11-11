package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class StripeOneTimePaymentIntentPayload {
  @JsonProperty
  private long amount;

  @JsonProperty
  private String currency;

  @JsonProperty
  private long level;

  public StripeOneTimePaymentIntentPayload(long amount, String currency, long level) {
    this.amount      = amount;
    this.currency    = currency;
    this.level       = level;
  }
}

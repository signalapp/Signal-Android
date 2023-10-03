package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class StripeOneTimePaymentIntentPayload {
  @JsonProperty
  private long amount;

  @JsonProperty
  private String currency;

  @JsonProperty
  private long level;

  @JsonProperty
  private String paymentMethod;

  public StripeOneTimePaymentIntentPayload(long amount, String currency, long level, String paymentMethod) {
    this.amount        = amount;
    this.currency      = currency;
    this.level         = level;
    this.paymentMethod = paymentMethod;
  }
}

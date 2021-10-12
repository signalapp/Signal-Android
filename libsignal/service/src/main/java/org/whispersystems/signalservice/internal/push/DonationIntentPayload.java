package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class DonationIntentPayload {
  @JsonProperty
  private long amount;

  @JsonProperty
  private String currency;

  public DonationIntentPayload(long amount, String currency) {
    this.amount   = amount;
    this.currency = currency;
  }
}

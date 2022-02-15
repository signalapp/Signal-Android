package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class DonationIntentPayload {
  @JsonProperty
  private long amount;

  @JsonProperty
  private String currency;

  @JsonProperty
  private String description;

  public DonationIntentPayload(long amount, String currency, String description) {
    this.amount      = amount;
    this.currency    = currency;
    this.description = description;
  }
}

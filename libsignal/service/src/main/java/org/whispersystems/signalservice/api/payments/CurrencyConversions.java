package org.whispersystems.signalservice.api.payments;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class CurrencyConversions {
  @JsonProperty
  private List<CurrencyConversion> currencies;

  @JsonProperty
  private long timestamp;

  public List<CurrencyConversion> getCurrencies() {
    return currencies;
  }

  public long getTimestamp() {
    return timestamp;
  }
}

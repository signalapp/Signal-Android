package org.whispersystems.signalservice.api.payments;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public final class CurrencyConversion {
  @JsonProperty
  private String base;

  @JsonProperty
  private Map<String, Double> conversions;

  public String getBase() {
    return base;
  }

  public Map<String, Double> getConversions() {
    return conversions;
  }
}

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WhoAmIResponse {
  @JsonProperty
  private String uuid;

  @JsonProperty
  private String number;

  public String getUuid() {
    return uuid;
  }

  public String getNumber() {
    return number;
  }
}

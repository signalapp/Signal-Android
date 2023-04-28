package org.thoughtcrime.securesms.registration.v2.testdata;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PinValidityVector {

  @JsonProperty("name")
  private String name;

  @JsonProperty("pin")
  private String pin;

  @JsonProperty("valid")
  private boolean valid;

  public String getName() {
    return name;
  }

  public String getPin() {
    return pin;
  }

  public boolean isValid() {
    return valid;
  }
}

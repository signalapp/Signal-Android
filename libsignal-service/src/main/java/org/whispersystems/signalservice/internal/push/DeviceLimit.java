package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceLimit {

  @JsonProperty
  private int current;

  @JsonProperty
  private int max;

  public int getCurrent() {
    return current;
  }

  public int getMax() {
    return max;
  }
}

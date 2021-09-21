package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the body of a 410 response from the service during a sender key send.
 */
public class GroupStaleDevices {

  @JsonProperty
  private String uuid;

  @JsonProperty
  private StaleDevices devices;

  public String getUuid() {
    return uuid;
  }

  public StaleDevices getDevices() {
    return devices;
  }
}

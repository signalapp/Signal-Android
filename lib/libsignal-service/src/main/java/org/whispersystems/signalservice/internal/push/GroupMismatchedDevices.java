package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the body of a 409 response from the service during a sender key send.
 */
public class GroupMismatchedDevices {
  @JsonProperty
  private String uuid;

  @JsonProperty
  private MismatchedDevices devices;

  public GroupMismatchedDevices() {}

  public String getUuid() {
    return uuid;
  }

  public MismatchedDevices getDevices() {
    return devices;
  }
}
